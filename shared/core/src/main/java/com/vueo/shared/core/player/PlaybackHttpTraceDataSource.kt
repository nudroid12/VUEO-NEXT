package com.vueo.shared.core.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Collections

/** Observes the existing transport; does not retry or change requests. */
class PlaybackHttpTraceDataSource private constructor(
    private val upstream: DataSource,
    private val owner: Factory,
) : DataSource {
    private var request: DataSpec? = null
    private var headers: Map<String, String> = emptyMap()
    private var capture: ByteArrayOutputStream? = null
    private var failed = false
    private var prefixChecked = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        request = dataSpec
        failed = false
        prefixChecked = false
        headers = owner.defaultHeaders().filterKeys { key ->
            dataSpec.httpRequestHeaders.keys.none { it.equals(key, true) }
        } + dataSpec.httpRequestHeaders
        capture = ByteArrayOutputStream()
        return try { upstream.open(dataSpec) } catch (error: Exception) {
            runCatching { report(error, "open") }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        val count = upstream.read(buffer, offset, length)
        capture?.let { output ->
            if (count > 0) {
                if (output.size() + count <= 256 * 1024) {
                    output.write(buffer, offset, count)
                    // Stop collecting binary media after its first bytes.
                    if (!prefixChecked && output.size() >= 7) {
                        prefixChecked = true
                        if (!output.toByteArray().copyOfRange(0, 7)
                                .contentEquals("#EXTM3U".toByteArray())) capture = null
                    }
                } else capture = null
            } else if (count == -1) {
                runCatching { owner.inspectManifest(upstream.uri ?: request?.uri, output.toString("UTF-8")) }
                capture = null
            }
        }
        count
    } catch (error: Exception) {
        runCatching { report(error, "read") }
        throw error
    }

    private fun report(error: Exception, phase: String) {
        if (failed) return
        failed = true
        val spec = request ?: return
        var cause: Throwable? = error
        var status: Int? = null
        repeat(8) {
            val current = cause
            if (current is HttpDataSource.InvalidResponseCodeException) status = current.responseCode
            cause = current?.cause
        }
        fun present(name: String) = headers.any { it.key.equals(name, true) && it.value.isNotBlank() }
        val uri = upstream.uri ?: spec.uri
        // Paths, query strings, header values and response bodies must never enter this log.
        val id = MessageDigest.getInstance("SHA-256").digest(spec.uri.toString().toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        RuntimeDiagnostics.recordPlayerEvent(owner.platform, "HTTP_REQUEST_ERROR",
            "id=$id kind=${owner.kind(spec.uri)} host=${uri.host.orEmpty().take(100)} " +
                "phase=$phase status=${status ?: -1} position=${spec.position} length=${spec.length} " +
                "headerScope=configured ua=${present("User-Agent")} referer=${present("Referer")} origin=${present("Origin")} " +
                "proof=${present("X-Settlar-Key-Proof")} cookie=${present("Cookie")}")
    }

    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() {
        try { upstream.close() } finally { request = null; capture = null; headers = emptyMap() }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        internal val platform: String,
        internal val defaultHeaders: () -> Map<String, String>,
        private val rootUrl: () -> String?,
    ) : DataSource.Factory {
        private val kinds = Collections.synchronizedMap(LinkedHashMap<String, String>())
        internal fun kind(uri: Uri): String = when {
            SubtitleSessionCache.isRegistered(uri.toString()) -> "subtitle"
            uri.toString() == rootUrl() -> "root"
            else -> kinds[uri.toString()] ?: "unknown"
        }
        internal fun inspectManifest(base: Uri?, text: String) {
            if (base == null || !text.startsWith("#EXTM3U")) return
            val master = text.contains("#EXT-X-STREAM-INF")
            text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
                val role = when {
                    line.startsWith("#EXT-X-KEY:") -> "key"
                    line.startsWith("#EXT-X-MAP:") -> "init"
                    line.startsWith("#EXT-X-MEDIA:") -> "rendition"
                    line.startsWith("#") -> return@forEach
                    master -> "playlist"
                    else -> "segment"
                }
                val reference = if (line.startsWith("#"))
                    Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return@forEach
                    else line
                val resolved = runCatching { java.net.URI(base.toString()).resolve(reference).toString() }
                    .getOrNull() ?: return@forEach
                synchronized(kinds) {
                    kinds[resolved] = role
                    while (kinds.size > 2048) kinds.remove(kinds.keys.first())
                }
            }
        }
        override fun createDataSource(): DataSource =
            PlaybackHttpTraceDataSource(upstreamFactory.createDataSource(), this)
    }
}
