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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TV 40A1 overlay correction.
 *
 * These overlays now follow the supplied Nuvio 0.8.6 player geometry directly:
 * transient subtitle/audio rails are bottom-left and compact; Sources/Episodes
 * are fixed 520dp right side panels. No playback, track, source or routing logic
 * lives here.
 */
@Composable
internal fun NuvioPlayerLeftOptionsOverlay(
    panel: TvPlayerPanel,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val title = when (panel) {
        TvPlayerPanel.SUBTITLES -> "Subtitles"
        TvPlayerPanel.AUDIO -> "Audio"
        else -> "Options"
    }
    val railWidth = if (panel == TvPlayerPanel.AUDIO) 444.dp else 320.dp

    NuvioPlayerOverlayScaffold(leftWeighted = true) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 52.dp, end = 52.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            NuvioPlayerOptionRail(
                options = options,
                width = railWidth,
                maxHeight = 430.dp,
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
    NuvioPlayerOverlayScaffold(leftWeighted = false) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF101419).copy(alpha = .99f))
                .padding(32.dp),
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
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = .62f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            NuvioPlayerOptionRail(
                options = options,
                width = 456.dp,
                maxHeight = 720.dp,
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
        val regular = episodes.map { it.season }.distinct().filter { it > 0 }.sorted()
        val specials = episodes.map { it.season }.distinct().filter { it == 0 }
        regular + specials
    }
    var selectedSeason by remember(episodes, currentEpisode?.season) {
        mutableIntStateOf(currentEpisode?.season?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1)
    }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.season == selectedSeason }.sortedBy { it.episode }
    }

    NuvioPlayerOverlayScaffold(leftWeighted = false) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xFF101419).copy(alpha = .99f))
                .padding(32.dp),
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
                    Spacer(Modifier.height(5.dp))
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
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            Spacer(Modifier.height(16.dp))
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
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (leftWeighted) .34f else .45f)),
        )
        if (leftWeighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = .88f),
                                Color.Black.copy(alpha = .48f),
                                Color.Transparent,
                            )
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = .18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = .20f),
                            )
                        )
                    ),
            )
        }
        content(this)
    }
}

@Composable
private fun NuvioPlayerOptionRail(
    options: List<TvPlayerOption>,
    width: Dp,
    maxHeight: Dp,
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
        delay(80)
        runCatching { requesters[index].requestFocus() }
    }

    if (options.isEmpty()) {
        Text(
            text = "Nothing available for this stream.",
            color = Color.White.copy(alpha = .58f),
            fontSize = 13.sp,
            modifier = Modifier.width(width),
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(width)
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
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
    val shape = RoundedCornerShape(10.dp)
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
                    option.selected && focused -> Color.White.copy(alpha = .18f)
                    option.selected -> Color.White.copy(alpha = .11f)
                    focused -> Color.White.copy(alpha = .12f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.Focus
                    option.selected -> Color.White.copy(alpha = .24f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
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
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(
                when {
                    focused && selected -> Color.White
                    selected -> Color(0xFFF5F5F5)
                    focused -> Color.White.copy(alpha = .14f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else if (selected) Color.Transparent else Color.White.copy(alpha = .14f),
                shape = shape,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (season == 0) "Specials" else "Season $season",
            color = if (selected) Color.Black else Color.White.copy(alpha = if (focused) .96f else .72f),
            fontSize = 12.sp,
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
        delay(80)
        runCatching { requesters[currentIndex].requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val shape = RoundedCornerShape(14.dp)
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
            .background(
                when {
                    focused -> Color.White.copy(alpha = .13f)
                    selected -> Color.White.copy(alpha = .05f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.Focus
                    selected -> Color.White.copy(alpha = .20f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(TvDesign.SurfaceRaised),
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
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .76f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "S${episode.season}E${episode.episode}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(TvDesign.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Current episode",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = episode.title.ifBlank { "Episode ${episode.episode}" },
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            formatEpisodeReleaseDate(episode.released)?.let { released ->
                Text(
                    text = released,
                    color = Color.White.copy(alpha = .45f),
                    fontSize = 10.sp,
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
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = if (focused) Color.Black else Color.White.copy(alpha = .82f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = .07f), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else Color.White.copy(alpha = .12f),
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun formatEpisodeReleaseDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val datePart = Regex("""\\d{4}-\\d{2}-\\d{2}""").find(value)?.value ?: return value
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val date = requireNotNull(parser.parse(datePart))
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(date)
    }.getOrDefault(datePart)
}
