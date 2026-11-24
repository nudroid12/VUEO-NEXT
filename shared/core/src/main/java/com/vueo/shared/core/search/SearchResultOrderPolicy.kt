package com.vueo.shared.core.search

import com.vueo.shared.core.media.AnimeMediaPolicy
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy

object SearchResultOrderPolicy {
    fun matchesType(
        item: MediaItem,
        filter: SearchMediaFilter,
        animeCatalogKeys: Set<String>,
    ): Boolean = when (filter) {
        SearchMediaFilter.ALL -> true
        SearchMediaFilter.MOVIES -> MediaTypePolicy.isMovie(item.type) && !isAnime(item, animeCatalogKeys)
        SearchMediaFilter.SERIES -> MediaTypePolicy.isSeries(item.type) && !isAnime(item, animeCatalogKeys)
        SearchMediaFilter.ANIME -> isAnime(item, animeCatalogKeys)
    }

    fun matchesGenre(item: MediaItem, genre: String?): Boolean =
        genre == null || item.genres.any { it.equals(genre, ignoreCase = true) }

    fun isAnime(item: MediaItem, animeCatalogKeys: Set<String>): Boolean =
        AnimeMediaPolicy.isAnime(item, animeCatalogKeys)

    fun sortActorItems(items: List<MediaItem>, mode: DiscoverSortMode): List<MediaItem> = when (mode) {
        DiscoverSortMode.POPULAR, DiscoverSortMode.TRENDING -> items
        DiscoverSortMode.NEWEST -> items.sortedByDescending(SearchPolicy::releaseYear)
    }

    fun sortTitleItems(items: List<MediaItem>, mode: DiscoverSortMode, query: String? = null): List<MediaItem> {
        if (query.isNullOrBlank() || SearchPolicy.normalizeText(query).isBlank()) {
            return when (mode) {
                DiscoverSortMode.POPULAR -> items.sortedByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
                DiscoverSortMode.TRENDING -> items
                DiscoverSortMode.NEWEST -> items.sortedByDescending(SearchPolicy::releaseYear)
            }
        }
        val popularity = compareByDescending<MediaItem> { it.popularity ?: 0.0 }
        val rating = compareByDescending<MediaItem> { it.imdbRating ?: it.tmdbRating ?: 0.0 }
        val tieBreak = when (mode) {
            DiscoverSortMode.POPULAR -> popularity.then(rating).thenByDescending(SearchPolicy::releaseYear)
            DiscoverSortMode.TRENDING -> popularity.thenByDescending(SearchPolicy::metadataScore)
            DiscoverSortMode.NEWEST -> compareByDescending(SearchPolicy::releaseYear).then(popularity).then(rating)
        }
        return SearchPolicy.orderTitleResults(items, query, tieBreak)
    }
}

enum class SearchMediaFilter { ALL, MOVIES, SERIES, ANIME }
