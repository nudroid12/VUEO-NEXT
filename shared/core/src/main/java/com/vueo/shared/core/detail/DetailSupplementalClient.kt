package com.vueo.shared.core.detail

import android.net.Uri
import com.vueo.shared.core.enrichment.MetadataHttp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.plugin.TmdbResolver
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Optional detail presentation data that is not part of the canonical MediaItem.
 * The fetch/selection rules live in Shared Core so TV does not own a private TMDB
 * client. Mobile remains free to ignore these visual extras.
 */
data class DetailSupplementalInfo(
    val logo: String? = null,
    val country: String? = null,
    val language: String? = null,
    val status: String? = null,
    val fullReleaseDate: String? = null,
    val trailerUrl: String? = null,
)

object DetailSupplementalClient {
    suspend fun load(
        media: MediaItem,
        tmdbApiKey: String,
    ): DetailSupplementalInfo {
        val apiKey = tmdbApiKey.trim()
        if (apiKey.isBlank()) return DetailSupplementalInfo()

        val isSeries = MediaTypePolicy.isSeries(media.type)
        val endpoint = if (isSeries) "tv" else "movie"
        val tmdbId = runCatching {
            TmdbResolver.resolve(
                rawId = media.id,
                mediaType = media.type,
                apiKey = apiKey,
            )
        }.getOrNull() ?: resolveByTitle(media, apiKey) ?: return DetailSupplementalInfo()

        val detailUrl =
            "https://api.themoviedb.org/3/$endpoint/${Uri.encode(tmdbId)}" +
                "?api_key=${Uri.encode(apiKey)}" +
                "&append_to_response=images,videos" +
                "&include_image_language=en,null"

        val json = JSONObject(MetadataHttp.get(detailUrl))
        val logo = chooseLogo(json.optJSONObject("images")?.optJSONArray("logos"), media.originalLanguage)
        val country = json.optJSONArray("production_countries")?.let(::countryLabel)
        val language = json.optString("original_language")
            .trim()
            .takeIf(String::isNotBlank)
            ?.uppercase()
        val status = json.optString("status")
            .trim()
            .takeIf(String::isNotBlank)
            ?.uppercase()
        val releaseDate = json.optString(if (isSeries) "first_air_date" else "release_date")
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(::formatReleaseDate)
        val trailerUrl = trailerUrl(json.optJSONObject("videos")?.optJSONArray("results"))

        return DetailSupplementalInfo(
            logo = logo,
            country = country,
            language = language,
            status = status,
            fullReleaseDate = releaseDate,
            trailerUrl = trailerUrl,
        )
    }

    private suspend fun resolveByTitle(
        media: MediaItem,
        apiKey: String,
    ): String? {
        val query = media.name.trim().takeIf(String::isNotBlank) ?: return null
        val isSeries = MediaTypePolicy.isSeries(media.type)
        val endpoint = if (isSeries) "tv" else "movie"
        val year = media.releaseInfo
            ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value }
        val yearParam = when {
            year == null -> ""
            isSeries -> "&first_air_date_year=${Uri.encode(year)}"
            else -> "&year=${Uri.encode(year)}"
        }
        val url =
            "https://api.themoviedb.org/3/search/$endpoint" +
                "?api_key=${Uri.encode(apiKey)}" +
                "&query=${Uri.encode(query)}" +
                yearParam
        val results = JSONObject(MetadataHttp.get(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val titleField = if (isSeries) "name" else "title"
        val normalizedQuery = query.lowercase().filter(Char::isLetterOrDigit)
        val best = (0 until results.length())
            .mapNotNull(results::optJSONObject)
            .maxByOrNull { candidate ->
                val title = candidate.optString(titleField)
                    .lowercase()
                    .filter(Char::isLetterOrDigit)
                when {
                    title == normalizedQuery -> 3
                    title.contains(normalizedQuery) || normalizedQuery.contains(title) -> 2
                    else -> 1
                }
            }
        val id = best?.optLong("id", -1L) ?: -1L
        return id.takeIf { it > 0L }?.toString()
    }

    private fun formatReleaseDate(raw: String): String =
        runCatching {
            val source = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val date = requireNotNull(source.parse(raw))
            SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(date)
        }.getOrDefault(raw)

    private fun chooseLogo(
        logos: JSONArray?,
        preferredLanguage: String?,
    ): String? {
        if (logos == null || logos.length() == 0) return null
        val preferred = preferredLanguage?.trim()?.lowercase()
        val candidates = (0 until logos.length())
            .mapNotNull(logos::optJSONObject)
            .filter { it.optString("file_path").startsWith("/") }
        val winner = candidates.minByOrNull { logo ->
            when (logo.optString("iso_639_1").trim().lowercase()) {
                "en" -> 0
                preferred -> 1
                "", "null" -> 2
                else -> 3
            }
        } ?: return null
        return "https://image.tmdb.org/t/p/w500${winner.optString("file_path")}"
    }

    private fun countryLabel(countries: JSONArray): String? {
        val names = buildList {
            for (index in 0 until countries.length()) {
                countries.optJSONObject(index)
                    ?.optString("name")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }.distinct().take(2)
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun trailerUrl(videos: JSONArray?): String? {
        if (videos == null) return null
        val candidates = (0 until videos.length())
            .mapNotNull(videos::optJSONObject)
            .filter {
                it.optString("site").equals("YouTube", ignoreCase = true) &&
                    it.optString("type").equals("Trailer", ignoreCase = true) &&
                    it.optString("key").isNotBlank()
            }
        val selected = candidates.firstOrNull { it.optBoolean("official", false) }
            ?: candidates.firstOrNull()
            ?: return null
        return "https://www.youtube.com/watch?v=${selected.optString("key")}"
    }
}
