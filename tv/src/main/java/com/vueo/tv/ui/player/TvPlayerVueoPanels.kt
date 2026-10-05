package com.vueo.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import com.vueo.tv.ui.rememberTvEpisodeRanges
import com.vueo.tv.ui.TvEpisodeRangeControls
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VueoPlayerSourcesPanel(
    title: String,
    options: List<TvPlayerOption>,
    searching: Boolean,
    pluginsStopped: Boolean,
    onRefresh: () -> Unit,
    sourcesStopped: Boolean,
    onStop: () -> Unit,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
    panelModifier: Modifier = Modifier,
) {
    val listEntryRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var initialSourceFocus by remember { mutableStateOf(true) }
    var enterListRequest by remember { mutableIntStateOf(0) }
    val providers = options.mapNotNull { it.providerName }.distinct()
    val tabRequesters = remember { mutableMapOf<String?, FocusRequester>() }
    fun tabRequester(provider: String?) = tabRequesters.getOrPut(provider) { FocusRequester() }
    val filteredOptions = options.filter { selectedProvider == null || it.providerName == selectedProvider }
    LaunchedEffect(providers) {
        if (selectedProvider != null && selectedProvider !in providers) selectedProvider = null
    }
    LaunchedEffect(enterListRequest) {
        if (enterListRequest > 0 && filteredOptions.any { it.enabled }) {
            withFrameNanos { }
            listEntryRequester.requestTvFocus()
        }
    }


    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .then(panelModifier)
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xF5202124))
                .padding(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sources", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), color = TvDesign.Accent, strokeWidth = 2.dp)
                }
                VueoPlayerTopAction(
                    icon = if (searching) Icons.Default.Stop else Icons.Default.Refresh,
                    label = if (searching) "Stop source scan" else "Refresh sources",
                    requester = refreshRequester,
                    downRequester = tabRequester(selectedProvider),
                    leftRequester = tabRequester((listOf<String?>(null) + providers).last()),
                    rightRequester = FocusRequester.Cancel,
                    onInteraction = onInteraction,
                    onClick = { if (searching) onStop() else onRefresh() },
                )
            }
            if (searching || pluginsStopped || sourcesStopped) {
                Text(if (sourcesStopped) "Source scan stopped" else if (searching && pluginsStopped) "Addons loading • plugins stopped"
                    else if (searching) "Discovering sources…" else "Plugin scan stopped",
                    color = Color.White.copy(alpha = .6f), fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            VueoLeadingTabs {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf<String?>(null) + providers).forEach { provider ->
                    key(provider) {
                        var focused by remember { mutableStateOf(false) }
                        val selected = provider == selectedProvider
                        val shape = RoundedCornerShape(50)
                        Text(
                            text = provider?.substringAfterLast(" / ") ?: "All",
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.focusRequester(tabRequester(provider))
                                .focusProperties {
                                    up = refreshRequester
                                    if (provider == providers.lastOrNull()) right = refreshRequester
                                }
                                .onFocusChanged {
                                    focused = it.isFocused
                                    if (it.isFocused) {
                                        initialSourceFocus = false
                                        selectedProvider = provider
                                        onInteraction()
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                        if (event.type == KeyEventType.KeyDown) {
                                            onInteraction()
                                            enterListRequest++
                                        }
                                        true
                                    } else false
                                }
                                .clip(shape)
                                .background(if (selected) Color.White else if (focused) Color(0xFF555555) else Color(0xFF303030))
                                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .22f), shape)
                                .clickable {
                                    initialSourceFocus = false
                                    selectedProvider = provider
                                    onInteraction()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            }
            Spacer(Modifier.height(12.dp))


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                key(selectedProvider) {
                    VueoOptionList(
                        options = filteredOptions,
                        maxHeightFraction = 1f,
                        onInteraction = onInteraction,
                        onSelected = onSelected,
                        topRequester = tabRequester(selectedProvider),
                        entryFocusRequester = listEntryRequester,
                        assignInitialFocus = initialSourceFocus,
                    )
                }
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
    assignInitialFocus: Boolean = true,
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
        if (!assignInitialFocus || initialFocusAssigned || options.isEmpty()) return@LaunchedEffect
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
                    option.selected -> Color.White
                    focused -> Color(0xFF555555)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color(0xFF888888)
                    option.selected -> Color.White
                    else -> Color.White.copy(alpha = .07f)
                },
                shape,
            )
            .padding(horizontal = 13.dp, vertical = if (option.providerName != null) 7.dp else 10.dp),
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
        if (option.providerName != null) {
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        option.title,
                        color = if (option.selected) Color.Black else if (option.enabled) Color.White else Color.White.copy(alpha = .30f),
                        fontSize = 12.sp,
                        fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    option.qualityLabel?.let { quality ->
                        Text(
                            quality, color = if (option.selected) Color.Black else Color.White, fontSize = 9.sp,
                            modifier = Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        option.meta.orEmpty(), color = if (option.selected) Color.Black.copy(alpha = .72f) else Color.White.copy(alpha = .72f), fontSize = 9.sp,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (option.playbackFailed) "Failed" else if (option.selected) "Playing" else "Direct",
                        color = if (option.playbackFailed) Color(0xFFFF7777) else if (option.selected) Color.Black else Color.White.copy(alpha = .62f),
                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(
                            if (option.playbackFailed) Color(0xFFFF7777).copy(alpha = .12f) else if (option.selected) TvDesign.Accent.copy(alpha = .10f) else Color.White.copy(alpha = .06f),
                            RoundedCornerShape(50),
                        ).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        } else {
        Column(Modifier.weight(1f)) {
            Text(
                option.title,
                color = if (option.selected) Color.Black else if (option.enabled) Color.White else Color.White.copy(alpha = .30f),
                fontSize = 12.sp,
                fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.meta?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    color = (if (option.selected) Color.Black else Color.White).copy(alpha = if (option.providerName != null) .72f else .48f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        option.qualityLabel?.let { quality ->
            Spacer(Modifier.width(8.dp))
            Text(
                quality, color = if (option.selected) Color.Black else Color.White, fontSize = 9.sp,
                modifier = Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        if (option.selected) {
            Spacer(Modifier.width(8.dp))
            Text(if (option.providerName != null) "Playing" else "Active",
                color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VueoPlayerEpisodesPanel(
    mediaTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
    panelModifier: Modifier = Modifier,
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
    val ranges = rememberTvEpisodeRanges(seasonEpisodes, currentEpisode?.id)
    val rangeRequester = remember { FocusRequester() }
    val episodeEntryRequester = remember { FocusRequester() }
    var episodeEntryRequest by remember { mutableIntStateOf(0) }
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
    val listTopRequester = if (ranges.enabled) rangeRequester else if (seasons.size > 1) seasonReturnRequester else FocusRequester.Cancel

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .then(panelModifier)
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xF5202124))
                .padding(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 48.dp),
        ) {
            Text(
                "Episodes",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (seasons.size > 1) {
                VueoLeadingTabs {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
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
                            downRequester = FocusRequester.Cancel,
                            onDown = {
                                if (ranges.enabled) runCatching { rangeRequester.requestFocus() } else episodeEntryRequest++
                            },
                            blockLeft = index == 0,
                            blockRight = index == seasons.lastIndex,
                            onInteraction = onInteraction,
                            onFocused = { lastFocusedSeasonIndex = index },
                        ) {
                            selectedSeason = season
                        }
                    }
                }
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            TvEpisodeRangeControls(ranges, rangeRequester,
                if (seasons.size > 1) seasonReturnRequester else FocusRequester.Cancel,
                onDown = { ranges.focusCards = true; ranges.focusRequest++ },
                onInteraction = onInteraction)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    VueoEpisodeList(
                        episodes = ranges.visible,
                        currentEpisode = currentEpisode,
                        entryFocusRequester = episodeEntryRequester,
                        entryRequest = episodeEntryRequest,
                        topRequester = listTopRequester,
                        onInteraction = onInteraction,
                        onSelected = onSelected,
                        targetEpisodeId = if (ranges.enabled) ranges.targetId else null,
                        rangeFocusRequest = ranges.focusRequest,
                        focusRangeCards = ranges.focusCards,
                        onRange = { forward ->
                            val next = ranges.group + if (forward) 1 else -1
                            if (ranges.enabled && next in ranges.groups.indices) {
                                ranges.select(next, focusCards = true, last = !forward); true
                            } else false
                        },
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
    onDown: () -> Unit,
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
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        onInteraction()
                        onDown()
                    }
                    return@onPreviewKeyEvent true
                }
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .clickable(onClick = onClick)
            .background(
                when {
                    selected -> Color.White
                    focused -> Color(0xFF555555)
                    else -> Color(0xFF303030)
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
            color = if (selected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VueoEpisodeList(
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    entryFocusRequester: FocusRequester,
    entryRequest: Int,
    topRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
    targetEpisodeId: String? = null,
    rangeFocusRequest: Int = 0,
    focusRangeCards: Boolean = false,
    onRange: (Boolean) -> Boolean = { false },
) {
    val currentIndex = episodes.indexOfFirst { episode ->
        if (targetEpisodeId != null) targetEpisodeId == episode.id else currentEpisode?.let {
            it.id == episode.id ||
                (it.season == episode.season && it.episode == episode.episode)
        } == true
    }.coerceAtLeast(0)
    val state = key(episodes.firstOrNull()?.id) { rememberLazyListState(initialFirstVisibleItemIndex = currentIndex) }
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

    LaunchedEffect(episodes.firstOrNull()?.id) {
        if (episodes.isNotEmpty()) state.scrollToItem(currentIndex)
    }

    LaunchedEffect(entryRequest) {
        if (entryRequest == 0 || episodes.isEmpty()) return@LaunchedEffect
        state.scrollToItem(currentIndex)
        withFrameNanos { }
        entryFocusRequester.requestTvFocus()
    }

    LaunchedEffect(rangeFocusRequest) {
        if (rangeFocusRequest > 0 && focusRangeCards && episodes.isNotEmpty()) {
            state.scrollToItem(currentIndex)
            withFrameNanos { }
            entryFocusRequester.requestTvFocus()
        }
    }

    // Keep the focused row at the top, matching Details' leading-edge reveal.
    // LazyColumn clamps at the end before focus moves down the remaining rows.
    val episodeReveal = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (containerSize <= 0f || size <= 0f) return 0f
                return offset
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides episodeReveal) {

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
                    onSelected = { onSelected(episode) },
                    onRange = onRange,
                )
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
    onRange: (Boolean) -> Boolean,
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
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    (native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && blockDown)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        onInteraction()
                        return@onPreviewKeyEvent onRange(native.keyCode != KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                    return@onPreviewKeyEvent true
                }
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onSelected()
                true
            }
            .focusable()
            .clickable(onClick = onSelected)
            .background(if (selected) Color.White else if (focused) Color(0xFF555555) else Color.Transparent, shape)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color(0xFF888888)
                    selected -> Color.White
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
                fadeEnabled = false,
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
                color = if (selected) Color.Black else Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            vueoPlayerFormatReleaseDate(episode.released)?.let {
                Text(it, color = (if (selected) Color.Black else Color.White).copy(alpha = .42f), fontSize = 9.sp)
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = (if (selected) Color.Black else Color.White).copy(alpha = .48f),
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



/** Leading-edge focus reveal, naturally clamped at the last tab. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VueoLeadingTabs(content: @Composable () -> Unit) {
    val reveal = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (containerSize > 0f && size > 0f) offset else 0f
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides reveal, content = content)
}
