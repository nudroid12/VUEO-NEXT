package com.vueo.shared.core.recommendation

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException

object RelatedContentOrchestrator {
    private const val MAX_REMOTE_CANDIDATES = 54

    fun local(item: MediaItem, limit: Int = 18): List<MediaItem> =
        CatalogDiscoveryCache.related(item, limit)

    suspend fun mergeRemote(
        item: MediaItem,
        localItems: List<MediaItem>,
        apiKey: String,
        recommendationsEnabled: Boolean,
        similarEnabled: Boolean,
        limit: Int = 18,
    ): List<MediaItem> {
        if (
            apiKey.isBlank() ||
            (!recommendationsEnabled && !similarEnabled)
        ) {
            return localItems.take(limit)
        }

        // Fetch a wider candidate pool than the visible rail so TMDB
        // Recommendations and Similar titles can both participate in VUEO's
        // final relevance ranking instead of winning purely by API order.
        val remoteCandidateLimit =
            (limit * 3)
                .coerceAtLeast(limit)
                .coerceAtMost(
                    MAX_REMOTE_CANDIDATES
                )
        val tmdbRelated =
            try {
                TmdbEnhancementClient.moreLikeThis(
                    item = item,
                    apiKey = apiKey,
                    recommendationsEnabled =
                        recommendationsEnabled,
                    similarEnabled =
                        similarEnabled,
                    limit = remoteCandidateLimit,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }

        if (tmdbRelated.isEmpty()) {
            return localItems.take(limit)
        }

        return CatalogDiscoveryCache
            .blendRelated(
                item = item,
                localItems = localItems,
                remoteItems = tmdbRelated,
                limit = limit,
            )
            .ifEmpty {
                localItems.take(limit)
            }
    }
}
