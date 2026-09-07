package com.vueo.tv.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay

private val Detail39EpisodeWidth = 360.dp
private val Detail39EpisodeHeight = 235.dp
private val Detail39EpisodeShape = RoundedCornerShape(12.dp)
private val Detail39SeasonShape = RoundedCornerShape(20.dp)

/** Nuvio SeasonTabs: no section heading, focus changes season after a small settle delay. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail39SeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    selectedRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    val sortedSeasons = remember(seasons) {
        seasons.filter { it > 0 }.sorted() + seasons.filter { it == 0 }
    }
    val selectedIndex = sortedSeasons.indexOf(selectedSeason).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val focusSeason = selectedSeason?.takeIf { it in sortedSeasons } ?: sortedSeasons.firstOrNull()
    val requesters = remember(sortedSeasons, focusSeason) {
        sortedSeasons.associateWith { season ->
            if (season == focusSeason) selectedRequester else FocusRequester()
        }
    }
    var pendingSeason by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(150)
        onSelect(target)
        pendingSeason = null
    }

    LaunchedEffect(sortedSeasons, selectedSeason) {
        val index = sortedSeasons.indexOf(selectedSeason)
        if (index >= 0 && index !in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
            listState.scrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer { requesters[focusSeason] ?: FocusRequester.Default }
            .focusGroup(),
        contentPadding = PaddingValues(
            horizontal = Detail39HorizontalPadding,
            vertical = 20.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(sortedSeasons, key = { _, season -> season }) { _, season ->
            val selected = season == selectedSeason
            var focused by remember(season) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .focusRequester(requesters.getValue(season))
                    .focusProperties {
                        up = upRequester
                        downRequester?.let { down = it }
                    }
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (state.isFocused && !selected) pendingSeason = season
                    }
                    .clip(Detail39SeasonShape)
                    .background(
                        when {
                            focused -> TvDesign.White
                            selected -> TvDesign.White.copy(alpha = .16f)
                            else -> TvDesign.Surface.copy(alpha = .82f)
                        }
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .08f),
                        shape = Detail39SeasonShape,
                    )
                    .clickable { onSelect(season) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (season == 0) "Specials" else "Season $season",
                    color = if (focused) Color.Black else if (selected) TvDesign.White else TvDesign.White.copy(alpha = .62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Nuvio EpisodesRow: full-bleed 360x235 cards, no extra "Episodes" heading. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail39EpisodeRow(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    selectedEpisode: EpisodeItem?,
    history: List<LibraryPlaybackEntry>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: (EpisodeItem) -> Unit,
    onOpen: (EpisodeItem) -> Unit,
) {
    val rememberedId = TvDetailSessionMemory.episodeId
        ?.takeIf { id -> episodes.any { it.id == id } }
    val focusEpisodeId = rememberedId ?: selectedEpisode?.id ?: episodes.firstOrNull()?.id
    val selectedIndex = episodes.indexOfFirst { it.id == focusEpisodeId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val requesters = remember(episodes.map(EpisodeItem::id), focusEpisodeId) {
        episodes.associate { episode ->
            episode.id to if (episode.id == focusEpisodeId) rowRequester else FocusRequester()
        }
    }

    LaunchedEffect(episodes, rememberedId) {
        if (rememberedId == null) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { requesters[rememberedId]?.requestFocus() }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer { requesters[focusEpisodeId] ?: FocusRequester.Default }
            .focusGroup(),
        contentPadding = PaddingValues(
            horizontal = Detail39HorizontalPadding,
            vertical = 10.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = episodes,
            key = { _, episode -> episode.id },
        ) { _, episode ->
            val progress = history.firstOrNull { entry ->
                entry.media.id == media.id &&
                    entry.media.type == media.type &&
                    entry.season == episode.season &&
                    entry.episode == episode.episode
            }
            Detail39EpisodeCard(
                episode = episode,
                progress = progress,
                requester = requesters.getValue(episode.id),
                upRequester = upRequester,
                downRequester = downRequester,
                onFocused = { onFocused(episode) },
                onOpen = { onOpen(episode) },
            )
        }
    }
}

@Composable
private fun Detail39EpisodeCard(
    episode: EpisodeItem,
    progress: LibraryPlaybackEntry?,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(Detail39EpisodeWidth)
            .height(Detail39EpisodeHeight)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (nowFocused && !focused) onFocused()
                focused = nowFocused
            }
            .clip(Detail39EpisodeShape)
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.Focus else Color.Transparent,
                shape = Detail39EpisodeShape,
            )
            .clickable(onClick = onOpen),
    ) {
        TvNetworkImage(
            url = episode.thumbnail,
            contentDescription = episode.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.SurfaceRaised,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.20f to Color.Black.copy(alpha = .04f),
                        0.38f to Color.Black.copy(alpha = .26f),
                        0.54f to Color.Black.copy(alpha = .56f),
                        0.70f to Color.Black.copy(alpha = .76f),
                        0.86f to Color.Black.copy(alpha = .88f),
                        1.00f to Color.Black.copy(alpha = .95f),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .44f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "EPISODE ${episode.episode}",
                    color = TvDesign.White.copy(alpha = .92f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .6.sp,
                )
            }

            Text(
                text = episode.title.ifBlank { "Episode ${episode.episode}" },
                color = TvDesign.White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            episode.overview?.takeIf(String::isNotBlank)?.let { overview ->
                Text(
                    text = overview,
                    color = TvDesign.White.copy(alpha = .82f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S${episode.season} · E${episode.episode}",
                    color = TvDesign.White.copy(alpha = .58f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                episode.released?.takeIf(String::isNotBlank)?.let { released ->
                    Text(
                        text = released,
                        color = TvDesign.White.copy(alpha = .50f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        progress?.takeIf { it.durationMs > 0L && it.positionMs > 5_000L }?.let { entry ->
            LinearProgressIndicator(
                progress = { entry.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = TvDesign.White,
                trackColor = TvDesign.White.copy(alpha = .18f),
            )
        }
    }
}
