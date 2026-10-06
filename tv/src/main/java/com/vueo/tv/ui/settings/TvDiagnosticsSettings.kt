package com.vueo.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun TvDiagnosticsSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var showCrashDiagnostics by remember { mutableStateOf(false) }
    var showPerformanceDiagnostics by remember { mutableStateOf(false) }

    if (showCrashDiagnostics) {
        TvRuntimeDiagnosticsDialog(
            onDismiss = { showCrashDiagnostics = false },
        )
    }
    if (showPerformanceDiagnostics) {
        TvPerformanceDiagnosticsDialog(
            onDismiss = { showPerformanceDiagnostics = false },
        )
    }

    val entries = listOf(
        TvSettingsEntry(
            id = "crash-diagnostics",
            title = "Crash Diagnostics",
            subtitle = "Crash, native/QuickJS, provider failure and stall evidence.",
            value = "Open",
            icon = Icons.Default.Settings,
            onActivate = { showCrashDiagnostics = true },
        ),
        TvSettingsEntry(
            id = "performance-diagnostics",
            title = "Performance Diagnostics",
            subtitle = "Master ON/OFF runtime performance diagnostics with Summary and Raw logs.",
            value = "Open",
            icon = Icons.Default.SettingsInputComponent,
            onActivate = { showPerformanceDiagnostics = true },
        ),
    )

    TvSettingsListScreen(
        title = "Diagnostics",
        subtitle = "Crash evidence and opt-in performance recording.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
    )
}
