package com.vueo.tv.player

import android.content.Context

class TvPlaybackStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun resumePositionMs(request: TvPlaybackRequest): Long =
        prefs.getLong(positionKey(request.cacheKey), 0L)
            .takeIf { it >= MIN_RESUME_MS }
            ?: 0L

    fun save(
        request: TvPlaybackRequest,
        positionMs: Long,
        durationMs: Long,
    ) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val completed =
            safeDuration > 0L && safePosition >= safeDuration - COMPLETION_WINDOW_MS

        if (completed || safePosition < MIN_RESUME_MS) {
            clear(request)
            return
        }

        prefs.edit()
            .putLong(positionKey(request.cacheKey), safePosition)
            .putLong(durationKey(request.cacheKey), safeDuration)
            .apply()
    }

    fun clear(request: TvPlaybackRequest) {
        prefs.edit()
            .remove(positionKey(request.cacheKey))
            .remove(durationKey(request.cacheKey))
            .apply()
    }

    private fun positionKey(key: String) = "position:$key"
    private fun durationKey(key: String) = "duration:$key"

    companion object {
        private const val PREFS_NAME = "vueo_tv_playback"
        private const val MIN_RESUME_MS = 30_000L
        private const val COMPLETION_WINDOW_MS = 60_000L
    }
}
