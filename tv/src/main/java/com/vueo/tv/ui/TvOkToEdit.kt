package com.vueo.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

internal data class TvOkToEdit(val readOnly: Boolean, val modifier: Modifier)

@Composable
internal fun rememberTvOkToEdit(enabled: Boolean = true): TvOkToEdit {
    var editing by remember(enabled) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            // Let the field become writable before asking its input session to show.
            withFrameNanos { }
            keyboard?.show()
        }
    }

    return TvOkToEdit(
        readOnly = !editing,
        modifier = Modifier
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { event ->
                val activation = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!enabled || !activation || (editing && event.key != Key.DirectionCenter)) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp) {
                    if (editing) keyboard?.show() else editing = true
                }
                true
            },
    )
}
