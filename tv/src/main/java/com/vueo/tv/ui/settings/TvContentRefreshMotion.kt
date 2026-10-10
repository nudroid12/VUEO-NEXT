package com.vueo.tv.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Only Content Manager refresh/test actions spin, using their actual busy state. */
@Composable
internal fun tvContentRefreshMotion(entry: TvSettingsEntry): Modifier {
    val busy = when (entry.id) {
        "refresh-addons", "refresh-repository" -> entry.value == "Refreshing…"
        "test-connection" -> entry.value == "Testing…"
        else -> false
    }
    if (!busy) return Modifier

    val transition = rememberInfiniteTransition(label = "contentRefresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "contentRefreshRotation",
    )
    return Modifier.graphicsLayer { rotationZ = rotation }
}
