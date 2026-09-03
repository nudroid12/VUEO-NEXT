package com.vueo.app.core.plugin

import com.vueo.app.core.model.MediaItem
import com.vueo.shared.core.plugin.TmdbResolver as SharedTmdbResolver

/** Mobile compatibility facade. TMDB ID resolution now lives in shared/core. */
object TmdbResolver {
    suspend fun resolve(
        media: MediaItem,
        apiKey: String,
    ): String? =
        SharedTmdbResolver.resolve(
            rawId = media.id,
            mediaType = media.type,
            apiKey = apiKey,
        )
}
