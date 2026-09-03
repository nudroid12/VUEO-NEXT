package com.vueo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vueo.app.ui.VueoPalette

@Composable
fun VueoTheme(
    content: @Composable () -> Unit,
) {
    val accent =
        VueoPalette.Accent

    val colors = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF080B0F),
            secondary = accent.copy(
                alpha = .82f
            ),
            onSecondary = Color(0xFF080B0F),
            background = VueoPalette.Background,
            onBackground = Color(0xFFF4F7F2),
            surface = VueoPalette.Surface,
            onSurface = Color(0xFFE7ECE5),
            surfaceVariant =
                VueoPalette.SurfaceStrong,
            outline = VueoPalette.Stroke,
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography =
            MaterialTheme.typography,
        content = content,
    )
}
