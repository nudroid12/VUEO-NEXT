package com.vueo.shared.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics

/** One bounded recovery session per player/source. Never retries the failed video configuration. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoDecoderRecovery {
    private val policy = VideoFallbackPolicy()

    private fun isVideoDecoderError(error: PlaybackException, format: Format?): Boolean =
        format?.sampleMimeType?.startsWith("video/") == true &&
            error.errorCode in setOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            )

    private fun snapshot(format: Format) = VideoFallbackFormat(
        format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate,
    )

    private data class Candidate(val group: Tracks.Group, val index: Int, val format: Format)

    fun tryRecover(player: Player, error: PlaybackException, failedFormat: Format?, platform: String): Boolean {
        if (!isVideoDecoderError(error, failedFormat)) return false
        val failed = failedFormat ?: return false
        val candidates = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    val format = group.getTrackFormat(index)
                    if (group.isTrackSupported(index)) Candidate(group, index, format) else null
                }
            }
        val nextIndex = policy.next(snapshot(failed), candidates.map { snapshot(it.format) })
        val next = nextIndex?.let(candidates::get)
        if (next == null) {
            RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_DECODER_EXHAUSTED", "attempts=${policy.attempts} alternatives=${candidates.size}")
            return false
        }
        val position = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setOverrideForType(TrackSelectionOverride(next.group.mediaTrackGroup, next.index))
            .build()
        RuntimeDiagnostics.recordPlayerEvent(
            platform, "VIDEO_DECODER_RECOVERY",
            "attempt=${policy.attempts} from=${failed.sampleMimeType}/${failed.width}x${failed.height} to=${next.format.sampleMimeType}/${next.format.width}x${next.format.height} positionMs=$position",
        )
        // Keep the existing media item, headers, audio/subtitle selections and resume position.
        player.seekTo(position)
        player.prepare()
        player.playWhenReady = shouldPlay
        return true
    }

    fun failureMessage(error: PlaybackException, format: Format?): String? {
        if (!isVideoDecoderError(error, format)) return null
        val codec = when (format?.sampleMimeType) {
            "video/hevc" -> "HEVC/H.265"
            "video/avc" -> "H.264"
            else -> "this video format"
        }
        return "This device could not decode $codec from this source. No compatible video fallback succeeded. Try another source."
    }
}
