package com.vueo.shared.core.player

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.LinkedHashMap
import com.vueo.shared.core.media.SubtitleTextTransform
import java.io.ByteArrayOutputStream

/**
 * Keeps a prepared external subtitle in memory for the active app session.
 * Media3 can then seek/reopen the text source without contacting the provider again.
 */
object SubtitleSessionCache {
    private const val MAX_ENTRIES = 16
    private const val MAX_TOTAL_BYTES = 8 * 1024 * 1024
    internal const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private val entries = LinkedHashMap<String, ByteArray>(MAX_ENTRIES, .75f, true)
    private val transforms = mutableMapOf<String, SubtitleTextTransform>()
    private val transformVersions = mutableMapOf<String, Any>()
    private val registered = LinkedHashMap<String, Map<String, String>>()

    @Synchronized
    fun register(url: String, headers: Map<String, String> = emptyMap(), textTransform: SubtitleTextTransform? = null) {
        val transform = textTransform?.takeIf { it.algorithm == "aes-cbc" && it.scope == "cue-lines" && it.keys.size in 1..4 }
        if (!registered.containsKey(url) || transforms[url] != transform) {
            entries.remove(url)
            transformVersions[url] = Any()
        }
        if (transform == null) transforms.remove(url) else transforms[url] = transform
        registered[url] = headers.toMap()
        while (registered.size > 256) {
            val oldest = registered.keys.first()
            registered.remove(oldest)
            transformVersions.remove(oldest)
            transforms.remove(oldest)
            entries.remove(oldest)
        }
    }

    @Synchronized
    fun headers(url: String): Map<String, String> = registered[url].orEmpty()

    @Synchronized
    fun isRegistered(url: String): Boolean = registered.containsKey(url)

    fun put(url: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        prepareAndCache(url, bytes)
    }

    /**
     * Snapshot configuration under the cache lock, prepare outside it, then publish
     * only if that registration still exists. No shared mutable Cipher state.
     */
    internal fun prepareAndCache(url: String, bytes: ByteArray): ByteArray {
        val snapshot = synchronized(this) { Pair(transforms[url], transformVersions[url]) }
        val transform = snapshot.first
        val decoded = SubtitlePreparationDiagnostics.measure(url, if (transform == null) "plaintext" else "decrypt") {
            transform?.let {
                SubtitleCipherDecoder.decode(bytes, it.keys.map { key -> key.key }, it.keys.map { key -> key.iv })
            } ?: bytes
        }
        val prepared = SubtitlePreparationDiagnostics.measure(url, "normalize") {
            SubtitleTextNormalizer.normalize(decoded)
        }
        synchronized(this) {
            if (transformVersions[url] !== snapshot.second) {
                throw java.io.IOException("Subtitle transform changed during preparation")
            }
            if (prepared.isNotEmpty() && prepared.size <= MAX_FILE_BYTES) {
                entries[url] = prepared
                while (entries.size > MAX_ENTRIES || entries.values.sumOf { it.size } > MAX_TOTAL_BYTES) {
                    entries.remove(entries.keys.first())
                }
            }
        }
        return prepared
    }

    @Synchronized
    fun requiresTransform(url: String): Boolean = transforms[url] != null

    @Synchronized
    fun get(url: String): ByteArray? = entries[url]

}

class SubtitleSessionDataSource private constructor(
    private val upstream: DataSource,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null
    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource is already open" }
        val url = dataSpec.uri.toString()
        val registeredSubtitle = SubtitleSessionCache.isRegistered(url)
        val cacheSpan = if (registeredSubtitle) SubtitlePreparationDiagnostics.start(url, "media3_cache") else null
        val cached = SubtitleSessionCache.get(url)
        SubtitlePreparationDiagnostics.end(cacheSpan, if (cached == null) "miss" else "hit", cached?.size ?: -1)
        if (cached != null) {
            return openPrepared(dataSpec, cached)
        }
        val trackHeaders = SubtitleSessionCache.headers(url)
        val request = if (trackHeaders.isNotEmpty()) {
            dataSpec.buildUpon().setHttpRequestHeaders(
                dataSpec.httpRequestHeaders.filterKeys { key ->
                    trackHeaders.keys.none { it.equals(key, true) }
                } + trackHeaders
            ).build()
        } else dataSpec
        if (SubtitleSessionCache.requiresTransform(url) &&
            (dataSpec.position != 0L || dataSpec.length != -1L || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET)
        ) throw java.io.IOException("Encrypted subtitle must be prepared with a full GET")
        active = upstream
        val downloadSpan = if (registeredSubtitle) SubtitlePreparationDiagnostics.start(url, "media3_download") else null
        var downloadEnded = false
        var downloadResult = "not_prepared"
        try {
            val length = upstream.open(request)
            // Text parsers need the complete sidecar. Prepare registered full GETs once;
            // videos, partial requests and oversized sidecars retain their streaming path.
            if (!SubtitleSessionCache.isRegistered(url) || dataSpec.position != 0L ||
                dataSpec.length != -1L || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET
            ) return length
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= SubtitleSessionCache.MAX_FILE_BYTES) {
                val count = upstream.read(buffer, 0, buffer.size)
                if (count == -1) {
                    if (output.size() == 0) return length
                    SubtitlePreparationDiagnostics.end(downloadSpan, "ok", output.size())
                    downloadEnded = true
                    val prepared = SubtitleSessionCache.prepareAndCache(url, output.toByteArray())
                    upstream.close()
                    return openPrepared(dataSpec, prepared)
                }
                output.write(buffer, 0, count)
            }
            if (SubtitleSessionCache.requiresTransform(url)) {
                upstream.close()
                active = null
                throw java.io.IOException("Encrypted subtitle exceeds the 2 MB limit")
            }
            active = PrefixedDataSource(output.toByteArray(), upstream)
            return length
        } catch (e: Throwable) {
            downloadResult = e.javaClass.simpleName
            throw e
        } finally {
            if (!downloadEnded) SubtitlePreparationDiagnostics.end(downloadSpan, downloadResult)
        }
    }

    private fun openPrepared(dataSpec: DataSpec, bytes: ByteArray): Long {
        val prepared = ByteArrayDataSource(bytes)
        listeners.forEach(prepared::addTransferListener)
        active = prepared
        return prepared.open(dataSpec).also {
            val span = SubtitlePreparationDiagnostics.start(dataSpec.uri.toString(), "media3_bytes_ready")
            SubtitlePreparationDiagnostics.end(span, bytes = bytes.size)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)

    /** Replays bytes already inspected, then continues the same open connection. */
    private class PrefixedDataSource(
        private val prefix: ByteArray,
        private val upstream: DataSource,
    ) : DataSource by upstream {
        private var position = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= prefix.size) return upstream.read(buffer, offset, length)
            val count = minOf(length, prefix.size - position)
            prefix.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
    ) : DataSource.Factory {
        private val providerFactory = ProviderHlsDataSource.Factory(upstreamFactory)
        override fun createDataSource(): DataSource =
            SubtitleSessionDataSource(providerFactory.createDataSource())
    }
}
