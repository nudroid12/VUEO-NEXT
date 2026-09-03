package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.CatalogPage
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack

interface MediaExtension {
    val descriptor: ExtensionDescriptor

    suspend fun catalog(
        type: String,
        catalogId: String,
        extras: Map<String, String> = emptyMap(),
    ): CatalogPage = CatalogPage(emptyList())

    suspend fun meta(
        type: String,
        id: String,
    ): MediaItem? = null

    suspend fun streams(
        type: String,
        videoId: String,
    ): List<StreamSource> = emptyList()

    suspend fun subtitles(
        type: String,
        id: String,
        extras: Map<String, String> = emptyMap(),
    ): List<SubtitleTrack> = emptyList()
}
