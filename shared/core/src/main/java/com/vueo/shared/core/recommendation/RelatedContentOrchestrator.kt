package com.vueo.shared.core.recommendation

import com.vueo.shared.core.storage.RecommendationEngine
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException

object RelatedContentOrchestrator {
    fun local(item: MediaItem, limit: Int = 18, checkActive: () -> Unit = {}): List<MediaItem> =
        CatalogDiscoveryCache.related(item, limit, checkActive)

    suspend fun mergeRemote(
        item: MediaItem,
        localItems: List<MediaItem>,
        apiKey: String,
        recommendationsEnabled: Boolean,
        similarEnabled: Boolean,
        limit: Int = 18,
        engine: RecommendationEngine = RecommendationEngine.VUEO,
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        // Direct TMDB mode keeps API order and never uses local fallback or VUEO ranking.
        if (engine == RecommendationEngine.TMDB) {
            if (apiKey.isBlank() || (!recommendationsEnabled && !similarEnabled)) return emptyList()
            return try {
                TmdbEnhancementClient.moreLikeThis(item, apiKey, recommendationsEnabled, similarEnabled, limit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        // VUEO ranks catalog candidates in local(); TMDB lists never enter this mode.
        return localItems.take(limit)
    }
}
