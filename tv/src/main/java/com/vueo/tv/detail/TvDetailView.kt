package com.vueo.tv.detail

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.delay

internal val Detail39HorizontalPadding = 52.dp
internal val Detail39HeroHeight = 540.dp

private enum class Detail39PeopleTab {
    CAST,
    MORE_LIKE_THIS,
}

/**
 * 39A composition is sourced from Nuvio's MetaDetailsScreen structure:
 * sticky backdrop -> hero -> season tabs -> episodes -> Cast/More Like This tabs
 * -> networks/production. VUEO remains only the data/action boundary.
 */
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
    val libraryRequester = remember(mediaKey) { FocusRequester() }
    val seasonRequester = remember(mediaKey) { FocusRequester() }
    val episodeRequester = remember(mediaKey) { FocusRequester() }
    val castTabRequester = remember(mediaKey) { FocusRequester() }
    val relatedTabRequester = remember(mediaKey) { FocusRequester() }
    val castRequester = remember(mediaKey) { FocusRequester() }
    val relatedRequester = remember(mediaKey) { FocusRequester() }
    val networkRequester = remember(mediaKey) { FocusRequester() }
    val productionRequester = remember(mediaKey) { FocusRequester() }
    val insightRequester = remember(mediaKey) { FocusRequester() }

    val hasSeasons = state.item.isTvSeries() && state.seasons.isNotEmpty()
    val hasEpisodes = state.item.isTvSeries() && state.episodes.isNotEmpty()
    val hasCast = state.item.cast.isNotEmpty()
    val hasRelated = state.related.isNotEmpty()
    val hasPeopleTabs = hasCast && hasRelated
    val hasNetworks = state.item.networks.isNotEmpty()
    val hasProduction = state.item.productionCompanies.isNotEmpty()
    val hasInsight = state.insightAvailable

    var activePeopleTab by remember(mediaKey) {
        mutableStateOf(if (hasCast) Detail39PeopleTab.CAST else Detail39PeopleTab.MORE_LIKE_THIS)
    }

    LaunchedEffect(hasCast, hasRelated) {
        if (!hasCast && hasRelated) activePeopleTab = Detail39PeopleTab.MORE_LIKE_THIS
        if (hasCast && !hasRelated) activePeopleTab = Detail39PeopleTab.CAST
    }

    val activePeopleContentRequester = when (activePeopleTab) {
        Detail39PeopleTab.CAST -> castRequester
        Detail39PeopleTab.MORE_LIKE_THIS -> relatedRequester
    }
    val activePeopleTabRequester = when (activePeopleTab) {
        Detail39PeopleTab.CAST -> castTabRequester
        Detail39PeopleTab.MORE_LIKE_THIS -> relatedTabRequester
    }

    fun firstOf(vararg candidates: Pair<Boolean, FocusRequester>): FocusRequester? =
        candidates.firstOrNull { it.first }?.second

    val firstAfterHero = firstOf(
        hasSeasons to seasonRequester,
        hasEpisodes to episodeRequester,
        hasPeopleTabs to activePeopleTabRequester,
        hasCast to castRequester,
        hasRelated to relatedRequester,
        hasNetworks to networkRequester,
        hasProduction to productionRequester,
        hasInsight to insightRequester,
    )
    val firstAfterSeasons = firstOf(
        hasEpisodes to episodeRequester,
        hasPeopleTabs to activePeopleTabRequester,
        hasCast to castRequester,
        hasRelated to relatedRequester,
        hasNetworks to networkRequester,
        hasProduction to productionRequester,
        hasInsight to insightRequester,
    )
    val firstAfterEpisodes = firstOf(
        hasPeopleTabs to activePeopleTabRequester,
        hasCast to castRequester,
        hasRelated to relatedRequester,
        hasNetworks to networkRequester,
        hasProduction to productionRequester,
        hasInsight to insightRequester,
    )
    val firstAfterPeople = firstOf(
        hasNetworks to networkRequester,
        hasProduction to productionRequester,
        hasInsight to insightRequester,
    )
    val firstAfterNetworks = firstOf(
        hasProduction to productionRequester,
        hasInsight to insightRequester,
    )
    val firstAfterProduction = firstOf(hasInsight to insightRequester)

    val peopleUpRequester = when {
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val networkUpRequester = when {
        hasPeopleTabs -> activePeopleContentRequester
        hasCast -> castRequester
        hasRelated -> relatedRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val productionUpRequester = if (hasNetworks) networkRequester else networkUpRequester
    val insightUpRequester = when {
        hasProduction -> productionRequester
        hasNetworks -> networkRequester
        hasPeopleTabs -> activePeopleContentRequester
        hasCast -> castRequester
        hasRelated -> relatedRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }

    val scrolledPastHero = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260
    val backdropAlpha by animateFloatAsState(
        targetValue = if (scrolledPastHero) .15f else 1f,
        animationSpec = tween(if (scrolledPastHero) 300 else 800),
        label = "detail39BackdropAlpha",
    )
    val heroScrimAlpha by animateFloatAsState(
        targetValue = if (scrolledPastHero) 0f else 1f,
        animationSpec = tween(if (scrolledPastHero) 300 else 800),
        label = "detail39HeroScrimAlpha",
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
        TvDetail39Backdrop(
            item = state.item,
            imageAlpha = backdropAlpha,
            scrimAlpha = heroScrimAlpha,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "hero:$mediaKey", contentType = "hero") {
                TvDetail39Hero(
                    state = state,
                    playRequester = playRequester,
                    libraryRequester = libraryRequester,
                    downRequester = firstAfterHero,
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                )
            }

            if (hasSeasons) {
                item(key = "season-tabs:$mediaKey", contentType = "season-tabs") {
                    TvDetail39SeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        selectedRequester = seasonRequester,
                        upRequester = playRequester,
                        downRequester = firstAfterSeasons,
                        onSelect = onSeasonSelected,
                    )
                }
            }

            if (hasEpisodes) {
                item(key = "episodes:$mediaKey:${state.selectedSeason}", contentType = "episodes") {
                    TvDetail39EpisodeRow(
                        media = state.item,
                        episodes = state.episodes,
                        selectedEpisode = state.selectedEpisode,
                        history = state.history,
                        rowRequester = episodeRequester,
                        upRequester = if (hasSeasons) seasonRequester else playRequester,
                        downRequester = firstAfterEpisodes,
                        onFocused = onEpisodeFocused,
                        onOpen = onEpisodeSelected,
                    )
                }
            } else if (state.item.isTvSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "episodes-empty:$mediaKey", contentType = "message") {
                    TvDetail39Message("Episodes are not available for this title yet.")
                }
            }

            if (hasPeopleTabs) {
                item(key = "people-tabs:$mediaKey", contentType = "people-tabs") {
                    TvDetail39PeopleTabs(
                        activeCast = activePeopleTab == Detail39PeopleTab.CAST,
                        castRequester = castTabRequester,
                        relatedRequester = relatedTabRequester,
                        upRequester = peopleUpRequester,
                        downRequester = activePeopleContentRequester,
                        onCastFocused = { activePeopleTab = Detail39PeopleTab.CAST },
                        onRelatedFocused = { activePeopleTab = Detail39PeopleTab.MORE_LIKE_THIS },
                    )
                }

                item(key = "people-content:$mediaKey", contentType = "horizontal-row") {
                    Crossfade(
                        targetState = activePeopleTab,
                        animationSpec = tween(160),
                        label = "detail39PeopleSwitch",
                    ) { tab ->
                        when (tab) {
                            Detail39PeopleTab.CAST -> TvDetail39CastRow(
                                cast = state.item.cast,
                                title = null,
                                rowRequester = castRequester,
                                upRequester = castTabRequester,
                                downRequester = firstAfterPeople,
                            )
                            Detail39PeopleTab.MORE_LIKE_THIS -> TvDetail39RelatedRow(
                                items = state.related,
                                title = null,
                                rowRequester = relatedRequester,
                                upRequester = relatedTabRequester,
                                downRequester = firstAfterPeople,
                                onOpen = onOpenRelated,
                            )
                        }
                    }
                }
            } else if (hasCast) {
                item(key = "cast:$mediaKey", contentType = "horizontal-row") {
                    TvDetail39CastRow(
                        cast = state.item.cast,
                        title = "Cast",
                        rowRequester = castRequester,
                        upRequester = peopleUpRequester,
                        downRequester = firstAfterPeople,
                    )
                }
            } else if (hasRelated) {
                item(key = "related:$mediaKey", contentType = "horizontal-row") {
                    TvDetail39RelatedRow(
                        items = state.related,
                        title = "More Like This",
                        rowRequester = relatedRequester,
                        upRequester = peopleUpRequester,
                        downRequester = firstAfterPeople,
                        onOpen = onOpenRelated,
                    )
                }
            }

            if (hasNetworks) {
                item(key = "networks:$mediaKey", contentType = "horizontal-row") {
                    TvDetail39CompanyRow(
                        title = if (state.item.networks.size == 1) "Network" else "Networks",
                        companies = state.item.networks,
                        rowRequester = networkRequester,
                        upRequester = networkUpRequester,
                        downRequester = firstAfterNetworks,
                    )
                }
            }

            if (hasProduction) {
                item(key = "production:$mediaKey", contentType = "horizontal-row") {
                    TvDetail39CompanyRow(
                        title = "Production",
                        companies = state.item.productionCompanies,
                        rowRequester = productionRequester,
                        upRequester = productionUpRequester,
                        downRequester = firstAfterProduction,
                    )
                }
            }

            if (hasInsight) {
                item(key = "insight:$mediaKey", contentType = "insight") {
                    TvDetail39Insight(
                        insight = state.insight,
                        loading = state.insightLoading,
                        error = state.insightError,
                        requester = insightRequester,
                        upRequester = insightUpRequester,
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
