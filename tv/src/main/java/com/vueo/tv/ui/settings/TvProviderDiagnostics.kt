package com.vueo.tv.settings

import com.vueo.shared.core.plugin.ProviderDiagnosticProgress
import androidx.activity.compose.BackHandler

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.diagnostics.DiagnosticsViewer
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
    val scope = rememberCoroutineScope()
    val toggleFocus = remember { FocusRequester() }
    val summaryFocus = remember { FocusRequester() }
    val rawFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val logFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    val logScroll = rememberScrollState()
    val scrollStep = with(LocalDensity.current) { 112.dp.roundToPx() }
    var showRaw by remember { mutableStateOf(false) }
    var diagnosticText by remember { mutableStateOf("Loading diagnostics…") }
    var saving by remember { mutableStateOf(false) }
    var diagnosticsEnabled by remember {
        mutableStateOf(RuntimeDiagnostics.isEnabled(context.applicationContext))
    }
    var searchQuery by remember { mutableStateOf("") }
    var logFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }

    fun closeAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    suspend fun loadDiagnostics(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (showRaw) RuntimeDiagnostics.exportRaw(context.applicationContext)
        else RuntimeDiagnostics.exportSummary(context.applicationContext)
    }

    LaunchedEffect(showRaw, diagnosticsEnabled) {
        diagnosticText = loadDiagnostics()
        logScroll.scrollTo(0)
        if (diagnosticsEnabled) {
            while (true) {
                kotlinx.coroutines.delay(1_500L)
                diagnosticText = loadDiagnostics()
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { summaryFocus.requestFocus() }
    }

    val visibleLog = remember(diagnosticText, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            diagnosticText
        } else {
            diagnosticText
                .lineSequence()
                .filter { line -> line.contains(query, ignoreCase = true) }
                .joinToString("\n")
                .ifBlank { "No diagnostic lines match \"$query\"." }
        }
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

    BackHandler(onBack = ::closeAndRestore)

    Dialog(
        onDismissRequest = ::closeAndRestore,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TvDesign.Black,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 44.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::closeAndRestore,
                        modifier = Modifier.focusProperties { right = toggleFocus },
                    ) {
                        Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Crash Diagnostics",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (diagnosticsEnabled) {
                                "ON • recording crash/stall evidence"
                            } else {
                                "OFF • diagnostics inactive"
                            },
                            color = TvDesign.Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (diagnosticsEnabled) "ON" else "OFF",
                        color = if (diagnosticsEnabled) TvDesign.Accent else TvDesign.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Switch(
                        checked = diagnosticsEnabled,
                        onCheckedChange = { value ->
                            RuntimeDiagnostics.setEnabled(context.applicationContext, value)
                            diagnosticsEnabled = RuntimeDiagnostics.isEnabled(context.applicationContext)
                        },
                        modifier = Modifier
                            .focusRequester(toggleFocus)
                            .focusProperties { down = summaryFocus },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TvDesign.Surface, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(summaryFocus)
                            .background(
                                if (!showRaw) TvDesign.SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .focusProperties {
                                up = toggleFocus
                                right = rawFocus
                                down = searchFocus
                            },
                        onClick = { showRaw = false },
                    ) {
                        Text(
                            if (!showRaw) "✓  Summary" else "Summary",
                            fontWeight = if (!showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(rawFocus)
                            .background(
                                if (showRaw) TvDesign.SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .focusProperties {
                                up = toggleFocus
                                left = summaryFocus
                                down = searchFocus
                            },
                        onClick = { showRaw = true },
                    ) {
                        Text(
                            if (showRaw) "✓  Raw" else "Raw",
                            fontWeight = if (showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(TvDesign.Surface, RoundedCornerShape(8.dp))
                        .border(
                            width = if (searchFocused) 2.dp else 1.dp,
                            color = if (searchFocused) TvDesign.Focus else TvDesign.White.copy(alpha = .14f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", color = TvDesign.Muted, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus)
                            .onFocusChanged { searchFocused = it.isFocused }
                            .focusProperties {
                                up = if (showRaw) rawFocus else summaryFocus
                                down = copyFocus
                            },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TvDesign.White,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TvDesign.Accent),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "Search diagnostic…",
                                        color = TvDesign.Muted,
                                        fontSize = 13.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.focusProperties { left = searchFocus; down = copyFocus },
                        ) {
                            Text("×", fontSize = 20.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(TvDesign.Surface, RoundedCornerShape(10.dp))
                        .border(
                            2.dp,
                            if (logFocused) TvDesign.Focus.copy(alpha = .9f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .focusRequester(logFocus)
                        .onFocusChanged { logFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        val down = key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                        val target = (logScroll.value + if (down) scrollStep else -scrollStep)
                                            .coerceIn(0, logScroll.maxValue)
                                        scope.launch { logScroll.scrollTo(target) }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        runCatching { searchFocus.requestFocus() }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .verticalScroll(logScroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (visibleLog.length > 24_000) {
                            "[Recent preview. Copy and Save use the full selected log.]\n\n" +
                                visibleLog.takeLast(24_000)
                        } else visibleLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TvDesign.White.copy(alpha = .88f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(copyFocus)
                            .then(bottomButtonNavigation),
                        onClick = {
                            val fullText = if (showRaw) {
                                RuntimeDiagnostics.exportRaw(context.applicationContext)
                            } else {
                                RuntimeDiagnostics.exportSummary(context.applicationContext)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    if (showRaw) "VUEO raw diagnostic" else "VUEO diagnostic summary",
                                    fullText,
                                )
                            )
                            Toast.makeText(
                                context,
                                if (showRaw) "Raw diagnostic copied" else "Diagnostic summary copied",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) { Text("Copy") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
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
                    ) { Text(if (saving) "Saving…" else "Save") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
                        onClick = {
                            scope.launch {
                                diagnosticText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    RuntimeDiagnostics.clear(context.applicationContext)
                                    if (showRaw) RuntimeDiagnostics.exportRaw(context.applicationContext)
                                    else RuntimeDiagnostics.exportSummary(context.applicationContext)
                                }
                                logScroll.scrollTo(0)
                            }
                        },
                    ) { Text("Clear") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
                        onClick = ::closeAndRestore,
                    ) { Text("Close") }
                }
            }
        }
    }
}

@Composable
internal fun TvPerformanceDiagnosticsDialog(
    onDismiss: () -> Unit,
    providerMode: Boolean = false,
) {
    val context = LocalContext.current
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val scope = rememberCoroutineScope()
    val diagnostics = remember(providerMode) { DiagnosticsViewer(providerMode) }
    val tabs = diagnostics.tabs
    val logScroll = rememberScrollState()
    val toggleFocus = remember { FocusRequester() }
    val summaryFocus = remember { FocusRequester() }
    val rawFocus = remember { FocusRequester() }
    val tabFocusers = remember { tabs.map { FocusRequester() } }
    val searchFocus = remember { FocusRequester() }
    val logFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    var selectedTab by remember { mutableStateOf(tabs.first()) }
    var showRaw by remember { mutableStateOf(false) }
    var diagnosticsEnabled by remember {
        mutableStateOf(diagnostics.isEnabled(context.applicationContext))
    }
    var diagnosticText by remember {
        mutableStateOf(diagnostics.previewSummary(selectedTab))
    }
    var searchQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var logFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var focusedTabIndex by remember { mutableStateOf(0) }
    val scrollStep = with(LocalDensity.current) { 112.dp.roundToPx() }

    fun closeAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    fun refreshPreview(): String =
        if (showRaw) diagnostics.previewRaw(selectedTab, maxEvents = 320)
        else diagnostics.previewSummary(selectedTab)

    LaunchedEffect(selectedTab, showRaw, diagnosticsEnabled) {
        diagnosticText = refreshPreview()
        logScroll.scrollTo(0)
        if (diagnosticsEnabled) {
            while (true) {
                kotlinx.coroutines.delay(1_500L)
                diagnosticText = refreshPreview()
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { summaryFocus.requestFocus() }
    }

    val visibleLog = remember(diagnosticText, searchQuery, providerMode, showRaw) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            diagnosticText
        } else if (providerMode && !showRaw) {
            diagnosticText.split("\n\n")
                .filter { block -> block.contains(query, ignoreCase = true) }
                .joinToString("\n\n")
                .ifBlank { "No provider summaries match \"$query\"." }
        } else {
            diagnosticText
                .lineSequence()
                .filter { line -> line.contains(query, ignoreCase = true) }
                .joinToString(if (providerMode) "\n\n" else "\n")
                .ifBlank { "No diagnostic lines match \"$query\"." }
        }
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

    BackHandler(onBack = ::closeAndRestore)

    Dialog(
        onDismissRequest = ::closeAndRestore,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TvDesign.Black,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 44.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::closeAndRestore,
                        modifier = Modifier.focusProperties { right = toggleFocus },
                    ) {
                        Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            diagnostics.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (diagnosticsEnabled) {
                                if (providerMode) "ON • recording provider activity" else "ON • recording runtime activity"
                            } else {
                                "OFF • diagnostics inactive • recorded log retained"
                            },
                            color = TvDesign.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        if (diagnosticsEnabled) "ON" else "OFF",
                        color = if (diagnosticsEnabled) TvDesign.Accent else TvDesign.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Switch(
                        checked = diagnosticsEnabled,
                        onCheckedChange = { value ->
                            diagnostics.setEnabled(context.applicationContext, value)
                            diagnosticsEnabled = diagnostics.isCollecting()
                            diagnosticText = refreshPreview()
                        },
                        modifier = Modifier
                            .focusRequester(toggleFocus)
                            .focusProperties { down = summaryFocus },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TvDesign.Surface, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(summaryFocus)
                            .background(
                                if (!showRaw) TvDesign.SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .focusProperties {
                                up = toggleFocus
                                right = rawFocus
                                down = tabFocusers.first()
                            },
                        onClick = { showRaw = false },
                    ) {
                        Text(
                            if (!showRaw) "✓  Summary" else "Summary",
                            fontWeight = if (!showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(rawFocus)
                            .background(
                                if (showRaw) TvDesign.SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .focusProperties {
                                up = toggleFocus
                                left = summaryFocus
                                down = tabFocusers.first()
                            },
                        onClick = { showRaw = true },
                    ) {
                        Text(
                            if (showRaw) "✓  Raw" else "Raw",
                            fontWeight = if (showRaw) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = selectedTab == tab
                        TextButton(
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(tabFocusers[index])
                                .background(
                                    if (selected) TvDesign.SurfaceRaised else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .onFocusChanged { state ->
                                    if (state.isFocused) focusedTabIndex = index
                                }
                                .focusProperties {
                                    up = if (showRaw) rawFocus else summaryFocus
                                    down = searchFocus
                                    if (index > 0) left = tabFocusers[index - 1]
                                    if (index < tabFocusers.lastIndex) right = tabFocusers[index + 1]
                                },
                            onClick = { selectedTab = tab },
                        ) {
                            Text(
                                if (selected) "✓ ${tab.label}" else tab.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(TvDesign.Surface, RoundedCornerShape(8.dp))
                        .border(
                            width = if (searchFocused) 2.dp else 1.dp,
                            color = if (searchFocused) TvDesign.Focus else TvDesign.White.copy(alpha = .14f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", color = TvDesign.Muted, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus)
                            .onFocusChanged { searchFocused = it.isFocused }
                            .focusProperties {
                                up = tabFocusers[focusedTabIndex.coerceIn(tabFocusers.indices)]
                                down = copyFocus
                            },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TvDesign.White,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TvDesign.Accent),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "Search diagnostic…",
                                        color = TvDesign.Muted,
                                        fontSize = 13.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.focusProperties { left = searchFocus; down = copyFocus },
                        ) {
                            Text("×", fontSize = 20.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(TvDesign.Surface, RoundedCornerShape(10.dp))
                        .border(
                            2.dp,
                            if (logFocused) TvDesign.Focus.copy(alpha = .9f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .focusRequester(logFocus)
                        .onFocusChanged { logFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        val down = key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                        val target = (logScroll.value + if (down) scrollStep else -scrollStep)
                                            .coerceIn(0, logScroll.maxValue)
                                        scope.launch { logScroll.scrollTo(target) }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (key.action == KeyEvent.ACTION_DOWN) {
                                        runCatching { searchFocus.requestFocus() }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .verticalScroll(logScroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = remember(visibleLog, providerMode) {
                            if (providerMode) styledProviderDiagnosticText(visibleLog) else androidx.compose.ui.text.AnnotatedString(visibleLog)
                        },
                        fontFamily = if (providerMode && !showRaw) FontFamily.SansSerif else FontFamily.Monospace,
                        fontSize = if (providerMode && !showRaw) 12.sp else 10.sp,
                        lineHeight = if (providerMode && !showRaw) 17.sp else if (providerMode) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                        color = TvDesign.White.copy(alpha = .88f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(copyFocus)
                            .then(bottomButtonNavigation),
                        onClick = {
                            val fullText = if (showRaw) {
                                diagnostics.exportRaw(selectedTab)
                            } else {
                                diagnostics.exportSummary(selectedTab)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    "VUEO ${diagnostics.kind} ${if (showRaw) "raw" else "summary"} ${selectedTab.label}",
                                    fullText,
                                )
                            )
                            Toast.makeText(
                                context,
                                "${if (showRaw) "Raw" else "Summary"} ${selectedTab.label} copied",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) { Text("Copy") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { diagnostics.saveBundle(context.applicationContext) }
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
                    ) { Text(if (saving) "Saving…" else "Save") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
                        onClick = {
                            diagnostics.clear()
                            diagnosticText = refreshPreview()
                        },
                    ) { Text("Clear") }

                    TextButton(
                        modifier = Modifier.weight(1f).then(bottomButtonNavigation),
                        onClick = ::closeAndRestore,
                    ) { Text("Close") }
                }
            }
        }
    }
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

private fun styledProviderDiagnosticText(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        text.lineSequence().forEach { line ->
            val color = when {
                line.startsWith("INCOMPLETE RECORD:") || "Status: ERROR" in line ||
                    "status=FAILED " in line || "QJS_ABORT" in line || "ERROR: " in line -> Color(0xFFE5A1A1)
                "Status: TIMEOUT" in line || "status=TIMEOUT " in line || "QJS_TIMEOUT" in line -> Color(0xFFE0C28C)
                "Status: OK" in line || "status=ONLINE " in line || "status=SLOW " in line -> Color(0xFFA9D5B7)
                "Status: EMPTY" in line || "Status: CANCELLED" in line ||
                    "status=NO_RESULTS " in line || "status=CANCELLED " in line -> Color(0xFFB6BAC4)
                else -> null
            }
            if (color != null) pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
            append(line)
            append('\n')
            if (color != null) pop()
        }
    }
