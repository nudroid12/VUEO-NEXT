package com.vueo.tv.library

import android.content.Context
import com.vueo.shared.core.storage.MediaLibraryStore
import com.vueo.tv.data.TvMediaItem

class TvLibraryStore(
    context: Context,
) {
    private val delegate =
        MediaLibraryStore(
            context = context.applicationContext,
            prefsName = PREFS_NAME,
            storageKey = KEY_LIBRARY,
        )

    fun items(): List<TvMediaItem> = delegate.items()

    fun contains(media: TvMediaItem): Boolean = delegate.contains(media)

    fun toggle(media: TvMediaItem): Boolean = delegate.toggle(media)

    companion object {
        private const val PREFS_NAME = "vueo_tv_library"
        private const val KEY_LIBRARY = "my_list_v1"
    }
}
