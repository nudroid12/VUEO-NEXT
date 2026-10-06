package com.vueo.tv.settings

import com.vueo.shared.core.plugin.ProviderDiagnosticProgress

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.ProviderHealthRecord
import com.vueo.shared.core.plugin.ProviderHealthStatus
import com.vueo.tv.ui.TvDesign

@Composable
internal fun TvProviderDiagnosticDialog(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    health: ProviderHealthRecord?,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var rawExpanded by remember(repository.manifestUrl, provider.id) { mutableStateOf(false) }
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()

    fun closeAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    AlertDialog(
        onDismissRequest = ::closeAndRestore,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Provider Diagnostic")
                Text(
                    text = "${provider.name} • v${provider.version}",
                    color = TvDesign.Muted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (health == null) {
                    Text(
                        text = "No diagnostic captured yet. Run source discovery for this provider, then reopen diagnostics.",
                        color = TvDesign.Muted,
                        fontSize = 12.sp,
                    )
                } else {
                    providerRequestLabelOrNull(health)?.let { DiagnosticLine("Request", it) }
                    DiagnosticLine(
                        "Failure",
                        "${providerFailureStage(health, providerCodeReady)} • ${providerFailureCategory(health)}",
                    )
                    health.errorType?.takeIf { it.isNotBlank() }?.let {
                        DiagnosticLine("Error type", sanitizeDiagnosticText(it))
                    }
                    providerHttpStatus(health)?.let { DiagnosticLine("HTTP", it) }
                    providerRelevantTimingLabel(health)?.let { DiagnosticLine("Timing", it) }
                    ProviderDiagnosticProgress.lastStage(health.logs)?.let { DiagnosticLine("Last stage", sanitizeDiagnosticText(it)) }
                    ProviderDiagnosticProgress.pending(health.logs).takeIf { it.isNotEmpty() }?.let {
                        DiagnosticLine("Pending requests", it.joinToString("\n") { line -> sanitizeDiagnosticText(line) })
                    }
                    ProviderDiagnosticProgress.timeline(health.logs).takeIf { it.isNotEmpty() }?.let {
                        DiagnosticLine("Timeline", it.joinToString("\n") { line -> sanitizeDiagnosticText(line) })
                    }
                    if (health.status == ProviderHealthStatus.NO_RESULTS || health.streamCount == 0) {
                        DiagnosticLine(
                            "Result",
                            "${health.streamCount} playable source${if (health.streamCount == 1) "" else "s"}",
                        )
                    }
                    if (!providerCodeReady) {
                        DiagnosticLine("Provider code", "Missing or not ready")
                    }
                    health.error?.takeIf { it.isNotBlank() }?.let {
                        DiagnosticLine("Error", sanitizeDiagnosticText(it))
                    }

                    Surface(
                        color = TvDesign.Surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Likely cause", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = providerLikelyCause(health, providerCodeReady),
                                color = TvDesign.Muted,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    TextButton(onClick = { rawExpanded = !rawExpanded }) {
                        Text("Raw technical log  ${if (rawExpanded) "v" else ">"}")
                    }
                    if (rawExpanded) {
                        Text(
                            text = providerRawDiagnosticLog(health),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TvDesign.Muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (health != null) {
                TextButton(
                    onClick = {
                        copyProviderDiagnostic(
                            context = context,
                            text = providerDiagnosticFullLog(
                                repository = repository,
                                provider = provider,
                                health = health,
                                currentlyEnabled = currentlyEnabled,
                                providerCodeReady = providerCodeReady,
                            ),
                        )
                    },
                ) {
                    Text("Copy Debug Log")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = ::closeAndRestore) { Text("Close") }
        },
    )
}


@Composable
internal fun TvRuntimeDiagnosticsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val summaryFocus = remember { FocusRequester() }
    val rawFocus = remember { FocusRequester() }
    val logFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    val logScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 96.dp.roundToPx() }
    var logFocused by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var diagnosticText by remember { mutableStateOf("Loading diagnostics…") }
    var saving by remember { mutableStateOf(false) }
    var diagnosticsEnabled by remember { mutableStateOf(RuntimeDiagnostics.isEnabled(context.applicationContext)) }

    LaunchedEffect(showRaw, diagnosticsEnabled) {
        diagnosticText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (showRaw) RuntimeDiagnostics.exportRaw(context.applicationContext)
            else RuntimeDiagnostics.exportSummary(context.applicationContext)
        }
        logScroll.scrollTo(0)
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { summaryFocus.requestFocus() }
    }

    val buttonNavigation = Modifier.focusProperties { up = logFocus }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (key.action == KeyEvent.ACTION_DOWN) runCatching { logFocus.requestFocus() }
                true
            } else false
        }

    fun closeAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    AlertDialog(
        onDismissRequest = ::closeAndRestore,
        title = { Text("Crash Diagnostics") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crash Diagnostics", fontWeight = FontWeight.Bold)
                        Text(
                            if (diagnosticsEnabled) "ON • crash/native and stall evidence active" else "OFF • crash collector and stall watchdog disabled",
                            color = TvDesign.Muted,
                            fontSize = 10.5.sp,
                        )
                    }
                    Switch(
                        checked = diagnosticsEnabled,
                        onCheckedChange = { value ->
                            RuntimeDiagnostics.setEnabled(context.applicationContext, value)
                            diagnosticsEnabled = value
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        modifier = Modifier
                            .focusRequester(summaryFocus)
                            .focusProperties { right = rawFocus; down = logFocus },
                        onClick = { showRaw = false },
                    ) {
                        Text(
                            "Summary",
                            fontWeight = if (!showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    TextButton(
                        modifier = Modifier
                            .focusRequester(rawFocus)
                            .focusProperties { left = summaryFocus; down = logFocus },
                        onClick = { showRaw = true },
                    ) {
                        Text(
                            "Raw",
                            fontWeight = if (showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                Text(
                    text = if (showRaw) {
                        "Raw: chronological events. Save Log exports the complete ZIP, including native tombstone evidence when Android provides it."
                    } else {
                        "Summary: provider phases, QuickJS/native boundaries, memory and recent failures."
                    },
                    color = TvDesign.Muted,
                    fontSize = 11.sp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .border(1.dp, if (logFocused) Color.White.copy(alpha = .65f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .focusRequester(logFocus)
                        .onFocusChanged { logFocused = it.isFocused }
                        .focusProperties { up = if (showRaw) rawFocus else summaryFocus; down = copyFocus }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        val down = key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                        if (down && logScroll.value >= logScroll.maxValue) {
                                            runCatching { copyFocus.requestFocus() }
                                        } else if (!down && logScroll.value <= 0) {
                                            runCatching { (if (showRaw) rawFocus else summaryFocus).requestFocus() }
                                        } else {
                                            val target = (logScroll.value + if (down) scrollStep else -scrollStep)
                                                .coerceIn(0, logScroll.maxValue)
                                            scope.launch { logScroll.scrollTo(target) }
                                        }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) runCatching { copyFocus.requestFocus() }
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
                    Text(
                        text = if (diagnosticText.length > 24_000) {
                            "[Recent preview. Copy Log and Save Log use the full selected log.]\n\n" +
                                diagnosticText.takeLast(24_000)
                        } else diagnosticText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = TvDesign.Muted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = buttonNavigation.focusRequester(copyFocus),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText(
                            if (showRaw) "VUEO raw diagnostic" else "VUEO diagnostic summary",
                            diagnosticText,
                        )
                    )
                    Toast.makeText(context, "Diagnostic log copied", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Copy Log")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    modifier = buttonNavigation,
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { RuntimeDiagnostics.saveBundle(context.applicationContext) }
                            }
                            saving = false
                            result.onSuccess { saved ->
                                Toast.makeText(
                                    context,
                                    "Saved ${saved.displayName} to ${saved.location}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Save failed: ${error.javaClass.simpleName}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(if (saving) "Saving…" else "Save Log")
                }
                TextButton(
                    modifier = buttonNavigation,
                    onClick = {
                        scope.launch {
                            diagnosticText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                RuntimeDiagnostics.clear(context.applicationContext)
                                if (showRaw) RuntimeDiagnostics.exportRaw(context.applicationContext)
                                else RuntimeDiagnostics.exportSummary(context.applicationContext)
                            }
                        }
                    },
                ) {
                    Text("Clear Log")
                }
                TextButton(modifier = buttonNavigation, onClick = ::closeAndRestore) { Text("Close") }
            }
        },
    )
}

@Composable
internal fun TvPerformanceDiagnosticsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val scope = rememberCoroutineScope()
    val tabScroll = rememberScrollState()
    val logScroll = rememberScrollState()
    val tabFocusers = remember { PerformanceDiagnostics.Tab.entries.map { FocusRequester() } }
    val recordFocus = remember { FocusRequester() }
    val logFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    var selectedTab by remember { mutableStateOf(PerformanceDiagnostics.Tab.FULL) }
    var diagnosticsEnabled by remember { mutableStateOf(PerformanceDiagnostics.isEnabled(context.applicationContext)) }
    var recording by remember { mutableStateOf(PerformanceDiagnostics.isRecording()) }
    var diagnosticText by remember { mutableStateOf(PerformanceDiagnostics.preview(selectedTab)) }
    var saving by remember { mutableStateOf(false) }
    var logFocused by remember { mutableStateOf(false) }
    var focusedTabIndex by remember { mutableStateOf(0) }
    val scrollStep = with(LocalDensity.current) { 96.dp.roundToPx() }

    fun closeAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    LaunchedEffect(selectedTab, diagnosticsEnabled, recording) {
        diagnosticText = PerformanceDiagnostics.preview(selectedTab)
        logScroll.scrollTo(0)
        if (recording) {
            while (true) {
                kotlinx.coroutines.delay(1_500L)
                diagnosticText = PerformanceDiagnostics.preview(selectedTab)
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { tabFocusers.first().requestFocus() }
    }

    val bottomButtonNavigation = Modifier
        .focusProperties { up = logFocus }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (key.action == KeyEvent.ACTION_DOWN) runCatching { logFocus.requestFocus() }
                true
            } else false
        }

    AlertDialog(
        onDismissRequest = ::closeAndRestore,
        title = { Text("Performance Diagnostics") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Performance recorder", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                !diagnosticsEnabled -> "OFF • no sampler, frame probe or event buffering"
                                recording -> "ON • recording runtime activity"
                                else -> "ON • armed, not recording"
                            },
                            color = TvDesign.Muted,
                            fontSize = 10.5.sp,
                        )
                    }
                    Switch(
                        checked = diagnosticsEnabled,
                        onCheckedChange = { value ->
                            PerformanceDiagnostics.setEnabled(context.applicationContext, value)
                            diagnosticsEnabled = value
                            recording = PerformanceDiagnostics.isRecording()
                            diagnosticText = PerformanceDiagnostics.preview(selectedTab)
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        modifier = Modifier
                            .focusRequester(recordFocus)
                            .focusProperties { down = tabFocusers.first() },
                        enabled = diagnosticsEnabled,
                        onClick = {
                            if (recording) PerformanceDiagnostics.stopRecording()
                            else PerformanceDiagnostics.startRecording(context.applicationContext)
                            recording = PerformanceDiagnostics.isRecording()
                            diagnosticText = PerformanceDiagnostics.preview(selectedTab)
                        },
                    ) {
                        Text(if (recording) "Stop Recording" else "Start Recording")
                    }
                    Text(
                        "Recording stays in memory. Disk is touched only when Save Log is pressed.",
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabScroll),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PerformanceDiagnostics.Tab.entries.forEachIndexed { index, tab ->
                        TextButton(
                            modifier = Modifier
                                .focusRequester(tabFocusers[index])
                                .onFocusChanged { state ->
                                    if (state.isFocused) focusedTabIndex = index
                                }
                                .focusProperties {
                                    up = recordFocus
                                    down = logFocus
                                },
                            onClick = { selectedTab = tab },
                        ) {
                            Text(
                                tab.label,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .border(1.dp, if (logFocused) Color.White.copy(alpha = .65f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .focusRequester(logFocus)
                        .onFocusChanged { logFocused = it.isFocused }
                        .focusProperties {
                            up = tabFocusers[focusedTabIndex.coerceIn(tabFocusers.indices)]
                            down = copyFocus
                        }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        val down = key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                        if (down && logScroll.value >= logScroll.maxValue) {
                                            runCatching { copyFocus.requestFocus() }
                                        } else if (!down && logScroll.value <= 0) {
                                            runCatching {
                                                tabFocusers[focusedTabIndex.coerceIn(tabFocusers.indices)].requestFocus()
                                            }
                                        } else {
                                            val target = (logScroll.value + if (down) scrollStep else -scrollStep)
                                                .coerceIn(0, logScroll.maxValue)
                                            scope.launch { logScroll.scrollTo(target) }
                                        }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) runCatching { copyFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .verticalScroll(logScroll)
                        .padding(8.dp),
                ) {
                    Text(
                        text = if (diagnosticText.length > 30_000) {
                            "[Recent preview. Copy and Save use the complete selected log.]\n\n" + diagnosticText.takeLast(30_000)
                        } else diagnosticText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = TvDesign.Muted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier
                    .focusRequester(copyFocus)
                    .then(bottomButtonNavigation),
                onClick = {
                    val fullText = PerformanceDiagnostics.export(selectedTab)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("VUEO performance ${selectedTab.label}", fullText))
                    Toast.makeText(context, "${selectedTab.label} log copied", Toast.LENGTH_SHORT).show()
                },
            ) { Text("Copy") }
        },
        dismissButton = {
            Row {
                TextButton(
                    modifier = bottomButtonNavigation,
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { PerformanceDiagnostics.saveBundle(context.applicationContext) }
                            }
                            saving = false
                            result.onSuccess { saved ->
                                Toast.makeText(context, "Saved ${saved.displayName} to ${saved.location}", Toast.LENGTH_LONG).show()
                            }.onFailure { error ->
                                Toast.makeText(context, "Save failed: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text(if (saving) "Saving…" else "Save Log") }
                TextButton(
                    modifier = bottomButtonNavigation,
                    onClick = {
                        PerformanceDiagnostics.clear()
                        diagnosticText = PerformanceDiagnostics.preview(selectedTab)
                    },
                ) { Text("Clear") }
                TextButton(modifier = bottomButtonNavigation, onClick = ::closeAndRestore) { Text("Close") }
            }
        },
    )
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(value, color = TvDesign.Muted, fontSize = 11.sp)
    }
}

private fun providerRequestLabelOrNull(health: ProviderHealthRecord): String? {
    val parts = buildList {
        health.requestMediaType?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
        health.requestTmdbId?.takeIf { it.isNotBlank() }?.let { add("TMDB $it") }
        if (health.requestSeason != null && health.requestEpisode != null) {
            add("S${health.requestSeason.toString().padStart(2, '0')} E${health.requestEpisode.toString().padStart(2, '0')}")
        }
    }
    return parts.joinToString(" • ").takeIf { it.isNotBlank() }
}

private fun providerRelevantTimingLabel(health: ProviderHealthRecord): String? {
    val elapsed = health.responseMs?.let { "$it ms" }
    val timeout = health.timeoutMs?.let { "timeout $it ms" }
    return when {
        health.status == ProviderHealthStatus.TIMEOUT && elapsed != null && timeout != null -> "$elapsed • $timeout"
        health.status == ProviderHealthStatus.TIMEOUT && timeout != null -> timeout
        health.status in setOf(
            ProviderHealthStatus.SLOW,
            ProviderHealthStatus.FAILED,
            ProviderHealthStatus.UNAVAILABLE,
            ProviderHealthStatus.BLOCKED,
        ) && elapsed != null -> elapsed
        else -> null
    }
}

private fun providerFailureStage(health: ProviderHealthRecord, providerCodeReady: Boolean): String {
    val error = health.error.orEmpty().lowercase()
    return when {
        !providerCodeReady || "code is not installed" in error -> "Provider preparation"
        health.status == ProviderHealthStatus.NEEDS_SETUP -> "Provider configuration"
        health.status == ProviderHealthStatus.UNAVAILABLE || health.status == ProviderHealthStatus.BLOCKED -> "Network / upstream access"
        health.status == ProviderHealthStatus.TIMEOUT -> "Provider execution"
        health.status == ProviderHealthStatus.NO_RESULTS -> "Result extraction"
        else -> "Source discovery"
    }
}

private fun providerFailureCategory(health: ProviderHealthRecord): String {
    val error = health.error.orEmpty().lowercase()
    return when {
        health.status == ProviderHealthStatus.ONLINE -> "Healthy"
        health.status == ProviderHealthStatus.SLOW -> "Slow response"
        health.status == ProviderHealthStatus.NO_RESULTS -> "No playable sources"
        health.status == ProviderHealthStatus.NEEDS_SETUP -> "Configuration required"
        health.status == ProviderHealthStatus.TIMEOUT -> "Execution timeout"
        health.status == ProviderHealthStatus.UNAVAILABLE -> "Host unavailable / DNS"
        health.status == ProviderHealthStatus.BLOCKED -> "Upstream blocked request"
        "not found" in error && health.requestSeason != null -> "Episode or source not found"
        "status 404" in error || "http 404" in error -> "HTTP not found"
        "status 429" in error || "http 429" in error -> "Rate limited"
        "status 5" in error || "http 5" in error -> "Upstream server error"
        health.status == ProviderHealthStatus.FAILED -> "Provider execution failed"
        else -> "Unknown"
    }
}

private fun providerLikelyCause(health: ProviderHealthRecord, providerCodeReady: Boolean): String {
    val error = health.error.orEmpty().lowercase()
    val http = providerHttpStatus(health)
    return when {
        !providerCodeReady || "code is not installed" in error ->
            "Provider code is missing or not ready locally. Refresh the repository and run source discovery again."
        health.status == ProviderHealthStatus.NEEDS_SETUP ->
            "Provider configuration is incomplete. Complete required setup before source discovery."
        health.status == ProviderHealthStatus.TIMEOUT ->
            "Provider execution exceeded the captured timeout. Check Last stage, Pending requests and Timeline for the last observed progress."
        health.status == ProviderHealthStatus.UNAVAILABLE ->
            "The captured run could not reach the upstream host. Inspect DNS, connection or host-resolution evidence."
        health.status == ProviderHealthStatus.BLOCKED ->
            "The captured run indicates upstream access was blocked. Inspect the HTTP/error evidence for the rejection."
        http == "404" ->
            "The captured upstream request returned HTTP 404. Check route generation, title mapping, episode mapping or an upstream path change."
        http == "429" ->
            "The captured upstream request returned HTTP 429 rate limiting. Request pacing or caching may need adjustment."
        http?.startsWith("5") == true ->
            "The captured upstream request returned a server-side HTTP error. Verify whether the upstream endpoint is failing."
        "not found" in error && health.requestSeason != null ->
            "The provider could not resolve the requested episode/source. Check episode mapping, URL construction and extraction selectors."
        health.status == ProviderHealthStatus.NO_RESULTS ->
            "Provider execution completed but returned zero playable sources. Check title mapping and extraction selectors against the current upstream response."
        health.status == ProviderHealthStatus.FAILED && !health.error.isNullOrBlank() ->
            "Provider execution failed with the captured error above. Use that error and the raw log to identify the failing request or parser step."
        health.status == ProviderHealthStatus.SLOW ->
            "Provider returned a slow response for the captured request. Timing evidence is shown above."
        health.status == ProviderHealthStatus.ONLINE ->
            "No provider failure was captured in the latest run."
        else ->
            "Cause not determined from captured evidence. Run source discovery again and inspect the raw technical log."
    }
}

private fun providerHttpStatus(health: ProviderHealthRecord): String? {
    val combined = buildString {
        health.error?.let { append(it).append('\n') }
        health.logs.forEach { append(it).append('\n') }
    }
    val regexes = listOf(
        Regex("(?i)(?:http|status|status code|request failed with status)\\s*[:=]?\\s*(\\d{3})"),
        Regex("(?i)\\b(4\\d{2}|5\\d{2})\\b"),
    )
    return regexes.asSequence()
        .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1) }
        .firstOrNull()
}

private fun providerDiagnosticFullLog(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    health: ProviderHealthRecord,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
): String = buildString {
    appendLine("VUEO Provider Debug Log")
    appendLine("Provider: ${provider.name} v${provider.version}")
    appendLine("Status: ${if (currentlyEnabled) health.status.label else "Disabled (last ${health.status.label})"}")
    providerRequestLabelOrNull(health)?.let { appendLine("Request: $it") }
    appendLine("Failure: ${providerFailureStage(health, providerCodeReady)} • ${providerFailureCategory(health)}")
    health.errorType?.takeIf { it.isNotBlank() }?.let { appendLine("Error type: ${sanitizeDiagnosticText(it)}") }
    providerHttpStatus(health)?.let { appendLine("HTTP: $it") }
    providerRelevantTimingLabel(health)?.let { appendLine("Timing: $it") }
    ProviderDiagnosticProgress.lastStage(health.logs)?.let { appendLine("Last stage: ${sanitizeDiagnosticText(it)}") }
    ProviderDiagnosticProgress.pending(health.logs).forEach { appendLine("Pending request: ${sanitizeDiagnosticText(it)}") }
    ProviderDiagnosticProgress.timeline(health.logs).takeIf { it.isNotEmpty() }?.let { timeline ->
        appendLine("Timeline (recent events)")
        timeline.forEach { appendLine(sanitizeDiagnosticText(it)) }
    }
    if (health.status == ProviderHealthStatus.NO_RESULTS || health.streamCount == 0) {
        appendLine("Result: ${health.streamCount} playable source${if (health.streamCount == 1) "" else "s"}")
    }
    if (!providerCodeReady) appendLine("Provider code: Missing or not ready")
    health.error?.takeIf { it.isNotBlank() }?.let { appendLine("Error: ${sanitizeDiagnosticText(it)}") }
    appendLine("Likely cause: ${providerLikelyCause(health, providerCodeReady)}")
    appendLine()
    appendLine("Raw technical log (sanitized)")
    append(providerRawDiagnosticLog(health))
}

private fun providerRawDiagnosticLog(health: ProviderHealthRecord): String {
    val lines = buildList {
        health.error?.takeIf { it.isNotBlank() }?.let { add("ERROR: $it") }
        addAll(health.logs)
    }
    return if (lines.isEmpty()) {
        "No raw provider log was captured for this run."
    } else {
        lines.joinToString("\n") { sanitizeDiagnosticText(it) }
    }
}

private fun sanitizeDiagnosticText(raw: String): String {
    var text = raw
    text = text.replace(
        Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\\s*[:=]\\s*([^\\s,;]+)"),
    ) { match -> "${match.groupValues[1]}=<redacted>" }
    text = text.replace(
        Regex("https?://[^\\s\\]\\[<>\\\"']+"),
    ) { match -> sanitizeDiagnosticUrl(match.value) }
    return text.take(12_000)
}

private fun sanitizeDiagnosticUrl(url: String): String {
    val queryIndex = url.indexOf('?')
    if (queryIndex < 0) return url
    val base = url.substring(0, queryIndex)
    val rawQuery = url.substring(queryIndex + 1)
    if (rawQuery.isBlank()) return base
    val safeQuery = rawQuery
        .split('&')
        .take(12)
        .mapNotNull { part ->
            val key = part.substringBefore('=').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "$key=<redacted>"
        }
        .joinToString("&")
    return if (safeQuery.isBlank()) base else "$base?$safeQuery"
}

private fun copyProviderDiagnostic(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("VUEO provider diagnostic", text))
    Toast.makeText(context, "Debug log copied", Toast.LENGTH_SHORT).show()
}
