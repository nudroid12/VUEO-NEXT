package com.vueo.tv.search

import android.content.Context
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvMediaItem
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TvSearchMode {
    TITLE,
    ACTOR,
}

enum class TvSearchType {
    ALL,
    MOVIE,
    SERIES,
}

data class TvSearchResult(
    val media: TvMediaItem,
    val providerName: String,
)

class TvSearchRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val homeRepository = TvHomeRepository(appContext)

    /**
     * TV currently has Cinemeta as its metadata catalog. Cinemeta exposes title search,
     * not a dedicated cast/person catalog, so Actor mode is surfaced honestly as unavailable
     * until a provider or TMDB actor source is wired into the shared TV core.
     */
    val actorSearchAvailable: Boolean = false

    fun searchLocal(
        query: String,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val homeItems =
            homeRepository.cached()
                ?.rows
                .orEmpty()
                .flatMap { row ->
                    row.items.map { item ->
                        TvSearchResult(
                            media = item,
                            providerName = row.providerName.ifBlank { "Cinemeta" },
                        )
                    }
                }

        return rankAndDedupe(
            query = cleanQuery,
            items = homeItems + cachedRemoteItems(),
            type = type,
        )
    }

    suspend fun searchRemote(
        query: String,
        type: TvSearchType,
    ): List<TvSearchResult> =
        coroutineScope {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@coroutineScope emptyList()

            val encoded =
                URLEncoder
                    .encode(cleanQuery, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")

            val requestedTypes =
                when (type) {
                    TvSearchType.ALL -> listOf("movie", "series")
                    TvSearchType.MOVIE -> listOf("movie")
                    TvSearchType.SERIES -> listOf("series")
                }

            val remote =
                requestedTypes
                    .map { mediaType ->
                        async(Dispatchers.IO) {
                            runCatching {
                                fetchCatalogSearch(
                                    mediaType = mediaType,
                                    encodedQuery = encoded,
                                )
                            }.getOrDefault(emptyList())
                        }
                    }
                    .awaitAll()
                    .flatten()

            val ranked =
                rankAndDedupe(
                    query = cleanQuery,
                    items = remote,
                    type = type,
                )

            if (ranked.isNotEmpty()) {
                persistRemoteItems(ranked)
            }

            ranked
        }

    fun merge(
        query: String,
        type: TvSearchType,
        local: List<TvSearchResult>,
        remote: List<TvSearchResult>,
    ): List<TvSearchResult> =
        rankAndDedupe(
            query = query,
            items = local + remote,
            type = type,
        )

    private suspend fun fetchCatalogSearch(
        mediaType: String,
        encodedQuery: String,
    ): List<TvSearchResult> =
        withContext(Dispatchers.IO) {
            val url = "$CINEMETA_BASE/catalog/$mediaType/top/search=$encodedQuery.json"
            val json = JSONObject(httpGet(url))
            val metas = json.optJSONArray("metas") ?: JSONArray()

            (0 until metas.length())
                .mapNotNull { index ->
                    metas.optJSONObject(index)
                        ?.toMediaItem(mediaType)
                        ?.let { media ->
                            TvSearchResult(
                                media = media,
                                providerName = "Cinemeta",
                            )
                        }
                }
                .take(MAX_REMOTE_ITEMS_PER_TYPE)
        }

    private fun rankAndDedupe(
        query: String,
        items: List<TvSearchResult>,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

        return items
            .asSequence()
            .filter { result -> type.accepts(result.media.type) }
            .map { result ->
                result to relevanceScore(normalizedQuery, queryTokens, result.media)
            }
            .filter { (_, score) -> score >= MIN_RELEVANCE_SCORE }
            .sortedWith(
                compareByDescending<Pair<TvSearchResult, Int>> { it.second }
                    .thenByDescending { (_, score) -> score }
                    .thenByDescending { (result, _) -> result.media.imdbRating ?: 0.0 }
                    .thenBy { (result, _) -> result.media.name.lowercase(Locale.ROOT) }
            )
            .distinctBy { (result, _) -> canonicalKey(result.media) }
            .take(MAX_RESULTS)
            .map { it.first }
            .toList()
    }

    private fun relevanceScore(
        normalizedQuery: String,
        queryTokens: List<String>,
        media: TvMediaItem,
    ): Int {
        val title = normalize(media.name)
        if (title.isBlank()) return 0

        var score = 0
        when {
            title == normalizedQuery -> score += 1_000
            title.startsWith(normalizedQuery) -> score += 850
            title.contains(normalizedQuery) -> score += 650
        }

        val titleTokens = title.split(' ').filter { it.isNotBlank() }
        val matchedTokens =
            queryTokens.count { queryToken ->
                titleTokens.any { titleToken ->
                    titleToken == queryToken || titleToken.startsWith(queryToken)
                }
            }

        if (queryTokens.isNotEmpty()) {
            score += matchedTokens * 90
            if (matchedTokens == queryTokens.size) score += 240
        }

        val releaseInfo = media.releaseInfo
        releaseInfo
            ?.substring(0, minOf(releaseInfo.length, 4))
            ?.toIntOrNull()
            ?.let { year ->
                if (normalizedQuery.contains(year.toString())) score += 100
            }

        media.imdbRating?.let { score += (it * 3).toInt() }
        return score
    }

    private fun persistRemoteItems(items: List<TvSearchResult>) {
        val merged =
            (items + cachedRemoteItems())
                .distinctBy { canonicalKey(it.media) }
                .take(MAX_CACHED_ITEMS)

        val array = JSONArray()
        merged.forEach { result ->
            array.put(
                JSONObject()
                    .put("providerName", result.providerName)
                    .put("media", mediaToJson(result.media))
            )
        }

        prefs.edit()
            .putString(KEY_SEARCH_CACHE, array.toString())
            .apply()
    }

    private fun cachedRemoteItems(): List<TvSearchResult> {
        val raw = prefs.getString(KEY_SEARCH_CACHE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    val media = entry.optJSONObject("media")?.let(::mediaFromJson)
                        ?: return@mapNotNull null
                    TvSearchResult(
                        media = media,
                        providerName = entry.optString("providerName", "Cinemeta"),
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) {
            "VUEO TV search requests require HTTPS."
        }

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VUEO-TV-Search/0.4")
            }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code from Cinemeta search")
            }

            connection.inputStream
                .bufferedReader()
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PREFS_NAME = "vueo_tv_search"
        private const val KEY_SEARCH_CACHE = "search_cache_v1"
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000
        private const val MAX_REMOTE_ITEMS_PER_TYPE = 30
        private const val MAX_CACHED_ITEMS = 120
        private const val MAX_RESULTS = 60
        private const val MIN_RELEVANCE_SCORE = 180
    }
}

private fun TvSearchType.accepts(mediaType: String): Boolean =
    when (this) {
        TvSearchType.ALL -> true
        TvSearchType.MOVIE -> mediaType.equals("movie", ignoreCase = true)
        TvSearchType.SERIES -> mediaType.equals("series", ignoreCase = true)
    }

private fun normalize(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun canonicalKey(media: TvMediaItem): String {
    val year =
        media.releaseInfo
            ?.take(4)
            ?.toIntOrNull()
            ?.toString()
            .orEmpty()
    return "${media.type.lowercase(Locale.ROOT)}:${normalize(media.name)}:$year"
}

private fun JSONObject.toMediaItem(fallbackType: String): TvMediaItem? {
    val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").trim().takeIf { it.isNotBlank() } ?: return null

    return TvMediaItem(
        id = id,
        type = optString("type", fallbackType).ifBlank { fallbackType },
        name = name,
        poster = httpsOrNull(optString("poster")),
        background = httpsOrNull(optString("background")),
        description = optString("description").trim().takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").trim().takeIf { it.isNotBlank() },
        genres = optJSONArray("genres").toStringList(),
        imdbRating = flexibleDouble("imdbRating", "imdb_rating"),
    )
}

private fun httpsOrNull(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.startsWith("https://") }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            optString(index)
                .trim()
                .takeIf { it.isNotBlank() }
        }
}

private fun JSONObject.flexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val parsed =
            when (val value = opt(key)) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        if (parsed != null) return parsed
    }
    return null
}

private fun mediaToJson(media: TvMediaItem): JSONObject =
    JSONObject()
        .put("id", media.id)
        .put("type", media.type)
        .put("name", media.name)
        .put("poster", media.poster)
        .put("background", media.background)
        .put("description", media.description)
        .put("releaseInfo", media.releaseInfo)
        .put("genres", JSONArray(media.genres))
        .put("imdbRating", media.imdbRating)

private fun mediaFromJson(json: JSONObject): TvMediaItem? {
    val id = json.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = json.optString("name").trim().takeIf { it.isNotBlank() } ?: return null

    return TvMediaItem(
        id = id,
        type = json.optString("type", "movie"),
        name = name,
        poster = json.optString("poster").takeIf { it.startsWith("https://") },
        background = json.optString("background").takeIf { it.startsWith("https://") },
        description = json.optString("description").takeIf { it.isNotBlank() },
        releaseInfo = json.optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = json.optJSONArray("genres").toStringList(),
        imdbRating = json.optDouble("imdbRating", Double.NaN).takeIf { !it.isNaN() },
    )
}
