package com.vueo.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Display-only playback status. It never changes player focus or control visibility. */
@Composable
internal fun MobileBufferingIndicator(
    buffering: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var showBuffering by remember { mutableStateOf(false) }

    LaunchedEffect(buffering, enabled) {
        showBuffering = false
        if (buffering && enabled) {
            delay(500L)
            if (buffering) {
                showBuffering = true
            }
        }
    }

    AnimatedVisibility(
        visible = enabled && buffering && showBuffering,
        modifier = modifier,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(Color.Black.copy(alpha = .55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = VueoPalette.Accent,
                strokeWidth = 3.dp,
            )
        }
    }
}

/** Compact status shown in the normal top control row while subtitle translation is active. */
@Composable
internal fun MobileTranslationStatusPill() {
    Row(
        modifier = Modifier
            .background(
                Color.Black.copy(alpha = .58f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            color = VueoPalette.Accent,
            strokeWidth = 1.5.dp,
        )
        Text(
            "Translating…",
            color = VueoPalette.Accent,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}
