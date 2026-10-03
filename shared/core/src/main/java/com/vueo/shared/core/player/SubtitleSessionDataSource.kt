package com.vueo.shared.core.player

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.LinkedHashMap
import java.io.ByteArrayOutputStream

/**
 * Keeps a prepared external subtitle in memory for the active app session.
 * Media3 can then seek/reopen the text source without contacting the provider again.
 */
object SubtitleSessionCache {
    private const val MAX_ENTRIES = 16
    private const val MAX_TOTAL_BYTES = 8 * 1024 * 1024
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private val entries = LinkedHashMap<String, ByteArray>(MAX_ENTRIES, .75f, true)
    private val registered = LinkedHashMap<String, Boolean>()

    @Synchronized
    fun register(url: String) {
        registered[url] = true
        while (registered.size > 256) registered.remove(registered.keys.first())
    }

    @Synchronized
    fun isRegistered(url: String): Boolean = registered.containsKey(url)

    @Synchronized
    fun put(url: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        entries[url] = bytes
        while (entries.size > MAX_ENTRIES || entries.values.sumOf { it.size } > MAX_TOTAL_BYTES) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun get(url: String): ByteArray? = entries[url]

}

class SubtitleSessionDataSource private constructor(
    private val upstream: DataSource,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null
    private var capture: ByteArrayOutputStream? = null
    private var captureUrl: String? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource is already open" }
        val cached = SubtitleSessionCache.get(dataSpec.uri.toString())
        active = if (cached != null) {
            ByteArrayDataSource(cached).also { dataSource ->
                listeners.forEach(dataSource::addTransferListener)
            }
        } else {
            upstream
        }
        // Capture only registered subtitle URLs, never video or partial/range loads.
        captureUrl = dataSpec.uri.toString().takeIf {
            cached == null && SubtitleSessionCache.isRegistered(it) && dataSpec.position == 0L && dataSpec.length == -1L
        }
        capture = captureUrl?.let { ByteArrayOutputStream() }
        return requireNotNull(active).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = requireNotNull(active).read(buffer, offset, length)
        val output = capture
        if (output != null) {
            if (count > 0) {
                if (output.size() + count <= 2 * 1024 * 1024) output.write(buffer, offset, count)
                else { capture = null; captureUrl = null }
            } else if (count == -1) {
                captureUrl?.let { SubtitleSessionCache.put(it, output.toByteArray()) }
                capture = null
                captureUrl = null
            }
        }
        return count
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
            capture = null
            captureUrl = null
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SubtitleSessionDataSource(upstreamFactory.createDataSource())
    }
}
