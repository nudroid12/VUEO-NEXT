package com.vueo.shared.core.enrichment

import org.json.JSONObject

data class ContentWarning(
    val label: String,
    val severity: String,
    val severityRank: Int,
)

object ContentWarningRepository {
    private const val BASE_URL = "https://api.tiffara.com"
    private val cache = mutableMapOf<String, List<ContentWarning>>()

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
        }.getOrDefault(emptyList())

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
