package com.vueo.app.core.extensions

import com.vueo.app.core.model.StreamSource
import com.vueo.shared.core.source.SourceCandidate
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
                streams = cached.sources.map(SourceCandidate::toMobile),
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
            sources = streams.map(StreamSource::toShared),
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

private fun StreamSource.toShared(): SourceCandidate =
    SourceCandidate(
        id = buildString {
            append(providerId ?: providerName ?: name)
            append(':')
            append(url ?: infoHash ?: name)
            fileIndex?.let {
                append(':')
                append(it)
            }
        },
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId ?: providerName ?: "unknown",
        providerName = providerName ?: providerId ?: name,
    )

private fun SourceCandidate.toMobile(): StreamSource =
    StreamSource(
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId,
        providerName = providerName,
    )
