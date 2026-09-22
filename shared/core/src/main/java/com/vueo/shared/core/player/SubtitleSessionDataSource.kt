package com.vueo.shared.core.player

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.LinkedHashMap

/**
 * Keeps a prepared external subtitle in memory for the active app session.
 * Media3 can then seek/reopen the text source without contacting the provider again.
 */
object SubtitleSessionCache {
    private const val MAX_ENTRIES = 16
    private val entries = object : LinkedHashMap<String, ByteArray>(MAX_ENTRIES, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(url: String, bytes: ByteArray) {
        entries[url] = bytes
    }

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
        val cached = SubtitleSessionCache.get(dataSpec.uri.toString())
        active = if (cached != null) {
            ByteArrayDataSource(cached).also { dataSource ->
                listeners.forEach(dataSource::addTransferListener)
            }
        } else {
            upstream
        }
        return requireNotNull(active).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)

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
        override fun createDataSource(): DataSource =
            SubtitleSessionDataSource(upstreamFactory.createDataSource())
    }
}
