package com.vueo.tv.core

import android.content.Context
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.SourceCleaner
import com.vueo.shared.core.extensions.StremioAddonExtension
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.ProviderCodeSyncManager
import com.vueo.shared.core.plugin.TmdbResolver
import com.vueo.shared.core.source.SourceCandidate
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
    }

    suspend fun search(query: String): List<MediaItem> =
        engine.search(query = query, maxResults = 80)

    suspend fun loadMeta(item: MediaItem): MediaItem =
        engine.loadMeta(item)

    suspend fun discover(
        item: MediaItem,
        episode: EpisodeItem?,
        onProgress: (String) -> Unit = {},
    ): TvSourceBundle = coroutineScope {
        val videoId = if (item.type.lowercase() in setOf("series", "tv")) {
            episode?.id ?: item.id
        } else {
            item.id
        }

        var addonCompleted = 0
        var addonTotal = 0
        var pluginCompleted = 0
        var pluginTotal = 0

        val subtitlesDeferred = async {
            runCatching {
                engine.resolveSubtitles(item.type, videoId)
            }.getOrDefault(emptyList())
        }

        val addonsDeferred = async {
            runCatching {
                engine.resolveStreamsProgressive(
                    type = item.type,
                    videoId = videoId,
                ) { progress ->
                    addonCompleted = progress.completedAddons
                    addonTotal = progress.totalAddons
                    onProgress(
                        progressLabel(
                            addonCompleted,
                            addonTotal,
                            pluginCompleted,
                            pluginTotal,
                            progress.streams.size,
                        )
                    )
                }
            }.getOrDefault(emptyList())
        }

        val pluginsDeferred = async {
            if (!pluginStore.pluginsEnabled() || pluginStore.repositories().isEmpty()) {
                return@async emptyList<StreamSource>()
            }

            val tmdbId =
                runCatching {
                    TmdbResolver.resolve(
                        rawId = item.id,
                        mediaType = item.type,
                        apiKey = pluginStore.tmdbApiKey(),
                    )
                }.getOrNull()
                    ?: return@async emptyList<StreamSource>()

            val mediaType =
                if (item.type.lowercase() in setOf("series", "tv")) "tv" else "movie"

            runCatching {
                pluginEngine.discoverProgressive(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = episode?.season,
                    episode = episode?.episode,
                ) { progress ->
                    pluginCompleted = progress.completedProviders
                    pluginTotal = progress.totalProviders
                    onProgress(
                        progressLabel(
                            addonCompleted,
                            addonTotal,
                            pluginCompleted,
                            pluginTotal,
                            progress.result.streams.size,
                        )
                    )
                }.streams.map { it.toStreamSource() }
            }.getOrDefault(emptyList())
        }

        val addonStreams = addonsDeferred.await()
        val pluginStreams = pluginsDeferred.await()
        val subtitles = subtitlesDeferred.await()

        val ranked =
            SourceCleaner.clean(
                sources = addonStreams + pluginStreams,
                preferredQuality = settingsStore.preferredQuality().rankKey,
                originalLanguage = item.originalLanguage,
            )

        TvSourceBundle(
            videoId = videoId,
            sources = ranked,
            subtitles = subtitles,
        )
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
