package com.vueo.shared.core.source

import com.vueo.shared.core.extensions.SourceCleaner
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.PluginDiscoveryResult
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.TmdbResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Shared progressive source-discovery orchestration for Mobile and TV.
 * UI/player lifecycle remains owned by each app; this class owns only the
 * deterministic cache/addon/plugin/subtitle merge and ranking pipeline.
 */
class SourceDiscoveryEngine(
    private val mediaEngine: UnifiedMediaEngine,
    private val pluginEngine: PluginSourceEngine,
    private val pluginStore: PluginStore,
) {
    suspend fun discover(
        request: SourceDiscoveryRequest,
        onUpdate: (SourceDiscoverySnapshot) -> Unit = {},
    ): SourceDiscoveryBundle = coroutineScope {
        val item = request.item
        val videoId = request.videoId
        val preferredQuality = request.preferredQuality
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
        var subtitlesResolved = false
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
        var latestProgress = if (cached != null) {
            "Recent sources loaded instantly • refreshing in background"
        } else {
            "Starting source discovery…"
        }
        var providerOrder = cachedStreams
            .asSequence()
            .filter { it.isDirectPlayable }
            .map(::providerKey)
            .distinct()
            .toList()

        fun elapsedMs(): Long =
            (System.nanoTime() - startedAtNs) / 1_000_000L

        fun recordProviders(candidates: List<StreamSource>) {
            val next = providerOrder.toMutableList()
            candidates
                .asSequence()
                .filter { it.isDirectPlayable }
                .map(::providerKey)
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
            onUpdate(
                SourceDiscoverySnapshot(
                    bundle = SourceDiscoveryBundle(
                        videoId = videoId,
                        sources = display,
                        subtitles = subtitles,
                    ),
                    rawCount = rawCount,
                    notice = notice,
                    searching = searching,
                    progress = progress,
                    firstResultMs = firstResultMs,
                    providerOrder = providerOrder,
                    fromCache = cachedStreams.isNotEmpty(),
                    subtitlesResolved = subtitlesResolved,
                )
            )
        }

        publish(latestProgress, streams = cachedStreams)

        val subtitlesDeferred = async {
            try {
                mediaEngine.resolveSubtitles(item.type, videoId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }

        val subtitlesUpdateDeferred = async {
            subtitles = subtitlesDeferred.await()
            subtitlesResolved = true
            publish(latestProgress)
        }

        val addonsDeferred = async {
            try {
                mediaEngine.resolveStreamsProgressive(
                    type = item.type,
                    videoId = videoId,
                ) { progress ->
                    freshAddonStreams = progress.streams
                    addonRawCount = progress.rawCount
                    addonCompleted = progress.completedAddons
                    addonTotal = progress.totalAddons
                    publish(
                        progressLabel(
                            addonCompleted = addonCompleted,
                            addonTotal = addonTotal,
                            pluginCompleted = pluginCompleted,
                            pluginTotal = pluginTotal,
                            found = cleanFresh().size,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }

        val pluginsDeferred = async {
            discoverPlugins(
                request = request,
                onSkipped = { skippedNotice ->
                    notice = skippedNotice
                    publish(
                        progressLabel(
                            addonCompleted = addonCompleted,
                            addonTotal = addonTotal,
                            pluginCompleted = pluginCompleted,
                            pluginTotal = pluginTotal,
                            found = cleanFresh().size,
                        )
                    )
                },
                onProgress = { progressResult, completed, total ->
                    freshPluginStreams = progressResult.streams.map { it.toStreamSource() }
                    pluginRawCount = progressResult.streams.size
                    pluginCompleted = completed
                    pluginTotal = total
                    publish(
                        progressLabel(
                            addonCompleted = addonCompleted,
                            addonTotal = addonTotal,
                            pluginCompleted = pluginCompleted,
                            pluginTotal = pluginTotal,
                            found = cleanFresh().size,
                        )
                    )
                },
            )
        }

        freshAddonStreams = addonsDeferred.await()
        val pluginResult = pluginsDeferred.await()
        subtitlesUpdateDeferred.await()

        if (pluginResult != null) {
            freshPluginStreams = pluginResult.streams.map { it.toStreamSource() }
            pluginRawCount = pluginResult.streams.size
            notice = pluginSummary(pluginResult)
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
        val finalBundle = SourceDiscoveryBundle(
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

        onUpdate(
            SourceDiscoverySnapshot(
                bundle = finalBundle,
                rawCount = rawCount,
                notice = notice,
                searching = false,
                progress = finalProgress,
                firstResultMs = firstResultMs,
                providerOrder = providerOrder,
                fromCache = cachedStreams.isNotEmpty(),
                subtitlesResolved = true,
            )
        )
        finalBundle
    }

    private suspend fun discoverPlugins(
        request: SourceDiscoveryRequest,
        onSkipped: (String) -> Unit,
        onProgress: suspend (PluginDiscoveryResult, Int, Int) -> Unit,
    ): PluginDiscoveryResult? {
        if (!pluginStore.pluginsEnabled() || pluginStore.repositories().isEmpty()) {
            return null
        }

        val item = request.item
        val tmdbId = try {
            TmdbResolver.resolve(
                rawId = item.id,
                mediaType = item.type,
                apiKey = pluginStore.tmdbApiKey(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        if (tmdbId == null) {
            onSkipped(
                "Plugin providers skipped: VUEO could not resolve a TMDB ID. " +
                    "Add your TMDB API key in Settings > Enhancements > TMDB."
            )
            return null
        }

        return try {
            pluginEngine.discoverProgressive(
                tmdbId = tmdbId,
                mediaType = MediaTypePolicy.pluginType(item.type),
                season = request.episode?.season,
                episode = request.episode?.episode,
                mediaTitle = item.name,
                mediaOriginalTitle = item.originalTitle,
                mediaAliases = item.aliases,
                mediaYear = item.releaseInfo,
                mediaExternalId = item.id,
                mediaOriginalLanguage = item.originalLanguage,
            ) { progress ->
                onProgress(
                    progress.result,
                    progress.completedProviders,
                    progress.totalProviders,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private fun pluginSummary(result: PluginDiscoveryResult): String =
        "Plugins: ${result.attemptedProviders} checked • " +
            "${result.successfulProviders} online • " +
            "${result.slowProviders} slow • " +
            "${result.noResultProviders} no results • " +
            "${result.needsSetupProviders} setup • " +
            "${result.unavailableProviders} unavailable • " +
            "${result.blockedProviders} blocked • " +
            "${result.timeoutProviders} timeout • " +
            "${result.failedProviders} failed."

    private fun progressLabel(
        addonCompleted: Int,
        addonTotal: Int,
        pluginCompleted: Int,
        pluginTotal: Int,
        found: Int,
    ): String = buildString {
        append("Searching")
        if (addonTotal > 0) append(" • Addons $addonCompleted/$addonTotal")
        if (pluginTotal > 0) append(" • Providers $pluginCompleted/$pluginTotal")
        if (found > 0) append(" • $found found")
    }

    private fun providerKey(source: StreamSource): String =
        source.providerName.trim().ifBlank { "Other" }
}

data class SourceDiscoveryRequest(
    val item: MediaItem,
    val episode: EpisodeItem?,
    val videoId: String,
    val preferredQuality: String? = null,
)

data class SourceDiscoverySnapshot(
    val bundle: SourceDiscoveryBundle,
    val rawCount: Int,
    val notice: String?,
    val searching: Boolean,
    val progress: String,
    val firstResultMs: Long?,
    val providerOrder: List<String>,
    val fromCache: Boolean,
    val subtitlesResolved: Boolean,
)

data class SourceDiscoveryBundle(
    val videoId: String,
    val sources: List<StreamSource>,
    val subtitles: List<SubtitleTrack>,
)
