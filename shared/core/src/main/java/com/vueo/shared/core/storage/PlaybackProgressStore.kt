package com.vueo.shared.core.storage

import android.content.Context

/**
 * Compatibility facade retained for TV and older call sites.
 * The canonical implementation is now [PlaybackStore].
 */
class PlaybackProgressStore(
    context: Context,
    prefsName: String,
    private val minResumeMs: Long = 30_000L,
    private val completionWindowMs: Long = 60_000L,
) {
    private val delegate =
        PlaybackStore(
            context = context.applicationContext,
            prefsName = prefsName,
            minResumeMs = minResumeMs,
            completionWindowMs = completionWindowMs,
        )

    fun resumePositionMs(key: String): Long =
        delegate.positionMs(key)
            .takeIf { it >= minResumeMs }
            ?: 0L

    fun save(
        key: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        delegate.savePositionMs(
            mediaKey = key,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun clear(key: String) {
        delegate.clearPosition(key)
    }
}
