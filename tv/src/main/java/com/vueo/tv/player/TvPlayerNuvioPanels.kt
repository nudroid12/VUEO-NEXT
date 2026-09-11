package com.vueo.tv.player

import android.view.KeyEvent
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
import kotlinx.coroutines.delay

private val PanelShape = RoundedCornerShape(16.dp)

@Composable
internal fun NuvioPlayerCompactOverlay(
    panel: TvPlayerPanel,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
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
            NuvioOptionList(options, .58f, onInteraction, onSelected)
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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .985f))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sources", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                NuvioPanelTextAction("Close", onDismiss)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White.copy(alpha = .56f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            NuvioOptionList(options, .90f, onInteraction, onSelected)
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
        val normal = episodes.map { it.season }.distinct().filter { it > 0 }.sorted()
        normal + episodes.map { it.season }.distinct().filter { it == 0 }
    }
    var selectedSeason by remember(episodes, currentEpisode?.season) {
        mutableIntStateOf(currentEpisode?.season?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1)
    }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.season == selectedSeason }.sortedBy { it.episode }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f))) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF111418).copy(alpha = .99f))
                .padding(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Episodes", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(mediaTitle, color = Color.White.copy(alpha = .54f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NuvioPanelTextAction("Close", onDismiss)
            }

            if (seasons.size > 1) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    itemsIndexed(seasons, key = { _, season -> season }) { _, season ->
                        NuvioSeasonChip(season, season == selectedSeason) { selectedSeason = season }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            NuvioEpisodeList(seasonEpisodes, currentEpisode, onInteraction, onSelected)
        }
    }
}

@Composable
private fun NuvioOptionList(
    options: List<TvPlayerOption>,
    maxHeightFraction: Float,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val state = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var initialFocusAssigned by remember { mutableStateOf(false) }

    fun requesterFor(option: TvPlayerOption): FocusRequester =
        requesters.getOrPut(option.key) { FocusRequester() }

    LaunchedEffect(options, initialFocusAssigned) {
        if (initialFocusAssigned || options.isEmpty()) return@LaunchedEffect
        val index = options.indexOfFirst { it.selected && it.enabled }.takeIf { it >= 0 }
            ?: options.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: 0
        state.scrollToItem(index)
        delay(45)
        runCatching { requesterFor(options[index]).requestFocus() }
        initialFocusAssigned = true
    }
    if (options.isEmpty()) {
        Text("Nothing available for this stream.", color = Color.White.copy(alpha = .52f), fontSize = 12.sp)
        return
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxHeight(maxHeightFraction), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        itemsIndexed(options, key = { _, option -> option.key }) { _, option ->
            NuvioOptionRow(option, requesterFor(option), onInteraction) { onSelected(option) }
        }
    }
}

@Composable
private fun NuvioOptionRow(
    option: TvPlayerOption,
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(option.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.fillMaxWidth().focusRequester(requester)
            .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .focusable(option.enabled).clickable(enabled = option.enabled, onClick = onSelected)
            .background(when { focused -> Color.White.copy(alpha = .13f); option.selected -> Color.White.copy(alpha = .06f); else -> Color.Transparent }, shape)
            .border(if (focused) 2.dp else 1.dp, when { focused -> Color.White; option.selected -> TvDesign.Accent.copy(alpha = .58f); else -> Color.White.copy(alpha = .07f) }, shape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(28.dp).background(if (option.selected) TvDesign.Accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, color = if (option.enabled) Color.White else Color.White.copy(alpha = .30f), fontSize = 12.sp, fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            option.meta?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp)); Text(it, color = Color.White.copy(alpha = .48f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (option.selected) Text("Active", color = TvDesign.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NuvioSeasonChip(season: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember(season) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(when { focused -> Color.White; selected -> Color.White.copy(alpha = .13f); else -> Color.Transparent }, shape)
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = .12f), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(if (season == 0) "Specials" else "Season $season", color = if (focused) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NuvioEpisodeList(
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    onInteraction: () -> Unit,
    onSelected: (EpisodeItem) -> Unit,
) {
    val state = rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { e -> currentEpisode?.let { it.id == e.id || (it.season == e.season && it.episode == e.episode) } == true }.coerceAtLeast(0)
    val requesters = remember(episodes.map { it.id }) { List(episodes.size.coerceAtLeast(1)) { FocusRequester() } }
    LaunchedEffect(episodes, currentEpisode?.id) {
        if (episodes.isEmpty()) return@LaunchedEffect
        state.scrollToItem(currentIndex); delay(45); runCatching { requesters[currentIndex].requestFocus() }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        itemsIndexed(episodes, key = { _, e -> e.id }) { index, episode ->
            val selected = currentEpisode?.let { it.id == episode.id || (it.season == episode.season && it.episode == episode.episode) } == true
            NuvioEpisodeRow(episode, selected, requesters[index], onInteraction) { onSelected(episode) }
        }
    }
}

@Composable
private fun NuvioEpisodeRow(
    episode: EpisodeItem,
    selected: Boolean,
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.fillMaxWidth().focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .focusable().clickable(onClick = onSelected)
            .background(if (focused) Color.White.copy(alpha = .11f) else Color.Transparent, shape)
            .border(if (focused) 2.dp else 1.dp, when { focused -> Color.White; selected -> TvDesign.Accent.copy(alpha = .52f); else -> Color.White.copy(alpha = .07f) }, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(130.dp).height(90.dp).clip(RoundedCornerShape(8.dp)).background(TvDesign.SurfaceRaised)) {
            TvNetworkImage(episode.thumbnail, episode.title, Modifier.fillMaxSize(), ContentScale.Crop, TvDesign.SurfaceRaised)
            Text("S${episode.season}E${episode.episode}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).background(TvDesign.Accent, CircleShape))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(episode.title.ifBlank { "Episode ${episode.episode}" }, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            nuvioPlayerFormatReleaseDate(episode.released)?.let { Text(it, color = Color.White.copy(alpha = .42f), fontSize = 9.sp) }
            episode.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = .48f), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun NuvioPanelTextAction(label: String, onClick: () -> Unit) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Text(
        label, color = if (focused) Color.Black else Color.White.copy(alpha = .74f), fontSize = 10.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = .07f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}


@Composable
internal fun NuvioPlayerSubtitleWorkspace(
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
    val activeLanguageRequester = languageRequesters.getOrNull(
        groups.indexOfFirst { it.code == activeLanguageCode }.let { if (it < 0) 0 else it + 1 }
    ) ?: languageRequesters.first()
    val firstTrackRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else FocusRequester.Cancel
    val selectedVisibleTrackIndex = visibleTracks.indexOfFirst { it.selected }
    val styleLeftRequester = trackRequesters.getOrNull(selectedVisibleTrackIndex)
        ?: if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester
    var initialFocusAssigned by remember { mutableStateOf(false) }

    LaunchedEffect(groups, entryLanguageIndex, initialFocusAssigned) {
        if (initialFocusAssigned) return@LaunchedEffect
        repeat(4) { attempt ->
            delay(if (attempt == 0) 24 else 48)
            val focused = runCatching {
                languageRequesters[entryLanguageIndex.coerceIn(languageRequesters.indices)].requestFocus()
            }.getOrDefault(false)
            if (focused) {
                initialFocusAssigned = true
                return@LaunchedEffect
            }
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
    val cardBackground = Color(0xFF17191C).copy(alpha = .96f)
    val cardBorder = Color.White.copy(alpha = .11f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .18f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = .56f),
                    .38f to Color.Black.copy(alpha = .28f),
                    .72f to Color.Black.copy(alpha = .16f),
                    1f to Color.Black.copy(alpha = .10f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 28.dp, end = 48.dp, bottom = 24.dp),
        ) {
            Text(
                "Subtitles",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a language, track and style",
                color = Color.White.copy(alpha = .56f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(250.dp)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    NuvioSubtitleColumnTitle("Languages")
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        item(key = "subtitle:none") {
                            NuvioSubtitleLanguageRow(
                                title = "Off",
                                count = null,
                                selected = subtitlesDisabled && activeLanguageCode == null,
                                requester = languageRequesters[0],
                                rightRequester = firstTrackRequester,
                                onInteraction = onInteraction,
                            ) {
                                activeLanguageCode = null
                                styleOpen = false
                                onDisable()
                            }
                        }
                        itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                            NuvioSubtitleLanguageRow(
                                title = group.label,
                                count = group.tracks.size,
                                selected = group.code == activeLanguageCode ||
                                    (activeLanguageCode == null && !subtitlesDisabled && group.code == selectedLanguageCode),
                                requester = languageRequesters[index + 1],
                                rightRequester = if (group.code == activeLanguageCode) firstTrackRequester else FocusRequester.Cancel,
                                onInteraction = onInteraction,
                            ) {
                                activeLanguageCode = group.code
                                styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .width(430.dp)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    NuvioSubtitleColumnTitle("Subtitles")
                    Spacer(Modifier.height(12.dp))
                    when {
                        activeLanguageCode == null -> NuvioSubtitleEmpty("Choose a language to see its exact subtitle tracks.")
                        visibleTracks.isNotEmpty() -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                val identity = buildList {
                                    track.metadata?.takeIf { it.isNotBlank() }?.let(::add)
                                    if (matchingLabels > 1) add("Track $matchingIndex")
                                }.distinct().joinToString(" • ")

                                NuvioSubtitleTrackRow(
                                    title = track.label,
                                    provider = track.sourceLabel,
                                    detail = identity,
                                    selected = !subtitlesDisabled && track.selected,
                                    requester = trackRequesters[index],
                                    leftRequester = activeLanguageRequester,
                                    rightRequester = if (styleOpen) syncRequester else FocusRequester.Cancel,
                                    onInteraction = onInteraction,
                                ) {
                                    styleOpen = true
                                    onSelect(track)
                                }
                            }
                        }
                        groups.isEmpty() -> NuvioSubtitleEmpty(
                            "No subtitles available. Try another source or install a subtitle addon."
                        )
                        else -> NuvioSubtitleEmpty("No subtitle track is available for this language.")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    NuvioSubtitleColumnTitle("Style")
                    Spacer(Modifier.height(12.dp))
                    if (styleOpen && !subtitlesDisabled) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(15.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            item(key = "subtitle:sync") {
                                NuvioSubtitleStepperRow(
                                    title = "Sync",
                                    value = formatSubtitleDelayTv(subtitleDelayMs),
                                    requester = syncRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        onSubtitleDelayChange((subtitleDelayMs - 250).coerceAtLeast(-60_000))
                                    },
                                    onIncrease = {
                                        onSubtitleDelayChange((subtitleDelayMs + 250).coerceAtMost(60_000))
                                    },
                                )
                            }
                            item(key = "subtitle:size") {
                                NuvioSubtitleStepperRow(
                                    title = "Font Size",
                                    value = "${style.fontSizeSp}sp",
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp - 2).coerceAtLeast(12)))
                                    },
                                    onIncrease = {
                                        onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp + 2).coerceAtMost(40)))
                                    },
                                )
                            }
                            item(key = "subtitle:bold") {
                                NuvioSubtitleToggleRow(
                                    title = "Bold",
                                    enabled = style.bold,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = { onStyleChange(style.copy(bold = !style.bold)) },
                                )
                            }
                            item(key = "subtitle:text-colour") {
                                NuvioSubtitleColorRow(
                                    title = "Text Color",
                                    colours = textColours,
                                    selectedColour = style.textColor,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) { colour ->
                                    onStyleChange(
                                        style.copy(
                                            textColor = subtitleWithAlpha(colour, opacity)
                                        )
                                    )
                                }
                            }
                            item(key = "subtitle:opacity") {
                                NuvioSubtitleStepperRow(
                                    title = "Text Opacity",
                                    value = "$opacity%",
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
                            }
                            item(key = "subtitle:outline") {
                                NuvioSubtitleToggleRow(
                                    title = "Outline",
                                    enabled = style.outlineEnabled,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = { onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled)) },
                                )
                            }
                            if (style.outlineEnabled) {
                                item(key = "subtitle:outline-colour") {
                                    NuvioSubtitleColorRow(
                                        title = "Outline Color",
                                        colours = outlineColours,
                                        selectedColour = style.outlineColor,
                                        leftRequester = styleLeftRequester,
                                        onInteraction = onInteraction,
                                    ) { colour ->
                                        onStyleChange(style.copy(outlineColor = colour))
                                    }
                                }
                            }
                            item(key = "subtitle:position") {
                                NuvioSubtitleStepperRow(
                                    title = "Bottom Position",
                                    value = "${style.bottomPaddingPercent}%",
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
                            }
                            item(key = "subtitle:reset") {
                                NuvioSubtitleActionRow(
                                    title = "Reset Style",
                                    detail = "White • 22sp • black outline • 8% bottom",
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) {
                                    onStyleChange(TvPlayerSubtitleStyleState())
                                }
                            }
                        }
                    } else {
                        NuvioSubtitleEmpty("Select an exact subtitle track to adjust its style.")
                    }
                }
            }
        }
    }
}


@Composable
internal fun NuvioPlayerAudioWorkspace(
    tracks: List<TvPlayerTrackChoice>,
    automaticSelected: Boolean,
    activeSourceLabel: String?,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onAutomatic: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
) {
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
            Text("Audio", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (tracks.isEmpty()) "No selectable alternate audio tracks" else "Choose an exact audio track",
                color = Color.White.copy(alpha = .54f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(18.dp))
            NuvioOptionList(options, .84f, onInteraction) { option ->
                if (option.key == TV_AUDIO_AUTO) onAutomatic()
                else tracks.firstOrNull { it.selectionId == option.key }?.let(onSelect)
            }
        }
    }
}

@Composable
private fun NuvioSubtitleColumnTitle(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = .94f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun subtitleAccentContentColor(): Color =
    if (TvDesign.Accent.luminance() >= .48f) Color.Black else Color.White

@Composable
private fun NuvioSubtitleLanguageRow(
    title: String,
    count: Int?,
    selected: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties { right = rightRequester }
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
                when {
                    focused -> TvDesign.Accent.copy(alpha = .30f)
                    selected -> TvDesign.Accent.copy(alpha = .16f)
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
                    selected -> TvDesign.Accent.copy(alpha = .72f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Box(
                modifier = Modifier
                    .width(31.dp)
                    .height(31.dp)
                    .background(
                        when {
                            focused -> TvDesign.Accent.copy(alpha = .42f)
                            selected -> TvDesign.Accent.copy(alpha = .28f)
                            else -> Color.White.copy(alpha = .12f)
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    it.toString(),
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NuvioSubtitleTrackRow(
    title: String,
    provider: String,
    detail: String,
    selected: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title, provider, detail) { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
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
                when {
                    focused -> TvDesign.Accent.copy(alpha = .28f)
                    selected -> TvDesign.Accent.copy(alpha = .16f)
                    else -> Color.Black.copy(alpha = .20f)
                },
                shape,
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> TvDesign.Accent
                    selected -> TvDesign.Accent.copy(alpha = .72f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape = shape,
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .background(
                        if (selected || focused) TvDesign.Accent.copy(alpha = .18f)
                        else Color.White.copy(alpha = .08f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        if (selected || focused) TvDesign.Accent.copy(alpha = .50f)
                        else Color.White.copy(alpha = .13f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    provider.ifBlank { "Subtitle" },
                    color = if (selected || focused) TvDesign.Accent else Color.White.copy(alpha = .66f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
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
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun NuvioSubtitleStepperRow(
    title: String,
    value: String,
    requester: FocusRequester? = null,
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            NuvioSubtitleStepperButton(
                label = "−",
                modifier = Modifier.width(52.dp),
                requester = minusRequester,
                leftRequester = leftRequester,
                rightRequester = valueRequester,
                onInteraction = onInteraction,
                onClick = onDecrease,
            )
            NuvioSubtitleStepperButton(
                label = value,
                modifier = Modifier.weight(1f),
                requester = valueRequester,
                leftRequester = minusRequester,
                rightRequester = plusRequester,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
            NuvioSubtitleStepperButton(
                label = "+",
                modifier = Modifier.width(52.dp),
                requester = plusRequester,
                leftRequester = valueRequester,
                rightRequester = FocusRequester.Cancel,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun NuvioSubtitleStepperButton(
    label: String,
    modifier: Modifier,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(requester) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Box(
        modifier = modifier
            .height(44.dp)
            .focusRequester(requester)
            .focusProperties {
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
            fontSize = if (label == "+" || label == "−") 20.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun NuvioSubtitleToggleRow(
    title: String,
    enabled: Boolean,
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(44.dp)
                .focusProperties {
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
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NuvioSubtitleColorRow(
    title: String,
    colours: List<Int>,
    selectedColour: Int,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val requesters = remember(colours) { List(colours.size) { FocusRequester() } }

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            colours.forEachIndexed { index, colour ->
                var focused by remember(colour) { mutableStateOf(false) }
                val selected = (selectedColour and 0x00FFFFFF) == (colour and 0x00FFFFFF)
                val swatch = Color(colour)
                val checkColor = if (swatch.luminance() > .48f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .focusRequester(requesters[index])
                        .focusProperties {
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
                                fontSize = 13.sp,
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
private fun NuvioSubtitleActionRow(
    title: String,
    detail: String,
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
            .focusProperties {
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
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            color = if (focused) contentColor.copy(alpha = .62f) else Color.White.copy(alpha = .44f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NuvioSubtitleEmpty(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = .52f),
        fontSize = 11.sp,
        lineHeight = 15.sp,
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
