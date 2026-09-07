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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val NuvioEpisodeWidth = 360.dp
private val NuvioEpisodeHeight = 235.dp
private val NuvioEpisodeShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun NuvioDetailSeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    val selectedIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val focusSeason = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val requesters = remember(seasons, focusSeason) {
        seasons.associateWith { season ->
            if (season == focusSeason) sectionRequester else FocusRequester()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { requesters[focusSeason] ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = NuvioDetailHorizontalPadding, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(seasons, key = { _, season -> season }) { _, season ->
                var focused by remember(season) { mutableStateOf(false) }
                val selected = season == selectedSeason
                val shape = RoundedCornerShape(24.dp)
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
                                selected -> TvDesign.White.copy(alpha = .16f)
                                else -> TvDesign.Surface.copy(alpha = .82f)
                            }
                        )
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .10f),
                            shape = shape,
                        )
                        .clickable { onSelect(season) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
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
internal fun NuvioDetailEpisodes(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    selectedEpisode: EpisodeItem?,
    history: List<LibraryPlaybackEntry>,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: (EpisodeItem) -> Unit,
    onOpen: (EpisodeItem) -> Unit,
) {
    val rememberedId = NuvioDetailFocusMemory.episodeId
        ?.takeIf { id -> episodes.any { it.id == id } }
    val focusId = rememberedId ?: selectedEpisode?.id ?: episodes.firstOrNull()?.id
    val selectedIndex = episodes.indexOfFirst { it.id == focusId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val requesters = remember(episodes.map(EpisodeItem::id), focusId) {
        episodes.associate { episode ->
            episode.id to if (episode.id == focusId) sectionRequester else FocusRequester()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NuvioDetailSectionTitle("Episodes")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { requesters[focusId] ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = NuvioDetailHorizontalPadding, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.id }) { _, episode ->
                val progress = history.firstOrNull { entry ->
                    entry.media.id == media.id &&
                        entry.media.type == media.type &&
                        entry.season == episode.season &&
                        entry.episode == episode.episode
                }
                NuvioEpisodeCard(
                    episode = episode,
                    progress = progress,
                    selected = selectedEpisode?.id == episode.id,
                    requester = requesters.getValue(episode.id),
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        NuvioDetailFocusMemory.selectedSeason = episode.season
                        NuvioDetailFocusMemory.episodeId = episode.id
                        onFocused(episode)
                    },
                    onOpen = { onOpen(episode) },
                )
            }
        }
    }
}

@Composable
private fun NuvioEpisodeCard(
    episode: EpisodeItem,
    progress: LibraryPlaybackEntry?,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(NuvioEpisodeWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(NuvioEpisodeWidth)
                .height(NuvioEpisodeHeight)
                .focusRequester(requester)
                .focusProperties {
                    up = upRequester
                    downRequester?.let { down = it }
                }
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) onFocused()
                    focused = state.isFocused
                }
                .clip(NuvioEpisodeShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                    color = when {
                        focused -> TvDesign.Focus
                        selected -> TvDesign.White.copy(alpha = .30f)
                        else -> Color.Transparent
                    },
                    shape = NuvioEpisodeShape,
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
                            .52f to Color.Transparent,
                            1f to TvDesign.Black.copy(alpha = .93f),
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
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
                    fontWeight = FontWeight.SemiBold,
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

        nuvioDetailFormatReleaseDate(episode.released)?.let { released ->
            Text(
                text = released,
                color = TvDesign.White.copy(alpha = .44f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


private fun nuvioDetailFormatReleaseDate(raw: String?): String? {
    val input = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(input)
        }.getOrNull() ?: continue
        return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(parsed)
    }
    return input.substringBefore('T').takeIf { it != input } ?: input
}
