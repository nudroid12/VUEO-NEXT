package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.RecommendationEngine
import org.junit.Assert.*
import org.junit.Test

class HomeRecommendationFeedsTest {
    private val now = 1_800_000_000_000L
    private fun media(id: String, name: String = id) = MediaItem(id, "movie", name, releaseInfo = "2020")
    private fun watched(item: MediaItem, time: Long = now) = LibraryPlaybackEntry(item, item.id,
        positionMs = 3_600_000, durationMs = 3_600_000, lastWatchedEpochMs = time)
    private fun request(history: List<LibraryPlaybackEntry>, favorites: List<MediaItem> = emptyList()) =
        HomeRecommendationFeeds.Request("profile", history, favorites, RecommendationEngine.TMDB,
            "tmdb", "trakt", true, true)

    @Test fun freshAndRetryWindowsExpireAndRejectFutureClocks() {
        assertTrue(HomeRecommendationFeeds.fresh(now - 1, false, now))
        assertFalse(HomeRecommendationFeeds.fresh(now - HomeRecommendationFeeds.FRESH_MS, false, now))
        assertFalse(HomeRecommendationFeeds.fresh(now - HomeRecommendationFeeds.RETRY_MS, true, now))
        assertFalse(HomeRecommendationFeeds.fresh(now + 1, false, now))
    }
    @Test fun cachedForYouNeverRestoresWatchedOrFavoriteAliases() {
        val item = media("tt001", "Original Movie")
        val favorite = media("tt002")
        val alias = item.copy(id = "tmdb:123")
        val other = media("tt003")
        val cached = HomeRecommendationSections(forYou = listOf(alias, favorite, other))
        val visible = HomeRecommendationFeeds.visible(cached, request(listOf(watched(item)), listOf(favorite)))
        assertEquals(listOf(other), visible.forYou)
    }
    @Test fun changedSeedCannotReusePreviousBecauseResults() {
        val old = media("tt001")
        val current = media("tt002")
        val cached = HomeRecommendationSections(becauseYouWatchedSeed = old, becauseYouWatched = listOf(media("tt003")))
        val visible = HomeRecommendationFeeds.visible(cached, request(listOf(watched(old, now - 1), watched(current))))
        assertEquals(current, visible.becauseYouWatchedSeed)
        assertTrue(visible.becauseYouWatched.isEmpty())
    }
    @Test fun meaningfulLibraryChangesInvalidateRequestSignatures() {
        val original = request(listOf(watched(media("tt001"))))
        val changed = request(original.history + watched(media("tt002"), now + 1))
        assertNotEquals(HomeRecommendationFeeds.requestSignatures(original, now), HomeRecommendationFeeds.requestSignatures(changed, now))
        assertEquals(HomeRecommendationFeeds.requestSignatures(original, now), HomeRecommendationFeeds.requestSignatures(original, now))
    }

    @Test fun rowFlagsHideOnlyTheirOwnCachedSection() {
        val seed = media("tt001")
        val candidate = media("tt002")
        val cached = HomeRecommendationSections(listOf(candidate), seed, listOf(candidate))
        val input = request(listOf(watched(seed)))
        val becauseOnly = HomeRecommendationFeeds.visible(cached, input.copy(forYouEnabled = false))
        assertTrue(becauseOnly.forYou.isEmpty())
        assertEquals(listOf(candidate), becauseOnly.becauseYouWatched)
        val forYouOnly = HomeRecommendationFeeds.visible(cached, input.copy(becauseEnabled = false))
        assertEquals(listOf(candidate), forYouOnly.forYou)
        assertTrue(forYouOnly.becauseYouWatched.isEmpty())
        assertNull(forYouOnly.becauseYouWatchedSeed)
        val off = HomeRecommendationFeeds.visible(cached, input.copy(forYouEnabled = false, becauseEnabled = false))
        assertTrue(off.forYou.isEmpty() && off.becauseYouWatched.isEmpty())
    }
}
