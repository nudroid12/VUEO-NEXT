package com.vueo.shared.core.player

/** Pure selection policy; decoder capabilities come from Media3's current track snapshot. */
internal data class VideoFallbackFormat(
    val mime: String?, val codecs: String?, val width: Int, val height: Int, val frameRate: Float,
) {
    val configuration: String get() = "$mime:$codecs:$width:$height:$frameRate"
}

internal class VideoFallbackPolicy {
    private val failed = mutableSetOf<String>()
    var attempts: Int = 0
        private set

    /** Returns an index into supported candidates, or null when exhausted. */
    fun next(failedFormat: VideoFallbackFormat, supported: List<VideoFallbackFormat>): Int? {
        failed += failedFormat.configuration
        if (attempts >= 3) return null
        val next = supported.indices.filter { index ->
            val format = supported[index]
            val differentCodec = format.mime != failedFormat.mime || format.codecs != failedFormat.codecs
            val smaller = format.width > 0 && format.height > 0 &&
                format.width <= failedFormat.width && format.height <= failedFormat.height &&
                (format.width < failedFormat.width || format.height < failedFormat.height)
            format.configuration !in failed && (differentCodec || smaller)
        }.sortedWith(
            compareBy<Int> { if (supported[it].mime == "video/avc") 0 else 1 }
                .thenBy { if (supported[it].width in 1..1920 && supported[it].height in 1..1080) 0 else 1 }
                .thenByDescending { supported[it].width.toLong() * supported[it].height }
        ).firstOrNull()
        if (next != null) attempts++
        return next
    }
}
