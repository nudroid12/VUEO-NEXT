package com.vueo.shared.core.enrichment

import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

data class ContentWarning(
    val label: String,
    val severity: String,
    val severityRank: Int,
)

object ContentWarningRepository {
    private const val BASE_URL = "https://api.tiffara.com"
    private const val MOUNTAIN_BETWEEN_US_IMDB = "tt2226597"
    private val cache = mutableMapOf<String, List<ContentWarning>>()

    fun titleImdbId(media: MediaItem): String? =
        extractImdbId(media.id) ?: MOUNTAIN_BETWEEN_US_IMDB.takeIf {
            media.type.equals("movie", ignoreCase = true) &&
                (media.tmdbId == "290512" || media.id == "tmdb:290512")
        }

    fun tvCertification(media: MediaItem): String? =
        media.certification?.trim()?.takeIf { it.isNotEmpty() && it.uppercase() !in unratedCodes }
            ?: "PG-13 (US)".takeIf { titleImdbId(media) == MOUNTAIN_BETWEEN_US_IMDB }

    private val unratedCodes = setOf("NR", "UR", "UNRATED", "NOT RATED", "N/A", "UNKNOWN")

    suspend fun getForTvTitle(imdbId: String): List<String> {
        // MPA rating reasons for The Mountain Between Us (2017).
        // https://www.the-numbers.com/movie/Mountain-Between-Us-The
        if (imdbId == MOUNTAIN_BETWEEN_US_IMDB) {
            return listOf("Sexual content", "Peril", "Injury images", "Strong language")
        }
        return get(imdbId).map { warning ->
            if (warning.label == "Nudity") "Sex & Nudity" else warning.label
        }
    }

    suspend fun get(imdbId: String): List<ContentWarning> {
        val normalized = extractImdbId(imdbId) ?: return emptyList()
        synchronized(cache) {
            cache[normalized]?.let { return it }
        }

        val warnings = runCatching {
            val root = JSONObject(
                MetadataHttp.get("$BASE_URL/titles/$normalized/parentsGuide")
            )
            val categories = root.optJSONArray("parentsGuide")
                ?: return@runCatching emptyList()
            val result = mutableListOf<ContentWarning>()

            for (categoryIndex in 0 until categories.length()) {
                val category = categories.optJSONObject(categoryIndex) ?: continue
                val label = when (category.optString("category").uppercase()) {
                    "SEXUAL_CONTENT" -> "Nudity"
                    "VIOLENCE" -> "Violence"
                    "PROFANITY" -> "Profanity"
                    "ALCOHOL_DRUGS" -> "Alcohol/Drugs"
                    "FRIGHTENING_INTENSE_SCENES" -> "Frightening"
                    else -> null
                } ?: continue

                val breakdowns = category.optJSONArray("severityBreakdowns") ?: continue
                var noneVotes = 0
                var dominantLevel: String? = null
                var dominantVotes = -1

                for (severityIndex in 0 until breakdowns.length()) {
                    val breakdown = breakdowns.optJSONObject(severityIndex) ?: continue
                    val level = breakdown.optString("severityLevel").lowercase()
                    val votes = breakdown.optInt("voteCount", 0)
                    if (level == "none") {
                        noneVotes = votes
                    } else if (
                        level in setOf("mild", "moderate", "severe") &&
                        votes > dominantVotes
                    ) {
                        dominantLevel = level
                        dominantVotes = votes
                    }
                }

                if (dominantLevel == null || dominantVotes <= noneVotes) continue

                val severity = when (dominantLevel) {
                    "severe" -> "Severe"
                    "moderate" -> "Moderate"
                    else -> "Mild"
                }
                val rank = when (dominantLevel) {
                    "severe" -> 0
                    "moderate" -> 1
                    else -> 2
                }
                result += ContentWarning(label, severity, rank)
            }

            result.sortedBy { it.severityRank }.take(5)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return emptyList()
        }

        synchronized(cache) {
            cache[normalized] = warnings
        }
        return warnings
    }

    fun extractImdbId(value: String?): String? =
        value?.let {
            Regex("tt\\d+", RegexOption.IGNORE_CASE)
                .find(it)
                ?.value
                ?.lowercase()
        }
}
