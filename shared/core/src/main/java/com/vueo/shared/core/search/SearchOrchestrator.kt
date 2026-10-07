package com.vueo.shared.core.search

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class MediaEntityKind {
    ACTOR,
    COMPANY,
    NETWORK,
}

data class MediaEntityTarget(
    val kind: MediaEntityKind,
    val name: String,
    val tmdbId: Long? = null,
)

enum class EntityResultOrder {
    NEWEST,
    POPULAR,
}

object SearchOrchestrator {
    data class ActorAvailability(val addonSearch: Boolean, val tmdbSearch: Boolean) {
        val available: Boolean get() = addonSearch || tmdbSearch
    }

    /**
     * Local-only presentation ordering for entity filmographies. Switching between
     * Newest and Popular never triggers another provider/TMDB request.
     */
    fun orderEntityResults(
        items: List<MediaItem>,
        order: EntityResultOrder,
    ): List<MediaItem> {
        if (items.size < 2) return items

        data class IndexedItem(
            val index: Int,
            val item: MediaItem,
        )

        fun releaseYear(item: MediaItem): Int =
            item.releaseInfo
                ?.take(4)
                ?.toIntOrNull()
                ?: Int.MIN_VALUE

        val indexed = items.mapIndexed { index, item -> IndexedItem(index, item) }

        val comparator =
            when (order) {
                EntityResultOrder.NEWEST ->
                    compareByDescending<IndexedItem> { releaseYear(it.item) }
                        .thenByDescending { it.item.popularity ?: Double.NEGATIVE_INFINITY }
                        .thenBy { it.index }

                EntityResultOrder.POPULAR ->
                    compareByDescending<IndexedItem> { it.item.popularity ?: Double.NEGATIVE_INFINITY }
                        .thenBy { it.index }
            }

        return indexed
            .sortedWith(comparator)
            .map { it.item }
    }

    suspend fun localTitleResults(query: String, limit: Int = 60): List<MediaItem> =
        withContext(Dispatchers.Default) {
            SearchPolicy.rankAndDedupe(
                CatalogDiscoveryCache.searchLocal(query, limit = limit),
                query,
            ).take(limit)
        }

    suspend fun remoteTitleResults(
        engine: UnifiedMediaEngine,
        query: String,
        localResults: List<MediaItem>? = null,
        limit: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> {
        val local = localResults ?: localTitleResults(query, limit)
        val remote = coroutineScope {
            // Deliver provider snapshots on the caller context; keep only the newest
            // queued snapshot when providers return faster than the UI can render.
            val partials = Channel<List<MediaItem>>(Channel.CONFLATED)
            val request = async(Dispatchers.IO) {
                try {
                    engine.search(query = query, maxResults = limit, onPartial = { partial ->
                        partials.trySend(partial.toList())
                    })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    emptyList()
                } finally {
                    partials.close()
                }
            }
            for (partial in partials) {
                val ranked = withContext(Dispatchers.Default) {
                    SearchPolicy.rankAndDedupe(partial + local, query).take(limit)
                }
                onPartial?.invoke(ranked)
            }
            request.await()
        }
        val combined = withContext(Dispatchers.Default) {
            SearchPolicy.rankAndDedupe(remote + local, query).take(limit)
        }
        onPartial?.invoke(combined)
        return combined
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

    suspend fun entityResults(
        engine: UnifiedMediaEngine,
        target: MediaEntityTarget,
        tmdbApiKey: String,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> {
        return when (target.kind) {
            MediaEntityKind.ACTOR ->
                actorResults(
                    engine = engine,
                    query = target.name,
                    tmdbApiKey = tmdbApiKey,
                    maxResults = maxResults,
                    onPartial = onPartial,
                )

            MediaEntityKind.COMPANY,
            MediaEntityKind.NETWORK -> {
                val network = target.kind == MediaEntityKind.NETWORK
                val local = CatalogDiscoveryCache.companyTitles(
                    companyName = target.name,
                    networkOnly = network,
                    limit = maxResults,
                )
                onPartial?.invoke(local)

                val remote = try {
                    TmdbEnhancementClient.companyFilmography(
                        query = target.name,
                        apiKey = tmdbApiKey,
                        tmdbId = target.tmdbId,
                        network = network,
                        limit = maxResults,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    emptyList()
                }

                engine.mergeActorResults(remote + local, maxResults)
                    .also { merged ->
                        if (merged.isNotEmpty()) onPartial?.invoke(merged)
                    }
            }
        }
    }

}
