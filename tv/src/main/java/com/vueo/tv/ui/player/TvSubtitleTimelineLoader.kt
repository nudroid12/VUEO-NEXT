package com.vueo.tv.player

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.SubtitleParser
import com.vueo.shared.core.player.LiveSmartSubs
import com.vueo.shared.core.player.SubtitleSessionCache

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object TvSubtitleTimelineLoader {
    data class Request(val key: String, val mime: String, val bytes: ByteArray)
    fun request(player: Player): Request? {
        val smart = LiveSmartSubs.get(player)?.selectedSubtitleForLayout()
        val configuration = if (smart == null) {
            val selected = player.currentTracks.groups.firstOrNull {
                it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected
            } ?: return null
            val format = (0 until selected.length).firstOrNull { selected.isTrackSelected(it) }
                ?.let(selected::getTrackFormat) ?: return null
            player.currentMediaItem?.localConfiguration?.subtitleConfigurations
                ?.firstOrNull { it.id == format.id } ?: return null
        } else null
        val url = smart?.url ?: configuration?.uri?.toString() ?: return null
        val mime = if (smart != null) when (com.vueo.shared.core.player.SubtitleFormatPolicy.detect(smart.url, smart.mimeType)) {
            com.vueo.shared.core.player.SubtitleFormat.WEBVTT -> "text/vtt"
            com.vueo.shared.core.player.SubtitleFormat.SUBRIP -> "application/x-subrip"
            com.vueo.shared.core.player.SubtitleFormat.SSA -> "text/x-ssa"
            com.vueo.shared.core.player.SubtitleFormat.TTML -> "application/ttml+xml"
        } else configuration?.mimeType ?: return null
        return SubtitleSessionCache.get(url)?.let { Request(url, mime, it) }
    }
    data class Analysis(val regions: List<TvSubtitleTemporalPolicy.Region>, val protectedText: Set<String>)
    fun parse(request: Request): Analysis {
        val protected = mutableSetOf<String>()
        if (request.mime == "text/vtt") {
            val body = request.bytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
            for (block in body.split("\n\n")) {
                val timing = block.lineSequence().firstOrNull { "-->" in it } ?: continue
                if (!TvSubtitleTemporalPolicy.hasVttPositionSettings(timing)) continue
                val bytes = ("WEBVTT\n\n" + block + "\n\n").toByteArray()
                val positionedParser = androidx.media3.extractor.text.DefaultSubtitleParserFactory()
                    .create(Format.Builder().setSampleMimeType("text/vtt").build())
                positionedParser.parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { group ->
                    // A negative numbered WebVTT line is a lower-stack cue, not an
                    // authored sign position. Keep only genuinely non-automatic
                    // placements protected so ordinary English tracks can use the
                    // same lower/upper layer policy as SmartSubs.
                    group.cues.filterNot(::isAutomaticText)
                        .mapNotNullTo(protected) { it.text?.toString() }
                }
            }
        }
        val events = mutableListOf<TvSubtitleTemporalPolicy.Event>()
        val parser = androidx.media3.extractor.text.DefaultSubtitleParserFactory()
            .create(Format.Builder().setSampleMimeType(request.mime).build())
        parser.parse(request.bytes, 0, request.bytes.size, SubtitleParser.OutputOptions.allCues()) { group ->
            for (cue in group.cues) {
                check(events.size < 50_000) { "Subtitle layout interval limit" }
                if (isAutomaticText(cue) && cue.text.toString() !in protected) events.add(TvSubtitleTemporalPolicy.Event(
                    cue.text.toString(), group.startTimeUs, group.endTimeUs))
            }
        }
        return Analysis(TvSubtitleTemporalPolicy.analyze(events), protected)
    }
    // Explicit fractional/positive line, noncentral horizontal placement, bitmaps,
    // and vertical writing are protected. Negative line numbers may be auto-generated.
    fun isAutomaticText(cue: Cue): Boolean = !cue.text.isNullOrBlank() &&
        TvSubtitleTemporalPolicy.automaticPlacement(
            line = cue.line.takeUnless { it == Cue.DIMEN_UNSET },
            numbered = cue.lineType == Cue.LINE_TYPE_NUMBER,
            horizontal = cue.position.takeUnless { it == Cue.DIMEN_UNSET },
            width = cue.size.takeUnless { it == Cue.DIMEN_UNSET },
            vertical = cue.verticalType != Cue.TYPE_UNSET,
            bitmap = cue.bitmap != null,
            shear = cue.shearDegrees,
        )
}
