package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack

/** Source sidecars change with the source; independent addon results remain. */
object StreamSubtitlePolicy {
    fun forPlayback(source: StreamSource, addonTracks: List<SubtitleTrack>): List<SubtitleTrack> =
        (source.subtitles + addonTracks).distinctBy { it.url }
}
