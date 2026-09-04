package com.vueo.tv.player

import android.content.Context
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.PlaybackProgressStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.library.TvLibraryStore

class TvPlaybackStore(context: Context) {
    private val appContext = context.applicationContext

    private val delegate =
        PlaybackProgressStore(
            context = appContext,
            prefsName = PREFS_NAME,
        )

    private val libraryStore =
        LibraryStore(
            context = appContext,
            prefsName = TvLibraryStore.PREFS_NAME,
            watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
        )

    private val settingsStore =
        SettingsStore(
            context = appContext,
            prefsName = SETTINGS_PREFS_NAME,
        )

    fun resumePositionMs(request: TvPlaybackRequest): Long =
        if (settingsStore.resumePlaybackEnabled()) {
            delegate.resumePositionMs(request.cacheKey)
        } else {
            0L
        }

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

        if (positionMs > 0L) {
            libraryStore.recordPlayback(
                media = request.media,
                videoId = request.videoId,
                episodeTitle = request.episodeTitle,
                season = request.season,
                episode = request.episode,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }

    fun complete(
        request: TvPlaybackRequest,
        durationMs: Long,
    ) {
        delegate.clear(request.cacheKey)

        if (durationMs > 0L) {
            libraryStore.recordPlayback(
                media = request.media,
                videoId = request.videoId,
                episodeTitle = request.episodeTitle,
                season = request.season,
                episode = request.episode,
                positionMs = durationMs,
                durationMs = durationMs,
            )
        }
    }

    fun clear(request: TvPlaybackRequest) {
        delegate.clear(request.cacheKey)
    }

    companion object {
        private const val PREFS_NAME = "vueo_tv_playback"
        const val SETTINGS_PREFS_NAME = "vueo_tv_settings"
    }
}
