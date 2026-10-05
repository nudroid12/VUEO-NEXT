package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import org.junit.Assert.*
import org.junit.Test

class NextEpisodeSourcePolicyTest {
    private fun source(provider: String, server: String, episode: Int = 1) = StreamSource(
        name = "$server 1080p", serverName = server, quality = "1080p",
        providerId = provider, providerName = provider,
        url = "https://example.test/$provider/$server/$episode.mp4",
    )
    private val current = source("Repo / Provider", "Server A")
    private fun select(sources: List<StreamSource>, done: Boolean = false, completed: Boolean = false) =
        NextEpisodeSourcePolicy.select(sources, current, done, completed, "1080p", null)

    @Test fun fasterOtherProviderWaitsForCurrentProvider() {
        assertNull(select(listOf(source("Other", "Server B", 2))))
    }
    @Test fun lateMatchingServerWinsBeforeAllProvidersFinish() {
        val matching = source("Repo / Provider", "Server A", 2)
        assertEquals(matching, select(listOf(source("Other", "Server B", 2), matching)))
    }
    @Test fun sameProviderDifferentServerWaitsWhileCurrentServerCanStillArrive() {
        assertNull(select(listOf(source("Repo / Provider", "Server B", 2))))
    }
    @Test fun providerCompletionUnlocksFallbackWithoutWaitingForAllProviders() {
        val fallback = source("Other", "Server B", 2)
        assertEquals(fallback, select(listOf(fallback), done = true))
    }
    @Test fun identicalServerLabelFromAnotherProviderDoesNotCountAsMatch() {
        assertNull(select(listOf(source("Other", "Server A", 2))))
    }
    @Test fun sameProviderAlternativeIsPreferredWhenOriginalServerUnavailable() {
        val alternative = source("Repo / Provider", "Server B", 2)
        assertEquals(alternative, select(listOf(source("Other", "Server C", 2), alternative), done = true))
    }
    @Test fun completedEmptyScanReturnsNoCandidate() {
        assertNull(select(emptyList(), done = true, completed = true))
    }
    @Test fun missingCurrentSourcePreservesEarlySelection() {
        val available = source("Other", "Server B", 2)
        assertEquals(available, NextEpisodeSourcePolicy.select(listOf(available), null, false, false, "1080p", null))
    }
    @Test fun prefetchNeverReturnsAnotherProviderOrDifferentServer() {
        assertNull(NextEpisodeSourcePolicy.matchingServer(
            listOf(source("Other", "Server A", 2), source("Repo / Provider", "Server B", 2)),
            current, "1080p", null,
        ))
    }
    @Test fun prefetchMatchesSameServerAcrossEpisodeUrls() {
        val matching = source("Repo / Provider", "Server A", 2)
        assertEquals(matching, NextEpisodeSourcePolicy.matchingServer(
            listOf(source("Other", "Server A", 2), matching), current, "1080p", null,
        ))
    }
}
