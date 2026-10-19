package com.vueo.shared.core.plugin

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Exact identity resolution shared by Mobile and TV; never searches by title. */
object TmdbResolver {
    private const val MAX_CACHE_ENTRIES = 256
    enum class Route { DIRECT, METADATA, CACHE, TMDB_FIND, PUBLIC_METADATA, ANIME_ID, UNSUPPORTED_ID, INVALID_TYPE, CONFLICT, NO_MAPPING, AMBIGUOUS, REQUEST_FAILED }
    data class Resolution(val tmdbId: String?, val route: Route)

    private val resolvedIdCache = object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    private fun positive(value: String?): String? = value?.trim()
        ?.takeIf { it.matches(Regex("""\d+""")) }
        ?.toLongOrNull()?.takeIf { it > 0L }?.toString()

    fun directId(rawId: String): String? {
        val id = rawId.trim()
        return positive(if (id.startsWith("tmdb:", true)) id.substringAfter(':') else id)
    }

    /** Bare addon IDs are not assumed to be TMDB hints. Conflicting explicit fields are rejected. */
    fun metadataId(json: JSONObject): String? {
        val candidates = listOf("tmdbId", "tmdb_id", "moviedb_id")
            .mapNotNull { positive(json.optString(it)) }.toMutableList()
        val ids = json.optJSONObject("ids")
        if (ids != null) positive(ids.optString("tmdb"))?.let(candidates::add)
        val raw = json.optString("id")
        if (raw.startsWith("tmdb:", true)) directId(raw)?.let(candidates::add)
        return candidates.distinct().singleOrNull()
    }

    private fun namespace(type: String): String? = when (type.trim().lowercase()) {
        "series", "tv" -> "tv"
        "movie" -> "movie"
        else -> null
    }
    private fun cacheKey(id: String, type: String): String? = namespace(type)?.let { "$it|${id.trim().lowercase()}" }

    /** Seed only an explicit metadata/API mapping; never infer from names or years. */
    fun remember(rawId: String, mediaType: String, tmdbId: String) {
        val id = positive(tmdbId) ?: return
        val key = cacheKey(rawId, mediaType) ?: return
        if (rawId.isBlank()) return
        synchronized(resolvedIdCache) { resolvedIdCache[key] = id }
    }

    suspend fun resolve(rawId: String, mediaType: String, apiKey: String, knownTmdbId: String? = null): String? =
        resolveDetailed(rawId, mediaType, apiKey, knownTmdbId).tmdbId

    suspend fun resolveDetailed(rawId: String, mediaType: String, apiKey: String, knownTmdbId: String? = null): Resolution {
        val type = namespace(mediaType) ?: return Resolution(null, Route.INVALID_TYPE)
        val id = rawId.trim()
        val direct = directId(id)
        val known = positive(knownTmdbId)
        if (direct != null) return if (known != null && known != direct) Resolution(null, Route.CONFLICT) else Resolution(direct, Route.DIRECT)
        if (known != null) { remember(id, type, known); return Resolution(known, Route.METADATA) }
        val key = cacheKey(id, type)!!
        synchronized(resolvedIdCache) { resolvedIdCache[key] }?.let { return Resolution(it, Route.CACHE) }
        val imdb = id.removePrefix("imdb:")
        if (!imdb.matches(Regex("tt\\d+"))) return Resolution(null, Route.UNSUPPORTED_ID)
        try {
            val resolved: Resolution
            if (apiKey.isNotBlank()) {
                val url = "https://api.themoviedb.org/3/find/${encode(imdb)}?external_source=imdb_id&api_key=${encode(apiKey.trim())}"
                val data = JSONObject(PluginHttp.getText(url))
                val array = data.optJSONArray(if (type == "tv") "tv_results" else "movie_results")
                val ids = (0 until (array?.length() ?: 0)).mapNotNull { index -> positive(array?.optJSONObject(index)?.optString("id")) }.distinct()
                resolved = when (ids.size) {
                    0 -> Resolution(null, Route.NO_MAPPING)
                    1 -> Resolution(ids.single(), Route.TMDB_FIND)
                    else -> Resolution(null, Route.AMBIGUOUS)
                }
            } else {
                // One exact-ID request only when metadata/cache did not already supply the mapping.
                val kind = if (type == "tv") "series" else "movie"
                val meta = JSONObject(PluginHttp.getText("https://v3-cinemeta.strem.io/meta/$kind/${encode(imdb)}.json")).optJSONObject("meta")
                val returnedIds = listOfNotNull(meta?.optString("id"), meta?.optString("imdb_id")).filter(String::isNotBlank)
                if (meta == null || returnedIds.isEmpty() || returnedIds.any { it != imdb } || namespace(meta.optString("type")) != type) {
                    return Resolution(null, Route.CONFLICT)
                }
                val hint = metadataId(meta)
                resolved = Resolution(hint, if (hint != null) Route.PUBLIC_METADATA else Route.NO_MAPPING)
            }
            resolved.tmdbId?.let { remember(id, type, it) }
            return resolved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never include request URLs or API keys in a user-facing failure reason.
            return Resolution(null, Route.REQUEST_FAILED)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
