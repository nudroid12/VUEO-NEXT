package com.vueo.tv.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.StremioAddonExtension
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.home.HomeCatalogPolicy
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryManager
import com.vueo.shared.core.plugin.PluginRepositoryRefreshSummary
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.ProviderCodeSyncManager
import com.vueo.shared.core.recommendation.RelatedContentOrchestrator
import com.vueo.shared.core.source.SourceDiscoveryCache
import com.vueo.shared.core.source.SourceDiscoveryEngine
import com.vueo.shared.core.source.SourceDiscoveryRequest
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.PlaybackStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex

/**
 * New TV runtime. It deliberately mirrors Mobile's proven runtime boundaries
 * instead of depending on any legacy TV repositories/stores.
 */
class TvRuntime(context: Context) {
    private val appContext = context.applicationContext

    val engine = UnifiedMediaEngine()
    val content = TvContentPreferences(appContext)
    val profileStore = ProfileStore(appContext)
    // Match Mobile's proven Shared Core store semantics. TV is a separate
    // application sandbox, so these names do not couple TV to the Mobile app;
    // they simply remove legacy TV-specific storage behavior.
    val libraryStore = LibraryStore(
        context = appContext,
        profileStore = profileStore,
    )
    val playbackStore = PlaybackStore(
        context = appContext,
        profileStore = profileStore,
    )
    val settingsStore = SettingsStore(
        context = appContext,
        profileStore = profileStore,
    )
    val dnaPreferences = UserDnaPreferences(appContext)
    val dnaEngine = UserDnaEngine(libraryStore)
    val pluginStore = PluginStore(appContext)
    val pluginEngine = PluginSourceEngine(appContext, pluginStore)
    private val sourceDiscoveryEngine = SourceDiscoveryEngine(engine, pluginEngine, pluginStore)
    private val providerSync = ProviderCodeSyncManager(appContext)
    private val pluginRepositoryManager = PluginRepositoryManager(appContext)
    private val pluginHealthStore = PluginHealthStore(appContext)
    private val addonLoadMutex = Mutex()

    @Volatile
    private var addonsPrepared = false
    private val addonRevision = MutableStateFlow(0)
    private val homeLoadGate = Semaphore(4)
    private val homeCachePrefs = appContext.getSharedPreferences("vueo_tv_home_freshness", Context.MODE_PRIVATE)
    private val homeStartedAt = SystemClock.elapsedRealtime()
    private val homePresented = AtomicBoolean(false)

    private fun signalAddonChange() = synchronized(addonRevision) {
        addonRevision.value = addonRevision.value + 1
    }

    fun traceHome(stage: String, count: Int = 0) {
        Log.d("VUEO_HOME", "stage=$stage elapsed_ms=${SystemClock.elapsedRealtime() - homeStartedAt} count=$count")
    }

    fun markHomePresented() {
        if (homePresented.compareAndSet(false, true)) traceHome("first_rows_presented")
    }

    private fun homeConfigurationKey(): String {
        val configuration = buildString {
            content.manifestUrls().sorted().forEach { append(it.trim()); append(':'); append(content.isAddonEnabled(it)); append('\n') }
            append(content.catalogOrder().joinToString("\n")); append('|')
            append(content.disabledCatalogKeys().sorted().joinToString("\n"))
        }
        return MessageDigest.getInstance("SHA-256").digest(configuration.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Startup-critical work only. Keep this path local so a slow addon can never
     * hold the launch screen. Network-backed addon preparation is deliberately
     * handled by [prepareAddonsInBackground] after the first app destination is shown.
     */
    fun boot() {
        profileStore.ensureDefaultProfile()
        content.seedDevelopmentDefaultsIfNeeded()
    }

    suspend fun restoreHomeCache() {
        val rows = CatalogDiscoveryCache.restoreHome(appContext)
        traceHome("cache_restored", rows.size)
    }

    fun cachedHomeRows(): List<CatalogRow> =
        CatalogDiscoveryCache.home(allowStale = true)
            .orEmpty()
            .let(::applyCatalogPreferences)

    fun isHomeCatalogRuntimeReady(): Boolean = addonsPrepared

    fun needsHomeRefresh(): Boolean =
        CatalogDiscoveryCache.home(allowStale = false).isNullOrEmpty() ||
            homeCachePrefs.getString("configuration", null) != homeConfigurationKey()


    suspend fun prepareAddonsInBackground() {
        addonLoadMutex.lock()
        try {
            installConfiguredAddons()
        } finally {
            addonsPrepared = true
            signalAddonChange()
            traceHome("manifests_finished", engine.stremioAddons().size)
            addonLoadMutex.unlock()
        }
    }

    suspend fun prepareProvidersInBackground() {
        runCatching { pluginStore.seedDevelopmentDefaultsIfNeeded() }
        runCatching { providerSync.syncMissing(pluginStore.repositories()) }
    }

    suspend fun homeRows(
        forceRefresh: Boolean = false,
        onPartial: ((List<CatalogRow>) -> Unit)? = null,
    ): List<CatalogRow> = coroutineScope {
        val configurationKey = homeConfigurationKey()
        val cachedConfiguration = homeCachePrefs.getString("configuration", null)
        val freshCached = CatalogDiscoveryCache.home(allowStale = false).orEmpty()
        if (!forceRefresh && freshCached.isNotEmpty() &&
            cachedConfiguration == configurationKey
        ) {
            traceHome("fresh_cache_used", freshCached.size)
            return@coroutineScope applyCatalogPreferences(freshCached)
        }
        val staleCached = CatalogDiscoveryCache.home(allowStale = true).orEmpty()
        val freshRows = linkedMapOf<String, CatalogRow>()
        val scheduled = mutableSetOf<String>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        var firstFreshRow = true

        fun publish(rows: List<CatalogRow>) = synchronized(freshRows) {
            rows.forEach { freshRows[it.id] = it }
            if (freshRows.isNotEmpty()) {
                if (firstFreshRow) {
                    firstFreshRow = false
                    traceHome("first_fresh_row", freshRows.size)
                }
                // Keep untouched cached rows visible while fresh rows replace them.
                val combined = staleCached.map { freshRows[it.id] ?: it } +
                    freshRows.values.filter { fresh -> staleCached.none { it.id == fresh.id } }
                onPartial?.invoke(applyCatalogPreferences(combined))
            }
            Unit
        }

        // Each installed addon can start catalogs immediately. The shared gate
        // limits ALL these addon jobs together to four catalog requests.
        while (true) {
            val observedRevision = addonRevision.value
            // Read completion BEFORE the addon snapshot: a manifest finishing
            // during scheduling must trigger another pass, not be skipped.
            val preparationComplete = addonsPrepared
            engine.activeStremioAddons().forEach { extension ->
                if (scheduled.add(extension.descriptor.id)) {
                    jobs += launch {
                        val rows = engine.loadCatalogRows(
                            forceRefresh = true,
                            catalogOrder = content.catalogOrder(),
                            disabledCatalogKeys = content.disabledCatalogKeys(),
                            extensionIds = setOf(extension.descriptor.id),
                            updateHomeCache = false,
                            catalogLoadGate = homeLoadGate,
                            onPartial = ::publish,
                        )
                        publish(rows)
                    }
                }
            }
            if (preparationComplete) break
            addonRevision.first { it != observedRevision }
        }
        jobs.joinAll()
        val fresh = synchronized(freshRows) { freshRows.values.toList() }
        if (fresh.isNotEmpty()) {
            val activeIds = engine.activeStremioAddons().map { it.descriptor.id }
            val fallback = staleCached.filter { row ->
                (cachedConfiguration == null || cachedConfiguration == configurationKey ||
                    activeIds.any { row.id.startsWith("$it:") }) && fresh.none { it.id == row.id }
            }
            val result = applyCatalogPreferences(fresh + fallback)
            content.reconcileCatalogOrder(result.map { it.id })
            CatalogDiscoveryCache.putHome(result)
            CatalogDiscoveryCache.persistHome(appContext, result)
            homeCachePrefs.edit().putString("configuration", homeConfigurationKey()).apply()
            traceHome("catalogs_finished", result.size)
            result
        } else {
            traceHome("catalogs_finished_using_fallback", staleCached.size)
            applyCatalogPreferences(staleCached)
        }
    }

    suspend fun refreshAddons(pruneRemoved: Boolean = false): TvAddonRefreshSummary {
        addonLoadMutex.lock()
        addonsPrepared = false
        try {
            if (pruneRemoved) {
                val configured = content.manifestUrls().map { it.trim() }.toSet()
                engine.stremioAddons()
                    .filter { it.descriptor.baseUrl.trim() !in configured }
                    .forEach { engine.uninstall(it.descriptor.id) }
            }
            val summary = installConfiguredAddons()
            CatalogDiscoveryCache.clearAll(appContext)
            return summary
        } finally {
            addonsPrepared = true
            signalAddonChange()
            addonLoadMutex.unlock()
        }
    }

    private suspend fun installConfiguredAddons(): TvAddonRefreshSummary =
        coroutineScope {
            val results = content.manifestUrls().map { manifestUrl ->
                async {
                    val result = runCatching {
                        require(manifestUrl.startsWith("https://"))
                        StremioAddonExtension.fromManifestUrlWithRetry(manifestUrl)
                    }
                    result.onSuccess { extension ->
                        engine.stremioAddons()
                            .filter { it.descriptor.baseUrl.trim() == manifestUrl.trim() && it.descriptor.id != extension.descriptor.id }
                            .forEach { engine.uninstall(it.descriptor.id) }
                        engine.install(extension)
                        engine.setExtensionEnabled(extension.descriptor.id, content.isAddonEnabled(manifestUrl))
                        traceHome("manifest_ready")
                        signalAddonChange()
                    }
                    result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
                    result
                }
            }.awaitAll()
            TvAddonRefreshSummary(
                refreshed = results.count { it.isSuccess },
                failed = results.count { it.isFailure },
            )
        }

    suspend fun reloadPersistentConfiguration() {
        content.seedDevelopmentDefaultsIfNeeded()
        pluginStore.seedDevelopmentDefaultsIfNeeded()
        refreshAddons(pruneRemoved = true)
        providerSync.syncMissing(pluginStore.repositories())
        profileStore.ensureDefaultProfile()
        SourceDiscoveryCache.clearAll()
    }

    suspend fun addAddon(manifestUrl: String) {
        val normalized = manifestUrl.trim()
        require(normalized.startsWith("https://")) {
            "VUEO requires an HTTPS addon manifest URL."
        }
        val extension = StremioAddonExtension.fromManifestUrl(normalized)
        content.add(extension.descriptor.baseUrl)
        engine.install(extension)
        engine.setExtensionEnabled(extension.descriptor.id, true)
        CatalogDiscoveryCache.clearAll(appContext)
    }

    suspend fun removeAddon(manifestUrl: String) {
        val normalized = manifestUrl.trim()
        engine.stremioAddons()
            .firstOrNull { it.descriptor.baseUrl == normalized }
            ?.let { engine.uninstall(it.descriptor.id) }
        content.remove(normalized)
        CatalogDiscoveryCache.clearAll(appContext)
    }

    suspend fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        val normalized = manifestUrl.trim()
        content.setAddonEnabled(normalized, enabled)
        engine.stremioAddons()
            .firstOrNull { it.descriptor.baseUrl == normalized }
            ?.let { engine.setExtensionEnabled(it.descriptor.id, enabled) }
        CatalogDiscoveryCache.clearAll(appContext)
    }

    suspend fun addPluginRepository(inputUrl: String): PluginRepositoryDescriptor {
        val result = pluginRepositoryManager.installOrRefresh(
            inputUrl = inputUrl,
            forceCodeRefresh = true,
        )
        pluginStore.setRepositoryEnabled(result.repository, true)
        return result.repository
    }

    suspend fun refreshPluginRepositories(): PluginRepositoryRefreshSummary =
        pluginRepositoryManager.refreshInstalled(forceCodeRefresh = true)

    suspend fun refreshPluginRepository(
        repository: PluginRepositoryDescriptor,
    ): PluginRepositoryDescriptor =
        pluginRepositoryManager.installOrRefresh(
            inputUrl = repository.manifestUrl,
            forceCodeRefresh = true,
        ).repository

    suspend fun removePluginRepository(repository: PluginRepositoryDescriptor) {
        pluginHealthStore.removeRepository(repository.manifestUrl)
        pluginStore.remove(repository.manifestUrl)
    }

    suspend fun discover(
        item: MediaItem,
        episode: EpisodeItem?,
        forceRefresh: Boolean = false,
        onProgress: (String) -> Unit = {},
        onUpdate: (TvSourceDiscoverySnapshot) -> Unit = {},
    ): TvSourceBundle {
        val videoId = if (MediaTypePolicy.isSeries(item.type)) {
            episode?.id ?: item.id
        } else {
            item.id
        }
        return sourceDiscoveryEngine.discover(
            request = SourceDiscoveryRequest(
                item = item,
                episode = episode,
                videoId = videoId,
                preferredQuality = settingsStore.preferredQuality().rankKey,
                forceRefresh = forceRefresh,
            ),
        ) { snapshot ->
            onProgress(snapshot.progress)
            onUpdate(snapshot)
        }
    }

    fun localRelatedTitles(item: MediaItem): List<MediaItem> =
        RelatedContentOrchestrator.local(
            item = item,
            limit = 18,
        )

    suspend fun relatedTitles(
        item: MediaItem,
        localItems: List<MediaItem> = localRelatedTitles(item),
    ): List<MediaItem> =
        RelatedContentOrchestrator.mergeRemote(
            item = item,
            localItems = localItems,
            apiKey = pluginStore.tmdbApiKey(),
            recommendationsEnabled = settingsStore.tmdbRecommendationsEnabled(),
            similarEnabled = settingsStore.tmdbSimilarTitlesEnabled(),
            limit = 18,
        )

    suspend fun ratings(item: MediaItem): List<MediaRating> {
        if (!settingsStore.mdblistRatingsEnabled() || settingsStore.mdblistApiKey().isBlank()) {
            return emptyList()
        }
        return MdblistClient.ratings(item, settingsStore.mdblistApiKey())
            .filter { rating ->
                when (rating.source) {
                    "imdb" -> settingsStore.mdblistImdbEnabled()
                    "tomatoes" -> settingsStore.mdblistRottenTomatoesEnabled()
                    "metacritic" -> settingsStore.mdblistMetacriticEnabled()
                    "tmdb" -> settingsStore.mdblistTmdbRatingEnabled()
                    "trakt" -> settingsStore.mdblistTraktEnabled()
                    else -> false
                }
            }
    }


    fun dnaMatch(item: MediaItem): Int? {
        val profileId = profileStore.activeProfileId()
        if (!dnaPreferences.shouldShowDnaMatch(profileId)) return null
        return dnaEngine.matchPercent(item)
    }

    private fun applyCatalogPreferences(rows: List<CatalogRow>): List<CatalogRow> =
        HomeCatalogPolicy.orderRows(
            rows = rows,
            catalogOrder = content.catalogOrder(),
            disabledCatalogKeys = content.disabledCatalogKeys(),
        )

}

data class TvAddonRefreshSummary(
    val refreshed: Int,
    val failed: Int,
)

typealias TvSourceDiscoverySnapshot = com.vueo.shared.core.source.SourceDiscoverySnapshot
typealias TvSourceBundle = com.vueo.shared.core.source.SourceDiscoveryBundle
