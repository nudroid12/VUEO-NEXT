package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.EpisodeItem
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

fun main() = runBlocking {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    val now = AtomicLong(0)
    val loads = AtomicInteger()
    val cache = MetadataRequestCache<String, String>(maxEntries = 2, maxWeight = 6, ttlMs = 100,
        weightOf = { it.length.toLong() }, clockMs = now::get)
    suspend fun fetch(key: String, value: String = key): String? = cache.getOrLoad(key) { loads.incrementAndGet(); value }
    verify(fetch("a") == "a")
    verify(fetch("a") == "a" && loads.get() == 1)
    now.set(100)
    verify(fetch("a") == "a" && loads.get() == 2)
    fetch("b"); fetch("a"); fetch("c")
    val beforeLru = loads.get(); fetch("b")
    verify(loads.get() == beforeLru + 1) // least-recently-used b was evicted
    cache.invalidate(); fetch("largeA", "aaaa"); fetch("largeB", "bbb")
    val beforeWeight = loads.get(); fetch("largeA", "aaaa")
    verify(loads.get() == beforeWeight + 1)
    val beforeOversize = loads.get(); fetch("huge", "1234567"); fetch("huge", "1234567")
    verify(loads.get() == beforeOversize + 2)
    var nullCalls = 0
    repeat(2) { verify(cache.getOrLoad("null") { nullCalls++; null } == null) }
    verify(nullCalls == 2)
    var errorCalls = 0
    repeat(2) {
        try { cache.getOrLoad("error") { errorCalls++; error("offline") }; error("expected failure") }
        catch (expected: IllegalStateException) { verify(expected.message == "offline") }
    }
    verify(errorCalls == 2)

    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val sharedCalls = AtomicInteger()
    val shared = MetadataRequestCache<String, String>(weightOf = { 1 })
    suspend fun sharedFetch() = shared.getOrLoad("same") {
        sharedCalls.incrementAndGet(); entered.complete(Unit); release.await(); "ready"
    }
    supervisorScope {
        val owner = async(start = CoroutineStart.UNDISPATCHED) { sharedFetch() }
        entered.await()
        val follower = async(start = CoroutineStart.UNDISPATCHED) { sharedFetch() }
        owner.cancelAndJoin()
        verify(sharedCalls.get() == 1)
        release.complete(Unit)
        verify(follower.await() == "ready")
    }
    verify(shared.getOrLoad("same") { error("must hit cache") } == "ready")

    val lastEntered = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    val lastCache = MetadataRequestCache<String, String>(weightOf = { 1 })
    val lastWaiter = launch(start = CoroutineStart.UNDISPATCHED) {
        lastCache.getOrLoad("last") {
            lastEntered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
    }
    lastEntered.await(); lastWaiter.cancelAndJoin()
    withTimeout(2000) { cancelled.await() }
    verify(lastCache.getOrLoad("last") { "retry" } == "retry")

    // Invalidation detaches old work; its late completion cannot populate or
    // remove a newer generation's entry/flight for the same key.
    val oldEntered = CompletableDeferred<Unit>()
    val oldRelease = CompletableDeferred<Unit>()
    val generationCache = MetadataRequestCache<String, String>(weightOf = { 1 })
    val old = async(start = CoroutineStart.UNDISPATCHED) {
        generationCache.getOrLoad("title") { oldEntered.complete(Unit); oldRelease.await(); "old" }
    }
    oldEntered.await(); generationCache.invalidate()
    verify(generationCache.getOrLoad("title") { "new" } == "new")
    oldRelease.complete(Unit)
    verify(old.await() == "old")
    verify(generationCache.getOrLoad("title") { error("new entry lost") } == "new")

    // Distinct provider/type/policy keys do not merge public metadata requests.
    verify(generationCache.getOrLoad("providerB:title") { "other provider" } == "other provider")
    verify(generationCache.getOrLoad("kids7:title") { "kids namespace" } == "kids namespace")
    verify(generationCache.getOrLoad("series:title") { "series" } == "series")

    val basic = MediaItem(id = "tt1", type = "series", name = "Test")
    val detailed = basic.copy(description = "A".repeat(1000),
        episodes = listOf(EpisodeItem(id = "e1", title = "Episode", season = 1, episode = 1)))
    verify(metadataCacheWeight(basic) > 0)
    verify(metadataCacheWeight(detailed) > metadataCacheWeight(basic) + 2000)
    val unknown = basic.copy(certification = null)
    val general = basic.copy(certification = "G")
    val mature = basic.copy(certification = "TV-MA")
    val teen = basic.copy(certification = "PG-13")
    verify(metadataAllowedForKids(unknown, mature, null))
    verify(metadataAllowedForKids(general, unknown, 7))
    verify(!metadataAllowedForKids(unknown, mature, 7))
    verify(!metadataAllowedForKids(unknown, unknown, 7))
    verify(!metadataAllowedForKids(unknown, teen, 12))
    verify(metadataAllowedForKids(unknown, teen, 16))
    verify(!metadataAllowedForKids(unknown, teen, 7))
    verify(!metadataAllowedForKids(mature, general, 16))
    println("Metadata cache: $checks behavioral checks passed")
}
