package com.vueo.shared.core.source

import java.net.URI

/**
 * Shared final-stage source selection used by both Mobile and TV.
 *
 * Discovery engines may return duplicates, torrents, malformed URLs and
 * multiple mirrors for the same stream. This selector keeps discovery fast by
 * doing only local structural checks here; actual playback readiness remains
 * the player's responsibility so we never add a blocking network probe before
 * playback starts.
 */
object SourceSelector {
    fun playable(
        sources: List<SourceCandidate>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): List<SourceCandidate> =
        SourceRanker.rank(
            sources = dedupe(sources).filter(::isStructurallyPlayable),
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )

    fun orderAll(
        sources: List<SourceCandidate>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): List<SourceCandidate> {
        val unique = dedupe(sources)
        val playable = playable(
            sources = unique,
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )
        val playableIds = playable.mapTo(hashSetOf()) { identityKey(it) }
        val remainder = unique
            .filter { identityKey(it) !in playableIds }
            .sortedWith(
                compareByDescending<SourceCandidate> { it.isTorrent }
                    .thenByDescending { it.rankBoost }
                    .thenBy { it.providerName.lowercase() }
                    .thenBy { it.name.lowercase() },
            )
        return playable + remainder
    }

    fun best(
        sources: List<SourceCandidate>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): SourceCandidate? =
        playable(
            sources = sources,
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        ).firstOrNull()

    fun dedupe(sources: List<SourceCandidate>): List<SourceCandidate> {
        val seen = hashSetOf<String>()
        return sources.filter { seen.add(identityKey(it)) }
    }

    fun identityKey(source: SourceCandidate): String {
        val url = source.url?.trim().orEmpty()
        if (url.isNotEmpty()) {
            return "url:${canonicalUrl(url)}"
        }
        val hash = source.infoHash?.trim()?.lowercase().orEmpty()
        if (hash.isNotEmpty()) {
            return "torrent:$hash:${source.fileIndex ?: -1}"
        }
        return listOf(
            "fallback",
            source.providerId.trim().lowercase(),
            source.name.trim().lowercase(),
            source.quality.orEmpty().trim().lowercase(),
        ).joinToString("|")
    }

    fun isStructurallyPlayable(source: SourceCandidate): Boolean {
        if (!source.isDirectPlayable) return false
        val raw = source.url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        return runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun canonicalUrl(raw: String): String =
        runCatching {
            val uri = URI(raw)
            URI(
                uri.scheme?.lowercase(),
                uri.userInfo,
                uri.host?.lowercase(),
                uri.port,
                uri.path,
                uri.query,
                null,
            ).toASCIIString()
        }.getOrElse {
            raw.substringBefore('#')
        }
}
