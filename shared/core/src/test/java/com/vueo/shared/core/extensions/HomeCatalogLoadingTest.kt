package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.CatalogPage
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeCatalogLoadingTest {
    private fun addon(id: String, catalogs: List<String>, load: suspend (String) -> Unit = {}): MediaExtension =
        object : MediaExtension {
            override val descriptor = ExtensionDescriptor(
                id = id, name = id, version = "1", kind = ExtensionKind.STREMIO_ADDON,
                baseUrl = "https://example.test/$id",
                catalogs = catalogs.map { CatalogDescriptor(type = "movie", id = it) },
            )
            override suspend fun catalog(type: String, catalogId: String, extras: Map<String, String>): CatalogPage {
                load(catalogId)
                return CatalogPage(listOf(MediaItem(id = "$id-$catalogId", type = "movie", name = catalogId)))
            }
        }

    @Test
    fun everyCompletedRowIsPublishedWithoutWaitingForSlowRemainder() = runBlocking {
        val secondPublished = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        val engine = UnifiedMediaEngine().apply {
            install(addon("a", listOf("one", "two", "slow")) { if (it == "slow") releaseSlow.await() })
        }
        val sizes = mutableListOf<Int>()
        val request = async {
            engine.loadCatalogRows(
                forceRefresh = true, updateHomeCache = false, catalogLoadGate = Semaphore(1),
                onPartial = { rows ->
                    sizes += rows.size
                    if (rows.size == 2) secondPublished.complete(Unit)
                },
            )
        }
        withTimeout(5_000) { secondPublished.await() }
        assertEquals(listOf(1, 2), sizes)
        assertFalse(request.isCompleted)
        releaseSlow.complete(Unit)
        assertEquals(3, request.await().size)
        assertEquals(listOf(1, 2, 3), sizes)
    }

    @Test
    fun addonScopedLoadsDoNotOverwriteTheCompleteHomeCache() = runBlocking {
        val previous = CatalogRow(id = "cached:movie:one", title = "Cached", providerName = "cached",
            items = listOf(MediaItem(id = "cached", type = "movie", name = "Cached")))
        CatalogDiscoveryCache.putHome(listOf(previous))
        val engine = UnifiedMediaEngine().apply {
            install(addon("a", listOf("one")))
            install(addon("b", listOf("one")))
        }
        val rows = engine.loadCatalogRows(extensionIds = setOf("a"), updateHomeCache = false)
        assertEquals(listOf("a:movie:one"), rows.map { it.id })
        assertEquals(listOf(previous), CatalogDiscoveryCache.home(allowStale = true))
    }

    @Test
    fun parallelAddonLoadsShareOneConcurrencyLimit() = runBlocking {
        val running = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val engine = UnifiedMediaEngine().apply {
            install(addon("a", listOf("one")) {
                val count = running.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                firstStarted.complete(Unit)
                releaseFirst.await()
                running.decrementAndGet()
            })
            install(addon("b", listOf("one")) {
                val count = running.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                running.decrementAndGet()
            })
        }
        val gate = Semaphore(1)
        val first = async { engine.loadCatalogRows(extensionIds = setOf("a"), updateHomeCache = false, catalogLoadGate = gate) }
        withTimeout(5_000) { firstStarted.await() }
        val second = async { engine.loadCatalogRows(extensionIds = setOf("b"), updateHomeCache = false, catalogLoadGate = gate) }
        yield() // Let the second addon reach the shared gate while the first holds it.
        releaseFirst.complete(Unit)
        val results = awaitAll(first, second)
        assertTrue(results.all { it.size == 1 })
        assertEquals(1, maximum.get())
    }
    @Test
    fun cancellingAnAddonLoadReleasesItsSharedSlot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = UnifiedMediaEngine().apply {
            install(addon("blocked", listOf("one")) {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            })
            install(addon("ready", listOf("one")))
        }
        val gate = Semaphore(1)
        val request = async {
            engine.loadCatalogRows(extensionIds = setOf("blocked"), updateHomeCache = false, catalogLoadGate = gate)
        }
        withTimeout(5_000) { started.await() }
        request.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
        val rows = withTimeout(5_000) {
            engine.loadCatalogRows(extensionIds = setOf("ready"), updateHomeCache = false, catalogLoadGate = gate)
        }
        assertEquals(1, rows.size)
    }

}
