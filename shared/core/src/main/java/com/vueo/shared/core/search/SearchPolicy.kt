package com.vueo.shared.core.search

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import java.text.Normalizer

/** Search parsing and relevance shared by the engine, Mobile and TV. */
object SearchPolicy {
    private val yearSuffix = Regex("""^(.+?)(?:\s*\(((?:18|19|20)\d{2})\)|\s+((?:18|19|20)\d{2}))$""")
    private val yearInMetadata = Regex("""\b(?:18|19|20)\d{2}\b""")
    private val combiningMarks = Regex("""\p{M}+""")
    private val separators = Regex("""[^\p{L}\p{N}]+""")
    private val seriesSuffix = Regex("""\s+(?:season\s+\d+.*|(?:tv\s+)?series)$""")

    data class TitleSearch(val original: String, val title: String, val year: Int?, val explicitYear: Boolean)

    fun parseTitleSearch(query: String): TitleSearch {
        val original = query.trim()
        val match = yearSuffix.matchEntire(original) ?: return TitleSearch(original, original, null, false)
        val title = match.groupValues[1].trim()
        if (normalizeText(title).isBlank()) return TitleSearch(original, original, null, false)
        val explicitYear = match.groupValues[2].isNotEmpty()
        val year = match.groupValues[if (explicitYear) 2 else 3].toInt()
        return TitleSearch(original, title, year, explicitYear)
    }

    fun normalizeText(value: String): String = Normalizer
        .normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(separators, " ")
        .trim()

    fun canonicalTitle(item: MediaItem): String {
        val suffix = parseTitleSearch(item.name)
        val name = if (suffix.year != null && suffix.year == releaseYear(item)) suffix.title else item.name
        val title = normalizeText(name)
        return if (MediaTypePolicy.isSeries(item.type)) title.replace(seriesSuffix, "") else title
    }

    fun canonicalType(value: String): String = MediaTypePolicy.canonical(value)

    fun releaseYear(item: MediaItem): Int {
        val metadataYear = item.releaseInfo?.let { yearInMetadata.find(it)?.value?.toIntOrNull() }
        if (metadataYear != null) return metadataYear
        val suffix = parseTitleSearch(item.name)
        return if (suffix.explicitYear) suffix.year ?: 0 else 0
    }

    fun isRelevantEnough(item: MediaItem, query: String): Boolean = relevanceScore(item, query) > 0

    fun relevanceScore(item: MediaItem, query: String): Int = ScoringQuery(parseTitleSearch(query)).score(item)

    private class ScoringQuery(private val query: TitleSearch) {
        private val title = normalizeText(query.title)
        private val compact = title.replace(" ", "")
        private val tokens = title.split(' ')
        private val literal = normalizeText(query.original).replace(" ", "")

        fun score(item: MediaItem): Int {
            if (compact.isEmpty()) return 0
            val sameYear = query.year == null || releaseYear(item) == query.year
            var best = 0
            for ((index, name) in (listOfNotNull(item.name, item.originalTitle) + item.aliases).distinct().withIndex()) {
                val cleanName = normalizeText(name)
                if (cleanName.isEmpty()) continue
                val suffix = parseTitleSearch(name)
                val hasYearLabel = suffix.year != null && suffix.year == releaseYear(item)
                // A bare suffix can also be part of a title (Blade Runner 2049).
                if (query.year != null && !query.explicitYear && !hasYearLabel &&
                    cleanName.replace(" ", "") == literal
                ) {
                    best = maxOf(best, if (index == 0) 130_000 else 129_000)
                    continue
                }
                if (!sameYear) continue
                val candidate = if (hasYearLabel) normalizeText(suffix.title) else cleanName
                val score = match(candidate)
                if (score > 0) best = maxOf(best, if (index == 0) score else score - 100)
            }
            return best
        }

        private fun match(candidate: String): Int {
            val candidateCompact = candidate.replace(" ", "")
            if (candidateCompact == compact) return 120_000
            if (candidate.startsWith("$title ")) return 100_000
            if (candidateCompact.startsWith(compact)) return 96_000
            val words = candidate.split(' ')
            if (tokens.all { it in words }) return 80_000
            if (candidateCompact.contains(compact)) return 76_000
            if (tokens.all { token -> words.any { it.startsWith(token) } }) return 70_000
            // Only score small spelling differences in candidates the sources supplied.
            val maxEdits = if (compact.length >= 9) 2 else 1
            if (compact.length !in 5..64 || kotlin.math.abs(candidateCompact.length - compact.length) > maxEdits) return 0
            val edits = editDistance(compact, candidateCompact, maxEdits)
            return if (edits <= maxEdits) 50_000 - edits * 1_000 else 0
        }
    }

    private fun editDistance(a: String, b: String, maxEdits: Int): Int {
        var twoRowsBack = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        var previousMinimum = 0
        for (i in a.indices) {
            current[0] = i + 1
            var minimum = current[0]
            for (j in b.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
                if (i > 0 && j > 0 && a[i] == b[j - 1] && a[i - 1] == b[j]) {
                    current[j + 1] = minOf(current[j + 1], twoRowsBack[j - 1] + 1)
                }
                minimum = minOf(minimum, current[j + 1])
            }
            if (minimum > maxEdits && previousMinimum >= maxEdits) return maxEdits + 1
            previousMinimum = minimum
            val scratch = twoRowsBack
            twoRowsBack = previous
            previous = current
            current = scratch
        }
        return previous[b.length]
    }

    fun metadataScore(item: MediaItem): Int {
        var score = 0
        if (!item.poster.isNullOrBlank()) score += 80
        if (!item.background.isNullOrBlank()) score += 35
        if (!item.description.isNullOrBlank()) score += 30
        if (!item.releaseInfo.isNullOrBlank()) score += 20
        if (item.genres.isNotEmpty()) score += 15
        if (item.catalogSources.isNotEmpty()) score += 12
        return score + ((item.imdbRating ?: item.tmdbRating ?: 0.0) * 10.0).toInt()
    }

    private data class Ranked(val item: MediaItem, val score: Int)
    private data class TitleIdentity(val title: String, val type: String, val year: Int, val unknownYearId: String?)

    fun orderTitleResults(
        items: List<MediaItem>,
        query: String,
        tieBreak: Comparator<MediaItem>,
    ): List<MediaItem> {
        val scoring = ScoringQuery(parseTitleSearch(query))
        return items.mapNotNull { item -> scoring.score(item).takeIf { it > 0 }?.let { Ranked(item, it) } }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenComparator { a, b ->
                tieBreak.compare(a.item, b.item)
            }).map { it.item }
    }

    fun rankAndDedupe(items: List<MediaItem>, query: String): List<MediaItem> {
        if (normalizeText(query).isBlank()) return items.distinctBy { canonicalType(it.type) to it.id }
        val scoring = ScoringQuery(parseTitleSearch(query))
        val groups = items.asSequence()
            .mapNotNull { item -> scoring.score(item).takeIf { it > 0 }?.let { Ranked(item, it) } }
            .groupBy { (item) ->
                val year = releaseYear(item)
                TitleIdentity(
                    canonicalTitle(item).replace(" ", ""),
                    canonicalType(item.type),
                    year,
                    item.id.takeIf { year == 0 },
                )
            }

        return groups.values.map { group ->
            val best = group.maxWith(compareBy<Ranked> { it.score }.thenBy { metadataScore(it.item) })
            best.copy(item = best.item.copy(
                genres = group.flatMap { it.item.genres }.distinctBy { it.lowercase() },
                catalogSources = group.flatMap { it.item.catalogSources }.map { it.trim() }
                    .filter { it.isNotBlank() }.distinctBy { it.lowercase() },
                popularity = group.mapNotNull { it.item.popularity }.maxOrNull(),
            ))
        }.sortedWith(compareByDescending<Ranked> { it.score }
            .thenByDescending { it.item.popularity ?: 0.0 }
            .thenByDescending { metadataScore(it.item) }
            .thenBy { it.item.name }
            .thenBy { it.item.id })
            .map { it.item }
            .distinctBy { canonicalType(it.type) to it.id }
    }
}
