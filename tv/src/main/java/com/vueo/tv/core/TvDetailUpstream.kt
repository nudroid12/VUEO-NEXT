package com.vueo.tv.core

import com.vueo.shared.core.detail.DetailUpstreamPolicy
import com.vueo.shared.core.media.MediaItem

internal suspend fun TvRuntime.prepareDetailForCore(item: MediaItem): MediaItem =
    DetailUpstreamPolicy.prepareForCore(
        item = item,
        tmdbApiKey = pluginStore.tmdbApiKey(),
    )

internal suspend fun TvRuntime.loadCoreDetail(item: MediaItem): MediaItem =
    DetailUpstreamPolicy.normalizeSeriesEpisodes(
        engine.loadMeta(item)
    )

internal suspend fun TvRuntime.enrichDetailMetadata(item: MediaItem): MediaItem =
    DetailUpstreamPolicy.enrichMetadata(
        media = item,
        tmdbApiKey = pluginStore.tmdbApiKey(),
        metadataEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
        artworkEnabled = settingsStore.tmdbArtworkEnrichmentEnabled(),
        richDetailsEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
    )
