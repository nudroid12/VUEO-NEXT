package com.vueo.shared.core.player

import com.vueo.shared.core.plugin.AdaptiveProviderOrder
import org.junit.Assert.*
import org.junit.Test

class AutomaticSubtitlePolicyTest {
    private data class Track(val name: String, val id: String, val language: String)
    private fun choose(tracks: List<Track>) = AutomaticSubtitlePolicy.choose(tracks, listOf("ms", "en"),
        { it.language }, { !AutomaticSubtitlePolicy.isAi(it.name, it.id) })
    @Test fun aiByNameOrIdNeverWinsAutomaticSelection() {
        val english = Track("English", "eng-1", "en")
        assertEquals(english, choose(listOf(Track("Malay AI", "ms-1", "ms"), english)))
        assertEquals(english, choose(listOf(Track("Malay", "subtitle-aI-1", "ms"), english)))
        assertNull(choose(listOf(Track("Malay AI", "ms-1", "ms"))))
    }
    @Test fun readyOrdinaryLastLanguageWinsAndInputPreferencesStayUnchanged() {
        val malay = Track("Malay", "ms-1", "ms")
        assertEquals(malay, choose(listOf(Track("English", "eng-1", "en"), malay)))
        assertFalse(AutomaticSubtitlePolicy.isAi("Malay", "ms-1"))
    }
    @Test fun appropriateProviderTierPrecedesUnknownOrOppositeContent() {
        assertTrue(AdaptiveProviderOrder.tier(true, setOf("anime")) < AdaptiveProviderOrder.tier(true, setOf("movie")))
        assertTrue(AdaptiveProviderOrder.tier(false, setOf("movie")) < AdaptiveProviderOrder.tier(false, setOf("anime", "movie")))
        assertTrue(AdaptiveProviderOrder.tier(false, emptySet()) < AdaptiveProviderOrder.tier(false, setOf("anime")))
    }
}
