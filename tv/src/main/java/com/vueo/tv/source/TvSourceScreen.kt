package com.vueo.tv.source

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
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
 * using the supplied Nuvio StreamScreen as the interaction/layout reference.
 */
@Composable
fun TvSourceScreen(
    runtime: TvRuntime,
    media: MediaItem,
    episode: EpisodeItem?,
    onBack: () -> Unit,
    onPlay: (TvSourceBundle, StreamSource) -> Unit,
) {
    BackHandler(onBack = onBack)

    val memoryKey = remember(media.id, media.type, episode?.id) {
        "${media.type}:${media.id}:${episode?.id ?: media.id}"
    }
    val memory = remember(memoryKey) { TvSourceUiMemory.forKey(memoryKey) }

    var bundle by remember(memoryKey) { mutableStateOf<TvSourceBundle?>(null) }
    var searching by remember(memoryKey) { mutableStateOf(true) }
    var progress by remember(memoryKey) { mutableStateOf("Starting source discovery…") }
    var rawCount by remember(memoryKey) { mutableIntStateOf(0) }
    var notice by remember(memoryKey) { mutableStateOf<String?>(null) }
    var firstResultMs by remember(memoryKey) { mutableStateOf<Long?>(null) }
    var providerOrder by remember(memoryKey) { mutableStateOf(emptyList<String>()) }
    var fromCache by remember(memoryKey) { mutableStateOf(false) }
    var error by remember(memoryKey) { mutableStateOf<String?>(null) }
    var retryToken by remember(memoryKey) { mutableIntStateOf(0) }
    var selectedProvider by remember(memoryKey) {
        mutableStateOf(memory.selectedProvider ?: SOURCE_PROVIDER_ALL)
    }
    var showEngineDetails by remember(memoryKey) {
        mutableStateOf(memory.showEngineDetails)
    }

    val showTechnicalDetails = runtime.settingsStore.showSourceTechnicalDetails()
    val preferredQuality = runtime.settingsStore.preferredQuality().rankKey

    LaunchedEffect(memoryKey, retryToken) {
        searching = true
        error = null
        progress = "Starting source discovery…"
        if (retryToken > 0) {
            bundle = null
            rawCount = 0
            notice = null
            firstResultMs = null
            providerOrder = emptyList()
            fromCache = false
        }

        runCatching {
            runtime.discover(
                item = media,
                episode = episode,
                onUpdate = { snapshot ->
                    bundle = snapshot.bundle
                    searching = snapshot.searching
                    progress = snapshot.progress
                    rawCount = snapshot.rawCount
                    notice = snapshot.notice
                    firstResultMs = snapshot.firstResultMs
                    providerOrder = snapshot.providerOrder
                    fromCache = snapshot.fromCache
                },
            )
        }.onFailure { throwable ->
            error = throwable.message ?: "Source discovery failed"
            searching = false
        }.onSuccess { finalBundle ->
            bundle = finalBundle
            searching = false
        }
    }

    val playable = bundle?.sources.orEmpty().filter(StreamSource::isDirectPlayable)
    val rankedSources = remember(playable, preferredQuality, media.originalLanguage) {
        playable.sortedWith(
            PlayerSourcePolicy.comparator(
                preferredQuality = preferredQuality,
                originalLanguage = media.originalLanguage,
            )
        )
    }
    val recommended = remember(rankedSources, preferredQuality, media.originalLanguage) {
        rankedSources.firstOrNull { source ->
            PlayerSourcePolicy.assess(
                source = source,
                preferredQuality = preferredQuality,
                originalLanguage = media.originalLanguage,
            ).let { assessment ->
                assessment.quality.automaticRecoveryEligible &&
                    assessment.audioMatch.recommendationEligible
            }
        } ?: rankedSources.firstOrNull()
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
            selectedProvider = selectedProvider,
            preferredQuality = preferredQuality,
            showTechnicalDetails = showTechnicalDetails,
            showEngineDetails = showEngineDetails,
            recommendedSourceKey = recommended?.let(::sourceStableKey),
            rememberedSourceKey = memory.focusedSourceKey,
        ),
        onSelectProvider = { provider ->
            selectedProvider = provider
            memory.selectedProvider = provider
        },
        onToggleDetails = {
            showEngineDetails = !showEngineDetails
            memory.showEngineDetails = showEngineDetails
        },
        onRefresh = { retryToken++ },
        onSourceFocused = { source ->
            memory.focusedSourceKey = sourceStableKey(source)
            memory.selectedProvider = selectedProvider
        },
        onPlay = { source ->
            bundle?.takeIf { source.isDirectPlayable }?.let { onPlay(it, source) }
        },
    )
}
