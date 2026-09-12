package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.media.StreamTransport
import com.vueo.shared.core.source.SourceCandidate

/** Pass-through resolver for already-playable HLS sources. */
internal object HlsProviderStreamResolver : ProviderStreamResolver {
    override val id: String = "hls"
    override val stage: ProviderResolverStage = ProviderResolverStage.IMMEDIATE

    override fun canResolve(source: SourceCandidate): Boolean =
        source.transport == StreamTransport.HLS && source.isDirectPlayable

    override suspend fun resolve(
        source: SourceCandidate,
    ): List<SourceCandidate> = listOf(source)
}

/** Pass-through resolver for already-playable DASH sources. */
internal object DashProviderStreamResolver : ProviderStreamResolver {
    override val id: String = "dash"
    override val stage: ProviderResolverStage = ProviderResolverStage.IMMEDIATE

    override fun canResolve(source: SourceCandidate): Boolean =
        source.transport == StreamTransport.DASH && source.isDirectPlayable

    override suspend fun resolve(
        source: SourceCandidate,
    ): List<SourceCandidate> = listOf(source)
}

/**
 * Generic pass-through resolver for MP4/WebM/file, explicit direct, and opaque
 * signed HTTPS URLs that Phase 1 already considers immediately playable.
 */
internal object DirectProviderStreamResolver : ProviderStreamResolver {
    override val id: String = "direct"
    override val stage: ProviderResolverStage = ProviderResolverStage.IMMEDIATE

    override fun canResolve(source: SourceCandidate): Boolean =
        source.isDirectPlayable &&
            source.transport != StreamTransport.HLS &&
            source.transport != StreamTransport.DASH &&
            source.transport != StreamTransport.EMBED

    override suspend fun resolve(
        source: SourceCandidate,
    ): List<SourceCandidate> = listOf(source)
}
