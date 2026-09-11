package com.vueo.shared.core.home

import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry

object HomeCatalogPolicy {
    fun orderRows(
        rows: List<CatalogRow>,
        catalogOrder: List<String>,
        disabledCatalogKeys: Set<String> = emptySet(),
    ): List<CatalogRow> {
        val enabledRows = if (disabledCatalogKeys.isEmpty()) rows else rows.filterNot { it.id in disabledCatalogKeys }
        if (catalogOrder.isEmpty()) return enabledRows
        val index = catalogOrder.withIndex().associate { it.value to it.index }
        return enabledRows.sortedBy { row -> index[row.id] ?: Int.MAX_VALUE }
    }
}

object HomeRecommendationPolicy {
    private const val FOR_YOU_MIN_MATCH_PERCENT = 55
    private const val DEFAULT_LIMIT = 12

    fun build(
        catalogRows: List<CatalogRow>,
        watchHistory: List<LibraryPlaybackEntry>,
        dnaEngine: UserDnaEngine,
        personalizationEnabled: Boolean,
        limit: Int = DEFAULT_LIMIT,
    ): HomeRecommendationSections {
        if (!personalizationEnabled || limit <= 0) return HomeRecommendationSections()
        val catalogCandidates = catalogRows.asSequence().flatMap { it.items.asSequence() }.distinctBy(::mediaKey).toList()
        val watchedTitleKeys = watchHistory.asSequence().map { mediaKey(it.media) }.toSet()
        val dna = dnaEngine.build()
        val forYou = if (!dna.hasUsefulData) emptyList() else catalogCandidates.asSequence()
            .filterNot { mediaKey(it) in watchedTitleKeys }
            .mapNotNull { candidate -> dnaEngine.matchPercent(media = candidate, dna = dna)?.takeIf { it >= FOR_YOU_MIN_MATCH_PERCENT }?.let { score -> candidate to score } }
            .sortedByDescending { it.second }.take(limit).map { it.first }.toList()
        val seed = watchHistory.asSequence()
            .filter { it.isCompleted || it.positionMs >= 120_000L || it.progressFraction >= .20f }
            .distinctBy { mediaKey(it.media) }.firstOrNull()?.media
        val because = seed?.let { watched ->
            val seedGenres = watched.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            val related = CatalogDiscoveryCache.related(watched, limit = 30)
            val fallback = catalogCandidates.filter { candidate -> candidate.type == watched.type && candidate.genres.any { it.trim().lowercase() in seedGenres } }
            val forYouKeys = forYou.asSequence().map(::mediaKey).toSet()
            val seedKey = mediaKey(watched)
            (related + fallback).asSequence().distinctBy(::mediaKey)
                .filterNot { candidate -> val key=mediaKey(candidate); key == seedKey || key in watchedTitleKeys || key in forYouKeys }
                .take(limit).toList()
        }.orEmpty()
        return HomeRecommendationSections(forYou, seed, because)
    }
    private fun mediaKey(item: MediaItem) = "${item.type}:${item.id}"
}

data class HomeRecommendationSections(
    val forYou: List<MediaItem> = emptyList(),
    val becauseYouWatchedSeed: MediaItem? = null,
    val becauseYouWatched: List<MediaItem> = emptyList(),
)
