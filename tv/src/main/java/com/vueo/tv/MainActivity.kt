package com.vueo.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private var lastAcceptedDpadRepeatAtMs = 0L
    private var repeatedDpadKeyCode = KeyEvent.KEYCODE_UNKNOWN

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
            // Activation is release-driven throughout the TV UI. Consume the
            // repeated downs so holding OK or Play/Pause can never fire twice.
            if (
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ) {
                return true
            }

            if (keyCode.isDirectionalDpadKey()) {
                val elapsed = event.eventTime - lastAcceptedDpadRepeatAtMs
                if (repeatedDpadKeyCode == keyCode && elapsed < TV_DPAD_REPEAT_INTERVAL_MS) {
                    return true
                }
                repeatedDpadKeyCode = keyCode
                lastAcceptedDpadRepeatAtMs = event.eventTime
            }
        } else if (event.action == KeyEvent.ACTION_UP && keyCode.isDirectionalDpadKey()) {
            repeatedDpadKeyCode = KeyEvent.KEYCODE_UNKNOWN
            lastAcceptedDpadRepeatAtMs = 0L
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RuntimeDiagnostics.install(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent { VueoTvApp(onExit = ::finish) }
    }

    private fun Int.isDirectionalDpadKey(): Boolean =
        this == KeyEvent.KEYCODE_DPAD_LEFT ||
            this == KeyEvent.KEYCODE_DPAD_RIGHT ||
            this == KeyEvent.KEYCODE_DPAD_UP ||
            this == KeyEvent.KEYCODE_DPAD_DOWN

    private companion object {
        const val TV_DPAD_REPEAT_INTERVAL_MS = 85L
    }
}
