package com.vueo.shared.core.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

/** Raw cue times, before the renderer's existing offset. No subtitle text is logged. */
data class SubtitleDialogue(val startMs: Long, val text: String)

class SubtitleSyncReadFailure(val userMessage: String, val reason: String, cause: Throwable? = null) : Exception(userMessage, cause)

object SubtitleDialogueSync {
    fun diagnostic(url: String, event: String, details: String = "") {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty().take(100)
        RuntimeDiagnostics.recordDiscoveryTrace(0L, "SUBTITLE_SYNC_$event", "host=$host $details")
    }

    const val MAX_BYTES = 2 * 1024 * 1024

    suspend fun load(url: String, factory: DataSource.Factory): List<SubtitleDialogue> = withContext(Dispatchers.IO) {
        diagnostic(url, "START")
        try {
            val cached = SubtitleSessionCache.get(url)
            val bytes = if (cached != null) {
                if (cached.size > MAX_BYTES) throw SubtitleSyncReadFailure("Subtitle exceeds the 2 MB sync limit.", "TOO_LARGE")
                cached
            } else {
                val source = factory.createDataSource()
                try {
                    source.open(DataSpec.Builder().setUri(Uri.parse(url)).build())
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count == 0) continue
                        if (output.size() + count > MAX_BYTES) throw SubtitleSyncReadFailure("Subtitle exceeds the 2 MB sync limit.", "TOO_LARGE")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } finally { runCatching { source.close() } }
            }
            coroutineContext.ensureActive()
            if (bytes.isEmpty()) throw SubtitleSyncReadFailure("Subtitle file is empty. Try another track.", "EMPTY_FILE")
            val charset = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            val raw = String(bytes, charset)
            val sample = raw.take(2000).trimStart().lowercase()
            val format = when {
                sample.startsWith("webvtt") || sample.startsWith("\uFEFFwebvtt") -> "VTT"
                "[events]" in raw.lowercase() -> "ASS"
                "-->" in raw -> "SRT_OR_VTT"
                "<tt" in sample || "<tt:" in sample -> "TTML"
                "<!doctype html" in sample || "<html" in sample -> "HTML"
                else -> "UNKNOWN"
            }
            diagnostic(url, "READ", "source=${if (cached != null) "cache" else "download"} bytes=${bytes.size} encoding=${charset.name()} format=$format")
            if (format == "HTML") throw SubtitleSyncReadFailure("Server returned a web page instead of subtitle text. Try another track.", "HTML_RESPONSE")
            if (format == "TTML") throw SubtitleSyncReadFailure("This subtitle uses TTML. Use manual Sync for this format.", "UNSUPPORTED_TTML")
            parse(raw).also {
                if (it.isEmpty()) throw SubtitleSyncReadFailure(
                    if (format == "UNKNOWN") "Subtitle format was not recognised. Use manual Sync or another track."
                    else "Subtitle was read, but no valid dialogue timestamps were found. Use manual Sync.",
                    if (format == "UNKNOWN") "UNSUPPORTED_FORMAT" else "NO_TIMESTAMPS"
                )
                SubtitleSessionCache.put(url, bytes)
                diagnostic(url, "READY", "cues=${it.size}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = when (error) {
                is SubtitleSyncReadFailure -> error
                is HttpDataSource.InvalidResponseCodeException -> SubtitleSyncReadFailure("Subtitle download failed (HTTP ${error.responseCode}). Try another track.", "HTTP_${error.responseCode}", error)
                is java.net.SocketTimeoutException -> SubtitleSyncReadFailure("Subtitle download timed out. Try again.", "NETWORK_TIMEOUT", error)
                is java.io.IOException -> SubtitleSyncReadFailure("Could not access the subtitle file. Try again or another track.", "READ_IO", error)
                else -> SubtitleSyncReadFailure("Subtitle processing failed. Use manual Sync and check Performance Diagnostic.", "PROCESSING", error)
            }
            diagnostic(url, "FAILED", "reason=${failure.reason} type=${error.javaClass.simpleName}")
            throw failure
        }
    }

    fun offset(videoMs: Long, cueMs: Long): Int? {
        val value = videoMs.coerceAtLeast(0L) - cueMs
        return if (value in -60_000L..60_000L) value.toInt() else null
    }

    fun parse(raw: String): List<SubtitleDialogue> {
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines()
        val result = mutableListOf<SubtitleDialogue>()
        var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        var events = false
        var index = 0
        while (index < lines.size && result.size < 20_000) {
            val line = lines[index].trim()
            if (line.startsWith("[")) events = line.equals("[Events]", true)
            if (events && line.startsWith("Format:", true)) fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
            if (events && line.startsWith("Dialogue:", true)) {
                val values = line.substringAfter(':').split(',', limit = fields.size)
                val startIndex = fields.indexOf("start")
                val textIndex = fields.indexOf("text")
                val start = values.getOrNull(startIndex)?.trim()?.let(::timestamp)
                val text = values.getOrNull(textIndex)?.let(::plainText)
                if (start != null && !text.isNullOrBlank()) result += SubtitleDialogue(start, text)
            } else if ("-->" in line) {
                val start = timestamp(line.substringBefore("-->").trim())
                val end = timestamp(line.substringAfter("-->").trim().substringBefore(' '))
                val text = StringBuilder()
                index++
                while (index < lines.size && lines[index].isNotBlank() && "-->" !in lines[index]) {
                    text.appendLine(lines[index]); index++
                }
                val clean = plainText(text.toString())
                if (start != null && end != null && end >= start && clean.isNotBlank()) result += SubtitleDialogue(start, clean)
                continue
            }
            index++
        }
        return result.distinct().sortedBy { it.startMs }
    }

    private fun timestamp(raw: String): Long? {
        val parts = raw.trim().replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 60 } ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull()?.takeIf { it in 0..999 } ?: return null else 0L
        return hours * 3_600_000L + minutes * 60_000L + (seconds * 1000).roundToLong()
    }

    private fun plainText(raw: String): String = raw.replace(Regex("\\{[^}]*}"), "")
        .replace(Regex("<[^>]*>"), "").replace("\\N", " ").replace("\\n", " ").replace("\\h", " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim().take(500)
}
