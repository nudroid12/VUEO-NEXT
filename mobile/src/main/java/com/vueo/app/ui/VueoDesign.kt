package com.vueo.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.R
import com.vueo.app.core.storage.AppAccent
import com.vueo.app.core.storage.AppTheme

private data class VueoThemePalette(
    val background: Color,
    val nav: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceStrong: Color,
    val profileSurface: Color,
    val profileSurfaceAlt: Color,
    val stroke: Color,
    val muted: Color,
)

internal object VueoPalette {
    private val charcoal =
        VueoThemePalette(
            background = Color(0xFF070A0D),
            nav = Color(0xFF0A0D11),
            surface = Color(0xFF101419),
            surfaceElevated = Color(0xFF161B20),
            surfaceStrong = Color(0xFF1D2329),
            profileSurface = Color(0xFF242A2E),
            profileSurfaceAlt = Color(0xFF20262A),
            stroke = Color(0xFF2A323A),
            muted = Color(0xFFA2AAB3),
        )

    private val midnight =
        VueoThemePalette(
            background = Color(0xFF060A12),
            nav = Color(0xFF090E18),
            surface = Color(0xFF101927),
            surfaceElevated = Color(0xFF182438),
            surfaceStrong = Color(0xFF213047),
            profileSurface = Color(0xFF262E3A),
            profileSurfaceAlt = Color(0xFF202733),
            stroke = Color(0xFF314057),
            muted = Color(0xFFA7B0C0),
        )

    private val deepTeal =
        VueoThemePalette(
            background = Color(0xFF061011),
            nav = Color(0xFF081415),
            surface = Color(0xFF0D1B1C),
            surfaceElevated = Color(0xFF142627),
            surfaceStrong = Color(0xFF1B3031),
            profileSurface = Color(0xFF283130),
            profileSurfaceAlt = Color(0xFF222B2A),
            stroke = Color(0xFF294143),
            muted = Color(0xFFA4B5B5),
        )

    private var themeState by mutableStateOf(
        AppTheme.CHARCOAL
    )

    private val palette: VueoThemePalette
        get() =
            when (themeState) {
                AppTheme.CHARCOAL -> charcoal
                AppTheme.MIDNIGHT -> midnight
                AppTheme.DEEP_TEAL -> deepTeal
            }

    val Theme: AppTheme
        get() = themeState

    val Background: Color
        get() = palette.background

    val Nav: Color
        get() = palette.nav

    val Surface: Color
        get() = palette.surface

    val SurfaceElevated: Color
        get() = palette.surfaceElevated

    val SurfaceStrong: Color
        get() = palette.surfaceStrong

    val ProfileSurface: Color
        get() = palette.profileSurface

    val ProfileSurfaceAlt: Color
        get() = palette.profileSurfaceAlt

    val Stroke: Color
        get() = palette.stroke

    val Muted: Color
        get() = palette.muted

    // Official VUEO identity:
    // lime V/play mark + clean white wordmark.
    // Brand identity never follows the user's UI accent or theme.
    val BrandLime = Color(0xFFB6FF00)

    private var accentState by mutableStateOf(
        Color(AppAccent.WHITE.argb)
    )

    val Accent: Color
        get() = accentState

    fun applyTheme(theme: AppTheme) {
        if (themeState != theme) {
            themeState = theme
        }
    }

    fun applyAccent(accent: AppAccent) {
        val next = Color(accent.argb)
        if (accentState != next) {
            accentState = next
        }
    }

    val Success = Color(0xFF72DF87)
    val Warning = Color(0xFFFFB84D)
    val Error = Color(0xFFFF6767)
}

internal fun AppAccent.composeColor(): Color = Color(argb)

@Composable
internal fun VueoBrandMark(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    color: Color = VueoPalette.BrandLime,
) {
    Image(
        painter = painterResource(
            R.drawable.vueo_logo_mark
        ),
        contentDescription = "VUEO",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun VueoBrandLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        VueoBrandMark(
            modifier = Modifier.size(
                if (compact) 29.dp else 36.dp
            )
        )

        Spacer(
            Modifier.width(
                if (compact) 8.dp else 11.dp
            )
        )

        Text(
            text = "VUEO",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 18.sp else 23.sp,
            letterSpacing = if (compact) 2.2.sp else 3.0.sp,
        )
    }
}
