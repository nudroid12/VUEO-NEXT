package com.vueo.shared.core.search

import com.vueo.shared.core.media.MediaItem

/**
 * Mobile search result ordering promoted to Shared Core so Mobile and TV use
 * the same ranking semantics instead of maintaining copy-pasted sort logic.
 */
object SearchResultOrderPolicy {

    fun matchesType(
        item: MediaItem,
        filter: SearchMediaFilter,
        animeCatalogKeys: Set<String>,
    ): Boolean = when (filter) {
        SearchMediaFilter.ALL -> true
        SearchMediaFilter.MOVIES ->
            item.type.equals("movie", ignoreCase = true) && !isAnime(item, animeCatalogKeys)
        SearchMediaFilter.SERIES ->
            item.type.equals("series", ignoreCase = true) && !isAnime(item, animeCatalogKeys)
        SearchMediaFilter.ANIME -> isAnime(item, animeCatalogKeys)
    }

    fun matchesGenre(item: MediaItem, genre: String?): Boolean =
        genre == null || item.genres.any { it.equals(genre, ignoreCase = true) }

    fun isAnime(item: MediaItem, animeCatalogKeys: Set<String>): Boolean {
        if (
            item.type.equals("anime", ignoreCase = true) ||
            item.genres.any { it.equals("anime", ignoreCase = true) }
        ) return true

        if ("${item.type}:${item.id}" in animeCatalogKeys) return true

        return listOfNotNull(item.sourceExtensionId, item.id)
            .any { it.contains("anime", ignoreCase = true) }
    }
    fun sortActorItems(
        items: List<MediaItem>,
        mode: DiscoverSortMode,
    ): List<MediaItem> =
        when (mode) {
            DiscoverSortMode.POPULAR,
            DiscoverSortMode.TRENDING -> items
            DiscoverSortMode.NEWEST -> items.sortedByDescending(SearchPolicy::releaseYear)
        }

    fun sortTitleItems(
        items: List<MediaItem>,
        mode: DiscoverSortMode,
        query: String? = null,
    ): List<MediaItem> {
        val normalizedQuery = query?.let(SearchPolicy::normalizeText).orEmpty()

        if (normalizedQuery.isBlank()) {
            return when (mode) {
                DiscoverSortMode.POPULAR -> items.sortedByDescending {
                    it.imdbRating ?: it.tmdbRating ?: 0.0
                }
                DiscoverSortMode.TRENDING -> items
                DiscoverSortMode.NEWEST -> items.sortedByDescending(SearchPolicy::releaseYear)
            }
        }

        val relevance = compareByDescending<MediaItem> {
            SearchPolicy.relevanceScore(it, normalizedQuery)
        }

        return when (mode) {
            DiscoverSortMode.POPULAR -> items.sortedWith(
                relevance
                    .thenByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
                    .thenByDescending(SearchPolicy::releaseYear)
            )
            DiscoverSortMode.TRENDING -> items.sortedWith(
                relevance.thenByDescending(SearchPolicy::metadataScore)
            )
            DiscoverSortMode.NEWEST -> items.sortedWith(
                relevance
                    .thenByDescending(SearchPolicy::releaseYear)
                    .thenByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
            )
        }
    }
}

enum class SearchMediaFilter {
    ALL,
    MOVIES,
    SERIES,
    ANIME,
}
