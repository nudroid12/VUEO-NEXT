package com.vueo.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.smartsubs.SmartSubsSettingsStore
import com.vueo.shared.core.smartsubs.VueoSmartSubsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vueo.shared.core.sync.VueoSyncService

@Composable
internal fun TvGeminiEnhancementSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SmartSubsSettingsStore(context.applicationContext) }
    val accountSync = remember { VueoSyncService.get(context) }
    val accountRevision by accountSync.integrationRevision.collectAsState()
    var config by remember { mutableStateOf(store.configuration()) }
    var editingKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accountRevision, testing, editingKey) {
        if (!testing && !editingKey) {
            val saved = withContext(Dispatchers.IO) { store.configuration() }
            if (saved.apiKey != config.apiKey) status = null
            config = saved
        }
    }


    if (editingKey) TvTextEntryDialog(
        title = "Gemini API Key",
        initialValue = config.apiKey,
        secret = true,
        allowSecretReveal = true,
        onDismiss = { editingKey = false },
        onSave = { value ->
            try {
                store.saveCredentials(value)
                config = store.configuration(); status = null
                editingKey = false
            } catch (_: Exception) { status = "Unable to save securely on this TV"; editingKey = false }
        },
    )
    val entries = buildList {
        add(TvSettingsEntry(
            id = "gemini-api-key", title = "Gemini API Key",
            subtitle = "Your own key and quota. Clear this field to remove the key and session.",
            value = configuredLabel(config.apiKey), enabled = !testing,
            onActivate = { editingKey = true }, section = "CONNECTION", icon = Icons.Default.SettingsInputComponent,
        ))
        add(TvSettingsEntry(
            id = "smartsubs-connect", title = "Test Connection",
            subtitle = status?.takeIf { it != "Connecting…" && it != "Connected" }
                ?: "Check Gemini key/model through VUEO SmartSubs. Uses a small amount of Gemini quota.",
            value = when {
                testing -> "Connecting…"
                status != null && status != "Connected" -> "Failed"
                config.connected -> "Connected"
                else -> "Test"
            },
            enabled = !testing,
            onActivate = {
                if (!testing) {
                    if (config.apiKey.isBlank()) status = "Enter Gemini API key"
                    else {
                        testing = true; status = "Connecting…"
                        val current = config
                        scope.launch {
                            try {
                                store.saveCredentials(current.apiKey)
                                val session = VueoSmartSubsClient.connect(current.apiKey)
                                status = if (store.saveSession(current.apiKey, session)) "Connected" else "Settings changed. Test again."
                                config = store.configuration()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                status = VueoSmartSubsClient.failureMessage(error)
                            } finally { testing = false }
                        }
                    }
                }
            }, section = "CONNECTION", icon = Icons.Default.Refresh,
        ))
        add(toggleEntry(
            id = "smartsubs-enabled", title = "Use Gemini for SmartSubs",
            subtitle = "Select a Malay AI track in the player to translate provider English subtitles.",
            checked = config.enabled && config.connected,
            enabled = config.connected && !testing,
        ) {
            try { store.setEnabled(it); config = store.configuration() }
            catch (_: Exception) { status = "Unable to save securely on this TV" }
        }.copy(section = "SMARTSUBS"))
    }
    TvSettingsListScreen(
        title = "Gemini", subtitle = "Your own API key for VUEO SmartSubs.",
        entries = entries, onNavigate = onNavigate, onProfile = onProfile, onBack = onBack,
        topLabel = "Enhancements",
        footer = "Account sync shares your API key. The session and SmartSubs ON/OFF stay on this TV. Select a Malay AI track in the player to translate provider English subtitles.",
    )
}
