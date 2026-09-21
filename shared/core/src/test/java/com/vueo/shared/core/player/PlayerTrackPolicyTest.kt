package com.vueo.shared.core.player

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerTrackPolicyTest {
    @Test
    fun languageAliasesResolveToSameCanonicalCode() {
        assertEquals("en", LanguagePolicy.canonicalCode("English"))
        assertEquals("en", LanguagePolicy.canonicalCode("eng"))
        assertEquals("en", LanguagePolicy.canonicalCode("English CC"))
        assertEquals("ms", LanguagePolicy.canonicalCode("Bahasa Malaysia"))
    }

    @Test
    fun builtInTrackIdsRemainUniqueAcrossGroups() {
        val first = PlayerTrackPolicy.builtinSubtitleSelectionId(
            language = "English",
            formatLabel = "English",
            trackId = null,
            groupIndex = 0,
            trackIndex = 0,
        )
        val second = PlayerTrackPolicy.builtinSubtitleSelectionId(
            language = "eng",
            formatLabel = "English",
            trackId = null,
            groupIndex = 1,
            trackIndex = 0,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun globalOffOverridesAnOlderContentTrack() {
        assertEquals(
            PlayerTrackPolicy.SUBTITLE_OFF,
            PlayerTrackPolicy.resolvedSubtitleSelection(
                globalSelection = PlayerTrackPolicy.SUBTITLE_OFF,
                contentSelection = "external:addon:old-track:123",
            ),
        )
    }

    @Test
    fun externalTrackIdSurvivesTranslatedUrlChange() {
        val preparing = externalSubtitle(
            id = "smartsubs-auto-1",
            url = "https://subs.example/preparing/1",
        )
        val ready = preparing.copy(
            url = "https://subs.example/ready/1.srt",
        )

        assertEquals(
            PlayerTrackPolicy.externalSubtitleSelectionId(preparing),
            PlayerTrackPolicy.externalSubtitleSelectionId(ready),
        )
    }

    private fun externalSubtitle(
        id: String,
        url: String,
    ) = SubtitleTrack(
        id = id,
        language = "ms",
        url = url,
        providerId = "smartsubs",
        providerName = "SmartSubs",
    )
}
