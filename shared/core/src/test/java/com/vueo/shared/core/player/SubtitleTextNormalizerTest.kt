package com.vueo.shared.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SubtitleTextNormalizerTest {
    @Test
    fun repairsDecimalCueTimesWithoutChangingDialogue() {
        val raw = "1\n0:00:20.71 --> 0:00:21.75\nCaption <i>text</i>\n\n2\n0:01:00.04 --> 0:01:02.08\nSecond caption\n"
        assertEquals(
            "1\n0:00:20,710 --> 0:00:21,750\nCaption <i>text</i>\n\n2\n0:01:00,040 --> 0:01:02,080\nSecond caption\n",
            SubtitleTextNormalizer.normalize(raw.toByteArray()).toString(Charsets.UTF_8),
        )
    }

    @Test
    fun retainsBomAndCrlfAndPadsDecimalPlaces() {
        val raw = "\uFEFF1\r\n0:00:01.1 --> 0:00:02.02\r\nCaption\r\n"
        assertEquals(
            "\uFEFF1\r\n0:00:01,100 --> 0:00:02,020\r\nCaption\r\n",
            SubtitleTextNormalizer.normalize(raw.toByteArray()).toString(Charsets.UTF_8),
        )
    }

    @Test
    fun leavesStandardSubRipAndOtherFormatsUntouched() {
        listOf(
            "1\n00:00:01,001 --> 00:00:02,002\nCaption\n",
            "WEBVTT\n\n1\n00:01.000 --> 00:02.000\nCaption\n",
            "[Script Info]\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Caption",
            "<?xml version=\"1.0\"?><tt><body/></tt>",
            "<!doctype html><html>Denied</html>",
        ).forEach { raw ->
            val bytes = raw.toByteArray()
            assertSame(bytes, SubtitleTextNormalizer.normalize(bytes))
        }
    }

    @Test
    fun leavesNonUtf8TextUntouched() {
        val bytes = "1\n0:00:01.1 --> 0:00:02.2\n".toByteArray() + byteArrayOf(0xE9.toByte())
        assertSame(bytes, SubtitleTextNormalizer.normalize(bytes))
    }

    @Test
    fun repeatedNormalizationDoesNotChangePreparedBytes() {
        val prepared = SubtitleTextNormalizer.normalize("1\n0:00:01.1 --> 0:00:02.2\nCaption\n".toByteArray())
        assertSame(prepared, SubtitleTextNormalizer.normalize(prepared))
    }
}
