package com.vueo.shared.core.trakt

import com.vueo.shared.core.media.MediaItem
import org.json.JSONArray
import org.json.JSONObject

internal object TraktMedia {
    fun ids(media: MediaItem): JSONObject? = when {
        Regex("^tt[0-9]+$").matches(media.id) -> JSONObject().put("imdb", media.id)
        media.id.startsWith("tmdb:") -> media.id.removePrefix("tmdb:").toLongOrNull()
            ?.takeIf { it > 0 }?.let { JSONObject().put("tmdb", it) }
        else -> null
    }
    fun isMovie(media: MediaItem) = media.type.lowercase() in listOf("movie", "movies", "film")
    fun isShow(media: MediaItem) = media.type.lowercase() in listOf("series", "tv", "show", "shows")
    fun payload(media: MediaItem, season: Int? = null, episode: Int? = null): JSONObject? {
        val ids = ids(media) ?: return null
        if (isMovie(media)) return JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", ids)))
        if (!isShow(media)) return null
        val show = JSONObject().put("ids", ids)
        if (season != null && episode != null) show.put("seasons", JSONArray().put(JSONObject()
            .put("number", season).put("episodes", JSONArray().put(JSONObject().put("number", episode)))))
        return JSONObject().put("shows", JSONArray().put(show))
    }
    fun parse(json: JSONObject, type: String): MediaItem? {
        val ids = json.optJSONObject("ids") ?: return null
        val imdb = ids.optString("imdb").takeIf { Regex("^tt[0-9]+$").matches(it) }
        val tmdb = ids.optLong("tmdb", 0).takeIf { it > 0 }
        val id = imdb ?: tmdb?.let { "tmdb:$it" } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val images = json.optJSONObject("images")
        fun image(field: String): String? {
            val raw = images?.optJSONArray(field)?.optString(0).orEmpty()
            return when {
                raw.isBlank() || raw == "null" -> null
                raw.startsWith("https://") -> raw
                !raw.contains("://") -> "https://$raw"
                else -> null
            }
        }
        val genres = json.optJSONArray("genres")
        return MediaItem(id = id, type = type, name = title, poster = image("poster"), background = image("fanart"),
            description = json.optString("overview").takeUnless { it.isBlank() || it == "null" },
            releaseInfo = json.optInt("year").takeIf { it > 0 }?.toString(),
            originalLanguage = json.optString("language").takeUnless { it.isBlank() || it == "null" },
            genres = (0 until (genres?.length() ?: 0)).mapNotNull { genres?.optString(it)?.replace('-', ' ') },
            runtimeMinutes = json.optInt("runtime").takeIf { it > 0 }, catalogSources = listOf("Trakt"))
    }
    fun unwrap(row: JSONObject, known: Map<String, MediaItem>): MediaItem? {
        val movie = row.optJSONObject("movie")
        val show = row.optJSONObject("show")
        val json = movie ?: show ?: return null
        val type = if (movie != null) "movie" else "series"
        val remote = parse(json, type) ?: return null
        val ids = json.optJSONObject("ids") ?: return remote
        val local = listOf(ids.optString("imdb"), "tmdb:${ids.optLong("tmdb")}")
            .firstNotNullOfOrNull { known["$type:$it"] }
        return if (local == null) remote else remote.copy(id = local.id, type = local.type,
            sourceExtensionId = local.sourceExtensionId, poster = remote.poster ?: local.poster,
            background = remote.background ?: local.background)
    }
    fun unwrap(row: JSONObject): MediaItem? = row.optJSONObject("movie")?.let { parse(it, "movie") }
        ?: row.optJSONObject("show")?.let { parse(it, "series") }
}
