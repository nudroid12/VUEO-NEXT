package com.vueo.shared.core.storage

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class NewEpisodeEntry(
    val media: MediaItem,
    val episode: EpisodeItem,
    val releasedAtMs: Long,
) {
    val key: String get() = "${media.type}:${media.id}"
    val episodeLabel: String get() = "S${episode.season.toString().padStart(2, '0')} E${episode.episode.toString().padStart(2, '0')}"
}

object NewEpisodesPolicy {
    const val RECENT_WINDOW_MS = 7 * 24 * 60 * 60_000L
    const val REFRESH_INTERVAL_MS = 15 * 60_000L
    const val WATCHLIST_REFRESH_INTERVAL_MS = 60 * 60_000L
    const val WATCHLIST_BATCH_SIZE = 8

    /** Unlike Continue Watching, absent or malformed dates never qualify as new. */
    fun releasedAt(episode: EpisodeItem): Long? {
        val value = episode.released?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // A date without a time does not prove an episode has aired yet on that date.
        // Wait until the date has ended in UTC rather than announcing it early.
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    }

    fun followed(watchlist: List<MediaItem>, playback: List<LibraryPlaybackEntry>, hiddenPlaybackTitles: Set<String>): List<MediaItem> =
        (watchlist + playback.filterNot { "${it.media.type}:${it.media.id}" in hiddenPlaybackTitles }.map { it.media })
            .filter(ContinueWatchingPolicy::isSeries)
            .distinctBy { "${it.type}:${it.id}" }

    /** One card per followed series, for its newest recently released unwatched episode. */
    fun select(titles: List<MediaItem>, watched: Set<String>, nowMs: Long): List<NewEpisodeEntry> =
        titles.asSequence().filter(ContinueWatchingPolicy::isSeries).distinctBy { "${it.type}:${it.id}" }
            .mapNotNull { media ->
                media.episodes.asSequence().filter { it.season >= 0 && it.episode > 0 }
                    .filterNot { WatchedEpisodePolicy.contains(watched, media, it) }
                    .mapNotNull episodeEntry@{ episode ->
                        val released = releasedAt(episode) ?: return@episodeEntry null
                        if (released > nowMs || released < nowMs - RECENT_WINDOW_MS) null
                        else NewEpisodeEntry(media.copy(episodes = emptyList()), episode, released)
                    }.maxWithOrNull(compareBy<NewEpisodeEntry> { it.releasedAtMs }
                        .thenBy { it.episode.season }.thenBy { it.episode.episode })
            }.sortedByDescending { it.releasedAtMs }.toList()
}
