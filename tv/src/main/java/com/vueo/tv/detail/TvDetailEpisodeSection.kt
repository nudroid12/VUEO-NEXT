package com.vueo.tv.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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

private val Detail38EpisodeWidth = 360.dp
private val Detail38EpisodeHeight = 235.dp
private val Detail38EpisodeShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail38SeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    val selectedIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val focusSeason = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val requesters = remember(seasons, focusSeason) {
        seasons.associateWith { season ->
            if (season == focusSeason) rowRequester else FocusRequester()
        }
    }
    val selectedRequester = requesters[focusSeason]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetail38SectionTitle("Seasons")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { selectedRequester ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Detail38HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(seasons, key = { _, season -> season }) { _, season ->
                var focused by remember(season) { mutableStateOf(false) }
                val selected = season == selectedSeason
                val shape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .focusRequester(requesters.getValue(season))
                        .focusProperties {
                            up = upRequester
                            downRequester?.let { down = it }
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .clip(shape)
                        .background(
                            when {
                                focused -> TvDesign.White
                                selected -> TvDesign.White.copy(alpha = .17f)
                                else -> TvDesign.Surface.copy(alpha = .88f)
                            }
                        )
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .10f),
                            shape = shape,
                        )
                        .clickable { onSelect(season) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (season == 0) "Specials" else "Season $season",
                        color = if (focused) Color.Black else TvDesign.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail38EpisodeRail(
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
    val selectedIndex = episodes.indexOfFirst { it.id == (rememberedId ?: selectedEpisode?.id) }
        .coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val focusEpisodeId = rememberedId ?: selectedEpisode?.id ?: episodes.firstOrNull()?.id
    val requesters = remember(episodes.map(EpisodeItem::id), focusEpisodeId) {
        episodes.associate { episode ->
            episode.id to if (episode.id == focusEpisodeId) rowRequester else FocusRequester()
        }
    }
    val restoreRequester = requesters[focusEpisodeId]

    LaunchedEffect(episodes, rememberedId) {
        if (rememberedId == null) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { requesters[rememberedId]?.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetail38SectionTitle("Episodes")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { restoreRequester ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Detail38HorizontalPadding, vertical = 8.dp),
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
                TvDetail38EpisodeCard(
                    episode = episode,
                    selected = selectedEpisode?.id == episode.id,
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
}

@Composable
private fun TvDetail38EpisodeCard(
    episode: EpisodeItem,
    selected: Boolean,
    progress: LibraryPlaybackEntry?,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(Detail38EpisodeWidth),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .width(Detail38EpisodeWidth)
                .height(Detail38EpisodeHeight)
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
                .clip(Detail38EpisodeShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                    color = when {
                        focused -> TvDesign.Focus
                        selected -> TvDesign.White.copy(alpha = .34f)
                        else -> Color.Transparent
                    },
                    shape = Detail38EpisodeShape,
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
                            0f to Color.Transparent,
                            .54f to Color.Transparent,
                            1f to TvDesign.Black.copy(alpha = .92f),
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "S${episode.season} E${episode.episode}",
                    color = TvDesign.White.copy(alpha = .68f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = episode.title.ifBlank { "Episode ${episode.episode}" },
                    color = TvDesign.White,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            progress?.takeIf { it.durationMs > 0L && it.positionMs > 5_000L }?.let { entry ->
                LinearProgressIndicator(
                    progress = { entry.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TvDesign.White,
                    trackColor = TvDesign.White.copy(alpha = .20f),
                )
            }
        }

        episode.released?.takeIf(String::isNotBlank)?.let { released ->
            Text(
                text = released,
                color = TvDesign.White.copy(alpha = .40f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
