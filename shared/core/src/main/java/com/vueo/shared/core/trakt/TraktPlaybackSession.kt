package com.vueo.shared.core.trakt

import android.content.Context
import com.vueo.shared.core.media.MediaItem

/** Driven by player intent/state, never by progress persistence or rendering. */
class TraktPlaybackSession(context: Context, private val media: MediaItem,
    private val season: Int?, private val episode: Int?) {
    private val service = TraktService.get(context)
    private val profile = service.profileId()
    private var started = false
    private var paused = false
    private var stopped = false
    @Synchronized fun update(playing: Boolean, buffering: Boolean, ended: Boolean, positionMs: Long, durationMs: Long) {
        if (stopped || durationMs <= 0 || buffering) return
        val progress = positionMs.coerceAtLeast(0L).toDouble() * 100.0 / durationMs
        if (ended) { stop(positionMs, durationMs); return }
        if (playing && (!started || paused)) {
            started = true; paused = false
            service.sendScrobble(profile, media, season, episode, "start", progress)
        } else if (!playing && started && !paused) {
            paused = true
            service.sendScrobble(profile, media, season, episode, "pause", progress)
        }
    }
    @Synchronized fun stop(positionMs: Long, durationMs: Long) {
        if (stopped) return
        stopped = true
        if (started && durationMs > 0) service.sendScrobble(profile, media, season, episode, "stop",
            positionMs.coerceAtLeast(0L).toDouble() * 100.0 / durationMs)
    }
}
