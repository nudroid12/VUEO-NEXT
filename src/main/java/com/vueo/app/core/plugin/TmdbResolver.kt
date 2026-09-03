package com.vueo.app.core.plugin

import android.net.Uri
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.stremio.SimpleHttp
import org.json.JSONObject

object TmdbResolver {
    suspend fun resolve(
        media: MediaItem,
        apiKey: String,
    ): String? {
        val rawId = media.id.trim()

        if (rawId.matches(Regex("""\d+"""))) {
            return rawId
        }

        if (rawId.startsWith("tmdb:")) {
            return rawId
                .substringAfter("tmdb:")
                .takeIf {
                    it.matches(Regex("""\d+"""))
                }
        }

        if (!rawId.startsWith("tt")) {
            return null
        }

        if (apiKey.isBlank()) {
            return null
        }

        val url =
            "https://api.themoviedb.org/3/find/" +
                Uri.encode(rawId) +
                "?external_source=imdb_id" +
                "&api_key=" +
                Uri.encode(apiKey)

        val json = JSONObject(
            SimpleHttp.get(url)
        )

        val resultArray =
            if (media.type == "series") {
                json.optJSONArray("tv_results")
            } else {
                json.optJSONArray("movie_results")
            }

        if (
            resultArray == null ||
            resultArray.length() == 0
        ) {
            return null
        }

        val id = resultArray
            .optJSONObject(0)
            ?.optLong("id", -1L)
            ?: -1L

        return id
            .takeIf { it > 0L }
            ?.toString()
    }
}
