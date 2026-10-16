package com.vueo.shared.core.stremio

import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

/** Bounds bytes exposed by OkHttp, including transparently decompressed bodies. */
internal fun ResponseBody.readBoundedString(
    maxBytes: Long,
    isActive: () -> Boolean = { true },
): String {
    require(maxBytes in 0 until Long.MAX_VALUE)
    val declaredLength = contentLength()
    if (declaredLength > maxBytes) {
        close()
        throw IOException("Response too large ($declaredLength bytes; limit $maxBytes)")
    }
    val buffer = Buffer()
    source().use { source ->
        while (true) {
            if (!isActive()) throw CancellationException("Addon response read cancelled")
            // Read at most one byte past the limit into our buffer. Never turn
            // an oversized/partial JSON response into a successful truncated one.
            val count = source.read(buffer, minOf(8_192L, maxBytes - buffer.size + 1L))
            if (count == -1L) break
            if (buffer.size > maxBytes) throw IOException("Response exceeds $maxBytes byte limit")
        }
    }
    if (!isActive()) throw CancellationException("Addon response read cancelled")
    // Use OkHttp's normal BOM-aware string decoding without copying the entire
    // bounded body into a second byte array. This closes the temporary buffer.
    return buffer.asResponseBody(contentType(), buffer.size).string()
}
