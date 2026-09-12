package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.source.SourceCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared registry for JavaScript-provider stream finalization.
 *
 * Resolver order is intentional. Transport-specific immediate resolvers run
 * first; network-heavy fallbacks are only used if none produced a playable
 * source. Fallback resolvers are a recovery chain: if a future host-specific
 * resolver fails or returns nothing, the next compatible resolver may still
 * recover the same candidate before we give up.
 */
internal class ProviderStreamResolverRegistry(
    private val resolvers: List<ProviderStreamResolver>,
    private val maxFallbackCandidates: Int,
) {
    init {
        require(maxFallbackCandidates > 0) {
            "maxFallbackCandidates must be greater than zero."
        }
        require(
            resolvers.all { resolver ->
                resolver.timeoutMs?.let { it > 0L } ?: true
            },
        ) {
            "Provider resolver timeoutMs must be greater than zero."
        }
    }

    suspend fun resolve(
        sources: List<SourceCandidate>,
    ): ProviderStreamResolution {
        if (sources.isEmpty()) {
            return ProviderStreamResolution(emptyList())
        }

        val immediateResolvers = resolvers.filter {
            it.stage == ProviderResolverStage.IMMEDIATE
        }
        val immediate = mutableListOf<SourceCandidate>()

        for (source in sources) {
            val resolver = immediateResolvers.firstOrNull {
                it.canResolveSafely(source)
            } ?: continue

            try {
                immediate += resolver.resolve(source)
                    .filter { it.isDirectPlayable }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // A resolver bug must not fail the whole provider execution.
            }
        }

        /*
         * Never make a provider that already returned a playable stream wait
         * for optional resolver work. This is the zero-extra-network fast path.
         */
        if (immediate.isNotEmpty()) {
            return ProviderStreamResolution(
                sources = immediate,
            )
        }

        val fallbackResolvers = resolvers.filter {
            it.stage == ProviderResolverStage.FALLBACK
        }
        val fallbackCandidates = sources.filter { source ->
            fallbackResolvers.any { resolver ->
                resolver.canResolveSafely(source)
            }
        }

        if (fallbackCandidates.isEmpty()) {
            return ProviderStreamResolution(emptyList())
        }

        val targets = fallbackCandidates.take(maxFallbackCandidates)
        val outcomes = supervisorScope {
            targets.map { source ->
                async {
                    resolveFallbackChain(
                        source = source,
                        fallbackResolvers = fallbackResolvers,
                    )
                }
            }.awaitAll()
        }

        val resolved = outcomes
            .flatMap { it.sources }
            .filter { it.isDirectPlayable }
            .distinctBy { it.url }

        return ProviderStreamResolution(
            sources = resolved,
            fallbackCandidateCount = fallbackCandidates.size,
            fallbackResolvedCount = resolved.size,
            fallbackAttempted = true,
            fallbackResolverAttempts = outcomes.sumOf { it.attempts },
            fallbackFailureCount = outcomes.sumOf { it.failures },
            fallbackTimeoutCount = outcomes.sumOf { it.timeouts },
            fallbackRecoveredByNextCount = outcomes.count {
                it.recoveredByNextResolver
            },
        )
    }

    private suspend fun resolveFallbackChain(
        source: SourceCandidate,
        fallbackResolvers: List<ProviderStreamResolver>,
    ): FallbackOutcome {
        var attempts = 0
        var failures = 0
        var timeouts = 0

        for (resolver in fallbackResolvers) {
            if (!resolver.canResolveSafely(source)) continue
            attempts += 1

            val result = try {
                val timeoutMs = resolver.timeoutMs
                if (timeoutMs == null) {
                    resolver.resolve(source)
                } else {
                    withTimeoutOrNull(timeoutMs) {
                        resolver.resolve(source)
                    } ?: run {
                        timeouts += 1
                        emptyList()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failures += 1
                emptyList()
            }

            val playable = result
                .filter { it.isDirectPlayable }
                .distinctBy { it.url }

            if (playable.isNotEmpty()) {
                return FallbackOutcome(
                    sources = playable,
                    attempts = attempts,
                    failures = failures,
                    timeouts = timeouts,
                    recoveredByNextResolver = attempts > 1,
                )
            }
        }

        return FallbackOutcome(
            sources = emptyList(),
            attempts = attempts,
            failures = failures,
            timeouts = timeouts,
            recoveredByNextResolver = false,
        )
    }

    private fun ProviderStreamResolver.canResolveSafely(
        source: SourceCandidate,
    ): Boolean = runCatching {
        canResolve(source)
    }.getOrDefault(false)

    private data class FallbackOutcome(
        val sources: List<SourceCandidate>,
        val attempts: Int,
        val failures: Int,
        val timeouts: Int,
        val recoveredByNextResolver: Boolean,
    )
}
