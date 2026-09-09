package com.vueo.tv.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.PlayerTrackPolicy

internal const val TV_SUBTITLE_OFF = PlayerTrackPolicy.SUBTITLE_OFF
internal const val TV_SUBTITLE_LANGUAGE_PREFIX = PlayerTrackPolicy.SUBTITLE_LANGUAGE_PREFIX
internal const val TV_AUDIO_AUTO = PlayerTrackPolicy.AUDIO_AUTO
private const val TV_SUBTITLE_LABEL_PREFIX = PlayerTrackPolicy.SUBTITLE_LABEL_PREFIX

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

internal data class TvPlayerSubtitleStyleState(
    val fontSizeSp: Int = 18,
    val bold: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = 0xFF000000.toInt(),
    val bottomPaddingPercent: Int = 8,
)

internal fun tvExternalSubtitleSelectionId(track: SubtitleTrack): String =
    PlayerTrackPolicy.externalSubtitleSelectionId(track)

internal fun tvExternalSubtitleLabel(track: SubtitleTrack): String =
    PlayerTrackPolicy.externalSubtitleLabel(track)

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
                else -> PlayerTrackPolicy.builtinSubtitleSelectionId(
                    language = trackLanguage,
                    formatLabel = format.label,
                    trackIndex = trackIndex,
                )
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
    val preferred = preferredLanguageCode?.let(::tvCanonicalLanguage)
    val secondary = secondaryLanguageCode?.let(::tvCanonicalLanguage)

    return tracks
        .groupBy { tvCanonicalLanguage(it.language) }
        .map { (code, groupedTracks) ->
            TvSubtitleLanguageGroup(
                code = code,
                label = tvFriendlyLanguage(code),
                tracks = groupedTracks,
            )
        }
        .sortedWith(
            compareBy<TvSubtitleLanguageGroup> {
                when (it.code) {
                    preferred -> 0
                    secondary -> 1
                    "und" -> 3
                    else -> 2
                }
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

internal fun tvCanonicalLanguage(value: String?): String =
    LanguagePolicy.canonicalOrUnknown(value)

internal fun tvFriendlyLanguage(value: String?): String =
    LanguagePolicy.friendlyName(value)

private fun tvBuildAudioTrackLabel(
    formatLabel: String?,
    language: String?,
    fallbackIndex: Int,
): String =
    PlayerTrackPolicy.audioTrackLabel(
        formatLabel = formatLabel,
        language = language,
        fallbackIndex = fallbackIndex,
    )

private fun tvBuildAudioTrackMetadata(
    formatLabel: String?,
    channelCount: Int,
    sampleMimeType: String?,
): String =
    PlayerTrackPolicy.audioTrackMetadata(
        formatLabel = formatLabel,
        channelCount = channelCount,
        sampleMimeType = sampleMimeType,
    )

private fun tvBuildAudioSelectionId(
    language: String?,
    formatLabel: String?,
    channelCount: Int,
    sampleMimeType: String?,
    trackId: String?,
): String =
    PlayerTrackPolicy.audioSelectionId(
        language = language,
        formatLabel = formatLabel,
        channelCount = channelCount,
        sampleMimeType = sampleMimeType,
        trackId = trackId,
    )

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvSubtitleOffsetRenderersFactory(
    context: Context,
    private val subtitleDelayUsProvider: () -> Long,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val normalizingOutput = TvCueNormalizingTextOutput(
            delegate = output,
            shouldNormalizeCuePositionProvider = shouldNormalizeCuePositionProvider,
        )
        val firstTextRenderer = out.size
        super.buildTextRenderers(
            context,
            normalizingOutput,
            outputLooper,
            extensionRendererMode,
            out,
        )
        for (index in firstTextRenderer until out.size) {
            out[index] = TvSubtitleOffsetRenderer(
                baseRenderer = out[index],
                subtitleDelayUsProvider = subtitleDelayUsProvider,
            )
        }
    }
}

private class TvCueNormalizingTextOutput(
    private val delegate: TextOutput,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        delegate.onCues(
            CueGroup(
                cueGroup.cues.map(::normalizeCuePosition),
                cueGroup.presentationTimeUs,
            )
        )
    }

    @Deprecated("Uses a deprecated player callback for text outputs.")
    override fun onCues(cues: List<Cue>) {
        delegate.onCues(cues.map(::normalizeCuePosition))
    }

    private fun normalizeCuePosition(cue: Cue): Cue {
        if (
            !shouldNormalizeCuePositionProvider() ||
            cue.bitmap != null ||
            cue.verticalType != Cue.TYPE_UNSET ||
            cue.line == Cue.DIMEN_UNSET
        ) {
            return cue
        }

        return cue.buildUpon()
            .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
            .setLineAnchor(Cue.TYPE_UNSET)
            .build()
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
