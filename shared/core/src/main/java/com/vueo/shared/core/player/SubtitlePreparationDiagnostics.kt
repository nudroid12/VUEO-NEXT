package com.vueo.shared.core.player

import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.shared.core.diagnostics.ProviderDiagnostics
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** Metadata-only timing; no subtitle content, URLs, headers or cipher material. */
object SubtitlePreparationDiagnostics {
    private val sequence = AtomicLong()
    data class Span(val id: Long, val file: String, val stage: String, val started: Long)
    fun start(url: String, stage: String): Span? {
        if (!PerformanceDiagnostics.isCollecting() && !ProviderDiagnostics.isCollecting()) return null
        return runCatching {
            val file = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
            Span(sequence.incrementAndGet(), file, stage, System.nanoTime()).also {
                RuntimeDiagnostics.recordPlayerEvent("Shared", "SUBTITLE_PREP",
                    "id=${it.id} file=${it.file} stage=${it.stage} state=start")
            }
        }.getOrNull()
    }
    fun end(span: Span?, result: String = "ok", bytes: Int = -1) {
        if (span == null) return
        runCatching {
            RuntimeDiagnostics.recordPlayerEvent("Shared", "SUBTITLE_PREP",
                "id=${span.id} file=${span.file} stage=${span.stage} state=end result=$result ms=${(System.nanoTime()-span.started)/1_000_000} bytes=$bytes")
        }
    }
    inline fun <T> measure(url: String, stage: String, block: () -> T): T {
        val span = start(url, stage)
        try { return block().also { end(span) } }
        catch (e: Throwable) { end(span, e.javaClass.simpleName); throw e }
    }
}
