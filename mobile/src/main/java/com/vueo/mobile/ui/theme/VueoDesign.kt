package com.vueo.mobile.ui

import com.vueo.shared.ui.appearance.AppAppearance

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
import com.vueo.mobile.R
import com.vueo.mobile.core.storage.AppAccent
import com.vueo.mobile.core.storage.AppTheme

internal object VueoPalette {
    val Theme get() = AppAppearance.theme
    val AccentChoice get() = AppAppearance.accentChoice
    val Background get() = AppAppearance.palette.background
    val Nav get() = AppAppearance.palette.nav
    val Surface get() = AppAppearance.palette.surface
    val SurfaceElevated get() = AppAppearance.palette.elevated
    val SurfaceStrong get() = AppAppearance.palette.raised
    val ProfileSurface get() = AppAppearance.palette.profile
    val ProfileSurfaceAlt get() = AppAppearance.palette.profileAlt
    val Stroke get() = AppAppearance.palette.stroke
    val Muted get() = AppAppearance.palette.muted
    val Accent get() = AppAppearance.accent
    val OnAccent get() = AppAppearance.onAccent
    val AccentContainer get() = AppAppearance.accentContainer
    val FocusSurface get() = AppAppearance.focusSurface
    // Branding and semantic colors do not change with the interactive theme.
    val BrandLime = Color(0xFFB6FF00)

    fun applyTheme(theme: AppTheme) = AppAppearance.applyTheme(theme)
    fun applyAccent(accent: AppAccent) = AppAppearance.applyAccent(accent)

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
