package com.vueo.tv.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import androidx.compose.ui.unit.dp

internal const val MODERN_HOME_HERO_TEXT_WIDTH_FRACTION = 0.46f
internal const val MODERN_HOME_HERO_MEDIA_WIDTH_FRACTION = 0.86f
internal const val MODERN_HOME_ROWS_VIEWPORT_FRACTION = 0.52f
internal const val MODERN_HOME_EXPANDED_ROWS_VIEWPORT_FRACTION = 0.32f
internal val MODERN_HOME_POSTER_HEIGHT = 172.dp
internal val MODERN_HOME_LOGO_WIDTH = 260.dp
internal val MODERN_HOME_LOGO_HEIGHT = 116.dp
internal val MODERN_HOME_HERO_COPY_BOTTOM_GAP = 22.dp
internal const val MODERN_HOME_HERO_FOCUS_SETTLE_MS = 450L
internal val MODERN_HOME_CONTENT_START_PADDING = 96.dp

/** One resolved item supplies both hero planes; navigation never waits for it. */
internal data class TvHomeHeroScene(
    val entry: TvHomeEntry,
    val artwork: com.vueo.shared.core.detail.DetailSupplementalInfo?,
)

internal sealed interface TvHomeEntry {
    val key: String
    val media: MediaItem

    data class Media(
        override val key: String,
        override val media: MediaItem,
        val newEpisodeLabel: String? = null,
    ) : TvHomeEntry

    data class Folder(
        val collectionId: String,
        val folder: com.vueo.shared.core.collections.CollectionFolder,
    ) : TvHomeEntry {
        override val key = "collection:$collectionId:${folder.id}"
        override val media = MediaItem(key, "collection-folder", folder.title, poster = folder.coverImageUrl, background = folder.coverImageUrl)
    }

    data class Resume(
        override val key: String,
        override val media: MediaItem,
        val playback: LibraryPlaybackEntry,
    ) : TvHomeEntry
}

internal const val NEW_EPISODES_ROW_KEY = "new-episodes"

internal enum class TvHomeRowKind {
    CONTINUE_WATCHING,
    POSTERS,
    COLLECTIONS,
}

internal data class TvHomeRow(
    val key: String,
    val title: String,
    val kind: TvHomeRowKind,
    val entries: List<TvHomeEntry>,
    val providerName: String? = null,
)

internal fun TvHomeRow.isDynamicEpisodeRail(): Boolean =
    kind == TvHomeRowKind.CONTINUE_WATCHING || key == NEW_EPISODES_ROW_KEY

/**
 * Small process-lifetime focus snapshot, equivalent to the focus state Vueo
 * keeps for Home. It remembers the active row and the focused item per row,
 * but it does not own navigation or data.
 */
/** First displayed item and exact pixel offset, never a focused-card index. */
internal data class TvHomeHorizontalPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

/** Small value-only snapshot retained across Home -> Details -> Sources. */
internal data class HomeRouteFocusAnchor(
    val rowKey: String,
    val cardKey: String,
    val cardIndex: Int,
    val horizontal: TvHomeHorizontalPosition,
    val verticalIndex: Int,
    val verticalOffset: Int,
    val horizontalFirstCardKey: String? = null,
    val restoreReady: Boolean = true,
    val capturedRowIndex: Int = -1,
    val verticalFirstRowKey: String? = null,
)

internal object TvHomeFocusMemory {
    var activeRowKey: String? = null
    val focusedIndexByRow = mutableMapOf<String, Int>()
    val focusedKeyByRow = mutableMapOf<String, String>()
    val horizontalPositionByRow = mutableMapOf<String, TvHomeHorizontalPosition>()
}

internal fun TvHomeEntry.open(
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onFolder: (String, String) -> Unit,
) {
    when (this) {
        is TvHomeEntry.Folder -> onFolder(collectionId, folder.id)
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
            (this@heroSecondaryMeta as? TvHomeEntry.Media)?.newEpisodeLabel?.let { add("New episode · $it") }
            media.runtimeMinutes?.takeIf { it > 0 }?.let { add(formatMinutes(it.toLong())) }
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

internal fun TvHomeRow.savedFocusIndex(fallback: Int = 0): Int {
    val savedIndex = (TvHomeFocusMemory.focusedIndexByRow[key] ?: fallback)
        .coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    val savedKey = TvHomeFocusMemory.focusedKeyByRow[key] ?: return savedIndex
    if (entries.getOrNull(savedIndex)?.key == savedKey) return savedIndex
    return entries.indexOfFirst { it.key == savedKey }.takeIf { it >= 0 } ?: savedIndex
}

internal fun catalogCardKey(rowKey: String, media: MediaItem): String =
    "$rowKey:${media.type}:${media.id}"
