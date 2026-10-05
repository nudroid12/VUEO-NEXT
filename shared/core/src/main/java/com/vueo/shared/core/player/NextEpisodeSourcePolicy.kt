package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource

/** Prefer continuity without waiting for unrelated providers to finish. */
object NextEpisodeSourcePolicy {
    private fun sameProvider(a: StreamSource, b: StreamSource): Boolean =
        a.providerId == b.providerId && a.providerName.trim().equals(b.providerName.trim(), true)

    fun sameServer(a: StreamSource, b: StreamSource): Boolean =
        sameProvider(a, b) && PlayerSourceDisplay.title(a).trim()
            .equals(PlayerSourceDisplay.title(b).trim(), true)

    fun matchingServer(sources: List<StreamSource>, current: StreamSource,
        preferredQuality: String?, originalLanguage: String?): StreamSource? =
        sources.filter { it.isDirectPlayable && sameServer(it, current) }
            .sortedWith(PlayerSourcePolicy.comparator(preferredQuality, originalLanguage))
            .firstOrNull()

    fun select(
        sources: List<StreamSource>,
        current: StreamSource?,
        preferredProviderDone: Boolean,
        completed: Boolean,
        preferredQuality: String?,
        originalLanguage: String?,
    ): StreamSource? {
        val ranked = sources.filter { it.isDirectPlayable }
            .sortedWith(PlayerSourcePolicy.comparator(preferredQuality, originalLanguage))
        fun eligible(source: StreamSource): Boolean {
            val assessment = PlayerSourcePolicy.assess(source, preferredQuality, originalLanguage)
            return assessment.quality.automaticRecoveryEligible && assessment.audioMatch.recommendationEligible
        }
        if (current != null) {
            val sameProviderSources = ranked.filter { sameProvider(it, current) }
            val server = PlayerSourceDisplay.title(current).trim()
            sameProviderSources.firstOrNull {
                PlayerSourceDisplay.title(it).trim().equals(server, true) &&
                    (eligible(it) || completed)
            }?.let { return it }
            // A different provider can return first. Keep it as fallback until
            // the current provider has returned, failed or hit its normal timeout.
            if (!preferredProviderDone && !completed) return null
            sameProviderSources.firstOrNull(::eligible)?.let { return it }
        }
        return ranked.firstOrNull(::eligible) ?: ranked.firstOrNull()?.takeIf { completed }
    }
}
