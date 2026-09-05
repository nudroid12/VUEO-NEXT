package com.vueo.shared.core.stremio

import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SubtitleCandidate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class StremioAddonClient(
    val manifest: StremioManifest,
    private val httpClient: StremioHttpClient = DefaultStremioHttpClient,
) {
    suspend fun streams(
        type: String,
        videoId: String,
    ): List<SourceCandidate> {
        if (!manifest.supportsResource("stream") || !manifest.supportsType(type)) {
            return emptyList()
        }

        val url = "${manifest.baseUrl}/stream/" +
            "${encode(type)}/${encode(videoId)}.json"
        return StremioStreamParser.parseSources(
            payload = httpClient.get(url),
            manifest = manifest,
        )
    }

    suspend fun subtitles(
        type: String,
        videoId: String,
        extras: Map<String, String> = emptyMap(),
    ): List<SubtitleCandidate> {
        if (!manifest.supportsResource("subtitles") || !manifest.supportsType(type)) {
            return emptyList()
        }

        val suffix = encodeExtras(extras)
        val url = "${manifest.baseUrl}/subtitles/" +
            "${encode(type)}/${encode(videoId)}$suffix.json"
        return StremioStreamParser.parseSubtitles(
            payload = httpClient.get(url),
            manifest = manifest,
        )
    }

    companion object {
        suspend fun fromManifestUrl(
            manifestUrl: String,
            httpClient: StremioHttpClient = DefaultStremioHttpClient,
        ): StremioAddonClient {
            require(manifestUrl.startsWith("https://", ignoreCase = true)) {
                "VUEO requires HTTPS addon manifest URLs."
            }
            val payload = httpClient.get(manifestUrl)
            return StremioAddonClient(
                manifest = StremioManifestParser.parse(
                    manifestUrl = manifestUrl,
                    payload = payload,
                ),
                httpClient = httpClient,
            )
        }
    }
}

private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private fun encodeExtras(extras: Map<String, String>): String {
    if (extras.isEmpty()) return ""
    val encoded = extras.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }
    return "/$encoded"
}
