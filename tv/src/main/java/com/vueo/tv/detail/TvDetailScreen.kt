package com.vueo.tv.detail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.core.TvRuntime
import kotlinx.coroutines.launch

/**
 * TV 38A Details runtime boundary.
 *
 * Only data loading, library state and navigation callbacks live here.
 * Layout, focus, styling and section composition are owned by the new 38A UI
 * files and do not reuse the 34A/36A presentation tree.
 */
@Composable
fun TvDetailScreen(
    runtime: TvRuntime,
    initial: MediaItem,
    onBack: () -> Unit,
    onWatch: (MediaItem, EpisodeItem?) -> Unit,
    onOpenRelated: (MediaItem) -> Unit = {},
    onLibraryChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val mediaKey = "${initial.type}:${initial.id}"
    var item by remember(mediaKey) { mutableStateOf(initial) }
    var loading by remember(mediaKey) { mutableStateOf(true) }
    var watchlisted by remember(mediaKey) { mutableStateOf(runtime.libraryStore.isWatchlisted(initial)) }
    var ratings by remember(mediaKey) { mutableStateOf<List<MediaRating>>(emptyList()) }
    var related by remember(mediaKey) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedSeason by remember(mediaKey) { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember(mediaKey) { mutableStateOf<EpisodeItem?>(null) }
    var insight by remember(mediaKey) { mutableStateOf<String?>(null) }
    var insightLoading by remember(mediaKey) { mutableStateOf(false) }
    var insightError by remember(mediaKey) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mediaKey) {
        val restoringSameTitle = TvDetailSessionMemory.mediaKey == mediaKey
        if (!restoringSameTitle) TvDetailSessionMemory.reset(mediaKey)

        loading = true
        val enriched = runCatching { runtime.loadMeta(initial) }.getOrDefault(initial)
        item = enriched
        watchlisted = runtime.libraryStore.isWatchlisted(enriched)
        ratings = (
            detailBaseRatings(enriched) +
                runCatching { runtime.ratings(enriched) }.getOrDefault(emptyList())
            )
            .associateBy(MediaRating::source)
            .values
            .toList()
        related = runCatching { runtime.relatedTitles(enriched) }.getOrDefault(emptyList())

        if (enriched.isTvSeries()) {
            val history = runtime.libraryStore.history()
            val resumeEntry = history.firstOrNull { entry ->
                entry.media.id == enriched.id &&
                    entry.media.type == enriched.type &&
                    entry.season != null &&
                    entry.episode != null &&
                    detailCanResume(entry)
            }
            val resumeEpisode = resumeEntry?.let { entry ->
                enriched.episodes.firstOrNull { episode ->
                    episode.season == entry.season && episode.episode == entry.episode
                }
            }
            val seasons = detailSeasonNumbers(enriched)
            val rememberedSeason = TvDetailSessionMemory.selectedSeason
                ?.takeIf { restoringSameTitle && it in seasons }
            val initialSeason = rememberedSeason ?: resumeEpisode?.season ?: seasons.firstOrNull()

            selectedSeason = initialSeason
            selectedEpisode = TvDetailSessionMemory.episodeId
                ?.takeIf { restoringSameTitle }
                ?.let { id -> enriched.episodes.firstOrNull { it.id == id && it.season == initialSeason } }
                ?: resumeEpisode?.takeIf { it.season == initialSeason }
                ?: enriched.episodes.firstOrNull { it.season == initialSeason }

            TvDetailSessionMemory.selectedSeason = initialSeason
        } else {
            selectedSeason = null
            selectedEpisode = null
            TvDetailSessionMemory.selectedSeason = null
            TvDetailSessionMemory.episodeId = null
        }
        loading = false
    }

    val history = remember(item.id, item.type, selectedEpisode?.id, loading) {
        runtime.libraryStore.history()
    }
    val playbackEntry = remember(item.id, item.type, selectedEpisode?.id, history) {
        detailPlaybackEntry(item, selectedEpisode, history)
    }
    val seasons = remember(item.episodes) { detailSeasonNumbers(item) }
    val episodesForSeason = remember(item.episodes, selectedSeason) {
        item.episodes
            .filter { it.season == selectedSeason }
            .sortedBy { it.episode }
    }
    val dnaMatch = remember(item, loading) {
        if (loading) null else runtime.dnaMatch(item)
    }
    val primaryActionLabel = remember(item, selectedEpisode?.id, playbackEntry, loading) {
        detailPrimaryActionLabel(item, selectedEpisode, playbackEntry, loading)
    }
    val insightAvailable = remember(loading) {
        !loading &&
            runtime.settingsStore.geminiInsightsEnabled() &&
            runtime.settingsStore.geminiApiKey().isNotBlank()
    }

    TvDetailView(
        state = TvDetailUiState(
            item = item,
            loading = loading,
            watchlisted = watchlisted,
            ratings = ratings,
            dnaMatch = dnaMatch,
            seasons = seasons,
            selectedSeason = selectedSeason,
            episodes = episodesForSeason,
            selectedEpisode = selectedEpisode,
            history = history,
            playbackEntry = playbackEntry,
            related = related,
            primaryActionLabel = primaryActionLabel,
            insightAvailable = insightAvailable,
            insight = insight,
            insightLoading = insightLoading,
            insightError = insightError,
        ),
        onPlay = {
            if (!loading && (!item.isTvSeries() || selectedEpisode != null)) {
                onWatch(item, selectedEpisode)
            }
        },
        onToggleList = {
            watchlisted = runtime.libraryStore.toggleWatchlist(item)
            onLibraryChanged()
        },
        onSeasonSelected = { season ->
            selectedSeason = season
            selectedEpisode = item.episodes
                .filter { it.season == season }
                .minByOrNull { it.episode }
            TvDetailSessionMemory.selectedSeason = season
            TvDetailSessionMemory.episodeId = null
        },
        onEpisodeFocused = { episode ->
            selectedSeason = episode.season
            selectedEpisode = episode
            TvDetailSessionMemory.selectedSeason = episode.season
            TvDetailSessionMemory.episodeId = episode.id
        },
        onEpisodeSelected = { episode ->
            selectedSeason = episode.season
            selectedEpisode = episode
            TvDetailSessionMemory.selectedSeason = episode.season
            TvDetailSessionMemory.episodeId = episode.id
            onWatch(item, episode)
        },
        onOpenRelated = onOpenRelated,
        onGenerateInsight = {
            if (!insightLoading) {
                insightLoading = true
                insightError = null
                scope.launch {
                    runCatching { runtime.geminiInsight(item) }
                        .onSuccess { result ->
                            insight = result
                            if (result.isNullOrBlank()) insightError = "No insight returned."
                        }
                        .onFailure { error ->
                            insightError = error.message ?: "Insight failed."
                        }
                    insightLoading = false
                }
            }
        },
    )
}
