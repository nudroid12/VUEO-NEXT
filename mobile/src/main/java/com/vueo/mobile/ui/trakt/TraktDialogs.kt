package com.vueo.mobile.ui.trakt

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.trakt.TraktService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TraktAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val service = remember { TraktService.get(context) }
    val revision by service.revision.collectAsState()
    val profile = service.profileId()
    var login by remember(profile) { mutableStateOf<TraktService.DeviceLogin?>(null) }
    var request by remember(profile) { mutableIntStateOf(0) }
    var connecting by remember(profile) { mutableStateOf(false) }
    var busy by remember(profile) { mutableStateOf(false) }
    var message by remember(profile) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val connected = remember(profile, revision) { service.connected(profile) }
    LaunchedEffect(profile, request) {
        if (request == 0) return@LaunchedEffect
        connecting = true
        message = "Requesting login code…"
        try {
            val pending = service.beginLogin(profile)
            login = pending
            message = "Open the link and enter this code to allow VUEO access."
            service.awaitLogin(pending)
            login = null
            message = "Connected to Trakt"
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { message = error.message?.takeIf { error is java.io.IOException } ?: TraktService.message(error); login = null } finally { connecting = false }
    }
    DisposableEffect(profile) {
        onDispose { login?.let(service::cancelLogin) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trakt") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (connected) "Connected · ${service.username(profile).ifBlank { "Trakt account" }}" else "Connect your account to sync My List, watched history and ratings.")
                Text(service.status(profile))
                if (message.isNotBlank()) Text(message)
                login?.let { pending ->
                    Text(pending.userCode, fontSize = 28.sp)
                    Text(pending.verificationUrl)
                    TextButton(onClick = {
                        try {
                            val uri = Uri.parse(pending.verificationUrl)
                            if (uri.scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { message = "Open the displayed link on your phone or browser." }
                    }) { Text("Open Trakt login") }
                }
                if (!connected) TextButton(enabled = !connecting, onClick = { request++ }) { Text(if (connecting) "Waiting for approval…" else "Connect Trakt") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Trakt discovery rows", Modifier.weight(1f))
                    Switch(checked = service.enabled("discovery", profile), onCheckedChange = { service.setEnabled("discovery", it, profile) })
                }
                if (connected) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sync library", Modifier.weight(1f))
                        Switch(checked = service.enabled("sync", profile), onCheckedChange = {
                            service.setEnabled("sync", it, profile)
                            if (it) service.scheduleSync(profile, force = true)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Scrobble playback", Modifier.weight(1f))
                        Switch(checked = service.enabled("scrobble", profile), onCheckedChange = { service.setEnabled("scrobble", it, profile) })
                    }
                    TextButton(enabled = !busy && service.enabled("sync", profile), onClick = {
                        busy = true
                        scope.launch {
                            try { service.sync(profile) } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { message = TraktService.message(error) } finally { busy = false }
                        }
                    }) { Text(if (busy) "Syncing…" else "Sync now") }
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { service.disconnect(profile); message = "Disconnected" } finally { busy = false }
                        }
                    }) { Text("Disconnect") }
                }
                Text("Trending and popular titles work without login. Account access is optional. Data from Trakt.tv.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun TraktRatingDialog(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val service = remember { TraktService.get(context) }
    val profile = service.profileId()
    var rating by remember(media.id, profile) { mutableIntStateOf(service.rating(media, profile).takeIf { it > 0 } ?: 5) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rate ${media.name} on Trakt") }, text = {
        Column {
            if (!service.connected(profile)) Text("Connect Trakt in Settings → Enhancements first.")
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(enabled = rating > 1, onClick = { rating-- }) { Text("−") }
                Text("$rating / 10", Modifier.padding(top = 12.dp))
                TextButton(enabled = rating < 10, onClick = { rating++ }) { Text("+") }
            }
        }
    }, confirmButton = { TextButton(enabled = service.connected(profile), onClick = { service.rate(media, rating, profile); onDismiss() }) { Text("Save") } },
        dismissButton = { Row {
            if (service.connected(profile)) TextButton(onClick = { service.rate(media, 0, profile); onDismiss() }) { Text("Remove rating") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}
