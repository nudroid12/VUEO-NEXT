package com.vueo.tv.player

import android.view.KeyEvent
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
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

private val PanelShape = RoundedCornerShape(18.dp)
private val SubtitleWorkspaceBottomClearance = 104.dp

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
    val closeRequester = remember { FocusRequester() }
    val listEntryRequester = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .985f))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sources", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                VueoPanelTextAction(
                    label = "Close",
                    requester = closeRequester,
                    downRequester = listEntryRequester,
                    onInteraction = onInteraction,
                    onClick = onDismiss,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White.copy(alpha = .56f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            VueoOptionList(
                options = options,
                maxHeightFraction = .90f,
                onInteraction = onInteraction,
                onSelected = onSelected,
                topRequester = closeRequester,
                entryFocusRequester = listEntryRequester,
            )
        }
    }
}

@Composable
private fun VueoOptionList(
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
    val closeRequester = remember { FocusRequester() }
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
    val listTopRequester = if (seasons.size > 1) seasonReturnRequester else closeRequester
    val closeDownRequester = if (seasons.size > 1) seasonReturnRequester else episodeEntryRequester

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .99f))
                .padding(28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Episodes", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        mediaTitle,
                        color = Color.White.copy(alpha = .54f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VueoPanelTextAction(
                    label = "Close",
                    requester = closeRequester,
                    downRequester = closeDownRequester,
                    onInteraction = onInteraction,
                    onClick = onDismiss,
                )
            }

            if (seasons.size > 1) {
                Spacer(Modifier.height(16.dp))
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
                            upRequester = closeRequester,
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
            }

            Spacer(Modifier.height(16.dp))
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


@Composable
internal fun VueoPlayerSubtitleWorkspace(
    tracks: List<TvPlayerTrackChoice>,
    subtitlesDisabled: Boolean,
    entryFocusRequester: FocusRequester,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    subtitleDelayMs: Int,
    style: TvPlayerSubtitleStyleState,
    onInteraction: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
    onSubtitleDelayChange: (Int) -> Unit,
    onStyleChange: (TvPlayerSubtitleStyleState) -> Unit,
) {
    val groups = remember(tracks, preferredLanguageCode, secondaryLanguageCode) {
        tvBuildSubtitleLanguageGroups(tracks, preferredLanguageCode, secondaryLanguageCode)
    }
    val selectedTrack = tracks.firstOrNull { it.selected }
    val selectedLanguageCode = selectedTrack?.language?.let(::tvCanonicalLanguage)
    val hasSelectedSubtitle = !subtitlesDisabled && selectedLanguageCode != null
    var activeLanguageCode by remember(selectedLanguageCode, subtitlesDisabled) {
        mutableStateOf(selectedLanguageCode.takeIf { hasSelectedSubtitle })
    }
    var styleOpen by remember(selectedLanguageCode, subtitlesDisabled) {
        mutableStateOf(hasSelectedSubtitle)
    }
    val visibleTracks = groups.firstOrNull { it.code == activeLanguageCode }?.tracks.orEmpty()
    val entryLanguageIndex = when {
        subtitlesDisabled -> 0
        selectedLanguageCode != null -> groups.indexOfFirst { it.code == selectedLanguageCode }
            .let { if (it < 0) 0 else it + 1 }
        else -> 0
    }
    val languageRequesters = remember(groups.map { it.code }, entryLanguageIndex, entryFocusRequester) {
        List(groups.size + 1) { index ->
            if (index == entryLanguageIndex) entryFocusRequester else FocusRequester()
        }
    }
    val trackRequesters = remember(visibleTracks.map { it.key }) {
        List(visibleTracks.size.coerceAtLeast(1)) { FocusRequester() }
    }
    val syncRequester = remember { FocusRequester() }
    val sizeRequester = remember { FocusRequester() }
    val boldRequester = remember { FocusRequester() }
    val textColorRequester = remember { FocusRequester() }
    val opacityRequester = remember { FocusRequester() }
    val outlineRequester = remember { FocusRequester() }
    val outlineColorRequester = remember { FocusRequester() }
    val positionRequester = remember { FocusRequester() }
    val resetRequester = remember { FocusRequester() }
    val activeLanguageRequester = languageRequesters.getOrNull(
        groups.indexOfFirst { it.code == activeLanguageCode }.let { if (it < 0) 0 else it + 1 }
    ) ?: languageRequesters.first()
    val firstTrackRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else FocusRequester.Cancel
    val selectedVisibleTrackIndex = visibleTracks.indexOfFirst { it.selected }
    var styleReturnTrackIndex by remember(visibleTracks.map { it.key }) {
        mutableIntStateOf(selectedVisibleTrackIndex.coerceAtLeast(0))
    }
    var pendingTrackFocusLanguage by remember { mutableStateOf<String?>(null) }
    val styleLeftRequester = trackRequesters.getOrNull(styleReturnTrackIndex)
        ?: if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester
    var initialFocusAssigned by remember { mutableStateOf(false) }

    LaunchedEffect(groups, entryLanguageIndex, initialFocusAssigned) {
        if (initialFocusAssigned) return@LaunchedEffect
        initialFocusAssigned = languageRequesters[
            entryLanguageIndex.coerceIn(languageRequesters.indices)
        ].requestTvFocus()
    }

    LaunchedEffect(activeLanguageCode, visibleTracks, pendingTrackFocusLanguage) {
        if (
            pendingTrackFocusLanguage != activeLanguageCode ||
            visibleTracks.isEmpty()
        ) {
            return@LaunchedEffect
        }
        if (trackRequesters.first().requestTvFocus()) {
            pendingTrackFocusLanguage = null
        }
    }

    val textColours = remember {
        listOf(
            0xFFFFFFFF.toInt(),
            0xFFDCEEFF.toInt(),
            0xFFFFCC2F.toInt(),
            0xFF18C7F5.toInt(),
            0xFFFF6B86.toInt(),
            0xFF6EE7C1.toInt(),
        )
    }
    val outlineColours = remember {
        listOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF18C7F5.toInt(),
            0xFFFF6B86.toInt(),
        )
    }
    val opacity = subtitleAlphaPercent(style.textColor)
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
                .fillMaxSize()
                .padding(start = 44.dp, top = 24.dp, end = 44.dp, bottom = SubtitleWorkspaceBottomClearance),
        ) {
            Text(
                "Subtitles",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a language, track and style",
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(.30f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Languages")
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        item(key = "subtitle:none") {
                            VueoSubtitleLanguageRow(
                                title = "Off",
                                count = null,
                                selected = subtitlesDisabled && activeLanguageCode == null,
                                requester = languageRequesters[0],
                                blockUp = true,
                                blockDown = groups.isEmpty(),
                                rightRequester = firstTrackRequester,
                                onInteraction = onInteraction,
                            ) {
                                pendingTrackFocusLanguage = null
                                activeLanguageCode = null
                                styleOpen = false
                                onDisable()
                            }
                        }
                        itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                            VueoSubtitleLanguageRow(
                                title = group.label,
                                count = group.tracks.size,
                                selected = group.code == activeLanguageCode ||
                                    (activeLanguageCode == null && !subtitlesDisabled && group.code == selectedLanguageCode),
                                requester = languageRequesters[index + 1],
                                blockUp = false,
                                blockDown = index == groups.lastIndex,
                                rightRequester = if (group.code == activeLanguageCode) firstTrackRequester else FocusRequester.Cancel,
                                onRight = if (group.code != activeLanguageCode && group.tracks.isNotEmpty()) {
                                    {
                                        activeLanguageCode = group.code
                                        styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                                        pendingTrackFocusLanguage = group.code
                                    }
                                } else {
                                    null
                                },
                                onInteraction = onInteraction,
                            ) {
                                pendingTrackFocusLanguage = null
                                activeLanguageCode = group.code
                                styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(.40f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Subtitles")
                    Spacer(Modifier.height(10.dp))
                    when {
                        activeLanguageCode == null -> VueoSubtitleEmpty("Choose a language to see its exact subtitle tracks.")
                        visibleTracks.isNotEmpty() -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            itemsIndexed(visibleTracks, key = { _, track -> track.key }) { index, track ->
                                val matchingLabels = visibleTracks.count {
                                    it.label.equals(track.label, ignoreCase = true)
                                }
                                val matchingIndex = if (matchingLabels > 1) {
                                    visibleTracks.take(index + 1).count {
                                        it.label.equals(track.label, ignoreCase = true)
                                    }
                                } else 0
                                val identity = if (matchingLabels > 1) "Track $matchingIndex" else ""

                                VueoSubtitleTrackRow(
                                    title = track.label,
                                    provider = track.sourceLabel,
                                    detail = identity,
                                    selected = !subtitlesDisabled && track.selected,
                                    requester = trackRequesters[index],
                                    blockUp = index == 0,
                                    blockDown = index == visibleTracks.lastIndex,
                                    leftRequester = activeLanguageRequester,
                                    rightRequester = if (styleOpen) syncRequester else FocusRequester.Cancel,
                                    onInteraction = onInteraction,
                                    onFocused = { styleReturnTrackIndex = index },
                                ) {
                                    styleOpen = true
                                    onSelect(track)
                                }
                            }
                        }
                        groups.isEmpty() -> VueoSubtitleEmpty(
                            "No subtitles available. Try another source or install a subtitle addon."
                        )
                        else -> VueoSubtitleEmpty("No subtitle track is available for this language.")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(.30f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Style")
                    Spacer(Modifier.height(10.dp))
                    if (styleOpen && !subtitlesDisabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            VueoSubtitleStepperRow(
                                title = "Sync",
                                value = formatSubtitleDelayTv(subtitleDelayMs),
                                requester = syncRequester,
                                upRequester = FocusRequester.Cancel,
                                downRequester = sizeRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onDecrease = {
                                    onSubtitleDelayChange((subtitleDelayMs - 250).coerceAtLeast(-60_000))
                                },
                                onIncrease = {
                                    onSubtitleDelayChange((subtitleDelayMs + 250).coerceAtMost(60_000))
                                },
                            )
                            VueoSubtitleStepperRow(
                                title = "Font Size",
                                value = "${style.fontSizeSp}sp",
                                requester = sizeRequester,
                                upRequester = syncRequester,
                                downRequester = boldRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onDecrease = {
                                    onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp - 2).coerceAtLeast(12)))
                                },
                                onIncrease = {
                                    onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp + 2).coerceAtMost(40)))
                                },
                            )
                            VueoSubtitleToggleRow(
                                title = "Bold",
                                enabled = style.bold,
                                requester = boldRequester,
                                upRequester = sizeRequester,
                                downRequester = textColorRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onToggle = { onStyleChange(style.copy(bold = !style.bold)) },
                            )
                            VueoSubtitleColorRow(
                                title = "Text Color",
                                colours = textColours,
                                selectedColour = style.textColor,
                                requester = textColorRequester,
                                upRequester = boldRequester,
                                downRequester = opacityRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                            ) { colour ->
                                onStyleChange(
                                    style.copy(
                                        textColor = subtitleWithAlpha(colour, opacity)
                                    )
                                )
                            }
                            VueoSubtitleStepperRow(
                                title = "Text Opacity",
                                value = "$opacity%",
                                requester = opacityRequester,
                                upRequester = textColorRequester,
                                downRequester = outlineRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onDecrease = {
                                    onStyleChange(
                                        style.copy(
                                            textColor = subtitleWithAlpha(style.textColor, (opacity - 10).coerceAtLeast(30))
                                        )
                                    )
                                },
                                onIncrease = {
                                    onStyleChange(
                                        style.copy(
                                            textColor = subtitleWithAlpha(style.textColor, (opacity + 10).coerceAtMost(100))
                                        )
                                    )
                                },
                            )
                            VueoSubtitleToggleRow(
                                title = "Outline",
                                enabled = style.outlineEnabled,
                                requester = outlineRequester,
                                upRequester = opacityRequester,
                                downRequester = if (style.outlineEnabled) outlineColorRequester else positionRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onToggle = { onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled)) },
                            )
                            if (style.outlineEnabled) {
                                VueoSubtitleColorRow(
                                    title = "Outline Color",
                                    colours = outlineColours,
                                    selectedColour = style.outlineColor,
                                    requester = outlineColorRequester,
                                    upRequester = outlineRequester,
                                    downRequester = positionRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) { colour ->
                                    onStyleChange(style.copy(outlineColor = colour))
                                }
                            }
                            VueoSubtitleStepperRow(
                                title = "Bottom Position",
                                value = "${style.bottomPaddingPercent}%",
                                requester = positionRequester,
                                upRequester = if (style.outlineEnabled) outlineColorRequester else outlineRequester,
                                downRequester = resetRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                                onDecrease = {
                                    onStyleChange(
                                        style.copy(
                                            bottomPaddingPercent = (style.bottomPaddingPercent - 2).coerceAtLeast(5)
                                        )
                                    )
                                },
                                onIncrease = {
                                    onStyleChange(
                                        style.copy(
                                            bottomPaddingPercent = (style.bottomPaddingPercent + 2).coerceAtMost(40)
                                        )
                                    )
                                },
                            )
                            VueoSubtitleActionRow(
                                title = "Reset Style",
                                detail = "White • 22sp • black outline • 8% bottom",
                                requester = resetRequester,
                                upRequester = positionRequester,
                                downRequester = FocusRequester.Cancel,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                            ) {
                                onStyleChange(TvPlayerSubtitleStyleState())
                            }
                        }
                    } else {
                        VueoSubtitleEmpty("Select an exact subtitle track to adjust its style.")
                    }
                }
            }
        }
    }
}


@Composable
internal fun VueoPlayerAudioWorkspace(
    tracks: List<TvPlayerTrackChoice>,
    automaticSelected: Boolean,
    activeSourceLabel: String?,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onAutomatic: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
) {
    val closeRequester = remember { FocusRequester() }
    val listEntryRequester = remember { FocusRequester() }
    val options = remember(tracks, automaticSelected, activeSourceLabel) {
        buildList {
            add(
                TvPlayerOption(
                    key = TV_AUDIO_AUTO,
                    title = "Stream default",
                    meta = activeSourceLabel?.takeIf { it.isNotBlank() } ?: "Select audio automatically",
                    selected = automaticSelected,
                )
            )
            tracks.forEach { track ->
                add(
                    TvPlayerOption(
                        key = track.selectionId,
                        title = track.label,
                        meta = listOfNotNull(
                            track.metadata?.takeIf { it.isNotBlank() },
                            track.sourceLabel.takeIf { it.isNotBlank() },
                        ).distinct().joinToString(" • "),
                        selected = !automaticSelected && track.selected,
                    )
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .99f))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Audio", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                VueoPanelTextAction(
                    label = "Close",
                    requester = closeRequester,
                    downRequester = listEntryRequester,
                    onInteraction = onInteraction,
                    onClick = onDismiss,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (tracks.isEmpty()) "No selectable alternate audio tracks" else "Choose an exact audio track",
                color = Color.White.copy(alpha = .54f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(18.dp))
            VueoOptionList(
                options = options,
                maxHeightFraction = .84f,
                onInteraction = onInteraction,
                onSelected = { option ->
                    if (option.key == TV_AUDIO_AUTO) onAutomatic()
                    else tracks.firstOrNull { it.selectionId == option.key }?.let(onSelect)
                },
                topRequester = closeRequester,
                entryFocusRequester = listEntryRequester,
            )
        }
    }
}

@Composable
private fun VueoSubtitleColumnTitle(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = .94f),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun subtitleAccentContentColor(): Color =
    if (TvDesign.Accent.luminance() >= .48f) Color.Black else Color.White

@Composable
private fun VueoSubtitleLanguageRow(
    title: String,
    count: Int?,
    selected: Boolean,
    requester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    rightRequester: FocusRequester,
    onRight: (() -> Unit)? = null,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = rightRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                    onRight != null
                ) {
                    onInteraction()
                    onRight()
                    return@onPreviewKeyEvent true
                }
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    focused -> TvDesign.Accent.copy(alpha = .26f)
                    selected -> TvDesign.Accent.copy(alpha = .10f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> TvDesign.Accent
                    selected -> TvDesign.Accent.copy(alpha = .48f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Box(
                modifier = Modifier
                    .width(27.dp)
                    .height(27.dp)
                    .background(
                        when {
                            focused -> TvDesign.Accent.copy(alpha = .34f)
                            selected -> TvDesign.Accent.copy(alpha = .18f)
                            else -> Color.White.copy(alpha = .09f)
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    it.toString(),
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VueoSubtitleTrackRow(
    title: String,
    provider: String,
    detail: String,
    selected: Boolean,
    requester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title, provider, detail) { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                left = leftRequester
                right = rightRequester
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
            .background(
                when {
                    focused -> TvDesign.Accent.copy(alpha = .24f)
                    selected -> TvDesign.Accent.copy(alpha = .10f)
                    else -> Color.White.copy(alpha = .025f)
                },
                shape,
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> TvDesign.Accent
                    selected -> TvDesign.Accent.copy(alpha = .48f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .background(
                        if (selected || focused) TvDesign.Accent.copy(alpha = .12f)
                        else Color.White.copy(alpha = .055f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        if (selected || focused) TvDesign.Accent.copy(alpha = .38f)
                        else Color.White.copy(alpha = .09f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    provider.ifBlank { "Subtitle" },
                    color = if (selected || focused) TvDesign.Accent else Color.White.copy(alpha = .66f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    color = Color.White.copy(alpha = .50f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text(
                "✓",
                color = TvDesign.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun VueoSubtitleStepperRow(
    title: String,
    value: String,
    requester: FocusRequester? = null,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val minusRequester = remember(title) { FocusRequester() }
    val internalValueRequester = remember(title) { FocusRequester() }
    val plusRequester = remember(title) { FocusRequester() }
    val valueRequester = requester ?: internalValueRequester

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VueoSubtitleStepperButton(
                label = "−",
                modifier = Modifier.width(46.dp),
                requester = minusRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = leftRequester,
                rightRequester = valueRequester,
                onInteraction = onInteraction,
                onClick = onDecrease,
            )
            VueoSubtitleStepperButton(
                label = value,
                modifier = Modifier.weight(1f),
                requester = valueRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = minusRequester,
                rightRequester = plusRequester,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
            VueoSubtitleStepperButton(
                label = "+",
                modifier = Modifier.width(46.dp),
                requester = plusRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = valueRequester,
                rightRequester = FocusRequester.Cancel,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun VueoSubtitleStepperButton(
    label: String,
    modifier: Modifier,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(requester) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Box(
        modifier = modifier
            .height(38.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
                left = leftRequester
                right = rightRequester
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
            .background(
                if (focused) TvDesign.Accent.copy(alpha = .32f) else Color.White.copy(alpha = .09f),
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) TvDesign.Accent else Color.White.copy(alpha = .08f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = if (label == "+" || label == "−") 18.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun VueoSubtitleToggleRow(
    title: String,
    enabled: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onToggle: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(38.dp)
                .focusRequester(requester)
                .focusProperties {
                    up = upRequester
                    down = downRequester
                    left = leftRequester
                    right = FocusRequester.Cancel
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onInteraction()
                }
                .onPreviewKeyEvent { event ->
                    if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                    onInteraction()
                    if (event.type == KeyEventType.KeyUp) onToggle()
                    true
                }
                .focusable()
                .background(
                    when {
                        focused -> TvDesign.Accent.copy(alpha = .32f)
                        enabled -> TvDesign.Accent.copy(alpha = .14f)
                        else -> Color.White.copy(alpha = .09f)
                    },
                    shape,
                )
                .border(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> TvDesign.Accent
                        enabled -> TvDesign.Accent.copy(alpha = .55f)
                        else -> Color.White.copy(alpha = .08f)
                    },
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enabled) "On" else "Off",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun VueoSubtitleColorRow(
    title: String,
    colours: List<Int>,
    selectedColour: Int,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val selectedIndex = colours.indexOfFirst {
        (selectedColour and 0x00FFFFFF) == (it and 0x00FFFFFF)
    }.coerceAtLeast(0)
    val requesters = remember(colours, selectedIndex, requester) {
        List(colours.size) { index ->
            if (index == selectedIndex) requester else FocusRequester()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            colours.forEachIndexed { index, colour ->
                var focused by remember(colour) { mutableStateOf(false) }
                val selected = (selectedColour and 0x00FFFFFF) == (colour and 0x00FFFFFF)
                val swatch = Color(colour)
                val checkColor = if (swatch.luminance() > .48f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .focusRequester(requesters[index])
                        .focusProperties {
                            up = upRequester
                            down = downRequester
                            if (index == 0) left = leftRequester
                            if (index == colours.lastIndex) right = FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onInteraction()
                        }
                        .onPreviewKeyEvent { event ->
                            if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                            onInteraction()
                            if (event.type == KeyEventType.KeyUp) onSelected(colour)
                            true
                        }
                        .focusable()
                        .border(
                            width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                            color = when {
                                focused -> TvDesign.Accent
                                selected -> Color.White.copy(alpha = .92f)
                                else -> Color.White.copy(alpha = .18f)
                            },
                            shape = CircleShape,
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(swatch, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text(
                                "✓",
                                color = checkColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VueoSubtitleActionRow(
    title: String,
    detail: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val contentColor = if (focused) subtitleAccentContentColor() else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
                left = leftRequester
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
            .background(if (focused) TvDesign.Accent else Color.White.copy(alpha = .045f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            color = if (focused) contentColor.copy(alpha = .62f) else Color.White.copy(alpha = .44f),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VueoSubtitleEmpty(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = .52f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

private fun subtitleAlphaPercent(colour: Int): Int =
    (((colour ushr 24) * 100) + 127) / 255

private fun subtitleWithAlpha(colour: Int, opacityPercent: Int): Int {
    val alpha = (255 * opacityPercent.coerceIn(0, 100) / 100) shl 24
    return (colour and 0x00FFFFFF) or alpha
}

private fun formatSubtitleDelayTv(value: Int): String {
    if (value == 0) return "0.00s"
    val seconds = value / 1000.0
    return java.lang.String.format(java.util.Locale.US, if (value > 0) "+%.2fs" else "%.2fs", seconds)
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvPanelActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
