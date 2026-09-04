package com.vueo.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

enum class TvBrowseKind(
    val navLabel: String,
    val title: String,
    val subtitle: String,
) {
    MOVIE(
        navLabel = "Movie",
        title = "Movies",
        subtitle = "Popular and new movies across VUEO.",
    ),
    SERIES(
        navLabel = "Series",
        title = "Series",
        subtitle = "Popular and new series across VUEO.",
    ),
    ANIME(
        navLabel = "Anime",
        title = "Anime",
        subtitle = "Anime movies and series in one place.",
    ),
}

class TvBrowseRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun cached(kind: TvBrowseKind): List<TvMediaItem> =
        prefs.getString(cacheKey(kind), null)
            ?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    (0 until array.length())
                        .mapNotNull { index ->
                            array.optJSONObject(index)?.toMediaItem()
                        }
                }.getOrNull()
            }
            .orEmpty()

    suspend fun refresh(kind: TvBrowseKind): List<TvMediaItem> = coroutineScope {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val requests =
            when (kind) {
                TvBrowseKind.MOVIE ->
                    listOf(
                        CatalogRequest("movie", "$CINEMETA_BASE/catalog/movie/top.json"),
                        CatalogRequest("movie", "$CINEMETA_BASE/catalog/movie/year/genre=$year.json"),
                    )

                TvBrowseKind.SERIES ->
                    listOf(
                        CatalogRequest("series", "$CINEMETA_BASE/catalog/series/top.json"),
                        CatalogRequest("series", "$CINEMETA_BASE/catalog/series/year/genre=$year.json"),
                    )

                TvBrowseKind.ANIME ->
                    listOf(
                        CatalogRequest("series", "$CINEMETA_BASE/catalog/series/top/genre=Anime.json"),
                        CatalogRequest("movie", "$CINEMETA_BASE/catalog/movie/top/genre=Anime.json"),
                    )
            }

        val fresh =
            requests
                .map { request ->
                    async(Dispatchers.IO) {
                        runCatching { fetchCatalog(request) }.getOrDefault(emptyList())
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { "${it.type}:${it.id}" }
                .take(MAX_ITEMS)

        if (fresh.isEmpty()) {
            error("No ${kind.title.lowercase()} catalog could be loaded.")
        }

        persist(kind, fresh)
        fresh
    }

    private suspend fun fetchCatalog(request: CatalogRequest): List<TvMediaItem> =
        withContext(Dispatchers.IO) {
            val json = JSONObject(httpGet(request.url))
            val metas = json.optJSONArray("metas") ?: JSONArray()
            (0 until metas.length())
                .mapNotNull { index ->
                    metas.optJSONObject(index)?.toMediaItem(request.type)
                }
        }

    private fun persist(
        kind: TvBrowseKind,
        items: List<TvMediaItem>,
    ) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("name", item.name)
                    .put("poster", item.poster)
                    .put("background", item.background)
                    .put("description", item.description)
                    .put("releaseInfo", item.releaseInfo)
                    .put("genres", JSONArray(item.genres))
                    .put("imdbRating", item.imdbRating),
            )
        }
        prefs.edit().putString(cacheKey(kind), array.toString()).apply()
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://"))
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VUEO-TV/0.2")
            }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from Cinemeta")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheKey(kind: TvBrowseKind): String = "browse_${kind.name.lowercase()}_v1"

    private data class CatalogRequest(
        val type: String,
        val url: String,
    )

    companion object {
        private const val PREFS_NAME = "vueo_tv_browse"
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000
        private const val MAX_ITEMS = 60
    }
}

private fun JSONObject.toMediaItem(
    fallbackType: String? = null,
): TvMediaItem? {
    val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").trim().takeIf { it.isNotBlank() } ?: return null
    val type = optString("type", fallbackType.orEmpty()).trim().ifBlank { fallbackType ?: "movie" }

    return TvMediaItem(
        id = id,
        type = type,
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
    value?.trim()?.takeIf { it.startsWith("https://") }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
}

private fun JSONObject.flexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val value = opt(key)
        val parsed =
            when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        if (parsed != null) return parsed
    }
    return null
}
