package com.vueo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay

@Composable
internal fun NuvioPlayerLeftOptionsOverlay(
    panel: TvPlayerPanel,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val (title, subtitle) = when (panel) {
        TvPlayerPanel.SUBTITLES -> "Subtitles" to "Choose subtitle track"
        TvPlayerPanel.AUDIO -> "Audio" to "Choose audio track"
        TvPlayerPanel.MORE -> "More" to "Playback and picture options"
        else -> "Options" to ""
    }

    NuvioPlayerOverlayScaffold(
        leftWeighted = true,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(510.dp)
                .padding(start = 48.dp, end = 34.dp, top = 46.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
            NuvioPlayerOptionList(
                options = options,
                maxHeightFraction = .67f,
                onInteraction = onInteraction,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
internal fun NuvioPlayerSourcesPanel(
    title: String,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    NuvioPlayerOverlayScaffold(
        leftWeighted = false,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .98f))
                .padding(horizontal = 28.dp, vertical = 34.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sources",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                NuvioPanelTextAction("Close", onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = .62f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            NuvioPlayerOptionList(
                options = options,
                maxHeightFraction = .90f,
                onInteraction = onInteraction,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
internal fun NuvioPlayerEpisodesPanel(
    mediaTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
) {
    val seasons = remember(episodes) {
        episodes.map { it.season }.distinct().sortedWith(compareBy<Int> { if (it == 0) 1 else 0 }.thenBy { it })
    }
    var selectedSeason by remember(episodes, currentEpisode?.season) {
        mutableIntStateOf(currentEpisode?.season?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1)
    }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.season == selectedSeason }.sortedBy { it.episode }
    }

    NuvioPlayerOverlayScaffold(
        leftWeighted = false,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(570.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .985f))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Episodes",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mediaTitle,
                        color = Color.White.copy(alpha = .58f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NuvioPanelTextAction("Close", onDismiss)
            }

            if (seasons.size > 1) {
                Spacer(Modifier.height(18.dp))
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(seasons, key = { _, season -> season }) { _, season ->
                        NuvioSeasonChip(
                            season = season,
                            selected = season == selectedSeason,
                            onClick = { selectedSeason = season },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            NuvioEpisodeList(
                episodes = seasonEpisodes,
                currentEpisode = currentEpisode,
                onInteraction = onInteraction,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun NuvioPlayerOverlayScaffold(
    leftWeighted: Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .28f)),
    ) {
        if (leftWeighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(640.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = .97f),
                                Color.Black.copy(alpha = .84f),
                                Color.Transparent,
                            )
                        )
                    ),
            )
        }
        content(this)
    }
}

@Composable
private fun NuvioPlayerOptionList(
    options: List<TvPlayerOption>,
    maxHeightFraction: Float,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val listState = rememberLazyListState()
    val requesters = remember(options.map { it.key }) {
        List(options.size.coerceAtLeast(1)) { FocusRequester() }
    }

    LaunchedEffect(options) {
        if (options.isEmpty()) return@LaunchedEffect
        val index = options.indexOfFirst { it.selected && it.enabled }
            .takeIf { it >= 0 }
            ?: options.indexOfFirst { it.enabled }.takeIf { it >= 0 }
            ?: 0
        listState.scrollToItem(index)
        delay(45)
        runCatching { requesters[index].requestFocus() }
    }

    if (options.isEmpty()) {
        Text(
            text = "Nothing available for this stream.",
            color = Color.White.copy(alpha = .58f),
            fontSize = 13.sp,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxHeight(maxHeightFraction),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options, key = { index, option -> "${option.key}:$index" }) { index, option ->
            NuvioPlayerOptionRow(
                option = option,
                requester = requesters[index],
                onInteraction = onInteraction,
                onSelected = { onSelected(option) },
            )
        }
    }
}

@Composable
private fun NuvioPlayerOptionRow(
    option: TvPlayerOption,
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(option.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .focusable(option.enabled)
            .clickable(enabled = option.enabled, onClick = onSelected)
            .background(
                when {
                    focused -> Color.White.copy(alpha = .14f)
                    option.selected -> Color.White.copy(alpha = .07f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = .92f)
                    option.selected -> TvDesign.Accent.copy(alpha = .60f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(
                    if (option.selected) TvDesign.Accent else Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                color = if (option.enabled) Color.White else Color.White.copy(alpha = .34f),
                fontSize = 13.sp,
                fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    color = Color.White.copy(alpha = if (option.enabled) .52f else .26f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.selected) {
            Text(
                text = "Active",
                color = TvDesign.Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NuvioSeasonChip(
    season: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(season) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = .14f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = .12f),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (season == 0) "Specials" else "Season $season",
            color = if (focused) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NuvioEpisodeList(
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    onInteraction: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { candidate ->
        currentEpisode?.let { current ->
            current.id == candidate.id || (current.season == candidate.season && current.episode == candidate.episode)
        } == true
    }.coerceAtLeast(0)
    val requesters = remember(episodes.map { it.id }) {
        List(episodes.size.coerceAtLeast(1)) { FocusRequester() }
    }

    LaunchedEffect(episodes, currentEpisode?.id) {
        if (episodes.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
        delay(45)
        runCatching { requesters[currentIndex].requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            val selected = currentEpisode?.let { current ->
                current.id == episode.id || (current.season == episode.season && current.episode == episode.episode)
            } == true
            NuvioEpisodePanelRow(
                episode = episode,
                selected = selected,
                requester = requesters[index],
                onInteraction = onInteraction,
                onSelected = { onSelected(episode) },
            )
        }
    }
}

@Composable
private fun NuvioEpisodePanelRow(
    episode: EpisodeItem,
    selected: Boolean,
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .focusable()
            .clickable(onClick = onSelected)
            .background(if (focused) Color.White.copy(alpha = .12f) else Color.Transparent, shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = .92f)
                    selected -> TvDesign.Accent.copy(alpha = .55f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(TvDesign.SurfaceRaised),
        ) {
            TvNetworkImage(
                url = episode.thumbnail,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.SurfaceRaised,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .background(TvDesign.Accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "S${episode.season}E${episode.episode}",
                color = Color.White.copy(alpha = .52f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = episode.title.ifBlank { "Episode ${episode.episode}" },
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.released?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = Color.White.copy(alpha = .42f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NuvioPanelTextAction(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = label,
        color = if (focused) Color.Black else Color.White.copy(alpha = .78f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = .08f), shape)
            .border(1.dp, Color.White.copy(alpha = .10f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
