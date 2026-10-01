package com.vueo.shared.core.player

/** Timestamp/duration heuristic only; provider data must match the played cut. */
object PlayerSkipPolicy {
    const val END_TOLERANCE_MS = 1_000L

    fun skipTargetMs(segment: PlayerSkipSegment, durationMs: Long): Long? {
        if (durationMs <= 0L || segment.startMs < 0L ||
            segment.endMs <= segment.startMs || segment.startMs >= durationMs
        ) return null
        if (segment.endMs > durationMs &&
            segment.endMs - durationMs > END_TOLERANCE_MS
        ) return null
        return segment.endMs.coerceAtMost(durationMs)
    }

    fun activeSegment(
        segments: List<PlayerSkipSegment>,
        positionMs: Long,
        durationMs: Long,
    ): PlayerSkipSegment? = segments.firstOrNull { segment ->
        val target = skipTargetMs(segment, durationMs)
        target != null && positionMs >= segment.startMs && target - positionMs > 800L
    }

    fun canStartNextDuringCredits(
        segments: List<PlayerSkipSegment>,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        // Use the same first applicable ending as the skip UI. Conflicting
        // overlapping provider intervals must not enable an earlier jump.
        val ending = segments.firstOrNull { segment ->
            val target = skipTargetMs(segment, durationMs)
            segment.kind == PlayerSkipKind.ENDING && target != null &&
                positionMs >= segment.startMs && positionMs <= target
        } ?: return false
        return kotlin.math.abs(ending.endMs - durationMs) <= END_TOLERANCE_MS
    }
}
