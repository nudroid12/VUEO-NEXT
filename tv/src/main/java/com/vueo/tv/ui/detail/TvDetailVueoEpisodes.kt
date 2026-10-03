package com.vueo.tv.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val VueoEpisodeShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun VueoDetailSeasonTabs(
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
    val requesters = remember(seasons) {
        seasons.associateWith { FocusRequester() }
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
                .focusRequester(sectionRequester)
                .focusRestorer { requesters[focusSeason] ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = VueoDetailHorizontalPadding, vertical = 7.dp),
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
internal fun VueoDetailEpisodes(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    selectedEpisode: EpisodeItem?,
    history: List<LibraryPlaybackEntry>,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: (EpisodeItem) -> Unit,
    onOpen: (EpisodeItem) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // Same leading-edge reveal rule as Modern Home. The viewport already
    // excludes the left margin, so its leading target is zero. LazyRow clamps
    // at its natural end; only then does focus advance across the last cards.
    val horizontalReveal = remember(layoutDirection) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (containerSize <= 0f || size <= 0f) return 0f
                val childSize = abs(size)
                return if (rtl) {
                    val initialTarget = containerSize
                    val target = if (childSize <= containerSize && initialTarget < childSize) childSize else initialTarget
                    (offset + size) - target
                } else {
                    val initialTarget = 0f
                    val available = containerSize - initialTarget
                    val target = if (childSize <= containerSize && available < childSize) containerSize - childSize else initialTarget
                    offset - target
                }
            }
        }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val cardWidth = when { screenWidth >= 1300 -> 400.dp; screenWidth >= 1000 -> 360.dp; screenWidth >= 760 -> 320.dp; else -> 280.dp }
    val cardHeight = when { screenWidth >= 1300 -> 263.dp; screenWidth >= 1000 -> 235.dp; screenWidth >= 760 -> 207.dp; else -> 179.dp }
    val rememberedId = VueoDetailFocusMemory.episodeId
        ?.takeIf { id -> episodes.any { it.id == id } }
    val focusId = rememberedId ?: selectedEpisode?.id ?: episodes.firstOrNull()?.id
    val selectedIndex = episodes.indexOfFirst { it.id == focusId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val requesters = remember(media.id, media.type, episodes.map(EpisodeItem::id)) {
        episodes.associate { episode ->
            episode.id to FocusRequester()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalReveal) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = VueoDetailHorizontalPadding)
                    .clipToBounds(),
            ) {
            LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(sectionRequester)
                        .focusRestorer { requesters[focusId] ?: FocusRequester.Default }
                        .focusGroup(),
                    contentPadding = PaddingValues(end = VueoDetailHorizontalPadding, top = 7.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(episodes, key = { _, episode -> episode.id }) { _, episode ->
                        val progress = history.firstOrNull { entry ->
                            entry.media.id == media.id &&
                                entry.media.type == media.type &&
                                entry.season == episode.season &&
                                entry.episode == episode.episode
                        }
                        VueoEpisodeCard(
                            episode = episode,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            runtimeMinutes = media.runtimeMinutes,
                            imdbRating = episodeRatings[episode.season to episode.episode],
                            progress = progress,
                            selected = selectedEpisode?.id == episode.id,
                            requester = requesters.getValue(episode.id),
                            upRequester = upRequester,
                            downRequester = downRequester,
                            onFocused = {
                                VueoDetailFocusMemory.selectedSeason = episode.season
                                VueoDetailFocusMemory.episodeId = episode.id
                                onFocused(episode)
                            },
                            onOpen = { onOpen(episode) },
                        )
                    }
                }
                }
            }
    }
}

@Composable
private fun VueoEpisodeCard(
    episode: EpisodeItem,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    runtimeMinutes: Int?,
    imdbRating: Double?,
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
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .focusRequester(requester)
                .focusProperties {
                    up = upRequester
                    downRequester?.let { down = it }
                }
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) onFocused()
                    focused = state.isFocused
                }
                .clip(VueoEpisodeShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                    color = when {
                        focused -> TvDesign.Focus
                        selected -> TvDesign.White.copy(alpha = .30f)
                        else -> Color.Transparent
                    },
                    shape = VueoEpisodeShape,
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
                            .20f to TvDesign.Black.copy(alpha = .04f),
                            .38f to TvDesign.Black.copy(alpha = .26f),
                            .60f to TvDesign.Black.copy(alpha = .60f),
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
                    text = "EPISODE ${episode.episode}",
                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                        .background(TvDesign.Black.copy(alpha = .60f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
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
                episode.overview?.trim()?.takeIf(String::isNotBlank)?.let { synopsis ->
                    Text(
                        text = synopsis,
                        color = TvDesign.White.copy(alpha = .88f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = if (cardHeight >= 235.dp) 4 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val runtime = progress?.durationMs?.takeIf { it > 0L }?.let { (it / 60_000L).toInt() }
                    ?: runtimeMinutes
                val released = vueoDetailFormatReleaseDate(episode.released)
                if (runtime != null || released != null || imdbRating != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        runtime?.takeIf { it > 0 }?.let {
                            Text("${it}m", color = TvDesign.White.copy(alpha = .76f), fontSize = 10.sp)
                        }
                        imdbRating?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }?.let { score ->
                            Spacer(Modifier.width(9.dp))
                            Text("IMDb", color = TvDesign.White.copy(alpha = .76f), fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(String.format(Locale.US, "%.1f", score), color = Color(0xFFF5C518), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        released?.let {
                            Text(it, color = TvDesign.White.copy(alpha = .76f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }

            progress?.takeIf { !it.isCompleted && it.durationMs > 0L && it.positionMs > 0L }?.let { entry ->
                LinearProgressIndicator(
                    progress = { entry.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TvDesign.White,
                    trackColor = TvDesign.White.copy(alpha = .20f),
                )
            }
            val watched = progress?.isCompleted == true
            val notStarted = !watched && (progress == null || progress.positionMs <= 0L)
            if (watched) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .size(24.dp).background(TvDesign.Focus, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Watched",
                        tint = Color.Black, modifier = Modifier.size(17.dp))
                }
            } else if (notStarted) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp)) {
                    drawCircle(
                        color = TvDesign.White.copy(alpha = .80f),
                        style = Stroke(width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))),
                    )
                }
            }
        }

    }
}


private fun vueoDetailFormatReleaseDate(raw: String?): String? {
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
