package com.vueo.tv.detail

import android.net.Uri
import com.vueo.shared.core.enrichment.MetadataHttp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.plugin.TmdbResolver
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

internal data class TvDetailNuvioExtras(
    val logo: String? = null,
    val country: String? = null,
    val language: String? = null,
    val status: String? = null,
    val fullReleaseDate: String? = null,
    val leadingCrew: List<MediaPerson> = emptyList(),
    val trailerUrl: String? = null,
)

internal suspend fun loadTvDetailNuvioExtras(
    media: MediaItem,
    tmdbApiKey: String,
): TvDetailNuvioExtras {
    val apiKey = tmdbApiKey.trim()
    if (apiKey.isBlank()) return TvDetailNuvioExtras()

    val isSeries = media.isDetailSeries()
    val endpoint = if (isSeries) "tv" else "movie"
    val tmdbId = runCatching {
        TmdbResolver.resolve(
            rawId = media.id,
            mediaType = media.type,
            apiKey = apiKey,
        )
    }.getOrNull() ?: resolveTvDetailTmdbIdByTitle(media, apiKey) ?: return TvDetailNuvioExtras()

    val detailUrl =
        "https://api.themoviedb.org/3/$endpoint/${Uri.encode(tmdbId)}" +
            "?api_key=${Uri.encode(apiKey)}" +
            "&append_to_response=credits,images,videos" +
            "&include_image_language=en,null"

    val json = JSONObject(MetadataHttp.get(detailUrl))
    val logo = chooseTvDetailLogo(json.optJSONObject("images")?.optJSONArray("logos"), media.originalLanguage)
    val country = json.optJSONArray("production_countries")
        ?.let(::tvDetailCountryLabel)
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
        ?.let(::formatTvDetailReleaseDate)
    val leadingCrew = tvDetailLeadingCrew(json, isSeries)
    val trailerUrl = tvDetailTrailerUrl(json.optJSONObject("videos")?.optJSONArray("results"))

    return TvDetailNuvioExtras(
        logo = logo,
        country = country,
        language = language,
        status = status,
        fullReleaseDate = releaseDate,
        leadingCrew = leadingCrew,
        trailerUrl = trailerUrl,
    )
}

private suspend fun resolveTvDetailTmdbIdByTitle(
    media: MediaItem,
    apiKey: String,
): String? {
    val query = media.name.trim().takeIf(String::isNotBlank) ?: return null
    val endpoint = if (media.isDetailSeries()) "tv" else "movie"
    val year = media.releaseInfo
        ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value }
    val yearParam = when {
        year == null -> ""
        media.isDetailSeries() -> "&first_air_date_year=${Uri.encode(year)}"
        else -> "&year=${Uri.encode(year)}"
    }
    val url =
        "https://api.themoviedb.org/3/search/$endpoint" +
            "?api_key=${Uri.encode(apiKey)}" +
            "&query=${Uri.encode(query)}" +
            yearParam
    val results = JSONObject(MetadataHttp.get(url)).optJSONArray("results") ?: return null
    if (results.length() == 0) return null

    val titleField = if (media.isDetailSeries()) "name" else "title"
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

private fun formatTvDetailReleaseDate(raw: String): String =
    runCatching {
        val source = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val date = requireNotNull(source.parse(raw))
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(date)
    }.getOrDefault(raw)

private fun chooseTvDetailLogo(
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

private fun tvDetailCountryLabel(countries: JSONArray): String? {
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

private fun tvDetailLeadingCrew(
    json: JSONObject,
    isSeries: Boolean,
): List<MediaPerson> {
    val result = mutableListOf<MediaPerson>()
    if (isSeries) {
        val creators = json.optJSONArray("created_by")
        if (creators != null) {
            for (index in 0 until creators.length()) {
                val person = creators.optJSONObject(index) ?: continue
                val name = person.optString("name").trim()
                if (name.isBlank()) continue
                result += MediaPerson(
                    name = name,
                    character = "Creator",
                    role = "Creator",
                    profile = person.optString("profile_path")
                        .takeIf { it.startsWith("/") }
                        ?.let { "https://image.tmdb.org/t/p/w185$it" },
                )
            }
        }
    }

    val crew = json.optJSONObject("credits")?.optJSONArray("crew")
    if (crew != null) {
        val wantedJobs = if (isSeries) {
            setOf("Director", "Writer", "Screenplay", "Teleplay")
        } else {
            setOf("Director", "Writer", "Screenplay")
        }
        for (index in 0 until crew.length()) {
            val person = crew.optJSONObject(index) ?: continue
            val job = person.optString("job").trim()
            if (job !in wantedJobs) continue
            val name = person.optString("name").trim()
            if (name.isBlank()) continue
            result += MediaPerson(
                name = name,
                character = job,
                role = job,
                profile = person.optString("profile_path")
                    .takeIf { it.startsWith("/") }
                    ?.let { "https://image.tmdb.org/t/p/w185$it" },
            )
        }
    }

    return result
        .distinctBy { it.name.trim().lowercase() + "|" + it.character.orEmpty() }
        .take(4)
}

private fun tvDetailTrailerUrl(videos: JSONArray?): String? {
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
