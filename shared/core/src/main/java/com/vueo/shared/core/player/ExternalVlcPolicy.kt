package com.vueo.shared.core.player

import java.net.URI
import java.util.Locale

enum class SourcePlaybackTarget(val label: String) {
    DEFAULT("Default player"), SOFTWARE_TEST("Software test"), VLC("Open in VLC");
    fun next(): SourcePlaybackTarget = entries[(ordinal + 1) % entries.size]
}

object ExternalVlcPolicy {
    fun validateUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "VLC testing requires a direct HTTPS video source."
        }
    }

    /** A tiny header-free range request verifies that VLC can fetch a direct file. */
    fun acceptsProbe(status: Int, contentType: String, prefix: ByteArray): Boolean {
        if (status != 200 && status != 206 || prefix.isEmpty()) return false
        val text = prefix.toString(Charsets.ISO_8859_1).trimStart().lowercase(Locale.ROOT)
        if (text.startsWith("<") || text.startsWith("#extm3u") || text.startsWith("{") || text.startsWith("[")) return false
        val mime = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime.startsWith("video/") && !mime.contains("mpegurl")) return true
        // Many storage hosts use octet-stream. Require a real container signature.
        val ebml = prefix.size >= 4 && prefix.take(4).map { it.toInt() and 255 } == listOf(0x1a, 0x45, 0xdf, 0xa3)
        val mp4 = prefix.size >= 8 && String(prefix, 4, 4, Charsets.US_ASCII) == "ftyp"
        return (mime.isEmpty() || mime in setOf("application/octet-stream", "binary/octet-stream", "application/mp4", "application/x-matroska")) && (ebml || mp4)
    }

    fun returnPosition(expectedUrl: String, returnedUrl: String?, value: Number?): Long? =
        value?.toLong()?.takeIf { returnedUrl == expectedUrl && it >= 0L }
}
