package com.vueo.shared.core.source

import org.junit.Assert.*
import org.junit.Test

class SourceRankerParityTest {
    @Test fun cachedScoresPreserveOrderingAndStableTies() {
        val sources = listOf("1080p", "720p", "4K", "Auto", "480p").flatMapIndexed { i, quality ->
            listOf("B", "A", "A").mapIndexed { j, provider ->
                SourceCandidate(id = "$i-$j", name = "Movie $quality", quality = quality,
                    url = "https://example.com/$i/$j.m3u8", sizeBytes = if (j == 0) null else 1000L,
                    providerId = provider, providerName = provider)
            }
        }
        for (quality in listOf(null, "720p", "4K")) {
            for (language in listOf(null, "en", "ms")) {
                val expected = sources.sortedWith(SourceRanker.comparator(quality, language))
                assertEquals(expected, SourceRanker.rank(sources, quality, language))
                assertEquals(expected.reversed().sortedWith(SourceRanker.comparator(quality, language)),
                    SourceRanker.rank(expected.reversed(), quality, language))
            }
        }
        assertTrue(SourceRanker.rank(emptyList(), null, null).isEmpty())
    }
}
