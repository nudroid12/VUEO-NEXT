package com.vueo.shared.core.storage

import android.content.Context

class PlaybackProgressStore(
    context: Context,
    prefsName: String,
    private val minResumeMs: Long = 30_000L,
    private val completionWindowMs: Long = 60_000L,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )

    fun resumePositionMs(key: String): Long =
        prefs.getLong(positionKey(key), 0L)
            .takeIf { it >= minResumeMs }
            ?: 0L

    fun save(
        key: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val completed = safeDuration > 0L && safePosition >= safeDuration - completionWindowMs
        if (completed || safePosition < minResumeMs) {
            clear(key)
            return
        }
        prefs.edit()
            .putLong(positionKey(key), safePosition)
            .putLong(durationKey(key), safeDuration)
            .apply()
    }

    fun clear(key: String) {
        prefs.edit()
            .remove(positionKey(key))
            .remove(durationKey(key))
            .apply()
    }

    private fun positionKey(key: String) = "position:$key"
    private fun durationKey(key: String) = "duration:$key"
}
