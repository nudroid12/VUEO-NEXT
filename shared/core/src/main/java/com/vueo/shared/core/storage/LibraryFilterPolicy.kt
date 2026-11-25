package com.vueo.shared.core.storage

import android.content.Context

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.search.SearchPolicy
import java.text.Collator
import java.util.Locale

enum class LibraryTypeFilter(val label: String) {
    ALL("All"), MOVIES("Movies"), SERIES("Series")
}

enum class LibrarySort(val label: String) {
    RECENT("Recent"), TITLE("Title A–Z"), YEAR("Release Year");

    fun label(history: Boolean): String =
        if (this == RECENT) if (history) "Recently Watched" else "Recently Added" else label
}

object LibraryFilterPolicy {
    /** Input order is the existing watchlist-added or history-watched timestamp order. */
    fun apply(items: List<MediaItem>, type: LibraryTypeFilter, sort: LibrarySort): List<MediaItem> {
        val filtered = items.filter { item ->
            when (type) {
                LibraryTypeFilter.ALL -> true
                LibraryTypeFilter.MOVIES -> MediaTypePolicy.canonical(item.type) == "movie"
                LibraryTypeFilter.SERIES -> MediaTypePolicy.isSeries(item.type)
            }
        }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val titleComparator = Comparator<MediaItem> { first, second ->
            collator.compare(first.name, second.name)
        }
        return when (sort) {
            LibrarySort.RECENT -> filtered
            LibrarySort.TITLE -> filtered.sortedWith(titleComparator)
            LibrarySort.YEAR -> filtered.sortedWith(compareByDescending<MediaItem> { SearchPolicy.releaseYear(it) }.then(titleComparator))
        }
    }
}

data class LibraryFilters(
    val type: LibraryTypeFilter = LibraryTypeFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
)

class LibraryFilterStore(context: Context, private val profileId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("vueo_library", Context.MODE_PRIVATE)
    private fun key(history: Boolean) = ProfileStore.scopedPreferenceKey(profileId,
        if (history) "history_filter_v1" else "watchlist_filter_v1")

    fun read(history: Boolean): LibraryFilters {
        val values = prefs.getString(key(history), "").orEmpty().split(':')
        return LibraryFilters(
            LibraryTypeFilter.entries.firstOrNull { it.name == values.getOrNull(0) } ?: LibraryTypeFilter.ALL,
            LibrarySort.entries.firstOrNull { it.name == values.getOrNull(1) } ?: LibrarySort.RECENT,
        )
    }

    fun save(history: Boolean, filters: LibraryFilters) {
        prefs.edit().putString(key(history), "${filters.type.name}:${filters.sort.name}").apply()
    }
}
