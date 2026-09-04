package com.vueo.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalTvAccent = staticCompositionLocalOf { Color(0xFFF2F3F5) }

internal val TvAccent: Color
    @Composable get() = LocalTvAccent.current
