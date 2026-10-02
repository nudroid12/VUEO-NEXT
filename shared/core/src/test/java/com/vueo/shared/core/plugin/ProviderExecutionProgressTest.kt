package com.vueo.shared.core.plugin

import org.junit.Assert.*
import org.junit.Test

class ProviderExecutionProgressTest {
    @Test fun timeoutSnapshotRetainsConsoleAndPendingDuration() {
        var clock = 0L
        val progress = ProviderExecutionProgress { clock }
        progress.stage("Evaluating provider script")
        progress += "LOG: Found movie page"
        clock = 2_000_000_000L
        progress.begin("HTTP https://example.org/movie")
        clock = 10_000_000_000L
        val logs = progress.snapshot()
        assertTrue(logs.contains("LOG: Found movie page"))
        assertEquals("Started HTTP https://example.org/movie", ProviderDiagnosticProgress.lastStage(logs))
        assertEquals(listOf("HTTP https://example.org/movie • waiting 8000ms"), ProviderDiagnosticProgress.pending(logs))
        assertTrue(ProviderDiagnosticProgress.timeline(logs).any { it.startsWith("+2000ms Started HTTP") })
    }

    @Test fun completedAndFailedRequestsAreRemovedButConcurrentPendingSurvives() {
        val progress = ProviderExecutionProgress { 0L }
        val first = progress.begin("HTTP first")
        val second = progress.begin("HTTP second")
        progress.begin("WebView third")
        progress.finish(first, "completed")
        progress.finish(second, "failed (IOException)")
        assertEquals(listOf("WebView third • waiting 0ms"), ProviderDiagnosticProgress.pending(progress.snapshot()))
    }

    @Test fun boundedEvidenceRetainsSummaryAndRecentConsoleErrors() {
        val progress = ProviderExecutionProgress { 0L }
        repeat(1000) { progress += "LOG: $it" }
        progress += "ERROR: HTTP 403"
        repeat(100) { progress.begin("HTTP $it") }
        val logs = progress.snapshot()
        assertTrue(logs.size <= 40)
        assertTrue(logs.contains("ERROR: HTTP 403"))
        assertEquals(5, ProviderDiagnosticProgress.pending(logs).size)
        assertTrue(ProviderDiagnosticProgress.pending(logs).last().contains("96 additional"))
        assertNotNull(ProviderDiagnosticProgress.lastStage(logs))
    }

    @Test fun parallelWritersKeepBoundedConsistentSnapshots() {
        val progress = ProviderExecutionProgress { 0L }
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val workers = (1..4).map { worker -> Thread {
            try { repeat(100) {
                val id = progress.begin("HTTP $worker/$it")
                progress += "LOG: $worker/$it"
                progress.finish(id, "completed")
                assertTrue(progress.snapshot().size <= 40)
            } } catch (error: Throwable) { failures.add(error) }
        } }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        assertTrue(failures.toString(), failures.isEmpty())
        assertTrue(ProviderDiagnosticProgress.pending(progress.snapshot()).isEmpty())
    }

    @Test fun legacyLogsHaveNoInventedProgress() {
        val logs = listOf("ERROR: Timed out after 10s")
        assertNull(ProviderDiagnosticProgress.lastStage(logs))
        assertTrue(ProviderDiagnosticProgress.pending(logs).isEmpty())
        assertTrue(ProviderDiagnosticProgress.timeline(logs).isEmpty())
    }
}
