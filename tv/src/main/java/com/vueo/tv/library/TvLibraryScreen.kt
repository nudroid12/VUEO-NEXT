package com.vueo.tv.library

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private const val LIBRARY_UI_PREFS = "vueo_library_ui"
private const val LIBRARY_GRID_VIEW_KEY = "grid_view"
private const val LIBRARY_HEADER_ITEMS = 2
private const val LIBRARY_TARGET_POSTER_WIDTH_DP = 126f
private const val LIBRARY_GRID_GAP_DP = 16f

/**
 * TV-only ephemeral focus/scroll memory.
 *
 * This deliberately stores no library data. My List continues to come from the
 * shared LibraryStore used by Mobile. The memory only lets the manual TV route
 * switch return to the same Library item after Detail without creating another
 * source of truth.
 */
private object TvLibraryFocusMemory {
    var lastTarget: String = "my-list"
    var lastMediaKey: String? = null
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun TvLibraryScreen(
    runtime: TvRuntime,
    refreshToken: Int,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Mobile parity: Library's visible content is My List. Continue Watching
    // and History remain shared LibraryStore data, but they are not Library UI
    // sections here.
    val watchlist = remember(refreshToken) { runtime.libraryStore.watchlist() }
    val context = LocalContext.current
    val libraryUiPreferences = remember {
        context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
    }

    var cloudSelected by remember { mutableStateOf(false) }
    var gridView by remember {
        mutableStateOf(libraryUiPreferences.getBoolean(LIBRARY_GRID_VIEW_KEY, true))
    }

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val myListRequester = remember { FocusRequester() }
    val cloudRequester = remember { FocusRequester() }
    val viewModeRequester = remember { FocusRequester() }

    val mediaKeys = remember(watchlist) {
        watchlist.map(::libraryMediaKey)
    }
    val mediaRequesters = remember(mediaKeys) {
        mediaKeys.associateWith { FocusRequester() }
    }

    val initialMaxIndex = if (watchlist.isEmpty()) {
        LIBRARY_HEADER_ITEMS
    } else {
        watchlist.size + LIBRARY_HEADER_ITEMS - 1
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex =
            TvLibraryFocusMemory.firstVisibleItemIndex.coerceIn(0, initialMaxIndex),
        initialFirstVisibleItemScrollOffset =
            TvLibraryFocusMemory.firstVisibleItemScrollOffset.coerceAtLeast(0),
    )

    var navExpanded by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableStateOf(TvLibraryFocusMemory.lastTarget) }
    var lastMediaKey by remember { mutableStateOf(TvLibraryFocusMemory.lastMediaKey) }

    fun rememberTarget(target: String, mediaKey: String? = lastMediaKey) {
        lastTarget = target
        lastMediaKey = mediaKey
        TvLibraryFocusMemory.lastTarget = target
        TvLibraryFocusMemory.lastMediaKey = mediaKey
    }

    fun focusSidebar() {
        navExpanded = true
        runCatching { navRequesters.getValue("Library").requestFocus() }
    }

    fun requestSelectedTabFocus(): Boolean =
        runCatching {
            if (cloudSelected) cloudRequester.requestFocus() else myListRequester.requestFocus()
            true
        }.getOrDefault(false)

    fun requestFirstMediaFocus(): Boolean {
        if (cloudSelected || watchlist.isEmpty()) return false
        val key = mediaKeys.firstOrNull() ?: return false
        return runCatching {
            mediaRequesters.getValue(key).requestFocus()
            true
        }.getOrDefault(false)
    }

    fun restoreContentFocus(): Boolean {
        navExpanded = false
        return when {
            lastTarget == "view" ->
                runCatching {
                    viewModeRequester.requestFocus()
                    true
                }.getOrDefault(false)

            lastTarget == "cloud" ->
                runCatching {
                    cloudRequester.requestFocus()
                    true
                }.getOrDefault(false)

            lastTarget == "item" && lastMediaKey != null && mediaRequesters.containsKey(lastMediaKey) ->
                runCatching {
                    mediaRequesters.getValue(requireNotNull(lastMediaKey)).requestFocus()
                    true
                }.getOrDefault(false)

            else -> requestSelectedTabFocus()
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                TvLibraryFocusMemory.firstVisibleItemIndex = index
                TvLibraryFocusMemory.firstVisibleItemScrollOffset = offset
            }
    }

    // Nuvio-reference focus restoration: poster first when returning from
    // Detail, otherwise the primary Library selector owns initial focus.
    LaunchedEffect(mediaKeys, gridView, cloudSelected) {
        delay(110)
        var restored = false
        if (!cloudSelected && lastTarget == "item") {
            val key = lastMediaKey
            val mediaIndex = key?.let(mediaKeys::indexOf)?.takeIf { it >= 0 }
            val requester = key?.let(mediaRequesters::get)
            if (mediaIndex != null && requester != null) {
                if (
                    TvLibraryFocusMemory.firstVisibleItemIndex == 0 &&
                    TvLibraryFocusMemory.firstVisibleItemScrollOffset == 0
                ) {
                    runCatching { gridState.scrollToItem(mediaIndex + LIBRARY_HEADER_ITEMS) }
                }
                restored = runCatching { requester.requestFocus() }.isSuccess
                if (!restored) {
                    delay(20)
                    restored = runCatching { requester.requestFocus() }.isSuccess
                }
            }
        }

        if (!restored) {
            restored = restoreContentFocus()
        }
        if (!restored) {
            delay(20)
            requestSelectedTabFocus()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        val availableWidthDp = (maxWidth.value - 104f).coerceAtLeast(420f)
        val gridColumns =
            ((availableWidthDp + LIBRARY_GRID_GAP_DP) /
                (LIBRARY_TARGET_POSTER_WIDTH_DP + LIBRARY_GRID_GAP_DP))
                .toInt()
                .coerceIn(4, 6)

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 52.dp,
                end = 52.dp,
                top = 82.dp,
                bottom = 48.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(
                key = "library-header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = "Library",
                    color = TvDesign.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .4.sp,
                )
            }

            item(
                key = "library-controls",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryControlPill(
                            label = "My List",
                            selected = !cloudSelected,
                            requester = myListRequester,
                            onFocused = {
                                navExpanded = false
                                rememberTarget("my-list")
                            },
                            onClick = {
                                cloudSelected = false
                                rememberTarget("my-list")
                            },
                            onLeft = ::focusSidebar,
                            onRight = {
                                runCatching { cloudRequester.requestFocus() }
                                true
                            },
                            onDown = {
                                requestFirstMediaFocus()
                                true
                            },
                        )

                        LibraryControlPill(
                            label = "Cloud",
                            selected = cloudSelected,
                            requester = cloudRequester,
                            onFocused = {
                                navExpanded = false
                                rememberTarget("cloud")
                            },
                            onClick = {
                                cloudSelected = true
                                rememberTarget("cloud")
                            },
                            onLeft = {
                                runCatching { myListRequester.requestFocus() }
                                true
                            },
                            onRight = {
                                runCatching { viewModeRequester.requestFocus() }
                                true
                            },
                            onDown = { true },
                        )
                    }

                    LibraryViewModeButton(
                        gridView = gridView,
                        requester = viewModeRequester,
                        onFocused = {
                            navExpanded = false
                            rememberTarget("view")
                        },
                        onToggle = {
                            val nextGridView = !gridView
                            gridView = nextGridView
                            libraryUiPreferences
                                .edit()
                                .putBoolean(LIBRARY_GRID_VIEW_KEY, nextGridView)
                                .apply()
                            rememberTarget("view")
                        },
                        onLeft = {
                            runCatching {
                                if (cloudSelected) cloudRequester.requestFocus()
                                else myListRequester.requestFocus()
                            }
                            true
                        },
                        onDown = {
                            requestFirstMediaFocus()
                            true
                        },
                    )
                }
            }

            when {
                cloudSelected -> {
                    item(
                        key = "library-cloud-empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LibraryEmptyState(
                            title = "Cloud library",
                            body = "Cloud sync is not connected yet. Your locally saved titles stay available in My List.",
                        )
                    }
                }

                watchlist.isEmpty() -> {
                    item(
                        key = "library-saved-empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LibraryEmptyState(
                            title = "Your library is empty",
                            body = "Titles added to My List will appear here.",
                        )
                    }
                }

                gridView -> {
                    watchlist.forEachIndexed { index, media ->
                        val key = libraryMediaKey(media)
                        item(key = "library-grid:$key") {
                            LibraryPosterCard(
                                media = media,
                                requester = mediaRequesters.getValue(key),
                                onFocused = {
                                    navExpanded = false
                                    rememberTarget("item", key)
                                },
                                onClick = {
                                    TvLibraryFocusMemory.firstVisibleItemIndex =
                                        gridState.firstVisibleItemIndex
                                    TvLibraryFocusMemory.firstVisibleItemScrollOffset =
                                        gridState.firstVisibleItemScrollOffset
                                    rememberTarget("item", key)
                                    onOpenMedia(media)
                                },
                                onUpFromFirstRow = if (index < gridColumns) {
                                    {
                                        requestSelectedTabFocus()
                                    }
                                } else null,
                                onLeftFromFirstColumn = if (index % gridColumns == 0) {
                                    ::focusSidebar
                                } else null,
                                blockRight =
                                    index % gridColumns == gridColumns - 1 ||
                                        index == watchlist.lastIndex,
                            )
                        }
                    }
                }

                else -> {
                    watchlist.forEachIndexed { index, media ->
                        val key = libraryMediaKey(media)
                        item(
                            key = "library-list:$key",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            LibraryListRow(
                                media = media,
                                requester = mediaRequesters.getValue(key),
                                onFocused = {
                                    navExpanded = false
                                    rememberTarget("item", key)
                                },
                                onClick = {
                                    TvLibraryFocusMemory.firstVisibleItemIndex =
                                        gridState.firstVisibleItemIndex
                                    TvLibraryFocusMemory.firstVisibleItemScrollOffset =
                                        gridState.firstVisibleItemScrollOffset
                                    rememberTarget("item", key)
                                    onOpenMedia(media)
                                },
                                onLeft = ::focusSidebar,
                                onUpFromFirst = if (index == 0) {
                                    { requestSelectedTabFocus() }
                                } else null,
                            )
                        }
                    }
                }
            }
        }

        TvSidebar(
            selected = "Library",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = ::restoreContentFocus,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }

    // `onResume` stays in the existing route contract for compatibility. This
    // screen intentionally exposes only Mobile-parity My List / Cloud content.
}

@Composable
private fun LibraryControlPill(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Boolean,
    onDown: () -> Boolean,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "libraryControlScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(44.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeft()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> true
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    focused -> TvDesign.White.copy(alpha = .18f)
                    selected -> TvDesign.White.copy(alpha = .11f)
                    else -> TvDesign.SurfaceRaised.copy(alpha = .82f)
                },
                shape = ControlShape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .42f) else Color.Transparent,
                shape = ControlShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused || selected) TvDesign.White else TvDesign.Muted,
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryViewModeButton(
    gridView: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onToggle: () -> Unit,
    onLeft: () -> Boolean,
    onDown: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "libraryViewModeScale",
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(44.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> onLeft()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> true
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onToggle()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = if (focused) {
                    TvDesign.White.copy(alpha = .18f)
                } else {
                    TvDesign.SurfaceRaised.copy(alpha = .82f)
                },
                shape = ControlShape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .42f) else Color.Transparent,
                shape = ControlShape,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryModeGlyph(grid = gridView, focused = focused)
        Text(
            text = if (gridView) "Grid" else "List",
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LibraryModeGlyph(
    grid: Boolean,
    focused: Boolean,
) {
    val tint = if (focused) TvDesign.White else TvDesign.Muted
    if (grid) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(tint, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(13.dp)
                        .height(2.dp)
                        .background(tint, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    media: MediaItem,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onUpFromFirstRow: (() -> Unit)?,
    onLeftFromFirstColumn: (() -> Unit)?,
    blockRight: Boolean,
) {
    var focused by remember(media.id, media.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "libraryPosterScale",
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 7.dp.toPx() else 0f
                shape = PosterShape
                clip = false
            }
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown &&
                        code == KeyEvent.KEYCODE_DPAD_UP &&
                        onUpFromFirstRow != null -> {
                        onUpFromFirstRow()
                        true
                    }

                    event.type == KeyEventType.KeyDown &&
                        code == KeyEvent.KEYCODE_DPAD_LEFT &&
                        onLeftFromFirstColumn != null -> {
                        onLeftFromFirstColumn()
                        true
                    }

                    event.type == KeyEventType.KeyDown &&
                        code == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        blockRight -> true

                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }

                    else -> false
                }
            }
            .clickable(onClick = onClick),
    ) {
        TvNetworkImage(
            url = media.poster,
            contentDescription = media.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(PosterShape)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White.copy(alpha = .88f) else Color.Transparent,
                    shape = PosterShape,
                ),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.height(7.dp))
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOfNotNull(
                media.releaseInfo,
                libraryTypeLabel(media),
            ).joinToString(" • "),
            color = TvDesign.Muted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryListRow(
    media: MediaItem,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onUpFromFirst: (() -> Unit)?,
) {
    var focused by remember(media.id, media.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = tween(180),
        label = "libraryListScale",
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(112.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeft()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        code == KeyEvent.KEYCODE_DPAD_UP &&
                        onUpFromFirst != null -> {
                        onUpFromFirst()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = if (focused) {
                    TvDesign.White.copy(alpha = .11f)
                } else {
                    TvDesign.SurfaceRaised.copy(alpha = .46f)
                },
                shape = ListRowShape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .34f) else Color.Transparent,
                shape = ListRowShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvNetworkImage(
            url = media.poster,
            contentDescription = media.name,
            modifier = Modifier
                .width(61.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = media.name,
                color = TvDesign.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    media.releaseInfo,
                    libraryTypeLabel(media),
                ).joinToString(" • "),
                color = TvDesign.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "›",
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(
                color = TvDesign.SurfaceRaised.copy(alpha = .58f),
                shape = EmptyShape,
            )
            .border(
                width = 1.dp,
                color = TvDesign.White.copy(alpha = .07f),
                shape = EmptyShape,
            )
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = TvDesign.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 3,
        )
    }
}

private val ControlShape = RoundedCornerShape(999.dp)
private val PosterShape = RoundedCornerShape(12.dp)
private val ListRowShape = RoundedCornerShape(14.dp)
private val EmptyShape = RoundedCornerShape(18.dp)

private fun libraryMediaKey(media: MediaItem): String =
    "${media.type}:${media.id}"

private fun libraryTypeLabel(media: MediaItem): String =
    when (media.type.lowercase()) {
        "movie" -> "Movie"
        "series", "tv" -> "Series"
        "anime" -> "Anime"
        else -> media.type.replaceFirstChar { it.uppercase() }
    }

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
