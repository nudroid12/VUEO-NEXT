package com.vueo.shared.core.player

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class SubtitleFormat {
    SUBRIP,
    WEBVTT,
    SSA,
    TTML,
}

/** Resolves subtitle formats consistently for Mobile and TV. */
object SubtitleFormatPolicy {
    fun detect(
        url: String,
        declaredMimeType: String? = null,
    ): SubtitleFormat {
        val declared = declaredMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)

        formatFromHint(declared)?.let { return it }

        val decodedUrl = runCatching {
            URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        }.getOrDefault(url).lowercase(Locale.ROOT)
        val path = decodedUrl.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "")
        formatFromHint(extension)?.let { return it }

        val query = decodedUrl.substringAfter('?', "").substringBefore('#')
        query.split('&').forEach { parameter ->
            val key = parameter.substringBefore('=').trim()
            val value = parameter.substringAfter('=', "").trim()
            if (key in FORMAT_QUERY_KEYS) {
                formatFromHint(value)?.let { return it }
            }
        }

        return SubtitleFormat.SUBRIP
    }

    private fun formatFromHint(value: String?): SubtitleFormat? {
        val hint = value?.trim()?.lowercase(Locale.ROOT)?.removePrefix(".")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when {
            hint == "vtt" || "text/vtt" in hint || "webvtt" in hint -> SubtitleFormat.WEBVTT
            hint == "ass" || hint == "ssa" || "text/x-ssa" in hint || "text/x-ass" in hint -> SubtitleFormat.SSA
            hint == "ttml" || hint == "xml" || "ttml" in hint -> SubtitleFormat.TTML
            hint == "srt" || "subrip" in hint || "application/x-subrip" in hint -> SubtitleFormat.SUBRIP
            else -> null
        }
    }

    private val FORMAT_QUERY_KEYS = setOf(
        "format",
        "type",
        "ext",
        "extension",
        "mime",
        "mimetype",
        "contenttype",
    )
}
