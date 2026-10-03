package com.vueo.tv.core

import com.vueo.shared.core.detail.DetailSupplementalClient
import com.vueo.shared.core.detail.DetailSupplementalInfo
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Home and Details reuse title artwork without waiting for core metadata. */
internal object TvTitleArtwork {
    private data class Key(val id: String, val type: String, val source: String?, val apiKey: String)
    private val cache = LinkedHashMap<Key, DetailSupplementalInfo>(96, .75f, true)
    private val locks = Array(32) { Mutex() }
    private val permits = Semaphore(2)

    private fun key(media: MediaItem, apiKey: String) =
        Key(media.id, media.type, media.sourceExtensionId, apiKey.trim())

    fun cached(media: MediaItem, apiKey: String): DetailSupplementalInfo? =
        synchronized(cache) { cache[key(media, apiKey)] }

    suspend fun load(media: MediaItem, apiKey: String): DetailSupplementalInfo = withContext(Dispatchers.IO) {
        val key = key(media, apiKey)
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(cache) { cache[key] }?.let { return@withLock it }
            val artwork = permits.withPermit { DetailSupplementalClient.load(media, apiKey) }
            synchronized(cache) {
                cache[key] = artwork
                while (cache.size > 96) cache.remove(cache.keys.first())
            }
            artwork
        }
    }
}
