package com.vueo.shared.ui.appearance

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme

/** Shared, observable appearance roles used by TV, mobile and shared collection screens. */
data class AppearancePalette(
    val background: Color,
    val nav: Color,
    val surface: Color,
    val elevated: Color,
    val raised: Color,
    val profile: Color,
    val profileAlt: Color,
    val stroke: Color,
    val muted: Color,
    val dim: Color,
)

object AppAppearance {
    private val charcoal = AppearancePalette(
        Color(0xFF070A0D), Color(0xFF0A0D11), Color(0xFF101419),
        Color(0xFF161B20), Color(0xFF1D2329), Color(0xFF242A2E),
        Color(0xFF20262A), Color(0xFF2A323A), Color(0xFFA2AAB3), Color(0xFF737B84),
    )
    private val midnight = AppearancePalette(
        Color(0xFF060A12), Color(0xFF090E18), Color(0xFF101927),
        Color(0xFF182438), Color(0xFF213047), Color(0xFF262E3A),
        Color(0xFF202733), Color(0xFF314057), Color(0xFFA7B0C0), Color(0xFF748096),
    )
    private val deepTeal = AppearancePalette(
        Color(0xFF061011), Color(0xFF081415), Color(0xFF0D1B1C),
        Color(0xFF142627), Color(0xFF1B3031), Color(0xFF283130),
        Color(0xFF222B2A), Color(0xFF294143), Color(0xFFA4B5B5), Color(0xFF718383),
    )

    var theme by mutableStateOf(AppTheme.CHARCOAL)
        private set
    var accentChoice by mutableStateOf(AppAccent.WHITE)
        private set

    fun paletteFor(theme: AppTheme): AppearancePalette = when (theme) {
        AppTheme.CHARCOAL -> charcoal
        AppTheme.MIDNIGHT -> midnight
        AppTheme.DEEP_TEAL -> deepTeal
    }
    val palette get() = paletteFor(theme)
    val accent get() = Color(accentChoice.argb)
    val onAccent get() = contentOn(accent)
    val accentContainer get() = lerp(palette.raised, accent, .18f)
    val focusSurface get() = lerp(palette.raised, accent, .25f)
    val text = Color(0xFFF4F5F7)
    val error = Color(0xFFFF8D8D)

    /** The WCAG crossover for black vs white foreground on an opaque color. */
    fun contentOn(color: Color): Color = if (color.luminance() >= .179f) Color.Black else Color.White
    fun applyTheme(value: AppTheme) { theme = value }
    fun applyAccent(value: AppAccent) { accentChoice = value }

    fun colorScheme() = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = text,
        background = palette.background,
        onBackground = text,
        surface = palette.surface,
        onSurface = text,
        surfaceVariant = palette.raised,
        onSurfaceVariant = palette.muted,
        surfaceTint = Color.Transparent,
        surfaceDim = palette.background,
        surfaceBright = palette.raised,
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.nav,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.elevated,
        surfaceContainerHighest = palette.raised,
        outline = palette.stroke,
        outlineVariant = palette.stroke,
        inverseSurface = text,
        inverseOnSurface = palette.background,
        error = error,
        onError = Color.Black,
        errorContainer = Color(0xFF542529),
        onErrorContainer = Color(0xFFFFDADA),
        scrim = Color.Black,
    )
}
