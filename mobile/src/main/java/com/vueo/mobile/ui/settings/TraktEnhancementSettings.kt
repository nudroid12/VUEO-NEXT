package com.vueo.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.trakt.TraktService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class TraktSettingsState(
    val service: TraktService,
    val profile: String,
    private val scope: CoroutineScope,
) {
    var connected by mutableStateOf(service.connected(profile))
    var username by mutableStateOf(service.username(profile))
    var status by mutableStateOf(service.status(profile))
    var discovery by mutableStateOf(service.enabled("discovery", profile))
    var sync by mutableStateOf(service.enabled("sync", profile))
    var scrobble by mutableStateOf(service.enabled("scrobble", profile))
    var login by mutableStateOf<TraktService.DeviceLogin?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
    var confirmDisconnect by mutableStateOf(false)
    private var loginJob: Job? = null
    private var attempt = 0

    fun refresh() {
        connected = service.connected(profile)
        username = service.username(profile)
        status = service.status(profile)
        discovery = service.enabled("discovery", profile)
        sync = service.enabled("sync", profile)
        scrobble = service.enabled("scrobble", profile)
    }

    fun connect() {
        if (connecting || busy || connected) return
        val currentAttempt = ++attempt
        connecting = true
        message = "Requesting login code…"
        loginJob = scope.launch {
            try {
                val pending = service.beginLogin(profile)
                login = pending
                message = "Waiting for approval. Enter the code at the address below."
                service.awaitLogin(pending)
                message = "Connected to Trakt."
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = if (error is java.io.IOException && error.message?.startsWith("Login code") == true) error.message.orEmpty() else TraktService.message(error)
            } finally {
                if (attempt == currentAttempt) {
                    login?.let(service::cancelLogin)
                    login = null
                    connecting = false
                }
            }
        }
    }

    fun cancelLogin() {
        attempt++
        login?.let(service::cancelLogin)
        loginJob?.cancel()
        loginJob = null
        login = null
        connecting = false
        message = "Connection cancelled."
    }

    fun setFeature(name: String, enabled: Boolean) {
        service.setEnabled(name, enabled, profile)
        if (name == "sync" && enabled) service.scheduleSync(profile, force = true)
        refresh()
    }

    fun syncNow() {
        if (busy || !connected || !sync) return
        busy = true
        message = "Syncing your Trakt library…"
        scope.launch {
            try {
                service.sync(profile)
                message = "Sync complete."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = TraktService.message(error)
            } finally {
                busy = false
                refresh()
            }
        }
    }

    fun disconnect() {
        if (busy || !connected) return
        confirmDisconnect = false
        busy = true
        scope.launch {
            try {
                service.disconnect(profile)
                message = "Disconnected from Trakt."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = TraktService.message(error)
            } finally {
                busy = false
                refresh()
            }
        }
    }
}

@Composable
private fun rememberTraktSettingsState(): TraktSettingsState {
    val context = LocalContext.current
    val service = remember(context) { TraktService.get(context) }
    val profile = service.profileId()
    val scope = rememberCoroutineScope()
    val state = remember(service, profile) { TraktSettingsState(service, profile, scope) }
    LaunchedEffect(state) { service.revision.collect { state.refresh() } }
    DisposableEffect(state) { onDispose { state.cancelLogin() } }
    return state
}

private fun openTraktLogin(context: android.content.Context, state: TraktSettingsState) {
    val pending = state.login ?: return
    try {
        val uri = Uri.parse(pending.verificationUrl)
        if (uri.scheme == "https") {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            state.message = "Open the displayed address on your phone or browser."
        }
    } catch (_: Exception) {
        state.message = "Open the displayed address on your phone or browser."
    }
}

@Composable
internal fun TraktEnhancementSettingsScreen(onBack: () -> Unit) {
    val state = rememberTraktSettingsState()
    val context = LocalContext.current
    VueoSettingsPage(
        title = "Trakt",
        subtitle = "Your account, discovery and viewing activity.",
        onBack = onBack,
    ) {
        item(key = "trakt-status") {
            VueoStatusCard(
                title = "Account",
                value = if (state.connected) "Connected" else if (state.connecting) "Connecting" else "Not connected",
                text = if (state.connected) state.username.ifBlank { "Trakt account" } else "Connect to sync My List, watched history and ratings for this profile.",
            )
        }
        item(key = "trakt-account-label") { VueoSectionLabel("ACCOUNT") }
        item(key = "trakt-account-actions") {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VueoPalette.Surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.connected) {
                        if (state.connecting) {
                            OutlinedButton(onClick = state::cancelLogin, modifier = Modifier.fillMaxWidth()) { Text("Cancel connection") }
                        } else {
                            Button(onClick = state::connect, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Connect Trakt") }
                        }
                    } else {
                        Text("Connected to ${state.username.ifBlank { "Trakt" }}", color = Color.White, fontWeight = FontWeight.Bold)
                        if (state.confirmDisconnect) {
                            Text("Disconnect Trakt for this profile?", color = VueoPalette.Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { state.confirmDisconnect = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = state::disconnect, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                            }
                        } else {
                            OutlinedButton(onClick = { state.confirmDisconnect = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Disconnect account") }
                        }
                    }
                }
            }
        }
        if (state.login != null) {
            item(key = "trakt-login-code") {
                val pending = state.login
                if (pending != null) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VueoPalette.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Enter this code", color = VueoPalette.Muted, fontSize = 12.sp)
                            Text(pending.userCode, color = VueoPalette.Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            Text(pending.verificationUrl, color = Color.White, fontSize = 14.sp)
                            Text("Keep this page open while approving access in your browser.", color = VueoPalette.Muted, fontSize = 12.sp)
                            Button(onClick = { openTraktLogin(context, state) }, modifier = Modifier.fillMaxWidth()) { Text("Open Trakt login") }
                        }
                    }
                }
            }
        }
        if (state.message.isNotBlank() || state.status.isNotBlank()) {
            item(key = "trakt-activity") {
                VueoInfoCard("Activity", listOf(state.message, state.status).filter { it.isNotBlank() }.distinct().joinToString("\n"))
            }
        }
        item(key = "trakt-discovery-label") { VueoSectionLabel("DISCOVERY") }
        item(key = "trakt-discovery") {
            VueoSettingsToggleCard(
                title = "Discovery Rows",
                subtitle = "Show Trakt trending and popular titles on Home. Works without login.",
                checked = state.discovery,
                onCheckedChange = { state.setFeature("discovery", it) },
            )
        }
        item(key = "trakt-sync-label") { VueoSectionLabel("SYNC & PLAYBACK") }
        item(key = "trakt-sync") {
            VueoSettingsToggleCard(
                title = "Library Sync",
                subtitle = if (state.connected) "Sync My List, watched history and ratings with Trakt." else "Connect your account to enable library sync.",
                checked = state.sync,
                enabled = state.connected && !state.busy,
                onCheckedChange = { state.setFeature("sync", it) },
            )
        }
        item(key = "trakt-scrobble") {
            VueoSettingsToggleCard(
                title = "Scrobble Playback",
                subtitle = if (state.connected) "Share playback activity with Trakt while you watch." else "Connect your account to enable scrobbling.",
                checked = state.scrobble,
                enabled = state.connected && !state.busy,
                onCheckedChange = { state.setFeature("scrobble", it) },
            )
        }
        item(key = "trakt-sync-now") {
            OutlinedButton(onClick = state::syncNow, enabled = state.connected && state.sync && !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Working…" else "Sync now")
            }
        }
        item(key = "trakt-note") {
            VueoCompactInfo("Optional by design", "Account settings apply to the current VUEO profile. Choose your recommendation engine in Enhancements. Data from Trakt.tv.")
        }
    }
}
