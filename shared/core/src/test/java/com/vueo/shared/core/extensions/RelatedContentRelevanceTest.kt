package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedContentRelevanceTest {
    private fun media(id: String, synopsis: String?, vararg genres: String) = MediaItem(
        id = id, name = id, type = "series", description = synopsis, genres = genres.toList(),
        originalLanguage = "en", countries = listOf("US"), releaseInfo = "2020",
    )

    private fun results(seed: MediaItem, vararg candidates: MediaItem): List<String> {
        CatalogDiscoveryCache.putHome(listOf(CatalogRow("relevance-fixtures", "Fixtures", "Test", candidates.toList())))
        return CatalogDiscoveryCache.related(seed, 1000).map { it.id }
    }

    @Test fun cookingCompetitionRejectsLocationDramaAnimationAndUnrelatedReality() {
        val seed = media("Hell's Kitchen", "Chefs compete in culinary challenges cooking food dishes to win a restaurant.", "Reality")
        val cooking = media("MasterChef", "Amateur chefs compete in cooking challenges preparing food dishes for judges.", "Reality")
        val crime = media("The Westies", "In Hell's Kitchen a crime gang fights the mafia for control of a city.", "Crime", "Drama")
        val cartoon = media("SpongeBob", "A cook and friends share adventures in a restaurant beneath the sea.", "Animation")
        val drama = media("The Bear", "A chef returns to his restaurant managing food and family grief.", "Drama")
        val dating = media("Love Island", "Singles find romantic partners and dating couples on an island.", "Reality")
        val found = results(seed, cooking, crime, cartoon, drama, dating)
        assertTrue("Cooking competition must survive", cooking.id in found)
        listOf(crime, cartoon, drama, dating).forEach { assertFalse(it.id, it.id in found) }
    }

    @Test fun dramaUsesStoryInsteadOfJustTheSameGenre() {
        val seed = media("Seed Drama", "A detective investigates a murder while police uncover a criminal conspiracy.", "Crime", "Drama")
        val match = media("Case Files", "A detective investigates a murder as police track a criminal conspiracy.", "Crime", "Thriller")
        val unrelated = media("Office Life", "Colleagues balance family relationships and everyday office responsibilities.", "Drama")
        val found = results(seed, match, unrelated)
        assertTrue(match.id in found)
        assertFalse(unrelated.id in found)
    }

    @Test fun animeRequiresContentEvidence() {
        val seed = media("Seed Anime", "A wizard trains with magic to battle a dragon and save the kingdom.", "Animation", "Fantasy")
        val match = media("Mage Quest", "A wizard studies magic and battles a dragon threatening the kingdom.", "Animation", "Fantasy")
        val unrelated = media("School Days", "Students navigate friendships romance and classroom activities.", "Animation", "Romance")
        val found = results(seed, match, unrelated)
        assertTrue(match.id in found)
        assertFalse(unrelated.id in found)
    }

    @Test fun documentariesNeedTheSameSubject() {
        val seed = media("Wild Earth", "Wildlife animals survive in forest habitats as species adapt to nature.", "Documentary")
        val match = media("Living Planet", "Animals and wildlife adapt to forest habitats as species thrive in nature.", "Documentary")
        val unrelated = media("Ancient Kingdoms", "Ancient civilization and archaeology reveal the history of an empire.", "Documentary")
        val found = results(seed, match, unrelated)
        assertTrue(match.id in found)
        assertFalse(unrelated.id in found)
    }

    @Test fun companyCountryPopularityAndGenreCannotQualifyAnUnrelatedItem() {
        val studio = listOf(MediaCompany("Same Studio"))
        val seed = media("Police Story", "Detectives investigate murders and police pursue criminal gangs.", "Drama").copy(productionCompanies = studio)
        val unrelated = media("Wedding Story", "Friends celebrate a wedding while families prepare a romantic celebration.", "Drama")
            .copy(productionCompanies = studio, tmdbRating = 10.0, popularity = 999999.0)
        assertFalse(unrelated.id in results(seed, unrelated))
    }

    @Test fun sparseMetadataRequiresPeopleAndGenre() {
        val host = listOf(MediaPerson("A Shared Host"))
        val seed = media("Seed Sparse", null, "Drama").copy(cast = host)
        val match = media("Sparse Match", null, "Drama").copy(cast = host)
        val unrelated = media("Sparse Unrelated", null, "Drama")
        val found = results(seed, match, unrelated)
        assertTrue(match.id in found)
        assertFalse(unrelated.id in found)
    }

    @Test fun storyCandidateSurvivesMoreThan600GenreOnlyDistractors() {
        val seed = media("Seed Mystery", "A detective investigates murder while police uncover criminal conspiracy evidence.", "Crime", "Drama")
        val match = media("Evidence Files", "A detective investigates murder while police uncover criminal conspiracy evidence.", "Thriller")
        val fillers = (1..610).map { media("Distractor $it", "Friends navigate romance at an office wedding celebration.", "Crime", "Drama") }
        assertTrue(match.id in results(seed, *(fillers + match).toTypedArray()))
    }

    @Test fun duplicateAndSeedExclusionArePreserved() {
        val seed = media("Seed Mystery", "A detective investigates murder while police uncover criminal conspiracy evidence.", "Crime")
        val match = media("Evidence Files", "A detective investigates murder while police uncover criminal conspiracy evidence.", "Crime")
        val found = results(seed, seed, match, match.copy(sourceExtensionId = "second-addon"))
        assertFalse(seed.id in found)
        assertEquals(1, found.count { it == match.id })
    }

    @Test fun franchiseEntriesAreNotLimitedByAQuota() {
        val seed = media("Seed Space", "Astronauts explore a galaxy in a spaceship to discover an alien planet.", "Science Fiction")
        val sequels = (1..4).map { media("Space Voyage: Part $it", "Astronauts explore a galaxy in a spaceship to discover an alien planet.", "Science Fiction") }
        val found = results(seed, *sequels.toTypedArray())
        sequels.forEach { assertTrue(it.id in found) }
    }
}
