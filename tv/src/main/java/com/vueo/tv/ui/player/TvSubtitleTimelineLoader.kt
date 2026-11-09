package com.vueo.tv.player

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.SubtitleParser
import com.vueo.shared.core.player.LiveSmartSubs
import com.vueo.shared.core.player.SubtitleSessionCache
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object TvSubtitleTimelineLoader {
    data class Request(val key: String, val mime: String, val bytes: ByteArray, val nativeTrack: Boolean = false)

    // Media3 may prefix a child-source index to the configured Format ID. The
    // VUEO machine label is another stable identity; language alone is not.
    internal fun configurationIndex(id: String?, label: String?, ids: List<String?>, labels: List<String?>): Int? {
        fun unique(indices: List<Int>) = indices.singleOrNull()
        if (!id.isNullOrBlank()) {
            val exact = ids.indices.filter { ids[it] == id }
            if (exact.isNotEmpty()) return unique(exact)
        }
        if (!label.isNullOrBlank() && label.startsWith("vueo-subtitle:")) {
            val exact = labels.indices.filter { labels[it] == label }
            if (exact.isNotEmpty()) return unique(exact)
        }
        val unprefixed = id?.replace(Regex("^(?:[0-9]+:)+"), "")
        if (!unprefixed.isNullOrBlank() && unprefixed != id) {
            return unique(ids.indices.filter { ids[it] == unprefixed })
        }
        return null
    }

    // Only a bare negative integer line setting is eligible for automatic
    // stacking. Keep explicitly authored settings protected, even when the
    // parsed Cue happens to contain default-looking coordinates.
    internal fun hasNativePositionSettings(timing: String): Boolean {
        if ("-->" !in timing) return false
        val settings = timing.substringAfter("-->").trim().split(Regex("\\s+")).drop(1)
        return settings.filter { ':' in it }.any { setting ->
            val name = setting.substringBefore(':')
            val value = setting.substringAfter(':')
            when (name) {
                "line" -> value.toIntOrNull()?.let { it >= 0 } ?: true
                "position", "size", "vertical", "region" -> true
                else -> false
            }
        }
    }

    private var lastNativeDiagnostic: String? = null
    private fun nativeDiagnostic(key: String, detail: String) {
        val signature = "$key|$detail"
        if (signature == lastNativeDiagnostic) return
        lastNativeDiagnostic = signature
        PerformanceDiagnostics.captureRuntimeEvent("TV_NATIVE_LAYOUT_REQUEST $detail")
    }
    fun request(player: Player): Request? {
        val smart = LiveSmartSubs.get(player)?.selectedSubtitleForLayout()
        val configuration = if (smart == null) {
            val selected = player.currentTracks.groups.firstOrNull {
                it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected
            } ?: return null
            val format = (0 until selected.length).firstOrNull { selected.isTrackSelected(it) }
                ?.let(selected::getTrackFormat) ?: return null
            val configurations = player.currentMediaItem?.localConfiguration?.subtitleConfigurations.orEmpty()
            val index = configurationIndex(format.id, format.label,
                configurations.map { it.id }, configurations.map { it.label })
            if (index == null) {
                nativeDiagnostic(format.id.orEmpty(), "matched=false configured=${configurations.size}")
                return null
            }
            configurations[index]
        } else null
        val url = smart?.url ?: configuration?.uri?.toString() ?: return null
        val mime = if (smart != null) when (com.vueo.shared.core.player.SubtitleFormatPolicy.detect(smart.url, smart.mimeType)) {
            com.vueo.shared.core.player.SubtitleFormat.WEBVTT -> "text/vtt"
            com.vueo.shared.core.player.SubtitleFormat.SUBRIP -> "application/x-subrip"
            com.vueo.shared.core.player.SubtitleFormat.SSA -> "text/x-ssa"
            com.vueo.shared.core.player.SubtitleFormat.TTML -> "application/ttml+xml"
        } else configuration?.mimeType ?: return null
        val bytes = SubtitleSessionCache.get(url)
        if (smart == null) nativeDiagnostic(url, "matched=true cached=${bytes != null}")
        return bytes?.let { Request(url, mime, it, nativeTrack = smart == null) }
    }
    data class Analysis(val regions: List<TvSubtitleTemporalPolicy.Region>, val protectedText: Set<String>)
    fun parse(request: Request): Analysis {
        val protected = mutableSetOf<String>()
        if (request.mime == "text/vtt") {
            val body = request.bytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
            for (block in body.split("\n\n")) {
                val timing = block.lineSequence().firstOrNull { "-->" in it } ?: continue
                val positioned = if (request.nativeTrack) hasNativePositionSettings(timing)
                    else TvSubtitleTemporalPolicy.hasVttPositionSettings(timing)
                if (!positioned) continue
                val bytes = ("WEBVTT\n\n" + block + "\n\n").toByteArray()
                val positionedParser = androidx.media3.extractor.text.DefaultSubtitleParserFactory()
                    .create(Format.Builder().setSampleMimeType("text/vtt").build())
                positionedParser.parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { group ->
                    group.cues.mapNotNullTo(protected) { it.text?.toString() }
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
