package com.vueo.shared.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class CrashRecoveryPolicyTest {
    private fun report(time: Long, label: String = "Java error") = AppCrashReport(time, label, "stack")

    @Test fun dismissalAndCurrentSessionDoNotReappear() {
        assertNull(CrashRecoveryPolicy.pendingLocal(report(100), 100, 200))
        assertNull(CrashRecoveryPolicy.pendingLocal(report(100), 150, 200))
        assertNull(CrashRecoveryPolicy.pendingLocal(report(200), 0, 200))
        assertNull(CrashRecoveryPolicy.pendingLocal(report(300), 0, 200))
        assertEquals(100L, CrashRecoveryPolicy.pendingLocal(report(100), 0, 200)?.timestampMs)
    }

    @Test fun duplicateAndroidExitKeepsRicherJavaReport() {
        val java = report(100_000)
        assertSame(java, CrashRecoveryPolicy.choose(java, report(102_000, "System Java crash")))
    }

    @Test fun newerDistinctNativeCrashWinsOverUnclosedOlderReport() {
        val native = report(130_000, "Native crash")
        assertSame(native, CrashRecoveryPolicy.choose(report(100_000), native))
    }

    @Test fun olderSystemRecordCannotReplaceNewJavaCrash() {
        val java = report(130_000)
        assertSame(java, CrashRecoveryPolicy.choose(java, report(100_000)))
        assertSame(java, CrashRecoveryPolicy.choose(java, null))
        assertSame(java, CrashRecoveryPolicy.choose(null, java))
        assertNull(CrashRecoveryPolicy.choose(null, null))
    }

    @Test fun copiedEvidenceRedactsCredentialsAndUrlQueries() {
        val sanitized = CrashRecoveryPolicy.sanitize("Authorization: Bearer bearerSecret api_key=secret token=private https://user:password@host/path?token=hidden#frag\n at com.vueo.Player.open(Player.kt:42)")
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("private"))
        assertFalse(sanitized.contains("hidden"))
        assertFalse(sanitized.contains("bearerSecret"))
        assertFalse(sanitized.contains("password"))
        assertTrue(sanitized.contains("host/path"))
        assertTrue(sanitized.contains("Player.kt:42"))
    }
}
