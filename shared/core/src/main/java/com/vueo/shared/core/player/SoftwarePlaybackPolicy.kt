package com.vueo.shared.core.player

import java.net.URI
import java.util.Locale

/** Deliberately narrow test transport: never silently discard required headers. */
object SoftwarePlaybackPolicy {
    fun mediaOptions(url: String, headers: Map<String, String>, positionMs: Long): List<String> {
        val uri = URI(url)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) {
            "Software test requires a direct HTTPS source."
        }
        val options = mutableListOf(":network-caching=1500")
        val seen = mutableSetOf<String>()
        headers.forEach { (name, value) ->
            val key = name.lowercase(Locale.ROOT)
            require(seen.add(key) && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Invalid source headers."
            }
            options += when (key) {
                "user-agent" -> ":http-user-agent=$value"
                "referer", "referrer" -> ":http-referrer=$value"
                else -> throw IllegalArgumentException("This source needs headers unsupported by the software test.")
            }
        }
        if (positionMs > 0) options += ":start-time=" + String.format(Locale.US, "%.3f", positionMs / 1000.0)
        return options
    }

    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long =
        (positionMs.coerceAtLeast(0L) + deltaMs.coerceIn(-60_000L, 60_000L))
            .coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
}
