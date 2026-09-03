package com.vueo.shared.core.source

/**
 * Platform-neutral result returned by any VUEO source resolver.
 *
 * Stremio addons and JavaScript providers will both adapt into this contract,
 * allowing Mobile and TV to consume the same source pipeline.
 */
data class SourceResolveResult(
    val sources: List<SourceCandidate> = emptyList(),
    val subtitles: List<SubtitleCandidate> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * Common discovery boundary for source implementations.
 * UI, Android Context and player classes must stay outside this contract.
 */
interface SourceResolver {
    val id: String
    val name: String

    suspend fun resolve(request: SourceRequest): SourceResolveResult
}

/**
 * Ranking contract shared by Mobile and TV.
 */
interface SourceRankingPolicy {
    fun assess(
        source: SourceCandidate,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): SourceAssessment

    fun comparator(
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): Comparator<SourceCandidate>

    fun rank(
        sources: List<SourceCandidate>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): List<SourceCandidate>

    fun automaticRecoveryCandidates(
        rankedSources: List<SourceCandidate>,
        attemptedUrls: Set<String>,
        originalLanguage: String? = null,
    ): List<SourceCandidate>
}
