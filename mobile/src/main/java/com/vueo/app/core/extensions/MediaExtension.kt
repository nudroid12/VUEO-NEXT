package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogPage
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack

interface MediaExtension {
    val descriptor: ExtensionDescriptor

    suspend fun catalog(type: String, catalogId: String, extras: Map<String, String> = emptyMap()): CatalogPage = CatalogPage(emptyList())
    suspend fun meta(type: String, id: String): MediaItem? = null
    suspend fun streams(type: String, videoId: String): List<StreamSource> = emptyList()
    suspend fun subtitles(type: String, id: String, extras: Map<String, String> = emptyMap()): List<SubtitleTrack> = emptyList()
}
