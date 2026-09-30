package com.vueo.tv

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private var lastExitRequestAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RuntimeDiagnostics.install(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent { VueoTvApp(onExit = ::requestExit) }
    }

    private fun requestExit() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastExitRequestAt <= EXIT_CONFIRMATION_WINDOW_MS) {
            finish()
            return
        }

        lastExitRequestAt = now
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val EXIT_CONFIRMATION_WINDOW_MS = 2_000L
    }
}
