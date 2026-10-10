package com.vueo.tv.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Popup buttons can finish an action after the tuned exit, once only. */
internal val LocalTvMotionFinish = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { it() } }

@Composable
internal fun TvMotionDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    if (!TvMotionTuning.active(TvMotionGroup.PANEL) || TvMotionTuning.reduceMotion) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
        return
    }
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    var completion by remember { mutableStateOf<(() -> Unit)?>(null) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    fun finish(action: () -> Unit) {
        if (completion != null) return
        completion = action
        visibility.targetState = false
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState, completion) {
        if (visibility.isIdle && !visibility.currentState) {
            val action = completion
            completion = null
            action?.invoke()
        }
    }
    Dialog(onDismissRequest = { finish { latestDismiss() } }, properties = properties) {
        // Avoid stacking Android's window animation over the Compose trial.
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val originalAnimations = window?.attributes?.windowAnimations ?: 0
            window?.setWindowAnimations(0)
            onDispose { window?.setWindowAnimations(originalAnimations) }
        }
        CompositionLocalProvider(LocalTvMotionFinish provides ::finish) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = tvPanelEnter(), exit = tvPanelExit(),
            ) { content() }
        }
    }
}

@Composable
internal fun TvMotionAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
) {
    if (!TvMotionTuning.active(TvMotionGroup.PANEL) || TvMotionTuning.reduceMotion) {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
            dismissButton = dismissButton, title = title, text = text,
            containerColor = containerColor, titleContentColor = titleContentColor)
        return
    }
    TvMotionDialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(480.dp).background(containerColor, RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CompositionLocalProvider(LocalContentColor provides titleContentColor) { title?.invoke() }
            text?.invoke()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton?.invoke(); confirmButton()
            }
        }
    }
}
