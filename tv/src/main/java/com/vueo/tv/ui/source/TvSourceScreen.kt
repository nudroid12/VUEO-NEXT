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
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle

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
    val activityLog = discovery?.activityLog.orEmpty()
    val fromCache = discovery?.fromCache ?: false
    val error = discoveryError
    var selectedProvider by remember(memoryKey) {
        mutableStateOf(memory.selectedProvider ?: SOURCE_PROVIDER_ALL)
    }
    var showEngineDetails by remember(memoryKey) {
        mutableStateOf(memory.showEngineDetails)
    }

    val showTechnicalDetails = runtime.settingsStore.showSourceTechnicalDetails()
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
    val activityStates = remember(activityLog) {
        sourceProviderActivityStates(activityLog)
    }
    val configuredPluginProviders = remember(media.type) {
        if (!runtime.pluginStore.pluginsEnabled()) {
            emptyList()
        } else {
            val pluginType = MediaTypePolicy.pluginType(media.type)
            runtime.pluginStore.repositories()
                .filter(runtime.pluginStore::isRepositoryEnabled)
                .flatMap { repository ->
                    repository.providers
                        .filter { provider ->
                            runtime.pluginStore.isProviderEnabled(repository, provider)
                        }
                        .filter { provider ->
                            provider.supportedTypes.isEmpty() ||
                                pluginType in provider.supportedTypes.map { it.lowercase() }
                        }
                        .filter { provider ->
                            "android" !in provider.disabledPlatforms.map { it.lowercase() }
                        }
                        .map { provider -> "${repository.name} / ${provider.name}" }
                }
        }
    }
    val completedActivityNames = remember(activityStates) {
        activityStates.filterValues { loading -> !loading }.keys
    }
    val loadingProviders = remember(
        searching,
        activityStates,
        configuredPluginProviders,
        completedActivityNames,
        currentProviders,
    ) {
        if (!searching) {
            emptySet()
        } else {
            buildSet {
                activityStates.filterValues { it }.keys.forEach(::add)
                configuredPluginProviders.forEach { provider ->
                    val displayName = sourceProviderDisplayName(provider)
                    val completed = completedActivityNames.any {
                        sourceProviderDisplayName(it).equals(displayName, ignoreCase = true)
                    }
                    val hasResult = currentProviders.any {
                        sourceProviderDisplayName(it).equals(displayName, ignoreCase = true)
                    }
                    if (!completed && !hasResult) add(provider)
                }
            }
        }
    }
    val liveProviderOrder = remember(
        providerOrder,
        activityStates,
        configuredPluginProviders,
        currentProviders,
    ) {
        (activityStates.keys + configuredPluginProviders + providerOrder + currentProviders)
            .distinct()
    }
    val visibleProviders = remember(
        liveProviderOrder,
        currentProviders,
        loadingProviders,
        selectedProvider,
    ) {
        liveProviderOrder.filter { provider ->
            provider in currentProviders ||
                provider in loadingProviders ||
                provider == selectedProvider
        }
            .distinct()
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
            loadingProviders = loadingProviders,
            selectedProvider = selectedProvider,
            preferredQuality = preferredQuality,
            showTechnicalDetails = showTechnicalDetails,
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
