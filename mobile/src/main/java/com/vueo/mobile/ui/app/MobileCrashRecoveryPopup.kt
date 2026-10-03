package com.vueo.mobile.ui

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

@Composable
internal fun MobileCrashRecoveryPopup(report: AppCrashReport, onClosed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(report.timestampMs) { mutableStateOf(false) }
    var closing by remember(report.timestampMs) { mutableStateOf(false) }
    var message by remember(report.timestampMs) { mutableStateOf<String?>(null) }
    val time = remember(report.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.timestampMs))
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(report.summary)
                Text(time)
                Text("The app has restarted. You can copy this report to help investigate.")
                if (expanded) Text(
                    if (report.details.length > 12_000) report.details.take(12_000) +
                        "\n\n[Preview shortened. Copy Log includes the full stored report.]"
                    else report.details
                )
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide details" else "Details")
                }
                TextButton(onClick = {
                    val copied = runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("VUEO Crash Report", "$time\n${report.summary}\n\n${report.details}"))
                    }.isSuccess
                    message = if (copied) "Log copied." else "Couldn't copy log."
                }) { Text("Copy Log") }
                TextButton(onClick = ::close, enabled = !closing) {
                    Text("Close")
                }
            }
        },
    )
}
