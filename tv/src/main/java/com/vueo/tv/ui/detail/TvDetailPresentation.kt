package com.vueo.tv.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
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
    var movingBetweenSections by remember(mediaKey) { mutableStateOf(false) }
    var heroFocused by remember(mediaKey) { mutableStateOf(false) }
    var lastVerticalRepeat by remember(mediaKey) { mutableStateOf(0L) }
    // Same row relocation boundary as Nuvio: the horizontal list handles its
    // child animation without re-running the parent's vertical relocation.
    val episodeRowResponder = remember(mediaKey) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = localRect
            override suspend fun bringChildIntoView(localRect: () -> Rect?) { }
        }
    }
    val detailBringIntoView = remember(mediaKey) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                movingBetweenSections || heroFocused -> 0f
                containerSize <= 0f || size <= 0f -> 0f
                size >= containerSize -> 0f
                else -> offset - (containerSize * .42f - size / 2f)
                    .coerceIn(0f, containerSize - size)
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
    val hasPeopleTabs = hasCast || hasCompanies
    val hasSeasons = state.item.isDetailSeries() && state.seasons.isNotEmpty()
    val hasEpisodes = state.item.isDetailSeries() && state.episodes.isNotEmpty()

    val hasEpisodeMessage = state.item.isDetailSeries() && !state.loading && state.item.episodes.isEmpty()
    val peopleIndex = 1 + (if (hasSeasons) 1 else 0) + (if (hasEpisodes || hasEpisodeMessage) 1 else 0)
    val relatedIndex = peopleIndex + (if (hasPeopleTabs) 1 else 0)
    fun belowIndex(target: FocusRequester): Int = when (target) {
        seasonRequester -> 1
        episodeRequester -> if (hasSeasons) 2 else 1
        peopleContentRequester -> peopleIndex
        else -> relatedIndex
    }

    val firstBelowHero = when {
        hasSeasons -> seasonRequester
        hasEpisodes -> episodeRequester
        hasPeopleTabs -> peopleContentRequester
        hasRelated -> relatedContentRequester
        else -> null
    }
    val navigationScope = rememberCoroutineScope()
    fun revealAndFocus(index: Int, target: FocusRequester) {
        if (movingBetweenSections) return
        if (index != 0 && runCatching { target.requestFocus() }.getOrDefault(false)) return
        movingBetweenSections = true
        navigationScope.launch {
            try {
                val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                // Reveal the next lazy item before requesting its not-yet-attached focus node.
                val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (visibleItem != null) {
                    val destinationOffset = if (index == 0) 0 else
                        (viewport * .42f - visibleItem.size / 2f).toInt()
                    listState.animateScrollBy(
                        (visibleItem.offset - destinationOffset).toFloat(),
                        animationSpec = spring(dampingRatio = .95f, stiffness = 180f),
                    )
                } else {
                    listState.animateScrollToItem(index, if (index == 0) 0 else -viewport / 2)
                }
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
            if (event.type == KeyEventType.KeyDown) {
                val now = SystemClock.uptimeMillis()
                if (event.nativeKeyEvent.repeatCount > 0 && now - lastVerticalRepeat < 80L) {
                    return@onPreviewKeyEvent true
                }
                lastVerticalRepeat = now
                revealAndFocus(destination.first, destination.second)
            }
            true
        }

    val firstBelowSeasons = when {
        hasEpisodes -> episodeRequester
        hasPeopleTabs -> peopleContentRequester
        hasRelated -> relatedContentRequester
        else -> null
    }
    val firstBelowEpisodes = when {
        hasPeopleTabs -> peopleContentRequester
        hasRelated -> relatedContentRequester
        else -> null
    }
    val peopleUp = when {
        hasEpisodes -> episodeRequester
        hasSeasons -> seasonRequester
        else -> playRequester
    }
    val relatedUpRequester = when {
        hasPeopleTabs -> peopleContentRequester
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

    val entryEpisodeId = remember(mediaKey) {
        VueoDetailFocusMemory.episodeId.takeIf { VueoDetailFocusMemory.mediaKey == mediaKey }
    }
    var entryFocusRestored by remember(mediaKey) { mutableStateOf(false) }
    LaunchedEffect(mediaKey) {
        delay(110)
        runCatching { playRequester.requestFocus() }
    }

    LaunchedEffect(mediaKey, state.selectedEpisode?.id, hasEpisodes) {
        val rememberedEpisodeId = entryEpisodeId
        val restoreEpisode = state.item.isDetailSeries() &&
            VueoDetailFocusMemory.mediaKey == mediaKey &&
            rememberedEpisodeId != null &&
            rememberedEpisodeId == state.selectedEpisode?.id
        if (restoreEpisode && hasEpisodes && !entryFocusRestored) {
            entryFocusRestored = true
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
            contentPadding = PaddingValues(bottom = if (hasPeopleTabs && !hasRelated)
                maxOf(88.dp, maxHeight / 2 - 96.dp) else 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "vueo-hero:$mediaKey") {
                Box(Modifier.onFocusChanged { heroFocused = it.hasFocus }.focusGroup()
                    .then(sectionNavigation(null, firstBelowHero?.let { belowIndex(it) to it }))) {
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
                    Box(sectionNavigation(0 to playRequester, firstBelowSeasons?.let { belowIndex(it) to it })) {
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
                    Box(sectionNavigation(
                        if (hasSeasons) 1 to seasonRequester else 0 to playRequester,
                        firstBelowEpisodes?.let { belowIndex(it) to it },
                    ).bringIntoViewResponder(episodeRowResponder)) {
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

            if (hasPeopleTabs) {
                item(key = "vueo-people-tabs:$mediaKey") {
                    val upIndex = when {
                        hasEpisodes -> if (hasSeasons) 2 else 1
                        hasSeasons -> 1
                        else -> 0
                    }
                    VueoDetailCastCompanyTabs(
                        mediaKey = mediaKey,
                        cast = people,
                        production = state.item.productionCompanies,
                        networks = state.item.networks,
                        sectionRequester = peopleContentRequester,
                        upRequester = peopleUp,
                        downRequester = if (hasRelated) relatedContentRequester else null,
                        onCenterContent = { contentTop, contentHeight ->
                            val layout = listState.layoutInfo
                            val section = layout.visibleItemsInfo.firstOrNull { it.index == peopleIndex }
                            if (section != null) {
                                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                                val distance = section.offset + contentTop + contentHeight / 2f - viewportCenter
                                if (kotlin.math.abs(distance) > 1f) {
                                    listState.animateScrollBy(distance,
                                        animationSpec = spring(dampingRatio = .95f, stiffness = 180f))
                                }
                            }
                        },
                        onMoveUp = { revealAndFocus(upIndex, peopleUp) },
                        onMoveDown = if (hasRelated) ({ revealAndFocus(peopleIndex + 1, relatedContentRequester) }) else null,
                        onOpenCast = { person ->
                            onOpenEntity(MediaEntityTarget(kind = MediaEntityKind.ACTOR, name = person.name))
                        },
                        onOpenCompany = { company, isNetwork ->
                            onOpenEntity(MediaEntityTarget(
                                kind = if (isNetwork) MediaEntityKind.NETWORK else MediaEntityKind.COMPANY,
                                name = company.name,
                                tmdbId = company.tmdbId,
                            ))
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
