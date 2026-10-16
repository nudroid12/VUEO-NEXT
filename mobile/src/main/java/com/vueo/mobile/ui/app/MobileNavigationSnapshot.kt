package com.vueo.mobile.ui

import com.vueo.shared.core.media.MediaItem

/** Small Bundle payload, never a catalog, episode list, playback URL or credentials. */
internal object MobileNavigationSnapshot {
    private const val VERSION = "1"
    private const val FIELDS = 12
    const val MAX_BACK_STACK = 8

    fun media(item: MediaItem?): List<String> {
        if (item == null || item.id.isBlank() || item.id.length > 1024 ||
            item.type.isBlank() || item.type.length > 64 ||
            item.sourceExtensionId.orEmpty().length > 512
        ) return listOf(VERSION)
        fun String?.bounded(limit: Int) = this?.takeIf { it.length <= limit }.orEmpty()
        return listOf(VERSION, item.id, item.type, item.name.take(512),
            item.poster.bounded(2048), item.background.bounded(2048),
            item.sourceExtensionId.orEmpty(), item.releaseInfo.bounded(128),
            item.certification.bounded(64), item.originalTitle.bounded(512),
            item.imdbRating?.toString().orEmpty(), item.tmdbRating?.toString().orEmpty())
    }

    fun restoreMedia(saved: List<String>): MediaItem? {
        if (saved.size != FIELDS || saved[0] != VERSION || saved[1].isBlank() ||
            saved[1].length > 1024 || saved[2].isBlank() || saved[2].length > 64 ||
            saved.any { it.length > 2048 } || saved[3].length > 512 ||
            saved[6].length > 512 || saved[7].length > 128 || saved[8].length > 64 ||
            saved[9].length > 512
        ) return null
        fun text(index: Int) = saved[index].takeIf { it.isNotEmpty() }
        fun rating(index: Int) = saved[index].toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10.0 }
        return MediaItem(id = saved[1], type = saved[2], name = saved[3],
            poster = text(4), background = text(5), sourceExtensionId = text(6),
            releaseInfo = text(7), certification = text(8), originalTitle = text(9),
            imdbRating = rating(10), tmdbRating = rating(11))
    }

    fun stack(items: List<MediaItem>): List<String> =
        listOf(VERSION) + items.takeLast(MAX_BACK_STACK).map(::media)
            .filter { restoreMedia(it) != null }.flatten()

    fun restoreStack(saved: List<String>): List<MediaItem> {
        if (saved.firstOrNull() != VERSION || saved.size > 1 + MAX_BACK_STACK * FIELDS ||
            (saved.size - 1) % FIELDS != 0
        ) return emptyList()
        val restored = saved.drop(1).chunked(FIELDS).map(::restoreMedia)
        return if (restored.any { it == null }) emptyList() else restored.filterNotNull()
    }

    fun mayResumeRoute(savedOwner: String, activeOwner: String, openedFromApp: Boolean): Boolean =
        savedOwner == activeOwner && !openedFromApp
}
