package com.vueo.tv.player

import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.vueo.shared.core.player.IndependentSubtitleRepository
import com.vueo.shared.core.player.TimedSubtitleCue
import kotlinx.coroutines.delay

@Composable
internal fun TvIndependentSubtitleOverlay(
    player: ExoPlayer,
    cues: List<TimedSubtitleCue>,
    delayMs: Int,
    style: TvPlayerSubtitleStyleState,
    bottomPaddingFraction: Float,
    visible: Boolean,
) {
    if (!visible) return

    var activeTexts by remember(cues, visible) {
        mutableStateOf<List<String>>(emptyList())
    }

    LaunchedEffect(player, cues, delayMs, visible) {
        if (!visible || cues.isEmpty()) {
            activeTexts = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            val subtitlePositionMs =
                (player.currentPosition - delayMs.toLong())
                    .coerceAtLeast(0L)
            activeTexts = IndependentSubtitleRepository.activeTexts(
                cues = cues,
                positionMs = subtitlePositionMs,
            )
            delay(120L)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SubtitleView(context).apply {
                isClickable = false
                isFocusable = false
                applyTvIndependentSubtitleStyle(
                    style = style,
                    bottomPaddingFraction = bottomPaddingFraction,
                )
            }
        },
        update = { view ->
            view.applyTvIndependentSubtitleStyle(
                style = style,
                bottomPaddingFraction = bottomPaddingFraction,
            )
            view.setCues(
                activeTexts.map { text ->
                    Cue.Builder()
                        .setText(text)
                        .build()
                }
            )
        },
    )
}

private fun SubtitleView.applyTvIndependentSubtitleStyle(
    style: TvPlayerSubtitleStyleState,
    bottomPaddingFraction: Float,
) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setFixedTextSize(
        TypedValue.COMPLEX_UNIT_SP,
        style.fontSizeSp.toFloat(),
    )
    setBottomPaddingFraction(bottomPaddingFraction)
    setStyle(
        CaptionStyleCompat(
            style.textColor,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            if (style.outlineEnabled) {
                CaptionStyleCompat.EDGE_TYPE_OUTLINE
            } else {
                CaptionStyleCompat.EDGE_TYPE_NONE
            },
            style.outlineColor,
            if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
        )
    )
}
