package com.vueo.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

@Composable
internal fun VueoPlayerCompactOverlay(
    panel: TvPlayerPanel,
    options: List<TvPlayerOption>,
    initialFocusKey: String?,
    onInteraction: () -> Unit,
    onFocused: (TvPlayerOption) -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val title = when (panel) {
        TvPlayerPanel.SUBTITLES -> "Subtitles"
        TvPlayerPanel.AUDIO -> "Audio"
        TvPlayerPanel.MORE -> "More"
        else -> "Options"
    }
    val subtitle = when (panel) {
        TvPlayerPanel.SUBTITLES -> "Choose subtitle track"
        TvPlayerPanel.AUDIO -> "Choose audio track"
        TvPlayerPanel.MORE -> "Playback and picture"
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f))) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(500.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .96f), Color.Black.copy(alpha = .78f), Color.Transparent))),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .width(420.dp)
                .padding(start = 44.dp, end = 22.dp, bottom = 54.dp),
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = .56f), fontSize = 11.sp)
            Spacer(Modifier.height(18.dp))
            VueoOptionList(
                options = options,
                maxHeightFraction = .58f,
                onInteraction = onInteraction,
                onSelected = onSelected,
                initialFocusKey = initialFocusKey,
                onFocused = onFocused,
            )
        }
    }
}

@Composable
internal fun VueoPlayerSourcesPanel(
    title: String,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val listEntryRequester = remember { FocusRequester() }
    val cardBackground = Color(0xFF17191C).copy(alpha = .92f)
    val cardBorder = Color.White.copy(alpha = .065f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .20f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = .60f),
                    .38f to Color.Black.copy(alpha = .30f),
                    .72f to Color.Black.copy(alpha = .15f),
                    1f to Color.Black.copy(alpha = .08f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .padding(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 48.dp),
        ) {
            Text(
                "Sources",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(PanelShape)
                    .background(cardBackground)
                    .border(1.dp, cardBorder, PanelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                VueoOptionList(
                    options = options,
                    maxHeightFraction = 1f,
                    onInteraction = onInteraction,
                    onSelected = onSelected,
                    topRequester = FocusRequester.Cancel,
                    entryFocusRequester = listEntryRequester,
                )
            }
        }
    }
}

@Composable
internal fun VueoOptionList(
    options: List<TvPlayerOption>,
    maxHeightFraction: Float,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
    topRequester: FocusRequester = FocusRequester.Cancel,
    entryFocusRequester: FocusRequester? = null,
    initialFocusKey: String? = null,
    onFocused: (TvPlayerOption) -> Unit = {},
) {
    val state = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var initialFocusAssigned by remember { mutableStateOf(false) }
    val initialIndex = remember(options, initialFocusKey) {
        options.indexOfFirst {
            it.key == initialFocusKey && it.enabled
        }.takeIf { it >= 0 }
            ?: options.indexOfFirst { it.selected && it.enabled }.takeIf { it >= 0 }
            ?: options.indexOfFirst { it.enabled }.takeIf { it >= 0 }
            ?: 0
    }
    val firstEnabledIndex = options.indexOfFirst { it.enabled }
    val lastEnabledIndex = options.indexOfLast { it.enabled }

    fun requesterFor(index: Int): FocusRequester {
        if (entryFocusRequester != null && index == firstEnabledIndex) {
            return entryFocusRequester
        }
        val option = options.getOrNull(index) ?: return FocusRequester.Cancel
        return requesters.getOrPut(option.key) { FocusRequester() }
    }

    LaunchedEffect(options, initialFocusKey, initialFocusAssigned) {
        if (initialFocusAssigned || options.isEmpty()) return@LaunchedEffect
        state.scrollToItem(initialIndex)
        if (requesterFor(initialIndex).requestTvFocus()) {
            initialFocusAssigned = true
        }
    }
    if (options.isEmpty()) {
        Text("Nothing available for this stream.", color = Color.White.copy(alpha = .52f), fontSize = 12.sp)
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxHeight(maxHeightFraction),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        itemsIndexed(options, key = { _, option -> option.key }) { index, option ->
            VueoOptionRow(
                option = option,
                requester = requesterFor(index),
                topRequester = topRequester,
                blockUp = index == firstEnabledIndex,
                blockDown = index == lastEnabledIndex,
                onInteraction = onInteraction,
                onFocused = { onFocused(option) },
            ) {
                onSelected(option)
            }
        }
    }
}

@Composable
private fun VueoOptionRow(
    option: TvPlayerOption,
    requester: FocusRequester,
    topRequester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    onInteraction: () -> Unit,
    onFocused: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(option.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = topRequester
                if (blockDown) down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && option.enabled) onSelected()
                true
            }
            .focusable(option.enabled)
            .clickable(enabled = option.enabled, onClick = onSelected)
            .background(
                when {
                    focused -> Color.White.copy(alpha = .13f)
                    option.selected -> Color.White.copy(alpha = .06f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    option.selected -> TvDesign.Accent.copy(alpha = .58f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(
                    if (option.selected) TvDesign.Accent else Color.Transparent,
                    RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                option.title,
                color = if (option.enabled) Color.White else Color.White.copy(alpha = .30f),
                fontSize = 12.sp,
                fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.meta?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.selected) {
            Text("Active", color = TvDesign.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun VueoPlayerEpisodesPanel(
    mediaTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
) {
    val seasons = remember(episodes) {
        val normal = episodes.map { it.season }.distinct().filter { it > 0 }.sorted()
        normal + episodes.map { it.season }.distinct().filter { it == 0 }
    }
    var selectedSeason by remember(episodes, currentEpisode?.season) {
        mutableIntStateOf(
            currentEpisode?.season?.takeIf { it in seasons }
                ?: seasons.firstOrNull()
                ?: 1
        )
    }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.season == selectedSeason }.sortedBy { it.episode }
    }
    val episodeEntryRequester = remember { FocusRequester() }
    val seasonRequesters = remember(seasons) {
        List(seasons.size.coerceAtLeast(1)) { FocusRequester() }
    }
    val selectedSeasonIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0)
    val seasonListState = rememberLazyListState()
    var lastFocusedSeasonIndex by remember(seasons) {
        mutableIntStateOf(selectedSeasonIndex)
    }
    val seasonReturnRequester = seasonRequesters.getOrNull(lastFocusedSeasonIndex)
        ?: seasonRequesters.getOrNull(selectedSeasonIndex)
        ?: FocusRequester.Cancel

    LaunchedEffect(selectedSeasonIndex, seasons) {
        if (seasons.isNotEmpty()) {
            seasonListState.scrollToItem(selectedSeasonIndex)
        }
    }
    val listTopRequester = if (seasons.size > 1) seasonReturnRequester else FocusRequester.Cancel
    val cardBackground = Color(0xFF17191C).copy(alpha = .92f)
    val cardBorder = Color.White.copy(alpha = .065f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .20f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = .60f),
                    .38f to Color.Black.copy(alpha = .30f),
                    .72f to Color.Black.copy(alpha = .15f),
                    1f to Color.Black.copy(alpha = .08f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .padding(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 48.dp),
        ) {
            Text(
                "Episodes",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                mediaTitle,
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(PanelShape)
                    .background(cardBackground)
                    .border(1.dp, cardBorder, PanelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (seasons.size > 1) {
                    LazyRow(
                        state = seasonListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                            VueoSeasonChip(
                                season = season,
                                selected = season == selectedSeason,
                                requester = seasonRequesters[index],
                                upRequester = FocusRequester.Cancel,
                                downRequester = episodeEntryRequester,
                                blockLeft = index == 0,
                                blockRight = index == seasons.lastIndex,
                                onInteraction = onInteraction,
                                onFocused = { lastFocusedSeasonIndex = index },
                            ) {
                                selectedSeason = season
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    VueoEpisodeList(
                        episodes = seasonEpisodes,
                        currentEpisode = currentEpisode,
                        entryFocusRequester = episodeEntryRequester,
                        topRequester = listTopRequester,
                        onInteraction = onInteraction,
                        onSelected = onSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoSeasonChip(
    season: Int,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    blockLeft: Boolean,
    blockRight: Boolean,
    onInteraction: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(season) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
                if (blockLeft) left = FocusRequester.Cancel
                if (blockRight) right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = .13f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = .12f),
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            if (season == 0) "Specials" else "Season $season",
            color = if (focused) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VueoEpisodeList(
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    entryFocusRequester: FocusRequester,
    topRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
) {
    val state = rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { episode ->
        currentEpisode?.let {
            it.id == episode.id ||
                (it.season == episode.season && it.episode == episode.episode)
        } == true
    }.coerceAtLeast(0)
    val requesters = remember(episodes.map { it.id }, currentIndex, entryFocusRequester) {
        List(episodes.size.coerceAtLeast(1)) { index ->
            if (index == currentIndex) entryFocusRequester else FocusRequester()
        }
    }
    var initialFocusAssigned by remember { mutableStateOf(false) }

    LaunchedEffect(initialFocusAssigned, episodes, currentEpisode?.id) {
        if (initialFocusAssigned || episodes.isEmpty()) return@LaunchedEffect
        state.scrollToItem(currentIndex)
        if (requesters[currentIndex].requestTvFocus()) {
            initialFocusAssigned = true
        }
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            val selected = currentEpisode?.let {
                it.id == episode.id ||
                    (it.season == episode.season && it.episode == episode.episode)
            } == true
            VueoEpisodeRow(
                episode = episode,
                selected = selected,
                requester = requesters[index],
                topRequester = topRequester,
                blockUp = index == 0,
                blockDown = index == episodes.lastIndex,
                onInteraction = onInteraction,
            ) {
                onSelected(episode)
            }
        }
    }
}

@Composable
private fun VueoEpisodeRow(
    episode: EpisodeItem,
    selected: Boolean,
    requester: FocusRequester,
    topRequester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    onInteraction: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = topRequester
                if (blockDown) down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onSelected()
                true
            }
            .focusable()
            .clickable(onClick = onSelected)
            .background(if (focused) Color.White.copy(alpha = .11f) else Color.Transparent, shape)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    selected -> TvDesign.Accent.copy(alpha = .52f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(130.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TvDesign.SurfaceRaised)
        ) {
            TvNetworkImage(
                episode.thumbnail,
                episode.title,
                Modifier.fillMaxSize(),
                ContentScale.Crop,
                TvDesign.SurfaceRaised,
            )
            Text(
                "S${episode.season}E${episode.episode}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(9.dp)
                        .background(TvDesign.Accent, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                episode.title.ifBlank { "Episode ${episode.episode}" },
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            vueoPlayerFormatReleaseDate(episode.released)?.let {
                Text(it, color = Color.White.copy(alpha = .42f), fontSize = 9.sp)
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VueoPanelTextAction(
    label: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Text(
        label,
        color = if (focused) Color.Black else Color.White.copy(alpha = .74f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = downRequester
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = .07f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}


