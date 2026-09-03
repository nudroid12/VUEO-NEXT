package com.vueo.tv.player

import android.content.Context
import android.net.Uri
import com.vueo.shared.core.plugin.PluginSourceEngine
import com.vueo.shared.core.plugin.TmdbResolver
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SourceRequest
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.stremio.StremioSourceResolver
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
    context: Context,
    private val contentStore: TvContentManagerStore,
) {
    private val appContext = context.applicationContext
    private val pluginStore = contentStore.sharedPluginStore()
    private val pluginEngine = PluginSourceEngine(appContext, pluginStore)
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
        runCatching { contentStore.preparePlugins() }
        val enabledPluginProviders = pluginStore.enabledProviderCount()
        val tmdbId =
            if (pluginStore.pluginsEnabled() && enabledPluginProviders > 0) {
                resolveTmdbId(effectiveRequest)
            } else {
                null
            }
        val pluginEnabled = tmdbId != null && enabledPluginProviders > 0
        val totalResolvers = addonUrls.size + if (pluginEnabled) 1 else 0

        if (totalResolvers == 0) {
            val notice =
                when {
                    enabledPluginProviders > 0 ->
                        "JS Providers are enabled, but VUEO could not resolve a TMDB ID for this title."
                    else ->
                        "No enabled stream addons or JS providers. Enable sources in Content Manager."
                }
            return@coroutineScope TvSourceDiscovery(
                sources = emptyList(),
                allSources = emptyList(),
                attemptedResolvers = 0,
                successfulResolvers = 0,
                failedResolvers = 0,
                notice = notice,
            )
        }

        val mutex = Mutex()
        val addonCollected = mutableListOf<SourceCandidate>()
        var pluginCollected = emptyList<SourceCandidate>()
        var completedResolvers = 0
        var successfulResolvers = 0
        var failedResolvers = 0

        suspend fun publishProgress() {
            val progress = mutex.withLock {
                val combined = addonCollected + pluginCollected
                val playable = rankAndDedup(combined, effectiveRequest.originalLanguage)
                TvSourceProgress(
                    sources = playable,
                    allSources = orderAllSources(combined, playable),
                    completedResolvers = completedResolvers,
                    totalResolvers = totalResolvers,
                )
            }
            onProgress(progress)
        }

        val addonJobs =
            addonUrls.map { manifestUrl ->
                async(Dispatchers.IO) {
                    val result = withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                        runCatching {
                            StremioSourceResolver.fromManifestUrl(manifestUrl)
                                .resolve(effectiveRequest.toSharedRequest())
                                .sources
                        }
                    }
                    val streams = result?.getOrNull().orEmpty()
                    val didFail = result == null || result.isFailure
                    mutex.withLock {
                        addonCollected += streams
                        completedResolvers += 1
                        if (didFail) failedResolvers += 1 else successfulResolvers += 1
                    }
                    publishProgress()
                }
            }

        val pluginJob =
            if (pluginEnabled) {
                async(Dispatchers.IO) {
                    val result =
                        runCatching {
                            pluginEngine.discoverProgressive(
                                tmdbId = tmdbId!!,
                                mediaType = effectiveRequest.pluginMediaType(),
                                season = effectiveRequest.season,
                                episode = effectiveRequest.episode,
                            ) { progress ->
                                mutex.withLock {
                                    pluginCollected = progress.result.streams
                                }
                                publishProgress()
                            }
                        }

                    mutex.withLock {
                        pluginCollected = result.getOrNull()?.streams.orEmpty()
                        completedResolvers += 1
                        if (result.isFailure) failedResolvers += 1 else successfulResolvers += 1
                    }
                    publishProgress()
                }
            } else {
                null
            }

        addonJobs.awaitAll()
        pluginJob?.await()

        val finalCombined = mutex.withLock { addonCollected + pluginCollected }
        val playable = rankAndDedup(finalCombined, effectiveRequest.originalLanguage)
        val all = orderAllSources(finalCombined, playable)
        val discovery =
            TvSourceDiscovery(
                sources = playable,
                allSources = all,
                attemptedResolvers = totalResolvers,
                successfulResolvers = successfulResolvers,
                failedResolvers = failedResolvers,
                notice =
                    when {
                        playable.isNotEmpty() -> null
                        all.any { it.isTorrent } ->
                            "Torrent sources found. Direct playback is not available for them yet."
                        failedResolvers == totalResolvers ->
                            "Source providers could not be reached."
                        else ->
                            "No direct playable source was returned for this title."
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
                            val resolver = StremioSourceResolver.fromManifestUrl(manifestUrl)
                            resolver.resolve(effectiveRequest.toSharedRequest()).subtitles
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
                val meta = fetchCinemetaMeta(request.media.type, request.media.id)
                val videos = meta.optJSONArray("videos")
                videos
                    ?.optJSONObject(0)
                    ?.optString("id")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        return if (resolvedId != null) request.copy(videoId = resolvedId) else request
    }

    private suspend fun resolveTmdbId(request: TvPlaybackRequest): String? =
        withContext(Dispatchers.IO) {
            val fromCinemeta =
                runCatching {
                    val meta = fetchCinemetaMeta(request.media.type, request.media.id)
                    meta.flexibleId("moviedb_id", "tmdb_id", "tmdbId")
                }.getOrNull()

            fromCinemeta
                ?: runCatching {
                    TmdbResolver.resolve(
                        rawId = request.media.id,
                        mediaType = request.media.type,
                        apiKey = pluginStore.tmdbApiKey(),
                    )
                }.getOrNull()
        }

    private fun fetchCinemetaMeta(mediaType: String, mediaId: String): JSONObject {
        val type = if (mediaType.equals("tv", ignoreCase = true)) "series" else mediaType
        val url = "$CINEMETA_BASE/meta/$type/${Uri.encode(mediaId)}.json"
        val root = JSONObject(httpGet(url))
        return root.optJSONObject("meta") ?: root
    }

    private fun JSONObject.flexibleId(vararg keys: String): String? {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is Number -> if (value.toLong() > 0L) return value.toLong().toString()
                is String -> {
                    val clean = value.trim().removePrefix("tmdb:")
                    if (clean.matches(Regex("""\d+"""))) return clean
                }
            }
        }
        return null
    }

    private fun TvPlaybackRequest.toSharedRequest(): SourceRequest =
        SourceRequest(
            mediaType = media.type,
            videoId = videoId,
            title = media.name,
            originalLanguage = originalLanguage,
            season = season,
            episode = episode,
        )

    private fun TvPlaybackRequest.pluginMediaType(): String =
        if (media.type.equals("series", ignoreCase = true)) "tv" else "movie"

    private fun rankAndDedup(
        input: List<SourceCandidate>,
        originalLanguage: String?,
    ): List<SourceCandidate> =
        SourceRanker.rank(
            sources =
                input
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
            "VUEO TV source discovery requires HTTPS endpoints."
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VUEO-TV-Source-Engine/1.0")
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
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
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
    val attemptedResolvers: Int,
    val successfulResolvers: Int,
    val failedResolvers: Int,
    val notice: String?,
    val fromCache: Boolean = false,
)

data class TvSourceProgress(
    val sources: List<SourceCandidate>,
    val allSources: List<SourceCandidate>,
    val completedResolvers: Int,
    val totalResolvers: Int,
)
