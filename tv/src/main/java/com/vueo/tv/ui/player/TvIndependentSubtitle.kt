package com.vueo.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.player.IndependentSubtitleRepository
import com.vueo.shared.core.player.TimedSubtitleCue
import kotlinx.coroutines.delay

/** Sidecar cues share PlayerView's native subtitle layer with embedded tracks. */
@Composable
internal fun TvBindIndependentSubtitleCues(
    playerView: PlayerView?,
    player: ExoPlayer,
    cues: List<TimedSubtitleCue>,
    delayMs: Int,
    visible: Boolean,
) {
    // Restore native cues only when sidecar previously owned this view.
    // A native-only session must NEVER receive setCues() from sidecar code.
    var sidecarOwnedView by remember(playerView) { mutableStateOf(false) }
    LaunchedEffect(playerView, player, cues, delayMs, visible) {
        val subtitleView = playerView?.subtitleView ?: return@LaunchedEffect
        if (!visible) {
            if (sidecarOwnedView) {
                sidecarOwnedView = false
                subtitleView.setCues(player.currentCues.cues)
            }
            return@LaunchedEffect
        }
        sidecarOwnedView = true

        var lastTexts: List<String>? = null
        var ticks = 0
        while (true) {
            val subtitlePositionMs =
                (player.currentPosition - delayMs.toLong()).coerceAtLeast(0L)
            val texts = IndependentSubtitleRepository.activeTexts(
                cues = cues,
                positionMs = subtitlePositionMs,
            )
            if (texts != lastTexts || ticks % 8 == 0) {
                subtitleView.setCues(
                    texts.map { text -> Cue.Builder().setText(text).build() }
                )
                lastTexts = texts
            }
            ticks++
            delay(120L)
        }
    }
}
