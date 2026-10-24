package com.vueo.shared.core.player

object EpisodeArtworkPolicy {
    fun usable(url: String?): String? {
        val value = url?.trim() ?: return null
        if (!com.vueo.shared.core.stremio.StremioUrls.isHttpUrl(value)) return null
        val scheme = java.net.URI(value).scheme
        return scheme.lowercase() + value.substring(scheme.length)
    }

    fun candidates(primary: String?, tmdb: String?, background: String?, poster: String?): List<String> =
        listOfNotNull(usable(primary), usable(tmdb), usable(background), usable(poster)).distinct()

    fun next(candidates: List<String>, failed: Set<String>): String? = candidates.firstOrNull { it !in failed }
}
