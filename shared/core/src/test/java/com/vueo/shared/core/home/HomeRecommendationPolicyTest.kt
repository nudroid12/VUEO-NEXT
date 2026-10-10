package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationPolicyTest {
    private fun media(id: String, genre: String = "Drama", name: String = id) =
        MediaItem(id = id, type = "movie", name = name, genres = listOf(genre))

    private fun entry(id: String, position: Long, duration: Long, recent: Long = 0) =
        LibraryPlaybackEntry(media(id), id, positionMs = position, durationMs = duration, lastWatchedEpochMs = recent)

    @Test fun previewAndTwoMinuteOpenDoNotCountAsWatched() {
        assertFalse(HomeRecommendationPolicy.isMeaningfullyWatched(entry("preview", 30_000, 7_200_000)))
        assertFalse(HomeRecommendationPolicy.isMeaningfullyWatched(entry("opened", 120_000, 7_200_000)))
        assertFalse(HomeRecommendationPolicy.isMeaningfullyWatched(entry("fiveMinutes", 300_000, 7_200_000)))
    }

    @Test fun meaningfulProgressCompletionAndUnknownDurationHaveSeparateRules() {
        assertTrue(HomeRecommendationPolicy.isMeaningfullyWatched(entry("episode", 360_000, 1_800_000)))
        assertTrue(HomeRecommendationPolicy.isMeaningfullyWatched(entry("shortComplete", 60_000, 60_000)))
        assertFalse(HomeRecommendationPolicy.isMeaningfullyWatched(entry("unknownShort", 599_999, 0)))
        assertTrue(HomeRecommendationPolicy.isMeaningfullyWatched(entry("unknownLong", 600_000, 0)))
    }

    @Test fun newestPreviewDoesNotReplaceMeaningfulSeedEvenWithUnsortedHistory() {
        val history = listOf(entry("older", 360_000, 1_800_000, 10), entry("preview", 120_000, 7_200_000, 30), entry("newer", 360_000, 1_800_000, 20))
        assertEquals("newer", HomeRecommendationPolicy.latestMeaningfulSeed(history)?.id)
    }

    @Test fun diversityKeepsTopMatchAndPromotesAnotherRelevantGenre() {
        val ranked = listOf(media("top") to 90, media("same") to 89, media("different", "Comedy") to 87)
        assertEquals(listOf("top", "different", "same"), HomeRecommendationPolicy.diversify(ranked, 3).map { it.id })
    }

    @Test fun diversityDoesNotPadWithLowMatchesOrDropResultsWhenOnlyOneGenreExists() {
        val ranked = listOf(media("a") to 80, media("b") to 79, media("c") to 78, media("bad", "Comedy") to 54)
        assertEquals(listOf("a", "b", "c"), HomeRecommendationPolicy.diversify(ranked, 12).map { it.id })
        assertEquals(emptyList<MediaItem>(), HomeRecommendationPolicy.diversify(ranked, 0))
    }

    @Test fun explicitFamilyGetsSoftPenaltyAndDuplicateIdsAppearOnce() {
        val ranked = listOf(media("a", name = "Shared Family: One") to 90, media("b", name = "Shared Family: Two") to 89, media("c", name = "Different Story") to 88, media("a") to 85)
        assertEquals(listOf("a", "c", "b"), HomeRecommendationPolicy.diversify(ranked, 12).map { it.id })
    }
}
