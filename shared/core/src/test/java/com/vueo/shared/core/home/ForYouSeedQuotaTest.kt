package com.vueo.shared.core.home

import com.vueo.shared.core.media.AnimeMediaPolicy
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import org.junit.Assert.*
import org.junit.Test

class ForYouSeedQuotaTest {
    private val now = 1_800_000_000_000L
    private fun item(id: String, type: String = "movie", anime: Boolean = false) =
        MediaItem(id, type, id, genres = if (anime) listOf("Animation") else listOf("Drama"),
            originalLanguage = if (anime) "ja" else "en")
    private fun watched(item: MediaItem, age: Long = 0) = LibraryPlaybackEntry(item, item.id,
        positionMs = 3_600_000, durationMs = 3_600_000, lastWatchedEpochMs = now - age)
    private fun ids(history: List<LibraryPlaybackEntry>, favorites: List<MediaItem> = emptyList()) =
        ForYouPolicy.seeds(history, favorites, now).map { it.media.id }

    @Test fun reservesTwoRegularAndOneAnimeForEachTypeByRecency() {
        val history = listOf(
            watched(item("m-old"), 30), watched(item("m1"), 1), watched(item("m2"), 2),
            watched(item("am-old", anime = true), 40), watched(item("am", anime = true), 20),
            watched(item("s1", "series"), 3), watched(item("s2", "tv"), 4),
            watched(item("s-old", "series"), 31), watched(item("as", "series", true), 21),
        )
        assertEquals(listOf("m1", "m2", "s1", "s2", "am", "as"), ids(history))
        assertEquals(ids(history), ids(history.reversed()))
    }
    @Test fun repeatedEpisodesAndPreviewsDoNotUseExtraSlots() {
        val series = item("series", "series")
        val history = (1..20).map { watched(series, it.toLong()).copy(videoId = "episode-$it") } +
            watched(item("preview")).copy(positionMs = 10_000) + watched(item("movie"), 30)
        assertEquals(listOf("series", "movie"), ids(history))
    }
    @Test fun favoritesNeverIntroduceUnwatchedSeeds() {
        assertTrue(ids(emptyList(), listOf(item("favorite"))).isEmpty())
        assertEquals(listOf("watched"), ids(listOf(watched(item("watched"))), listOf(item("favorite"))))
    }
    @Test fun missingAnimeUsesRecentRegularTitlesAndMissingTypeFillsToSix() {
        val movies = (1..8).map { watched(item("m$it"), it.toLong()) }
        assertEquals((1..6).map { "m$it" }, ids(movies))
        val series = (1..4).map { watched(item("s$it", "series"), (it + 10).toLong()) }
        assertEquals(setOf("m1", "m2", "m3", "s1", "s2", "s3"), ids(movies + series).toSet())
        assertEquals(listOf("m1"), ids(movies.take(1)))
    }
    @Test fun westernAnimationIsNotAnAnimeSeed() {
        assertFalse(AnimeMediaPolicy.isAnime(item("cartoon").copy(genres = listOf("Animation"))))
        assertTrue(AnimeMediaPolicy.isAnime(item("anime", anime = true)))
        assertTrue(AnimeMediaPolicy.isAnime(item("tagged").copy(genres = listOf("Anime"))))
        assertTrue(AnimeMediaPolicy.isAnime(item("country").copy(genres = listOf("Animation"), countries = listOf("Japan"))))
    }
    @Test fun richerWatchedMetadataCanIdentifyAnimeWithoutAnExtraRequest() {
        val sparse = item("am").copy(originalLanguage = null)
        val rich = sparse.copy(genres = listOf("Animation"), originalLanguage = "ja")
        val history = listOf(watched(sparse), watched(rich, 1)) +
            (1..3).map { watched(item("m$it"), it.toLong()) }
        assertEquals(setOf("am", "m1", "m2", "m3"), ids(history).toSet())
        assertTrue(AnimeMediaPolicy.isAnime(ForYouPolicy.seeds(history, emptyList(), now).first().media))
    }
}
