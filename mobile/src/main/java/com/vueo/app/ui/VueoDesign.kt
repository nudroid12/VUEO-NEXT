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

internal object VueoPalette {
    val Background = Color(0xFF070A0D)
    val Nav = Color(0xFF0A0D11)
    val Surface = Color(0xFF101419)
    val SurfaceElevated = Color(0xFF161B20)
    val SurfaceStrong = Color(0xFF1D2329)
    val Stroke = Color(0xFF2A323A)
    val Muted = Color(0xFFA2AAB3)

    // Official VUEO identity:
    // lime V/play mark + clean white wordmark.
    // Brand identity never follows the user's UI accent.
    val BrandLime = Color(0xFFB6FF00)

    private var accentState by mutableStateOf(
        Color(AppAccent.WHITE.argb)
    )

    val Accent: Color
        get() = accentState

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
