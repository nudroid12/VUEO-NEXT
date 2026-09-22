package com.vueo.shared.core.player

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack

/** Pure player track identity and presentation helpers shared by Mobile and TV. */
object PlayerTrackPolicy {
    const val SUBTITLE_LABEL_PREFIX = "vueo-subtitle:"
    const val SUBTITLE_OFF = "subtitle:off"
    const val SUBTITLE_LANGUAGE_PREFIX = "subtitle-language:"
    const val SUBTITLE_DEFERRED_LANGUAGE_PREFIX = "subtitle-deferred-language:"
    const val AUDIO_AUTO = "audio:auto"

    fun externalSubtitleSelectionId(track: SubtitleTrack): String {
        val providerId = track.providerId.trim().ifBlank { "unknown" }
        val trackId = track.id.trim()
        val stableTrackId = trackId.ifBlank {
            "${LanguagePolicy.canonicalOrUnknown(track.language)}:${track.url.hashCode()}"
        }
        return "external:$providerId:$stableTrackId"
    }

    fun externalSubtitleLabel(track: SubtitleTrack): String =
        "$SUBTITLE_LABEL_PREFIX${externalSubtitleSelectionId(track)}"

    fun subtitleDisplayId(track: SubtitleTrack): String? {
        val displayId = subtitleDisplayId(track.id) ?: return null
        val idLanguage = LanguagePolicy.knownCode(displayId)
        val trackLanguage = LanguagePolicy.canonicalCode(track.language)
        return displayId.takeUnless {
            idLanguage != null && idLanguage == trackLanguage
        }
    }

    fun subtitleDisplayId(rawId: String?): String? {
        val cleaned = rawId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val sourceId = Regex("^.+?:\\d+:(.+)$")
            .matchEntire(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cleaned

        return sourceId.takeIf { value ->
            !value.startsWith(SUBTITLE_LABEL_PREFIX) &&
                !value.startsWith("external:") &&
                !Regex("^track\\s*\\d+$", RegexOption.IGNORE_CASE).matches(value)
        }
    }

    fun builtinSubtitleSelectionId(
        language: String?,
        formatLabel: String?,
        trackId: String?,
        groupIndex: Int,
        trackIndex: Int,
    ): String = listOf(
        "builtin",
        LanguagePolicy.canonicalOrUnknown(language),
        formatLabel.orEmpty().trim().lowercase(),
        trackId.orEmpty().trim().lowercase(),
        groupIndex.toString(),
        trackIndex.toString(),
    ).joinToString(":")

    fun subtitleLanguageSelectionId(language: String?): String =
        SUBTITLE_LANGUAGE_PREFIX + LanguagePolicy.canonicalOrUnknown(language)

    fun deferredSubtitleSelectionId(language: String?): String =
        SUBTITLE_DEFERRED_LANGUAGE_PREFIX + LanguagePolicy.canonicalOrUnknown(language)

    fun deferredSubtitleLanguage(selectionId: String?): String? =
        selectionId
            ?.takeIf { it.startsWith(SUBTITLE_DEFERRED_LANGUAGE_PREFIX) }
            ?.removePrefix(SUBTITLE_DEFERRED_LANGUAGE_PREFIX)
            ?.takeIf { it.isNotBlank() && it != "und" }

    fun resolvedSubtitleSelection(
        globalSelection: String?,
        contentSelection: String?,
    ): String? =
        if (globalSelection == SUBTITLE_OFF) {
            SUBTITLE_OFF
        } else {
            contentSelection ?: globalSelection
        }

    fun audioSelectionId(
        language: String?,
        formatLabel: String?,
        channelCount: Int,
        sampleMimeType: String?,
        trackId: String?,
    ): String = listOf(
        "audio",
        LanguagePolicy.canonicalOrUnknown(language),
        formatLabel.orEmpty().trim().lowercase(),
        channelCount.toString(),
        sampleMimeType.orEmpty().lowercase(),
        trackId.orEmpty().lowercase(),
    ).joinToString(":")

    fun audioTrackLabel(
        formatLabel: String?,
        language: String?,
        fallbackIndex: Int,
    ): String {
        val languageName = LanguagePolicy.friendlyName(language)
        if (languageName != "Unknown") return languageName

        val label = formatLabel?.trim().orEmpty()
        val labelLanguage = LanguagePolicy.friendlyName(label)
        return when {
            labelLanguage != "Unknown" && label.length in 2..3 -> labelLanguage
            label.isNotBlank() -> label
            else -> "Audio track $fallbackIndex"
        }
    }

    fun audioTrackMetadata(
        formatLabel: String?,
        channelCount: Int,
        sampleMimeType: String?,
    ): String = buildList {
        when (channelCount) {
            1 -> add("Mono")
            2 -> add("Stereo")
            6 -> add("5.1")
            8 -> add("7.1")
            in 3..Int.MAX_VALUE -> add("$channelCount channels")
        }
        audioCodec(sampleMimeType)?.let(::add)
        audioVariant(formatLabel)?.let(::add)
    }.distinct().joinToString(" • ")

    private fun audioCodec(value: String?): String? = when (value?.lowercase()) {
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3" -> "Dolby Digital Plus"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/opus" -> "Opus"
        "audio/flac" -> "FLAC"
        "audio/mpeg" -> "MP3"
        else -> value
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
    }

    private fun audioVariant(value: String?): String? {
        val label = value?.lowercase().orEmpty()
        return when {
            "commentary" in label -> "Commentary"
            "original" in label -> "Original"
            "dub" in label -> "Dub"
            "descriptive" in label || "description" in label -> "Audio description"
            else -> null
        }
    }
}
