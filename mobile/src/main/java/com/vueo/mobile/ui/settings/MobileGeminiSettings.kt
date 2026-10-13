package com.vueo.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.smartsubs.SmartSubsSettingsStore
import com.vueo.shared.core.smartsubs.VueoSmartSubsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GeminiEnhancementSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SmartSubsSettingsStore(context.applicationContext) }
    var config by remember { mutableStateOf(store.configuration()) }
    var key by remember { mutableStateOf(config.apiKey) }
    var backend by remember { mutableStateOf(config.backendUrl) }
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val edited = key.trim() != config.apiKey || backend.trim().trimEnd('/') != config.backendUrl

    fun save(): Boolean {
        if (backend.isNotBlank() && !VueoSmartSubsClient.validBackendUrl(backend)) {
            status = "Enter a valid HTTPS backend URL"
            return false
        }
        return try {
            store.saveCredentials(key, backend)
            config = store.configuration()
            key = config.apiKey; backend = config.backendUrl
            status = "Saved on this device"
            true
        } catch (_: Exception) {
            status = "Unable to save securely on this device"
            false
        }
    }

    VueoSettingsPage(
        title = "Gemini",
        subtitle = "Your own API key for VUEO SmartSubs.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "VUEO SmartSubs",
                value = status ?: when {
                    edited -> "Unsaved changes"
                    config.connected -> "Connected"
                    config.apiKey.isBlank() -> "Enter API key"
                    config.backendUrl.isBlank() -> "Enter backend URL"
                    else -> "Test connection to activate session"
                },
                text = "Uses your Gemini quota. Subtitle translation runs on your VUEO SmartSubs backend.",
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = VueoPalette.Surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; status = null },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !testing,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (visible) "Hide API key" else "Show API key")
                            }
                        },
                    )
                    OutlinedTextField(
                        value = backend,
                        onValueChange = { backend = it; status = null },
                        label = { Text("VUEO SmartSubs Backend URL") },
                        placeholder = { Text("https://your-worker.workers.dev") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !testing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !testing, onClick = { save() }) { Text("Save") }
                        OutlinedButton(enabled = !testing, onClick = {
                            if (key.isBlank() || backend.isBlank()) status = "Enter API key and backend URL"
                            else if (save()) {
                                testing = true; status = "Connecting…"
                                val currentKey = key; val currentBackend = backend
                                scope.launch {
                                    try {
                                        val session = VueoSmartSubsClient.connect(currentBackend, currentKey)
                                        status = if (store.saveSession(currentKey, currentBackend, session)) "Connected" else "Settings changed. Test again."
                                        config = store.configuration()
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        status = VueoSmartSubsClient.failureMessage(error)
                                    } finally { testing = false }
                                }
                            }
                        }) { Text(if (testing) "Connecting…" else "Test Connection") }
                    }
                    Text("Test Connection saves these settings and checks your key/model through the backend. Uses a small amount of Gemini quota.")
                }
            }
        }
        item {
            VueoSettingsToggleCard(
                title = "Use Gemini for SmartSubs",
                subtitle = "Save and connect first. Malay subtitle playback is not available yet.",
                checked = config.enabled && config.connected && !edited,
                enabled = config.connected && !edited && !testing,
                onCheckedChange = {
                    if (config.connected && !edited && !testing) {
                        try {
                            store.setEnabled(it); config = store.configuration()
                        } catch (_: Exception) { status = "Unable to save securely on this device" }
                    } else status = "Save settings and test connection first"
                },
            )
        }
        item {
            VueoInfoCard(
                title = "On this device",
                text = "Key and session are encrypted locally and excluded from backup and account sync. Clear the API key and save to remove it. Malay subtitle playback is not available yet.",
            )
        }
    }
}
