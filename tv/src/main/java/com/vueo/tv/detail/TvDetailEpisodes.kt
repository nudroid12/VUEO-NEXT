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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.zIndex
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay

private val DetailEpisodeWidth = 360.dp
private val DetailEpisodeHeight = 235.dp
private val DetailEpisodeShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailSeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    selectedRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    val sortedSeasons = remember(seasons) {
        val regular = seasons.filter { it > 0 }.sorted()
        val specials = seasons.filter { it == 0 }
        regular + specials
    }
    val selectedIndex = sortedSeasons.indexOf(selectedSeason).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    var pendingSeason by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(140)
        onSelect(target)
        pendingSeason = null
    }

    LaunchedEffect(sortedSeasons, selectedSeason) {
        val index = sortedSeasons.indexOf(selectedSeason)
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer { selectedRequester }
            .focusGroup(),
        contentPadding = PaddingValues(
            horizontal = DetailHorizontalPadding,
            vertical = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sortedSeasons, key = { it }) { season ->
            val selected = season == selectedSeason
            var focused by remember(season) { mutableStateOf(false) }
            val shape = RoundedCornerShape(20.dp)

            Box(
                modifier = Modifier
                    .then(if (selected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .focusProperties {
                        up = upRequester
                        downRequester?.let { down = it }
                    }
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (state.isFocused && !selected) pendingSeason = season
                    }
                    .clip(shape)
                    .background(
                        when {
                            focused -> TvDesign.White
                            selected -> TvDesign.SurfaceRaised
                            else -> TvDesign.Surface.copy(alpha = .90f)
                        }
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .08f),
                        shape = shape,
                    )
                    .clickable {
                        pendingSeason = null
                        onSelect(season)
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (season == 0) "Specials" else "Season $season",
                    color = if (focused) Color.Black else TvDesign.White,
                    fontSize = 14.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailEpisodesRow(
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
    val restoreId = TvDetailFocusMemory.episodeId
        ?.takeIf { id -> episodes.any { it.id == id } }
    val restoreIndex = episodes.indexOfFirst { it.id == restoreId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = restoreIndex)
    val itemRequesters = remember(media.id, episodes.map(EpisodeItem::id)) {
        mutableMapOf<String, FocusRequester>()
    }
    val fallbackRequester = remember(media.id, episodes.firstOrNull()?.id) { FocusRequester() }

    LaunchedEffect(restoreId, episodes) {
        val id = restoreId ?: return@LaunchedEffect
        val index = episodes.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        delay(110)
        runCatching { itemRequesters[id]?.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Episodes",
            color = TvDesign.White.copy(alpha = .94f),
            fontSize = 19.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = DetailHorizontalPadding),
        )

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowRequester)
                .focusRestorer {
                    restoreId?.let { itemRequesters[it] }
                        ?: selectedEpisode?.id?.let { itemRequesters[it] }
                        ?: episodes.firstOrNull()?.id?.let { itemRequesters[it] }
                        ?: fallbackRequester
                }
                .focusGroup(),
            contentPadding = PaddingValues(
                horizontal = DetailHorizontalPadding,
                vertical = 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = episodes,
                key = { _, episode -> episode.id },
            ) { _, episode ->
                val requester = itemRequesters.getOrPut(episode.id) { FocusRequester() }
                val playback = remember(media.id, episode.id, history) {
                    detailPlaybackEntry(media, episode, history)
                }
                TvDetailEpisodeCard(
                    episode = episode,
                    playbackEntry = playback,
                    selected = episode.id == selectedEpisode?.id,
                    requester = requester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = { onFocused(episode) },
                    onOpen = { onOpen(episode) },
                )
            }
        }
    }
}

@Composable
private fun TvDetailEpisodeCard(
    episode: EpisodeItem,
    playbackEntry: LibraryPlaybackEntry?,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val progress = playbackEntry?.progressFraction?.coerceIn(0f, 1f) ?: 0f
    val showProgress = playbackEntry != null && playbackEntry.positionMs > 0L && !playbackEntry.isCompleted

    Box(
        modifier = Modifier
            .width(DetailEpisodeWidth)
            .height(DetailEpisodeHeight)
            .zIndex(if (focused) 1f else 0f)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { state ->
                val becameFocused = state.isFocused
                if (becameFocused && !focused) onFocused()
                focused = becameFocused
            }
            .clip(DetailEpisodeShape)
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                color = when {
                    focused -> TvDesign.White
                    selected -> TvDesign.White.copy(alpha = .24f)
                    else -> Color.Transparent
                },
                shape = DetailEpisodeShape,
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
                        .20f to Color.Black.copy(alpha = .04f),
                        .38f to Color.Black.copy(alpha = .26f),
                        .54f to Color.Black.copy(alpha = .56f),
                        .70f to Color.Black.copy(alpha = .76f),
                        .86f to Color.Black.copy(alpha = .88f),
                        1.00f to Color.Black.copy(alpha = .96f),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)
                .align(androidx.compose.ui.Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = .42f),
                        RoundedCornerShape(7.dp),
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "EP ${episode.episode}",
                    color = TvDesign.White.copy(alpha = .90f),
                    fontSize = 10.sp,
                    letterSpacing = .8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            Text(
                text = episode.title.ifBlank { "Episode ${episode.episode}" },
                color = TvDesign.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            episode.overview?.takeIf(String::isNotBlank)?.let { overview ->
                Text(
                    text = overview,
                    color = TvDesign.White.copy(alpha = .82f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            episode.released?.takeIf(String::isNotBlank)?.let { released ->
                Text(
                    text = released,
                    color = TvDesign.White.copy(alpha = .52f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (playbackEntry?.isCompleted == true) {
            Text(
                text = "WATCHED",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(12.dp)
                    .background(TvDesign.White, RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }

        if (showProgress) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
                color = TvDesign.Accent,
                trackColor = Color.Transparent,
            )
        }
    }
}
