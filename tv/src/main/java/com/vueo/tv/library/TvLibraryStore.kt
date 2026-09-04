package com.vueo.tv.library

import android.content.Context
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.tv.data.TvMediaItem

class TvLibraryStore(
    context: Context,
) {
    private val delegate =
        LibraryStore(
            context = context.applicationContext,
            prefsName = PREFS_NAME,
            watchlistStorageKey = KEY_LIBRARY,
        )

    fun items(): List<TvMediaItem> = delegate.watchlist()

    fun contains(media: TvMediaItem): Boolean = delegate.isWatchlisted(media)

    fun toggle(media: TvMediaItem): Boolean = delegate.toggleWatchlist(media)

    fun continueWatching(): List<LibraryPlaybackEntry> =
        delegate.continueWatching()

    fun history(): List<LibraryPlaybackEntry> =
        delegate.history()

    fun clearHistory() {
        delegate.clearHistory()
    }

    fun removeFromContinueWatching(
        entry: LibraryPlaybackEntry,
    ) {
        delegate.removeFromContinueWatching(entry)
    }

    companion object {
        const val PREFS_NAME = "vueo_tv_library"
        const val KEY_LIBRARY = "my_list_v1"
    }
}
