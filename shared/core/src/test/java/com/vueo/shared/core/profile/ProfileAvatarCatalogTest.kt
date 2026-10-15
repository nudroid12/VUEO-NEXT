package com.vueo.shared.core.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileAvatarCatalogTest {
    private val retired = listOf("avatar_man_3", "avatar_man_4") +
        (5..8).map { "avatar_character_$it" } + (1..4).map { "avatar_vueo_$it" }

    @Test fun retiredArtworkIsNotSelectable() {
        assertEquals(26, ProfileAvatarCatalog.selectable.size)
        assertEquals(26, ProfileAvatarCatalog.selectable.map { it.id }.distinct().size)
        assertTrue(ProfileAvatarCatalog.all.none { it.id in retired })
    }

    @Test fun retiredProfilesResolveToAvailableReplacementArtwork() {
        for (id in retired) {
            val replacement = ProfileAvatarCatalog.resolveId(id)
            assertTrue(ProfileAvatarCatalog.selectable.any { it.id == replacement })
            assertEquals(ProfileAvatarCatalog.drawableRes(replacement), ProfileAvatarCatalog.drawableRes(id))
            assertNotNull(ProfileAvatarCatalog.drawableRes(id))
            assertEquals(replacement, ProfileAvatarCatalog.resolveId(replacement))
        }
    }

    @Test fun currentAndCustomAvatarIdsArePreserved() {
        for (id in ProfileAvatarCatalog.selectable.map { it.id } + listOf("local_avatar:photo.jpg", "🙂")) {
            assertEquals(id, ProfileAvatarCatalog.resolveId(id))
        }
        assertNull(ProfileAvatarCatalog.drawableRes("local_avatar:photo.jpg"))
    }
}
