package com.vueo.shared.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleDialogueSyncTest {
    @Test fun subripPreservesRawTimeAndCleansMultilineText() {
        val cues = SubtitleDialogueSync.parse("\uFEFF1\r\n00:01:02,345 --> 00:01:04,000\r\n<i>Hello</i>\r\nworld &amp; friends\r\n\r\n")
        assertEquals(listOf(SubtitleDialogue(62_345L, "Hello world & friends")), cues)
    }
    @Test fun webvttSupportsCueIdsAndSettings() {
        val cues = SubtitleDialogueSync.parse("WEBVTT\n\ncue-one\n01:02.010 --> 01:05.000 align:start\n<v Alice>Hi</v>\n\n")
        assertEquals(listOf(SubtitleDialogue(62_010L, "Hi")), cues)
    }
    @Test fun assUsesEventFormatAndPreservesCommasInText() {
        val cues = SubtitleDialogueSync.parse("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:12.34,0:00:14.00,Default,,0,0,0,,{\\i1}Hello, world\\NAgain\n")
        assertEquals(listOf(SubtitleDialogue(12_340L, "Hello, world Again")), cues)
    }
    @Test fun badTimestampsAndReversedIntervalsAreIgnored() {
        assertEquals(emptyList<SubtitleDialogue>(), SubtitleDialogueSync.parse("00:00:60,000 --> 00:01:02,000\nBad\n\n00:00:03,000 --> 00:00:02,000\nBackwards\n"))
    }
    @Test fun absoluteOffsetHasCorrectSignAndRejectsOutOfRange() {
        assertEquals(1200, SubtitleDialogueSync.offset(11_200L, 10_000L))
        assertEquals(-1200, SubtitleDialogueSync.offset(8_800L, 10_000L))
        assertEquals(60_000, SubtitleDialogueSync.offset(70_000L, 10_000L))
        assertNull(SubtitleDialogueSync.offset(70_001L, 10_000L))
    }
}
