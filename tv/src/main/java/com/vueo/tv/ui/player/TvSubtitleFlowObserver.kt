package com.vueo.tv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.vueo.shared.core.player.SubtitleFlowDiagnostics
import java.io.IOException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvSubtitleFlowObserver(private val player: ExoPlayer) : AutoCloseable {
    private var previousTracks = "not_observed"
    private var cueTrack = ""
    private var deliveredForTrack = ""
    private var firstFrameSeen = false
    private var sourceUrl: String? = null
    private fun source(): String? = player.currentMediaItem?.localConfiguration?.uri?.toString()
    private fun syncSource() {
        val next = source()
        if (next != sourceUrl) {
            firstFrameSeen = false
            sourceUrl = next
            previousTracks = "not_observed"
            cueTrack = ""
            deliveredForTrack = ""
        }
    }
    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            syncSource()
            if (!firstFrameSeen) {
                firstFrameSeen = true
                SubtitleFlowDiagnostics.event("TV", source(), "VIDEO_FIRST_FRAME")
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            SubtitleFlowDiagnostics.event("TV", source(), "PLAYER_ERROR", "code=${error.errorCode} name=${safe(error.errorCodeName)}")
        }
        override fun onTracksChanged(tracks: Tracks) {
            syncSource()
            val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
                (0 until group.length).map { index ->
                    val format = group.getTrackFormat(index)
                    val external = player.currentMediaItem?.localConfiguration?.subtitleConfigurations
                        ?.firstOrNull { it.id == format.id }
                    val identity = external?.uri?.toString() ?: format.id.orEmpty()
                    "track=${SubtitleFlowDiagnostics.key(identity)} origin=${if (external != null) "external" else "embedded_or_unknown"} language=${safe(format.language)} " +
                        "mime=${safe(format.sampleMimeType)} supported=${group.isTrackSupported(index)} selected=${group.isTrackSelected(index)}"
                }
            }
            val selected = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
                (0 until group.length).filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it).id.orEmpty() }
            }
            cueTrack = selected.joinToString { SubtitleFlowDiagnostics.key(it) }
            val signature = text.joinToString(";")
            if (previousTracks != signature) {
                previousTracks = signature
                SubtitleFlowDiagnostics.event("TV", source(), "PLAYER_TEXT_TRACKS", "count=${text.size}")
                text.take(24).forEach { SubtitleFlowDiagnostics.event("TV", source(), "PLAYER_TEXT_TRACK", it) }
                deliveredForTrack = ""
            }
        }
        override fun onCues(cueGroup: CueGroup) {
            syncSource()
            if (cueGroup.cues.isNotEmpty() && deliveredForTrack != cueTrack.ifBlank { "unknown" }) {
                deliveredForTrack = cueTrack.ifBlank { "unknown" }
                SubtitleFlowDiagnostics.event("TV", source(), "SUBTITLE_CUES_DELIVERED", "selected=$deliveredForTrack count=${cueGroup.cues.size}")
            }
        }
    }
    private val analytics = object : AnalyticsListener {
        override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
            if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
                SubtitleFlowDiagnostics.load("TV", source(), loadEventInfo.dataSpec.uri.toString(), "SUBTITLE_LOAD_COMPLETED", "bytes=${loadEventInfo.bytesLoaded} elapsedMs=${loadEventInfo.loadDurationMs}")
            }
        }
        override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
            if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
                val httpStatus = (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    ?: (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                SubtitleFlowDiagnostics.load("TV", source(), loadEventInfo.dataSpec.uri.toString(), "SUBTITLE_LOAD_ERROR", "errorType=${safe(error::class.java.simpleName)} http=${httpStatus ?: "unknown"} cancelled=$wasCanceled elapsedMs=${loadEventInfo.loadDurationMs}")
            }
        }
    }
    init {
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        listener.onTracksChanged(player.currentTracks)
        listener.onCues(player.currentCues)
    }
    override fun close() {
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
    }
    private fun safe(value: String?): String = value.orEmpty().replace(Regex("[^a-zA-Z0-9_+./-]"), "_").take(80).ifBlank { "unknown" }
}
