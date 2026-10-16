package com.vueo.mobile.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class PlayerResourceLeaseTest {
    private fun outstanding(line: String) = line.substringAfter("outstanding=").substringBefore(' ').toInt()

    @Test fun successfulReleaseBalancesOwnershipAndOrdersCleanup() {
        val events = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val lease = MobilePlayerResourceLease(events::add)
        lease.release({ steps.add("stop") }, { steps.add("release") })
        assertEquals(listOf("stop", "release"), steps)
        assertTrue(events.first().contains("event=created"))
        assertTrue(events.last().contains("event=released"))
        assertEquals(outstanding(events.first()) - 1, outstanding(events.last()))
    }
    @Test fun cleanupFailureCannotSkipRelease() {
        val events = mutableListOf<String>()
        var released = false
        val lease = MobilePlayerResourceLease(events::add)
        lease.release({ throw IllegalStateException("private details") }, { released = true })
        assertTrue(released)
        assertTrue(events.any { "event=cleanup_failed" in it })
        assertTrue(events.last().contains("event=released"))
        assertFalse(events.any { "private details" in it })
    }
    @Test fun failureToReleaseIsNotReportedAsFreed() {
        val events = mutableListOf<String>()
        val lease = MobilePlayerResourceLease(events::add)
        lease.release({}, { throw IllegalStateException() })
        assertTrue(events.last().contains("event=release_failed"))
        assertEquals(outstanding(events.first()), outstanding(events.last()))
        assertFalse(events.any { "event=released " in it })
    }
    @Test fun duplicateDisposalReleasesOnlyOnce() {
        val count = AtomicInteger()
        val lease = MobilePlayerResourceLease {}
        repeat(3) { lease.release({}, { count.incrementAndGet() }) }
        assertEquals(1, count.get())
    }
    @Test fun concurrentDisposalCannotReleaseTwice() {
        val count = AtomicInteger()
        val lease = MobilePlayerResourceLease {}
        val start = CountDownLatch(1)
        val threads = List(8) { Thread { start.await(); lease.release({}, { count.incrementAndGet() }) } }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach { it.join(5000); assertFalse(it.isAlive) }
        assertEquals(1, count.get())
    }
    @Test fun diagnosticFailureCannotSkipRelease() {
        var released = false
        val lease = MobilePlayerResourceLease { throw IllegalStateException() }
        lease.release({ throw IllegalStateException() }, { released = true })
        assertTrue(released)
    }
    @Test fun fatalCleanupStillRunsReleaseAndPropagates() {
        var released = false
        val lease = MobilePlayerResourceLease {}
        var propagated = false
        try {
            lease.release({ throw AssertionError("fatal") }, { released = true })
        } catch (_: AssertionError) { propagated = true }
        assertTrue(released)
        assertTrue(propagated)
    }
    @Test fun separatePlayersHaveSeparateIdsAndBalance() {
        val events = mutableListOf<String>()
        val first = MobilePlayerResourceLease(events::add)
        val second = MobilePlayerResourceLease(events::add)
        assertNotEquals(events[0].substringBefore(" event="), events[1].substringBefore(" event="))
        assertEquals(outstanding(events[0]) + 1, outstanding(events[1]))
        first.release({}, {})
        second.release({}, {})
        assertEquals(outstanding(events[0]) - 1, outstanding(events.last()))
    }
}
