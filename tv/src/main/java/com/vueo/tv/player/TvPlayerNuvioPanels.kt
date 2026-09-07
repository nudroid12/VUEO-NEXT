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
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay

private val FinalLeftOverlayWidth = 370.dp
private val FinalRightPanelWidth = 400.dp
private val FinalPanelShape = RoundedCornerShape(14.dp)

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

    NuvioPlayerOverlayScaffold(leftWeighted = true) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(FinalLeftOverlayWidth)
                .padding(start = 48.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            NuvioPlayerOptionList(
                options = options,
                maxHeight = 360.dp,
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
                .padding(top = 18.dp, bottom = 18.dp, end = 18.dp)
                .fillMaxHeight()
                .width(FinalRightPanelWidth)
                .clip(FinalPanelShape)
                .background(Color(0xFF111418).copy(alpha = .98f))
                .border(1.dp, Color.White.copy(alpha = .07f), FinalPanelShape)
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            NuvioPanelHeader(
                title = "Sources",
                subtitle = title,
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(16.dp))
            NuvioPlayerOptionList(
                options = options,
                maxHeight = 620.dp,
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
        episodes
            .map { it.season }
            .distinct()
            .sortedWith(compareBy<Int> { if (it == 0) 1 else 0 }.thenBy { it })
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
                .padding(top = 18.dp, bottom = 18.dp, end = 18.dp)
                .fillMaxHeight()
                .width(FinalRightPanelWidth)
                .clip(FinalPanelShape)
                .background(Color(0xFF111418).copy(alpha = .985f))
                .border(1.dp, Color.White.copy(alpha = .07f), FinalPanelShape)
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            NuvioPanelHeader(
                title = "Episodes",
                subtitle = mediaTitle,
                onDismiss = onDismiss,
            )

            if (seasons.size > 1) {
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    contentPadding = PaddingValues(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
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

            Spacer(Modifier.height(14.dp))
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
            .background(Color.Black.copy(alpha = .22f)),
    ) {
        if (leftWeighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(500.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = .92f),
                                Color.Black.copy(alpha = .68f),
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
        delay(45)
        runCatching { requesters[index].requestFocus() }
    }

    if (options.isEmpty()) {
        Text(
            text = "Nothing available for this stream.",
            color = Color.White.copy(alpha = .56f),
            fontSize = 12.sp,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
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
    val shape = RoundedCornerShape(8.dp)

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
                    focused -> Color.White.copy(alpha = .12f)
                    option.selected -> Color.White.copy(alpha = .055f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = .90f)
                    option.selected -> Color.White.copy(alpha = .13f)
                    else -> Color.White.copy(alpha = .06f)
                },
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                color = if (option.enabled) Color.White else Color.White.copy(alpha = .32f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    color = Color.White.copy(alpha = if (option.enabled) .48f else .24f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (option.selected) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(TvDesign.Accent, CircleShape),
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
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
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
                if (focused) Color.White else Color.White.copy(alpha = .11f),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (season == 0) "Specials" else "Season $season",
            color = if (focused) Color.Black else Color.White.copy(alpha = if (selected) .95f else .72f),
            fontSize = 10.sp,
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
            current.id == candidate.id ||
                (current.season == candidate.season && current.episode == candidate.episode)
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
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            val selected = currentEpisode?.let { current ->
                current.id == episode.id ||
                    (current.season == episode.season && current.episode == episode.episode)
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
    val shape = RoundedCornerShape(9.dp)

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
            .background(if (focused) Color.White.copy(alpha = .10f) else Color.Transparent, shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = .90f)
                    selected -> TvDesign.Accent.copy(alpha = .45f)
                    else -> Color.White.copy(alpha = .055f)
                },
                shape,
            )
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(106.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(6.dp))
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
                        .padding(5.dp)
                        .size(7.dp)
                        .background(TvDesign.Accent, CircleShape),
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "S${episode.season}E${episode.episode}",
                color = Color.White.copy(alpha = .46f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = episode.title.ifBlank { "Episode ${episode.episode}" },
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.released
                ?.takeIf { it.isNotBlank() }
                ?.let(::formatPlayerEpisodeDate)
                ?.let { released ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = released,
                        color = Color.White.copy(alpha = .38f),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun NuvioPanelHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = .50f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        NuvioPanelTextAction("Close", onDismiss)
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
        color = if (focused) Color.Black else Color.White.copy(alpha = .66f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = .055f), shape)
            .border(1.dp, Color.White.copy(alpha = .08f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun formatPlayerEpisodeDate(raw: String): String {
    val trimmed = raw.trim()
    val datePart = trimmed.take(10)
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(datePart)) return trimmed
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val date = requireNotNull(parser.parse(datePart))
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(date)
    }.getOrDefault(datePart)
}
