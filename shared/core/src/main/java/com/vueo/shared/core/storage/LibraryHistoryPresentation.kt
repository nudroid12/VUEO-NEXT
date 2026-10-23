package com.vueo.shared.core.storage

/** Small local projection of the existing profile-filtered watch history. */
object LibraryHistoryPresentation {
    fun latestTitles(entries: List<LibraryPlaybackEntry>): List<LibraryPlaybackEntry> =
        entries.sortedByDescending { it.lastWatchedEpochMs }
            .distinctBy { "${it.media.type}:${it.media.id}" }

    fun caption(entry: LibraryPlaybackEntry): String {
        val episode = if (entry.season != null && entry.episode != null) {
            "S${entry.season.toString().padStart(2, '0')} E${entry.episode.toString().padStart(2, '0')}"
        } else entry.episodeTitle?.takeIf { it.isNotBlank() }
        val progress = if (entry.durationMs > 0L) {
            "${(entry.progressFraction.coerceIn(0f, 1f) * 100).toInt()}% watched"
        } else if (entry.positionMs > 0L) "${entry.positionMs / 60_000} min watched" else null
        return listOfNotNull(episode, progress).joinToString(" • ").ifBlank { "Watched" }
    }
}
