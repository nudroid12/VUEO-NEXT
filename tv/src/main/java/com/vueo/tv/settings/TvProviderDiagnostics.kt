package com.vueo.tv.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Close") }
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
            "Provider execution exceeded the captured timeout. Inspect the raw log for the last request or parser step reached."
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
