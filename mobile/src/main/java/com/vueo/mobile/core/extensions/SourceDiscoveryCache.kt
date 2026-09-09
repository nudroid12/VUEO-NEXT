package com.vueo.mobile.core.extensions

import com.vueo.mobile.core.model.StreamSource
import com.vueo.shared.core.source.toSourceCandidate
import com.vueo.shared.core.source.toStreamSource
import com.vueo.shared.core.source.SourceDiscoveryCache as SharedSourceDiscoveryCache

data class CachedSourceSession(
    val streams: List<StreamSource>,
    val rawCount: Int,
    val notice: String?,
    val cachedAtEpochMs: Long,
)

/** Mobile compatibility facade. Cache ownership now lives in shared/core. */
object SourceDiscoveryCache {
    fun get(key: String): CachedSourceSession? =
        SharedSourceDiscoveryCache.get(key)?.let { cached ->
            CachedSourceSession(
                streams = cached.sources.map { it.toStreamSource() },
                rawCount = cached.rawCount,
                notice = cached.notice,
                cachedAtEpochMs = cached.cachedAtEpochMs,
            )
        }

    fun clearExpired() {
        SharedSourceDiscoveryCache.clearExpired()
    }

    fun clearAll() {
        SharedSourceDiscoveryCache.clearAll()
    }

    fun put(
        key: String,
        streams: List<StreamSource>,
        rawCount: Int,
        notice: String?,
    ) {
        SharedSourceDiscoveryCache.put(
            key = key,
            sources = streams.map { it.toSourceCandidate() },
            rawCount = rawCount,
            notice = notice,
        )
    }

    fun key(
        mediaType: String,
        mediaId: String,
        videoId: String,
    ): String =
        SharedSourceDiscoveryCache.key(
            mediaType = mediaType,
            mediaId = mediaId,
            videoId = videoId,
        )
}
