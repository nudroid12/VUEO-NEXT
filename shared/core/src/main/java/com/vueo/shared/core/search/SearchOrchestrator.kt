package com.vueo.shared.core.search

import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Shared Search orchestration. Mobile owns the product behavior; TV consumes
 * the same local-first title search and actor-search source merge instead of
 * recreating it in its UI layer.
 */
object SearchOrchestrator {
    data class ActorAvailability(
        val addonSearch: Boolean,
        val tmdbSearch: Boolean,
    ) {
        val available: Boolean
            get() = addonSearch || tmdbSearch
    }

    fun localTitleResults(
        query: String,
        limit: Int = 60,
    ): List<MediaItem> =
        SearchPolicy.rankAndDedupe(
            items = CatalogDiscoveryCache.searchLocal(query, limit = limit),
            query = query,
        ).take(limit)

    suspend fun remoteTitleResults(
        engine: UnifiedMediaEngine,
        query: String,
        localResults: List<MediaItem> = localTitleResults(query),
        limit: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> {
        val remote = try {
            engine.search(
                query = query,
                maxResults = limit,
                onPartial = { partial ->
                    onPartial?.invoke(
                        SearchPolicy.rankAndDedupe(
                            items = partial + localResults,
                            query = query,
                        ).take(limit)
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }

        return SearchPolicy.rankAndDedupe(
            items = remote + localResults,
            query = query,
        ).take(limit)
    }

    fun actorAvailability(
        engine: UnifiedMediaEngine,
        tmdbApiKey: String,
    ): ActorAvailability =
        ActorAvailability(
            addonSearch = engine.hasActorSearchAddons(),
            tmdbSearch = tmdbApiKey.isNotBlank(),
        )

    suspend fun actorResults(
        engine: UnifiedMediaEngine,
        query: String,
        tmdbApiKey: String,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> = coroutineScope {
        val availability = actorAvailability(
            engine = engine,
            tmdbApiKey = tmdbApiKey,
        )
        if (!availability.available) {
            return@coroutineScope emptyList()
        }

        var providerItems = emptyList<MediaItem>()
        var tmdbItems = emptyList<MediaItem>()

        fun merged(): List<MediaItem> =
            engine.mergeActorResults(
                items = providerItems + tmdbItems,
                maxResults = maxResults,
            )

        fun publish() {
            onPartial?.invoke(merged())
        }

        val providerJob = launch {
            providerItems =
                if (!availability.addonSearch) {
                    emptyList()
                } else {
                    try {
                        engine.searchActor(
                            query = query,
                            maxResults = maxResults,
                            onPartial = { partial ->
                                providerItems = partial
                                publish()
                            },
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            publish()
        }

        val tmdbJob = launch {
            tmdbItems =
                if (!availability.tmdbSearch) {
                    emptyList()
                } else {
                    try {
                        TmdbEnhancementClient.actorFilmography(
                            query = query,
                            apiKey = tmdbApiKey,
                        ).orEmpty()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            publish()
        }

        providerJob.join()
        tmdbJob.join()

        // Canonical final list after both actor sources have completed.
        merged()
    }
}
