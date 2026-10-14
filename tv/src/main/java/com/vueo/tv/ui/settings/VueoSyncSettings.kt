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
    val syncOn=remember(revision) { service.syncEnabled() }
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
        add(TvSettingsEntry("message","Account Sync",if(message.isBlank() || message==status) status else "$message\n$status",enabled=false))
        if(!connected) {
            add(TvSettingsEntry("email","Email","Your VUEO account email.",email,enabled=!busy,onActivate={edit="email"}))
            add(TvSettingsEntry("password","Password","Your VUEO account password.",if(password.isBlank()) "Not set" else "••••••••",enabled=!busy,onActivate={edit="password"}))
            add(TvSettingsEntry("sign-in","Sign In","Connect this TV to your account.",enabled=!busy && email.isNotBlank() && password.isNotBlank(),
                onActivate={action { service.signIn(email,password) }}))
            add(TvSettingsEntry("register","Create Account","Create a VUEO account with the email and password entered above.",
                enabled=!busy && email.isNotBlank() && password.isNotBlank(),onActivate={action { service.signIn(email,password,true) }}))
        } else {
            add(TvSettingsEntry("sync-all", if(syncOn) "Sync On" else "Sync Off",
                "One control for all account data.", value=if(syncOn) "On" else "Off", enabled=!busy,
                onActivate={action {service.setSyncEnabledAndWait(!syncOn); service.state.value}}, section=null))
            add(TvSettingsEntry("sync-now","Sync Now","Sync your library and connections now.",enabled=!busy && syncOn,
                onActivate={action { service.syncNow(); service.state.value }},section="ACTIONS"))
            add(TvSettingsEntry("sign-out",if(confirmSignOut) "Confirm Sign Out" else "Sign Out","Local data stays on this TV.",enabled=!busy,
                onActivate={if(confirmSignOut) action {service.signOut(); confirmSignOut=false; "Signed out"} else confirmSignOut=true}))
            if(confirmSignOut) add(TvSettingsEntry("cancel-sign-out","Cancel","Stay connected.",onActivate={confirmSignOut=false}))

        }
    }
    TvSettingsListScreen("Account","Your library and connections across mobile and TV.",entries,onNavigate,onProfile,onBack)
    edit?.let { field -> TvTextEntryDialog(title=if(field=="email") "Email" else "Password",initialValue=if(field=="email") email else password,
        secret=field=="password",onDismiss={edit=null},onSave={if(field=="email") email=it else password=it; edit=null}) }
}
