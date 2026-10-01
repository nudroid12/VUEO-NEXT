package com.vueo.shared.core.source

import java.net.URI

/** Reject only explicit season AND episode labels; opaque IDs remain valid. */
internal object SourceEpisodePolicy {
    data class EpisodeReference(val season: Int, val episode: Int)

    private val labels = listOf(
        Regex("(?i)(?<![a-z0-9])s(\\d{1,3})[ ._-]*e(\\d{1,4})(?![a-z0-9])"),
        Regex("(?i)(?<![a-z0-9])season[ ._-]*(\\d{1,3})[ ._/-]*(?:episode|ep)[ ._-]*(\\d{1,4})(?![a-z0-9])"),
    )

    fun mismatch(
        name: String,
        url: String?,
        season: Int,
        episode: Int,
    ): EpisodeReference? {
        // Ignore hostname, query parameters and fragments (tokens/video IDs).
        val path = url?.let { runCatching { URI(it).path }.getOrNull() }.orEmpty()
        for (text in listOf(name, path)) {
            for (pattern in labels) {
                for (match in pattern.findAll(text)) {
                    val foundSeason = match.groupValues[1].toIntOrNull() ?: continue
                    val foundEpisode = match.groupValues[2].toIntOrNull() ?: continue
                    if (foundSeason != season || foundEpisode != episode) {
                        return EpisodeReference(foundSeason, foundEpisode)
                    }
                }
            }
        }
        return null
    }
}
