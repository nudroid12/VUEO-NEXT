package com.vueo.tv.core

import android.content.Context
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.StremioAddonExtension
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.home.HomeCatalogPolicy
import com.vueo.shared.core.enrichment.MetadataEnhancementEngine
import com.vueo.shared.core.enrichment.MetadataEnhancementOptions
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
        CatalogDiscoveryCache.restoreHome(appContext)
    }

    suspend fun prepareAddonsInBackground() {
        addonLoadMutex.lock()
        try {
            installConfiguredAddons()
            addonsPrepared = true
        } finally {
            addonLoadMutex.unlock()
        }
    }

    /**
     * Once manifests are ready, expire only the in-memory freshness marker.
     * Existing rows stay available as the visual fallback while Home refreshes
     * against the fully prepared addon set.
     */
    fun requestHomeRefreshAfterAddonPreparation() {
        CatalogDiscoveryCache.invalidateHomeMemory()
    }

    suspend fun prepareProvidersInBackground() {
        runCatching { pluginStore.seedDevelopmentDefaultsIfNeeded() }
        runCatching { providerSync.syncMissing(pluginStore.repositories()) }
    }

    suspend fun homeRows(forceRefresh: Boolean = false): List<CatalogRow> {
        if (!addonsPrepared) {
            return CatalogDiscoveryCache.home(allowStale = true)
                .orEmpty()
                .let(::applyCatalogPreferences)
        }

        val freshCached =
            CatalogDiscoveryCache.home(allowStale = false)
                .orEmpty()

        if (!forceRefresh && freshCached.isNotEmpty()) {
            return applyCatalogPreferences(freshCached)
        }

        val staleCached =
            CatalogDiscoveryCache.home(allowStale = true)
                .orEmpty()

        val fresh =
            engine.loadCatalogRows(
                forceRefresh = forceRefresh,
                catalogOrder = content.catalogOrder(),
                disabledCatalogKeys = content.disabledCatalogKeys(),
            )
        if (fresh.isNotEmpty()) {
            content.reconcileCatalogOrder(fresh.map { it.id })
            CatalogDiscoveryCache.persistHome(appContext, fresh)
        }
        return applyCatalogPreferences(fresh.ifEmpty { staleCached })
    }

    suspend fun search(query: String): List<MediaItem> =
        engine.search(query = query, maxResults = 80)

    suspend fun loadMeta(item: MediaItem): MediaItem {
        val core = engine.loadMeta(item)
        val result = MetadataEnhancementEngine.enrich(
            media = core,
            options = MetadataEnhancementOptions(
                tmdbApiKey = pluginStore.tmdbApiKey(),
                mdblistApiKey = settingsStore.mdblistApiKey(),
                tmdbMetadataEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
                tmdbArtworkEnabled = settingsStore.tmdbArtworkEnrichmentEnabled(),
                richDetailsEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
                ratingsEnabled = settingsStore.mdblistRatingsEnabled(),
            ),
        )

        val imdb = result.ratings.firstOrNull { it.source == "imdb" }?.value
            ?.takeIf { settingsStore.mdblistImdbEnabled() }
        val tmdb = result.ratings.firstOrNull { it.source == "tmdb" }?.value
            ?.takeIf { settingsStore.mdblistTmdbRatingEnabled() }

        return result.media.copy(
            imdbRating = imdb ?: result.media.imdbRating,
            tmdbRating = tmdb ?: result.media.tmdbRating,
        )
    }

    suspend fun refreshAddons() {
        addonLoadMutex.lock()
        addonsPrepared = false
        try {
            engine.installed().map { it.descriptor.id }.forEach(engine::uninstall)
            installConfiguredAddons()
            CatalogDiscoveryCache.clearAll(appContext)
        } finally {
            addonsPrepared = true
            addonLoadMutex.unlock()
        }
    }

    private suspend fun installConfiguredAddons() {
        coroutineScope {
            content.manifestUrls()
                .map { manifestUrl ->
                    async {
                        runCatching {
                            require(manifestUrl.startsWith("https://"))
                            StremioAddonExtension.fromManifestUrl(manifestUrl)
                        }.onSuccess { extension ->
                            engine.install(extension)
                            engine.setExtensionEnabled(
                                id = extension.descriptor.id,
                                enabled = content.isAddonEnabled(manifestUrl),
                            )
                        }
                    }
                }
                .awaitAll()
        }
    }

    suspend fun reloadPersistentConfiguration() {
        content.seedDevelopmentDefaultsIfNeeded()
        pluginStore.seedDevelopmentDefaultsIfNeeded()
        refreshAddons()
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

    suspend fun removePluginRepository(repository: PluginRepositoryDescriptor) {
        pluginHealthStore.removeRepository(repository.manifestUrl)
        pluginStore.remove(repository.manifestUrl)
    }

    suspend fun discover(
        item: MediaItem,
        episode: EpisodeItem?,
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

typealias TvSourceDiscoverySnapshot = com.vueo.shared.core.source.SourceDiscoverySnapshot
typealias TvSourceBundle = com.vueo.shared.core.source.SourceDiscoveryBundle
