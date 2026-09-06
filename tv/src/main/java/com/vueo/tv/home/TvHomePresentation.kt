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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HERO_SETTLE_MS = 320L
private const val ROW_VIEWPORT_FRACTION = .51f
private const val HERO_MEDIA_WIDTH_FRACTION = .72f
private const val HERO_TEXT_WIDTH_FRACTION = .42f
private val HomeContentStart = 88.dp
private val LandscapeCardWidth = 250.dp
private val LandscapeCardHeight = 140.dp
private val PosterCardWidth = 146.dp
private val PosterCardHeight = 219.dp
private val HomeCardShape = RoundedCornerShape(10.dp)

private object VueoHomeFocusMemory {
    var lastTileKey: String? = null
    val rowIndices = mutableMapOf<String, Int>()
}

@Composable
internal fun VueoHomePresentation(
    rows: List<VueoHomeRow>,
    loading: Boolean,
    error: String?,
    onNavigate: (String) -> Unit,
    onOpenTile: (VueoHomeTile) -> Unit,
    onProfile: () -> Unit,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun tileRequester(key: String): FocusRequester =
        requesters.getOrPut(key) { FocusRequester() }

    val navRequesters = remember {
        VueoHomeDestinations.associate { it.label to FocusRequester() }
    }
    val profileRequester = remember { FocusRequester() }
    val verticalState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowIndices = remember {
        mutableStateMapOf<String, Int>().apply {
            putAll(VueoHomeFocusMemory.rowIndices)
        }
    }

    var sidebarExpanded by remember { mutableStateOf(false) }
    var lastFocusedKey by remember { mutableStateOf(VueoHomeFocusMemory.lastTileKey) }
    var pendingHeroTile by remember { mutableStateOf<VueoHomeTile?>(null) }
    var heroTile by remember { mutableStateOf<VueoHomeTile?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(rows) {
        if (rows.isEmpty()) return@LaunchedEffect
        val allTiles = rows.flatMap(VueoHomeRow::tiles)
        val restored = lastFocusedKey?.let { key -> allTiles.firstOrNull { it.key == key } }
        val target = restored ?: allTiles.firstOrNull() ?: return@LaunchedEffect

        if (heroTile == null) heroTile = target
        if (pendingHeroTile == null) pendingHeroTile = target
        lastFocusedKey = target.key
        VueoHomeFocusMemory.lastTileKey = target.key

        if (!initialFocusDone) {
            initialFocusDone = true
            val targetRowIndex = rows.indexOfFirst { row ->
                row.tiles.any { it.key == target.key }
            }.coerceAtLeast(0)
            verticalState.scrollToItem(targetRowIndex)
            delay(120)
            runCatching { tileRequester(target.key).requestFocus() }
        }
    }

    LaunchedEffect(pendingHeroTile?.key) {
        val next = pendingHeroTile ?: return@LaunchedEffect
        delay(HERO_SETTLE_MS)
        heroTile = next
    }

    fun restoreContentFocus(): Boolean {
        sidebarExpanded = false
        val targetKey = lastFocusedKey ?: rows.firstOrNull()?.tiles?.firstOrNull()?.key
        if (targetKey != null) {
            runCatching { tileRequester(targetKey).requestFocus() }
            return true
        }
        return false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowViewportHeight = maxHeight * ROW_VIEWPORT_FRACTION
            val heroMediaHeight = (maxHeight - rowViewportHeight + 42.dp)
                .coerceAtMost(maxHeight)

            VueoHeroArtwork(
                media = heroTile?.media,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(HERO_MEDIA_WIDTH_FRACTION)
                    .height(heroMediaHeight),
            )
            VueoHeroScrim()

            VueoHeroMetadata(
                tile = heroTile,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = HomeContentStart,
                        end = 28.dp,
                        bottom = rowViewportHeight + 16.dp,
                    )
                    .fillMaxWidth(HERO_TEXT_WIDTH_FRACTION),
            )

            if (rows.isNotEmpty()) {
                VueoHomeRows(
                    rows = rows,
                    verticalState = verticalState,
                    rowViewportHeight = rowViewportHeight,
                    rowIndices = rowIndices,
                    tileRequester = ::tileRequester,
                    onTileFocused = { rowId, index, tile ->
                        sidebarExpanded = false
                        rowIndices[rowId] = index
                        VueoHomeFocusMemory.rowIndices[rowId] = index
                        lastFocusedKey = tile.key
                        VueoHomeFocusMemory.lastTileKey = tile.key
                        pendingHeroTile = tile
                    },
                    onOpenSidebar = {
                        sidebarExpanded = true
                        runCatching { navRequesters.getValue("Home").requestFocus() }
                    },
                    onOpenTile = onOpenTile,
                    onMoveVertically = { fromRowIndex, tileIndex, delta ->
                        val targetRowIndex = (fromRowIndex + delta).coerceIn(0, rows.lastIndex)
                        if (targetRowIndex != fromRowIndex) {
                            val targetRow = rows[targetRowIndex]
                            val remembered = rowIndices[targetRow.id] ?: tileIndex
                            val targetIndex = remembered.coerceIn(0, targetRow.tiles.lastIndex)
                            val target = targetRow.tiles[targetIndex]
                            rowIndices[targetRow.id] = targetIndex
                            VueoHomeFocusMemory.rowIndices[targetRow.id] = targetIndex
                            scope.launch {
                                verticalState.animateScrollToItem(targetRowIndex)
                                delay(80)
                                runCatching { tileRequester(target.key).requestFocus() }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(rowViewportHeight),
                )
            } else {
                VueoHomeStatus(
                    loading = loading,
                    error = error,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = HomeContentStart, bottom = rowViewportHeight * .58f),
                )
            }
        }

        VueoHomeSidebar(
            expanded = sidebarExpanded,
            selected = "Home",
            requesters = navRequesters,
            profileRequester = profileRequester,
            onExpanded = { sidebarExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = ::restoreContentFocus,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun VueoHeroArtwork(
    media: MediaItem?,
    modifier: Modifier = Modifier,
) {
    val url = media?.background ?: media?.poster
    AnimatedContent(
        targetState = url,
        transitionSpec = {
            fadeIn(tween(360)) togetherWith fadeOut(tween(240))
        },
        label = "vueoHeroArtwork",
        modifier = modifier,
    ) { imageUrl ->
        TvNetworkImage(
            url = imageUrl,
            contentDescription = media?.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )
    }
}

@Composable
private fun VueoHeroScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to TvDesign.Black,
                    .22f to TvDesign.Black.copy(alpha = .96f),
                    .43f to TvDesign.Black.copy(alpha = .77f),
                    .62f to TvDesign.Black.copy(alpha = .28f),
                    1f to Color.Transparent,
                )
            )
            .background(
                Brush.verticalGradient(
                    0f to TvDesign.Black.copy(alpha = .12f),
                    .39f to Color.Transparent,
                    .57f to TvDesign.Black.copy(alpha = .08f),
                    .73f to TvDesign.Black.copy(alpha = .64f),
                    1f to TvDesign.Black,
                )
            ),
    )
}

@Composable
private fun VueoHeroMetadata(
    tile: VueoHomeTile?,
    modifier: Modifier = Modifier,
) {
    val media = tile?.media ?: return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val meta = vueoHeroMeta(tile)
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = TvDesign.White.copy(alpha = .78f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        media.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .74f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VueoHomeRows(
    rows: List<VueoHomeRow>,
    verticalState: LazyListState,
    rowViewportHeight: androidx.compose.ui.unit.Dp,
    rowIndices: Map<String, Int>,
    tileRequester: (String) -> FocusRequester,
    onTileFocused: (String, Int, VueoHomeTile) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenTile: (VueoHomeTile) -> Unit,
    onMoveVertically: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = verticalState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = HomeContentStart,
            end = 30.dp,
            bottom = rowViewportHeight * .32f,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.id },
        ) { rowIndex, row ->
            VueoHomeRowContent(
                row = row,
                rowIndex = rowIndex,
                rememberedIndex = rowIndices[row.id] ?: 0,
                tileRequester = tileRequester,
                onTileFocused = onTileFocused,
                onOpenSidebar = onOpenSidebar,
                onOpenTile = onOpenTile,
                onMoveVertically = onMoveVertically,
            )
        }
    }
}

@Composable
private fun VueoHomeRowContent(
    row: VueoHomeRow,
    rowIndex: Int,
    rememberedIndex: Int,
    tileRequester: (String) -> FocusRequester,
    onTileFocused: (String, Int, VueoHomeTile) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenTile: (VueoHomeTile) -> Unit,
    onMoveVertically: (Int, Int, Int) -> Unit,
) {
    val horizontalState = rememberLazyListState(
        initialFirstVisibleItemIndex = rememberedIndex.coerceIn(0, row.tiles.lastIndex),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        LazyRow(
            state = horizontalState,
            horizontalArrangement = Arrangement.spacedBy(if (row.landscape) 14.dp else 13.dp),
            contentPadding = PaddingValues(end = 30.dp),
        ) {
            itemsIndexed(
                items = row.tiles,
                key = { _, tile -> tile.key },
            ) { index, tile ->
                VueoHomeCard(
                    tile = tile,
                    landscape = row.landscape,
                    requester = tileRequester(tile.key),
                    onFocused = { onTileFocused(row.id, index, tile) },
                    onOpen = { onOpenTile(tile) },
                    onKey = { keyCode ->
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (index == 0) {
                                    onOpenSidebar()
                                    true
                                } else {
                                    false
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_UP -> {
                                onMoveVertically(rowIndex, index, -1)
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                onMoveVertically(rowIndex, index, 1)
                                true
                            }

                            else -> false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VueoHomeCard(
    tile: VueoHomeTile,
    landscape: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onKey: (Int) -> Boolean,
) {
    var focused by remember(tile.key) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(if (focused) 120 else 90),
        label = "vueoHomeCardScale",
    )
    val width = if (landscape) LandscapeCardWidth else PosterCardWidth
    val height = if (landscape) LandscapeCardHeight else PosterCardHeight
    val imageUrl = if (landscape) {
        tile.media.background ?: tile.media.poster
    } else {
        tile.media.poster ?: tile.media.background
    }
    val progress = (tile as? VueoHomeTile.Resume)?.playback?.progressFraction

    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .scale(scale)
                .clip(HomeCardShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvDesign.White.copy(alpha = .96f)
                    else TvDesign.White.copy(alpha = .07f),
                    shape = HomeCardShape,
                )
                .focusRequester(requester)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        onKey(event.nativeKeyEvent.keyCode)
                    } else {
                        false
                    }
                }
                .clickable(onClick = onOpen)
                .focusable(),
        ) {
            TvNetworkImage(
                url = imageUrl,
                contentDescription = tile.media.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            if (landscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .55f to Color.Transparent,
                                1f to TvDesign.Black.copy(alpha = .80f),
                            )
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val playback = (tile as? VueoHomeTile.Resume)?.playback
                    if (playback?.season != null && playback.episode != null) {
                        Text(
                            text = "S${playback.season} E${playback.episode}",
                            color = TvDesign.White.copy(alpha = .78f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = tile.media.name,
                        color = TvDesign.White,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = .55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(TvDesign.White.copy(alpha = .94f)),
                    )
                }
            }
        }

        if (!landscape) {
            Text(
                text = tile.media.name,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .68f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun VueoHomeStatus(
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.width(20.dp).height(20.dp),
                color = TvDesign.White,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = when {
                loading -> "Loading Home"
                !error.isNullOrBlank() -> error.orEmpty()
                else -> "Nothing to show yet"
            },
            color = TvDesign.Muted,
            fontSize = 13.sp,
        )
    }
}

private fun vueoHeroMeta(tile: VueoHomeTile?): String {
    val media = tile?.media ?: return ""
    val resume = (tile as? VueoHomeTile.Resume)?.playback

    return buildList {
        if (resume?.season != null && resume.episode != null) {
            add("S${resume.season} E${resume.episode}")
            resume.episodeTitle?.takeIf { it.isNotBlank() }?.let(::add)
        }
        media.displayType.takeIf { it.isNotBlank() }?.let(::add)
        media.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        media.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        media.imdbRating?.let { add("IMDb %.1f".format(it)) }
        if (resume != null && resume.durationMs > resume.positionMs) {
            val minutesLeft = ((resume.durationMs - resume.positionMs) / 60_000L).coerceAtLeast(1L)
            add("${minutesLeft}m left")
        } else {
            media.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}m") }
        }
    }.joinToString("  •  ")
}
