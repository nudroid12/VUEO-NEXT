package com.vueo.shared.core.player

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack

/** Pure player track identity and presentation helpers shared by Mobile and TV. */
object PlayerTrackPolicy {
    const val SUBTITLE_LABEL_PREFIX = "vueo-subtitle:"
    const val SUBTITLE_OFF = "subtitle:off"
    const val SUBTITLE_LANGUAGE_PREFIX = "subtitle-language:"
    const val AUDIO_AUTO = "audio:auto"

    fun externalSubtitleSelectionId(track: SubtitleTrack): String =
        "external:${track.providerId}:${track.id}:${track.url.hashCode()}"

    fun externalSubtitleLabel(track: SubtitleTrack): String =
        "$SUBTITLE_LABEL_PREFIX${externalSubtitleSelectionId(track)}"

    fun builtinSubtitleSelectionId(
        language: String?,
        formatLabel: String?,
        trackIndex: Int,
    ): String =
        "builtin:${LanguagePolicy.canonicalOrUnknown(language)}:${formatLabel.orEmpty()}:$trackIndex"

    fun subtitleLanguageSelectionId(language: String?): String =
        SUBTITLE_LANGUAGE_PREFIX + LanguagePolicy.canonicalOrUnknown(language)

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
