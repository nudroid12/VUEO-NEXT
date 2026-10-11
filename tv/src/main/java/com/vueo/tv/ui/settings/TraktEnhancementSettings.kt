package com.vueo.tv.settings

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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh

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
internal fun TvTraktEnhancementSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val state = rememberTraktSettingsState()
    val context = LocalContext.current
    val entries = buildList {
        add(TvSettingsEntry(
            id = "account-status", title = "Account",
            subtitle = if (state.connected) state.username.ifBlank { "Trakt account" } else "Connect to sync My List, watched history and ratings for this profile.",
            value = if (state.connected) "Connected" else if (state.connecting) "Connecting" else "Not connected",
            section = "ACCOUNT", icon = Icons.Default.AccountCircle,
        ))
        if (!state.connected) {
            add(TvSettingsEntry(
                id = "connect", title = if (state.connecting) "Cancel Connection" else "Connect Trakt",
                subtitle = if (state.connecting) "Waiting for approval. You can cancel and request a new code." else "Get a login code to connect your Trakt account.",
                enabled = !state.busy,
                onActivate = { if (state.connecting) state.cancelLogin() else state.connect() },
                section = "ACCOUNT",
            ))
        }
        state.login?.let { pending ->
            add(TvSettingsEntry(
                id = "login-code", title = "Login code: ${pending.userCode}",
                subtitle = "On your phone, open ${pending.verificationUrl} and enter this code. Keep this page open.",
                value = "Waiting for approval", section = "CONNECT ACCOUNT", accented = true,
            ))
            add(TvSettingsEntry(
                id = "open-login", title = "Open Trakt Login",
                subtitle = "Open the login address in a browser on this device, if available.",
                onActivate = { openTraktLogin(context, state) }, section = "CONNECT ACCOUNT",
            ))
        }
        val activity = listOf(state.message, state.status).filter { it.isNotBlank() }.distinct().joinToString(" • ")
        if (activity.isNotBlank()) add(TvSettingsEntry("activity", "Activity", activity, section = "ACCOUNT"))
        add(toggleEntry(
            "discovery", "Discovery Rows", "Show Trakt trending and popular titles on Home. Works without login.", state.discovery,
        ) { state.setFeature("discovery", it) }.copy(section = "DISCOVERY", switchChecked = state.discovery))
        add(toggleEntry(
            "library-sync", "Library Sync",
            if (state.connected) "Sync My List, watched history and ratings with Trakt." else "Connect your account to enable library sync.",
            state.sync, enabled = state.connected && !state.busy,
        ) { state.setFeature("sync", it) }.copy(section = "SYNC & PLAYBACK", switchChecked = state.sync))
        add(toggleEntry(
            "scrobble", "Scrobble Playback",
            if (state.connected) "Share playback activity with Trakt while you watch." else "Connect your account to enable scrobbling.",
            state.scrobble, enabled = state.connected && !state.busy,
        ) { state.setFeature("scrobble", it) }.copy(section = "SYNC & PLAYBACK", switchChecked = state.scrobble))
        add(TvSettingsEntry(
            id = "sync-now", title = "Sync Now", subtitle = "Refresh your Trakt library for this profile.",
            value = if (state.busy) "Working…" else "", enabled = state.connected && state.sync && !state.busy,
            onActivate = state::syncNow, section = "SYNC & PLAYBACK", icon = Icons.Default.Refresh,
        ))
        if (state.connected) {
            add(TvSettingsEntry(
                id = "disconnect", title = if (state.confirmDisconnect) "Confirm Disconnect" else "Disconnect Account",
                subtitle = if (state.confirmDisconnect) "Press OK to disconnect Trakt for this profile." else "Remove this profile's Trakt connection.",
                enabled = !state.busy, onActivate = { if (state.confirmDisconnect) state.disconnect() else state.confirmDisconnect = true }, section = "ACCOUNT ACCESS",
            ))
            if (state.confirmDisconnect) add(TvSettingsEntry(
                id = "cancel-disconnect", title = "Keep Account Connected", subtitle = "Cancel disconnect.",
                onActivate = { state.confirmDisconnect = false }, section = "ACCOUNT ACCESS",
            ))
        }
    }
    TvSettingsListScreen(
        title = "Trakt", subtitle = "Your account, discovery and viewing activity.",
        entries = entries, onNavigate = onNavigate, onProfile = onProfile, onBack = onBack,
        footer = "Account settings apply to the current VUEO profile. Choose your recommendation engine in Enhancements. Data from Trakt.tv.",
    )
}
