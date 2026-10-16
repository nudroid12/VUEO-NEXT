package com.vueo.mobile.ui

import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.profile.KidsContentPolicy
import com.vueo.shared.core.search.DiscoverCatalogPolicy
import com.vueo.shared.core.search.DiscoverSortMode
import com.vueo.shared.core.search.SearchMediaFilter
import com.vueo.shared.core.search.SearchResultOrderPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class MobileSearchPreparationInput(
    val rows: List<CatalogRow>,
    val results: List<MediaItem>,
    val query: String,
    val actor: Boolean,
    val profileId: String,
    val kidsLimit: Int?,
    val contentVersion: Int,
)
internal data class MobileSearchPreparedSource(
    val items: List<MediaItem>,
    val genres: List<String>,
)
internal data class MobileSearchPreparedResult(
    val items: List<MediaItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val gridRows: List<List<MediaItem>> = emptyList(),
)

/** Caller runs this on Default; retain shared classification/order semantics. */
internal suspend fun prepareMobileSearchSource(
    input: MobileSearchPreparationInput,
    type: SearchMediaFilter,
    sort: DiscoverSortMode,
): MobileSearchPreparedSource {
    val job = currentCoroutineContext()
    job.ensureActive()
    val animeKeys = mutableSetOf<String>()
    input.rows.forEach { row ->
        job.ensureActive()
        if (listOf(row.id, row.title, row.providerName).any { it.contains("anime", ignoreCase = true) }) {
            row.items.forEachIndexed { index, item ->
                if (index % 128 == 0) job.ensureActive()
                animeKeys += "${item.type}:${item.id}"
            }
        }
    }
    val searching = input.query.isNotBlank()
    val source = if (searching) input.results else DiscoverCatalogPolicy.baseItems(input.rows, sort)
    val typed = ArrayList<MediaItem>()
    source.forEachIndexed { index, item ->
        if (index % 128 == 0) job.ensureActive()
        if (KidsContentPolicy.allows(item.certification, input.kidsLimit) &&
            SearchResultOrderPolicy.matchesType(item, type, animeKeys)) typed += item
    }
    val genres = typed.asSequence().flatMap { it.genres.asSequence() }.map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("anime", ignoreCase = true) }
        .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.toList()
    job.ensureActive()
    val ordered = when {
        searching && input.actor -> SearchResultOrderPolicy.sortActorItems(typed, sort)
        searching -> SearchResultOrderPolicy.sortTitleItems(typed, sort, input.query)
        else -> DiscoverCatalogPolicy.orderFiltered(typed, sort)
    }
    job.ensureActive()
    return MobileSearchPreparedSource(ordered, genres)
}

internal suspend fun prepareMobileSearchGenre(
    source: MobileSearchPreparedSource,
    genre: String?,
): MobileSearchPreparedResult {
    val job = currentCoroutineContext()
    job.ensureActive()
    val items = if (genre == null) source.items else source.items.filterIndexed { index, item ->
        if (index % 128 == 0) job.ensureActive()
        SearchResultOrderPolicy.matchesGenre(item, genre)
    }
    val rows = items.chunked(3)
    job.ensureActive()
    return MobileSearchPreparedResult(items, source.genres, rows)
}
