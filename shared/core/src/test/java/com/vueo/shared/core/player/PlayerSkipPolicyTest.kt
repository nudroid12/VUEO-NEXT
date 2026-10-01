package com.vueo.shared.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSkipPolicyTest {
    private val duration = 1_200_000L
    private fun ending(endMs: Long = duration, startMs: Long = 1_100_000L) =
        PlayerSkipSegment(startMs, endMs, PlayerSkipKind.ENDING, "test")

    @Test
    fun creditsThatReachVideoEndAllowEarlyNextOnlyAfterTheyStart() {
        val segments = listOf(ending())
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(segments, 1_099_999L, duration))
        assertTrue(PlayerSkipPolicy.canStartNextDuringCredits(segments, 1_100_000L, duration))
    }

    @Test
    fun remainingSceneIsPreservedBySkipAndBlocksEarlyNext() {
        val segment = ending(duration - 45_000L)
        assertEquals(duration - 45_000L, PlayerSkipPolicy.skipTargetMs(segment, duration)!!)
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(listOf(segment), 1_110_000L, duration))
    }

    @Test
    fun endToleranceIsInclusiveButDoesNotExpandBeyondOneSecond() {
        assertTrue(PlayerSkipPolicy.canStartNextDuringCredits(listOf(ending(duration - 1_000L)), 1_110_000L, duration))
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(listOf(ending(duration - 1_001L)), 1_110_000L, duration))
        assertTrue(PlayerSkipPolicy.canStartNextDuringCredits(listOf(ending(duration + 1_000L)), 1_110_000L, duration))
        assertEquals(duration, PlayerSkipPolicy.skipTargetMs(ending(duration + 1_000L), duration)!!)
        assertNull(PlayerSkipPolicy.skipTargetMs(ending(duration + 1_001L), duration))
    }

    @Test
    fun unknownDurationAndInvalidIntervalsDoNotEnableJumps() {
        assertNull(PlayerSkipPolicy.skipTargetMs(ending(), 0L))
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(listOf(ending()), 1_110_000L, 0L))
        assertNull(PlayerSkipPolicy.skipTargetMs(ending(1_000L, 2_000L), duration))
        assertNull(PlayerSkipPolicy.skipTargetMs(ending(duration + 500L, duration), duration))
    }

    @Test
    fun introIsNeverUsedAsAnEarlyNextSignal() {
        val intro = PlayerSkipSegment(0L, duration, PlayerSkipKind.INTRO, "test")
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(listOf(intro), 10_000L, duration))
    }

    @Test
    fun conflictingEndingIntervalsPreferTheSameFirstIntervalAsSkipUi() {
        val segments = listOf(ending(duration - 45_000L), ending())
        assertEquals(segments.first(), PlayerSkipPolicy.activeSegment(segments, 1_110_000L, duration))
        assertFalse(PlayerSkipPolicy.canStartNextDuringCredits(segments, 1_110_000L, duration))
    }

    @Test
    fun promptDisappearsBeforeItsTargetAndAfterACompletedSkip() {
        val segments = listOf(ending())
        assertEquals(segments.first(), PlayerSkipPolicy.activeSegment(segments, duration - 801L, duration))
        assertNull(PlayerSkipPolicy.activeSegment(segments, duration - 800L, duration))
        assertNull(PlayerSkipPolicy.activeSegment(segments, duration, duration))
    }
}
