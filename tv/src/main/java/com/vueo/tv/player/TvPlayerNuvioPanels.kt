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
        TvPlayerPanel.SUBTITLES -> "Choose a subtitle track"
        TvPlayerPanel.AUDIO -> "Choose an audio track"
        TvPlayerPanel.MORE -> "Playback and session controls"
        else -> ""
    }

    val rightPanel = panel == TvPlayerPanel.AUDIO
    val morePanel = panel == TvPlayerPanel.MORE
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .28f))
            .background(
                if (rightPanel) {
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = .12f),
                            Color.Black.copy(alpha = .68f),
                            Color.Black.copy(alpha = .96f),
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = .96f),
                            Color.Black.copy(alpha = .70f),
                            Color.Black.copy(alpha = if (morePanel) .58f else .12f),
                        )
                    )
                }
            )
    ) {
        val width = when {
            morePanel -> 760.dp
            panel == TvPlayerPanel.SUBTITLES -> 710.dp
            else -> 400.dp
        }
        Column(
            modifier = Modifier
                .align(if (rightPanel) Alignment.CenterEnd else Alignment.Center)
                .width(width)
                .fillMaxHeight(if (morePanel) .92f else .88f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2181A1C))
                .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Text(title, color = Color.White, fontSize = if (morePanel) 22.sp else 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color.White.copy(alpha = .52f), fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            if (morePanel) {
                MobileTvMoreOptions(options, onInteraction, onSelected)
            } else {
                NuvioOptionList(options, .90f, onInteraction, onSelected)
            }
        }
    }
}

@Composable
private fun MobileTvMoreOptions(
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val playback = options.filter { it.key.startsWith("speed:") || it.key.startsWith("fit:") }
    val sleep = options.filter { it.key.startsWith("sleep:") }
    val behaviour = options.filter { it.key.startsWith("toggle:") || it.key == "reset" }

    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MobileTvMoreSection("Playback", playback, true, Modifier.weight(1.05f), onInteraction, onSelected)
        MobileTvMoreSection("Sleep timer", sleep, false, Modifier.weight(.90f), onInteraction, onSelected)
        MobileTvMoreSection("Behaviour", behaviour, false, Modifier.weight(1f), onInteraction, onSelected)
    }
}

@Composable
private fun MobileTvMoreSection(
    title: String,
    options: List<TvPlayerOption>,
    autoFocus: Boolean,
    modifier: Modifier,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .035f))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        NuvioOptionList(
            options = options,
            maxHeightFraction = 1f,
            onInteraction = onInteraction,
            onSelected = onSelected,
            autoFocus = autoFocus,
        )
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
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .24f))
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .64f), Color.Black.copy(alpha = .97f))))
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(.90f).width(620.dp)
                .padding(end = 40.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2181A1C))
                .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sources", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                NuvioPanelTextAction("Close", onDismiss)
            }
            Spacer(Modifier.height(2.dp))
            Text(title, color = Color.White.copy(alpha = .52f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
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

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .32f))
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .64f), Color.Black.copy(alpha = .97f))))
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(.90f).width(620.dp)
                .padding(end = 40.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2181A1C))
                .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Episodes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
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
    autoFocus: Boolean = true,
) {
    val state = rememberLazyListState()
    val requesters = remember(options.map { it.key }) { List(options.size.coerceAtLeast(1)) { FocusRequester() } }
    val focusSignature = options.map { it.key to it.selected }
    LaunchedEffect(focusSignature, autoFocus) {
        if (!autoFocus || options.isEmpty()) return@LaunchedEffect
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth().focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .focusable(option.enabled).clickable(enabled = option.enabled, onClick = onSelected)
            .background(
                when {
                    focused -> Color.White.copy(alpha = .13f)
                    option.selected -> VueoTvPlayerAccent.copy(alpha = .15f)
                    else -> Color.White.copy(alpha = .045f)
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    option.selected -> VueoTvPlayerAccent.copy(alpha = .58f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.title, color = if (option.enabled) Color.White else Color.White.copy(alpha = .30f), fontSize = 12.sp, fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            option.meta?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp)); Text(it, color = Color.White.copy(alpha = .48f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (option.selected) Text("Active", color = VueoTvPlayerAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NuvioSeasonChip(season: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember(season) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(when { focused -> Color.White; selected -> VueoTvPlayerAccent.copy(alpha = .13f); else -> Color.Transparent }, shape)
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
            .border(if (focused) 2.dp else 1.dp, when { focused -> Color.White; selected -> VueoTvPlayerAccent.copy(alpha = .72f); else -> Color.White.copy(alpha = .07f) }, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(TvDesign.SurfaceRaised)) {
            TvNetworkImage(episode.thumbnail, episode.title, Modifier.fillMaxSize(), ContentScale.Crop, TvDesign.SurfaceRaised)
            Text("S${episode.season}E${episode.episode}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).background(VueoTvPlayerAccent, CircleShape))
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
