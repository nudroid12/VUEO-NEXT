package com.vueo.tv.player

import androidx.media3.common.text.Cue
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.shared.core.diagnostics.ProviderDiagnostics
import com.vueo.shared.core.player.SubtitleFlowDiagnostics

/** Metadata evidence only: does not change Cue objects or record readable caption text. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object TvSubtitleCueDiagnostics {
    fun enabled() = PerformanceDiagnostics.isCollecting() || ProviderDiagnostics.isCollecting()

    fun capture(source: String, group: Int, stage: String, cues: List<Cue>, layout: String) {
        if (!enabled()) return
        runCatching {
            SubtitleFlowDiagnostics.event("TV", source, "CUE_LAYOUT",
                "group=$group stage=$stage count=${cues.size} $layout")
            cues.take(8).forEachIndexed { index, cue ->
                val text = cue.text?.toString().orEmpty()
                fun value(v: Float) = if (v == Cue.DIMEN_UNSET) "unset" else v.toString()
                SubtitleFlowDiagnostics.event("TV", source, "CUE_LAYOUT_ITEM",
                    "group=$group stage=$stage i=$index textId=${SubtitleFlowDiagnostics.key(text)} " +
                        "chars=${text.length} rows=${text.count { it == '\n' } + 1} " +
                        "line=${value(cue.line)} type=${cue.lineType} anchor=${cue.lineAnchor} " +
                        "pos=${value(cue.position)} posAnchor=${cue.positionAnchor} size=${value(cue.size)} " +
                        "bitmap=${cue.bitmap != null} vertical=${cue.verticalType}")
            }
        }
    }
}
