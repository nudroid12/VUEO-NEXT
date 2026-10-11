package com.vueo.shared.core.recommendation

import com.vueo.shared.core.storage.RecommendationEngine
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException

/** Direct provider order only. No local candidates, blending or VUEO ranking. */
object RelatedContentOrchestrator {
    suspend fun direct(
        item: MediaItem,
        apiKey: String,
        recommendationsEnabled: Boolean,
        similarEnabled: Boolean,
        limit: Int = 18,
        engine: RecommendationEngine = RecommendationEngine.TMDB,
        traktClientId: String = "",
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        return try {
            when (engine) {
                RecommendationEngine.TRAKT ->
                    if (traktClientId.isBlank()) emptyList()
                    else TraktRelatedClient.related(item, traktClientId).take(limit)
                RecommendationEngine.TMDB ->
                    if (apiKey.isBlank() || (!recommendationsEnabled && !similarEnabled)) emptyList()
                    else TmdbEnhancementClient.moreLikeThis(item, apiKey, recommendationsEnabled, similarEnabled, limit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
}
