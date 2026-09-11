package com.vueo.shared.core.search

import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem

object DiscoverCatalogPolicy {
    fun baseItems(rows: List<CatalogRow>, mode: DiscoverSortMode): List<MediaItem> =
        rows.sortedByDescending { catalogPriority(it, mode) }.flatMap { it.items }.distinctBy { "${it.type}:${it.id}" }
    fun orderFiltered(items: List<MediaItem>, mode: DiscoverSortMode): List<MediaItem> =
        if (mode == DiscoverSortMode.NEWEST) items.sortedByDescending(SearchPolicy::releaseYear) else items
    fun catalogPriority(row: CatalogRow, mode: DiscoverSortMode): Int {
        val value = "${row.id} ${row.title}".lowercase()
        return when(mode) {
            DiscoverSortMode.POPULAR -> when { "popular" in value -> 100; "top" in value -> 80; else -> 0 }
            DiscoverSortMode.TRENDING -> when { "trending" in value || "trend" in value -> 100; "popular" in value -> 60; else -> 0 }
            DiscoverSortMode.NEWEST -> when { "new" in value || "latest" in value -> 100; "recent" in value -> 90; "release" in value -> 80; else -> 0 }
        }
    }
}
enum class DiscoverSortMode { POPULAR, TRENDING, NEWEST }
