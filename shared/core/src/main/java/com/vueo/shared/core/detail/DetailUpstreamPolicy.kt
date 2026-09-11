package com.vueo.shared.core.detail

import com.vueo.shared.core.enrichment.RichDetailsClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem

/**
 * Mobile detail behavior promoted into Shared Core so TV does not re-invent it.
 * Mobile remains the behavioral upstream; TV should only adapt presentation/focus.
 */
object DetailUpstreamPolicy {
    suspend fun prepareForCore(
        item: MediaItem,
        tmdbApiKey: String,
    ): MediaItem {
        if (tmdbApiKey.isBlank() || !item.id.startsWith("tmdb:")) return item
        return TmdbEnhancementClient.prepareForCore(item, tmdbApiKey)
    }

    fun normalizeSeriesEpisodes(media: MediaItem): MediaItem {
        if (media.type != "series" || media.episodes.isEmpty()) return media

        val normalized = media.episodes.map { episode ->
            val idParts = episode.id.split(":")
            val idSeason = idParts.getOrNull(idParts.lastIndex - 1)?.toIntOrNull()
            val idEpisode = idParts.lastOrNull()?.toIntOrNull()

            episode.copy(
                season = when {
                    episode.season > 0 -> episode.season
                    idSeason != null && idSeason > 0 -> idSeason
                    else -> episode.season
                },
                episode = when {
                    episode.episode > 0 -> episode.episode
                    idEpisode != null && idEpisode > 0 -> idEpisode
                    else -> episode.episode
                },
            )
        }

        val finalEpisodes = if (
            normalized.isNotEmpty() &&
            normalized.none { it.season > 0 } &&
            normalized.all { it.season == 0 }
        ) {
            normalized.map { it.copy(season = 1) }
        } else {
            normalized
        }

        return media.copy(
            episodes = finalEpisodes.sortedWith(
                compareBy<EpisodeItem> { it.season }.thenBy { it.episode }
            )
        )
    }

    suspend fun enrichTmdb(
        media: MediaItem,
        tmdbApiKey: String,
        metadataEnabled: Boolean,
        artworkEnabled: Boolean,
    ): MediaItem {
        if (tmdbApiKey.isBlank() || (!metadataEnabled && !artworkEnabled)) return normalizeSeriesEpisodes(media)
        return normalizeSeriesEpisodes(TmdbEnhancementClient.enrich(media, tmdbApiKey, metadataEnabled, artworkEnabled))
    }

    suspend fun enrichRichDetails(
        media: MediaItem,
        tmdbApiKey: String,
        enabled: Boolean,
    ): MediaItem {
        if (tmdbApiKey.isBlank() || !enabled) return normalizeSeriesEpisodes(media)
        return normalizeSeriesEpisodes(RichDetailsClient.enrich(media, tmdbApiKey))
    }
}
