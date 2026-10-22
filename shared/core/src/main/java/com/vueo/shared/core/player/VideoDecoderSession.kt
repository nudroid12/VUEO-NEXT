package com.vueo.shared.core.player

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics

/** Shared by the renderer thread and UI recovery; no URLs or global codec blacklist are logged. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoDecoderSession(private val platform: String) {
    private var sourceKey: String? = null
    private var generation = 0L
    private var policy = VideoCodecPolicy()
    private var queueing = VideoQueueingPolicy()
    private val plans = mutableMapOf<String, List<VideoCodecCandidate>>()
    private val initialized = mutableMapOf<String, String>()
    private var lastFailure: Pair<String, String>? = null

    internal data class Retry(val name: String, val synchronous: Boolean)

    internal data class Plan(val infos: List<MediaCodecInfo>, val generation: Long)

    @Synchronized
    fun beginSource(key: String?) {
        if (sourceKey == key) return
        sourceKey = key
        reset()
    }

    @Synchronized
    internal fun reset() {
        generation++
        policy = VideoCodecPolicy()
        queueing = VideoQueueingPolicy()
        plans.clear()
        initialized.clear()
        lastFailure = null
    }

    private fun key(format: Format): String = listOf(
        format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate,
        format.colorInfo, format.drmInitData?.hashCode(),
    ).joinToString("|")

    private fun description(format: Format): String =
        "mime=${format.sampleMimeType} codecs=${format.codecs} size=${format.width}x${format.height} " +
            "fps=${format.frameRate} color=${format.colorInfo}"

    @Synchronized
    internal fun plan(context: Context, format: Format, infos: List<MediaCodecInfo>): Plan {
        val key = key(format)
        val capabilities = infos.map { info ->
            VideoCodecCandidate(info.name, runCatching { info.isFormatSupported(context, format) }.getOrDefault(false), info.hardwareAccelerated)
        }
        // Bound adaptive-stream bookkeeping. Runtime failures remain scoped to the source session.
        if (plans.size >= 64 && key !in plans) {
            val oldest = plans.keys.first()
            plans.remove(oldest)
            initialized.remove(oldest)
        }
        plans[key] = capabilities
        val syncTarget = queueing.target(key)
        val ordered = if (syncTarget != null) {
            capabilities.filter { it.name == syncTarget }
        } else {
            policy.ordered(key, capabilities)
        }
        RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_DECODER_PLAN",
            "${description(format)} mode=${if (syncTarget != null) "sync-test" else "auto"} candidates=" + ordered.joinToString(",") {
                "${it.name}:${if (it.supported) "supported" else "exceeds-or-unsupported"}:${if (it.hardware) "hw" else "sw"}"
            })
        return Plan(ordered.mapNotNull { candidate -> infos.firstOrNull { it.name == candidate.name } }, generation)
    }

    @Synchronized
    internal fun initialized(format: Format, name: String, epoch: Long) {
        if (epoch != generation) return
        initialized[key(format)] = name
        if (lastFailure?.first == key(format)) lastFailure = null
        RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_DECODER_SELECTED", "name=$name ${description(format)}")
    }

    @Synchronized
    internal fun failed(format: Format?, name: String?, epoch: Long) {
        if (epoch != generation || format == null || name == null) return
        lastFailure = key(format) to name
    }

    @Synchronized
    internal fun synchronous(format: Format, name: String): Boolean = queueing.target(key(format)) == name

    @Synchronized
    internal fun adapterCreated(format: Format, name: String, synchronous: Boolean) {
        RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_CODEC_ADAPTER",
            "name=$name mode=${if (synchronous) "synchronous" else "auto"} ${description(format)}")
    }

    @Synchronized
    internal fun initializationFailed(format: Format?, name: String?, epoch: Long, diagnostic: String?, cause: String?) {
        if (epoch != generation) return
        val safeDiagnostic = diagnostic?.replace(Regex("https?://\\S+"), "<url>")?.take(160)
        RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_CODEC_INIT_FAILED",
            "name=${name ?: "unknown"} diagnostic=${safeDiagnostic ?: "none"} cause=${cause ?: "unknown"} " +
                (format?.let(::description) ?: "format=unknown"))
    }

    @Synchronized
    internal fun next(format: Format, reportedName: String?, allowSynchronous: Boolean = false): Retry? {
        val key = key(format)
        val name = reportedName ?: lastFailure?.takeIf { it.first == key }?.second ?: initialized[key]
        val candidates = plans[key].orEmpty()
        queueing.failed(key, name)
        val next = policy.next(key, name, candidates)
        if (next != null) {
            RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_CODEC_RETRY",
                "failed=${name ?: "unknown"} next=$next attempts=${policy.attempts} ${description(format)}")
            return Retry(next, synchronous = false)
        }
        // Capability reports can be conservative. Change only queueing mode for the decoder
        // that actually initialized; never force a different unsupported decoder or spoof MIME.
        val sync = if (allowSynchronous && candidates.any { it.name == name }) {
            queueing.request(key, name, initialized[key], encrypted = format.drmInitData != null)
        } else null
        if (sync != null) {
            RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_QUEUEING_RETRY",
                "name=$sync mode=synchronous reportedSupported=${candidates.firstOrNull { it.name == sync }?.supported} ${description(format)}")
            return Retry(sync, synchronous = true)
        }
        RuntimeDiagnostics.recordPlayerEvent(platform, "VIDEO_CODEC_EXHAUSTED",
            "failed=${name ?: "unknown"} next=none attempts=${policy.attempts} " +
                "supported=${candidates.count { it.supported }} ${description(format)}")
        return null
    }
}
