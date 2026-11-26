package com.vueo.shared.core.source

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceAssessment
import com.vueo.shared.core.player.PlayerSourceAudioMatch

/** Formats link details for display without changing source selection or ranking. */
object ProviderSourceDetails {
    private enum class Field(val pattern: Regex) {
        QUALITY(Regex("""(?i)\b(?:[1-9]\d{2,3}[pi]|4k|8k|uhd|full[ -]?hd|fhd|hd|sd|auto)\b""")),
        FORMAT(Regex("""(?i)\b(?:hls|m3u8|mp4|m4v|mkv|webm|dash|mpd|mpeg[ -]?ts)\b""")),
        CODEC(Regex("""(?i)\b(?:[hx]\.?26[45]|avc|hevc|av1|vp9)\b""")),
        HDR(Regex("""(?i)\b(?:hdr(?:10\+?)?|dolby vision|hlg|sdr)\b""")),
        SIZE(Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:[kmgt]i?b)\b""")),
        AUDIO(Regex("""(?i)\b(?:audio|dual|dub(?:bed)?|aac|eac3|ac3|flac|dts|opus|atmos)\b""")),
        SUBTITLE(Regex("""(?i)\b(?:soft[ -]?subs?|hard[ -]?subs?|subs?|subtitles?|subbed|srt|ass|vtt|raw)\b""")),
    }

    fun lines(
        source: StreamSource,
        assessment: PlayerSourceAssessment,
        includeServer: Boolean = true,
    ): List<String> {
        val details = visibleLines(source, source.name).distinctBy(String::lowercase)
        val standaloneServer = serverLines(source)
        val result = (if (includeServer) standaloneServer + details else details).toMutableList()
        val knownFields = (standaloneServer + details).flatMap(::fields).toMutableSet()
        val assessedTags = assessment.summary.split('•').map(String::trim)
        val inferredAudio = when (assessment.audioMatch) {
            PlayerSourceAudioMatch.ORIGINAL -> "Original audio"
            PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL -> "Original in multi audio"
            PlayerSourceAudioMatch.FOREIGN_DUB -> "Dub"
            PlayerSourceAudioMatch.UNKNOWN -> null
        }

        val technical = setOf(Field.QUALITY, Field.FORMAT, Field.CODEC, Field.HDR, Field.SIZE)
        val technicalRow = result.indexOfLast { line ->
            line.split('•', '|', '·').any { tag -> fields(tag).any { it in technical } }
        }.takeIf { it >= 0 }
        val fallbacks = listOf(
            Field.QUALITY to (knownValue(source.quality) ?: assessedTags.firstOrNull {
                !it.equals("Auto", true) && Field.QUALITY in fields(it)
            }),
            Field.FORMAT to transport(source),
            Field.CODEC to (knownValue(source.codec) ?: assessedTags.firstOrNull { Field.CODEC in fields(it) }),
            Field.HDR to source.hdr,
            Field.SIZE to source.sizeBytes?.takeIf { it > 0 }?.let { bytes ->
                val mib = bytes / (1024.0 * 1024.0)
                if (mib >= 1024) String.format(java.util.Locale.ROOT, "%.1f GB", mib / 1024)
                else String.format(java.util.Locale.ROOT, "%.0f MB", mib)
            },
            Field.SUBTITLE to source.subtitles.takeIf { it.isNotEmpty() }?.let { "Softsub" },
            Field.AUDIO to (knownValue(source.audio) ?: inferredAudio),
        )

        // Keep a missing field beside related provider fields when their row can be identified.
        // If the layout has no such row, add a separate line after the provider text.
        var appendedTechnicalRow: Int? = null
        for ((field, raw) in fallbacks) {
            val value = knownValue(raw) ?: continue
            if (field in knownFields) continue
            val target = if (field in technical) {
                technicalRow ?: appendedTechnicalRow
            } else null
            if (target != null) {
                val line = result[target]
                val separator = listOf(" • ", " | ", " · ").firstOrNull { it in line } ?: " • "
                result[target] = "$line$separator$value"
            } else {
                if (field in technical) appendedTechnicalRow = result.size
                result.add(value)
            }
            knownFields.add(field)
        }
        return result
    }

    fun serverLines(source: StreamSource): List<String> {
        val detailTags = visibleLines(source, source.name).flatMap { line ->
            line.split('•', '|', '·').map { it.trim().lowercase() }
        }.toSet()
        return visibleLines(source, source.serverName)
            .distinctBy(String::lowercase)
            .filterNot { it.lowercase() in detailTags }
    }

    private fun visibleLines(source: StreamSource, value: String?): List<String> {
        val provider = source.providerName.substringAfterLast(" / ").trim()
        return value?.let(SourceDisplayText::lines).orEmpty().filterNot {
            it.equals(provider, true) || it.equals(source.providerName.trim(), true) ||
                it.startsWith("https://", true) || it.startsWith("http://", true)
        }
    }

    private fun knownValue(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("Unknown", true) && !it.equals("N/A", true)
    }

    private fun fields(value: String): Set<Field> =
        Field.entries.filterTo(mutableSetOf()) { it.pattern.containsMatchIn(value) }

    private fun transport(source: StreamSource): String? {
        val mime = source.mimeType?.substringBefore(';')?.trim()?.lowercase()
        val path = source.url?.substringBefore('?')?.substringBefore('#')?.lowercase().orEmpty()
        val declaredFormat = when (mime) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl" -> "HLS"
            "application/dash+xml" -> "DASH"
            "video/mp4" -> "MP4"
            "video/webm" -> "WebM"
            "video/x-matroska" -> "MKV"
            else -> null
        }
        return declaredFormat ?: when {
            path.endsWith(".m3u8") -> "HLS"
            path.endsWith(".mpd") -> "DASH"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "MP4"
            path.endsWith(".webm") -> "WebM"
            path.endsWith(".mkv") -> "MKV"
            else -> null
        }
    }
}
