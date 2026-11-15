package com.vueo.shared.core.detail

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailUpstreamSeasonZeroTest {
    private fun normalize(vararg episodes: EpisodeItem) = DetailUpstreamPolicy.normalizeSeriesEpisodes(
        MediaItem(id = "tt123", type = "series", name = "Test", episodes = episodes.toList()),
    ).episodes

    @Test fun specialsOnlyStaySeasonZeroAndKeepIds() {
        val actual = normalize(
            EpisodeItem("tt123:0:2", "Special B", 0, 2),
            EpisodeItem("tt123:0:1", "Special A", 0, 1),
        )
        assertEquals(listOf(0, 0), actual.map { it.season })
        assertEquals(listOf("tt123:0:1", "tt123:0:2"), actual.map { it.id })
    }
    @Test fun explicitZeroWinsOverConflictingIdSeason() {
        assertEquals(0, normalize(EpisodeItem("tt123:1:1", "Special", 0, 1)).single().season)
    }
    @Test fun missingSeasonCanBeRecoveredAsZeroFromId() {
        assertEquals(0, normalize(EpisodeItem("tt123:0:1", "Special", -1, 1)).single().season)
    }
    @Test fun regularSeasonsAndMixedSpecialsArePreserved() {
        val actual = normalize(
            EpisodeItem("tt123:2:1", "Regular", 2, 1),
            EpisodeItem("tt123:0:1", "Special", 0, 1),
            EpisodeItem("tt123:1:1", "Inferred", -1, 1),
        )
        assertEquals(listOf(0, 1, 2), actual.map { it.season })
    }
}
