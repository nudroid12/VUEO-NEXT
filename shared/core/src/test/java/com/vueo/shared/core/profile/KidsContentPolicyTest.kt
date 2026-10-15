package com.vueo.shared.core.profile

import org.junit.Assert.*
import org.junit.Test

class KidsContentPolicyTest {
    @Test fun adultProfilesKeepRatedAndUnratedTitles() {
        for (rating in listOf(null, "", "NR", "TV-MA", "R", "18", "G")) {
            assertTrue(KidsContentPolicy.allows(rating, null))
        }
    }
    @Test fun allKidsLimitsHideMissingAndUnknownRatings() {
        for (limit in KidsContentPolicy.ageLimits) for (rating in listOf(null, "", "NR", "Unrated", "Not Rated", "Unknown", "2024", "IMDb 7.2")) {
            assertFalse(KidsContentPolicy.allows(rating, limit))
        }
    }
    @Test fun sevenAllowsChildrensRatingsWithoutTreatingPgAsGeneral() {
        for (rating in listOf("G", "U", "TV-Y", "TV-G", "TV-Y7", "TV-Y7-FV", "7+", "FSK 6")) {
            assertTrue("$rating", KidsContentPolicy.allows(rating, 7))
        }
        for (rating in listOf("PG", "12", "PG-13", "TV-14", "16", "R")) assertFalse(KidsContentPolicy.allows(rating, 7))
    }
    @Test fun twelveIncludesPgAndTwelveButExcludesThirteen() {
        for (rating in listOf("PG", "TV-PG", "12", "12A", "P12", "FSK12")) assertTrue(KidsContentPolicy.allows(rating, 12))
        for (rating in listOf("PG-13", "P13", "TV-14", "15", "16")) assertFalse(KidsContentPolicy.allows(rating, 12))
    }
    @Test fun sixteenNeverAllowsAdultRatings() {
        for (rating in listOf("PG-13", "TV-14", "15", "16", "16+", "FSK16")) assertTrue(KidsContentPolicy.allows(rating, 16))
        for (rating in listOf("R", "NC-17", "TV-MA", "18", "18+", "R18+", "X")) assertFalse(KidsContentPolicy.allows(rating, 16))
    }
    @Test fun caseAndWhitespaceAreNormalized() {
        assertTrue(KidsContentPolicy.allows("  tv-y7  ", 7))
        assertTrue(KidsContentPolicy.allows("TV_PG", 12))
        assertTrue(KidsContentPolicy.allows("fsk   12", 12))
    }
    @Test fun invalidLimitFallsBackToSeven() {
        for (limit in listOf(-1, 0, 8, 18, 99)) {
            assertEquals(7, KidsContentPolicy.normalizeLimit(limit))
            assertFalse(KidsContentPolicy.allows("12", limit))
        }
    }
    @Test fun ambiguousStringsDoNotBecomePermissiveByMatchingAnEmbeddedNumber() {
        for (rating in listOf("TV-MA / G", "US:18/7", "Season 7", "PG-13 / U")) {
            assertFalse(KidsContentPolicy.allows(rating, 16))
        }
    }
}
