package com.vueo.tv.player

import android.net.Uri
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.stremio.StremioAddonClient
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.data.TvMediaItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class TvSourceEngine(
    private val contentStore: TvContentManagerStore,
) {
    private val cache = LinkedHashMap<String, CachedSources>()
    private val subtitleCache = LinkedHashMap<String, CachedSubtitles>()
    private val cacheMutex = Mutex()

    suspend fun discoverProgressive(
        request: TvPlaybackRequest,
        onProgress: suspend (TvSourceProgress) -> Unit = {},
    ): TvSourceDiscovery = coroutineScope {
        val effectiveRequest = resolvePlayableRequest(request)
        val key = effectiveRequest.cacheKey
        val cached = cacheMutex.withLock { cache[key] }

        if (cached != null && System.currentTimeMillis() - cached.atMs <= CACHE_TTL_MS) {
            return@coroutineScope cached.discovery.copy(fromCache = true)
        }

        val addonUrls = contentStore.enabledAddonUrls()
        if (addonUrls.isEmpty()) {
            return@coroutineScope TvSourceDiscovery(
                sources = emptyList(),
                allSources = emptyList(),
                attemptedAddons = 0,
                successfulAddons = 0,
                failedAddons = 0,
                notice = "No enabled stream addons. Enable one in Content Manager.",
            )
        }

        val mutex = Mutex()
        val collected = mutableListOf<SourceCandidate>()
        var completed = 0
        var successful = 0
        var failed = 0

        addonUrls.map { manifestUrl ->
            async(Dispatchers.IO) {
                val result = withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                    runCatching {
                        val client = StremioAddonClient.fromManifestUrl(manifestUrl)
                        client.streams(
                            type = effectiveRequest.media.type,
                            videoId = effectiveRequest.videoId,
                        )
                    }
                }
                val streams = result?.getOrNull().orEmpty()
                val didFail = result == null || result.isFailure
                val progress = mutex.withLock {
                    completed += 1
                    if (didFail) failed += 1 else successful += 1
                    collected += streams
                    val playable = rankAndDedup(
                        input = collected,
                        originalLanguage = effectiveRequest.originalLanguage,
                    )
                    TvSourceProgress(
                        sources = playable,
                        allSources = orderAllSources(collected, playable),
                        completedAddons = completed,
                        totalAddons = addonUrls.size,
                    )
                }
                onProgress(progress)
            }
        }.awaitAll()

        val (playable, all) = mutex.withLock {
            val ranked = rankAndDedup(
                input = collected,
                originalLanguage = effectiveRequest.originalLanguage,
            )
            ranked to orderAllSources(collected, ranked)
        }
        val discovery = TvSourceDiscovery(
            sources = playable,
            allSources = all,
            attemptedAddons = addonUrls.size,
            successfulAddons = successful,
            failedAddons = failed,
            notice = when {
                playable.isNotEmpty() -> null
                all.any { it.isTorrent } -> "Torrent sources found. Direct playback is not available for them yet."
                failed == addonUrls.size -> "Source addons could not be reached."
                else -> "No direct playable source was returned for this title."
            },
        )

        if (all.isNotEmpty()) {
            cacheMutex.withLock {
                cache[key] = CachedSources(discovery, System.currentTimeMillis())
                trimCache(cache)
            }
        }

        discovery
    }

    suspend fun discoverSubtitles(request: TvPlaybackRequest): List<SubtitleCandidate> = coroutineScope {
        val effectiveRequest = resolvePlayableRequest(request)
        val key = effectiveRequest.cacheKey
        val cached = cacheMutex.withLock { subtitleCache[key] }
        if (cached != null && System.currentTimeMillis() - cached.atMs <= SUBTITLE_CACHE_TTL_MS) {
            return@coroutineScope cached.subtitles
        }

        val subtitles = contentStore.enabledAddonUrls()
            .map { manifestUrl ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                        runCatching {
                            val client = StremioAddonClient.fromManifestUrl(manifestUrl)
                            client.subtitles(
                                type = effectiveRequest.media.type,
                                videoId = effectiveRequest.videoId,
                            )
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
            .sortedWith(
                compareBy<SubtitleCandidate> { it.language.lowercase() }
                    .thenBy { it.providerName.lowercase() }
                    .thenBy { it.name.orEmpty().lowercase() },
            )

        if (subtitles.isNotEmpty()) {
            cacheMutex.withLock {
                subtitleCache[key] = CachedSubtitles(subtitles, System.currentTimeMillis())
                trimCache(subtitleCache)
            }
        }

        subtitles
    }

    private suspend fun resolvePlayableRequest(request: TvPlaybackRequest): TvPlaybackRequest {
        if (!request.media.type.equals("series", ignoreCase = true)) return request
        if (request.videoId != request.media.id) return request

        val resolvedId = withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    "https://v3-cinemeta.strem.io/meta/series/${Uri.encode(request.media.id)}.json"
                val root = JSONObject(httpGet(url))
                val videos = root.optJSONObject("meta")?.optJSONArray("videos")
                videos
                    ?.optJSONObject(0)
                    ?.optString("id")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        return if (resolvedId != null) request.copy(videoId = resolvedId) else request
    }

    private fun rankAndDedup(
        input: List<SourceCandidate>,
        originalLanguage: String?,
    ): List<SourceCandidate> =
        SourceRanker.rank(
            sources = input
                .filter { it.isDirectPlayable }
                .distinctBy { it.url.orEmpty().substringBefore('#') },
            originalLanguage = originalLanguage,
        )

    private fun orderAllSources(
        input: List<SourceCandidate>,
        playable: List<SourceCandidate>,
    ): List<SourceCandidate> {
        val playableIds = playable.mapTo(mutableSetOf()) { it.id }
        val other = input
            .filter { it.id !in playableIds }
            .distinctBy { source ->
                source.infoHash?.let { "torrent:$it:${source.fileIndex ?: -1}" }
                    ?: "url:${source.url.orEmpty().substringBefore('#')}"
            }
            .sortedWith(
                compareByDescending<SourceCandidate> { it.isTorrent }
                    .thenBy { it.providerName.lowercase() }
                    .thenBy { it.name.lowercase() },
            )
        return playable + other
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) {
            "VUEO TV source discovery requires HTTPS addon endpoints."
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VUEO-TV-Source-Engine/0.9")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun <K, V> trimCache(target: LinkedHashMap<K, V>) {
        while (target.size > MAX_CACHE_ENTRIES) {
            val first = target.keys.firstOrNull() ?: break
            target.remove(first)
        }
    }

    private data class CachedSources(
        val discovery: TvSourceDiscovery,
        val atMs: Long,
    )

    private data class CachedSubtitles(
        val subtitles: List<SubtitleCandidate>,
        val atMs: Long,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4_500
        private const val READ_TIMEOUT_MS = 7_500
        private const val ADDON_TIMEOUT_MS = 9_000L
        private const val CACHE_TTL_MS = 120_000L
        private const val SUBTITLE_CACHE_TTL_MS = 15 * 60_000L
        private const val MAX_CACHE_ENTRIES = 24
    }
}

data class TvEpisodeRef(
    val videoId: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
)

data class TvPlaybackRequest(
    val media: TvMediaItem,
    val videoId: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val originalLanguage: String? = null,
    val episodeQueue: List<TvEpisodeRef> = emptyList(),
) {
    val displayTitle: String
        get() =
            if (episodeTitle != null) {
                buildList {
                    add(media.name)
                    if (season != null && episode != null) add("S${season}E${episode}")
                    add(episodeTitle)
                }.joinToString(" • ")
            } else {
                media.name
            }

    val cacheKey: String
        get() = "${media.type}|${media.id}|$videoId"

    val nextEpisodeRef: TvEpisodeRef?
        get() {
            if (episodeQueue.isEmpty()) return null
            val currentIndex = episodeQueue.indexOfFirst { it.videoId == videoId }
            if (currentIndex < 0 || currentIndex >= episodeQueue.lastIndex) return null
            return episodeQueue[currentIndex + 1]
        }

    fun nextRequest(): TvPlaybackRequest? {
        val next = nextEpisodeRef ?: return null
        return copy(
            videoId = next.videoId,
            episodeTitle = next.title,
            season = next.season,
            episode = next.episode,
        )
    }
}

data class TvSourceDiscovery(
    val sources: List<SourceCandidate>,
    val allSources: List<SourceCandidate>,
    val attemptedAddons: Int,
    val successfulAddons: Int,
    val failedAddons: Int,
    val notice: String?,
    val fromCache: Boolean = false,
)

data class TvSourceProgress(
    val sources: List<SourceCandidate>,
    val allSources: List<SourceCandidate>,
    val completedAddons: Int,
    val totalAddons: Int,
)
