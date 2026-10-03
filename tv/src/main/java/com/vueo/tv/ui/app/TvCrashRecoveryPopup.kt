package com.vueo.tv

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.diagnostics.AppCrashReport
import com.vueo.shared.core.diagnostics.CrashReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos

@Composable
internal fun TvCrashRecoveryPopup(report: AppCrashReport, onClosed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(report.timestampMs) { mutableStateOf(false) }
    var closing by remember(report.timestampMs) { mutableStateOf(false) }
    var message by remember(report.timestampMs) { mutableStateOf<String?>(null) }
    val time = remember(report.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.timestampMs))
    }
    val closeFocus = remember { FocusRequester() }
    val logFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val logScroll = rememberScrollState()
    val scrollStep = with(LocalDensity.current) { 96.dp.roundToPx() }
    var logFocused by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            runCatching { logFocus.requestFocus() }
        }
    }

    val buttonNavigation = Modifier.onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (key.action == KeyEvent.ACTION_DOWN) runCatching { logFocus.requestFocus() }
            true
        } else false
    }.focusProperties { up = logFocus }
    LaunchedEffect(report.timestampMs) {
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { CrashReportStore.dismiss(context.applicationContext, report) }.getOrDefault(false)
            }
            if (saved) onClosed()
            else message = "Couldn't save dismissal. Please try Close again."
            closing = false
        }
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("Previous app crash") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    .border(1.dp, if (logFocused) Color.White.copy(alpha = .65f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .focusRequester(logFocus)
                    .onFocusChanged { logFocused = it.isFocused }
                    .focusProperties { down = detailsFocus }
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        when (key.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (key.action == KeyEvent.ACTION_DOWN) {
                                    val down = key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                    if (down && logScroll.value >= logScroll.maxValue) {
                                        runCatching { detailsFocus.requestFocus() }
                                    } else {
                                        val target = (logScroll.value + if (down) scrollStep else -scrollStep)
                                            .coerceIn(0, logScroll.maxValue)
                                        scope.launch { logScroll.scrollTo(target) }
                                    }
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (key.action == KeyEvent.ACTION_DOWN) runCatching { detailsFocus.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(logScroll)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(report.summary)
                Text(time)
                Text("The app has restarted. You can copy this report to help investigate.")
                if (expanded) Text(report.details)
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }, modifier = buttonNavigation.focusRequester(detailsFocus)) {
                    Text(if (expanded) "Hide details" else "Details")
                }
                TextButton(modifier = buttonNavigation, onClick = {
                    val copied = runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("VUEO Crash Report", "$time\n${report.summary}\n\n${report.details}"))
                    }.isSuccess
                    message = if (copied) "Log copied." else "Couldn't copy log."
                }) { Text("Copy Log") }
                TextButton(onClick = ::close, enabled = !closing, modifier = buttonNavigation.focusRequester(closeFocus)) {
                    Text("Close")
                }
            }
        },
    )
}
