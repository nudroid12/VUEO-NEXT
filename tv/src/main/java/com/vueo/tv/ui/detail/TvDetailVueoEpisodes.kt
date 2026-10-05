package com.vueo.tv.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Border
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
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.runtime.withFrameNanos
import com.vueo.tv.ui.rememberTvEpisodeRanges
import com.vueo.tv.ui.TvEpisodeRangeControls
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val VueoEpisodeShape = RoundedCornerShape(14.dp)
private const val VueoEpisodeScrollRepeatThrottleMs = 80L

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
            itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                var focused by remember(season) { mutableStateOf(false) }
                val selected = season == selectedSeason
                val shape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .focusRequester(requesters.getValue(season))
                        .focusProperties {
                            up = upRequester
                            downRequester?.let { down = it }
                            if (index == seasons.lastIndex) right = FocusRequester.Cancel
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .clip(shape)
                        .background(
                            if (selected) TvDesign.White else TvDesign.Surface.copy(alpha = .82f)
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
                        color = if (selected) Color.Black else TvDesign.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

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
    onToolbarFocus: (Boolean) -> Unit,
) {
    val initialId = VueoDetailFocusMemory.episodeId?.takeIf { id -> episodes.any { it.id == id } } ?: selectedEpisode?.id
    val ranges = rememberTvEpisodeRanges(episodes, initialId)
    val rangeRequester = remember { FocusRequester() }
    Column {
        TvEpisodeRangeControls(ranges, rangeRequester, upRequester,
            onDown = { ranges.focusCards = true; ranges.focusRequest++ },
            modifier = Modifier.padding(horizontal = VueoDetailHorizontalPadding),
            onFocused = { onToolbarFocus(true) })
            VueoDetailEpisodeCards(media, ranges.visible, episodeRatings, selectedEpisode, history,
                sectionRequester, if (ranges.enabled) rangeRequester else upRequester, downRequester,
                onFocused = { onToolbarFocus(false); onFocused(it) }, onOpen = onOpen,
                targetEpisodeId = if (ranges.enabled) ranges.targetId else null,
                focusRequest = ranges.focusRequest, focusCards = ranges.focusCards,
                onRangeUp = if (ranges.enabled) ({ runCatching { rangeRequester.requestFocus() }; Unit }) else null,
                onBoundary = { forward ->
                    val next = ranges.group + if (forward) 1 else -1
                    if (ranges.enabled && next in ranges.groups.indices) {
                        ranges.select(next, focusCards = true, last = !forward); true
                    } else false
                })
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun VueoDetailEpisodeCards(
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
    targetEpisodeId: String? = null,
    focusRequest: Int = 0,
    focusCards: Boolean = false,
    onBoundary: ((Boolean) -> Boolean)? = null,
    onRangeUp: (() -> Unit)? = null,
) {
    val progressByEpisode = remember(media.id, media.type, history) {
        history.filter { it.media.id == media.id && it.media.type == media.type }
            .groupBy { it.season to it.episode }
            .mapValues { (_, entries) -> entries.first() }
    }
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
    val focusId = targetEpisodeId ?: rememberedId ?: selectedEpisode?.id?.takeIf { id -> episodes.any { it.id == id } } ?: episodes.firstOrNull()?.id
    val selectedIndex = episodes.indexOfFirst { it.id == focusId }.coerceAtLeast(0)
    val rowPrefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 2) }
    val listState = androidx.compose.runtime.key(episodes.firstOrNull()?.id) {
        rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex, prefetchStrategy = rowPrefetchStrategy)
    }
    var lastHorizontalKeyRepeatTime by remember { mutableStateOf(0L) }
    val requesters = remember(media.id, media.type, episodes.map(EpisodeItem::id)) {
        episodes.associate { episode ->
            episode.id to FocusRequester()
        }
    }

    LaunchedEffect(episodes.firstOrNull()?.id) {
        if (episodes.isNotEmpty()) listState.scrollToItem(selectedIndex)
    }

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0 && focusCards && episodes.isNotEmpty()) {
            listState.scrollToItem(selectedIndex)
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (requesters[focusId]?.let { runCatching { it.requestFocus() }.getOrDefault(false) } == true) break
            }
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
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (onRangeUp != null && native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) {
                                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) onRangeUp()
                                return@onPreviewKeyEvent true
                            }
                            val isHorizontalKey = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                                native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                            if (
                                isHorizontalKey &&
                                native.action == AndroidKeyEvent.ACTION_DOWN &&
                                native.repeatCount > 0
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastHorizontalKeyRepeatTime < VueoEpisodeScrollRepeatThrottleMs) {
                                    return@onPreviewKeyEvent true
                                }
                                lastHorizontalKeyRepeatTime = now
                            }
                            false
                        }
                        .focusRequester(sectionRequester)
                        .focusRestorer { requesters[focusId] ?: FocusRequester.Default }
                        .focusGroup(),
                    contentPadding = PaddingValues(end = 18.dp, top = 7.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(episodes, key = { _, episode -> episode.id }, contentType = { _, _ -> "episode-card" }) { index, episode ->
                        val progress = progressByEpisode[episode.season to episode.episode]
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
                            onBoundary = { forward ->
                                if ((forward && index == episodes.lastIndex) || (!forward && index == 0)) onBoundary?.invoke(forward) == true else false
                            },
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
    onBoundary: (Boolean) -> Boolean,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            onClick = onOpen,
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0 &&
                        (native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT || native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT)) {
                        onBoundary(native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
                    } else false
                }
                .focusRequester(requester)
                .focusProperties {
                    up = upRequester
                    downRequester?.let { down = it }
                }
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) onFocused()
                    focused = state.isFocused
                },
            shape = CardDefaults.shape(shape = VueoEpisodeShape),
            colors = CardDefaults.colors(
                containerColor = TvDesign.SurfaceRaised,
                focusedContainerColor = TvDesign.SurfaceRaised,
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(if (selected) 1.dp else 0.dp,
                        if (selected) TvDesign.White.copy(alpha = .30f) else Color.Transparent),
                    shape = VueoEpisodeShape,
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, TvDesign.Focus),
                    shape = VueoEpisodeShape,
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f),
            glow = CardDefaults.glow(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TvNetworkImage(
                    url = episode.thumbnail,
                    fadeEnabled = false,
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
                    val released = remember(episode.released) { vueoDetailFormatReleaseDate(episode.released) }
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
