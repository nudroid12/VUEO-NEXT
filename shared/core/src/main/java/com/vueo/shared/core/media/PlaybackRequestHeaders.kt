package com.vueo.shared.core.media

/**
 * Shared playback-header handling for provider/addon streams.
 *
 * Header names are case-insensitive. We keep end-to-end request context such
 * as Referer, Origin, Cookie, Authorization and provider-specific X-* headers,
 * while removing transport-level headers that Media3/HTTP should control.
 */
object PlaybackRequestHeaders {
    private val blockedNames = setOf(
        "host",
        "connection",
        "proxy-connection",
        "keep-alive",
        "transfer-encoding",
        "te",
        "trailer",
        "upgrade",
        "accept-encoding",
        "content-length",
        "range",
        "if-range",
    )

    fun sanitize(
        headers: Map<String, String>,
    ): Map<String, String> {
        if (headers.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        headers.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            val value = rawValue.trim()
            if (
                name.isBlank() ||
                value.isBlank() ||
                name.lowercase() in blockedNames
            ) {
                return@forEach
            }
            putCaseInsensitive(
                target = result,
                name = name,
                value = value,
            )
        }
        return result
    }

    /**
     * Merge two header contexts case-insensitively. Overlay values win.
     */
    fun merge(
        base: Map<String, String>,
        overlay: Map<String, String>,
    ): Map<String, String> {
        if (base.isEmpty()) return sanitize(overlay)
        if (overlay.isEmpty()) return sanitize(base)

        val result = sanitize(base).toMutableMap()
        sanitize(overlay).forEach { (name, value) ->
            putCaseInsensitive(
                target = result,
                name = name,
                value = value,
            )
        }
        return result
    }

    fun withDefaults(
        headers: Map<String, String>,
        userAgent: String? = null,
        referer: String? = null,
        accept: String? = null,
    ): Map<String, String> {
        val result = sanitize(headers).toMutableMap()
        putIfMissing(result, "User-Agent", userAgent)
        putIfMissing(result, "Referer", referer)
        putIfMissing(result, "Accept", accept)
        return result
    }

    fun value(
        headers: Map<String, String>,
        name: String,
    ): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }

    fun without(
        headers: Map<String, String>,
        name: String,
    ): Map<String, String> = sanitize(headers)
        .filterKeys { !it.equals(name, ignoreCase = true) }

    private fun putIfMissing(
        target: MutableMap<String, String>,
        name: String,
        rawValue: String?,
    ) {
        val value = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (value(target, name) != null) return
        target[name] = value
    }

    private fun putCaseInsensitive(
        target: MutableMap<String, String>,
        name: String,
        value: String,
    ) {
        val previous = target.keys.firstOrNull {
            it.equals(name, ignoreCase = true)
        }
        if (previous != null && previous != name) {
            target.remove(previous)
        }
        target[name] = value
    }
}
