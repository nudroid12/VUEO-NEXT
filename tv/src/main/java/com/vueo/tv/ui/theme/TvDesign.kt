package com.vueo.tv.ui

import com.vueo.shared.ui.appearance.AppAppearance

import com.vueo.tv.ui.motion.*
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
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme

object TvDesign {
    val Theme get() = AppAppearance.theme
    val AccentChoice get() = AppAppearance.accentChoice
    val Black get() = AppAppearance.palette.background
    val Nav get() = AppAppearance.palette.nav
    val Surface get() = AppAppearance.palette.surface
    val SurfaceElevated get() = AppAppearance.palette.elevated
    val SurfaceRaised get() = AppAppearance.palette.raised
    val Stroke get() = AppAppearance.palette.stroke
    val White get() = AppAppearance.text
    val Muted get() = AppAppearance.palette.muted
    val Dim get() = AppAppearance.palette.dim
    val Accent get() = AppAppearance.accent
    val OnAccent get() = AppAppearance.onAccent
    val Focus get() = Accent
    val AccentContainer get() = AppAppearance.accentContainer
    val FocusSurface get() = AppAppearance.focusSurface
    val Rating = Color(0xFFE8BD62)

    val ScreenPadding = PaddingValues(horizontal = 52.dp, vertical = 32.dp)
    val CardShape = RoundedCornerShape(12.dp)

    fun applyTheme(theme: AppTheme) = AppAppearance.applyTheme(theme)
    fun applyAccent(accent: AppAccent) = AppAppearance.applyAccent(accent)

}

@Composable
fun Modifier.tvPremiumFocus(
    onFocused: (() -> Unit)? = null,
    scale: Float = 1.025f,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(scale) else 1f,
        animationSpec = tvTunedSpec(TvMotionGroup.FOCUS, focused, tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        )),
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
                if (focused) TvDesign.Accent.copy(alpha = .15f)
                else TvDesign.Surface.copy(alpha = .70f),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus.copy(alpha = .92f)
                else TvDesign.White.copy(alpha = .10f),
                shape = TvDesign.CardShape,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        content()
    }
}
