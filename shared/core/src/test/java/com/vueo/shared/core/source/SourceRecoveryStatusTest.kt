package com.vueo.shared.core.source

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceRecoverySession
import org.junit.Assert.*
import org.junit.Test

class SourceRecoveryStatusTest {
    private val a = SourceCandidate("a", "A", "https://example.com/a.m3u8",
        providerId = "test", providerName = "Test")
    private val b = a.copy(id = "b", name = "B", url = "https://example.com/b.m3u8")
    private fun stream(source: SourceCandidate) = StreamSource(
        name = source.name, url = source.url,
        providerId = source.providerId, providerName = source.providerName,
    )

    @Test fun tvRecoveredSourceClearsFailureWithoutForgettingOtherAttempts() {
        val session = SourceRecoverySession()
        verifyRecovery(session::markFailed, session::markReady, session::allowRetry,
            session::failedSourceUrls, { session.next(listOf(a, b))?.url })
    }

    @Test fun mobileRecoveredSourceClearsFailureWithoutForgettingOtherAttempts() {
        val session = PlayerSourceRecoverySession()
        verifyRecovery(
            { session.markFailed(stream(it)) }, { session.markReady(stream(it)) },
            { session.allowRetry(stream(it)) }, session::failedSourceUrls,
            { session.next(listOf(stream(a), stream(b)))?.url },
        )
    }

    private fun verifyRecovery(
        fail: (SourceCandidate) -> Unit,
        ready: (SourceCandidate) -> Unit,
        retry: (SourceCandidate) -> Unit,
        failures: () -> Set<String>,
        next: () -> String?,
    ) {
        fail(a)
        fail(b)
        val oldSnapshot = failures()
        ready(a)
        assertEquals(setOf(b.url), failures())
        assertEquals(setOf(a.url, b.url), oldSnapshot)
        ready(a) // READY + first frame must be idempotent.
        assertEquals(setOf(b.url), failures())
        assertNull(next()) // Success must not make attempted URLs eligible again.
        fail(a) // Later real failures remain visible.
        assertEquals(setOf(a.url, b.url), failures())
        retry(a)
        assertEquals(setOf(b.url), failures())
        assertEquals(a.url, next()) // Explicit retry re-enables just A.
        ready(a)
        assertEquals(setOf(b.url), failures())
    }
}
