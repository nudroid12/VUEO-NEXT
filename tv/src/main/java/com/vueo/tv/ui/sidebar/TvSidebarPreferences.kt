package com.vueo.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TvSidebarStyle(
    val label: String,
) {
    CLASSIC("Sidebar"),
    PILL_ICONS("Topbar"),
}

internal object TvSidebarStyleState {
    var value by mutableStateOf<TvSidebarStyle?>(null)
    var hideSidebar by mutableStateOf<Boolean?>(null)
}

internal object TvSidebarPreferences {
    private const val PREFS_NAME = "vueo_tv_ui"
    private const val KEY_STYLE = "sidebar_style"
    private const val KEY_HIDE_SIDEBAR = "hide_sidebar"

    fun hideSidebar(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_SIDEBAR, false)

    fun setHideSidebar(context: Context, hidden: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_SIDEBAR, hidden)
            .apply()
        TvSidebarStyleState.hideSidebar = hidden
    }

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

@Composable
internal fun tvSidebarContentStartPadding(
    classic: Dp,
    pill: Dp = 28.dp,
): Dp {
    val context = LocalContext.current
    val style = TvSidebarStyleState.value ?: TvSidebarPreferences.style(context)
    val hidden = TvSidebarStyleState.hideSidebar ?: TvSidebarPreferences.hideSidebar(context)
    return if (style == TvSidebarStyle.PILL_ICONS || (style == TvSidebarStyle.CLASSIC && hidden)) pill else classic
}

@Composable
internal fun tvSidebarHomeRowsViewportFraction(
    classic: Float,
    pill: Float,
): Float {
    val context = LocalContext.current
    val style = TvSidebarStyleState.value ?: TvSidebarPreferences.style(context)
    return if (style == TvSidebarStyle.PILL_ICONS) pill else classic
}

@Composable
internal fun tvSidebarIsPillMode(): Boolean {
    val context = LocalContext.current
    val style = TvSidebarStyleState.value ?: TvSidebarPreferences.style(context)
    return style == TvSidebarStyle.PILL_ICONS
}

@Composable
internal fun tvSidebarIsHiddenClassic(): Boolean {
    val context = LocalContext.current
    val style = TvSidebarStyleState.value ?: TvSidebarPreferences.style(context)
    val hidden = TvSidebarStyleState.hideSidebar ?: TvSidebarPreferences.hideSidebar(context)
    return style == TvSidebarStyle.CLASSIC && hidden
}
