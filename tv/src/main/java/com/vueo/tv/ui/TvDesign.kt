package com.vueo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TvDesign {
    val Black = Color(0xFF050607)
    val Surface = Color(0xFF111315)
    val SurfaceRaised = Color(0xFF181B1F)
    val White = Color(0xFFF4F5F7)
    val Muted = Color(0xFFA9ADB4)
    val Dim = Color(0xFF737780)
    val Focus = Color(0xFFF7F8FA)

    val ScreenPadding = PaddingValues(horizontal = 52.dp, vertical = 32.dp)
    val CardShape = RoundedCornerShape(12.dp)
}

@Composable
fun Modifier.tvPremiumFocus(
    onFocused: (() -> Unit)? = null,
    scale: Float = 1.055f,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(durationMillis = if (focused) 135 else 105),
        label = "tvPremiumFocusScale",
    )

    return this
        .scale(animatedScale)
        .onFocusChanged {
            val becameFocused = it.isFocused
            if (becameFocused && !focused) onFocused?.invoke()
            focused = becameFocused
        }
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) TvDesign.Focus.copy(alpha = .92f) else Color.Transparent,
            shape = TvDesign.CardShape,
        )
        .focusable()
}

@Composable
fun TvFocusSurface(
    modifier: Modifier = Modifier,
    focused: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(TvDesign.CardShape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .15f)
                else TvDesign.Surface.copy(alpha = .70f),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .92f)
                else TvDesign.White.copy(alpha = .10f),
                shape = TvDesign.CardShape,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        content()
    }
}
