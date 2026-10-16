package com.vueo.mobile.ui

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.EpisodeItem
import kotlin.random.Random

fun main() {
    var checks = 0
    fun verify(ok: Boolean, label: String) { check(ok) { label }; checks++ }
    val original = MediaItem("tt123", "series", "Example", poster = "https://img/poster",
        background = "https://img/back", sourceExtensionId = "addon.example",
        releaseInfo = "2026", certification = "TV-14", originalTitle = "Original",
        imdbRating = 8.2, tmdbRating = 7.5,
        description = "not saved", episodes = listOf(EpisodeItem("secret-episode-url", "Episode", 1, 1)))
    val saved = MobileNavigationSnapshot.media(original)
    val restored = MobileNavigationSnapshot.restoreMedia(saved)!!
    verify(restored == original.copy(description = null, episodes = emptyList()), "identity and artwork round trip")
    verify(saved.none { "secret" in it || "not saved" in it }, "no episode or description payload")
    verify(MobileNavigationSnapshot.restoreMedia(MobileNavigationSnapshot.media(null)) == null, "no route")
    for (bad in listOf(original.copy(id = ""), original.copy(id = "a".repeat(1025)),
        original.copy(type = ""), original.copy(type = "a".repeat(65)),
        original.copy(sourceExtensionId = "a".repeat(513)))) {
        verify(MobileNavigationSnapshot.restoreMedia(MobileNavigationSnapshot.media(bad)) == null, "reject invalid identity")
    }
    val large = MobileNavigationSnapshot.restoreMedia(MobileNavigationSnapshot.media(original.copy(
        name = "n".repeat(10000), poster = "p".repeat(10000), background = "b".repeat(10000),
        releaseInfo = "y".repeat(10000), originalTitle = "o".repeat(10000))))!!
    verify(large.name.length == 512 && large.poster == null && large.background == null &&
        large.releaseInfo == null && large.originalTitle == null, "bounded presentation fields")
    val items = (0..40).map { original.copy(id = "id-$it") }
    verify(MobileNavigationSnapshot.restoreStack(MobileNavigationSnapshot.stack(items)).map { it.id } ==
        items.takeLast(8).map { it.id }, "latest eight ancestors retain Back order")
    verify(MobileNavigationSnapshot.restoreStack(MobileNavigationSnapshot.stack(listOf(
        original.copy(id = ""), original))).map { it.id } == listOf(original.id), "skip invalid stack entry without corrupting next")
    verify(MobileNavigationSnapshot.restoreStack(listOf("1")).isEmpty(), "empty stack")
    verify(MobileNavigationSnapshot.restoreStack(listOf("2") + saved).isEmpty(), "unknown version")
    verify(MobileNavigationSnapshot.restoreStack(listOf("1") + saved + "partial").isEmpty(), "partial stack")
    verify(MobileNavigationSnapshot.restoreStack(listOf("1") + List(9) { saved }.flatten()).isEmpty(), "oversized stack")
    for (savedOwner in listOf("p1:adult", "p1:kids12", "p2:adult")) {
        for (owner in listOf("p1:adult", "p1:kids12", "p2:adult")) {
            for (manual in listOf(true, false)) verify(
                MobileNavigationSnapshot.mayResumeRoute(savedOwner, owner, manual) == (savedOwner == owner && !manual),
                "profile/policy ownership and manual picker")
        }
    }
    val random = Random(11)
    repeat(500) { i ->
        val item = original.copy(id = "id-$i", name = "Title $i", imdbRating = random.nextDouble(0.0, 10.0))
        verify(MobileNavigationSnapshot.restoreMedia(MobileNavigationSnapshot.media(item)) ==
            item.copy(description = null, episodes = emptyList()), "random round trip $i")
        val fields = MobileNavigationSnapshot.media(item).toMutableList()
        fields[random.nextInt(fields.size)] = "bad".repeat(1000)
        verify(MobileNavigationSnapshot.restoreMedia(fields) == null, "reject oversized/corrupt field $i")
    }
    val invalidRating = saved.toMutableList().also { it[10] = "NaN"; it[11] = "999" }
    verify(MobileNavigationSnapshot.restoreMedia(invalidRating)?.let { it.imdbRating == null && it.tmdbRating == null } == true,
        "reject non-finite and invalid ratings")
    println("Navigation snapshot: $checks behavioral checks passed")
}
