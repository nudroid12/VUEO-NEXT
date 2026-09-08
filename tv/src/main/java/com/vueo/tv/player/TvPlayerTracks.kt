package com.vueo.tv.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.vueo.shared.core.media.SubtitleTrack

internal const val TV_SUBTITLE_OFF = "subtitle:off"
internal const val TV_SUBTITLE_LANGUAGE_PREFIX = "subtitle-language:"
internal const val TV_AUDIO_AUTO = "audio:auto"
private const val TV_SUBTITLE_LABEL_PREFIX = "vueo-subtitle:"

internal data class TvPlayerTrackChoice(
    val key: String,
    val label: String,
    val override: TrackSelectionOverride,
    val selected: Boolean,
    val language: String?,
    val sourceLabel: String,
    val metadata: String?,
    val selectionId: String,
)

internal data class TvSubtitleLanguageGroup(
    val code: String,
    val label: String,
    val tracks: List<TvPlayerTrackChoice>,
)

internal fun tvExternalSubtitleSelectionId(track: SubtitleTrack): String =
    "external:${track.providerId}:${track.id}:${track.url.hashCode()}"

internal fun tvExternalSubtitleLabel(track: SubtitleTrack): String =
    "$TV_SUBTITLE_LABEL_PREFIX${tvExternalSubtitleSelectionId(track)}"

internal fun tvPlayerTrackChoices(
    tracks: Tracks,
    trackType: Int,
    externalSubtitles: Map<String, SubtitleTrack> = emptyMap(),
): List<TvPlayerTrackChoice> {
    val result = mutableListOf<TvPlayerTrackChoice>()

    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != trackType) return@forEachIndexed

        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val externalSubtitle =
                format.id?.let(externalSubtitles::get)
                    ?: format.label
                        ?.removePrefix(TV_SUBTITLE_LABEL_PREFIX)
                        ?.takeIf { format.label?.startsWith(TV_SUBTITLE_LABEL_PREFIX) == true }
                        ?.let(externalSubtitles::get)
            val trackLanguage =
                externalSubtitle?.language
                    ?: format.language
                    ?: format.label?.trim()?.takeIf { it.length in 2..3 }
            val selectionId = when {
                externalSubtitle != null -> tvExternalSubtitleSelectionId(externalSubtitle)
                trackType == C.TRACK_TYPE_AUDIO -> tvBuildAudioSelectionId(
                    language = trackLanguage,
                    formatLabel = format.label,
                    channelCount = format.channelCount,
                    sampleMimeType = format.sampleMimeType,
                    trackId = format.id,
                )
                else -> "builtin:${tvCanonicalLanguage(trackLanguage)}:${format.label.orEmpty()}:$trackIndex"
            }
            val label = if (trackType == C.TRACK_TYPE_TEXT) {
                externalSubtitle?.name?.takeIf { it.isNotBlank() }
                    ?: tvFriendlyLanguage(trackLanguage)
            } else {
                tvBuildAudioTrackLabel(format.label, format.language, result.size + 1)
            }
            result += TvPlayerTrackChoice(
                key = "$groupIndex:$trackIndex",
                label = label,
                override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
                selected = group.isTrackSelected(trackIndex),
                language = trackLanguage,
                sourceLabel = externalSubtitle?.providerName ?: if (trackType == C.TRACK_TYPE_TEXT) "Built-in" else "Stream",
                metadata = if (trackType == C.TRACK_TYPE_AUDIO) {
                    tvBuildAudioTrackMetadata(format.label, format.channelCount, format.sampleMimeType)
                } else {
                    tvFriendlyLanguage(trackLanguage)
                },
                selectionId = selectionId,
            )
        }
    }

    return result
}

internal fun tvBuildSubtitleLanguageGroups(
    tracks: List<TvPlayerTrackChoice>,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
): List<TvSubtitleLanguageGroup> {
    val preferredOrder = listOfNotNull(preferredLanguageCode, secondaryLanguageCode)
        .map(::tvCanonicalLanguage)
        .distinct()

    return tracks
        .groupBy { tvCanonicalLanguage(it.language) }
        .map { (code, groupedTracks) ->
            TvSubtitleLanguageGroup(
                code = code,
                label = tvFriendlyLanguage(groupedTracks.firstOrNull()?.language ?: code),
                tracks = groupedTracks,
            )
        }
        .sortedWith(
            compareBy<TvSubtitleLanguageGroup> {
                preferredOrder.indexOf(it.code).let { index -> if (index < 0) Int.MAX_VALUE else index }
            }.thenBy { it.label.lowercase() }
        )
}

internal fun tvFindSavedAudioTrack(
    tracks: List<TvPlayerTrackChoice>,
    savedSelection: String?,
): TvPlayerTrackChoice? {
    if (savedSelection.isNullOrBlank()) return null
    tracks.firstOrNull { it.selectionId == savedSelection }?.let { return it }
    val savedLanguage = savedSelection.split(':').getOrNull(1)
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?: return null
    return tracks.firstOrNull { tvCanonicalLanguage(it.language) == savedLanguage }
}

internal fun tvApplyTrackChoice(
    player: androidx.media3.exoplayer.ExoPlayer,
    trackType: Int,
    choice: TvPlayerTrackChoice,
) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .clearOverridesOfType(trackType)
        .setOverrideForType(choice.override)
        .build()
}

internal fun tvClearTrackOverride(
    player: androidx.media3.exoplayer.ExoPlayer,
    trackType: Int,
    disable: Boolean,
) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(trackType)
        .setTrackTypeDisabled(trackType, disable)
        .build()
}

internal fun tvCanonicalLanguage(value: String?): String {
    val code = value?.trim()?.lowercase().orEmpty().substringBefore('-').substringBefore('_')
    return when (code) {
        "may", "msa" -> "ms"
        "ind" -> "id"
        "chi", "zho" -> "zh"
        "jpn" -> "ja"
        "kor" -> "ko"
        "tha" -> "th"
        "spa" -> "es"
        "fra", "fre" -> "fr"
        "deu", "ger" -> "de"
        "ara" -> "ar"
        "hin" -> "hi"
        "" -> "und"
        else -> code
    }
}

internal fun tvFriendlyLanguage(value: String?): String = when (tvCanonicalLanguage(value)) {
    "en" -> "English"
    "ms" -> "Malay"
    "id" -> "Indonesian"
    "zh" -> "Chinese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "th" -> "Thai"
    "es" -> "Spanish"
    "fr" -> "French"
    "de" -> "German"
    "ar" -> "Arabic"
    "hi" -> "Hindi"
    "und" -> "Unknown"
    else -> value?.replaceFirstChar { it.uppercase() } ?: "Unknown"
}

private fun tvBuildAudioTrackLabel(formatLabel: String?, language: String?, fallbackIndex: Int): String {
    val languageName = tvFriendlyLanguage(language)
    if (languageName != "Unknown") return languageName
    val label = formatLabel?.trim().orEmpty()
    val labelLanguage = tvFriendlyLanguage(label)
    return when {
        labelLanguage != "Unknown" && label.length in 2..3 -> labelLanguage
        label.isNotBlank() -> label
        else -> "Audio track $fallbackIndex"
    }
}

private fun tvBuildAudioTrackMetadata(formatLabel: String?, channelCount: Int, sampleMimeType: String?): String = buildList {
    when (channelCount) {
        1 -> add("Mono")
        2 -> add("Stereo")
        6 -> add("5.1")
        8 -> add("7.1")
        in 3..Int.MAX_VALUE -> add("$channelCount channels")
    }
    when (sampleMimeType?.lowercase()) {
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
        else -> sampleMimeType?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.uppercase()
    }?.let(::add)
    val lowerLabel = formatLabel?.lowercase().orEmpty()
    when {
        "commentary" in lowerLabel -> add("Commentary")
        "original" in lowerLabel -> add("Original")
        "dub" in lowerLabel -> add("Dub")
        "descriptive" in lowerLabel || "description" in lowerLabel -> add("Audio description")
    }
}.distinct().joinToString(" • ")

private fun tvBuildAudioSelectionId(
    language: String?,
    formatLabel: String?,
    channelCount: Int,
    sampleMimeType: String?,
    trackId: String?,
): String = listOf(
    "audio",
    tvCanonicalLanguage(language),
    formatLabel.orEmpty().trim().lowercase(),
    channelCount.toString(),
    sampleMimeType.orEmpty().lowercase(),
    trackId.orEmpty().lowercase(),
).joinToString(":")

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvSubtitleOffsetRenderersFactory(
    context: Context,
    private val subtitleDelayUsProvider: () -> Long,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val firstTextRenderer = out.size
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        for (index in firstTextRenderer until out.size) {
            out[index] = TvSubtitleOffsetRenderer(
                baseRenderer = out[index],
                subtitleDelayUsProvider = subtitleDelayUsProvider,
            )
        }
    }
}

private class TvSubtitleOffsetRenderer(
    baseRenderer: Renderer,
    private val subtitleDelayUsProvider: () -> Long,
) : ForwardingRenderer(baseRenderer) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val subtitlePositionUs = (positionUs - subtitleDelayUsProvider()).coerceAtLeast(0L)
        super.render(subtitlePositionUs, elapsedRealtimeUs)
    }
}
