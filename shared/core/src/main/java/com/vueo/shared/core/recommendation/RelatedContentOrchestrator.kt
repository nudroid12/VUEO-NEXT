package com.vueo.shared.core.recommendation

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException

/**
 * Canonical More Like This behavior shared by Mobile and TV.
 *
 * Mobile is the upstream behavior: publish VUEO's local similarity result
 * immediately, then prepend TMDB Recommendations/Similar when available and
 * keep the local engine as the fallback. Ordering is intentionally preserved.
 */
object RelatedContentOrchestrator {
    fun local(
        item: MediaItem,
        limit: Int = 18,
    ): List<MediaItem> =
        CatalogDiscoveryCache.related(
            item = item,
            limit = limit,
        )

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

        val tmdbRelated = try {
            TmdbEnhancementClient.moreLikeThis(
                item = item,
                apiKey = apiKey,
                recommendationsEnabled = recommendationsEnabled,
                similarEnabled = similarEnabled,
                limit = limit,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }

        if (tmdbRelated.isEmpty()) {
            return localItems.take(limit)
        }

        return (tmdbRelated + localItems)
            .distinctBy { "${it.type}:${it.id}" }
            .take(limit)
    }
}
