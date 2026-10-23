package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack

/** Shares sidecars only within the current episode discovery result. */
object StreamSubtitlePolicy {
    /** No global storage: each episode uses its own discovered sources.
     * Caller places fresh sources first to retain current sidecar credentials.
     */
    fun forEpisode(sources: List<StreamSource>, addonTracks: List<SubtitleTrack>): List<SubtitleTrack> =
        (sources.flatMap { it.subtitles } + addonTracks).filterNot(DeferredPluginSubtitles::isIndex).distinctBy { it.url }


    fun forPlayback(source: StreamSource, addonTracks: List<SubtitleTrack>): List<SubtitleTrack> =
        mergePlaybackTracks(source.subtitles + addonTracks)

    /** A later duplicate must not move an overlay track back into the video source. */
    private fun mergePlaybackTracks(tracks: List<SubtitleTrack>): List<SubtitleTrack> =
        tracks.filterNot(DeferredPluginSubtitles::isIndex).groupBy { it.url }.values.map { duplicates ->
            duplicates.firstOrNull { it.liveOverlay } ?: duplicates.first()
        }
}
