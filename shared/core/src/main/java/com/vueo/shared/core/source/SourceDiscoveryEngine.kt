package com.vueo.shared.core.source

import com.vueo.shared.core.extensions.SourceCleaner
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.extensions.AddonRequestActivity
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
import java.util.concurrent.CopyOnWriteArrayList

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
        val activityLog = CopyOnWriteArrayList<SourceDiscoveryActivity>()
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

        fun activity(
            category: String,
            level: String = "info",
            message: String,
        ) {
            activityLog += SourceDiscoveryActivity(
                elapsedMs = elapsedMs(),
                category = category,
                level = level,
                message = sanitizeActivityText(message),
            )
            if (activityLog.size > MAX_ACTIVITY_ENTRIES) {
                activityLog.removeAt(0)
            }
        }

        fun recordAddonActivity(event: AddonRequestActivity) {
            val category = if (event.resource == "subtitles") "subtitles" else "requests"
            val level = when (event.phase) {
                "timeout", "failed" -> "error"
                "retrying" -> "warning"
                else -> "info"
            }
            val message = when (event.phase) {
                "started" -> "${event.providerName} → Requesting ${event.resource}"
                "retrying" -> "${event.providerName} → Retrying subtitle request"
                "timeout" -> "${event.providerName} → ${event.resource.replaceFirstChar { it.uppercase() }} timed out after ${event.elapsedMs} ms"
                "failed" -> "${event.providerName} → ${event.resource.replaceFirstChar { it.uppercase() }} failed (${event.errorType ?: "unknown error"})"
                else -> "${event.providerName} ← ${event.resultCount} ${event.resource} received in ${event.elapsedMs} ms"
            }
            activity(category, level, message)
        }

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
                fresh.ifEmpty { cachedStreams }
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
                    activityLog = activityLog.toList(),
                )
            )
        }

        activity(
            category = "system",
            message = if (request.forceRefresh) {
                "Source discovery started • manual refresh"
            } else {
                "Source discovery started • video ${maskedVideoId(videoId)}"
            },
        )
        if (cachedStreams.isNotEmpty()) {
            activity("sources", message = "Cache → ${cachedStreams.size} recent sources loaded")
        }
        publish(latestProgress, streams = cachedStreams)

        val subtitlesUpdateDeferred = async {
            activity("subtitles", message = "Subtitle discovery started in parallel")
            publish(latestProgress)
            try {
                subtitles = mediaEngine.resolveSubtitles(
                    type = item.type,
                    videoId = videoId,
                    onProgress = { discovered ->
                        subtitles = discovered
                        activity(
                            "subtitles",
                            message = "Subtitle merge → ${discovered.size} unique tracks available",
                        )
                        publish(latestProgress)
                    },
                    onInitialPassComplete = {
                        subtitlesResolved = true
                        publish(latestProgress)
                    },
                    onActivity = ::recordAddonActivity,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                subtitles = emptyList()
                activity(
                    "subtitles",
                    level = "error",
                    message = "Subtitle discovery failed before completion",
                )
            } finally {
                if (!subtitlesResolved) {
                    subtitlesResolved = true
                    publish(latestProgress)
                }
                activity(
                    "subtitles",
                    message = "Subtitle discovery completed • ${subtitles.size} tracks",
                )
                publish(latestProgress)
            }
        }

        val addonsDeferred = async {
            activity("requests", message = "Addon source requests started")
            publish(latestProgress)
            try {
                mediaEngine.resolveStreamsProgressive(
                    type = item.type,
                    videoId = videoId,
                    onActivity = { event ->
                        recordAddonActivity(event)
                        publish(latestProgress)
                    },
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
                activity(
                    "errors",
                    level = "error",
                    message = "Addon source discovery failed before completion",
                )
                publish(latestProgress)
                emptyList()
            }
        }

        val pluginsDeferred = async {
            activity(
                "requests",
                message = buildString {
                    append("Plugin request → ")
                    append(item.type)
                    request.episode?.let { episode ->
                        append(" • S${episode.season} E${episode.episode}")
                    }
                    append(" • ")
                    append(item.name)
                },
            )
            publish(latestProgress)
            var loggedPluginDiagnostics = 0
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
                    progressResult.diagnostics
                        .drop(loggedPluginDiagnostics)
                        .forEach { diagnostic ->
                            val failed = diagnostic.status.name in setOf(
                                "TIMEOUT", "FAILED", "BLOCKED", "UNAVAILABLE"
                            )
                            activity(
                                category = if (failed) "errors" else "requests",
                                level = if (failed) "error" else "info",
                                message = buildString {
                                    append(diagnostic.providerName)
                                    append(" ← ")
                                    append(diagnostic.status.name.lowercase().replace('_', ' '))
                                    append(" • ")
                                    append(diagnostic.streamCount)
                                    append(" sources • ")
                                    append(diagnostic.responseMs)
                                    append(" ms")
                                    diagnostic.errorType?.let { append(" • $it") }
                                },
                            )
                        }
                    loggedPluginDiagnostics = progressResult.diagnostics.size
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
                forceRefresh = request.forceRefresh,
            )
        }

        freshAddonStreams = addonsDeferred.await()
        val pluginResult = pluginsDeferred.await()

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
        val removedCount = (rawCount - finalStreams.size).coerceAtLeast(0)
        activity(
            category = "sources",
            message = "Validation → $rawCount raw • ${finalStreams.size} unique • $removedCount removed",
        )
        finalStreams.firstOrNull()?.let { recommended ->
            activity(
                category = "sources",
                message = "Ranking → ${recommended.providerName} selected as current top source",
            )
        }
        activity(
            category = "system",
            level = if (finalStreams.isEmpty()) "warning" else "info",
            message = finalProgress,
        )
        latestProgress = finalProgress
        var finalBundle = SourceDiscoveryBundle(
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
                subtitlesResolved = subtitlesResolved,
                activityLog = activityLog.toList(),
            )
        )

        // Streams are complete independently from subtitle addons. Publish the
        // playable result now; slow subtitle retries may continue and merge into
        // the active player without holding source selection or next-episode play.
        subtitlesUpdateDeferred.await()
        finalBundle = finalBundle.copy(subtitles = subtitles)
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
                activityLog = activityLog.toList(),
            )
        )
        finalBundle
    }

    private suspend fun discoverPlugins(
        request: SourceDiscoveryRequest,
        onSkipped: (String) -> Unit,
        onProgress: suspend (PluginDiscoveryResult, Int, Int) -> Unit,
        forceRefresh: Boolean,
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
                forceRefresh = forceRefresh,
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
    val forceRefresh: Boolean = false,
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
    val activityLog: List<SourceDiscoveryActivity> = emptyList(),
)

data class SourceDiscoveryActivity(
    val elapsedMs: Long,
    val category: String,
    val level: String,
    val message: String,
)

data class SourceDiscoveryBundle(
    val videoId: String,
    val sources: List<StreamSource>,
    val subtitles: List<SubtitleTrack>,
)

private const val MAX_ACTIVITY_ENTRIES = 180

private fun maskedVideoId(value: String): String =
    value.trim().let { clean ->
        when {
            clean.length <= 18 -> clean
            else -> clean.take(8) + "…" + clean.takeLast(6)
        }
    }

private fun sanitizeActivityText(value: String): String =
    value
        .replace(
            Regex("(?i)(token|api[_-]?key|authorization|cookie)=([^&\\s]+)")
        ) { match ->
            "${match.groupValues[1]}=•••"
        }
        .replace(Regex("https?://[^\\s]+"), "[request URL hidden]")
        .take(280)
