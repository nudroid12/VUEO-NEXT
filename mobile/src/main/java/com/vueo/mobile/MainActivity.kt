package com.vueo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.mobile.core.storage.SettingsStore
import com.vueo.mobile.ui.VueoApp
import com.vueo.mobile.ui.VueoPalette
import com.vueo.mobile.ui.theme.VueoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RuntimeDiagnostics.install(applicationContext)

        val settingsStore = SettingsStore(applicationContext)
        VueoPalette.applyTheme(settingsStore.appTheme())
        VueoPalette.applyAccent(settingsStore.appAccent())

        setContent {
            VueoTheme {
                VueoApp()
            }
        }
    }
}
