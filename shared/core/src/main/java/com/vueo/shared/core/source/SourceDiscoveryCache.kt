package com.vueo.shared.core.source

/**
 * Short-lived source discovery cache shared by Mobile and TV.
 * Keeps the same TTL and LRU behaviour as the current VUEO Mobile cache.
 */
data class CachedSourceSession(
    val sources: List<SourceCandidate>,
    val rawCount: Int,
    val notice: String?,
    val cachedAtEpochMs: Long,
)

object SourceDiscoveryCache {
    private const val TTL_MS = 120_000L
    private const val MAX_ENTRIES = 24

    private val sessions =
        object : LinkedHashMap<String, CachedSourceSession>(
            32,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CachedSourceSession>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(key: String): CachedSourceSession? {
        val cached = sessions[key] ?: return null

        if (System.currentTimeMillis() - cached.cachedAtEpochMs > TTL_MS) {
            sessions.remove(key)
            return null
        }

        return cached
    }

    @Synchronized
    fun clearExpired() {
        val now = System.currentTimeMillis()
        val iterator = sessions.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.cachedAtEpochMs > TTL_MS) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clearAll() {
        sessions.clear()
    }

    @Synchronized
    fun put(
        key: String,
        sources: List<SourceCandidate>,
        rawCount: Int,
        notice: String?,
    ) {
        if (sources.isEmpty()) return

        sessions[key] =
            CachedSourceSession(
                sources = sources,
                rawCount = rawCount,
                notice = notice,
                cachedAtEpochMs = System.currentTimeMillis(),
            )
    }

    fun key(
        mediaType: String,
        mediaId: String,
        videoId: String,
    ): String = "$mediaType|$mediaId|$videoId"
}
