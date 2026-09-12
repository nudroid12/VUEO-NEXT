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

    /**
     * Optional outer execution budget. This is a safety net around resolver
     * implementations, not a delay: successful resolvers return immediately.
     */
    val timeoutMs: Long?
        get() = null

    fun canResolve(source: SourceCandidate): Boolean

    suspend fun resolve(source: SourceCandidate): List<SourceCandidate>
}

internal data class ProviderStreamResolution(
    val sources: List<SourceCandidate>,
    val fallbackCandidateCount: Int = 0,
    val fallbackResolvedCount: Int = 0,
    val fallbackAttempted: Boolean = false,
    val fallbackResolverAttempts: Int = 0,
    val fallbackFailureCount: Int = 0,
    val fallbackTimeoutCount: Int = 0,
    val fallbackRecoveredByNextCount: Int = 0,
)
