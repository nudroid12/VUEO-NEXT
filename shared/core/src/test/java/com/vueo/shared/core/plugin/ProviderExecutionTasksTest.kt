package com.vueo.shared.core.plugin

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProviderExecutionTasksTest {
    @Test fun finishCancelsPendingWorkAndRejectsLateWork() = runBlocking {
        val tasks = ProviderExecutionTasks()
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val pending = async {
            tasks.run<Unit> {
                try { started.complete(Unit); awaitCancellation() }
                finally { cancelled = true }
            }
        }
        started.await()
        tasks.finish()
        assertNull(pending.await())
        assertTrue(cancelled)
        assertNull(tasks.run<Unit> { fail("Late work must not start") })
    }

    @Test fun sequentialAndWaitAllProvidersKeepAwaitedResults() = runBlocking {
        val tasks = ProviderExecutionTasks()
        assertEquals(1, tasks.run { delay(1); 1 })
        assertEquals(2, tasks.run { delay(1); 2 })
        val results = (1..3).map { n -> async { tasks.run { delay(n.toLong()); n } } }.awaitAll()
        assertEquals(listOf(1, 2, 3), results)
        tasks.finish()
    }

    @Test fun finishingOneProviderDoesNotCancelAnother() = runBlocking {
        val first = ProviderExecutionTasks()
        val second = ProviderExecutionTasks()
        val pending = async { second.run { delay(1); "second" } }
        first.finish()
        assertEquals("second", pending.await())
    }

    @Test fun clearTimerHandlesBothRegistrationOrders() = runBlocking {
        val tasks = ProviderExecutionTasks()
        tasks.cancelTimer("early")
        assertNull(tasks.run<Unit>("early") { fail("Cleared timer must not start") })
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val pending = async {
            tasks.run<Unit>("active") {
                try { started.complete(Unit); awaitCancellation() }
                finally { cancelled = true }
            }
        }
        started.await()
        tasks.cancelTimer("active")
        assertNull(pending.await())
        assertTrue(cancelled)
        assertEquals(42, tasks.run { 42 })
    }

    @Test fun callerTimeoutAndOriginalErrorsArePreserved() = runBlocking {
        val tasks = ProviderExecutionTasks()
        assertNull(withTimeoutOrNull(20) { tasks.run { awaitCancellation() } })
        assertEquals("original", runCatching {
            tasks.run<Unit> { throw IllegalStateException("original") }
        }.exceptionOrNull()?.message)
    }
}
