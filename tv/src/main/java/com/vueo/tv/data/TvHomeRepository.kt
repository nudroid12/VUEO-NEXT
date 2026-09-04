package com.vueo.tv.data

import android.content.Context
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
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

/**
 * TV-02 real home feed.
 *
 * Uses the same Cinemeta Stremio addon that VUEO Mobile seeds by default.
 * The TV app keeps its own small home cache so a previously loaded home can
 * render immediately while fresh catalog requests happen in the background.
 */
class TvHomeRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val profileStore = ProfileStore(appContext)
    private val dnaPreferences =
        UserDnaPreferences(
            context = appContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    private val dnaEngine =
        UserDnaEngine(
            LibraryStore(
                context = appContext,
                prefsName = TvLibraryStore.PREFS_NAME,
                watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
            ),
        )

    fun cached(): TvHomeData? {
        val activeProfileId = profileStore.activeProfileId()
        val cachedProfileId = prefs.getString(KEY_PROFILE_ID, null)
        if (cachedProfileId != activeProfileId) return null

        return prefs.getString(KEY_HOME_CACHE, null)
            ?.let { raw ->
                runCatching {
                    homeFromJson(JSONObject(raw))
                }.getOrNull()
            }
    }

    fun shouldRefresh(home: TvHomeData?): Boolean {
        if (home == null) return true
        val age = System.currentTimeMillis() - home.refreshedAtEpochMs
        return age < 0L || age >= CACHE_MAX_AGE_MS
    }

    suspend fun refresh(): TvHomeData = coroutineScope {
        val year = Calendar.getInstance().get(Calendar.YEAR)

        val requests =
            listOf(
                CatalogRequest(
                    id = "popular-movies",
                    title = "Popular Movies",
                    type = "movie",
                    url = "$CINEMETA_BASE/catalog/movie/top.json",
                ),
                CatalogRequest(
                    id = "popular-series",
                    title = "Popular Series",
                    type = "series",
                    url = "$CINEMETA_BASE/catalog/series/top.json",
                ),
                CatalogRequest(
                    id = "new-movies",
                    title = "New Movies",
                    type = "movie",
                    url = "$CINEMETA_BASE/catalog/movie/year/genre=$year.json",
                ),
                CatalogRequest(
                    id = "new-series",
                    title = "New Series",
                    type = "series",
                    url = "$CINEMETA_BASE/catalog/series/year/genre=$year.json",
                ),
            )

        val rows =
            requests
                .map { request ->
                    async(Dispatchers.IO) {
                        runCatching {
                            fetchCatalog(request)
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .filter { it.items.isNotEmpty() }

        if (rows.isEmpty()) {
            error("No VUEO TV catalogs could be loaded.")
        }

        val finalRows = personalize(rows)

        val hero =
            finalRows
                .asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.background.isNullOrBlank() }
                ?: finalRows.first().items.first()

        val home =
            TvHomeData(
                hero = hero,
                rows = finalRows,
                providerName = "Cinemeta",
                refreshedAtEpochMs = System.currentTimeMillis(),
            )

        persist(home)
        home
    }

    private suspend fun fetchCatalog(
        request: CatalogRequest,
    ): TvCatalogRow =
        withContext(Dispatchers.IO) {
            val json = JSONObject(httpGet(request.url))
            val metas = json.optJSONArray("metas") ?: JSONArray()

            val items =
                (0 until metas.length())
                    .mapNotNull { index ->
                        metas.optJSONObject(index)
                            ?.toMediaItem(request.type)
                    }
                    .distinctBy { "${it.type}:${it.id}" }
                    .take(MAX_ITEMS_PER_ROW)

            TvCatalogRow(
                id = request.id,
                title = request.title,
                providerName = "Cinemeta",
                items = items,
            )
        }

    private fun personalize(
        rows: List<TvCatalogRow>,
    ): List<TvCatalogRow> {
        val profileId = profileStore.activeProfileId()
        if (!dnaPreferences.shouldPersonalizeRecommendations(profileId)) {
            return rows
        }

        val dna = dnaEngine.build()
        val ranked =
            rows
                .asSequence()
                .flatMap { it.items.asSequence() }
                .distinctBy { "${it.type}:${it.id}" }
                .mapNotNull { media ->
                    dnaEngine.matchPercent(media, dna)
                        ?.let { score -> media to score }
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(MAX_ITEMS_PER_ROW)
                .toList()

        if (ranked.size < MIN_PERSONALIZED_ITEMS) {
            return rows
        }

        return listOf(
            TvCatalogRow(
                id = "for-you",
                title = "For You",
                providerName = "VUEO DNA",
                items = ranked,
            ),
        ) + rows
    }

    private fun persist(home: TvHomeData) {
        prefs.edit()
            .putString(
                KEY_HOME_CACHE,
                homeToJson(home).toString(),
            )
            .putString(KEY_PROFILE_ID, profileStore.activeProfileId())
            .apply()
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) {
            "VUEO TV catalog requests require HTTPS."
        }

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
            if (code !in 200..299) {
                error("HTTP $code from Cinemeta")
            }

            connection.inputStream
                .bufferedReader()
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private data class CatalogRequest(
        val id: String,
        val title: String,
        val type: String,
        val url: String,
    )

    companion object {
        private const val PREFS_NAME = "vueo_tv_home"
        private const val KEY_HOME_CACHE = "home_cache_v1"
        private const val KEY_PROFILE_ID = "home_cache_profile_v1"
        private const val CACHE_MAX_AGE_MS = 20L * 60L * 1000L

        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000
        private const val MAX_ITEMS_PER_ROW = 24
        private const val MIN_PERSONALIZED_ITEMS = 6
    }
}

data class TvHomeData(
    val hero: TvMediaItem,
    val rows: List<TvCatalogRow>,
    val providerName: String,
    val refreshedAtEpochMs: Long,
)

data class TvCatalogRow(
    val id: String,
    val title: String,
    val providerName: String,
    val items: List<TvMediaItem>,
)

typealias TvMediaItem = com.vueo.shared.core.media.MediaItem

private fun JSONObject.toMediaItem(
    fallbackType: String,
): TvMediaItem? {
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

private fun JSONObject.flexibleDouble(
    vararg keys: String,
): Double? {
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

private fun homeToJson(home: TvHomeData): JSONObject =
    JSONObject()
        .put("hero", mediaToJson(home.hero))
        .put(
            "rows",
            JSONArray().apply {
                home.rows.forEach { row ->
                    put(
                        JSONObject()
                            .put("id", row.id)
                            .put("title", row.title)
                            .put("providerName", row.providerName)
                            .put(
                                "items",
                                JSONArray().apply {
                                    row.items.forEach { item ->
                                        put(mediaToJson(item))
                                    }
                                },
                            )
                    )
                }
            },
        )
        .put("providerName", home.providerName)
        .put("refreshedAtEpochMs", home.refreshedAtEpochMs)

private fun homeFromJson(json: JSONObject): TvHomeData {
    val rowsJson = json.optJSONArray("rows") ?: JSONArray()
    val rows =
        (0 until rowsJson.length())
            .mapNotNull { index ->
                val rowJson = rowsJson.optJSONObject(index) ?: return@mapNotNull null
                val itemsJson = rowJson.optJSONArray("items") ?: JSONArray()
                val items =
                    (0 until itemsJson.length())
                        .mapNotNull { itemIndex ->
                            itemsJson.optJSONObject(itemIndex)?.let(::mediaFromJson)
                        }

                TvCatalogRow(
                    id = rowJson.optString("id"),
                    title = rowJson.optString("title"),
                    providerName = rowJson.optString("providerName", "Cinemeta"),
                    items = items,
                )
            }

    val hero =
        json.optJSONObject("hero")
            ?.let(::mediaFromJson)
            ?: rows.firstOrNull()?.items?.firstOrNull()
            ?: error("Invalid VUEO TV home cache.")

    return TvHomeData(
        hero = hero,
        rows = rows,
        providerName = json.optString("providerName", "Cinemeta"),
        refreshedAtEpochMs = json.optLong("refreshedAtEpochMs", 0L),
    )
}

private fun mediaToJson(item: TvMediaItem): JSONObject =
    JSONObject()
        .put("id", item.id)
        .put("type", item.type)
        .put("name", item.name)
        .put("poster", item.poster)
        .put("background", item.background)
        .put("description", item.description)
        .put("releaseInfo", item.releaseInfo)
        .put("genres", JSONArray(item.genres))
        .put("imdbRating", item.imdbRating)

private fun mediaFromJson(json: JSONObject): TvMediaItem =
    TvMediaItem(
        id = json.optString("id"),
        type = json.optString("type", "movie"),
        name = json.optString("name"),
        poster = json.optString("poster").takeIf { it.isNotBlank() && it != "null" },
        background = json.optString("background").takeIf { it.isNotBlank() && it != "null" },
        description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
        releaseInfo = json.optString("releaseInfo").takeIf { it.isNotBlank() && it != "null" },
        genres = json.optJSONArray("genres").toStringList(),
        imdbRating = json.opt("imdbRating").let { value ->
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        },
    )
