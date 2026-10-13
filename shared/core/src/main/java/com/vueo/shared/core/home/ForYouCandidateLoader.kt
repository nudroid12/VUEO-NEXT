package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.recommendation.TraktRelatedClient
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Bounded remote discovery; profiles and Trakt credentials never share a feed cache. */
object ForYouCandidateLoader {
    data class Result(val seeds: List<ForYouPolicy.Seed>, val batches: List<ForYouPolicy.RelatedBatch>,
        val discovery: List<MediaItem> = emptyList()) {
        fun relatedTo(item: MediaItem?): List<MediaItem> = item?.let { watched ->
            batches.firstOrNull { ForYouPolicy.identity(it.seed.media) == ForYouPolicy.identity(watched) }?.items
        }.orEmpty()
    }
    private data class Key(val profile: String, val traktId: String, val day: Long,
        val media: List<MediaItem>, val weights: List<Int>)
    private data class Cached(val result: Result, val until: Long)
    private val cache = LinkedHashMap<Key, Cached>(12, 0.75f, true)

    suspend fun load(
        profileId: String, history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>,
        traktClientId: String,
    ): Result = withContext(Dispatchers.IO) {
        val seeds = ForYouPolicy.seeds(history, favorites)
        if (traktClientId.isBlank()) return@withContext Result(seeds, emptyList())
        val now = System.currentTimeMillis()
        val key = Key(profileId, traktClientId.trim(),
            now / 86_400_000L, seeds.map { it.media }, seeds.map { (it.weight * 10).toInt() })
        synchronized(cache) { cache[key]?.takeIf { it.until > now } }?.let { return@withContext it.result }
        val received = ConcurrentHashMap<Int, List<MediaItem>>()
        val gate = Semaphore(3)
        var discovery = emptyList<MediaItem>()
        // A stalled provider cannot leave twelve requests queued indefinitely.
        withTimeoutOrNull(12_000L) {
            coroutineScope {
                seeds.mapIndexed { index, seed -> async {
                    gate.withPermit {
                        val items = TraktRelatedClient.related(seed.media, traktClientId)
                        coroutineContext.ensureActive()
                        if (items.isNotEmpty()) received[index] = items
                    }
                } }.awaitAll()
            }
            // Sparse profiles use Trakt discovery only; never fall back to Home/TMDB catalogs.
            if (seeds.size < 3 || received.values.flatten().distinctBy(ForYouPolicy::identity).size < 4) {
                discovery = TraktRelatedClient.discovery(traktClientId)
            }
        }
        coroutineContext.ensureActive()
        val result = Result(seeds, seeds.mapIndexedNotNull { index, seed ->
            received[index]?.let { ForYouPolicy.RelatedBatch(seed, it) }
        }, discovery)
        synchronized(cache) {
            cache[key] = Cached(result, System.currentTimeMillis() + if (result.batches.isEmpty() && result.discovery.isEmpty()) 90_000L else 15 * 60_000L)
            while (cache.size > 12) cache.remove(cache.keys.first())
        }
        result
    }
}
