package com.vueo.shared.core.player

/** Provider declarations take precedence; labels are a fallback for older plugins. */
object PlaybackMediaPolicy {
    fun normalize(value: String?): String? = when (value?.substringBefore(';')?.trim()?.lowercase()) {
        "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> "application/x-mpegURL"
        "dash", "mpd", "application/dash+xml" -> "application/dash+xml"
        "mp4", "video/mp4" -> "video/mp4"
        "webm", "video/webm" -> "video/webm"
        else -> null
    }

    fun resolve(declared: String?, url: String?, name: String?, server: String?): String? {
        normalize(declared)?.let { return it }
        val path = url.orEmpty().substringBefore('?').substringBefore('#').lowercase()
        if (path.endsWith(".m3u8")) return "application/x-mpegURL"
        if (path.endsWith(".mpd")) return "application/dash+xml"
        if (path.endsWith(".mp4")) return "video/mp4"
        if (path.endsWith(".webm")) return "video/webm"
        val labels = listOfNotNull(name, server).joinToString(" ")
        return if (Regex("(?i)(?:^|[^a-z0-9])(?:hls|m3u8)(?:$|[^a-z0-9])").containsMatchIn(labels)) {
            "application/x-mpegURL"
        } else null
    }
}
