package com.vueo.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    VueoSettingsPage("Account","Sync across mobile and TV.",onBack) {
        item("account") {
            if (connected) {
                VueoConnectedAccountCard(
                    email = service.email(),
                    syncOn = syncOn,
                    status = status,
                    message = message,
                    busy = busy,
                    confirmSignOut = confirmSignOut,
                    onSyncChanged = { enabled ->
                        action { service.setSyncEnabledAndWait(enabled); service.state.value }
                    },
                    onSyncNow = { action { service.syncNow(); service.state.value } },
                    onSignOut = { confirmSignOut = !confirmSignOut },
                    onConfirmSignOut = {
                        action { service.signOut(); confirmSignOut = false; "Signed out. Local data is retained." }
                    },
                )
            } else {
                VueoStatusCard("VUEO Account", "Not connected",
                    "Sign in to bring your library and connections to this device.")
            }
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
    }
}

@Composable
private fun VueoConnectedAccountCard(
    email: String,
    syncOn: Boolean,
    status: String,
    message: String,
    busy: Boolean,
    confirmSignOut: Boolean,
    onSyncChanged: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VueoPalette.Surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VUEO Account", modifier = Modifier.weight(1f), color = Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50), color = VueoPalette.Accent.copy(alpha = .10f)) {
                    Text("Connected", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = VueoPalette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(email, color = VueoPalette.Muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Switch(checked = syncOn, onCheckedChange = onSyncChanged, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Account sync" })
                    Text("Sync", color = VueoPalette.Muted, fontSize = 12.sp)
                }
                Button(onClick = onSyncNow, enabled = !busy && syncOn,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(if (busy) "Working…" else "Sync now", fontSize = 12.sp)
                }
            }
            Text(status, color = VueoPalette.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            if (message.isNotBlank() && message != status) {
                Text(message, color = VueoPalette.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (confirmSignOut) "Cancel sign out" else "Sign out", fontSize = 12.sp)
            }
            if (confirmSignOut) {
                Text("Sign out of this device? Your local data will be kept.",
                    color = VueoPalette.Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Button(onClick = onConfirmSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm sign out", fontSize = 12.sp)
                }
            }
        }
    }
}
