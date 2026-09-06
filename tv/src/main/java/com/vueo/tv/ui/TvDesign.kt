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
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme

private data class TvThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val dim: Color,
)

object TvDesign {
    private val charcoal =
        TvThemePalette(
            background = Color(0xFF070A0D),
            surface = Color(0xFF101419),
            surfaceRaised = Color(0xFF1D2329),
            muted = Color(0xFFA2AAB3),
            dim = Color(0xFF737B84),
        )
    private val midnight =
        TvThemePalette(
            background = Color(0xFF060A12),
            surface = Color(0xFF101927),
            surfaceRaised = Color(0xFF213047),
            muted = Color(0xFFA7B0C0),
            dim = Color(0xFF748096),
        )
    private val deepTeal =
        TvThemePalette(
            background = Color(0xFF061011),
            surface = Color(0xFF0D1B1C),
            surfaceRaised = Color(0xFF1B3031),
            muted = Color(0xFFA4B5B5),
            dim = Color(0xFF718383),
        )

    private var themeState by mutableStateOf(AppTheme.CHARCOAL)
    private var accentState by mutableStateOf(Color(AppAccent.WHITE.argb))

    private val palette: TvThemePalette
        get() = when (themeState) {
            AppTheme.CHARCOAL -> charcoal
            AppTheme.MIDNIGHT -> midnight
            AppTheme.DEEP_TEAL -> deepTeal
        }

    val Black: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val SurfaceRaised: Color get() = palette.surfaceRaised
    val White = Color(0xFFF4F5F7)
    val Muted: Color get() = palette.muted
    val Dim: Color get() = palette.dim
    val Accent: Color get() = accentState
    val Focus: Color get() = accentState

    val ScreenPadding = PaddingValues(horizontal = 52.dp, vertical = 32.dp)
    val CardShape = RoundedCornerShape(12.dp)

    fun applyTheme(theme: AppTheme) {
        if (themeState != theme) themeState = theme
    }

    fun applyAccent(accent: AppAccent) {
        val next = Color(accent.argb)
        if (accentState != next) accentState = next
    }
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
                color = if (focused) TvDesign.Accent.copy(alpha = .92f)
                else TvDesign.White.copy(alpha = .10f),
                shape = TvDesign.CardShape,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        content()
    }
}
