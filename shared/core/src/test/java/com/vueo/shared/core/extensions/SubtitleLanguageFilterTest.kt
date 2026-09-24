package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleLanguageFilterTest {
    @Test
    fun `preferred-only keeps preferred and secondary language aliases`() {
        val tracks = listOf(
            subtitle("Malay"),
            subtitle("eng"),
            subtitle("Spanish"),
            subtitle("und"),
        )

        assertEquals(
            listOf("Malay", "eng"),
            filterSubtitleTracksByLanguage(
                tracks = tracks,
                allowedLanguageCodes = setOf("ms", "en"),
            ).map(SubtitleTrack::language),
        )
    }

    @Test
    fun `show-all keeps every discovered track`() {
        val tracks = listOf(
            subtitle("ms"),
            subtitle("en"),
            subtitle("und"),
        )

        assertEquals(
            tracks,
            filterSubtitleTracksByLanguage(
                tracks = tracks,
                allowedLanguageCodes = null,
            ),
        )
    }

    private fun subtitle(language: String) =
        SubtitleTrack(
            id = language,
            language = language,
            url = "https://subtitles.example/$language.srt",
            providerId = "test",
            providerName = "Test",
        )
}
