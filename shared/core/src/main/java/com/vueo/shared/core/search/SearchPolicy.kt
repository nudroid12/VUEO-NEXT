package com.vueo.shared.core.search

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy

/** Pure search normalisation and scoring shared by engine, Mobile and TV. */
object SearchPolicy {
    fun isRelevantEnough(item: MediaItem, query: String): Boolean {
        val normalizedQuery = normalizeText(query)
        val titleQuery = titleQuery(normalizedQuery)
        val title = normalizeText(item.name)
        if (normalizedQuery.isBlank() || titleQuery.isBlank() || title.isBlank()) return false

        val score = relevanceScore(item, normalizedQuery)
        if (score >= 44_000) return true

        val queryTokens = titleQuery.split(' ').filter { it.length >= 2 }
        val titleTokens = title.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return score > 0

        val matched = queryTokens.count { token ->
            titleTokens.any { titleToken ->
                titleToken == token ||
                    titleToken.startsWith(token) ||
                    token.startsWith(titleToken)
            }
        }
        return if (queryTokens.size == 1) matched == 1 else matched == queryTokens.size
    }

    fun canonicalTitle(item: MediaItem): String {
        var title = normalizeText(item.name)
            .replace(Regex("""\s+(19|20)\d{2}$"""), "")

        if (MediaTypePolicy.isSeries(item.type)) {
            title = title
                .replace(Regex("""\s+season\s+\d+.*$"""), "")
                .replace(Regex("""\s+(tv\s+)?series\s*$"""), "")
        }
        return title.trim()
    }

    fun canonicalType(value: String): String = MediaTypePolicy.canonical(value)

    fun relevanceScore(item: MediaItem, query: String): Int {
        val normalizedQuery = normalizeText(query)
        val titleQuery = titleQuery(normalizedQuery)
        val title = normalizeText(item.name)
        if (normalizedQuery.isBlank() || titleQuery.isBlank() || title.isBlank()) return 0

        val queryTokens = titleQuery.split(' ').filter { it.isNotBlank() }
        val titleTokens = title.split(' ').filter { it.isNotBlank() }
        val exactTokenMatches = queryTokens.count { it in titleTokens }
        val prefixTokenMatches = queryTokens.count { token ->
            titleTokens.any { it.startsWith(token) }
        }

        var score = when {
            title == titleQuery -> 120_000
            title.startsWith("$titleQuery ") -> 96_000
            title.contains(" $titleQuery ") || title.endsWith(" $titleQuery") -> 86_000
            title.contains(titleQuery) -> 76_000
            queryTokens.isNotEmpty() && exactTokenMatches == queryTokens.size -> 62_000
            queryTokens.isNotEmpty() && prefixTokenMatches == queryTokens.size -> 52_000
            else -> exactTokenMatches * 6_000 + prefixTokenMatches * 3_000
        }

        if (queryTokens.size > 1 && exactTokenMatches < queryTokens.size) {
            score -= (queryTokens.size - exactTokenMatches) * 4_000
        }

        val queryYear = Regex("""\b(19|20)\d{2}\b""")
            .find(normalizedQuery)
            ?.value
            ?.toIntOrNull()
        if (queryYear != null) {
            score += if (releaseYear(item) == queryYear) 12_000 else -4_000
        }
        return score
    }

    fun metadataScore(item: MediaItem): Int {
        var score = 0
        if (!item.poster.isNullOrBlank()) score += 80
        if (!item.background.isNullOrBlank()) score += 35
        if (!item.description.isNullOrBlank()) score += 30
        if (!item.releaseInfo.isNullOrBlank()) score += 20
        if (item.genres.isNotEmpty()) score += 15
        if (item.catalogSources.isNotEmpty()) score += 12
        score += (((item.imdbRating ?: item.tmdbRating ?: 0.0) * 10.0).toInt())
        return score
    }

    fun releaseYear(item: MediaItem): Int = item.releaseInfo
        ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value?.toIntOrNull() }
        ?: 0

    fun titleQuery(normalizedQuery: String): String = normalizedQuery
        .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

    fun normalizeText(value: String): String = value
        .lowercase()
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")
}
