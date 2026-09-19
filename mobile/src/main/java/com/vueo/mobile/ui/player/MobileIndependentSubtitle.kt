package com.vueo.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.player.IndependentSubtitleCueChannel
import com.vueo.shared.core.player.IndependentSubtitleRepository
import kotlinx.coroutines.delay

/**
 * Feed late sidecar cues to the existing subtitle child inside PlayerView.
 * No extra AndroidView or video MediaItem update is involved.
 */
@Composable
internal fun BindIndependentSubtitleCues(
    playerView: PlayerView?,
    player: ExoPlayer,
    cueChannel: IndependentSubtitleCueChannel,
    delayMs: Int,
    visible: Boolean,
) {
    // Restore native cues only when sidecar previously owned this view.
    // A native-only session must NEVER receive setCues() from sidecar code.
    var sidecarOwnedView by remember(playerView) { mutableStateOf(false) }
    LaunchedEffect(playerView, player, cueChannel, delayMs, visible) {
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
                cues = cueChannel.cues,
                positionMs = subtitlePositionMs,
            )
            // Reassert occasionally in case PlayerView receives a native onCues
            // callback while the independent track is selected.
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
