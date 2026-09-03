package com.vueo.tv.player

import android.content.Context
import com.vueo.shared.core.storage.PlaybackProgressStore

class TvPlaybackStore(context: Context) {
    private val delegate =
        PlaybackProgressStore(
            context = context.applicationContext,
            prefsName = PREFS_NAME,
        )

    fun resumePositionMs(request: TvPlaybackRequest): Long =
        delegate.resumePositionMs(request.cacheKey)

    fun save(
        request: TvPlaybackRequest,
        positionMs: Long,
        durationMs: Long,
    ) {
        delegate.save(
            key = request.cacheKey,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun clear(request: TvPlaybackRequest) {
        delegate.clear(request.cacheKey)
    }

    companion object {
        private const val PREFS_NAME = "vueo_tv_playback"
    }
}
