package com.vueo.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vueo.tv.watchnext.TvWatchNextDeepLink
import com.vueo.tv.watchnext.TvWatchNextPublisher
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val exitHandler = Handler(Looper.getMainLooper())
    private var pendingWatchNextMediaKey by mutableStateOf<String?>(null)
    private var lastExitPressMs: Long? = null
    private var showExitPrompt by mutableStateOf(false)
    private val clearExitPrompt = Runnable {
        showExitPrompt = false
        lastExitPressMs = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RuntimeDiagnostics.install(applicationContext)
        pendingWatchNextMediaKey = TvWatchNextDeepLink.parseMediaKey(intent?.data)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            Box(Modifier.fillMaxSize()) {
                VueoTvApp(
                    onExit = ::requestExit,
                    watchNextMediaKey = pendingWatchNextMediaKey,
                    onWatchNextConsumed = { consumed ->
                        if (pendingWatchNextMediaKey == consumed) {
                            pendingWatchNextMediaKey = null
                        }
                    },
                )
                if (showExitPrompt) {
                    Text(
                        text = "Press Back again to exit",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                            .background(Color(0xE6222222), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TvWatchNextDeepLink.parseMediaKey(intent.data)?.let { mediaKey ->
            pendingWatchNextMediaKey = mediaKey
        }
    }

    private fun requestExit() {
        val now = SystemClock.elapsedRealtime()
        val previousPress = lastExitPressMs
        if (previousPress != null && now - previousPress < 2_000L) {
            exitHandler.removeCallbacks(clearExitPrompt)
            clearExitPrompt.run()
            finish()
            return
        }
        lastExitPressMs = now
        showExitPrompt = true
        exitHandler.removeCallbacks(clearExitPrompt)
        exitHandler.postDelayed(clearExitPrompt, 2_000L)
    }

    override fun onPause() {
        exitHandler.removeCallbacks(clearExitPrompt)
        clearExitPrompt.run()
        TvWatchNextPublisher.schedule(applicationContext)
        super.onPause()
    }

}
