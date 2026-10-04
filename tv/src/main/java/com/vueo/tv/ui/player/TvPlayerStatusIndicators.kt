package com.vueo.tv.player

import com.vueo.tv.ui.motion.TvMotion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.delay

/** Display-only feedback; never takes focus or reveals the controls. */
@Composable
internal fun VueoPlayerStatusIndicators(
    buffering: Boolean,
    feedbackToken: Int,
    paused: Boolean,
    translating: Boolean,
    enabled: Boolean,
    showPlaybackFeedback: Boolean,
    translationEndPadding: Dp,
) {
    val translationEnd by animateDpAsState(
        targetValue = translationEndPadding,
        animationSpec = tween(240, easing = TvMotion.EaseOut),
        label = "translationPillEnd",
    )
    var showBuffering by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(buffering, enabled) {
        showBuffering = false
        if (buffering && enabled) {
            delay(500L)
            showBuffering = true
        }
    }
    LaunchedEffect(feedbackToken) {
        showFeedback = false
        if (feedbackToken > 0 && enabled && showPlaybackFeedback) {
            showFeedback = true
            delay(800L)
            showFeedback = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Buffering wins immediately, including during its anti-flash delay.
        AnimatedVisibility(
            visible = enabled && ((buffering && showBuffering) || (!buffering && showFeedback && showPlaybackFeedback)),
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(76.dp).background(Color.Black.copy(alpha = .55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) {
                        CircularProgressIndicator(Modifier.size(40.dp), color = TvDesign.Accent, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            imageVector = if (paused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (paused) "Paused" else "Playing",
                            tint = TvDesign.Accent,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

            }
        }
        AnimatedVisibility(
            visible = enabled && translating,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 35.dp, end = translationEnd),
            enter = fadeIn(tween(TvMotion.ELEMENT_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            Row(
                modifier = Modifier.height(28.dp)
                    .widthIn(max = 132.dp)
                    .background(TvPlayerTopActionBackground, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = TvDesign.Accent, strokeWidth = 1.5.dp)
                Text("Translating…", color = TvDesign.Accent, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
