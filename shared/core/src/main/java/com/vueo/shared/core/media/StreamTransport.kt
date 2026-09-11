package com.vueo.shared.core.media

import java.net.URI

enum class StreamTransport {
    HLS,
    DASH,
    FILE,
    DIRECT,
    EMBED,
    UNKNOWN,
}

/**
 * Lightweight, network-free classification for source URLs.
 *
 * UNKNOWN intentionally remains playback-eligible for backwards compatibility
 * with signed CDN URLs that do not expose a file extension. Explicit/obvious
 * embed pages are the only HTTP(S) URLs rejected from direct playback here.
 */
object StreamTransportPolicy {
    fun classify(
        url: String?,
        streamType: String?,
        mimeType: String?,
    ): StreamTransport {
        val normalizedType = streamType
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (
            normalizedType in EMBED_TYPES ||
            normalizedMime == "text/html" ||
            normalizedMime == "application/xhtml+xml"
        ) {
            return StreamTransport.EMBED
        }

        transportFromHint(normalizedType, normalizedMime)?.let { return it }

        val rawUrl = url?.trim().orEmpty()
        if (rawUrl.isBlank()) return StreamTransport.UNKNOWN

        val lowerUrl = rawUrl.lowercase()
        val uri = runCatching { URI(rawUrl) }.getOrNull()
        val path = uri?.path?.lowercase().orEmpty()
        val query = uri?.query?.lowercase().orEmpty()

        transportFromUrl(path, query, lowerUrl)?.let { return it }

        if (looksLikeEmbedPage(path)) {
            return StreamTransport.EMBED
        }

        if (
            normalizedType.contains("direct") ||
            normalizedType == "stream" ||
            normalizedType == "video"
        ) {
            return StreamTransport.DIRECT
        }

        return StreamTransport.UNKNOWN
    }

    fun isDirectPlayable(
        url: String?,
        streamType: String?,
        mimeType: String?,
    ): Boolean {
        val rawUrl = url?.trim().orEmpty()
        if (!rawUrl.startsWith("https://", ignoreCase = true)) return false
        return classify(
            url = rawUrl,
            streamType = streamType,
            mimeType = mimeType,
        ) != StreamTransport.EMBED
    }

    fun playbackMimeType(
        url: String?,
        streamType: String?,
        mimeType: String?,
    ): String? {
        val explicit = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { '/' in it }
        val normalizedExplicit = explicit?.lowercase().orEmpty()
        when {
            "mpegurl" in normalizedExplicit ->
                return "application/x-mpegURL"

            normalizedExplicit == "application/dash+xml" ->
                return "application/dash+xml"

            normalizedExplicit.startsWith("video/") ||
                normalizedExplicit == "application/mp4" ->
                return explicit
        }

        return when (
            classify(
                url = url,
                streamType = streamType,
                mimeType = mimeType,
            )
        ) {
            StreamTransport.HLS -> "application/x-mpegURL"
            StreamTransport.DASH -> "application/dash+xml"
            StreamTransport.FILE -> mimeFromFileExtension(url, streamType)
            else -> null
        }
    }

    private fun transportFromHint(
        type: String,
        mime: String,
    ): StreamTransport? = when {
        type in HLS_TYPES ||
            "mpegurl" in mime ->
            StreamTransport.HLS

        type in DASH_TYPES ||
            mime == "application/dash+xml" ->
            StreamTransport.DASH

        type in FILE_TYPES ||
            mime.startsWith("video/") ->
            StreamTransport.FILE

        else -> null
    }

    private fun transportFromUrl(
        path: String,
        query: String,
        lowerUrl: String,
    ): StreamTransport? {
        val cleanPath = path.substringBefore('#')
        return when {
            cleanPath.endsWith(".m3u8") ||
                "format=m3u8" in query ||
                "type=m3u8" in query ||
                "type=hls" in query ->
                StreamTransport.HLS

            cleanPath.endsWith(".mpd") ||
                "format=mpd" in query ||
                "type=mpd" in query ||
                "type=dash" in query ->
                StreamTransport.DASH

            FILE_EXTENSIONS.any(cleanPath::endsWith) ||
                FILE_QUERY_HINTS.any(lowerUrl::contains) ->
                StreamTransport.FILE

            else -> null
        }
    }

    private fun looksLikeEmbedPage(path: String): Boolean {
        if (
            path.endsWith(".html") ||
            path.endsWith(".htm") ||
            path.endsWith(".php")
        ) {
            return true
        }

        return EMBED_PATH_REGEX.containsMatchIn(path)
    }

    private fun mimeFromFileExtension(
        url: String?,
        streamType: String?,
    ): String? {
        val hint = streamType?.trim()?.lowercase().orEmpty()
        val path = runCatching { URI(url.orEmpty()).path.lowercase() }
            .getOrDefault("")

        return when {
            hint == "mp4" || path.endsWith(".mp4") -> "video/mp4"
            hint == "m4v" || path.endsWith(".m4v") -> "video/x-m4v"
            hint == "webm" || path.endsWith(".webm") -> "video/webm"
            hint == "mkv" || path.endsWith(".mkv") -> "video/x-matroska"
            hint == "mov" || path.endsWith(".mov") -> "video/quicktime"
            else -> null
        }
    }

    private val EMBED_TYPES = setOf(
        "embed",
        "embedded",
        "iframe",
        "player",
        "page",
        "html",
    )

    private val HLS_TYPES = setOf(
        "hls",
        "m3u8",
        "application/x-mpegurl",
        "application/vnd.apple.mpegurl",
    )

    private val DASH_TYPES = setOf(
        "dash",
        "mpd",
        "application/dash+xml",
    )

    private val FILE_TYPES = setOf(
        "mp4",
        "m4v",
        "webm",
        "mkv",
        "mov",
        "file",
    )

    private val FILE_EXTENSIONS = listOf(
        ".mp4",
        ".m4v",
        ".webm",
        ".mkv",
        ".mov",
    )

    private val FILE_QUERY_HINTS = listOf(
        "format=mp4",
        "type=mp4",
        "format=webm",
        "type=webm",
        "format=mkv",
        "type=mkv",
    )

    private val EMBED_PATH_REGEX = Regex(
        pattern =
            "/(?:" +
                "embed(?:[-_][^/]+)?|" +
                "iframe(?:[-_][^/]+)?|" +
                "player(?:[-_][^/]+)?|" +
                "watch|" +
                "e" +
                ")(?:/|$)",
        option = RegexOption.IGNORE_CASE,
    )
}
