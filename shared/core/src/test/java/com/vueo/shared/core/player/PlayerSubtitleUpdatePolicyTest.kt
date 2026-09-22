package com.vueo.shared.core.player

import com.vueo.shared.core.media.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSubtitleUpdatePolicyTest {
    @Test
    fun `subtitle source keys do not change when provider order changes`() {
        val english = subtitle("en", "https://subs.example/en.srt")
        val malay = subtitle("ms", "https://subs.example/ms.srt")

        assertEquals(
            PlayerSubtitleUpdatePolicy.sourceKeys(listOf(english, malay)),
            PlayerSubtitleUpdatePolicy.sourceKeys(listOf(malay, english)),
        )
    }

    @Test
    fun `stable resume uses last known position during transient reset`() {
        assertEquals(
            82_000L,
            PlayerSubtitleUpdatePolicy.stableResumePositionMs(
                currentPositionMs = 0L,
                lastKnownPositionMs = 82_000L,
            ),
        )
    }

    @Test
    fun `stable resume follows a valid current position after seeking`() {
        assertEquals(
            25_000L,
            PlayerSubtitleUpdatePolicy.stableResumePositionMs(
                currentPositionMs = 25_000L,
                lastKnownPositionMs = 82_000L,
            ),
        )
    }

    private fun subtitle(language: String, url: String) =
        SubtitleTrack(
            id = url,
            language = language,
            url = url,
            providerId = "test",
            providerName = "Test",
        )
}
