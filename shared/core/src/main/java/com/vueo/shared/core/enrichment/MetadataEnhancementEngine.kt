package com.vueo.shared.core.enrichment

import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Shared non-AI metadata enhancement pipeline for Mobile and TV.
 * All integrations remain optional and fail soft to the original metadata.
 */
data class MetadataEnhancementOptions(
    val tmdbApiKey: String = "",
    val mdblistApiKey: String = "",
    val tmdbMetadataEnabled: Boolean = true,
    val tmdbArtworkEnabled: Boolean = true,
    val richDetailsEnabled: Boolean = true,
    val ratingsEnabled: Boolean = true,
)

data class MetadataEnhancementResult(
    val media: MediaItem,
    val ratings: List<MediaRating> = emptyList(),
)

object MetadataEnhancementEngine {
    suspend fun enrich(
        media: MediaItem,
        options: MetadataEnhancementOptions,
    ): MetadataEnhancementResult = coroutineScope {
        val tmdbKey = options.tmdbApiKey.trim()
        val mdblistKey = options.mdblistApiKey.trim()

        val enrichedDeferred = async {
            var current = media

            if (
                tmdbKey.isNotBlank() &&
                (options.tmdbMetadataEnabled || options.tmdbArtworkEnabled)
            ) {
                current = runCatching {
                    TmdbEnhancementClient.enrich(
                        item = current,
                        apiKey = tmdbKey,
                        metadataEnabled = options.tmdbMetadataEnabled,
                        artworkEnabled = options.tmdbArtworkEnabled,
                    )
                }.getOrDefault(current)
            }

            if (tmdbKey.isNotBlank() && options.richDetailsEnabled) {
                current = runCatching {
                    RichDetailsClient.enrich(
                        media = current,
                        apiKey = tmdbKey,
                    )
                }.getOrDefault(current)
            }

            current
        }

        val ratingsDeferred = async {
            if (mdblistKey.isBlank() || !options.ratingsEnabled) {
                emptyList()
            } else {
                runCatching {
                    MdblistClient.ratings(
                        media = media,
                        apiKey = mdblistKey,
                    )
                }.getOrDefault(emptyList())
            }
        }

        MetadataEnhancementResult(
            media = enrichedDeferred.await(),
            ratings = ratingsDeferred.await(),
        )
    }
}
