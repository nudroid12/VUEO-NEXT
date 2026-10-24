package com.vueo.shared.core.stremio

import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SubtitleCandidate

class StremioAddonClient(
    val manifest: StremioManifest,
    private val httpClient: StremioHttpClient = DefaultStremioHttpClient,
) {
    suspend fun streams(
        type: String,
        videoId: String,
    ): List<SourceCandidate> {
        if (!manifest.supportsRequest("stream", type, videoId)) {
            return emptyList()
        }

        val url = StremioUrls.resource(manifest.manifestUrl, "stream", StremioUrls.canonicalType(type), videoId)
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
        if (!manifest.supportsRequest("subtitles", type, videoId)) {
            return emptyList()
        }

        val url = StremioUrls.resource(manifest.manifestUrl, "subtitles", StremioUrls.canonicalType(type), videoId, extras)
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
            val normalizedUrl = StremioUrls.normalizeManifest(manifestUrl)
            val payload = httpClient.get(normalizedUrl)
            return StremioAddonClient(
                manifest = StremioManifestParser.parse(
                    manifestUrl = normalizedUrl,
                    payload = payload,
                ),
                httpClient = httpClient,
            )
        }
    }
}
