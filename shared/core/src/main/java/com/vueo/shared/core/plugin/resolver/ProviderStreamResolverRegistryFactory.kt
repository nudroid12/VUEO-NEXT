package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.plugin.PluginWebViewResolver
import kotlinx.coroutines.sync.Semaphore

/**
 * Single composition point for provider stream resolvers.
 *
 * New host-specific resolvers should be registered here before the generic
 * WebView fallback so PluginRuntime remains unaware of host/extractor details.
 */
internal fun createProviderStreamResolverRegistry(
    webViewResolver: PluginWebViewResolver,
    webViewConcurrency: Semaphore,
    maxFallbackCandidates: Int,
    hostSpecificFallbackResolvers: List<ProviderStreamResolver> = emptyList(),
): ProviderStreamResolverRegistry {
    require(
        hostSpecificFallbackResolvers.all {
            it.stage == ProviderResolverStage.FALLBACK
        },
    ) {
        "Host-specific provider resolvers must be FALLBACK resolvers."
    }

    return ProviderStreamResolverRegistry(
        resolvers =
            listOf(
                HlsProviderStreamResolver,
                DashProviderStreamResolver,
                DirectProviderStreamResolver,
            ) +
                hostSpecificFallbackResolvers +
                WebViewEmbedProviderStreamResolver(
                    webViewResolver = webViewResolver,
                    webViewConcurrency = webViewConcurrency,
                ),
        maxFallbackCandidates = maxFallbackCandidates,
    )
}
