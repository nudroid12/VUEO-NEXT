package com.vueo.shared.core.plugin

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Shared TMDB ID resolver used by provider discovery on Mobile and TV. */
object TmdbResolver {
    suspend fun resolve(
        rawId: String,
        mediaType: String,
        apiKey: String,
    ): String? {
        val id = rawId.trim()

        if (id.matches(Regex("""\d+"""))) {
            return id
        }

        if (id.startsWith("tmdb:")) {
            return id
                .substringAfter("tmdb:")
                .takeIf { it.matches(Regex("""\d+""")) }
        }

        if (!id.startsWith("tt") || apiKey.isBlank()) {
            return null
        }

        val url =
            "https://api.themoviedb.org/3/find/" +
                encode(id) +
                "?external_source=imdb_id" +
                "&api_key=" +
                encode(apiKey)

        val json = JSONObject(PluginHttp.getText(url))
        val resultArray =
            if (mediaType == "series" || mediaType == "tv") {
                json.optJSONArray("tv_results")
            } else {
                json.optJSONArray("movie_results")
            }

        if (resultArray == null || resultArray.length() == 0) {
            return null
        }

        val tmdbId = resultArray.optJSONObject(0)?.optLong("id", -1L) ?: -1L
        return tmdbId.takeIf { it > 0L }?.toString()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
