package com.vueo.tv.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.vueo.shared.core.detail.DetailPeoplePolicy
import com.vueo.shared.core.search.MediaEntityKind
import com.vueo.shared.core.search.MediaEntityTarget
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** TV Details composition and remote focus map. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailPresentation(
    state: TvDetailPresentationState,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeFocused: (com.vueo.shared.core.media.EpisodeItem) -> Unit,
    onEpisodeSelected: (com.vueo.shared.core.media.EpisodeItem) -> Unit,
    onOpenRelated: (com.vueo.shared.core.media.MediaItem) -> Unit,
    onOpenEntity: (MediaEntityTarget) -> Unit,
) {
    val mediaKey = "${state.item.type}:${state.item.id}"
    val listState = rememberLazyListState()
    val safeBottom = with(LocalDensity.current) { 32.dp.toPx() }
    val detailBringIntoView = remember(safeBottom) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                size >= containerSize -> 0f
                offset < 0f -> offset
                offset + size > containerSize - safeBottom -> offset + size - (containerSize - safeBottom)
                else -> 0f
            }
        }
    }

    val playRequester = remember(mediaKey) { FocusRequester() }
    val listRequester = remember(mediaKey) { FocusRequester() }
    val seasonRequester = remember(mediaKey) { FocusRequester() }
    val episodeRequester = remember(mediaKey) { FocusRequester() }
    val peopleContentRequester = remember(mediaKey) { FocusRequester() }
    val relatedContentRequester = remember(mediaKey) { FocusRequester() }

    val people = remember(state.item) {
        DetailPeoplePolicy.cast(state.item)
    }
    val hasCast = people.isNotEmpty()
    val hasRelated = state.related.isNotEmpty()
    val hasCompanies = state.item.networks.isNotEmpty() || state.item.productionCompanies.isNotEmpty()
    val hasSeasons = state.item.isDetailSeries() && state.seasons.isNotEmpty()
    val hasEpisodes = state.item.isDetailSeries() && state.episodes.isNotEmpty()

    val firstBelowHero = when {
        hasSeasons -> seasonRequester
        hasEpisodes -> episodeRequester
        hasCast -> peopleContentRequester
        !hasCompanies && hasRelated -> relatedContentRequester
        else -> null
    }
    val navigationScope = rememberCoroutineScope()
    var movingBetweenSections by remember(mediaKey) { mutableStateOf(false) }
    fun revealAndFocus(index: Int, target: FocusRequester) {
        if (movingBetweenSections) return
        movingBetweenSections = true
        navigationScope.launch {
            try {
                val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                // Reveal the next lazy item before requesting its not-yet-attached focus node.
                listState.animateScrollToItem(index, if (index == 0) 0 else -viewport / 2)
                for (attempt in 0 until 8) {
                    withFrameNanos { }
                    if (runCatching { target.requestFocus() }.getOrDefault(false)) break
                }
            } finally {
                movingBetweenSections = false
            }
        }
    }
    fun sectionNavigation(up: Pair<Int, FocusRequester>?, down: Pair<Int, FocusRequester>?): Modifier =
        Modifier.onPreviewKeyEvent { event ->
            val destination = when (event.key) {
                Key.DirectionDown -> down
                Key.DirectionUp -> up
                else -> null
            } ?: return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) revealAndFocus(destination.first, destination.second)
            true
        }

    val firstBelowSeasons = when {
        hasEpisodes -> episodeRequester
        hasCast -> peopleContentRequester
        !hasCompanies && hasRelated -> relatedContentRequester
        else -> null
    }
    val firstBelowEpisodes = when {
        hasCast -> peopleContentRequester
        !hasCompanies && hasRelated -> relatedContentRequester
        else -> null
    }
    val peopleUp = when {
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val relatedUpRequester = when {
        hasCompanies -> null
        hasCast -> peopleContentRequester
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }

    val backdropScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 190
    val backdropAlpha by animateFloatAsState(
        targetValue = if (backdropScrolled) .55f else 1f,
        animationSpec = tween(
            durationMillis = if (backdropScrolled) 180 else TvMotion.BACKDROP_MS,
            easing = if (backdropScrolled) TvMotion.EaseInOut else TvMotion.EaseOut,
        ),
        label = "detail39BackdropAlpha",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = if (backdropScrolled) 180 else TvMotion.BACKDROP_MS,
            easing = if (backdropScrolled) TvMotion.EaseInOut else TvMotion.EaseOut,
        ),
        label = "detail39ScrimAlpha",
    )

    LaunchedEffect(mediaKey) {
        delay(110)
        runCatching { playRequester.requestFocus() }
    }

    LaunchedEffect(mediaKey, state.selectedEpisode?.id) {
        val rememberedEpisodeId = VueoDetailFocusMemory.episodeId
        val restoreEpisode = state.item.isDetailSeries() &&
            VueoDetailFocusMemory.mediaKey == mediaKey &&
            rememberedEpisodeId != null &&
            rememberedEpisodeId == state.selectedEpisode?.id
        if (restoreEpisode) {
            listState.scrollToItem(if (hasSeasons) 2 else 1)
            delay(120)
            runCatching { episodeRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        val heroHeight = maxHeight
        VueoDetailBackdrop(
            item = state.item,
            imageAlpha = backdropAlpha,
            scrimAlpha = scrimAlpha,
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides detailBringIntoView) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "vueo-hero:$mediaKey") {
                Box(sectionNavigation(null, firstBelowHero?.let { 1 to it })) {
                VueoDetailHero(
                    state = state,
                    heroHeight = heroHeight,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = firstBelowHero,
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                    onToggleWatched = onToggleWatched,
                )
                }
            }

            if (hasSeasons) {
                item(key = "vueo-seasons:$mediaKey") {
                    Box(sectionNavigation(0 to playRequester, firstBelowSeasons?.let { 2 to it })) {
                    VueoDetailSeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        sectionRequester = seasonRequester,
                        upRequester = playRequester,
                        downRequester = firstBelowSeasons,
                        onSelect = onSeasonSelected,
                    )
                    }
                }
            }

            if (hasEpisodes) {
                item(key = "vueo-episodes:$mediaKey:${state.selectedSeason}") {
                    val episodeIndex = if (hasSeasons) 2 else 1
                    Box(sectionNavigation(
                        if (hasSeasons) 1 to seasonRequester else 0 to playRequester,
                        firstBelowEpisodes?.let { episodeIndex + 1 to it },
                    )) {
                    VueoDetailEpisodes(
                        media = state.item,
                        episodes = state.episodes,
                        episodeRatings = state.episodeRatings,
                        selectedEpisode = state.selectedEpisode,
                        history = state.history,
                        sectionRequester = episodeRequester,
                        upRequester = if (hasSeasons) seasonRequester else playRequester,
                        downRequester = firstBelowEpisodes,
                        onFocused = onEpisodeFocused,
                        onOpen = onEpisodeSelected,
                    )
                    }
                }
            } else if (state.item.isDetailSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "vueo-episodes-empty:$mediaKey") {
                    VueoDetailMessage("Episodes are not available for this title yet.")
                }
            }

            if (hasCast) {
                item(key = "vueo-cast:$mediaKey") {
                    VueoDetailCastSection(
                        cast = people,
                        sectionRequester = peopleContentRequester,
                        upRequester = peopleUp,
                        downRequester = if (!hasCompanies && hasRelated) relatedContentRequester else null,
                        onOpen = { person ->
                            onOpenEntity(
                                MediaEntityTarget(
                                    kind = MediaEntityKind.ACTOR,
                                    name = person.name,
                                )
                            )
                        },
                    )
                }
            }

            val networks = state.item.networks
            val production = state.item.productionCompanies
            if (networks.isNotEmpty()) {
                item(key = "vueo-networks:$mediaKey") {
                    VueoDetailCompanies(
                        title = if (networks.size == 1) "Network" else "Networks",
                        companies = networks,
                        onOpenCompany = { company ->
                            onOpenEntity(
                                MediaEntityTarget(
                                    kind = MediaEntityKind.NETWORK,
                                    name = company.name,
                                    tmdbId = company.tmdbId,
                                )
                            )
                        },
                    )
                }
            }
            if (production.isNotEmpty()) {
                item(key = "vueo-production:$mediaKey") {
                    VueoDetailCompanies(
                        title = "Production",
                        companies = production,
                        onOpenCompany = { company ->
                            onOpenEntity(
                                MediaEntityTarget(
                                    kind = MediaEntityKind.COMPANY,
                                    name = company.name,
                                    tmdbId = company.tmdbId,
                                )
                            )
                        },
                    )
                }
            }

            if (hasRelated) {
                item(key = "vueo-related:$mediaKey") {
                    VueoDetailRelatedSection(
                        items = state.related,
                        sectionRequester = relatedContentRequester,
                        upRequester = relatedUpRequester,
                        usesTmdb = state.tmdbMoreLikeThisEnabled,
                        onOpen = onOpenRelated,
                    )
                }
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
