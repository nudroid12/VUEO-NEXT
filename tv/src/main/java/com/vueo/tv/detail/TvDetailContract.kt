package com.vueo.tv.detail

import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry

/**
 * TV 38A Details UI contract.
 *
 * This file is deliberately presentation-agnostic. It is the only contract
 * between the VUEO runtime boundary and the brand-new Details UI tree.
 */
internal data class TvDetailUiState(
    val item: MediaItem,
    val loading: Boolean,
    val watchlisted: Boolean,
    val ratings: List<MediaRating>,
    val dnaMatch: Int?,
    val seasons: List<Int>,
    val selectedSeason: Int?,
    val episodes: List<EpisodeItem>,
    val selectedEpisode: EpisodeItem?,
    val history: List<LibraryPlaybackEntry>,
    val playbackEntry: LibraryPlaybackEntry?,
    val related: List<MediaItem>,
    val primaryActionLabel: String,
    val insightAvailable: Boolean,
    val insight: String?,
    val insightLoading: Boolean,
    val insightError: String?,
)

/**
 * Small process-local focus memory used only to restore Details after opening
 * Source. No layout state is stored here.
 */
internal object TvDetailSessionMemory {
    var mediaKey: String? = null
    var selectedSeason: Int? = null
    var episodeId: String? = null
    var relatedIndex: Int = 0

    fun reset(key: String) {
        mediaKey = key
        selectedSeason = null
        episodeId = null
        relatedIndex = 0
    }
}

internal fun MediaItem.isTvSeries(): Boolean =
    type.lowercase() in setOf("series", "tv")

internal fun detailSeasonNumbers(item: MediaItem): List<Int> {
    val all = item.episodes.map(EpisodeItem::season).distinct()
    val regular = all.filter { it > 0 }.sorted()
    val specials = all.filter { it == 0 }
    return regular + specials
}

internal fun detailPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    entries: List<LibraryPlaybackEntry>,
): LibraryPlaybackEntry? =
    entries.firstOrNull { entry ->
        entry.media.id == media.id &&
            entry.media.type == media.type &&
            if (media.isTvSeries() && episode != null) {
                entry.season == episode.season && entry.episode == episode.episode
            } else {
                !media.isTvSeries()
            }
    }

internal fun detailCanResume(entry: LibraryPlaybackEntry): Boolean =
    entry.positionMs > 15_000L &&
        (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * .95f).toLong())

internal fun detailRemainingLabel(entry: LibraryPlaybackEntry): String {
    if (entry.durationMs <= 0L) return "Resume"
    val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0L)
    val minutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    return if (minutes > 0L) "$minutes min left" else "Almost done"
}

internal fun detailPrimaryActionLabel(
    item: MediaItem,
    episode: EpisodeItem?,
    playbackEntry: LibraryPlaybackEntry?,
    loading: Boolean,
): String {
    val canResume = playbackEntry?.let(::detailCanResume) == true
    return when {
        loading -> "Loading…"
        item.isTvSeries() && episode != null && canResume ->
            "Resume S${episode.season} E${episode.episode}"
        item.isTvSeries() && episode != null ->
            "Play S${episode.season} E${episode.episode}"
        item.isTvSeries() -> "Select an Episode"
        canResume -> "Resume"
        else -> "Play"
    }
}

internal fun detailBaseRatings(media: MediaItem): List<MediaRating> =
    buildList {
        media.imdbRating?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "imdb", value = it)) }
        media.tmdbRating?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "tmdb", value = it)) }
    }
