package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.MediaItem

/** Conservative accounting estimate, not a measurement of VM object allocation. */
internal fun metadataCacheWeight(item: MediaItem): Long {
    var bytes = 512L
    fun text(value: String?) { bytes += 40L + (value?.length?.toLong() ?: 0L) * 2L }
    listOf(item.id, item.type, item.name, item.poster, item.background, item.description,
        item.releaseInfo, item.originalTitle, item.originalLanguage, item.sourceExtensionId,
        item.certification).forEach(::text)
    listOf(item.aliases, item.countries, item.genres, item.catalogSources,
        item.directors, item.creators, item.writers).forEach { values -> values.forEach(::text) }
    item.episodes.forEach { episode ->
        bytes += 192L
        listOf(episode.id, episode.title, episode.released, episode.overview, episode.thumbnail).forEach(::text)
    }
    item.cast.forEach { person ->
        bytes += 128L
        listOf(person.name, person.character, person.role, person.profile).forEach(::text)
    }
    (item.productionCompanies + item.networks).forEach { company ->
        bytes += 96L
        text(company.name)
        text(company.logo)
    }
    return bytes
}

/** Same certification precedence as mergeMediaMetadata; permission is never cached. */
internal fun metadataAllowedForKids(input: MediaItem, candidate: MediaItem, ageLimit: Int?): Boolean =
    com.vueo.shared.core.profile.KidsContentPolicy.allows(input.certification ?: candidate.certification, ageLimit)
