package com.vueo.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.diagnostics.AppCrashReport
import com.vueo.shared.core.diagnostics.CrashReportStore
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvModalDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun TvCrashRecoveryPopup(
    report: AppCrashReport,
    onClosed: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
    val diagnoseFocus = remember { FocusRequester() }
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

    TvModalDialog(onDismissRequest = { acknowledge() }, initialFocus = closeFocus) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = .74f)),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier.width(580.dp).heightIn(max = 460.dp)
                    .clip(shape).background(TvDesign.SurfaceRaised)
                    .border(1.dp, TvDesign.White.copy(alpha = .14f), shape)
                    .verticalScroll(rememberScrollState()).padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Previous app crash", color = TvDesign.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Text(report.summary, color = TvDesign.White, fontSize = 17.sp)
                Text(time, color = TvDesign.Muted, fontSize = 14.sp)
                Text("The app has restarted. The report is available in Crash Diagnose.", color = TvDesign.Muted, fontSize = 16.sp)
                message?.let { Text(it, color = TvDesign.Muted) }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CrashNoticeAction(
                        label = "Open Crash Diagnose",
                        requester = diagnoseFocus,
                        neighbour = closeFocus,
                        busy = closing,
                        onClick = { acknowledge(openDiagnostics = true) },
                    )
                    CrashNoticeAction(
                        label = "Close",
                        requester = closeFocus,
                        neighbour = diagnoseFocus,
                        busy = closing,
                        onClick = { acknowledge() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashNoticeAction(
    label: String,
    requester: FocusRequester,
    neighbour: FocusRequester,
    busy: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier.focusRequester(requester)
            .focusProperties { left = neighbour; right = neighbour; up = requester; down = requester }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(TvDesign.White.copy(alpha = if (focused) .18f else .06f))
            .border(1.dp, if (focused) TvDesign.Accent else TvDesign.White.copy(alpha = .14f), shape)
            // Keep focus during the short acknowledgement write; ignore repeated presses.
            .clickable { if (!busy) onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TvDesign.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
