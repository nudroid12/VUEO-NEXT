package com.vueo.tv.data

import android.content.Context
import com.vueo.tv.content.TvContentManagerStore
import org.json.JSONArray
import org.json.JSONObject

enum class TvBrowseKind(
    val navLabel: String,
    val title: String,
    val subtitle: String,
) {
    MOVIE(
        navLabel = "Movie",
        title = "Movies",
        subtitle = "Movies from your enabled Content Manager catalogs.",
    ),
    SERIES(
        navLabel = "Series",
        title = "Series",
        subtitle = "Series from your enabled Content Manager catalogs.",
    ),
    ANIME(
        navLabel = "Anime",
        title = "Anime",
        subtitle = "Anime from compatible enabled catalogs.",
    ),
}

class TvBrowseRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val contentStore = TvContentManagerStore(appContext)
    private val discovery = TvUnifiedDiscovery(appContext)

    fun cached(kind: TvBrowseKind): List<TvMediaItem> {
        if (prefs.getInt(revisionKey(kind), Int.MIN_VALUE) != contentStore.discoveryRevision()) {
            return emptyList()
        }

        return prefs.getString(cacheKey(kind), null)
            ?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    (0 until array.length())
                        .mapNotNull { index -> array.optJSONObject(index)?.toMediaItem() }
                }.getOrNull()
            }
            .orEmpty()
    }

    suspend fun refresh(kind: TvBrowseKind): List<TvMediaItem> {
        val fresh = discovery.browse(kind)
        if (fresh.isEmpty()) {
            error("No enabled ${kind.title.lowercase()} catalog could be loaded.")
        }
        persist(kind, fresh)
        return fresh
    }

    private fun persist(
        kind: TvBrowseKind,
        items: List<TvMediaItem>,
    ) {
        val array = JSONArray()
        items.forEach { item -> array.put(mediaToJson(item)) }
        prefs.edit()
            .putString(cacheKey(kind), array.toString())
            .putInt(revisionKey(kind), contentStore.discoveryRevision())
            .apply()
    }

    private fun cacheKey(kind: TvBrowseKind): String = "browse_${kind.name.lowercase()}_v2"
    private fun revisionKey(kind: TvBrowseKind): String = "browse_${kind.name.lowercase()}_revision_v2"

    companion object {
        private const val PREFS_NAME = "vueo_tv_browse"
    }
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
        .put("sourceExtensionId", item.sourceExtensionId)
        .put("catalogSources", JSONArray(item.catalogSources))

private fun JSONObject.toMediaItem(): TvMediaItem? {
    val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").trim().takeIf { it.isNotBlank() } ?: return null

    return TvMediaItem(
        id = id,
        type = optString("type", "movie").ifBlank { "movie" },
        name = name,
        poster = optString("poster").takeIf { it.startsWith("https://") },
        background = optString("background").takeIf { it.startsWith("https://") },
        description = optString("description").takeIf { it.isNotBlank() && it != "null" },
        releaseInfo = optString("releaseInfo").takeIf { it.isNotBlank() && it != "null" },
        genres = optJSONArray("genres").toStringList(),
        imdbRating = optDouble("imdbRating", Double.NaN).takeIf { !it.isNaN() },
        sourceExtensionId = optString("sourceExtensionId").takeIf { it.isNotBlank() && it != "null" },
        catalogSources = optJSONArray("catalogSources").toStringList(),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
}
