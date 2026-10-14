package com.vueo.shared.core.home

import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import org.junit.Assert.*
import org.junit.Test

class ForYouSeedQuotaTest {
    private val now = 1_800_000_000_000L
    private fun item(n: Int, genre: String = "Drama", type: String = "movie") =
        MediaItem("tt$n", type, "Title $n", genres = listOf(genre), releaseInfo = "2020")
    private fun watched(item: MediaItem, days: Long = 0) = LibraryPlaybackEntry(item, item.id,
        positionMs = 3_600_000, durationMs = 3_600_000, lastWatchedEpochMs = now - days * 86_400_000L)
    @Test fun favoritesCanSeedWithoutAnyWatchHistory() {
        val favorite = item(1)
        assertEquals(listOf(favorite), ForYouPolicy.seeds(emptyList(), listOf(favorite), now).map { it.media })
    }
    @Test fun repeatedEpisodesAndPreviewsDoNotUseExtraSlots() {
        val series = item(1, type = "series")
        val history = (1..20).map { watched(series).copy(videoId = "episode-$it") } +
            watched(item(2)).copy(positionMs = 10_000)
        assertEquals(listOf(series), ForYouPolicy.seeds(history, emptyList(), now).map { it.media })
    }
    @Test fun seedSelectionIsDeterministicAndNotTheLatestSix() {
        val history = (1..10).map { watched(item(it), it.toLong()) } + watched(item(20, "Comedy"), 60)
        val first = ForYouPolicy.seeds(history, emptyList(), now)
        assertEquals(first, ForYouPolicy.seeds(history.reversed(), emptyList(), now))
        assertEquals(6, first.size)
        assertTrue(first.any { it.media.id == "tt20" })
    }
    @Test fun noForcedMovieAndSeriesQuota() {
        val movies = (1..8).map { watched(item(it), it.toLong()) }
        assertEquals(6, ForYouPolicy.seeds(movies, emptyList(), now).size)
        assertEquals(1, ForYouPolicy.seeds(movies.take(1), emptyList(), now).size)
    }
    @Test fun unsupportedIdsDoNotWasteRequestSlotsAndAliasesCollapse() {
        val media = item(1)
        val seeds = ForYouPolicy.seeds(listOf(watched(media)), listOf(
            media.copy(id = "tmdb:1"), item(2).copy(id = "addon-only")), now)
        assertEquals(1, seeds.size)
    }
    @Test fun sharedSnapshotContainsUnwatchedFavorites() {
        val dna = UserDnaEngine.analyzeSnapshot(emptyList(), listOf(item(1, "Comedy")), now)
        assertEquals("Comedy", dna.topGenres.single().name)
        assertEquals(1, dna.behavior.myListTitles)
        assertEquals(dna, ForYouPolicy.profile(emptyList(), listOf(item(1, "Comedy")), now))
    }
    @Test fun multiSeedSupportWinsEquivalentSingleSeedCandidates() {
        val history = listOf(watched(item(1)), watched(item(2)))
        val seeds = ForYouPolicy.seeds(history, emptyList(), now)
        val shared = item(3); val single = item(4)
        val batches = listOf(ForYouPolicy.RelatedBatch(seeds[0], listOf(single, shared)),
            ForYouPolicy.RelatedBatch(seeds[1], listOf(shared)))
        assertEquals(shared, ForYouPolicy.rank(seeds, batches, emptyList(), history, emptyList()).first())
    }
    @Test fun oneSeedCannotFillTheRowWhenOtherInterestsHaveCandidates() {
        val history = listOf(watched(item(1)), watched(item(2, "Comedy")), watched(item(3, "Mystery")))
        val seeds = ForYouPolicy.seeds(history, emptyList(), now)
        val batches = seeds.mapIndexed { index, seed -> ForYouPolicy.RelatedBatch(seed,
            (1..18).map { item(100 + index * 20 + it, seed.media.genres.first()) }) }
        val result = ForYouPolicy.rank(seeds, batches, emptyList(), history, emptyList(), limit = 12)
        assertEquals(12, result.size)
        batches.forEach { batch -> assertTrue(result.count { it in batch.items } <= 4) }
    }
}
