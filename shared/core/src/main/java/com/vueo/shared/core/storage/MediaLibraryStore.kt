package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.MediaItem

/**
 * Compatibility facade retained for TV and older call sites.
 * The canonical implementation is now [LibraryStore].
 */
class MediaLibraryStore(
    context: Context,
    prefsName: String,
    storageKey: String = "my_list_v1",
) {
    private val delegate =
        LibraryStore(
            context = context.applicationContext,
            prefsName = prefsName,
            watchlistStorageKey = storageKey,
        )

    fun items(): List<MediaItem> = delegate.watchlist()

    fun contains(media: MediaItem): Boolean = delegate.isWatchlisted(media)

    fun toggle(media: MediaItem): Boolean = delegate.toggleWatchlist(media)
}
