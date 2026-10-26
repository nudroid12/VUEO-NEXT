package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.dna.UserDnaSnapshot
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
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Bounded remote discovery; profiles and Trakt credentials never share a feed cache. */
object ForYouCandidateLoader {
    data class Result(val seeds: List<ForYouPolicy.Seed>, val batches: List<ForYouPolicy.RelatedBatch>,
        val discovery: List<MediaItem> = emptyList(), val dna: UserDnaSnapshot? = null) {
        fun relatedTo(item: MediaItem?): List<MediaItem> = item?.let { watched ->
            batches.firstOrNull { ForYouPolicy.identity(it.seed.media) == ForYouPolicy.identity(watched) }?.items
        }.orEmpty()
    }
    private data class Key(val profile: String, val traktId: String, val day: Long, val library: String)
    private data class Cached(val result: Result, val until: Long)
    private val cache = LinkedHashMap<Key, Cached>(12, 0.75f, true)

    suspend fun load(
        profileId: String, history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>,
        traktClientId: String, ratings: List<com.vueo.shared.core.storage.TitleRating> = emptyList(),
    ): Result = withContext(Dispatchers.IO) {
        if (traktClientId.isBlank()) return@withContext Result(emptyList(), emptyList())
        val now = System.currentTimeMillis()
        // Retain a digest, not twelve full copies of a potentially large history.
        val digest = MessageDigest.getInstance("SHA-256")
        history.forEach {
            coroutineContext.ensureActive()
            digest.update(it.copy(media = it.media.copy(episodes = emptyList())).toString().toByteArray())
            digest.update(0.toByte())
        }
        digest.update(1.toByte())
        favorites.forEach {
            coroutineContext.ensureActive()
            digest.update(it.copy(episodes = emptyList()).toString().toByteArray())
            digest.update(0.toByte())
        }
        digest.update(ratings.toString().toByteArray())
        val key = Key(profileId, traktClientId.trim(), now / 86_400_000L,
            digest.digest().joinToString("") { "%02x".format(it) })
        synchronized(cache) { cache[key]?.takeIf { it.until > now } }?.let { return@withContext it.result }
        val context = coroutineContext
        val (dna, seeds) = withContext(Dispatchers.Default) {
            val positiveHistory = history.filterNot { entry -> ratings.any { rating ->
                rating.value < 0 && ForYouPolicy.aliases(rating.media).any(ForYouPolicy.aliases(entry.media)::contains)
            } }
            val dna = ForYouPolicy.profile(positiveHistory, favorites.filterNot { media -> ratings.any {
                it.value < 0 && ForYouPolicy.aliases(it.media).any(ForYouPolicy.aliases(media)::contains)
            } }, now) { context.ensureActive() }
            dna to ForYouPolicy.seeds(history, favorites, now, dna, { context.ensureActive() }, ratings)
        }
        val received = ConcurrentHashMap<Int, List<MediaItem>>()
        val gate = Semaphore(3)
        var discovery = emptyList<MediaItem>()
        // A stalled provider cannot leave twelve requests queued indefinitely.
        withTimeoutOrNull(12_000L) {
            coroutineScope {
                seeds.mapIndexed { index, seed -> async {
                    gate.withPermit {
                        val items = TraktRelatedClient.related(seed.media.copy(type = if (ForYouPolicy.identity(seed.media).startsWith("series:")) "series" else "movie"), traktClientId)
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
        }, discovery, dna)
        synchronized(cache) {
            cache[key] = Cached(result, System.currentTimeMillis() + if (result.batches.isEmpty() && result.discovery.isEmpty()) 90_000L else 15 * 60_000L)
            while (cache.size > 12) cache.remove(cache.keys.first())
        }
        result
    }
}
