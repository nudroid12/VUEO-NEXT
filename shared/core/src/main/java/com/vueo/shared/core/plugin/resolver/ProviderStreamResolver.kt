package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.source.SourceCandidate

/**
 * Resolution stage for provider stream candidates.
 *
 * IMMEDIATE resolvers must not perform network work. FALLBACK resolvers may do
 * additional extraction and are only used when no immediately playable source
 * exists, preserving VUEO's fast-start behavior.
 */
internal enum class ProviderResolverStage {
    IMMEDIATE,
    FALLBACK,
}

internal interface ProviderStreamResolver {
    val id: String
    val stage: ProviderResolverStage

    fun canResolve(source: SourceCandidate): Boolean

    suspend fun resolve(source: SourceCandidate): List<SourceCandidate>
}

internal data class ProviderStreamResolution(
    val sources: List<SourceCandidate>,
    val fallbackCandidateCount: Int = 0,
    val fallbackResolvedCount: Int = 0,
    val fallbackAttempted: Boolean = false,
)
