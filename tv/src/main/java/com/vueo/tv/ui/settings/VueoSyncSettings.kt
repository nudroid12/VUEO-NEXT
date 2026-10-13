package com.vueo.tv.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.sync.VueoSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TvVueoSyncSettings(onNavigate: (String)->Unit,onProfile: ()->Unit,onBack: ()->Unit) {
    val context=LocalContext.current
    val service=remember { VueoSyncService.get(context) }
    val status by service.state.collectAsState()
    val revision by service.revision.collectAsState()
    val connected=remember(revision) { service.connected() }
    val scope=rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<String?>(null) }
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
    val entries=buildList {
        add(TvSettingsEntry("status","Account",if(connected) service.email() else "Sign in to the same account used on mobile.",
            if(connected) "Connected" else "Not connected",enabled=false,section="ACCOUNT"))
        add(TvSettingsEntry("message","Sync Status",if(message.isBlank() || message==status) status else "$message\n$status",enabled=false))
        if(!connected) {
            add(TvSettingsEntry("email","Email","Your VUEO account email.",email,enabled=!busy,onActivate={edit="email"}))
            add(TvSettingsEntry("password","Password","Your VUEO account password.",if(password.isBlank()) "Not set" else "••••••••",enabled=!busy,onActivate={edit="password"}))
            add(TvSettingsEntry("sign-in","Sign In","Connect this TV to your account.",enabled=!busy && email.isNotBlank() && password.isNotBlank(),
                onActivate={action { service.signIn(email,password) }}))
            add(TvSettingsEntry("register","Create Account","Create a VUEO account with the email and password entered above.",
                enabled=!busy && email.isNotBlank() && password.isNotBlank(),onActivate={action { service.signIn(email,password,true) }}))
        } else {
            add(TvSettingsEntry("sync-now","Sync Now","Sync this TV with your account.",enabled=!busy,
                onActivate={action { service.syncNow(); "Sync finished" }},section="ACTIONS"))
            add(TvSettingsEntry("sign-out",if(confirmSignOut) "Confirm Sign Out" else "Sign Out","Local data stays on this TV.",enabled=!busy,
                onActivate={if(confirmSignOut) action {service.signOut(); confirmSignOut=false; "Signed out"} else confirmSignOut=true}))
            if(confirmSignOut) add(TvSettingsEntry("cancel-sign-out","Cancel","Stay connected.",onActivate={confirmSignOut=false}))
            service.categories.forEach { (kind,label) ->
                val on=remember(revision) { service.enabled(kind) }
                add(TvSettingsEntry("sync-$kind",label,when(kind) {
                    "profiles" -> "Names and avatars across devices."
                    "trakt" -> "Trakt connection and account preferences."
                    "addons" -> "Sync installed addons and providers. ON/OFF and catalog settings stay on this device."
                    "integrations" -> "API keys and metadata preferences."
                    else -> "Keep $label in sync between devices."
                },value=if(on) "On" else "Off",switchChecked=on,enabled=!busy,
                    onActivate={service.setEnabled(kind,!on)},section="SYNC OPTIONS"))
            }
        }
    }
    TvSettingsListScreen("Account","Your library across mobile and TV.",entries,onNavigate,onProfile,onBack,
        footer="Playback, subtitle and device preferences stay on this TV.")
    edit?.let { field -> TvTextEntryDialog(title=if(field=="email") "Email" else "Password",initialValue=if(field=="email") email else password,
        secret=field=="password",onDismiss={edit=null},onSave={if(field=="email") email=it else password=it; edit=null}) }
}
