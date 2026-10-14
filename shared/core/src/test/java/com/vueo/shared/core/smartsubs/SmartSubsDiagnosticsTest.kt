package com.vueo.shared.core.smartsubs

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class SmartSubsDiagnosticsTest {
    @Test fun opensOnlyTheVueoBackendAndConfiguredDiagnosePath() {
        val uri = URI(SmartSubsDiagnostics.url("test.token_123"))
        assertEquals("https", uri.scheme)
        assertEquals(URI(VueoSmartSubsClient.BACKEND_URL).host, uri.host)
        assertEquals("/c/test.token_123/diagnose", uri.rawPath)
        assertNull(uri.query)
        assertNull(uri.fragment)
    }
    @Test fun tokenCannotInjectAnotherPathQueryOrFragment() {
        val uri = URI(SmartSubsDiagnostics.url("test/path?key=value#fragment"))
        assertTrue(uri.rawPath.startsWith("/c/test%2Fpath%3Fkey%3Dvalue%23fragment/"))
        assertNull(uri.query)
        assertNull(uri.fragment)
    }
    @Test fun blankWhitespaceControlAndOversizeTokensAreRejected() {
        for (token in listOf("", " ", "test\ntoken", "test token", "x".repeat(4097))) {
            try { SmartSubsDiagnostics.url(token); fail("Unsafe token accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
