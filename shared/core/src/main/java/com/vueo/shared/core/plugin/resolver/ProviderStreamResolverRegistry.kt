package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.source.SourceCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Shared registry for JavaScript-provider stream finalization.
 *
 * Resolver order is intentional. Transport-specific immediate resolvers run
 * first; network-heavy fallbacks are only used if none produced a playable
 * source. Host-specific resolvers can be inserted before the generic WebView
 * fallback without changing PluginRuntime.
 */
internal class ProviderStreamResolverRegistry(
    private val resolvers: List<ProviderStreamResolver>,
    private val maxFallbackCandidates: Int,
) {
    init {
        require(maxFallbackCandidates > 0) {
            "maxFallbackCandidates must be greater than zero."
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
        val immediate = sources.flatMap { source ->
            val resolver = immediateResolvers.firstOrNull {
                it.canResolve(source)
            }
            resolver?.resolve(source).orEmpty()
        }

        /*
         * Never make a provider that already returned a playable stream wait
         * for optional embed work. This preserves Phase 1 startup latency and
         * current provider precedence.
         */
        if (immediate.isNotEmpty()) {
            return ProviderStreamResolution(
                sources = immediate,
            )
        }

        val fallbackResolvers = resolvers.filter {
            it.stage == ProviderResolverStage.FALLBACK
        }
        val fallbackCandidates = sources.mapNotNull { source ->
            val resolver = fallbackResolvers.firstOrNull {
                it.canResolve(source)
            } ?: return@mapNotNull null
            source to resolver
        }

        if (fallbackCandidates.isEmpty()) {
            return ProviderStreamResolution(emptyList())
        }

        val targets = fallbackCandidates.take(maxFallbackCandidates)
        val resolved = supervisorScope {
            targets.map { (source, resolver) ->
                async {
                    resolver.resolve(source)
                }
            }.awaitAll()
                .flatten()
                .filter { it.isDirectPlayable }
                .distinctBy { it.url }
        }

        return ProviderStreamResolution(
            sources = resolved,
            fallbackCandidateCount = fallbackCandidates.size,
            fallbackResolvedCount = resolved.size,
            fallbackAttempted = true,
        )
    }
}
