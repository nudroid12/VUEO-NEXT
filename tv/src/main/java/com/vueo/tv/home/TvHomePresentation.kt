package com.vueo.tv.home

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val HERO_MEDIA_WIDTH_FRACTION = .72f
private const val HERO_TEXT_WIDTH_FRACTION = .42f
private const val ROWS_VIEWPORT_FRACTION = .52f
private const val HERO_SETTLE_MS = 180L

private val HomeStartPadding = 94.dp
private val HomeEndPadding = 34.dp
private val ContinueWidth = 258.dp
private val ContinueHeight = 145.dp
private val PosterWidth = 132.dp
private val PosterHeight = 198.dp
private val ContinueShape = RoundedCornerShape(12.dp)
private val PosterShape = RoundedCornerShape(10.dp)

@Composable
internal fun TvHomePresentation(
    rows: List<TvHomeRow>,
    loading: Boolean,
    error: String?,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenEntry: (TvHomeEntry) -> Unit,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    val navRequesters = remember {
        listOf("Home", "Search", "Library", "Settings")
            .associateWith { FocusRequester() }
    }
    val profileRequester = remember { FocusRequester() }
    val rowIndices = remember {
        mutableStateMapOf<String, Int>().apply {
            putAll(TvHomeMemory.itemIndexByRow)
        }
    }
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var activeRowIndex by remember { mutableStateOf(0) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingHero by remember { mutableStateOf<TvHomeEntry?>(null) }
    var heroEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(rows) {
        if (rows.isEmpty()) return@LaunchedEffect

        val savedRowIndex = TvHomeMemory.activeRowKey
            ?.let { key -> rows.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 }
            ?: 0
        activeRowIndex = savedRowIndex.coerceIn(0, rows.lastIndex)

        val row = rows[activeRowIndex]
        val itemIndex = (rowIndices[row.key] ?: 0).coerceIn(0, row.entries.lastIndex)
        val entry = row.entries[itemIndex]

        if (heroEntry == null) heroEntry = entry
        pendingHero = entry
        focusedKey = entry.key

        if (!initialFocusDone) {
            initialFocusDone = true
            pendingFocus = row.key to itemIndex
            columnState.scrollToItem(activeRowIndex)
        }
    }

    LaunchedEffect(pendingHero) {
        val next = pendingHero ?: return@LaunchedEffect
        delay(HERO_SETTLE_MS)
        heroEntry = next
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        val rowsViewportHeight = maxHeight * ROWS_VIEWPORT_FRACTION
        val heroHeight = (maxHeight - rowsViewportHeight + 42.dp).coerceAtMost(maxHeight * .55f)

        HomeHeroStage(
            entry = heroEntry,
            heroHeight = heroHeight,
            modifier = Modifier.fillMaxSize(),
        )

        if (loading && rows.isEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = HomeStartPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp),
                    strokeWidth = 2.dp,
                    color = TvDesign.White,
                )
                Text("Loading Home", color = TvDesign.Muted, fontSize = 13.sp)
            }
        }

        if (!loading && rows.isEmpty() && error != null) {
            Text(
                text = error ?: "Unable to load Home",
                color = TvDesign.Muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = HomeStartPadding),
            )
        }

        if (rows.isNotEmpty()) {
            LazyColumn(
                state = columnState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(rowsViewportHeight),
                contentPadding = PaddingValues(
                    start = HomeStartPadding,
                    end = HomeEndPadding,
                    bottom = rowsViewportHeight * .70f,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.key },
                ) { rowIndex, row ->
                    TvHomeRowStrip(
                        row = row,
                        rowIndex = rowIndex,
                        requester = ::requester,
                        savedIndex = rowIndices[row.key] ?: 0,
                        pendingFocus = pendingFocus,
                        onPendingFocusConsumed = {
                            if (pendingFocus?.first == row.key) pendingFocus = null
                        },
                        onFocused = { entry, itemIndex ->
                            activeRowIndex = rowIndex
                            focusedKey = entry.key
                            rowIndices[row.key] = itemIndex
                            TvHomeMemory.activeRowKey = row.key
                            TvHomeMemory.itemIndexByRow[row.key] = itemIndex
                            pendingHero = entry
                        },
                        onMoveVertical = { direction, sourceItemIndex ->
                            val targetRowIndex = (rowIndex + direction).coerceIn(0, rows.lastIndex)
                            if (targetRowIndex == rowIndex) {
                                true
                            } else {
                                val targetRow = rows[targetRowIndex]
                                val remembered = rowIndices[targetRow.key] ?: sourceItemIndex
                                val targetIndex = remembered.coerceIn(0, targetRow.entries.lastIndex)
                                activeRowIndex = targetRowIndex
                                TvHomeMemory.activeRowKey = targetRow.key
                                pendingFocus = targetRow.key to targetIndex
                                scope.launch { columnState.animateScrollToItem(targetRowIndex) }
                                true
                            }
                        },
                        onOpenSidebar = {
                            runCatching { navRequesters.getValue("Home").requestFocus() }
                        },
                        onOpen = onOpenEntry,
                    )
                }
            }
        }

        TvHomeSidebar(
            selected = "Home",
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                val target = focusedKey
                if (target == null) {
                    false
                } else {
                    runCatching { requester(target).requestFocus() }.isSuccess
                }
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun HomeHeroStage(
    entry: TvHomeEntry?,
    heroHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val media = entry?.media
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = media?.background ?: media?.poster,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(220))
            },
            label = "homeHeroBackdrop",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(HERO_MEDIA_WIDTH_FRACTION)
                .height(heroHeight),
        ) { imageUrl ->
            TvNetworkImage(
                url = imageUrl,
                contentDescription = media?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.Black,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.horizontalGradient(
                        0f to TvDesign.Black,
                        .28f to TvDesign.Black.copy(alpha = .96f),
                        .47f to TvDesign.Black.copy(alpha = .73f),
                        .66f to TvDesign.Black.copy(alpha = .22f),
                        1f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to TvDesign.Black.copy(alpha = .12f),
                        .60f to Color.Transparent,
                        .83f to TvDesign.Black.copy(alpha = .60f),
                        1f to TvDesign.Black,
                    )
                ),
        )

        if (entry != null) {
            HomeHeroText(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth(HERO_TEXT_WIDTH_FRACTION)
                    .padding(start = HomeStartPadding, top = 68.dp),
            )
        }
    }
}

@Composable
private fun HomeHeroText(
    entry: TvHomeEntry,
    modifier: Modifier = Modifier,
) {
    val media = entry.media
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val primary = heroPrimaryMeta(entry)
        if (primary.isNotBlank()) {
            Text(
                text = primary,
                color = TvDesign.White.copy(alpha = .82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val secondary = heroSecondaryMeta(entry)
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        media.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvHomeRowStrip(
    row: TvHomeRow,
    rowIndex: Int,
    requester: (String) -> FocusRequester,
    savedIndex: Int,
    pendingFocus: Pair<String, Int>?,
    onPendingFocusConsumed: () -> Unit,
    onFocused: (TvHomeEntry, Int) -> Unit,
    onMoveVertical: (Int, Int) -> Boolean,
    onOpenSidebar: () -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
) {
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedIndex.coerceIn(0, row.entries.lastIndex),
    )

    LaunchedEffect(pendingFocus) {
        val pending = pendingFocus ?: return@LaunchedEffect
        if (pending.first != row.key) return@LaunchedEffect
        val targetIndex = pending.second.coerceIn(0, row.entries.lastIndex)
        rowState.scrollToItem(targetIndex)
        delay(35)
        runCatching { requester(row.entries[targetIndex].key).requestFocus() }
        onPendingFocusConsumed()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .94f),
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(
                if (row.style == TvHomeRowStyle.CONTINUE) 14.dp else 12.dp
            ),
            contentPadding = PaddingValues(end = HomeEndPadding),
        ) {
            itemsIndexed(
                items = row.entries,
                key = { _, entry -> entry.key },
            ) { index, entry ->
                TvHomeCard(
                    entry = entry,
                    style = row.style,
                    requester = requester(entry.key),
                    onFocused = { onFocused(entry, index) },
                    onOpen = { onOpen(entry) },
                    onArrow = { keyCode ->
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (index == 0) {
                                    onOpenSidebar()
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> onMoveVertical(-1, index)
                            KeyEvent.KEYCODE_DPAD_DOWN -> onMoveVertical(1, index)
                            else -> false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TvHomeCard(
    entry: TvHomeEntry,
    style: TvHomeRowStyle,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onArrow: (Int) -> Boolean,
) {
    var focused by remember(entry.key) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(if (focused) 125 else 95),
        label = "homeFreshCardScale",
    )

    val isContinue = style == TvHomeRowStyle.CONTINUE
    val width = if (isContinue) ContinueWidth else PosterWidth
    val height = if (isContinue) ContinueHeight else PosterHeight
    val shape = if (isContinue) ContinueShape else PosterShape
    val imageUrl = if (isContinue) {
        entry.media.background ?: entry.media.poster
    } else {
        entry.media.poster ?: entry.media.background
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    TvDesign.White.copy(alpha = .94f)
                } else {
                    TvDesign.White.copy(alpha = .07f)
                },
                shape = shape,
            )
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onArrow(event.nativeKeyEvent.keyCode)
                } else {
                    false
                }
            }
            .clickable(onClick = onOpen)
            .focusable(),
    ) {
        TvNetworkImage(
            url = imageUrl,
            contentDescription = entry.media.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (isContinue) {
            ContinueWatchingOverlay(entry)
        }
    }
}

@Composable
private fun ContinueWatchingOverlay(entry: TvHomeEntry) {
    val resume = entry as? TvHomeEntry.Resume
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .48f to Color.Transparent,
                    1f to TvDesign.Black.copy(alpha = .88f),
                )
            ),
    ) {
        resume?.let { value ->
            val remaining = remainingLabel(value.playback)
            if (remaining.isNotBlank()) {
                Text(
                    text = remaining,
                    color = TvDesign.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            TvDesign.Black.copy(alpha = .70f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            resume?.let { value ->
                val episode = episodeLabel(value.playback)
                if (episode.isNotBlank()) {
                    Text(
                        text = episode,
                        color = TvDesign.White.copy(alpha = .78f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Text(
                text = entry.media.name,
                color = TvDesign.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            resume?.let { value ->
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            TvDesign.White.copy(alpha = .24f),
                            RoundedCornerShape(2.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.playback.progressFraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(
                                TvDesign.Accent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}

private fun heroPrimaryMeta(entry: TvHomeEntry): String {
    val media = entry.media
    return buildList {
        val resume = entry as? TvHomeEntry.Resume
        if (resume != null) {
            episodeLabel(resume.playback).takeIf { it.isNotBlank() }?.let(::add)
        } else {
            media.displayType.takeIf { it.isNotBlank() }?.let(::add)
        }
        media.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        media.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString("  •  ")
}

private fun heroSecondaryMeta(entry: TvHomeEntry): String {
    val media = entry.media
    return buildList {
        val resume = entry as? TvHomeEntry.Resume
        if (resume != null) {
            remainingLabel(resume.playback).takeIf { it.isNotBlank() }?.let(::add)
        } else {
            runtimeLabel(media.runtimeMinutes)?.let(::add)
        }
        media.imdbRating?.takeIf { it > 0.0 }?.let { add("IMDb ${"%.1f".format(it)}") }
        media.certification?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString("  •  ")
}

private fun episodeLabel(playback: LibraryPlaybackEntry): String {
    val season = playback.season
    val episode = playback.episode
    return when {
        season != null && episode != null -> "S$season E$episode"
        !playback.episodeTitle.isNullOrBlank() -> playback.episodeTitle.orEmpty()
        else -> ""
    }
}

private fun remainingLabel(playback: LibraryPlaybackEntry): String {
    if (playback.durationMs <= 0L) return ""
    val remainingMs = (playback.durationMs - playback.positionMs).coerceAtLeast(0L)
    val minutes = ceil(remainingMs / 60_000.0).toInt()
    return when {
        minutes <= 0 -> ""
        minutes < 60 -> "${minutes}m left"
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "${hours}h left" else "${hours}h ${mins}m left"
        }
    }
}

private fun runtimeLabel(minutes: Int?): String? {
    val value = minutes?.takeIf { it > 0 } ?: return null
    return if (value < 60) {
        "${value}m"
    } else {
        val hours = value / 60
        val mins = value % 60
        if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }
}
