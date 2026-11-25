package com.vueo.shared.core.storage

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem

/** Stable episode identity independent of provider video IDs and recent history. */
object WatchedEpisodePolicy {
    fun prefix(media: MediaItem): String = "${media.type}:${media.id.length}:${media.id}:"

    fun key(media: MediaItem, season: Int, episode: Int): String =
        "${prefix(media)}$season:$episode"

    fun key(entry: LibraryPlaybackEntry): String? {
        if (!ContinueWatchingPolicy.isSeries(entry.media)) return null
        val season = entry.season ?: return null
        val episode = entry.episode ?: return null
        if (season < 0 || episode < 0) return null
        return key(entry.media, season, episode)
    }

    fun contains(keys: Set<String>, media: MediaItem, episode: EpisodeItem): Boolean =
        key(media, episode.season, episode.episode) in keys

    fun completedKeys(entries: List<LibraryPlaybackEntry>): Set<String> =
        entries.asSequence().filter { it.isCompleted }.mapNotNull(::key).toSet()
}
