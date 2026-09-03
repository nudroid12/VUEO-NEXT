package com.vueo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vueo.app.ui.VueoApp
import com.vueo.app.ui.theme.VueoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VueoTheme {
                VueoApp()
            }
        }
    }
}
