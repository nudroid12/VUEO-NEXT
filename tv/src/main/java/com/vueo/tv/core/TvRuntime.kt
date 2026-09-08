package com.vueo.tv.core

import android.content.Context
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.StremioAddonExtension
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.enrichment.MetadataEnhancementEngine
import com.vueo.shared.core.enrichment.MetadataEnhancementOptions
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.enrichment.GeminiClient
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.PluginRepositoryClient
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.ProviderCodeSyncManager
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

    suspend fun boot() {
        profileStore.ensureDefaultProfile()
        CatalogDiscoveryCache.restoreHome(appContext)
        content.seedDevelopmentDefaultsIfNeeded()

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

    suspend fun prepareProvidersInBackground() {
        runCatching { pluginStore.seedDevelopmentDefaultsIfNeeded() }
        runCatching { providerSync.syncMissing(pluginStore.repositories()) }
    }

    suspend fun homeRows(forceRefresh: Boolean = false): List<CatalogRow> {
        val freshCached =
            CatalogDiscoveryCache.home(allowStale = false)
                .orEmpty()

        if (!forceRefresh && freshCached.isNotEmpty()) {
            return applyCatalogPreferences(freshCached)
                .let(::applyPersonalization)
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
            .let(::applyPersonalization)
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
        engine.installed().map { it.descriptor.id }.forEach(engine::uninstall)
        content.manifestUrls().forEach { manifestUrl ->
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
        CatalogDiscoveryCache.clearAll(appContext)
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
        require(manifestUrl.trim().startsWith("https://")) {
            "VUEO requires an HTTPS addon manifest URL."
        }
        content.add(manifestUrl)
        refreshAddons()
    }

    suspend fun removeAddon(manifestUrl: String) {
        content.remove(manifestUrl)
        refreshAddons()
    }

    suspend fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        content.setAddonEnabled(manifestUrl, enabled)
        refreshAddons()
    }

    suspend fun addPluginRepository(inputUrl: String): PluginRepositoryDescriptor {
        val repository = PluginRepositoryClient.fetch(inputUrl)
        pluginStore.upsert(repository)
        pluginStore.setRepositoryEnabled(repository, true)
        providerSync.syncRepository(repository, force = true)
        return repository
    }

    suspend fun removePluginRepository(repository: PluginRepositoryDescriptor) {
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

    suspend fun relatedTitles(item: MediaItem): List<MediaItem> =
        TmdbEnhancementClient.moreLikeThis(
            item = item,
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

    suspend fun geminiInsight(item: MediaItem): String? {
        val apiKey = settingsStore.geminiApiKey()
        if (!settingsStore.geminiInsightsEnabled() || apiKey.isBlank()) return null
        val profileId = profileStore.activeProfileId()
        val dna = dnaEngine.build().takeIf { dnaPreferences.userDnaEnabled(profileId) }
        val match = dna?.let { dnaEngine.matchPercent(item, it) }
        return runCatching {
            GeminiClient.titleInsight(
                media = item,
                dna = dna,
                dnaMatchPercent = match,
                apiKey = apiKey,
            )
        }.getOrNull()
    }

    fun dnaMatch(item: MediaItem): Int? {
        val profileId = profileStore.activeProfileId()
        if (!dnaPreferences.shouldShowDnaMatch(profileId)) return null
        return dnaEngine.matchPercent(item)
    }

    private fun applyPersonalization(rows: List<CatalogRow>): List<CatalogRow> {
        val profileId = profileStore.activeProfileId()
        if (!dnaPreferences.shouldPersonalizeRecommendations(profileId)) return rows
        val dna = dnaEngine.build()
        return rows.map { row ->
            row.copy(
                items = row.items
                    .withIndex()
                    .sortedWith(
                        compareByDescending<IndexedValue<MediaItem>> { indexed ->
                            dnaEngine.matchPercent(indexed.value, dna) ?: -1
                        }.thenBy { it.index }
                    )
                    .map { it.value }
            )
        }
    }

    private fun applyCatalogPreferences(rows: List<CatalogRow>): List<CatalogRow> {
        if (rows.isEmpty()) return rows
        val disabled = content.disabledCatalogKeys()
        val order = content.catalogOrder().withIndex().associate { it.value to it.index }
        return rows
            .filterNot { it.id in disabled }
            .sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

}

typealias TvSourceDiscoverySnapshot = com.vueo.shared.core.source.SourceDiscoverySnapshot
typealias TvSourceBundle = com.vueo.shared.core.source.SourceDiscoveryBundle
