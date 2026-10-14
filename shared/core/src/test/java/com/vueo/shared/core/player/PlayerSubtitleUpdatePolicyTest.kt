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
    fun `subtitle source keys change when a late language arrives`() {
        val english = subtitle("en", "https://subs.example/en.srt")
        val malay = subtitle("ms", "https://subs.example/ms.srt")

        val initial = PlayerSubtitleUpdatePolicy.sourceKeys(listOf(english))
        val updated = PlayerSubtitleUpdatePolicy.sourceKeys(listOf(english, malay))

        assertEquals(false, initial == updated)
        assertEquals(2, updated.size)
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
    fun `late generated tracks require different source keys but addon changes do not`() {
        val addon = subtitle("ms", "https://addon.example/a.vtt")
        val ai = subtitle("ms", "https://vueo.example/translated/a.vtt")
            .copy(providerId = "vueo-smartsubs-ai")
        assertEquals(emptyList<String>(), PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(addon)))
        assertEquals(1, PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(addon, ai)).size)
        assertEquals(
            PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(ai)),
            PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(addon, ai)),
        )
        assertEquals(false,
            PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(ai)) ==
                PlayerSubtitleUpdatePolicy.smartSubsSourceKeys(listOf(ai.copy(url = ai.url + "?new=1"))),
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
