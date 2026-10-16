package com.vueo.shared.core.stremio

import java.io.IOException
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.GzipSink
import okio.GzipSource
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class BoundedStremioResponseTest {
    private class StreamingBody(
        private val total: Long,
        private val declared: Long = -1,
        private val failAfter: Long? = null,
    ) : ResponseBody() {
        var readBytes = 0L
        var closed = false
        private val input = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (failAfter != null && readBytes >= failAfter) throw IOException("read failed")
                if (readBytes == total) return -1
                val count = minOf(byteCount, total - readBytes)
                sink.writeUtf8("x".repeat(count.toInt()))
                readBytes += count
                return count
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = declared
        override fun source(): BufferedSource = input
    }

    @Test fun unknownLengthWithinLimit() {
        val body = StreamingBody(127)
        assertEquals("x".repeat(127), body.readBoundedString(128))
        assertTrue(body.closed)
    }
    @Test fun exactEightMiBBoundary() {
        val limit = 8L * 1024 * 1024
        val body = StreamingBody(limit)
        assertEquals(limit.toInt(), body.readBoundedString(limit).length)
        assertTrue(body.closed)
    }
    @Test fun unknownLengthOverLimitStopsReading() {
        val body = StreamingBody(Long.MAX_VALUE)
        assertThrows(IOException::class.java) { body.readBoundedString(128) }
        // Okio can prefetch one segment; the transport never drains the body.
        assertTrue(body.readBytes <= 128 + 8192)
        assertTrue(body.closed)
    }
    @Test fun decompressedBytesAreBounded() {
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { it.writeUtf8("x".repeat(10000)) }
        assertTrue(compressed.size < 128)
        val body = GzipSource(compressed).buffer().asResponseBody(contentLength = -1)
        assertThrows(IOException::class.java) { body.readBoundedString(128) }
    }
    @Test fun dishonestSmallContentLengthStillBounded() {
        val body = StreamingBody(1000, declared = 10)
        assertThrows(IOException::class.java) { body.readBoundedString(128) }
        assertTrue(body.closed)
    }
    @Test fun declaredOversizeRejectedWithoutRead() {
        val body = StreamingBody(1000, declared = 1000)
        assertThrows(IOException::class.java) { body.readBoundedString(128) }
        assertEquals(0L, body.readBytes)
        assertTrue(body.closed)
    }
    @Test fun emptyBodyAndZeroLimit() {
        assertEquals("", StreamingBody(0).readBoundedString(0))
        assertThrows(IOException::class.java) { StreamingBody(1).readBoundedString(0) }
    }
    @Test fun ioFailureClosesSourceAndPropagates() {
        val body = StreamingBody(20000, failAfter = 1)
        assertThrows(IOException::class.java) { body.readBoundedString(30000) }
        assertTrue(body.closed)
    }
    @Test fun cancelledBeforeRead() {
        val body = StreamingBody(100)
        assertThrows(CancellationException::class.java) { body.readBoundedString(128) { false } }
        assertEquals(0L, body.readBytes)
        assertTrue(body.closed)
    }
    @Test fun cancelledDuringRead() {
        val body = StreamingBody(100000)
        assertThrows(CancellationException::class.java) { body.readBoundedString(200000) { body.readBytes == 0L } }
        assertTrue(body.readBytes <= 8192)
        assertTrue(body.closed)
    }
    @Test fun preservesCharsetAndBomDecoding() {
        val cases = listOf(
            Triple(byteArrayOf(), "UTF-8", "text/plain; charset=utf-8"),
            Triple(byteArrayOf(), "ISO-8859-1", "text/plain; charset=iso-8859-1"),
            Triple(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), "UTF-8", "text/plain; charset=utf-8"),
            Triple(byteArrayOf(0xff.toByte(), 0xfe.toByte()), "UTF-16LE", "text/plain; charset=utf-8"),
            Triple(byteArrayOf(0xfe.toByte(), 0xff.toByte()), "UTF-16BE", "text/plain; charset=utf-8"),
            Triple(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, 0), "UTF-32LE", "text/plain; charset=utf-8"),
            Triple(byteArrayOf(0, 0, 0xfe.toByte(), 0xff.toByte()), "UTF-32BE", "text/plain; charset=utf-8"),
        )
        for ((bom, encoding, header) in cases) {
            val bytes = bom + "café".toByteArray(Charset.forName(encoding))
            val contentType = header.toMediaType()
            val expected = bytes.toResponseBody(contentType).string()
            assertEquals(encoding, expected, bytes.toResponseBody(contentType).readBoundedString(128))
        }
    }
    @Test fun limitCountsUtf8BytesRatherThanCharacters() {
        val bytes = "éé".toByteArray(Charsets.UTF_8)
        assertThrows(IOException::class.java) { bytes.toResponseBody().readBoundedString(3) }
        assertEquals("éé", bytes.toResponseBody().readBoundedString(4))
    }
}
