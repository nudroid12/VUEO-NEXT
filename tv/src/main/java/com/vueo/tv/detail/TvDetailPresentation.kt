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

/**
 * TV 39A — true Nuvio-first Details presentation.
 *
 * This file intentionally contains only the screen composition/root focus map.
 * The previous VUEO Details visual tree is not reused. The supplied Nuvio
 * 0.8.6 Details screen is the layout/interaction reference.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailPresentation(
    state: TvDetailPresentationState,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    onTrailer: () -> Unit,
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
    val peopleTabsRequester = remember(mediaKey) { FocusRequester() }
    val peopleContentRequester = remember(mediaKey) { FocusRequester() }
    val relatedContentRequester = remember(mediaKey) { FocusRequester() }
    val trailerContentRequester = remember(mediaKey) { FocusRequester() }
    val insightRequester = remember(mediaKey) { FocusRequester() }

    val people = remember(state.item, state.nuvioExtras.leadingCrew) {
        nuvioDetailPeople(state.item, state.nuvioExtras.leadingCrew)
    }
    val hasCast = people.isNotEmpty()
    val hasRelated = state.related.isNotEmpty()
    val hasTrailer = !state.nuvioExtras.trailerUrl.isNullOrBlank()
    val peopleSectionCount = listOf(hasCast, hasRelated, hasTrailer).count { it }
    val hasPeopleSection = peopleSectionCount > 0
    val hasPeopleTabs = peopleSectionCount > 1
    val peopleEntryRequester = when {
        hasPeopleTabs -> peopleTabsRequester
        hasCast -> peopleContentRequester
        hasRelated -> relatedContentRequester
        else -> trailerContentRequester
    }
    val hasSeasons = state.item.isDetailSeries() && state.seasons.isNotEmpty()
    val hasEpisodes = state.item.isDetailSeries() && state.episodes.isNotEmpty()

    val firstBelowHero = when {
        hasSeasons -> seasonRequester
        hasEpisodes -> episodeRequester
        hasPeopleSection -> peopleEntryRequester
        state.insightAvailable -> insightRequester
        else -> null
    }
    val firstBelowSeasons = when {
        hasEpisodes -> episodeRequester
        hasPeopleSection -> peopleEntryRequester
        state.insightAvailable -> insightRequester
        else -> null
    }
    val firstBelowEpisodes = when {
        hasPeopleSection -> peopleEntryRequester
        state.insightAvailable -> insightRequester
        else -> null
    }
    val peopleUp = when {
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }

    val backdropScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 190
    val backdropAlpha by animateFloatAsState(
        targetValue = if (backdropScrolled) .07f else 1f,
        animationSpec = tween(if (backdropScrolled) 180 else 420),
        label = "detail39BackdropAlpha",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (backdropScrolled) 0f else 1f,
        animationSpec = tween(if (backdropScrolled) 180 else 360),
        label = "detail39ScrimAlpha",
    )

    LaunchedEffect(mediaKey, state.loading, state.selectedEpisode?.id) {
        if (!state.loading) {
            delay(110)
            val restoreEpisode = state.item.isDetailSeries() &&
                NuvioDetailFocusMemory.mediaKey == mediaKey &&
                NuvioDetailFocusMemory.episodeId != null &&
                NuvioDetailFocusMemory.episodeId == state.selectedEpisode?.id
            runCatching {
                if (restoreEpisode) episodeRequester.requestFocus() else playRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        NuvioDetailBackdrop(
            item = state.item,
            imageAlpha = backdropAlpha,
            scrimAlpha = scrimAlpha,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "nuvio-hero:$mediaKey") {
                NuvioDetailHero(
                    state = state,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = firstBelowHero,
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                    onToggleWatched = onToggleWatched,
                    onTrailer = onTrailer,
                )
            }

            if (hasSeasons) {
                item(key = "nuvio-seasons:$mediaKey") {
                    NuvioDetailSeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        sectionRequester = seasonRequester,
                        upRequester = playRequester,
                        downRequester = firstBelowSeasons,
                        onSelect = onSeasonSelected,
                    )
                }
            }

            if (hasEpisodes) {
                item(key = "nuvio-episodes:$mediaKey:${state.selectedSeason}") {
                    NuvioDetailEpisodes(
                        media = state.item,
                        episodes = state.episodes,
                        selectedEpisode = state.selectedEpisode,
                        history = state.history,
                        sectionRequester = episodeRequester,
                        upRequester = if (hasSeasons) seasonRequester else playRequester,
                        downRequester = firstBelowEpisodes,
                        onFocused = onEpisodeFocused,
                        onOpen = onEpisodeSelected,
                    )
                }
            } else if (state.item.isDetailSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "nuvio-episodes-empty:$mediaKey") {
                    NuvioDetailMessage("Episodes are not available for this title yet.")
                }
            }

            if (hasPeopleSection) {
                item(key = "nuvio-people:$mediaKey") {
                    NuvioDetailPeopleSwitcher(
                        media = state.item,
                        cast = people,
                        related = state.related,
                        trailerAvailable = hasTrailer,
                        tabsRequester = peopleTabsRequester,
                        castContentRequester = peopleContentRequester,
                        relatedContentRequester = relatedContentRequester,
                        trailerContentRequester = trailerContentRequester,
                        upRequester = peopleUp,
                        downRequester = null,
                        onOpenRelated = onOpenRelated,
                        onTrailer = onTrailer,
                    )
                }
            }

            val networks = state.item.networks
            val production = state.item.productionCompanies
            if (networks.isNotEmpty()) {
                item(key = "nuvio-networks:$mediaKey") {
                    NuvioDetailCompanies(
                        title = if (networks.size == 1) "Network" else "Networks",
                        companies = networks,
                    )
                }
            }
            if (production.isNotEmpty()) {
                item(key = "nuvio-production:$mediaKey") {
                    NuvioDetailCompanies(
                        title = "Production",
                        companies = production,
                    )
                }
            }

            if (state.insightAvailable) {
                item(key = "nuvio-insight:$mediaKey") {
                    NuvioDetailInsight(
                        insight = state.insight,
                        loading = state.insightLoading,
                        error = state.insightError,
                        requester = insightRequester,
                        upRequester = when {
                            hasPeopleSection -> peopleEntryRequester
                            hasEpisodes -> episodeRequester
                            hasSeasons -> seasonRequester
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
                    .padding(top = 28.dp, end = 36.dp)
                    .size(22.dp),
                color = TvDesign.White,
                strokeWidth = 2.dp,
            )
        }
    }
}
