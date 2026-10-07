package com.vueo.mobile.core.extensions

import java.net.URI

/** Converts an Android Stremio deep link into the HTTPS manifest URL VUEO installs. */
fun normalizeStremioAddonManifestUrl(rawUrl: String?): String? {
    val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val candidate = when {
        raw.startsWith("stremio://", ignoreCase = true) -> {
            val payload = raw.substringAfter("://").trimStart('/')
            when {
                payload.startsWith("https://", ignoreCase = true) -> payload
                payload.startsWith("http://", ignoreCase = true) -> return null
                payload.isBlank() -> return null
                else -> "https://$payload"
            }
        }

        raw.startsWith("https://", ignoreCase = true) -> raw
        else -> return null
    }

    val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    if (parsed.host.isNullOrBlank()) return null

    return candidate
}
