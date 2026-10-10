package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedRealityRelevanceTest {
    private fun reality(id: String, name: String, description: String? = null) =
        MediaItem(id = id, type = "series", name = name, genres = listOf("Reality"), description = description)

    private val seed = reality("seed", "Hell's Kitchen", "Chefs compete in a kitchen to run a restaurant.")
    private val cooking = reality("cooking", "MasterChef", "Amateur cooks prepare recipes in a culinary competition.")
    private val unrelated = listOf(
        reality("obstacles", "Wipeout", "Contestants compete on a giant obstacle course."),
        reality("dating", "Love Island", "Singles find love and relationships in a villa."),
        reality("music", "Rhythm + Flow", "Singers compete to launch a music career."),
        reality("sparse", "Unknown Reality"),
    )

    @Test fun remoteRankAndRealityGenreCannotOverrideCookingSubject() {
        val result = CatalogDiscoveryCache.blendRelated(seed, emptyList(), unrelated + cooking, 12)
        assertEquals(listOf("cooking"), result.map { it.id })
    }

    @Test fun localCandidatesUseTheSameSubjectGateAndKeepShortResults() {
        val result = CatalogDiscoveryCache.blendRelated(seed, unrelated + cooking, emptyList(), 12)
        assertEquals(listOf("cooking"), result.map { it.id })
    }

    @Test fun missingSynopsisDoesNotLetRemoteRealityBypassRelevance() {
        val sparseSeed = reality("seed", "Untitled Reality")
        val sparseCandidate = reality("sparse", "Other Program")
        assertTrue(CatalogDiscoveryCache.blendRelated(sparseSeed, emptyList(), listOf(sparseCandidate), 12).isEmpty())
    }

    @Test fun cookingThemeRecognizesBakingAndDoesNotCrossMediaTypes() {
        val baking = reality("baking", "Bake Off", "Bakers prepare cakes and recipes in a baking competition.")
        val movie = cooking.copy(id = "movie", type = "movie")
        val result = CatalogDiscoveryCache.blendRelated(seed, emptyList(), listOf(movie, baking), 12)
        assertEquals(listOf("baking"), result.map { it.id })
    }
}
