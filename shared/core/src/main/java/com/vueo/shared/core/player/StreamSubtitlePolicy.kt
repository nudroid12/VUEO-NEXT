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
        (source.subtitles + addonTracks).filterNot(DeferredPluginSubtitles::isIndex).distinctBy { it.url }
}
