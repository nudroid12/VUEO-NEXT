package com.vueo.tv.player

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvNetworkImage

/** Presentation only: the player root retains focus and owns dismissal. */
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(180)),
    ) {
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
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 56.dp),
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
                    .widthIn(max = 680.dp),
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
                Text("Press OK to resume", color = Color.White.copy(alpha = .65f), fontSize = 14.sp)
            }
        }
    }
}
