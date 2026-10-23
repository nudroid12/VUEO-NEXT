package com.vueo.shared.core.player
import com.vueo.shared.core.media.SubtitleTrack
object DeferredPluginSubtitles {
    const val INDEX_MIME = "application/vnd.vueo.subtitle-list+json"
    fun isIndex(track: SubtitleTrack): Boolean = track.mimeType == INDEX_MIME
}
