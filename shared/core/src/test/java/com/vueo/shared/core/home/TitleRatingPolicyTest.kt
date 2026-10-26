package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.TitleRating
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import org.junit.Assert.*
import org.junit.Test

class TitleRatingPolicyTest {
    private val drama = MediaItem("tt100", "movie", "Drama", genres = listOf("Drama"))
    private val action = MediaItem("tt200", "movie", "Action", genres = listOf("Action"))

    @Test fun likeCanSeedWithoutHistoryOrWatchlist() {
        val seeds = ForYouPolicy.seeds(emptyList(), emptyList(), ratings = listOf(TitleRating(drama, 1)))
        assertEquals(listOf(drama), seeds.map { it.media })
    }
    @Test fun dislikeOverridesHistoryAndWatchlistSeeds() {
        val history = listOf(LibraryPlaybackEntry(drama, drama.id, positionMs = 100000, durationMs = 100000))
        assertTrue(ForYouPolicy.seeds(history, listOf(drama), ratings = listOf(TitleRating(drama, -1))).isEmpty())
    }
    @Test fun explicitLikeHasMoreWeightThanSavedTitle() {
        val seeds = ForYouPolicy.seeds(emptyList(), listOf(drama, action), ratings = listOf(TitleRating(action, 1)))
        assertEquals(action.id, seeds.first().media.id)
    }
    @Test fun ratedTitlesAreNotRecommendedAgain() {
        assertTrue(ForYouPolicy.rank(emptyList(), emptyList(), listOf(drama), emptyList(), emptyList(),
            ratings = listOf(TitleRating(drama, -1))).isEmpty())
    }
    @Test fun dislikeDemotesSimilarGenresWithoutBlockingTheGenre() {
        val similar = drama.copy(id = "tt300")
        val result = ForYouPolicy.rank(emptyList(), emptyList(), listOf(similar, action), emptyList(), emptyList(),
            ratings = listOf(TitleRating(drama, -1)))
        assertEquals(action.id, result.first().id)
        assertTrue(result.contains(similar))
    }
}
