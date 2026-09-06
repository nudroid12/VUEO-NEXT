package com.vueo.tv.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.delay

internal val DetailHorizontalPadding = 52.dp
internal val DetailHeroHeight = 540.dp

/**
 * 36A Details composition.
 *
 * This file is intentionally only the screen composition. Hero, episodes and
 * supporting rows are split into fresh files so none of the failed 34A
 * monolithic presentation survives.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailPresentation(
    state: TvDetailPresentationState,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeFocused: (com.vueo.shared.core.media.EpisodeItem) -> Unit,
    onEpisodeSelected: (com.vueo.shared.core.media.EpisodeItem) -> Unit,
    onOpenRelated: (com.vueo.shared.core.media.MediaItem) -> Unit,
    onGenerateInsight: () -> Unit,
) {
    val mediaKey = "${state.item.type}:${state.item.id}"
    val listState = rememberLazyListState()
    val playRequester = remember(mediaKey) { FocusRequester() }
    val listRequester = remember(mediaKey) { FocusRequester() }
    val seasonRequester = remember(mediaKey) { FocusRequester() }
    val episodeRowRequester = remember(mediaKey) { FocusRequester() }
    val relatedRowRequester = remember(mediaKey) { FocusRequester() }
    val insightRequester = remember(mediaKey) { FocusRequester() }

    val dimBackdrop = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
    val backdropAlpha by animateFloatAsState(
        targetValue = if (dimBackdrop) .16f else 1f,
        animationSpec = tween(if (dimBackdrop) 260 else 520),
        label = "detailBackdropAlpha",
    )
    val gradientAlpha by animateFloatAsState(
        targetValue = if (dimBackdrop) 0f else 1f,
        animationSpec = tween(if (dimBackdrop) 220 else 480),
        label = "detailGradientAlpha",
    )

    val firstContentDownRequester = when {
        state.item.isDetailSeries() && state.seasons.isNotEmpty() -> seasonRequester
        state.item.isDetailSeries() && state.episodes.isNotEmpty() -> episodeRowRequester
        state.related.isNotEmpty() -> relatedRowRequester
        state.insightAvailable -> insightRequester
        else -> null
    }
    val afterEpisodesRequester = when {
        state.related.isNotEmpty() -> relatedRowRequester
        state.insightAvailable -> insightRequester
        else -> null
    }

    LaunchedEffect(mediaKey, state.loading, state.episodes) {
        if (state.loading) return@LaunchedEffect
        val restoreEpisode = TvDetailFocusMemory.episodeId
            ?.takeIf { id -> state.episodes.any { it.id == id } }
        if (restoreEpisode == null) {
            delay(120)
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        TvDetailBackdrop(
            item = state.item,
            imageAlpha = backdropAlpha,
            gradientAlpha = gradientAlpha,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero:$mediaKey") {
                TvDetailHero(
                    state = state,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = firstContentDownRequester,
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                )
            }

            if (state.item.isDetailSeries() && state.seasons.isNotEmpty()) {
                item(key = "seasons:$mediaKey") {
                    TvDetailSeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        selectedRequester = seasonRequester,
                        upRequester = playRequester,
                        downRequester = if (state.episodes.isNotEmpty()) episodeRowRequester else afterEpisodesRequester,
                        onSelect = onSeasonSelected,
                    )
                }
            }

            if (state.item.isDetailSeries() && state.episodes.isNotEmpty()) {
                item(key = "episodes:$mediaKey:${state.selectedSeason}") {
                    TvDetailEpisodesRow(
                        media = state.item,
                        episodes = state.episodes,
                        selectedEpisode = state.selectedEpisode,
                        history = state.history,
                        rowRequester = episodeRowRequester,
                        upRequester = if (state.seasons.isNotEmpty()) seasonRequester else playRequester,
                        downRequester = afterEpisodesRequester,
                        onFocused = onEpisodeFocused,
                        onOpen = onEpisodeSelected,
                    )
                }
            } else if (state.item.isDetailSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "episodes-empty:$mediaKey") {
                    TvDetailMessage("Episodes are not available for this title yet.")
                }
            }

            if (state.item.cast.isNotEmpty()) {
                item(key = "cast:$mediaKey") {
                    TvDetailCastRow(state.item.cast)
                }
            }

            val companies = if (state.item.isDetailSeries()) state.item.networks else state.item.productionCompanies
            if (companies.isNotEmpty()) {
                item(key = "companies:$mediaKey") {
                    TvDetailCompanyRow(
                        title = if (state.item.isDetailSeries()) {
                            if (companies.size == 1) "Network" else "Networks"
                        } else {
                            "Production"
                        },
                        companies = companies,
                    )
                }
            }

            if (state.related.isNotEmpty()) {
                item(key = "related:$mediaKey") {
                    TvDetailRelatedRow(
                        items = state.related,
                        rowRequester = relatedRowRequester,
                        upRequester = if (state.item.isDetailSeries() && state.episodes.isNotEmpty()) {
                            episodeRowRequester
                        } else if (state.item.isDetailSeries() && state.seasons.isNotEmpty()) {
                            seasonRequester
                        } else {
                            playRequester
                        },
                        downRequester = if (state.insightAvailable) insightRequester else null,
                        onOpen = onOpenRelated,
                    )
                }
            }

            if (state.insightAvailable) {
                item(key = "insight:$mediaKey") {
                    TvDetailInsight(
                        insight = state.insight,
                        loading = state.insightLoading,
                        error = state.insightError,
                        requester = insightRequester,
                        upRequester = when {
                            state.related.isNotEmpty() -> relatedRowRequester
                            state.item.isDetailSeries() && state.episodes.isNotEmpty() -> episodeRowRequester
                            state.item.isDetailSeries() && state.seasons.isNotEmpty() -> seasonRequester
                            else -> playRequester
                        },
                        onGenerate = onGenerateInsight,
                    )
                }
            }
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 30.dp, end = 38.dp)
                    .size(22.dp),
                color = TvDesign.White,
                strokeWidth = 2.dp,
            )
        }
    }
}
