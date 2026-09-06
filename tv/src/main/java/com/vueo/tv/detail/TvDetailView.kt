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

internal val Detail38HorizontalPadding = 52.dp
internal val Detail38HeroHeight = 540.dp

/** Fresh 38A Details composition root. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailView(
    state: TvDetailUiState,
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
    val episodeRequester = remember(mediaKey) { FocusRequester() }
    val castRequester = remember(mediaKey) { FocusRequester() }
    val companyRequester = remember(mediaKey) { FocusRequester() }
    val relatedRequester = remember(mediaKey) { FocusRequester() }
    val insightRequester = remember(mediaKey) { FocusRequester() }

    val companies = if (state.item.isTvSeries()) state.item.networks else state.item.productionCompanies
    val hasSeasons = state.item.isTvSeries() && state.seasons.isNotEmpty()
    val hasEpisodes = state.item.isTvSeries() && state.episodes.isNotEmpty()
    val hasCast = state.item.cast.isNotEmpty()
    val hasCompanies = companies.isNotEmpty()
    val hasRelated = state.related.isNotEmpty()
    val hasInsight = state.insightAvailable

    fun firstOf(vararg candidates: Pair<Boolean, FocusRequester>): FocusRequester? =
        candidates.firstOrNull { it.first }?.second

    val afterHero = firstOf(
        hasSeasons to seasonRequester,
        hasEpisodes to episodeRequester,
        hasCast to castRequester,
        hasCompanies to companyRequester,
        hasRelated to relatedRequester,
        hasInsight to insightRequester,
    )
    val afterSeasons = firstOf(
        hasEpisodes to episodeRequester,
        hasCast to castRequester,
        hasCompanies to companyRequester,
        hasRelated to relatedRequester,
        hasInsight to insightRequester,
    )
    val afterEpisodes = firstOf(
        hasCast to castRequester,
        hasCompanies to companyRequester,
        hasRelated to relatedRequester,
        hasInsight to insightRequester,
    )
    val afterCast = firstOf(
        hasCompanies to companyRequester,
        hasRelated to relatedRequester,
        hasInsight to insightRequester,
    )
    val afterCompanies = firstOf(
        hasRelated to relatedRequester,
        hasInsight to insightRequester,
    )

    val beforeCast = when {
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val beforeCompanies = when {
        hasCast -> castRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val beforeRelated = when {
        hasCompanies -> companyRequester
        hasCast -> castRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val beforeInsight = when {
        hasRelated -> relatedRequester
        hasCompanies -> companyRequester
        hasCast -> castRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }

    val backdropDimmed = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 210
    val imageAlpha by animateFloatAsState(
        targetValue = if (backdropDimmed) .14f else 1f,
        animationSpec = tween(if (backdropDimmed) 240 else 500),
        label = "detail38BackdropAlpha",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (backdropDimmed) 0f else 1f,
        animationSpec = tween(if (backdropDimmed) 210 else 440),
        label = "detail38ScrimAlpha",
    )

    LaunchedEffect(mediaKey, state.loading, state.episodes) {
        if (state.loading) return@LaunchedEffect
        val restoringEpisode = TvDetailSessionMemory.episodeId
            ?.takeIf { id -> state.episodes.any { it.id == id } }
        if (restoringEpisode == null) {
            delay(120)
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        TvDetail38Backdrop(
            item = state.item,
            imageAlpha = imageAlpha,
            scrimAlpha = scrimAlpha,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero:$mediaKey") {
                TvDetail38Hero(
                    state = state,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = afterHero,
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                )
            }

            if (hasSeasons) {
                item(key = "seasons:$mediaKey") {
                    TvDetail38SeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        rowRequester = seasonRequester,
                        upRequester = playRequester,
                        downRequester = afterSeasons,
                        onSelect = onSeasonSelected,
                    )
                }
            }

            if (hasEpisodes) {
                item(key = "episodes:$mediaKey:${state.selectedSeason}") {
                    TvDetail38EpisodeRail(
                        media = state.item,
                        episodes = state.episodes,
                        selectedEpisode = state.selectedEpisode,
                        history = state.history,
                        rowRequester = episodeRequester,
                        upRequester = if (hasSeasons) seasonRequester else playRequester,
                        downRequester = afterEpisodes,
                        onFocused = onEpisodeFocused,
                        onOpen = onEpisodeSelected,
                    )
                }
            } else if (state.item.isTvSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "episodes-empty:$mediaKey") {
                    TvDetail38Message("Episodes are not available for this title yet.")
                }
            }

            if (hasCast) {
                item(key = "cast:$mediaKey") {
                    TvDetail38CastRail(
                        cast = state.item.cast,
                        rowRequester = castRequester,
                        upRequester = beforeCast,
                        downRequester = afterCast,
                    )
                }
            }

            if (hasCompanies) {
                item(key = "companies:$mediaKey") {
                    TvDetail38CompanyRail(
                        title = if (state.item.isTvSeries()) {
                            if (companies.size == 1) "Network" else "Networks"
                        } else {
                            "Production"
                        },
                        companies = companies,
                        rowRequester = companyRequester,
                        upRequester = beforeCompanies,
                        downRequester = afterCompanies,
                    )
                }
            }

            if (hasRelated) {
                item(key = "related:$mediaKey") {
                    TvDetail38RelatedRail(
                        items = state.related,
                        rowRequester = relatedRequester,
                        upRequester = beforeRelated,
                        downRequester = if (hasInsight) insightRequester else null,
                        onOpen = onOpenRelated,
                    )
                }
            }

            if (hasInsight) {
                item(key = "insight:$mediaKey") {
                    TvDetail38Insight(
                        insight = state.insight,
                        loading = state.insightLoading,
                        error = state.insightError,
                        requester = insightRequester,
                        upRequester = beforeInsight,
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
