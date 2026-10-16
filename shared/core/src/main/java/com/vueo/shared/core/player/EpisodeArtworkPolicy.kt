package com.vueo.shared.core.player

object EpisodeArtworkPolicy {
    fun usable(url: String?): String? {
        val value = url?.trim() ?: return null
        if (!value.startsWith("https://", ignoreCase = true)) return null
        val normalized = "https://" + value.substring(8)
        return runCatching {
            normalized.takeIf { !java.net.URI(it).host.isNullOrBlank() }
        }.getOrNull()
    }

    fun candidates(primary: String?, tmdb: String?, background: String?, poster: String?): List<String> =
        listOfNotNull(usable(primary), usable(tmdb), usable(background), usable(poster)).distinct()

    fun next(candidates: List<String>, failed: Set<String>): String? = candidates.firstOrNull { it !in failed }
}
