package com.vueo.shared.core.stremio

import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SubtitleCandidate
import org.json.JSONArray
import org.json.JSONObject

object StremioStreamParser {
    fun parseSources(
        payload: String,
        manifest: StremioManifest,
    ): List<SourceCandidate> {
        val streams = JSONObject(payload).optJSONArray("streams") ?: JSONArray()
        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val streamUrl = item.optString("url")
                .trim()
                .takeIf(String::isNotBlank)
            val infoHash = item.optString("infoHash")
                .trim()
                .takeIf(String::isNotBlank)

            if (streamUrl == null && infoHash == null) {
                return@mapNotNull null
            }

            val title = item.optString(
                "title",
                item.optString("name", manifest.name),
            ).trim().ifBlank { manifest.name }
            val behaviorHints = item.optJSONObject("behaviorHints")
            val requestHeaders = behaviorHints
                ?.optJSONObject("proxyHeaders")
                ?.optJSONObject("request")
                .toStringMap()
            val videoSize = behaviorHints
                ?.optLong("videoSize", -1L)
                ?.takeIf { it > 0L }

            SourceCandidate(
                id = "${manifest.id}:stream:$index",
                name = title,
                url = streamUrl,
                infoHash = infoHash,
                fileIndex = item.optInt("fileIdx", -1).takeIf { it >= 0 },
                quality = inferQuality(title),
                codec = inferCodec(title),
                hdr = inferHdr(title),
                sizeBytes = videoSize,
                headers = requestHeaders,
                providerId = manifest.id,
                providerName = manifest.name,
            )
        }
    }

    fun parseSubtitles(
        payload: String,
        manifest: StremioManifest,
    ): List<SubtitleCandidate> {
        val subtitles = JSONObject(payload).optJSONArray("subtitles") ?: JSONArray()
        return (0 until subtitles.length()).mapNotNull { index ->
            val item = subtitles.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url")
                .trim()
                .takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val language = item.optString("lang", "und")
                .trim()
                .ifBlank { "und" }
            val sourceId = item.optString("id", language)
                .trim()
                .ifBlank { language }

            SubtitleCandidate(
                id = "${manifest.id}:subtitle:$index:$sourceId",
                language = language,
                url = url,
                providerId = manifest.id,
                providerName = manifest.name,
                name = item.optString("name")
                    .trim()
                    .takeIf(String::isNotBlank),
            )
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { key ->
                optString(key)
                    .takeIf(String::isNotBlank)
                    ?.let { put(key, it) }
            }
        }
    }

    private fun inferQuality(text: String): String? {
        val value = text.lowercase()
        return when {
            "2160" in value || "4k" in value || "uhd" in value -> "4K"
            "1080" in value -> "1080p"
            "720" in value -> "720p"
            "576" in value -> "576p"
            "480" in value -> "480p"
            else -> null
        }
    }

    private fun inferCodec(text: String): String? {
        val value = text.lowercase()
        return when {
            "av1" in value -> "AV1"
            "hevc" in value || "h265" in value || "x265" in value -> "HEVC"
            "h264" in value || "x264" in value || "avc" in value -> "H.264"
            else -> null
        }
    }

    private fun inferHdr(text: String): String? {
        val value = text.lowercase()
        return when {
            "dolby vision" in value || " dovi" in value || " dv " in value ->
                "Dolby Vision"
            "hdr10+" in value -> "HDR10+"
            "hdr" in value -> "HDR"
            else -> null
        }
    }
}
