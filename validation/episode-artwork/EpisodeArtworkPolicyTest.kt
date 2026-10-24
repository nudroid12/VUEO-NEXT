package com.vueo.shared.core.player

import org.junit.Assert.*
import org.junit.Test

class EpisodeArtworkPolicyTest {
    private val primary = "https://addon.test/episode.jpg"
    private val tmdb = "https://image.tmdb.org/t/p/w500/still.jpg"
    private val background = "https://addon.test/backdrop.jpg"
    private val poster = "https://addon.test/poster.jpg"

    @Test fun realEpisodeImageWinsBeforeFallbacks() {
        val candidates = EpisodeArtworkPolicy.candidates(primary, tmdb, background, poster)
        assertEquals(listOf(primary, tmdb, background, poster), candidates)
        assertEquals(primary, EpisodeArtworkPolicy.next(candidates, emptySet()))
    }
    @Test fun failedPrimaryUsesTmdbThenBackdropThenPoster() {
        val candidates = EpisodeArtworkPolicy.candidates(primary, tmdb, background, poster)
        assertEquals(tmdb, EpisodeArtworkPolicy.next(candidates, setOf(primary)))
        assertEquals(background, EpisodeArtworkPolicy.next(candidates, setOf(primary, tmdb)))
        assertEquals(poster, EpisodeArtworkPolicy.next(candidates, setOf(primary, tmdb, background)))
        assertNull(EpisodeArtworkPolicy.next(candidates, candidates.toSet()))
    }
    @Test fun missingEpisodeImageCanUseShowArtworkWithoutTmdbKey() {
        assertEquals(background, EpisodeArtworkPolicy.next(EpisodeArtworkPolicy.candidates(null, null, background, poster), emptySet()))
    }
    @Test fun duplicateFallbackUrlIsNotRetriedUnderAnotherLabel() {
        val candidates = EpisodeArtworkPolicy.candidates(primary, primary, primary, poster)
        assertEquals(listOf(primary, poster), candidates)
        assertEquals(poster, EpisodeArtworkPolicy.next(candidates, setOf(primary)))
    }
    @Test fun lateTmdbArtworkReplacesBackdropWithoutRetryingFailedPrimary() {
        val failures = setOf(primary)
        assertEquals(background, EpisodeArtworkPolicy.next(EpisodeArtworkPolicy.candidates(primary, null, background, poster), failures))
        assertEquals(tmdb, EpisodeArtworkPolicy.next(EpisodeArtworkPolicy.candidates(primary, tmdb, background, poster), failures))
    }
    @Test fun invalidArtworkIsSkippedInsteadOfBlockingOtherCandidates() {
        for (bad in listOf(null, "", " ", "/relative.jpg", "https://", "https:///missing-host", "https://bad host/a")) {
            assertNull(EpisodeArtworkPolicy.usable(bad))
            assertEquals(background, EpisodeArtworkPolicy.candidates(bad, null, background, poster).first())
        }
    }
    @Test fun httpArtworkPreservesLanSchemeAndSignedQuery() {
        val url = "http://192.168.1.2:7000/episode.jpg?token=A%2FB"
        assertEquals(url, EpisodeArtworkPolicy.usable(url))
        assertEquals(url, EpisodeArtworkPolicy.candidates(url, null, background, poster).first())
    }
    @Test fun schemeAndWhitespaceAreNormalizedWithoutChangingSignedQuery() {
        val input = "  HTTPS://addon.test/a.jpg?signature=AbC%2Fxyz&expires=99  "
        assertEquals("https://addon.test/a.jpg?signature=AbC%2Fxyz&expires=99", EpisodeArtworkPolicy.usable(input))
    }
    @Test fun separateEpisodeFailuresDoNotHideAnotherEpisodesArtwork() {
        val candidates = EpisodeArtworkPolicy.candidates(primary, tmdb, background, poster)
        assertEquals(tmdb, EpisodeArtworkPolicy.next(candidates, setOf(primary)))
        assertEquals(primary, EpisodeArtworkPolicy.next(candidates, emptySet()))
    }
}
