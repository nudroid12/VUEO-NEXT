package com.vueo.shared.core.media

/** Shared anime classification for search and recommendation seed quotas. */
object AnimeMediaPolicy {
    fun isAnime(item: MediaItem, animeCatalogKeys: Set<String> = emptySet()): Boolean {
        if (item.type.equals("anime", ignoreCase = true) || item.genres.any { it.equals("anime", ignoreCase = true) }) return true
        if ("${item.type}:${item.id}" in animeCatalogKeys) return true
        if (listOfNotNull(item.sourceExtensionId, item.id).any { it.contains("anime", ignoreCase = true) }) return true

        val isAnimation = item.genres.any { it.equals("animation", ignoreCase = true) }
        if (!isAnimation) return false

        val language = item.originalLanguage
            ?.trim()
            ?.lowercase()
            ?.substringBefore('-')
            ?.substringBefore('_')
        val isJapaneseLanguage = language in setOf("ja", "jp", "jpn", "japanese")
        val isJapaneseCountry = item.countries.any { country ->
            country.trim().lowercase() in setOf("japan", "jp", "jpn")
        }

        return isJapaneseLanguage || isJapaneseCountry
    }

}
