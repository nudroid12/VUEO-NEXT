package com.vueo.shared.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFormatPolicyTest {
    @Test
    fun detectsFormatFromDeclaredMimeType() {
        assertEquals(
            SubtitleFormat.WEBVTT,
            SubtitleFormatPolicy.detect(
                url = "https://subtitles.example/download/42",
                declaredMimeType = "text/vtt; charset=utf-8",
            ),
        )
    }

    @Test
    fun detectsFormatFromEncodedPathAndQuery() {
        assertEquals(
            SubtitleFormat.SSA,
            SubtitleFormatPolicy.detect("https://subtitles.example/file%2Eass?token=abc"),
        )
        assertEquals(
            SubtitleFormat.TTML,
            SubtitleFormatPolicy.detect("https://subtitles.example/download/42?format=ttml"),
        )
    }

    @Test
    fun defaultsUnknownFormatToSubRip() {
        assertEquals(
            SubtitleFormat.SUBRIP,
            SubtitleFormatPolicy.detect("https://subtitles.example/download/42"),
        )
    }
}
