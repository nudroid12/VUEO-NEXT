package com.vueo.tv.player

import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * TV focus requests can race Compose enter/exit animations and lazy content.
 * Retry across a few frames instead of silently losing remote focus.
 */
internal suspend fun FocusRequester.requestTvFocus(
    attempts: Int = 6,
): Boolean {
    for (attempt in 0 until attempts.coerceAtLeast(1)) {
        if (attempt > 0) {
            delay((16L * attempt).coerceAtMost(64L))
        }
        val focused = runCatching { requestFocus() }.getOrDefault(false)
        if (focused) return true
    }
    return false
}
