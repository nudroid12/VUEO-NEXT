package com.vueo.shared.core.storage

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Pure selection policy shared by Home and both detail screens. */
object ContinueWatchingPolicy {
    fun isSeries(media: MediaItem): Boolean = media.type.lowercase() in setOf("series", "tv")

    fun canResume(entry: LibraryPlaybackEntry): Boolean =
        entry.positionMs > (if (isSeries(entry.media)) 0L else 5_000L) && !entry.isCompleted

    fun resolve(entries: List<LibraryPlaybackEntry>, nowMs: Long = System.currentTimeMillis()): List<LibraryPlaybackEntry> =
        entries.sortedByDescending { it.lastWatchedEpochMs }
            .distinctBy { it.mediaKey }
            .groupBy { "${it.media.type}:${it.media.id}" }
            .values.mapNotNull { titleEntries ->
                if (isSeries(titleEntries.first().media)) resolveSeries(titleEntries, nowMs)
                else titleEntries.firstOrNull { it.isCompleted || it.positionMs > 5_000L }
                    ?.takeUnless { it.isCompleted }
            }.sortedByDescending { it.lastWatchedEpochMs }

    private fun resolveSeries(entries: List<LibraryPlaybackEntry>, nowMs: Long): LibraryPlaybackEntry? {
        val latest = entries.firstOrNull { it.season != null && it.episode != null } ?: return null
        // Even 0:00 is a valid episode cursor; a completed prior episode cannot erase it.
        if (!latest.isCompleted) return latest
        val episodes = entries.flatMap { it.media.episodes }
            .distinctBy { it.season to it.episode }
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
        val currentIndex = episodes.indexOfFirst { it.season == latest.season && it.episode == latest.episode }
        if (currentIndex < 0) return null // retain the cursor in storage until metadata arrives
        val completed = entries.filter { it.isCompleted }.map { it.season to it.episode }.toSet()
        val next = episodes.drop(currentIndex + 1).firstOrNull {
            it.season > 0 && it.episode > 0 && (it.season to it.episode) !in completed && isReleased(it, nowMs)
        } ?: return null // caught up: waiting state stays durable, hidden from the row
        val saved = entries.firstOrNull { it.season == next.season && it.episode == next.episode }
        return latest.copy(
            videoId = next.id, episodeTitle = next.title, season = next.season, episode = next.episode,
            positionMs = saved?.positionMs ?: 0L, durationMs = saved?.durationMs ?: 0L,
        )
    }

    fun isReleased(episode: EpisodeItem, nowMs: Long = System.currentTimeMillis()): Boolean {
        val released = episode.released?.takeIf { it.isNotBlank() } ?: return true
        val releaseMs = runCatching { Instant.parse(released).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(released.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            ?: return true
        return releaseMs <= nowMs
    }

    fun targetEpisode(media: MediaItem, entries: List<LibraryPlaybackEntry>, initial: LibraryPlaybackEntry?): EpisodeItem? {
        val matching = (entries + listOfNotNull(initial)).filter { it.media.id == media.id && it.media.type == media.type }
            .map { it.copy(media = media) }
        val target = resolve(matching).firstOrNull() ?: return null
        return media.episodes.firstOrNull { it.season == target.season && it.episode == target.episode }
    }
}
