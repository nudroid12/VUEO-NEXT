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
internal fun VueoPlayerPauseBackdrop(visible: Boolean, media: MediaItem, episode: EpisodeItem?) {
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
                Text("Press OK to resume", color = Color.White.copy(alpha = .65f), fontSize = 14.sp)
            }
        }
    }
}
