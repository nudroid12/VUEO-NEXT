package com.vueo.shared.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProviderLogTest {
    @Test fun noisyProviderRetainsOnlyLatestMessages() {
        val logs = BoundedProviderLog(24)
        repeat(100_000) { logs += "message-$it" }
        assertEquals((99_976 until 100_000).map { "message-$it" }, logs.toList())
    }

    @Test fun concurrentConsoleCallbacksKeepBoundAndSnapshotsIndependent() {
        val logs = BoundedProviderLog(24)
        logs += "initial"
        val snapshot = logs.toList()
        val workers = List(4) { worker ->
            Thread { repeat(5_000) { logs += "$worker:$it" } }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        assertEquals(listOf("initial"), snapshot)
        assertEquals(24, logs.toList().size)
        assertTrue(logs.toList().all { ':' in it })
    }
}
