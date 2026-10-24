package com.vueo.shared.core.player

import android.net.Uri
import android.util.Base64
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSchemeDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.LinkedHashMap

/** Explicit provider playback instructions; no host or content-language rules. */
class ProviderHlsDataSource private constructor(
    private val upstream: DataSource,
    private val session: Factory,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null
    private var response: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
        active?.takeIf { it !== upstream }?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource is already open" }
        // HLS may reference inline keys or other embedded bytes. These are local
        // data, so route them before the provider policy and HTTP transport.
        if (dataSpec.uri.scheme.equals(DataSchemeDataSource.SCHEME_DATA, ignoreCase = true)) {
            val embedded = DataSchemeDataSource()
            listeners.forEach(embedded::addTransferListener)
            active = embedded
            return embedded.open(dataSpec)
        }
        active = upstream
        val playlistPolicy = session.policy(dataSpec.uri)
        val segment = playlistPolicy == null
        val policy = playlistPolicy ?: session.segmentPolicy(dataSpec.uri)
        if (policy == null) {
            return upstream.open(dataSpec)
        }
        if ((!segment && (dataSpec.position != 0L || dataSpec.length != -1L)) || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET) {
            throw IOException("Partial provider playlist requests are unsupported")
        }
        val request = dataSpec.buildUpon().setUri(dataSpec.uri.buildUpon().fragment(null).build()).setPosition(0L).setLength(-1L).build()
        upstream.open(request)
        val resolved = upstream.uri ?: request.uri
        response = upstream.responseHeaders.filterKeys {
            !it.equals("Content-Length", true) && !it.equals("Content-Encoding", true)
        }
        val body = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(8192)
            while (true) {
                val count = upstream.read(buffer, 0, buffer.size)
                if (count == -1) break
                if (body.size() + count > (if (segment) 32 else 2) * 1024 * 1024) throw IOException("provider playlist exceeds size limit")
                body.write(buffer, 0, count)
            }
        } finally {
            upstream.close()
        }
        val raw = body.toByteArray()
        val plain = if (segment) {
            ProviderHlsCodec.decodeSegment(raw, policy.segmentPrefixes.toTypedArray(), policy.segmentKey, policy.segmentPlain)
        } else if (raw.size >= 7 && String(raw, 0, 7, Charsets.UTF_8) == "#EXTM3U") {
            ProviderHlsCodec.filterAudio(raw, policy.audioLanguages)
        } else {
            if (policy.encoding != "base64-xor") throw IOException("Expected plain HLS playlist")
            val encoded = String(raw, Charsets.US_ASCII).trim()
            if (encoded.isEmpty() || !encoded.matches(Regex("[A-Za-z0-9+/=\\r\\n]+"))) {
                throw IOException("Invalid provider playlist encoding")
            }
            val cipher = try { Base64.decode(encoded, Base64.DEFAULT) } catch (error: IllegalArgumentException) {
                throw IOException("Invalid provider playlist encoding", error)
            }
            ProviderHlsCodec.decode(cipher, policy.key, policy.audioLanguages)
        }
        if (!segment) session.registerChildren(resolved, String(plain, Charsets.UTF_8), policy)
        val decoded = ByteArrayDataSource(plain)
        active = decoded
        return decoded.open(dataSpec.buildUpon().setUri(resolved).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(active).read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (active === upstream) upstream.responseHeaders else response
    override fun close() {
        try { active?.close() } finally { active = null; response = emptyMap() }
    }

    internal data class Policy(val encoding: String, val key: ByteArray, val audioLanguages: Set<String>, val segmentPrefixes: List<String> = emptyList(), val segmentKey: ByteArray = byteArrayOf(), val segmentPlain: ByteArray = byteArrayOf())

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        private val policies = LinkedHashMap<String, Policy>()
        private val segments = LinkedHashMap<String, Policy>()

        @Synchronized
        internal fun segmentPolicy(uri: Uri): Policy? = segments[uri.buildUpon().fragment(null).build().toString()]

        @Synchronized
        internal fun policy(uri: Uri): Policy? {
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
            val identity = uri.buildUpon().fragment(null).build().toString()
            val fragment = uri.fragment.orEmpty()
            if (fragment.startsWith("vueo_hls_v1=")) {
                if (fragment.length > 1024) throw IOException("Provider HLS parameters exceed size limit")
                val parameters = linkedMapOf<String, String>()
                fragment.split('&').forEach { part ->
                    val pair = part.split('=', limit = 2)
                    if (pair.size != 2 || parameters.put(pair[0], pair[1]) != null) throw IOException("Invalid provider HLS parameters")
                }
                if (parameters.keys.any { it !in setOf("vueo_hls_v1", "key", "audio", "segment_prefixes", "segment_key", "segment_plain") }) throw IOException("Unknown provider HLS parameter")
                val encoding = parameters["vueo_hls_v1"].orEmpty()
                if (encoding !in setOf("plain", "base64-xor")) throw IOException("Unsupported provider HLS encoding")
                val hex = parameters["key"].orEmpty()
                if (encoding == "base64-xor" && (!hex.matches(Regex("[0-9a-f]{2,128}")) || hex.length % 2 != 0)) throw IOException("Invalid provider HLS key")
                val key = if (encoding == "plain") byteArrayOf() else ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                val languages = parameters["audio"].orEmpty().split(',').filter(String::isNotEmpty).toSet()
                if (languages.size > 16 || languages.any { !it.matches(Regex("[a-z0-9_-]{1,20}")) }) throw IOException("Invalid provider audio selection")
                val prefixes = parameters["segment_prefixes"].orEmpty().split(',').filter(String::isNotEmpty)
                fun hexBytes(value: String): ByteArray {
                    if (!value.matches(Regex("(?:[0-9a-f]{2}){1,64}"))) throw IOException("Invalid provider segment key")
                    return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                }
                if (prefixes.size > 8 || prefixes.any { !it.matches(Regex("(?:[0-9a-f]{2}|\\?\\?){1,64}")) || it.replace("??", "").isEmpty() }) throw IOException("Invalid provider segment prefix")
                val segmentKey = parameters["segment_key"]?.let(::hexBytes) ?: byteArrayOf()
                val segmentPlain = parameters["segment_plain"]?.let(::hexBytes) ?: byteArrayOf()
                if (prefixes.isEmpty() && (segmentKey.isNotEmpty() || segmentPlain.isNotEmpty())) throw IOException("Missing provider segment prefixes")
                policies[identity] = Policy(encoding, key, languages, prefixes, segmentKey, segmentPlain)
                while (policies.size > 128) policies.remove(policies.keys.first())
            }
            return policies[identity]
        }

        @Synchronized
        internal fun registerChildren(base: Uri, playlist: String, policy: Policy) {
            val master = playlist.contains("#EXT-X-STREAM-INF:") || playlist.contains("#EXT-X-I-FRAME-STREAM-INF:")
            if (!master) {
                // Exact media references only. Encryption keys and initialization data stay untouched.
                if (policy.segmentPrefixes.isEmpty()) return
                playlist.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { reference ->
                    val child = runCatching { java.net.URI(base.toString()).resolve(reference) }.getOrNull() ?: return@forEach
                    if (child.scheme != "https" || child.host.isNullOrBlank()) return@forEach
                    segments[child.toString()] = policy
                    while (segments.size > 4096) segments.remove(segments.keys.first())
                }
                return
            }
            val uriAttribute = Regex("URI=\"([^\"]+)\"")
            playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
                val reference = when {
                    line.startsWith("#EXT-X-MEDIA:") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") -> uriAttribute.find(line)?.groupValues?.get(1)
                    !line.startsWith("#") -> line
                    else -> null
                } ?: return@forEach
                val child = runCatching { Uri.parse(java.net.URI(base.toString()).resolve(reference).toString()) }.getOrNull() ?: return@forEach
                if (child.scheme != "https" || child.host != base.host) return@forEach
                policies[child.buildUpon().fragment(null).build().toString()] = policy
                while (policies.size > 128) policies.remove(policies.keys.first())
            }
        }

        override fun createDataSource(): DataSource = ProviderHlsDataSource(upstreamFactory.createDataSource(), this)
    }
}
