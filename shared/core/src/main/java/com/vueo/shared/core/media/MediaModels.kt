package com.vueo.shared.core.media

data class EpisodeItem(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
)

data class MediaPerson(
    val name: String,
    val character: String? = null,
    val role: String? = null,
    val profile: String? = null,
)

data class MediaCompany(
    val name: String,
    val logo: String? = null,
)

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val originalTitle: String? = null,
    val aliases: List<String> = emptyList(),
    val originalLanguage: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val sourceExtensionId: String? = null,
    val catalogSources: List<String> = emptyList(),
    val imdbRating: Double? = null,
    val tmdbRating: Double? = null,
    val runtimeMinutes: Int? = null,
    val certification: String? = null,
    val directors: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val cast: List<MediaPerson> = emptyList(),
    val productionCompanies: List<MediaCompany> = emptyList(),
    val networks: List<MediaCompany> = emptyList(),
) {
    val displayType: String
        get() =
            when (type.lowercase()) {
                "series", "tv" -> "Series"
                "movie" -> "Movie"
                else -> type.replaceFirstChar { it.uppercase() }
            }
}

data class CatalogRow(
    val id: String,
    val title: String,
    val providerName: String,
    val items: List<MediaItem>,
)

data class StreamSource(
    val name: String,
    val url: String? = null,
    val streamType: String? = null,
    val mimeType: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val quality: String? = null,
    val codec: String? = null,
    val hdr: String? = null,
    val audio: String? = null,
    val language: String? = null,
    val sizeBytes: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val rankBoost: Int = 0,
    val providerId: String,
    val providerName: String,
) {
    val transport: StreamTransport
        get() = StreamTransportPolicy.classify(
            url = url,
            streamType = streamType,
            mimeType = mimeType,
        )

    val playbackMimeType: String?
        get() = StreamTransportPolicy.playbackMimeType(
            url = url,
            streamType = streamType,
            mimeType = mimeType,
        )

    val isDirectPlayable: Boolean
        get() = StreamTransportPolicy.isDirectPlayable(
            url = url,
            streamType = streamType,
            mimeType = mimeType,
        )
}

data class SubtitleTrack(
    val id: String,
    val language: String,
    val url: String,
    val providerId: String,
    val providerName: String,
    val name: String? = null,
)

data class CatalogPage(
    val items: List<MediaItem>,
    val hasMore: Boolean = false,
)
