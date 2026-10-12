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
internal fun VueoSyncSettingsScreen(onBack: () -> Unit) {
    val context=LocalContext.current
    val service=remember { VueoSyncService.get(context) }
    val status by service.state.collectAsState()
    val revision by service.revision.collectAsState()
    val connected=remember(revision) { service.connected() }
    val scope=rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
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
    VueoSettingsPage("VUEO Sync","Use the same VUEO account on mobile and TV.",onBack) {
        item("account") { VueoStatusCard("Account",if(connected) "Connected" else "Not connected",
            if(connected) service.email() else "Sign in, then choose what to sync.") }
        item("status") { Text(message.ifBlank { status },color=VueoPalette.Muted,modifier=Modifier.padding(8.dp)) }
        if(!connected) item("login") {
            Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,enabled=!busy,
                    visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                Button({action { service.signIn(email,password) }},enabled=!busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Sign in") }
                OutlinedButton({action { service.signIn(email,password,register=true) }},enabled=!busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Create account") }
            }
        }
        if(connected) {
            service.categories.forEach { (kind,label) -> item("sync-$kind") {
                VueoSettingsToggleCard(title=label,subtitle=when(kind) {
                    "profiles" -> "Profile names, avatars and order. Required by other sync options."
                    "trakt" -> "Share this profile's Trakt account and its chosen options across devices."
                    "integrations" -> "Share API keys and metadata/rating settings."
                    else -> "Keep $label in sync between your devices."
                },checked=remember(revision){service.enabled(kind)},onCheckedChange={service.setEnabled(kind,it)})
            } }
            item("actions") {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({action { service.syncNow(); "Sync finished" }},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Working…" else "Sync now") }
                    OutlinedButton({confirmSignOut=!confirmSignOut},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(confirmSignOut) "Cancel sign out" else "Sign out") }
                    if(confirmSignOut) Button({action { service.signOut(); confirmSignOut=false; "Signed out. Local data is retained." }},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Confirm sign out") }
                }
            }
        }
        item("local") { VueoCompactInfo("Stays on this device","Subtitle, audio, player preferences, layout, cache, downloads and diagnostics. Sync options start off.") }
    }
}
