package com.vueo.shared.core.storage

import com.vueo.shared.core.detail.DetailUpstreamPolicy
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Best-effort background metadata only; never performs source discovery. */
object ContinueWatchingMetadataRefresh {
    private val gate = Semaphore(2)

    suspend fun refresh(store: LibraryStore, loadMetadata: suspend (MediaItem) -> MediaItem): Boolean = coroutineScope {
        val profileId = store.activeContinueWatchingProfileId()
        val candidates = withContext(Dispatchers.IO) { store.continueWatchingMetadataCandidates(System.currentTimeMillis()) }
        candidates.map { media ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    if (!store.claimContinueWatchingMetadata(media, profileId, System.currentTimeMillis())) return@withPermit false
                    try {
                        val fresh = withTimeoutOrNull(15_000L) { loadMetadata(media) } ?: return@withPermit false
                        if (fresh.id != media.id || fresh.type != media.type) return@withPermit false
                        store.updateContinueWatchingMetadata(DetailUpstreamPolicy.normalizeSeriesEpisodes(fresh), profileId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false // retain cached cursor and retry on a later refresh
                    }
                }
            }
        }.awaitAll().any { it }
    }
}
