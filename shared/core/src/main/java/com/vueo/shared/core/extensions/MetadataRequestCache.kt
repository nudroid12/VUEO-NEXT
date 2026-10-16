package com.vueo.shared.core.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** RAM-only positive metadata cache. Shared work lives only while callers await it. */
internal class MetadataRequestCache<K : Any, V : Any>(
    private val maxEntries: Int = 32,
    private val maxWeight: Long = 8L * 1024L * 1024L,
    private val ttlMs: Long = 5L * 60L * 1000L,
    private val weightOf: (V) -> Long,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<V>(val value: V, val storedAt: Long, val weight: Long)
    private class Flight<V>(val result: Deferred<V?>, var waiters: Int = 0)
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private val flights = mutableMapOf<K, Flight<V>>()
    private var currentWeight = 0L
    private var generation = 0L

    suspend fun getOrLoad(key: K, load: suspend () -> V?): V? {
        currentCoroutineContext().ensureActive()
        val flight = synchronized(lock) {
            val now = clockMs()
            entries[key]?.let { entry ->
                if (now - entry.storedAt in 0 until ttlMs) return entry.value
                entries.remove(key)
                currentWeight -= entry.weight
            }
            val selected = flights[key] ?: run {
                val epoch = generation
                val result = scope.async(start = CoroutineStart.LAZY) {
                    val loaded = load()
                    currentCoroutineContext().ensureActive()
                    if (loaded != null) {
                        val weight = weightOf(loaded).coerceAtLeast(1L)
                        synchronized(lock) {
                            if (generation == epoch && weight <= maxWeight) {
                                entries.remove(key)?.let { currentWeight -= it.weight }
                                entries[key] = Entry(loaded, clockMs(), weight)
                                currentWeight += weight
                                while (entries.size > maxEntries || currentWeight > maxWeight) {
                                    val eldest = entries.entries.iterator()
                                    val entry = eldest.next()
                                    currentWeight -= entry.value.weight
                                    eldest.remove()
                                }
                            }
                        }
                    }
                    loaded
                }
                val created = Flight(result)
                flights[key] = created
                result.invokeOnCompletion {
                    synchronized(lock) {
                        if (flights[key] === created) flights.remove(key)
                    }
                }
                created
            }
            selected.waiters++
            selected
        }
        return try {
            flight.result.start()
            flight.result.await()
        } finally {
            val cancel = synchronized(lock) {
                flight.waiters--
                if (flight.waiters == 0 && !flight.result.isCompleted) {
                    if (flights[key] === flight) flights.remove(key)
                    true
                } else false
            }
            if (cancel) flight.result.cancel()
        }
    }

    fun invalidate() = synchronized(lock) {
        generation++
        entries.clear()
        currentWeight = 0L
        // Existing callers may finish, but new callers cannot join old topology
        // and old results cannot repopulate the current generation's cache.
        flights.clear()
    }
}
