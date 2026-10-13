package com.vueo.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.sync.VueoSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VueoSyncSettingsScreen(onBack: () -> Unit, returnHint: String? = null) {
    val context=LocalContext.current
    val service=remember { VueoSyncService.get(context) }
    val status by service.state.collectAsState()
    val revision by service.revision.collectAsState()
    val connected=remember(revision) { service.connected() }
    val syncOn=remember(revision) { service.syncEnabled() }
    val scope=rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(returnHint) { if (returnHint != null) message = returnHint }
    var confirmSignOut by remember { mutableStateOf(false) }
    fun action(block: suspend ()->String) {
        if(busy) return
        busy=true
        scope.launch {
            try { message=block() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { message=VueoSyncService.message(error) }
            finally { password=""; busy=false }
        }
    }
    VueoSettingsPage("Account","Your library and connections across mobile and TV.",onBack) {
        item("account") {
            VueoStatusCard("VUEO Account", if(connected) "Connected" else "Not connected",
                if(connected) service.email() else "Sign in to bring your library and connections to this device.")
        }
        if (connected) item("sync-overview") {
            VueoStatusCard("Account Sync", if(syncOn) "On" else "Off",
                if(message.isBlank() || message==status) status else "$message\n$status")
        }
        if (!connected && message.isNotBlank()) item("message") {
            Text(message, color=VueoPalette.Muted, modifier=Modifier.padding(8.dp))
        }
        if(!connected) item("login") {
            Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(email,{email=it; message=""},label={Text("Email")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it; message=""},label={Text("Password")},singleLine=true,enabled=!busy,
                    visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                Button({action { service.signIn(email,password) }},enabled=!busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Sign in") }
                OutlinedButton({action { service.signIn(email,password,register=true) }},enabled=!busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Create account") }
            }
        }
        if(connected) {
            item("shared") {
                VueoCompactInfo("Synced together",
                    "Profiles, Favorites, Continue Watching, watched history, installed addons and providers. Enhancement shares TMDB, MDBList and Gemini API keys, plus your Trakt account.")
            }
            item("actions") {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({action { service.syncNow(); service.state.value }},enabled=!busy && syncOn,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Working…" else "Sync now") }
                    OutlinedButton({action { service.setSyncEnabledAndWait(!syncOn); service.state.value }},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(syncOn) "Turn sync off" else "Turn sync on") }
                    OutlinedButton({confirmSignOut=!confirmSignOut},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(confirmSignOut) "Cancel sign out" else "Sign out") }
                    if(confirmSignOut) Button({action { service.signOut(); confirmSignOut=false; "Signed out. Local data is retained." }},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Confirm sign out") }
                }
            }
        }
        item("local") { VueoCompactInfo("Stays on this device","All enhancement switches, Trakt feature options, addon/provider ON/OFF, catalogs, subtitle, audio, player preferences, layout, cache, downloads and diagnostics.") }
    }
}
