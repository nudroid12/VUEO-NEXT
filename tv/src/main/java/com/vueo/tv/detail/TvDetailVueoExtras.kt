package com.vueo.tv.detail

import com.vueo.shared.core.detail.DetailSupplementalClient
import com.vueo.shared.core.detail.DetailSupplementalInfo
import com.vueo.shared.core.media.MediaItem

internal typealias TvDetailVueoExtras = DetailSupplementalInfo

internal suspend fun loadTvDetailVueoExtras(media: MediaItem, tmdbApiKey: String): TvDetailVueoExtras =
    DetailSupplementalClient.load(media, tmdbApiKey)
