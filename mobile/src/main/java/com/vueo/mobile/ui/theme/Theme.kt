package com.vueo.mobile.ui.theme

import com.vueo.shared.ui.appearance.AppAppearance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vueo.mobile.ui.VueoPalette

@Composable
fun VueoTheme(
    content: @Composable () -> Unit,
) {
    val accent =
        VueoPalette.Accent
    val appTheme =
        VueoPalette.Theme

    val colors = remember(accent, appTheme) { AppAppearance.colorScheme() }

    MaterialTheme(
        colorScheme = colors,
        typography =
            MaterialTheme.typography,
        content = content,
    )
}
