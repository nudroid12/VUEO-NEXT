package com.vueo.tv.settings

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Only the Activity window participates. Native dialogs keep their own Back handling. */
internal class TvMotionBackShortcut {
    private val handler = Handler(Looper.getMainLooper())
    private var down: KeyEvent? = null
    private var held = false
    private val trigger = Runnable {
        if (down != null && allowed && !held) {
            held = true
            TvMotionBubble.focusBubble()
        }
    }

    fun dispatch(event: KeyEvent, forward: (KeyEvent) -> Boolean): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val captured = down
                if (captured != null && captured.downTime == event.downTime && captured.deviceId == event.deviceId) return true
                cancel()
                // Never reinterpret an orphan repeat or a modal's press as a shortcut.
                if (!allowed || event.repeatCount != 0 || event.isCanceled) return false
                down = KeyEvent(event)
                handler.postDelayed(trigger, HOLD_MS)
                return true
            }
            KeyEvent.ACTION_UP -> {
                val captured = down ?: return false
                if (captured.downTime != event.downTime || captured.deviceId != event.deviceId) return false
                handler.removeCallbacks(trigger)
                if (!event.isCanceled && !held && allowed && event.eventTime - captured.eventTime >= HOLD_MS) {
                    held = true
                    TvMotionBubble.focusBubble()
                }
                val shortPress = !held && !event.isCanceled
                cancel()
                if (shortPress) {
                    // Replay the original pair through the original dispatch, never recursively.
                    forward(captured)
                    forward(event)
                }
                return true
            }
        }
        return false
    }

    fun cancel() {
        handler.removeCallbacks(trigger)
        down = null
        held = false
    }

    companion object {
        const val HOLD_MS = 700L
        var allowed = false
    }
}
