package com.vueo.shared.core.search

import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EntityResultsPipelineTest {
    private fun item(id: String) = MediaItem(id, "movie", id)

    @Test fun fetchRunsOnDefaultAndCollectorStaysOnCaller() = runBlocking {
        val caller = Thread.currentThread()
        var fetchedOn: Thread? = null
        EntityResultsPipeline.batches { partial ->
            fetchedOn = Thread.currentThread()
            partial(listOf(item("partial")))
            listOf(item("final"))
        }.collect { assertSame(caller, Thread.currentThread()) }
        assertNotSame(caller, fetchedOn)
        assertTrue(fetchedOn!!.name.contains("DefaultDispatcher"))
    }
    @Test fun terminalSuccessIsDelivered() = runBlocking {
        val batches = EntityResultsPipeline.batches { listOf(item("final")) }.toList()
        assertEquals(listOf(item("final")), batches.last().items)
        assertTrue(batches.last().complete)
        assertNull(batches.last().failureClass)
    }
    @Test fun failureRetainsLatestPartialEvenWhenConflated() = runBlocking {
        val batches = EntityResultsPipeline.batches { partial ->
            repeat(1000) { partial(listOf(item("$it"))) }
            throw IOException("private URL")
        }.toList()
        assertEquals(listOf(item("999")), batches.last().items)
        assertTrue(batches.last().complete)
        assertEquals("IOException", batches.last().failureClass)
        assertFalse(batches.last().toString().contains("private URL"))
    }
    @Test fun failureBeforeAnyPartialCompletesEmpty() = runBlocking {
        val terminal = EntityResultsPipeline.batches { throw IOException() }.last()
        assertTrue(terminal.complete)
        assertTrue(terminal.items.isEmpty())
        assertEquals("IOException", terminal.failureClass)
    }
    @Test fun finalEmptyResultIsAuthoritative() = runBlocking {
        val terminal = EntityResultsPipeline.batches { partial ->
            partial(listOf(item("temporary")))
            emptyList()
        }.last()
        assertTrue(terminal.complete)
        assertTrue(terminal.items.isEmpty())
        assertNull(terminal.failureClass)
    }
    @Test fun partialSnapshotCannotBeChangedByProducerMutation() = runBlocking {
        val delivered = CompletableDeferred<Unit>()
        val batches = EntityResultsPipeline.batches { partial ->
            val mutable = mutableListOf(item("original"))
            partial(mutable)
            delivered.await()
            mutable.clear()
            mutable.add(item("mutated"))
            mutable
        }.onEach { if (!it.complete) delivered.complete(Unit) }.toList()
        assertEquals(listOf(item("original")), batches.first().items)
        assertEquals(listOf(item("mutated")), batches.last().items)
    }
    @Test fun lateCallbackCannotPublishAfterCompletion() = runBlocking {
        lateinit var late: (List<MediaItem>) -> Unit
        val batches = EntityResultsPipeline.batches { partial ->
            late = partial
            listOf(item("final"))
        }.toList()
        late(listOf(item("late")))
        assertEquals(listOf(item("final")), batches.last().items)
        assertEquals(1, batches.count { it.complete })
    }
    @Test fun collectorCancellationCancelsFetchAndRejectsLateCallbacks() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        var delivered = 0
        lateinit var late: (List<MediaItem>) -> Unit
        val job = launch {
            EntityResultsPipeline.batches { partial ->
                late = partial
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped.complete(Unit) }
            }.collect { delivered++ }
        }
        withTimeout(5000) { started.await() }
        job.cancelAndJoin()
        withTimeout(5000) { stopped.await() }
        late(listOf(item("late")))
        assertEquals(0, delivered)
    }
    @Test fun cancelledFetchDoesNotBecomeFailureBatch() = runBlocking {
        val terminalSeen = AtomicBoolean(false)
        try {
            EntityResultsPipeline.batches { throw CancellationException("cancelled") }
                .collect { if (it.complete) terminalSeen.set(true) }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(terminalSeen.get())
    }
    @Test fun prepareRunsOnDefaultAndReturnsTheValue() = runBlocking {
        val caller = Thread.currentThread()
        val value = EntityResultsPipeline.prepare {
            assertNotSame(caller, Thread.currentThread())
            listOf(3, 1, 2).sorted()
        }
        assertEquals(listOf(1, 2, 3), value)
    }
    @Test fun cancelledNonCooperativePreparationCannotPublish() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val unblock = CountDownLatch(1)
        var published = false
        val job = launch {
            EntityResultsPipeline.prepare {
                started.complete(Unit)
                check(unblock.await(5, TimeUnit.SECONDS))
                "stale"
            }
            published = true
        }
        withTimeout(5000) { started.await() }
        job.cancel()
        unblock.countDown()
        job.join()
        assertFalse(published)
    }
    @Test fun retryUsesANewProducerWithoutOldTerminalState() = runBlocking {
        var calls = 0
        fun load() = EntityResultsPipeline.batches {
            if (++calls == 1) throw IOException()
            listOf(item("retry"))
        }
        assertNotNull(load().last().failureClass)
        val retried = load().last()
        assertNull(retried.failureClass)
        assertEquals(listOf(item("retry")), retried.items)
        assertEquals(2, calls)
    }
    @Test fun inheritedFetchChildrenKeepSingleCallerSequencing() = runBlocking {
        val active = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        EntityResultsPipeline.batches {
            coroutineScope {
                List(12) {
                    async {
                        val count = active.incrementAndGet()
                        peak.updateAndGet { old -> maxOf(old, count) }
                        repeat(100) { Thread.yield() }
                        active.decrementAndGet()
                    }
                }.awaitAll()
            }
            emptyList()
        }.toList()
        assertEquals(1, peak.get())
    }
}
