package com.vueo.shared.core.player

import com.vueo.shared.core.language.LanguagePolicy
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
}
