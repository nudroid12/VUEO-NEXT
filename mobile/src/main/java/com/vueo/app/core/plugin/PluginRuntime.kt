package com.vueo.app.core.plugin

import android.content.Context
import com.vueo.app.core.model.StreamSource
import com.vueo.shared.core.plugin.PluginDiscoveryProgress as SharedPluginDiscoveryProgress
import com.vueo.shared.core.plugin.PluginDiscoveryResult as SharedPluginDiscoveryResult
import com.vueo.shared.core.plugin.PluginSourceEngine as SharedPluginSourceEngine
import com.vueo.shared.core.plugin.PluginStore as SharedPluginStore
import com.vueo.shared.core.plugin.ProviderDiagnostic as SharedProviderDiagnostic
import com.vueo.shared.core.source.SourceCandidate

/**
 * Mobile compatibility facade. Provider execution now lives in shared/core.
 * The public Mobile API stays unchanged so existing UI behaviour is preserved.
 */
data class ProviderDiagnostic(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long,
    val streamCount: Int,
    val error: String? = null,
    val logs: List<String> = emptyList(),
)

data class PluginDiscoveryProgress(
    val result: PluginDiscoveryResult,
    val completedProviders: Int,
    val totalProviders: Int,
)

data class PluginDiscoveryResult(
    val streams: List<StreamSource>,
    val attemptedProviders: Int,
    val successfulProviders: Int,
    val slowProviders: Int,
    val noResultProviders: Int,
    val needsSetupProviders: Int,
    val unavailableProviders: Int,
    val blockedProviders: Int,
    val timeoutProviders: Int,
    val failedProviders: Int,
    val diagnostics: List<ProviderDiagnostic>,
    val fromCache: Boolean = false,
    val coalesced: Boolean = false,
    val readyProviders: Int = 0,
    val repairedProviders: Int = 0,
    val preflightErrors: List<String> = emptyList(),
)

class PluginSourceEngine(
    context: Context,
    @Suppress("UNUSED_PARAMETER") store: PluginStore,
) {
    private val delegate = SharedPluginSourceEngine(
        context = context.applicationContext,
        store = SharedPluginStore(context.applicationContext),
    )

    suspend fun discover(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): PluginDiscoveryResult =
        delegate.discover(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
        ).toMobile()

    suspend fun discoverProgressive(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        onProgress: suspend (PluginDiscoveryProgress) -> Unit,
    ): PluginDiscoveryResult =
        delegate.discoverProgressive(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
        ) { progress: SharedPluginDiscoveryProgress ->
            onProgress(
                PluginDiscoveryProgress(
                    result = progress.result.toMobile(),
                    completedProviders = progress.completedProviders,
                    totalProviders = progress.totalProviders,
                )
            )
        }.toMobile()
}

private fun SharedPluginDiscoveryResult.toMobile(): PluginDiscoveryResult =
    PluginDiscoveryResult(
        streams = streams.map { it.toMobile() },
        attemptedProviders = attemptedProviders,
        successfulProviders = successfulProviders,
        slowProviders = slowProviders,
        noResultProviders = noResultProviders,
        needsSetupProviders = needsSetupProviders,
        unavailableProviders = unavailableProviders,
        blockedProviders = blockedProviders,
        timeoutProviders = timeoutProviders,
        failedProviders = failedProviders,
        diagnostics = diagnostics.map { it.toMobile() },
        fromCache = fromCache,
        coalesced = coalesced,
        readyProviders = readyProviders,
        repairedProviders = repairedProviders,
        preflightErrors = preflightErrors,
    )

private fun SharedProviderDiagnostic.toMobile(): ProviderDiagnostic =
    ProviderDiagnostic(
        repositoryManifestUrl = repositoryManifestUrl,
        repositoryName = repositoryName,
        providerId = providerId,
        providerName = providerName,
        status = ProviderHealthStatus.valueOf(status.name),
        responseMs = responseMs,
        streamCount = streamCount,
        error = error,
        logs = logs,
    )

private fun SourceCandidate.toMobile(): StreamSource =
    StreamSource(
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId,
        providerName = providerName,
    )
