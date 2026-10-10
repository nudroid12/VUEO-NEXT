package com.vueo.tv.player

import com.vueo.tv.ui.motion.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvNetworkImage

/** Presentation only: the player root retains focus and owns dismissal. */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun VueoPlayerPauseBackdrop(
    visible: Boolean,
    media: MediaItem,
    episode: EpisodeItem?,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
) {
    val context = LocalContext.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visible) {
        if (visible) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    val pauseEasing = remember { CubicBezierEasing(0.22f, 0f, 0.18f, 1f) }
    AnimatedVisibility(
        visible = visible,
        enter = tvTunedEnter(TvMotionGroup.PAUSE, fadeIn(tween(280, easing = pauseEasing))),
        exit = tvTunedExit(TvMotionGroup.PAUSE, fadeOut(tween(200, easing = pauseEasing))),
    ) {
        // This child animation shares the visibility lifetime, so dismissal
        // retains the text until its exit fade completes.
        val textAlpha by transition.animateFloat(
            transitionSpec = {
                tvTunedSpec(TvMotionGroup.PAUSE, targetState == EnterExitState.Visible,
                    if (targetState == EnterExitState.Visible) tween(220, delayMillis = 60, easing = pauseEasing)
                    else tween(160, easing = pauseEasing))
            },
            label = "pauseBackdropText",
        ) { state -> if (state == EnterExitState.Visible) 1f else tvTunedAlpha(TvMotionGroup.PAUSE, 0f) }
        Box(Modifier.fillMaxSize()) {
            // A transparent fallback leaves the paused PlayerView frame visible
            // while the backdrop is unavailable, loading or has failed.
            TvNetworkImage(
                url = media.background,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                fallback = Color.Transparent,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = .85f)),
            )))
            val timeFormat = DateFormat.getTimeFormat(context)
            val validSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
            val remainingWallMs = if (durationMs > 0L) {
                ((durationMs - positionMs.coerceIn(0L, durationMs)).toDouble() / validSpeed).toLong()
            } else null
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 56.dp)
                    .graphicsLayer { alpha = textAlpha.coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = timeFormat.format(Date(nowMs)),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                remainingWallMs?.let {
                    Text(
                        text = "End at ${timeFormat.format(Date(nowMs + it))}",
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 14.sp,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 56.dp, vertical = 56.dp)
                    .widthIn(max = 680.dp).graphicsLayer { alpha = textAlpha.coerceIn(0f, 1f) },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Paused", color = Color.White.copy(alpha = .70f), fontSize = 14.sp)
                Text(
                    media.name,
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = episode?.let { "S${it.season} E${it.episode} • ${it.title}" }
                    ?: media.releaseInfo?.takeIf { it.isNotBlank() }
                details?.let {
                    Text(it, color = Color.White.copy(alpha = .85f), fontSize = 18.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val synopsis = episode?.overview?.takeIf { it.isNotBlank() }
                    ?: media.description?.takeIf { it.isNotBlank() }
                synopsis?.let {
                    Text(
                        text = it.replace(Regex("<[^>]*>"), " ")
                            .replace(Regex("\\s+"), " ").trim().take(1_000),
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val knownDuration = durationMs > 0L
                val displayPosition = if (knownDuration) positionMs.coerceIn(0L, durationMs)
                    else positionMs.coerceAtLeast(0L)
                val playbackTime = "${vueoPlayerTime(displayPosition)} / " +
                    if (knownDuration) vueoPlayerTime(durationMs) else "--:--"
                val remaining = if (knownDuration) {
                    val remainingMs = durationMs - displayPosition
                    val minutes = remainingMs / 60_000L + if (remainingMs % 60_000L > 0L) 1L else 0L
                    " • $minutes min left"
                } else ""
                Text(
                    text = playbackTime + remaining,
                    color = Color.White.copy(alpha = .78f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Press OK to resume",
                color = Color.White.copy(alpha = .65f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 56.dp)
                    .graphicsLayer { alpha = textAlpha.coerceIn(0f, 1f) },
            )
        }
    }
}
