package com.vueo.tv.source

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import java.net.URI

/**
 * TV 35A Source boundary.
 *
 * This file now owns only VUEO discovery/ranking/settings/playback semantics.
 * The previous 29F Compose presentation was removed. TV layout, focus and
 * source-card presentation live in TvSourcePresentation.kt and are rebuilt
 * using the supplied Vueo StreamScreen as the interaction/layout reference.
 */
@Composable
fun TvSourceScreen(
    runtime: TvRuntime,
    media: MediaItem,
    episode: EpisodeItem?,
    discovery: com.vueo.tv.core.TvSourceDiscoverySnapshot?,
    discoveryError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (TvSourceBundle, StreamSource) -> Unit,
) {
    BackHandler(onBack = onBack)

    val memoryKey = remember(media.id, media.type, episode?.id) {
        "${media.type}:${media.id}:${episode?.id ?: media.id}"
    }
    val memory = remember(memoryKey) { TvSourceUiMemory.forKey(memoryKey) }

    val bundle = discovery?.bundle
    val searching = discovery?.searching ?: discoveryError == null
    val progress = discovery?.progress ?: "Starting source discovery…"
    val rawCount = discovery?.rawCount ?: 0
    val notice = discovery?.notice
    val firstResultMs = discovery?.firstResultMs
    val providerOrder = discovery?.providerOrder.orEmpty()
    val fromCache = discovery?.fromCache ?: false
    val error = discoveryError
    var selectedProvider by remember(memoryKey) {
        mutableStateOf(memory.selectedProvider ?: SOURCE_PROVIDER_ALL)
    }
    var showEngineDetails by remember(memoryKey) {
        mutableStateOf(memory.showEngineDetails)
    }

    val preferredQuality = runtime.settingsStore.preferredQuality().rankKey

    val playable = bundle?.sources.orEmpty().filter(StreamSource::isDirectPlayable)
    val rankedSources = remember(playable, preferredQuality, media.originalLanguage) {
        playable.sortedWith(
            PlayerSourcePolicy.comparator(
                preferredQuality = preferredQuality,
                originalLanguage = media.originalLanguage,
            )
        )
    }
    val currentProviders = remember(rankedSources) {
        rankedSources
            .asSequence()
            .map(::sourceProviderKey)
            .distinct()
            .toList()
    }
    val visibleProviders = remember(providerOrder, currentProviders) {
        (providerOrder.filter { it in currentProviders } +
            currentProviders.filter { it !in providerOrder })
            .distinct()
    }
    val providerLogos = remember(runtime) {
        buildProviderLogoLookup(runtime)
    }

    LaunchedEffect(visibleProviders, searching) {
        if (
            selectedProvider != SOURCE_PROVIDER_ALL &&
            selectedProvider !in visibleProviders &&
            (visibleProviders.isNotEmpty() || !searching)
        ) {
            selectedProvider = SOURCE_PROVIDER_ALL
            memory.selectedProvider = SOURCE_PROVIDER_ALL
        }
    }

    val filteredSources = remember(rankedSources, selectedProvider) {
        if (selectedProvider == SOURCE_PROVIDER_ALL) rankedSources
        else rankedSources.filter { sourceProviderKey(it) == selectedProvider }
    }

    TvSourcePresentation(
        state = TvSourcePresentationState(
            media = media,
            episode = episode,
            bundle = bundle,
            searching = searching,
            progress = progress,
            rawCount = rawCount,
            notice = notice,
            firstResultMs = firstResultMs,
            fromCache = fromCache,
            error = error,
            rankedSources = rankedSources,
            filteredSources = filteredSources,
            visibleProviders = visibleProviders,
            providerLogos = providerLogos,
            selectedProvider = selectedProvider,
            preferredQuality = preferredQuality,
            showEngineDetails = showEngineDetails,
        ),
        onSelectProvider = { provider ->
            selectedProvider = provider
            memory.selectedProvider = provider
        },
        onToggleDetails = {
            showEngineDetails = !showEngineDetails
            memory.showEngineDetails = showEngineDetails
        },
        onRefresh = onRefresh,
        onSourceFocused = { source ->
            memory.focusedSourceKey = sourceStableKey(source)
            memory.selectedProvider = selectedProvider
        },
        onPlay = { source ->
            bundle?.takeIf { source.isDirectPlayable }?.let { onPlay(it, source) }
        },
    )
}

private fun buildProviderLogoLookup(runtime: TvRuntime): Map<String, String> =
    buildMap {
        for (repository in runtime.pluginStore.repositories()) {
            for (provider in repository.providers) {
                val logo = resolveProviderLogoUrl(
                    baseUrl = repository.baseUrl,
                    value = provider.logo,
                ) ?: continue
                val keys = listOf(
                    "${repository.name} / ${provider.name}",
                    provider.name,
                    provider.id,
                )
                for (key in keys) {
                    val normalized = key.trim().lowercase()
                    if (normalized !in this) put(normalized, logo)
                }
            }
        }
    }

private fun resolveProviderLogoUrl(
    baseUrl: String,
    value: String?,
): String? {
    val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    val resolved = if (
        raw.startsWith("https://", ignoreCase = true) ||
        raw.startsWith("http://", ignoreCase = true)
    ) {
        raw
    } else {
        runCatching {
            URI(baseUrl.trimEnd('/') + "/").resolve(raw).toString()
        }.getOrNull()
    }
    return resolved?.takeIf {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }
}
