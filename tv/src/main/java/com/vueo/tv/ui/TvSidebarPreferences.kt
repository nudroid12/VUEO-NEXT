package com.vueo.tv.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TvSidebarStyle(
    val label: String,
) {
    CLASSIC("Classic"),
    PILL_ICONS("Pill Icons"),
}

internal object TvSidebarStyleState {
    var value by mutableStateOf<TvSidebarStyle?>(null)
}

internal object TvSidebarPreferences {
    private const val PREFS_NAME = "vueo_tv_ui"
    private const val KEY_STYLE = "sidebar_style"

    fun style(context: Context): TvSidebarStyle {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_STYLE, TvSidebarStyle.CLASSIC.name)
        return when (stored) {
            TvSidebarStyle.PILL_ICONS.name -> TvSidebarStyle.PILL_ICONS
            TvSidebarStyle.CLASSIC.name -> TvSidebarStyle.CLASSIC
            // Migrate the two experimental styles from the previous patch.
            "FLOATING_GLASS", "MINIMAL_EDGE" -> TvSidebarStyle.CLASSIC
            else -> TvSidebarStyle.CLASSIC
        }
    }

    fun setStyle(
        context: Context,
        style: TvSidebarStyle,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
        TvSidebarStyleState.value = style
    }
}
