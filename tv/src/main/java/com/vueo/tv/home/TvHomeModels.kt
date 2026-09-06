package com.vueo.tv.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry

internal const val MODERN_HOME_HERO_TEXT_WIDTH_FRACTION = 0.42f
internal const val MODERN_HOME_HERO_MEDIA_WIDTH_FRACTION = 0.72f
internal const val MODERN_HOME_ROWS_VIEWPORT_FRACTION = 0.52f
internal const val MODERN_HOME_HERO_FOCUS_SETTLE_MS = 450L

internal sealed interface TvHomeEntry {
    val key: String
    val media: MediaItem

    data class Media(
        override val key: String,
        override val media: MediaItem,
    ) : TvHomeEntry

    data class Resume(
        override val key: String,
        override val media: MediaItem,
        val playback: LibraryPlaybackEntry,
    ) : TvHomeEntry
}

internal enum class TvHomeRowKind {
    CONTINUE_WATCHING,
    POSTERS,
}

internal data class TvHomeRow(
    val key: String,
    val title: String,
    val kind: TvHomeRowKind,
    val entries: List<TvHomeEntry>,
)

/**
 * Small process-lifetime focus snapshot, equivalent to the focus state Nuvio
 * keeps for Home. It remembers the active row and the focused item per row,
 * but it does not own navigation or data.
 */
internal object TvHomeFocusMemory {
    var activeRowKey: String? = null
    val focusedIndexByRow = mutableMapOf<String, Int>()
}

internal fun TvHomeEntry.open(
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
) {
    when (this) {
        is TvHomeEntry.Media -> onOpenMedia(media)
        is TvHomeEntry.Resume -> onResume(playback)
    }
}

internal fun TvHomeEntry.remainingText(): String? {
    val resume = this as? TvHomeEntry.Resume ?: return null
    val remainingMs = (resume.playback.durationMs - resume.playback.positionMs).coerceAtLeast(0L)
    if (remainingMs <= 0L) return null
    return formatMinutes(remainingMs / 60_000L) + " left"
}

internal fun TvHomeEntry.episodeText(): String? {
    val resume = this as? TvHomeEntry.Resume ?: return null
    val season = resume.playback.season
    val episode = resume.playback.episode
    return when {
        season != null && episode != null -> "S${season} E${episode}"
        !resume.playback.episodeTitle.isNullOrBlank() -> resume.playback.episodeTitle
        else -> null
    }
}

internal fun MediaItem.heroPrimaryMeta(): String {
    val parts = buildList {
        add(displayType)
        genres.take(2).filter { it.isNotBlank() }.forEach(::add)
        releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return parts.joinToString("  •  ")
}

internal fun TvHomeEntry.heroSecondaryMeta(): String {
    val parts = buildList {
        if (this@heroSecondaryMeta is TvHomeEntry.Resume) {
            episodeText()?.let(::add)
            remainingText()?.let(::add)
        } else {
            media.runtimeMinutes?.takeIf { it > 0 }?.let { add(formatMinutes(it.toLong())) }
        }
        media.imdbRating?.takeIf { it > 0.0 }?.let { rating ->
            add("IMDb ${"%.1f".format(java.util.Locale.US, rating)}")
        }
    }
    return parts.distinct().joinToString("  •  ")
}

private fun formatMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
