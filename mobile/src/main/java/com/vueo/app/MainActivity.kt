package com.vueo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vueo.app.core.storage.SettingsStore
import com.vueo.app.ui.VueoApp
import com.vueo.app.ui.VueoPalette
import com.vueo.app.ui.theme.VueoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
