package com.vueo.shared.core.recommendation

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException

object RelatedContentOrchestrator {
    fun local(item: MediaItem, limit: Int = 18): List<MediaItem> = CatalogDiscoveryCache.related(item, limit)
    suspend fun mergeRemote(
        item: MediaItem,
        localItems: List<MediaItem>,
        apiKey: String,
        recommendationsEnabled: Boolean,
        similarEnabled: Boolean,
        limit: Int = 18,
    ): List<MediaItem> {
        if (apiKey.isBlank() || (!recommendationsEnabled && !similarEnabled)) return localItems.take(limit)
        val tmdbRelated = try {
            TmdbEnhancementClient.moreLikeThis(item, apiKey, recommendationsEnabled, similarEnabled, limit)
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emptyList() }
        if (tmdbRelated.isEmpty()) return localItems.take(limit)
        return (tmdbRelated + localItems).distinctBy { "${it.type}:${it.id}" }.take(limit)
    }
}
