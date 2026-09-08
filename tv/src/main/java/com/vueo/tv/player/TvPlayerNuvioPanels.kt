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
    val requesters = remember(options.map { it.key }) { List(options.size.coerceAtLeast(1)) { FocusRequester() } }
    LaunchedEffect(options) {
        if (options.isEmpty()) return@LaunchedEffect
        val index = options.indexOfFirst { it.selected && it.enabled }.takeIf { it >= 0 }
            ?: options.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: 0
        state.scrollToItem(index)
        delay(45)
        runCatching { requesters[index].requestFocus() }
    }
    if (options.isEmpty()) {
        Text("Nothing available for this stream.", color = Color.White.copy(alpha = .52f), fontSize = 12.sp)
        return
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxHeight(maxHeightFraction), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        itemsIndexed(options, key = { index, option -> "${option.key}:$index" }) { index, option ->
            NuvioOptionRow(option, requesters[index], onInteraction) { onSelected(option) }
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
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    subtitleDelayMs: Int,
    fontSizeSp: Int,
    bold: Boolean,
    textColor: Int,
    textOpacityPercent: Int,
    outlineEnabled: Boolean,
    outlineColor: Int,
    bottomPaddingPercent: Int,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
    onSubtitleDelayChange: (Int) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onTextColorChange: (Int) -> Unit,
    onTextOpacityChange: (Int) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onOutlineColorChange: (Int) -> Unit,
    onBottomPaddingChange: (Int) -> Unit,
) {
    val groups = remember(tracks, preferredLanguageCode, secondaryLanguageCode) {
        tvBuildSubtitleLanguageGroups(tracks, preferredLanguageCode, secondaryLanguageCode)
    }
    val selectedTrack = tracks.firstOrNull { it.selected }
    val selectedLanguageCode = selectedTrack?.language?.let(::tvCanonicalLanguage)
    var activeLanguageCode by remember(groups, selectedLanguageCode, subtitlesDisabled) {
        mutableStateOf(
            if (subtitlesDisabled) null
            else selectedLanguageCode ?: groups.firstOrNull()?.code
        )
    }
    val visibleTracks = groups.firstOrNull { it.code == activeLanguageCode }?.tracks.orEmpty()
    val languageRequesters = remember(groups.map { it.code }) {
        List(groups.size + 1) { FocusRequester() }
    }
    val trackRequesters = remember(visibleTracks.map { it.key }) {
        List(visibleTracks.size.coerceAtLeast(1)) { FocusRequester() }
    }
    val syncRequester = remember { FocusRequester() }
    val activeLanguageRequester = languageRequesters.getOrNull(
        groups.indexOfFirst { it.code == activeLanguageCode }.let { if (it < 0) 0 else it + 1 }
    ) ?: languageRequesters.first()
    val firstTrackRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else syncRequester

    LaunchedEffect(groups, selectedLanguageCode, subtitlesDisabled) {
        delay(40)
        val index = when {
            subtitlesDisabled -> 0
            selectedLanguageCode != null -> groups.indexOfFirst { it.code == selectedLanguageCode }.let { if (it < 0) 0 else it + 1 }
            else -> if (groups.isNotEmpty()) 1 else 0
        }
        runCatching { languageRequesters[index.coerceIn(languageRequesters.indices)].requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .34f))) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .width(940.dp)
                .fillMaxHeight(.86f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NuvioSubtitleSectionCard(title = "Languages", modifier = Modifier.width(220.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    item(key = "subtitle:off") {
                        NuvioSubtitleChoiceRow(
                            title = "Off",
                            detail = "Disable subtitles",
                            selected = subtitlesDisabled,
                            requester = languageRequesters[0],
                            rightRequester = firstTrackRequester,
                            onInteraction = onInteraction,
                        ) {
                            activeLanguageCode = null
                            onDisable()
                        }
                    }
                    itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                        NuvioSubtitleChoiceRow(
                            title = group.label,
                            detail = "${group.tracks.size} track${if (group.tracks.size == 1) "" else "s"}",
                            selected = !subtitlesDisabled && group.code == selectedLanguageCode,
                            requester = languageRequesters[index + 1],
                            rightRequester = firstTrackRequester,
                            onInteraction = onInteraction,
                        ) {
                            activeLanguageCode = group.code
                        }
                    }
                }
            }

            NuvioSubtitleSectionCard(title = "Subtitles", modifier = Modifier.width(360.dp)) {
                when {
                    activeLanguageCode == null -> NuvioSubtitleEmpty("Choose a language to view its tracks.")
                    visibleTracks.isEmpty() -> NuvioSubtitleEmpty("No subtitle track is available for this language.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        itemsIndexed(visibleTracks, key = { _, track -> track.key }) { index, track ->
                            NuvioSubtitleChoiceRow(
                                title = track.label,
                                detail = listOfNotNull(
                                    track.sourceLabel.takeIf { it.isNotBlank() },
                                    track.metadata?.takeIf { it.isNotBlank() },
                                ).distinct().joinToString(" • "),
                                selected = !subtitlesDisabled && track.selected,
                                requester = trackRequesters[index],
                                leftRequester = activeLanguageRequester,
                                rightRequester = syncRequester,
                                onInteraction = onInteraction,
                            ) {
                                onSelect(track)
                            }
                        }
                    }
                }
            }

            NuvioSubtitleSectionCard(title = "Style & Sync", modifier = Modifier.width(336.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NuvioSubtitleAdjustRow(
                        title = "Subtitle sync",
                        value = formatSubtitleDelayTv(subtitleDelayMs),
                        requester = syncRequester,
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onLeft = { onSubtitleDelayChange((subtitleDelayMs - 250).coerceAtLeast(-60_000)) },
                        onRight = { onSubtitleDelayChange((subtitleDelayMs + 250).coerceAtMost(60_000)) },
                    )
                    NuvioSubtitleAdjustRow(
                        title = "Text size",
                        value = "$fontSizeSp sp",
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onLeft = { onFontSizeChange((fontSizeSp - 1).coerceAtLeast(12)) },
                        onRight = { onFontSizeChange((fontSizeSp + 1).coerceAtMost(40)) },
                    )
                    NuvioSubtitleToggleRow(
                        title = "Bold",
                        enabled = bold,
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onToggle = { onBoldChange(!bold) },
                    )
                    val textColours = listOf(
                        0xFFFFFFFF.toInt(),
                        0xFFFFFF66.toInt(),
                        0xFF66E7FF.toInt(),
                        0xFFB9FF3A.toInt(),
                        0xFFFF6577.toInt(),
                    )
                    NuvioSubtitleAdjustRow(
                        title = "Text colour",
                        value = subtitleColourName(textColor),
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onLeft = { onTextColorChange(cycleSubtitleColour(textColours, textColor, -1)) },
                        onRight = { onTextColorChange(cycleSubtitleColour(textColours, textColor, 1)) },
                    )
                    NuvioSubtitleAdjustRow(
                        title = "Text opacity",
                        value = "$textOpacityPercent%",
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onLeft = { onTextOpacityChange((textOpacityPercent - 10).coerceAtLeast(20)) },
                        onRight = { onTextOpacityChange((textOpacityPercent + 10).coerceAtMost(100)) },
                    )
                    NuvioSubtitleToggleRow(
                        title = "Outline",
                        enabled = outlineEnabled,
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onToggle = { onOutlineChange(!outlineEnabled) },
                    )
                    if (outlineEnabled) {
                        val outlineColours = listOf(
                            0xFF000000.toInt(),
                            0xFFFFFFFF.toInt(),
                            0xFF38E8F2.toInt(),
                            0xFFFF6577.toInt(),
                        )
                        NuvioSubtitleAdjustRow(
                            title = "Outline colour",
                            value = subtitleColourName(outlineColor),
                            leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                            onInteraction = onInteraction,
                            onLeft = { onOutlineColorChange(cycleSubtitleColour(outlineColours, outlineColor, -1)) },
                            onRight = { onOutlineColorChange(cycleSubtitleColour(outlineColours, outlineColor, 1)) },
                        )
                    }
                    NuvioSubtitleAdjustRow(
                        title = "Bottom position",
                        value = "$bottomPaddingPercent%",
                        leftRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester,
                        onInteraction = onInteraction,
                        onLeft = { onBottomPaddingChange((bottomPaddingPercent - 2).coerceAtLeast(5)) },
                        onRight = { onBottomPaddingChange((bottomPaddingPercent + 2).coerceAtMost(40)) },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Back closes subtitles",
                        color = Color.White.copy(alpha = .40f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Text(
            "Subtitles",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 28.dp),
        )
        Text(
            "Languages, exact tracks, sync and style",
            color = Color.White.copy(alpha = .56f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 61.dp),
        )
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
private fun NuvioSubtitleSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF15181C).copy(alpha = .97f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun NuvioSubtitleChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester = FocusRequester.Cancel,
    rightRequester: FocusRequester = FocusRequester.Cancel,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title, detail) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties { left = leftRequester; right = rightRequester }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(if (focused) Color.White else if (selected) Color.White.copy(alpha = .08f) else Color.Transparent, shape)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    selected -> TvDesign.Accent.copy(alpha = .62f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (focused) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    color = if (focused) Color.Black.copy(alpha = .62f) else Color.White.copy(alpha = .46f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text("Active", color = if (focused) Color.Black else TvDesign.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NuvioSubtitleAdjustRow(
    title: String,
    value: String,
    requester: FocusRequester? = null,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusProperties { left = leftRequester; right = FocusRequester.Cancel }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onLeft(); onInteraction(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onRight(); onInteraction(); true }
                    else -> false
                }
            }
            .focusable()
            .background(if (focused) Color.White else Color.White.copy(alpha = .04f), shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .08f), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = if (focused) Color.Black else Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("‹  $value  ›", color = if (focused) Color.Black else Color.White.copy(alpha = .68f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { left = leftRequester; right = FocusRequester.Cancel }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onToggle()
                true
            }
            .focusable()
            .background(if (focused) Color.White else Color.White.copy(alpha = .04f), shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .08f), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = if (focused) Color.Black else Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(if (enabled) "On" else "Off", color = if (focused) Color.Black else if (enabled) TvDesign.Accent else Color.White.copy(alpha = .54f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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

private fun cycleSubtitleColour(colours: List<Int>, current: Int, direction: Int): Int {
    if (colours.isEmpty()) return current
    val rgb = current and 0x00FFFFFF
    val index = colours.indexOfFirst { (it and 0x00FFFFFF) == rgb }.let { if (it < 0) 0 else it }
    return colours[(index + direction).floorMod(colours.size)]
}

private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size

private fun subtitleColourName(value: Int): String = when (value and 0x00FFFFFF) {
    0x00FFFFFF -> "White"
    0x00FFFF66 -> "Yellow"
    0x0066E7FF -> "Cyan"
    0x00B9FF3A -> "Lime"
    0x00FF6577 -> "Rose"
    0x00000000 -> "Black"
    0x0038E8F2 -> "Aqua"
    else -> "Custom"
}

private fun formatSubtitleDelayTv(value: Int): String = when {
    value == 0 -> "0 ms"
    value > 0 -> "+${value} ms"
    else -> "${value} ms"
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvPanelActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
