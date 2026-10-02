package com.vueo.shared.core.storage

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ContinueWatchingPolicyTest {
    private val now = Instant.parse("2026-10-02T00:00:00Z").toEpochMilli()
    private fun ep(number: Int, season: Int = 1, released: String? = null) =
        EpisodeItem("show:$season:$number", "Episode $number", season, number, released)
    private fun series(episodes: List<EpisodeItem> = (1..12).map { ep(it) }) =
        MediaItem("show", "series", "Show", episodes = episodes)
    private fun watched(media: MediaItem = series(), number: Int = 1, position: Long = 600_000L,
                        time: Long = 1L, season: Int = 1) =
        LibraryPlaybackEntry(media, "show:$season:$number", "Episode $number", season, number,
            position, 600_000L, time)
    private fun resolve(vararg entries: LibraryPlaybackEntry) = ContinueWatchingPolicy.resolve(entries.toList(), now)

    @Test fun completedFirstEpisodeOffersNextAtZero() {
        val result = resolve(watched()).single()
        assertEquals(2, result.episode)
        assertEquals("show:1:2", result.videoId)
        assertEquals(0L, result.positionMs)
        assertEquals(0L, result.durationMs)
        assertEquals(1L, result.lastWatchedEpochMs)
    }
    @Test fun shortNewEpisodeKeepsItsOwnPosition() {
        val result = resolve(watched(), watched(number = 2, position = 1_200L, time = 2L)).single()
        assertEquals(2, result.episode)
        assertEquals(1_200L, result.positionMs)
        assertTrue(ContinueWatchingPolicy.canResume(result))
    }
    @Test fun zeroPositionNewEpisodeDoesNotEraseSeries() {
        assertEquals(2, resolve(watched(), watched(number = 2, position = 0L, time = 2L)).single().episode)
    }
    @Test fun caughtUpSeriesWaitsUntilMetadataContainsNewEpisode() {
        val caughtUp = watched(series(listOf(ep(1), ep(2))), number = 2)
        assertTrue(resolve(caughtUp).isEmpty())
        val updated = caughtUp.copy(media = series(listOf(ep(1), ep(2), ep(3))))
        assertEquals(3, resolve(updated).single().episode)
    }
    @Test fun futureReleaseIsNotOffered() {
        val show = series(listOf(ep(1), ep(2, released = "2026-10-09T00:00:00Z")))
        assertTrue(resolve(watched(show)).isEmpty())
    }
    @Test fun releasedEpisodeIsOfferedAndDateOnlyIsSupported() {
        val show = series(listOf(ep(1), ep(2, released = "2026-10-01")))
        assertEquals(2, resolve(watched(show)).single().episode)
    }
    @Test fun nextSeasonCanBeOffered() {
        val show = series(listOf(ep(1), ep(2), ep(1, season = 2)))
        val result = resolve(watched(show, number = 2)).single()
        assertEquals(2, result.season)
        assertEquals(1, result.episode)
    }
    @Test fun completedEpisodesAreSkippedWhenRevisitingEarlierEpisode() {
        val result = resolve(watched(number = 2, time = 1L), watched(number = 1, time = 2L)).single()
        assertEquals(3, result.episode)
    }
    @Test fun movieStillRequiresMoreThanFiveSeconds() {
        val movie = MediaItem("movie", "movie", "Movie")
        val entry = LibraryPlaybackEntry(movie, "movie", positionMs = 5_000L, durationMs = 600_000L)
        assertTrue(resolve(entry).isEmpty())
        assertEquals(1, resolve(entry.copy(positionMs = 5_001L)).size)
        assertTrue(resolve(entry.copy(positionMs = 570_000L)).isEmpty())
    }
    @Test fun newestCursorWinsOverAnOlderCompletedSnapshot() {
        val older = watched()
        val newer = older.copy(positionMs = 2_000L, lastWatchedEpochMs = 2L)
        assertEquals(2_000L, resolve(older, newer).single().positionMs)
    }
    @Test fun allTitlesAreReturnedInRecentOrder() {
        val entries = (1..20).map { i ->
            val movie = MediaItem("movie$i", "movie", "Movie $i")
            LibraryPlaybackEntry(movie, movie.id, positionMs = 6_000L, durationMs = 600_000L,
                lastWatchedEpochMs = i.toLong())
        }
        val result = ContinueWatchingPolicy.resolve(entries, now)
        assertEquals(20, result.size)
        assertEquals("movie20", result.first().media.id)
    }
    @Test fun detailTargetsTheShortEpisodeAndThenTheNextEpisode() {
        val show = series()
        assertEquals(2, ContinueWatchingPolicy.targetEpisode(show,
            listOf(watched(), watched(number = 2, position = 1_000L, time = 2L)), null)?.episode)
        assertEquals(2, ContinueWatchingPolicy.targetEpisode(show, listOf(watched()), null)?.episode)
    }
}
