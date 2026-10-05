package com.vueo.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal class TvModalFocusHost(val open: () -> Unit, val close: () -> Unit)
internal val LocalTvModalFocusHost = staticCompositionLocalOf<TvModalFocusHost?> { null }

/** Separate input window: remote keys cannot reach the screen behind the popup. */
@Composable
internal fun TvModalDialog(
    onDismissRequest: () -> Unit,
    initialFocus: FocusRequester,
    content: @Composable () -> Unit,
) {
    val focusHost = LocalTvModalFocusHost.current
    DisposableEffect(focusHost) {
        focusHost?.open?.invoke()
        onDispose { focusHost?.close?.invoke() }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // These popups already draw their own full-screen scrim.
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
            onDispose { }
        }
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused, initialFocus) {
            if (windowFocused) {
                // Wait for focus targets to attach; retry only during window entry.
                // Returning from the Android installer runs the same bounded handoff.
                repeat(4) {
                    withFrameNanos { }
                    if (runCatching { initialFocus.requestFocus() }.getOrDefault(false)) {
                        return@LaunchedEffect
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}
