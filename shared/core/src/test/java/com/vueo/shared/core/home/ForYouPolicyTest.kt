package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouPolicyTest {
    private val now = 1_800_000_000_000L
    private val cooking = MediaItem("seed", "series", "Kitchen", description =
        "Chefs compete cooking recipes restaurant kitchens culinary food competitions", genres = listOf("Reality"))
    private val chef = cooking.copy(id = "chef", name = "Recipe Competition", description =
        "Restaurant chefs compete cooking culinary recipes food kitchens competitions")
    private val police = cooking.copy(id = "police", name = "Police Patrol", description =
        "Police patrol officers pursue suspects crime arrest investigations streets")
    private fun watched(media: MediaItem, last: Long = now) = LibraryPlaybackEntry(media, media.id,
        positionMs = 3_600_000, durationMs = 3_600_000, lastWatchedEpochMs = last)

    @Test fun repeatedEpisodesDoNotMultiplyOneSeries() {
        val one = ForYouPolicy.seeds(listOf(watched(cooking)), emptyList(), now)
        assertEquals(one, ForYouPolicy.seeds(List(30) { watched(cooking) }, emptyList(), now))
        assertTrue(ForYouPolicy.seeds(listOf(watched(cooking, now - 90L * 86_400_000)), emptyList(), now).single().weight < one.single().weight)
    }
    @Test fun synopsisSeparatesDifferentInterestsWithTheSameGenre() {
        val history = listOf(watched(cooking))
        val result = ForYouPolicy.rank(ForYouPolicy.seeds(history, emptyList(), now), emptyList(),
            listOf(police, chef, cooking), history, emptyList())
        assertEquals("chef", result.first().id)
        assertFalse(result.any { it.id == cooking.id })
    }
    @Test fun newProfilesGetStableGeneralSuggestions() {
        val first = ForYouPolicy.rank(emptyList(), emptyList(), listOf(chef, police), emptyList(), emptyList())
        assertTrue(first.isNotEmpty())
        assertEquals(first, ForYouPolicy.rank(emptyList(), emptyList(), listOf(chef, police), emptyList(), emptyList()))
    }
    @Test fun oneVoteDoesNotOutrankAnEstablishedRating() {
        val tiny = chef.copy(id = "tiny", recommendationRating = 10.0, recommendationVoteCount = 1, popularity = 100.0)
        val reliable = chef.copy(id = "reliable", recommendationRating = 8.0, recommendationVoteCount = 5000, popularity = 100.0)
        assertEquals("reliable", ForYouPolicy.rank(emptyList(), emptyList(), listOf(tiny, reliable), emptyList(), emptyList()).first().id)
    }
    @Test fun reconcilesKnownTitleYearAcrossIdsButKeepsRemakes() {
        val imdb = cooking.copy(id = "tt123", releaseInfo = "2020")
        val tmdb = cooking.copy(id = "tmdb:99", releaseInfo = "2020")
        assertTrue(ForYouPolicy.rank(emptyList(), emptyList(), listOf(tmdb), listOf(watched(imdb)), emptyList()).isEmpty())
        assertEquals(1, ForYouPolicy.rank(emptyList(), emptyList(), listOf(imdb, tmdb), emptyList(), emptyList()).size)
        assertEquals(2, ForYouPolicy.rank(emptyList(), emptyList(), listOf(imdb, tmdb.copy(releaseInfo = "2021")), emptyList(), emptyList()).size)
    }
    @Test fun remotePoolDoesNotDependOnHomeCatalogPresence() {
        val history = listOf(watched(cooking))
        val seeds = ForYouPolicy.seeds(history, emptyList(), now)
        val batch = ForYouPolicy.RelatedBatch(seeds.single(), listOf(chef, police))
        assertTrue(ForYouPolicy.rank(seeds, listOf(batch), emptyList(), history, emptyList()).isNotEmpty())
        assertEquals(1, ForYouPolicy.rank(seeds, listOf(batch), listOf(chef), history, emptyList()).count { it.id == "chef" })
        assertFalse(ForYouPolicy.rank(seeds, listOf(batch), listOf(chef), history, listOf(chef)).any { it.id == "chef" })
    }
}
