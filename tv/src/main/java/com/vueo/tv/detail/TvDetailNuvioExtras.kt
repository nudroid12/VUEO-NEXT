package com.vueo.tv.detail

import com.vueo.shared.core.detail.DetailSupplementalClient
import com.vueo.shared.core.detail.DetailSupplementalInfo
import com.vueo.shared.core.media.MediaItem

internal typealias TvDetailNuvioExtras = DetailSupplementalInfo

internal suspend fun loadTvDetailNuvioExtras(
    media: MediaItem,
    tmdbApiKey: String,
): TvDetailNuvioExtras =
    DetailSupplementalClient.load(
        media = media,
        tmdbApiKey = tmdbApiKey,
    )
