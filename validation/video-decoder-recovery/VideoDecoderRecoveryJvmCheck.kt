package com.vueo.shared.core.player

import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import java.lang.reflect.Proxy

/** JVM control-flow test. Inject capability snapshots; never claims to exercise an Android codec. */
private class TestDecoderError(code: Int) : PlaybackException("test", null, code, android.os.Bundle.EMPTY, 1L)

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    val format = Format.Builder().setSampleMimeType("video/hevc").setWidth(3840).setHeight(2160).build()
    val session = VideoDecoderSession("Test").apply { beginSource("private-source-url") }
    val keyMethod = VideoDecoderSession::class.java.getDeclaredMethod("key", Format::class.java).apply { isAccessible = true }
    val key = keyMethod.invoke(session, format) as String
    val planField = VideoDecoderSession::class.java.getDeclaredField("plans").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    val plans = planField.get(session) as MutableMap<String, List<VideoCodecCandidate>>
    plans[key] = listOf(VideoCodecCandidate("bad", true, true), VideoCodecCandidate("good", true, false))
    session.initialized(format, "bad", 1L)
    val operations = mutableListOf<String>()
    var position = 93_000L
    var shouldPlay = false
    val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentPosition" -> position
            "getPlayWhenReady" -> shouldPlay
            "setPlayWhenReady" -> { shouldPlay = args!![0] as Boolean; operations += "ready:$shouldPlay"; null }
            "stop" -> { position = 0L; operations += "stop"; null }
            "seekTo" -> { position = args!![0] as Long; operations += "seek:$position"; null }
            "prepare" -> { operations += "prepare"; null }
            "getCurrentTracks" -> Tracks.EMPTY
            else -> error("Unexpected Player call: ${method.name}")
        }
    } as Player
    val recovery = VideoDecoderRecovery(session)
    verify(recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test", 94_000L))
    verify(operations == listOf("stop", "seek:94000", "prepare", "ready:false"))
    verify(position == 94_000L)
    verify(!shouldPlay) // User pause survives recovery; no autoplay surprise.
    verify(RuntimeDiagnostics.events.any { it.contains("VIDEO_CODEC_RETRY") && it.contains("next=good") })
    verify(RuntimeDiagnostics.events.none { it.contains("private-source-url") })
    // No current track alternatives: give control back to the existing source recovery caller.
    session.initialized(format, "good", 1L)
    operations.clear()
    verify(recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test"))
    verify(session.synchronous(format, "good"))
    verify(RuntimeDiagnostics.events.any { it.contains("VIDEO_QUEUEING_RETRY") })
    operations.clear()
    verify(!recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test"))
    verify(!session.synchronous(format, "good"))
    verify(operations.isEmpty())
    verify(recovery.failureMessage(TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format)?.contains("HEVC") == true)
    // Manual retry grants a fresh policy; source switching clears plans and stale callbacks.
    recovery.reset()
    verify(plans.isEmpty())
    plans[key] = listOf(VideoCodecCandidate("bad", true, true), VideoCodecCandidate("good", true, false))
    session.initialized(format, "bad", 2L)
    shouldPlay = true
    verify(recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test"))
    verify(shouldPlay)
    session.beginSource("another-private-source")
    verify(plans.isEmpty())
    session.initialized(format, "bad", 1L) // stale callback from the first source
    verify(session.next(format, null) == null)
    // Initialization fallback is already handled by Media3; never replay its exhausted codec list.
    recovery.reset()
    plans[key] = listOf(VideoCodecCandidate("bad", true, true), VideoCodecCandidate("good", true, false))
    session.initialized(format, "bad", 4L)
    operations.clear()
    verify(!recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED), format, "Test"))
    verify(operations.isEmpty())
    verify(!recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), format, "Test"))
    // Reproduce a capability report of zero supported decoders without treating it as proof
    // that playback is impossible: only the decoder that really initialized gets one sync test.
    recovery.reset()
    plans[key] = listOf(VideoCodecCandidate("observed-software", false, false), VideoCodecCandidate("unopened-hardware", false, true))
    session.initialized(format, "observed-software", 5L)
    verify(recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test"))
    verify(session.synchronous(format, "observed-software"))
    verify(!session.synchronous(format, "unopened-hardware"))
    operations.clear()
    verify(!recovery.tryRecover(player, TestDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED), format, "Test"))
    verify(operations.isEmpty())
    session.initializationFailed(format, "unopened-hardware", 5L, "android.media.MediaCodec.error_neg_1100", "CodecException")
    verify(RuntimeDiagnostics.events.any { it.contains("VIDEO_CODEC_INIT_FAILED") && it.contains("error_neg_1100") })
    println("PASS: $checks decoder recovery JVM checks")
}
