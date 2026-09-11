package com.vueo.shared.core.search

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object SearchOrchestrator {
    data class ActorAvailability(val addonSearch: Boolean, val tmdbSearch: Boolean) {
        val available: Boolean get() = addonSearch || tmdbSearch
    }

    fun localTitleResults(query: String, limit: Int = 60): List<MediaItem> =
        SearchPolicy.rankAndDedupe(CatalogDiscoveryCache.searchLocal(query, limit = limit), query).take(limit)

    suspend fun remoteTitleResults(
        engine: UnifiedMediaEngine,
        query: String,
        localResults: List<MediaItem> = localTitleResults(query),
        limit: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> {
        val remote = try {
            engine.search(query = query, maxResults = limit, onPartial = { partial ->
                onPartial?.invoke(SearchPolicy.rankAndDedupe(partial + localResults, query).take(limit))
            })
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emptyList() }
        return SearchPolicy.rankAndDedupe(remote + localResults, query).take(limit)
    }

    fun actorAvailability(engine: UnifiedMediaEngine, tmdbApiKey: String): ActorAvailability =
        ActorAvailability(engine.hasActorSearchAddons(), tmdbApiKey.isNotBlank())

    suspend fun actorResults(
        engine: UnifiedMediaEngine,
        query: String,
        tmdbApiKey: String,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> = coroutineScope {
        val availability = actorAvailability(engine, tmdbApiKey)
        if (!availability.available) return@coroutineScope emptyList()
        var providerItems = emptyList<MediaItem>()
        var tmdbItems = emptyList<MediaItem>()
        fun merged() = engine.mergeActorResults(providerItems + tmdbItems, maxResults)
        fun publish() { onPartial?.invoke(merged()) }
        val providerJob = launch {
            providerItems = if (!availability.addonSearch) emptyList() else try {
                engine.searchActor(query = query, maxResults = maxResults, onPartial = { partial -> providerItems = partial; publish() })
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emptyList() }
            publish()
        }
        val tmdbJob = launch {
            tmdbItems = if (!availability.tmdbSearch) emptyList() else try {
                TmdbEnhancementClient.actorFilmography(query = query, apiKey = tmdbApiKey).orEmpty()
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emptyList() }
            publish()
        }
        providerJob.join(); tmdbJob.join(); merged()
    }
}
