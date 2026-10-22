package com.vueo.shared.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.google.common.collect.ImmutableList
import java.lang.reflect.Proxy

/** Exercises actual Media3 track-selection objects, with a controlled Player proxy. */
private class TestDecoderError(code: Int) : PlaybackException("test", null, code, android.os.Bundle.EMPTY, 1L)

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun format(mime: String, w: Int, h: Int) = Format.Builder().setSampleMimeType(mime).setWidth(w).setHeight(h).build()
    val hevc = format("video/hevc", 3840, 2160)
    val avc1080 = format("video/avc", 1920, 1080)
    val avc720 = format("video/avc", 1280, 720)
    val unsupported = format("video/avc", 3840, 2160)
    fun group(id: String, format: Format, supported: Boolean) = Tracks.Group(
        TrackGroup(id, format), false,
        intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_EXCEEDS_CAPABILITIES),
        booleanArrayOf(false),
    )
    val group1080 = group("avc1080", avc1080, true)
    var tracks = Tracks(ImmutableList.of(group("failed", hevc, true), group("unsupported", unsupported, false), group("avc720", avc720, true), group1080))
    var parameters = TrackSelectionParameters.Builder().build()
    var position = 90_000L
    var play = false
    val operations = mutableListOf<String>()
    val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentPosition" -> position
            "getPlayWhenReady" -> play
            "getCurrentTracks" -> tracks
            "getTrackSelectionParameters" -> parameters
            "setTrackSelectionParameters" -> { parameters = args!![0] as TrackSelectionParameters; operations += "override"; null }
            "setPlayWhenReady" -> { play = args!![0] as Boolean; operations += "ready:$play"; null }
            "seekTo" -> { position = args!![0] as Long; operations += "seek:$position"; null }
            "prepare" -> { operations += "prepare"; null }
            else -> error("Unexpected Player call: ${method.name}")
        }
    } as Player
    val recovery = VideoDecoderRecovery()
    val failure = TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED)
    verify(recovery.tryRecover(player, failure, hevc, "Test", 95_000L))
    verify(operations == listOf("override", "seek:95000", "prepare", "ready:false"))
    verify(parameters.overrides.keys.single() == group1080.mediaTrackGroup)
    verify(parameters.overrides.values.single().trackIndices == listOf(0))
    verify(position == 95_000L && !play)
    operations.clear()
    verify(recovery.tryRecover(player, failure, avc1080, "Test")) // smaller supported AVC remains
    verify(parameters.overrides.keys.single().id == "avc720")
    operations.clear()
    verify(!recovery.tryRecover(player, failure, avc720, "Test"))
    verify(operations.isEmpty()) // caller can now perform its existing source fallback
    recovery.reset()
    play = true
    verify(recovery.tryRecover(player, failure, hevc, "Test"))
    verify(play)
    operations.clear()
    verify(!recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), hevc, "Test"))
    verify(operations.isEmpty())
    verify(!recovery.tryRecover(player, failure, null, "Test"))
    verify(!recovery.tryRecover(player, failure, format("audio/aac", -1, -1), "Test"))
    tracks = Tracks(ImmutableList.of(group("failed", hevc, true), group("unsupported", avc1080, false)))
    recovery.reset()
    verify(!recovery.tryRecover(player, failure, hevc, "Test"))
    verify(operations.isEmpty())
    verify(recovery.failureMessage(failure, hevc)?.contains("HEVC/H.265") == true)
    verify(recovery.failureMessage(TestDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), hevc) == null)
    verify(RuntimeDiagnostics.events.none { "VIDEO_QUEUEING" in it || "VIDEO_CODEC_RETRY" in it })
    println("PASS: $checks track recovery JVM checks")
}
