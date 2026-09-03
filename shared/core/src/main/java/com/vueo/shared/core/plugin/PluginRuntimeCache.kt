package com.vueo.shared.core.plugin

import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred


internal data class PluginRuntimeFlight(
    val owner: Boolean,
    val deferred: CompletableDeferred<PluginDiscoveryResult>,
)

/**
 * Short-lived process cache for JavaScript provider discovery.
 *
 * The cache key includes the enabled provider topology so toggling a repository,
 * provider or installing a new provider version automatically bypasses stale data.
 */
internal object PluginRuntimeCache {
    private const val SUCCESS_TTL_MS = 120_000L
    private const val EMPTY_TTL_MS = 30_000L
    private const val MAX_ENTRIES = 32

    private data class CacheEntry(
        val result: PluginDiscoveryResult,
        val storedAtEpochMs: Long,
        val ttlMs: Long,
    )

    private val inFlight =
        mutableMapOf<
            String,
            CompletableDeferred<PluginDiscoveryResult>
        >()

    private val entries =
        object : LinkedHashMap<String, CacheEntry>(
            MAX_ENTRIES + 4,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(key: String): PluginDiscoveryResult? {
        val entry = entries[key] ?: return null
        val ageMs = System.currentTimeMillis() - entry.storedAtEpochMs

        if (ageMs > entry.ttlMs) {
            entries.remove(key)
            return null
        }

        return entry.result.copy(fromCache = true)
    }

    @Synchronized
    fun put(
        key: String,
        result: PluginDiscoveryResult,
    ) {
        entries[key] =
            CacheEntry(
                result = result.copy(fromCache = false),
                storedAtEpochMs = System.currentTimeMillis(),
                ttlMs =
                    if (result.streams.isEmpty()) {
                        EMPTY_TTL_MS
                    } else {
                        SUCCESS_TTL_MS
                    },
            )
    }

    @Synchronized
    fun acquireFlight(key: String): PluginRuntimeFlight {
        val existing = inFlight[key]
        if (existing != null) {
            return PluginRuntimeFlight(
                owner = false,
                deferred = existing,
            )
        }

        val created = CompletableDeferred<PluginDiscoveryResult>()
        inFlight[key] = created
        return PluginRuntimeFlight(
            owner = true,
            deferred = created,
        )
    }

    @Synchronized
    fun completeFlight(
        key: String,
        result: PluginDiscoveryResult,
    ) {
        inFlight.remove(key)?.complete(result)
    }

    @Synchronized
    fun failFlight(
        key: String,
        error: Throwable,
    ) {
        inFlight.remove(key)?.completeExceptionally(error)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    fun key(
        store: PluginStore,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): String {
        val topology =
            buildString {
                append("enabled=")
                append(store.pluginsEnabled())
                append('|')

                store.repositories()
                    .filter(store::isRepositoryEnabled)
                    .sortedBy { it.manifestUrl }
                    .forEach { repository ->
                        append(repository.manifestUrl)
                        append('@')
                        append(repository.version)
                        append('[')

                        repository.providers
                            .filter {
                                store.isProviderEnabled(
                                    repository,
                                    it,
                                )
                            }
                            .sortedBy { it.id }
                            .forEach { provider ->
                                append(provider.id)
                                append('@')
                                append(provider.version)
                                append(':')
                                append(provider.filename)
                                append(',')
                            }

                        append(']')
                    }
            }

        val topologyHash = sha256(topology).take(16)

        return listOf(
            mediaType.lowercase(),
            tmdbId,
            season?.toString().orEmpty(),
            episode?.toString().orEmpty(),
            topologyHash,
        ).joinToString("|")
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
