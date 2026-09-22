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

    @Test
    fun `late track remains pending when it introduces a new language`() {
        val english = subtitle("en", "https://subs.example/en.srt")
        val malayAi = subtitle("ms", "https://subs.example/ai-ms.vtt")

        assertEquals(
            listOf(malayAi),
            PlayerSubtitleUpdatePolicy.pendingExternalTracks(
                discovered = listOf(english, malayAi),
                materializedSelectionIds = setOf(
                    PlayerTrackPolicy.externalSubtitleSelectionId(english),
                ),
            ),
        )
    }

    @Test
    fun `materialized external track is not duplicated`() {
        val malayAi = subtitle("ms", "https://subs.example/ai-ms.vtt")

        assertEquals(
            emptyList<SubtitleTrack>(),
            PlayerSubtitleUpdatePolicy.pendingExternalTracks(
                discovered = listOf(malayAi, malayAi),
                materializedSelectionIds = setOf(
                    PlayerTrackPolicy.externalSubtitleSelectionId(malayAi),
                ),
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
