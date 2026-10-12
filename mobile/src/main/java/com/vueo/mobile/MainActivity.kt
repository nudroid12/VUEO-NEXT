package com.vueo.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.mobile.core.extensions.normalizeStremioAddonManifestUrl
import com.vueo.mobile.core.storage.SettingsStore
import com.vueo.mobile.ui.VueoApp
import com.vueo.mobile.ui.VueoPalette
import com.vueo.mobile.ui.theme.VueoTheme

class MainActivity : ComponentActivity() {
    private val pendingAccountReturn = mutableStateOf<String?>(null)
    private val pendingAddonManifestUrl = mutableStateOf<String?>(null)

    private fun captureAddonIntent(sourceIntent: Intent?) {
        if (sourceIntent?.action != Intent.ACTION_VIEW) return
        val uri = sourceIntent.data
        if (uri?.scheme == "vueo" && uri.host == "account" && uri.path == "/confirmed") {
            val fragment = uri.fragment.orEmpty()
            val failed = uri.getQueryParameter("error") != null || fragment.startsWith("error=") || fragment.contains("&error=")
            pendingAccountReturn.value = if (failed) "The email link could not be confirmed. Request a fresh email link." else
                "Email link opened. Sign in with your email and password."
            // Never retain callback fragments containing credentials in Activity state.
            setIntent(Intent(sourceIntent).apply { data = null })
            return
        }
        val normalized = normalizeStremioAddonManifestUrl(sourceIntent.dataString) ?: return
        pendingAddonManifestUrl.value = normalized
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) captureAddonIntent(intent)
        RuntimeDiagnostics.install(applicationContext)

        val settingsStore = SettingsStore(applicationContext)
        VueoPalette.applyTheme(settingsStore.appTheme())
        VueoPalette.applyAccent(settingsStore.appAccent())

        setContent {
            VueoTheme {
                VueoApp(
                    pendingAccountReturn = pendingAccountReturn.value,
                    onPendingAccountReturnConsumed = { pendingAccountReturn.value = null },
                    pendingAddonManifestUrl = pendingAddonManifestUrl.value,
                    onPendingAddonManifestConsumed = {
                        pendingAddonManifestUrl.value = null
                        setIntent(
                            Intent(intent).apply {
                                data = null
                            }
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureAddonIntent(intent)
    }
}
