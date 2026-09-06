package com.vueo.tv.core

import android.content.Context
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.SourceCleaner
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
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.PluginRepositoryClient
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.ProviderCodeSyncManager
import com.vueo.shared.core.plugin.TmdbResolver
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceDiscoveryCache
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
        val cached =
            CatalogDiscoveryCache.home(allowStale = true)
                .orEmpty()
                .let(::applyCatalogPreferences)
                .let(::applyPersonalization)

        if (!forceRefresh && cached.isNotEmpty()) return cached

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
        return applyCatalogPreferences(fresh.ifEmpty { cached })
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
    ): TvSourceBundle = coroutineScope {
        val videoId = if (item.type.lowercase() in setOf("series", "tv")) {
            episode?.id ?: item.id
        } else {
            item.id
        }
        val preferredQuality = settingsStore.preferredQuality().rankKey
        val cacheKey = SourceDiscoveryCache.key(
            mediaType = item.type,
            mediaId = item.id,
            videoId = videoId,
        )
        val cached = SourceDiscoveryCache.get(cacheKey)
        val cachedStreams = SourceCleaner.clean(
            sources = cached?.sources.orEmpty().map { it.toStreamSource() },
            preferredQuality = preferredQuality,
            originalLanguage = item.originalLanguage,
        )

        val startedAtNs = System.nanoTime()
        var subtitles = emptyList<SubtitleTrack>()
        var freshAddonStreams = emptyList<StreamSource>()
        var freshPluginStreams = emptyList<StreamSource>()
        var addonRawCount = 0
        var pluginRawCount = 0
        var addonCompleted = 0
        var addonTotal = 0
        var pluginCompleted = 0
        var pluginTotal = 0
        var notice = cached?.notice
        var firstResultMs: Long? = null
        var searching = true
        var latestProgress =
            if (cached != null) {
                "Recent sources loaded instantly • refreshing in background"
            } else {
                "Starting source discovery…"
            }
        var providerOrder =
            cachedStreams
                .asSequence()
                .filter { it.isDirectPlayable }
                .map(::sourceProviderKey)
                .distinct()
                .toList()

        fun elapsedMs(): Long =
            (System.nanoTime() - startedAtNs) / 1_000_000L

        fun recordProviders(candidates: List<StreamSource>) {
            val next = providerOrder.toMutableList()
            candidates
                .asSequence()
                .filter { it.isDirectPlayable }
                .map(::sourceProviderKey)
                .distinct()
                .forEach { provider ->
                    if (provider !in next) next += provider
                }
            providerOrder = next
        }

        fun cleanFresh(): List<StreamSource> =
            SourceCleaner.clean(
                sources = freshAddonStreams + freshPluginStreams,
                preferredQuality = preferredQuality,
                originalLanguage = item.originalLanguage,
            )

        fun publish(
            progress: String,
            streams: List<StreamSource>? = null,
        ) {
            latestProgress = progress
            val fresh = cleanFresh()
            val display = streams ?: if (searching) {
                SourceCleaner.clean(
                    sources = cachedStreams + fresh,
                    preferredQuality = preferredQuality,
                    originalLanguage = item.originalLanguage,
                )
            } else {
                fresh
            }

            recordProviders(display)
            if (
                firstResultMs == null &&
                cachedStreams.isEmpty() &&
                display.isNotEmpty()
            ) {
                firstResultMs = elapsedMs()
            }

            val rawCount = maxOf(
                cached?.rawCount ?: 0,
                addonRawCount + pluginRawCount,
            )
            val bundle = TvSourceBundle(
                videoId = videoId,
                sources = display,
                subtitles = subtitles,
            )
            onProgress(progress)
            onUpdate(
                TvSourceDiscoverySnapshot(
                    bundle = bundle,
                    rawCount = rawCount,
                    notice = notice,
                    searching = searching,
                    progress = progress,
                    firstResultMs = firstResultMs,
                    providerOrder = providerOrder,
                    fromCache = cachedStreams.isNotEmpty(),
                )
            )
        }

        publish(
            latestProgress,
            streams = cachedStreams,
        )

        val subtitlesDeferred = async {
            runCatching {
                engine.resolveSubtitles(item.type, videoId)
            }.getOrDefault(emptyList())
        }

        val subtitlesUpdateDeferred = async {
            subtitles = subtitlesDeferred.await()
            publish(latestProgress)
        }

        val addonsDeferred = async {
            runCatching {
                engine.resolveStreamsProgressive(
                    type = item.type,
                    videoId = videoId,
                ) { progress ->
                    freshAddonStreams = progress.streams
                    addonRawCount = progress.rawCount
                    addonCompleted = progress.completedAddons
                    addonTotal = progress.totalAddons
                    publish(
                        progressLabel(
                            addonCompleted,
                            addonTotal,
                            pluginCompleted,
                            pluginTotal,
                            cleanFresh().size,
                        )
                    )
                }
            }.getOrDefault(emptyList())
        }

        val pluginsDeferred = async {
            if (!pluginStore.pluginsEnabled() || pluginStore.repositories().isEmpty()) {
                return@async null
            }

            val tmdbId = runCatching {
                TmdbResolver.resolve(
                    rawId = item.id,
                    mediaType = item.type,
                    apiKey = pluginStore.tmdbApiKey(),
                )
            }.getOrNull()

            if (tmdbId == null) {
                notice =
                    "Plugin providers skipped: VUEO could not resolve a TMDB ID. " +
                        "Add your TMDB API key in Settings > Enhancements > TMDB."
                publish(
                    progressLabel(
                        addonCompleted,
                        addonTotal,
                        pluginCompleted,
                        pluginTotal,
                        cleanFresh().size,
                    )
                )
                return@async null
            }

            val mediaType =
                if (item.type.lowercase() in setOf("series", "tv")) "tv" else "movie"

            runCatching {
                pluginEngine.discoverProgressive(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = episode?.season,
                    episode = episode?.episode,
                ) { progress ->
                    freshPluginStreams = progress.result.streams.map { it.toStreamSource() }
                    pluginRawCount = progress.result.streams.size
                    pluginCompleted = progress.completedProviders
                    pluginTotal = progress.totalProviders
                    publish(
                        progressLabel(
                            addonCompleted,
                            addonTotal,
                            pluginCompleted,
                            pluginTotal,
                            cleanFresh().size,
                        )
                    )
                }
            }.getOrNull()
        }

        freshAddonStreams = addonsDeferred.await()
        val pluginResult = pluginsDeferred.await()
        subtitlesUpdateDeferred.await()

        if (pluginResult != null) {
            freshPluginStreams = pluginResult.streams.map { it.toStreamSource() }
            pluginRawCount = pluginResult.streams.size
            notice =
                "Plugins: ${pluginResult.attemptedProviders} checked • " +
                    "${pluginResult.successfulProviders} online • " +
                    "${pluginResult.slowProviders} slow • " +
                    "${pluginResult.noResultProviders} no results • " +
                    "${pluginResult.needsSetupProviders} setup • " +
                    "${pluginResult.unavailableProviders} unavailable • " +
                    "${pluginResult.blockedProviders} blocked • " +
                    "${pluginResult.timeoutProviders} timeout • " +
                    "${pluginResult.failedProviders} failed."
        }

        val freshFinal = cleanFresh()
        val finalStreams = freshFinal.ifEmpty { cachedStreams }
        searching = false
        recordProviders(finalStreams)

        val rawCount = maxOf(
            cached?.rawCount ?: 0,
            addonRawCount + pluginRawCount,
        )
        val finalProgress = if (finalStreams.isEmpty()) {
            "Search complete • no sources found"
        } else {
            "Search complete • ${finalStreams.size} unique sources"
        }
        val finalBundle = TvSourceBundle(
            videoId = videoId,
            sources = finalStreams,
            subtitles = subtitles,
        )

        if (finalStreams.isNotEmpty()) {
            SourceDiscoveryCache.put(
                key = cacheKey,
                sources = finalStreams.map { it.toSourceCandidate() },
                rawCount = rawCount,
                notice = notice,
            )
        }

        onProgress(finalProgress)
        onUpdate(
            TvSourceDiscoverySnapshot(
                bundle = finalBundle,
                rawCount = rawCount,
                notice = notice,
                searching = false,
                progress = finalProgress,
                firstResultMs = firstResultMs,
                providerOrder = providerOrder,
                fromCache = cachedStreams.isNotEmpty(),
            )
        )
        finalBundle
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

    private fun progressLabel(
        addonCompleted: Int,
        addonTotal: Int,
        pluginCompleted: Int,
        pluginTotal: Int,
        found: Int,
    ): String =
        buildString {
            append("Searching")
            if (addonTotal > 0) append(" • Addons $addonCompleted/$addonTotal")
            if (pluginTotal > 0) append(" • Providers $pluginCompleted/$pluginTotal")
            if (found > 0) append(" • $found found")
        }
}

data class TvSourceDiscoverySnapshot(
    val bundle: TvSourceBundle,
    val rawCount: Int,
    val notice: String?,
    val searching: Boolean,
    val progress: String,
    val firstResultMs: Long?,
    val providerOrder: List<String>,
    val fromCache: Boolean,
)

data class TvSourceBundle(
    val videoId: String,
    val sources: List<StreamSource>,
    val subtitles: List<SubtitleTrack>,
)

private fun SourceCandidate.toStreamSource(): StreamSource =
    StreamSource(
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId,
        providerName = providerName,
    )
private fun StreamSource.toSourceCandidate(): SourceCandidate =
    SourceCandidate(
        id = buildString {
            append(providerId)
            append(':')
            append(url ?: infoHash ?: name)
            fileIndex?.let {
                append(':')
                append(it)
            }
        },
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId,
        providerName = providerName,
    )

private fun sourceProviderKey(source: StreamSource): String =
    source.providerName.trim().ifBlank { "Other" }

