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
    private val publicationLock = Mutex()

    suspend fun reload(
        forceRefresh: Boolean = false,
        onInstalled: suspend () -> Unit = {},
    ): Summary = withContext(Dispatchers.IO) {
        reloadLock.withLock {
            val requested = configuredUrls()
            val installedUrls = engine.stremioAddons().map { it.descriptor.baseUrl.trim() }.toSet()
            val missing = requested.filter { forceRefresh || it !in installedUrls }
            // Publish each successful manifest without waiting for slower downloads.
            // Failed refreshes retain working descriptors. Publication is serialized.
            val prepared = coroutineScope {
                missing.map { url ->
                    async {
                        downloads.withPermit {
                            currentCoroutineContext().ensureActive()
                            val started = android.os.SystemClock.elapsedRealtime()
                            val result = try {
                                Result.success(loadManifest(url))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                            val elapsed = android.os.SystemClock.elapsedRealtime() - started
                            // Hash identifies repeated manifest requests without exposing tokens.
                            val manifestId = java.security.MessageDigest.getInstance("SHA-256")
                                .digest(url.toByteArray(Charsets.UTF_8)).take(8)
                                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                            com.vueo.shared.core.diagnostics.PerformanceDiagnostics.captureRuntimeEvent(
                                "ADDON_MANIFEST platform=Mobile id=$manifestId elapsedMs=$elapsed outcome=${if (result.isSuccess) "ok" else "failed"}",
                            )
                            val extension = result.getOrNull()
                            if (extension != null) {
                                val published = publicationLock.withLock {
                                    currentCoroutineContext().ensureActive()
                                    if (url !in configuredUrls()) false else {
                                        store.migrateAddonCatalogIdentity(extension.descriptor)
                                        engine.install(extension) { store.isAddonEnabled(url) }
                                        true
                                    }
                                }
                                if (published) onInstalled()
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
