package com.vueo.shared.core.plugin

object AdaptiveProviderOrder {
    fun tier(anime: Boolean, types: Set<String>, description: String = ""): Int {
        val supported = types.map { it.lowercase() }.toSet()
        val animeSupport = Regex("anime|donghua", RegexOption.IGNORE_CASE).containsMatchIn(description) || supported.any { it in setOf("anime", "animemovie", "anime_movie") }
        val generalSupport = supported.any { it in setOf("movie", "movies", "tv", "series") }
        return if (anime) when { animeSupport -> 0; generalSupport -> 1; else -> 2 }
        else when { animeSupport -> 2; generalSupport -> 0; else -> 1 }
    }
}
