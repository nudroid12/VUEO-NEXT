package com.vueo.shared.core.plugin

import java.net.URI

/** Optional media-session rules; legacy resolver requests retain their defaults. */
internal object WebViewSessionPolicy {
    private val mediaPath = Regex("\\.(mp4|m3u8)$")
    private val excludedPath = Regex("(?:^|[/_-])(?:init|segment|trailer|preview|sample)(?:[/_.-]|$)")
    private val headerControls = Regex("[\\r\\n\\u0000]")
    fun isMediaUrl(url: String, hosts: List<String>): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return@runCatching false
        if (uri.scheme !in listOf("https", "http") || uri.userInfo != null) return@runCatching false
        if (hosts.isEmpty() || host !in hosts) return@runCatching false
        val path = uri.path.orEmpty().lowercase()
        if (!mediaPath.containsMatchIn(path)) return@runCatching false
        if (excludedPath.containsMatchIn(path)) return@runCatching false
        true
    }.getOrDefault(false)

    fun withDefaults(headers: Map<String, String>, defaults: Map<String, String>): Map<String, String> =
        mergeHeaders(mergeHeaders(emptyMap(), headers, true), defaults, false)

    fun mergeHeaders(previous: Map<String, String>, incoming: Map<String, String>, preferIncoming: Boolean): Map<String, String> {
        val result = previous.toMutableMap()
        incoming.forEach { (key, value) ->
            if (value.isNotBlank() && !headerControls.containsMatchIn(key + value)) {
                val old = result.keys.firstOrNull { it.equals(key, ignoreCase = true) }
                if (old == null || preferIncoming) {
                    if (old != null) result.remove(old)
                    val canonical = when (key.lowercase()) {
                        "user-agent" -> "User-Agent"
                        "referer" -> "Referer"
                        "origin" -> "Origin"
                        "cookie" -> "Cookie"
                        "accept" -> "Accept"
                        else -> key
                    }
                    result[canonical] = value
                }
            }
        }
        return result
    }
}
