package com.vueo.shared.core.stremio

import java.net.URI
import java.net.URLEncoder

/** Shared addon URL rules. Keep configured paths and query values byte-for-byte. */
object StremioUrls {
    fun isHttpUrl(value: String?): Boolean = value != null && runCatching {
        val uri = URI(value)
        uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun normalizeManifest(raw: String): String {
        var url = raw.trim()
        require(url.isNotEmpty()) { "Enter an addon URL." }
        if (url.startsWith("stremio://", true)) url = url.substringAfter("://")
        if (!url.contains("://")) url = "https://$url"
        url = url.substringBefore('#')
        require(isHttpUrl(url)) { "Enter a valid HTTP or HTTPS addon URL." }
        val uri = URI(url)
        val path = url.substringBefore('?').trimEnd('/')
        val query = url.substringAfter('?', "").let { if (url.contains('?')) "?$it" else "" }
        val manifestPath = if (path.endsWith("/manifest.json", true)) path else "$path/manifest.json"
        return uri.scheme.lowercase() + manifestPath.substring(uri.scheme.length) + query
    }

    fun resource(manifestUrl: String, resource: String, type: String, id: String, extras: Map<String, String> = emptyMap()): String {
        val normalized = normalizeManifest(manifestUrl)
        val path = normalized.substringBefore('?').dropLast("/manifest.json".length)
        val query = normalized.substringAfter('?', "").let { if (normalized.contains('?')) "?$it" else "" }
        val extra = if (extras.isEmpty()) "" else extras.entries.joinToString("&", prefix = "/") { "${encode(it.key)}=${encode(it.value)}" }
        return "$path/$resource/${encode(type)}/${encode(id)}$extra.json$query"
    }

    fun configuration(manifestUrl: String): String {
        val normalized = normalizeManifest(manifestUrl)
        return normalized.substringBefore('?').dropLast("/manifest.json".length) + "/configure" +
            normalized.substringAfter('?', "").let { if (normalized.contains('?')) "?$it" else "" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    fun canonicalType(type: String): String = if (type.equals("tv", true)) "series" else type.lowercase()
}
