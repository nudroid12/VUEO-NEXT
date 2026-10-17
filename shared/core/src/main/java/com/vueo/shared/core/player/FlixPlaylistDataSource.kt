package com.vueo.shared.core.player

import android.net.Uri
import android.util.Base64
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.LinkedHashMap

/** Only provider-marked FlixCloud playlists are decoded. No additional network requests. */
class FlixPlaylistDataSource private constructor(
    private val upstream: DataSource,
    private val session: Factory,
) : DataSource {
    private var active: DataSource? = null
    private var response: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource is already open" }
        active = upstream
        val key = session.key(dataSpec.uri)
        if (key == null || dataSpec.uri.path?.endsWith(".m3u8", ignoreCase = true) != true) {
            return upstream.open(dataSpec)
        }
        if (dataSpec.position != 0L || dataSpec.length != -1L || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET) {
            throw IOException("Partial Flix playlist requests are unsupported")
        }
        val request = dataSpec.buildUpon().setUri(dataSpec.uri.buildUpon().fragment(null).build()).build()
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
                if (body.size() + count > 2 * 1024 * 1024) throw IOException("Flix playlist exceeds size limit")
                body.write(buffer, 0, count)
            }
        } finally {
            upstream.close()
        }
        val raw = body.toByteArray()
        val plain = if (raw.size >= 7 && String(raw, 0, 7, Charsets.UTF_8) == "#EXTM3U") {
            FlixPlaylistCodec.filterJapanese(raw)
        } else {
            val encoded = String(raw, Charsets.US_ASCII).trim()
            if (encoded.isEmpty() || !encoded.matches(Regex("[A-Za-z0-9+/=\\r\\n]+"))) {
                throw IOException("Invalid Flix playlist encoding")
            }
            val cipher = try { Base64.decode(encoded, Base64.DEFAULT) } catch (error: IllegalArgumentException) {
                throw IOException("Invalid Flix playlist encoding", error)
            }
            FlixPlaylistCodec.decode(cipher, key)
        }
        session.registerChildren(resolved, String(plain, Charsets.UTF_8), key)
        val decoded = ByteArrayDataSource(plain)
        active = decoded
        return decoded.open(request.buildUpon().setUri(resolved).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(active).read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (active === upstream) upstream.responseHeaders else response
    override fun close() {
        try { active?.close() } finally { active = null; response = emptyMap() }
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        private val keys = LinkedHashMap<String, ByteArray>()
        private val uuid = Regex("(?:^|/)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:/|$)", RegexOption.IGNORE_CASE)

        @Synchronized
        internal fun key(uri: Uri): ByteArray? {
            val host = uri.host?.lowercase() ?: return null
            if (uri.scheme != "https" || !(host == "flixcloud.cc" || host.endsWith(".flixcloud.cc"))) return null
            val identity = uri.buildUpon().fragment(null).build().toString()
            val fragment = uri.fragment.orEmpty()
            if (fragment.startsWith("vueo_flix_key=")) {
                if (uuid.find(uri.path.orEmpty()) == null) throw IOException("Invalid Flix source identity")
                val hex = fragment.removePrefix("vueo_flix_key=")
                if (!hex.matches(Regex("[0-9a-f]{64}"))) throw IOException("Invalid Flix source key")
                keys[identity] = ByteArray(32) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                while (keys.size > 128) keys.remove(keys.keys.first())
            }
            return keys[identity]?.copyOf()
        }

        @Synchronized
        internal fun registerChildren(base: Uri, playlist: String, key: ByteArray) {
            val uriAttribute = Regex("URI=\"([^\"]+)\"")
            playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
                val reference = if (line.startsWith("#")) uriAttribute.find(line)?.groupValues?.get(1) else line
                if (reference == null) return@forEach
                val child = runCatching { Uri.parse(java.net.URI(base.toString()).resolve(reference).toString()) }.getOrNull() ?: return@forEach
                if (child.scheme != "https" || child.host != base.host || child.path?.endsWith(".m3u8", true) != true) return@forEach
                keys[child.buildUpon().fragment(null).build().toString()] = key.copyOf()
                while (keys.size > 128) keys.remove(keys.keys.first())
            }
        }

        override fun createDataSource(): DataSource = FlixPlaylistDataSource(upstreamFactory.createDataSource(), this)
    }
}
