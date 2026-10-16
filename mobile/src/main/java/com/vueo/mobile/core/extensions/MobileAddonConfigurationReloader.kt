package com.vueo.mobile.core.extensions

import com.vueo.mobile.core.storage.AddonStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One coordinator for startup, imported preferences and account configuration. */
internal class MobileAddonConfigurationReloader(
    private val engine: UnifiedMediaEngine,
    private val store: AddonStore,
    private val loadManifest: suspend (String) -> MediaExtension = ExtensionInstaller::installStremioAddon,
) {
    data class Summary(
        val loaded: Int,
        val reused: Int,
        val removed: Int,
        val failed: Int,
        val conflicts: Int,
    )

    private val reloadLock = Mutex()
    private val downloads = Semaphore(2)

    suspend fun reload(forceRefresh: Boolean = false): Summary = withContext(Dispatchers.IO) {
        reloadLock.withLock {
            val requested = configuredUrls()
            val installedUrls = engine.stremioAddons().map { it.descriptor.baseUrl.trim() }.toSet()
            val missing = requested.filter { forceRefresh || it !in installedUrls }
            // Fetch before replacing anything. Failure/cancellation leaves working
            // descriptors intact; parallel jobs never mutate the engine.
            val prepared = coroutineScope {
                missing.map { url ->
                    async {
                        downloads.withPermit {
                            currentCoroutineContext().ensureActive()
                            val result = try {
                                Result.success(loadManifest(url))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                            url to result
                        }
                    }
                }.awaitAll()
            }
            currentCoroutineContext().ensureActive()
            // Preferences can change while network work is in flight. Do not
            // resurrect an addon removed during the fetch; use current toggles.
            val configured = configuredUrls().toSet()
            val removed = engine.stremioAddons().filter { it.descriptor.baseUrl.trim() !in configured }
            removed.forEach { engine.uninstall(it.descriptor.installationId) }
            var loaded = 0
            var failed = 0
            val conflicts = 0
            prepared.forEach { (url, result) ->
                if (url !in configured) return@forEach
                val extension = result.getOrNull()
                if (extension == null) {
                    failed++
                    return@forEach
                }
                store.migrateAddonCatalogIdentity(extension.descriptor)
                engine.install(extension) { store.isAddonEnabled(url) }
                loaded++
            }
            // Apply enable/disable even when a refresh failed and the previous
            // descriptor is retained. URLs added mid-fetch are handled on the
            // next queued configuration revision.
            engine.stremioAddons().forEach { extension ->
                val url = extension.descriptor.baseUrl.trim()
                if (url in configured) engine.setExtensionEnabled(extension.descriptor.installationId, store.isAddonEnabled(url))
            }
            Summary(
                loaded = loaded,
                reused = requested.count { it in installedUrls && !forceRefresh && it in configured },
                removed = removed.size,
                failed = failed,
                conflicts = conflicts,
            )
        }
    }

    private fun configuredUrls(): List<String> = store.manifestUrls()
        .map(String::trim).filter(String::isNotBlank).distinct()
}
