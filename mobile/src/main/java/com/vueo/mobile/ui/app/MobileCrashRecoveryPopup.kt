package com.vueo.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vueo.shared.core.diagnostics.AppCrashReport
import com.vueo.shared.core.diagnostics.CrashReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun MobileCrashRecoveryPopup(
    report: AppCrashReport,
    onClosed: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var closing by remember(report.timestampMs) { mutableStateOf(false) }
    var message by remember(report.timestampMs) { mutableStateOf<String?>(null) }
    val time = remember(report.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.timestampMs))
    }

    fun acknowledge(openDiagnostics: Boolean = false) {
        if (closing) return
        closing = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { CrashReportStore.dismiss(context.applicationContext, report) }.getOrDefault(false)
            }
            if (saved) {
                if (openDiagnostics) onOpenDiagnostics() else onClosed()
            } else message = "Couldn't save dismissal. Please try again."
            closing = false
        }
    }

    Dialog(
        onDismissRequest = { acknowledge() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = VueoPalette.SurfaceElevated,
            contentColor = Color.White,
            border = BorderStroke(1.dp, VueoPalette.Stroke),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Previous app crash", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(report.summary, fontSize = 15.sp)
                Text(time, color = VueoPalette.Muted, fontSize = 13.sp)
                Text("The app has restarted. The report is available in Crash Diagnose.", color = VueoPalette.Muted)
                message?.let { Text(it, color = VueoPalette.Muted) }
                Button(
                    onClick = { acknowledge(openDiagnostics = true) },
                    enabled = !closing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VueoPalette.Accent,
                        contentColor = if (VueoPalette.Accent.luminance() > .45f) Color.Black else Color.White,
                    ),
                ) { Text("Open Crash Diagnose") }
                TextButton(
                    onClick = { acknowledge() },
                    enabled = !closing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Close", color = VueoPalette.Muted) }
            }
        }
    }
}
