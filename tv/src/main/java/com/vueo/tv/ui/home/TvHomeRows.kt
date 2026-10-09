package com.vueo.tv.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.tvPosterActivation
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ContinueWatchingWidth = 210.dp
private val ContinueWatchingHeight = 119.dp
private val PosterWidth = 114.dp
private val PosterHeight = 172.dp
private val ContinueShape = RoundedCornerShape(12.dp)
private val PosterShape = RoundedCornerShape(12.dp)
private val VerticalRowCacheExtent = 520.dp

private data class HomeVerticalFocusTarget(
    val rowKey: String,
    val cardIndex: Int,
    val token: Int,
    val menuReturn: Boolean = false,
    val initialReset: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvModernHomeRows(
    rows: List<TvHomeRow>,
    rowsViewportHeight: Dp,
    showContinueWatchingPreview: Boolean,
    contentFocusRequester: FocusRequester,
    focusResetToken: Int,
    contentReturnToken: Int,
    navigationVisible: Boolean,
    onContentFocused: () -> Unit,
    onUpFromFirstRow: () -> Unit,
    onLeftAtRowStart: (() -> Unit)?,
    onFocused: (TvHomeRow, Int, TvHomeEntry) -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    onPosterLongClick: (TvHomeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalCacheWindow = remember {
        LazyLayoutCacheWindow(ahead = VerticalRowCacheExtent, behind = VerticalRowCacheExtent)
    }
    val verticalState = rememberLazyListState(cacheWindow = verticalCacheWindow)
    val verticalScope = rememberCoroutineScope()
    val verticalAlignmentJob = remember { arrayOfNulls<Job>(1) }
    val verticalFocusJob = remember { arrayOfNulls<Job>(1) }
    var verticalFocusToken by remember { mutableIntStateOf(0) }
    var verticalFocusTarget by remember { mutableStateOf<HomeVerticalFocusTarget?>(null) }
    val currentRows by rememberUpdatedState(rows)
    val currentNavigationVisible by rememberUpdatedState(navigationVisible)
    var appliedFocusResetToken by remember { mutableIntStateOf(0) }
    var appliedContentReturnToken by remember { mutableIntStateOf(0) }
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val initialActiveRowKey = TvHomeFocusMemory.activeRowKey
        ?.takeIf { saved -> rows.any { it.key == saved } }
        ?: rows.firstOrNull()?.key
    var previewReturnRowKey by remember { mutableStateOf<String?>(null) }
    var previewReturnCardIndex by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val verticalBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            // Focus changes below own the vertical scroll. Returning zero stops
            // the framework from launching a competing relocation animation.
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }

    fun requestRowAlignment(rowKey: String, skipIfAligned: Boolean = false) {
        val targetIndex = currentRows.indexOfFirst { it.key == rowKey }
        if (targetIndex < 0) return
        if (skipIfAligned && verticalAlignmentJob[0]?.isActive != true &&
            verticalFocusJob[0]?.isActive != true && !verticalState.isScrollInProgress &&
            verticalState.firstVisibleItemIndex == targetIndex &&
            verticalState.firstVisibleItemScrollOffset == 0
        ) return
        verticalAlignmentJob[0]?.cancel()
        verticalAlignmentJob[0] = verticalScope.launch {
            // Use LazyColumn's native animation instead of the old custom
            // frame-by-frame distance estimator. Repeated D-pad input cancels
            // toward the newest adjacent row without competing relocations.
            runCatching { verticalState.animateScrollToItem(targetIndex, 0) }
        }
    }

    fun moveVertical(row: TvHomeRow, focusedIndex: Int, delta: Int): Boolean {
        if (currentNavigationVisible) return true
        val availableRows = currentRows
        val currentRowIndex = availableRows.indexOfFirst { it.key == row.key }
        if (currentRowIndex < 0) return true

        if (delta < 0 && currentRowIndex == 0) {
            // The floating navigation bar is reachable only from row 1. This
            // removes Compose spatial-search jumps from deep catalog rows.
            onUpFromFirstRow()
            return true
        }
        if (delta > 0 && currentRowIndex == availableRows.lastIndex) {
            return true
        }

        val targetIndex = (currentRowIndex + delta).coerceIn(0, availableRows.lastIndex)
        if (targetIndex == currentRowIndex) return true
        val targetRow = availableRows[targetIndex]
        if (targetRow.entries.isEmpty()) return true
        val targetCardIndex = focusedIndex.coerceIn(0, targetRow.entries.lastIndex)

        // Address the exact poster instead of focusing the LazyRow container and
        // hoping focusRestorer resolves the child on a later key press. The row
        // owns the final card request once it is composed/placed.
        verticalFocusToken += 1
        verticalFocusTarget = HomeVerticalFocusTarget(
            rowKey = targetRow.key,
            cardIndex = targetCardIndex,
            token = verticalFocusToken,
        )

        verticalFocusJob[0]?.cancel()
        verticalFocusJob[0] = verticalScope.launch {
            runCatching { verticalState.animateScrollToItem(targetIndex, 0) }
        }
        return true
    }

    fun alignFocusedRow(row: TvHomeRow) {
        if (!showContinueWatchingPreview) requestRowAlignment(row.key)
    }

    DisposableEffect(verticalState) {
        onDispose {
            verticalAlignmentJob[0]?.cancel()
            verticalFocusJob[0]?.cancel()
        }
    }

    // The reset is owned outside lazy items. Re-composing row 1 cannot replay it.
    LaunchedEffect(focusResetToken, rows.isNotEmpty(), navigationVisible) {
        if (focusResetToken <= 0 || appliedFocusResetToken == focusResetToken ||
            rows.isEmpty() || navigationVisible
        ) return@LaunchedEffect
        appliedFocusResetToken = focusResetToken
        val firstRow = rows.first()
        TvHomeFocusMemory.activeRowKey = firstRow.key
        TvHomeFocusMemory.focusedIndexByRow[firstRow.key] = 0
        verticalFocusTarget = null
        verticalAlignmentJob[0]?.cancel()
        verticalFocusJob[0]?.cancel()
        verticalState.scrollToItem(0, 0)
        if (!currentNavigationVisible) {
            verticalFocusToken += 1
            verticalFocusTarget = HomeVerticalFocusTarget(firstRow.key, 0, verticalFocusToken, initialReset = true)
        }
    }

    // Opening navigation revokes every old row command before preview scrolling.
    // Keys exclude rows, so progressive catalog batches cannot restart the preview.
    LaunchedEffect(navigationVisible) {
        if (navigationVisible) {
            verticalFocusTarget = null
            verticalFocusJob[0]?.cancel()
            verticalFocusJob[0] = null
            verticalAlignmentJob[0]?.cancel()
            verticalAlignmentJob[0] = null
            previewReturnRowKey = TvHomeFocusMemory.activeRowKey
            previewReturnCardIndex = previewReturnRowKey?.let {
                TvHomeFocusMemory.focusedIndexByRow[it]
            } ?: 0
            if (showContinueWatchingPreview) {
                val previewKey = currentRows.firstOrNull { it.key == "continue-watching" }?.key
                    ?: currentRows.firstOrNull()?.key
                previewKey?.let { requestRowAlignment(it, skipIfAligned = true) }
            }
        }
    }

    // Scroll first while the menu still owns focus. Then address the exact saved
    // card, even when the original deep row is no longer composed.
    LaunchedEffect(contentReturnToken, navigationVisible) {
        if (contentReturnToken <= 0 || appliedContentReturnToken == contentReturnToken ||
            !navigationVisible
        ) return@LaunchedEffect
        appliedContentReturnToken = contentReturnToken
        val availableRows = currentRows
        val row = availableRows.firstOrNull { it.key == previewReturnRowKey && it.entries.isNotEmpty() }
            ?: availableRows.firstOrNull { it.key == TvHomeFocusMemory.activeRowKey && it.entries.isNotEmpty() }
            ?: availableRows.firstOrNull { it.entries.isNotEmpty() }
            ?: return@LaunchedEffect
        if (row.entries.isEmpty()) return@LaunchedEffect
        val cardIndex = (if (row.key == previewReturnRowKey) previewReturnCardIndex else
            TvHomeFocusMemory.focusedIndexByRow[row.key] ?: 0).coerceIn(0, row.entries.lastIndex)
        verticalFocusTarget = null
        verticalFocusJob[0]?.cancel()
        verticalAlignmentJob[0]?.cancel()
        try {
            verticalState.animateScrollToItem(availableRows.indexOfFirst { it.key == row.key }, 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        if (!currentNavigationVisible) return@LaunchedEffect
        verticalFocusToken += 1
        verticalFocusTarget = HomeVerticalFocusTarget(row.key, cardIndex, verticalFocusToken, menuReturn = true)
    }

    val focusRestorer = remember {
        {
            rowFocusRequesters[TvHomeFocusMemory.activeRowKey ?: initialActiveRowKey]
                ?: rowFocusRequesters[initialActiveRowKey]
                ?: FocusRequester.Default
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides verticalBringIntoViewSpec) {
        LazyColumn(
            state = verticalState,
            modifier = modifier
                .fillMaxWidth()
                .height(rowsViewportHeight)
                .focusRequester(contentFocusRequester)
                .focusRestorer { focusRestorer() }
                .onFocusChanged { state ->
                    if (state.hasFocus && !currentNavigationVisible) onContentFocused()
                },
            contentPadding = PaddingValues(bottom = rowsViewportHeight),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
                contentType = { _, row -> row.kind },
            ) { _, row ->
                val rowVisible by remember(verticalState, row.key) {
                    derivedStateOf {
                        verticalState.layoutInfo.visibleItemsInfo.any {
                            it.key == row.key && it.offset < verticalState.layoutInfo.viewportEndOffset &&
                                it.offset + it.size > verticalState.layoutInfo.viewportStartOffset
                        }
                    }
                }
                TvModernHomeRow(
                    rowVisible = rowVisible,
                    row = row,
                    rowFocusRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() },
                    contentFocusEnabled = !navigationVisible ||
                        (verticalFocusTarget?.menuReturn == true && verticalFocusTarget?.rowKey == row.key),
                    verticalFocusTarget = verticalFocusTarget?.takeIf { it.rowKey == row.key },
                    onFocusTargetConsumed = { token, focused ->
                        val target = verticalFocusTarget
                        if (target?.token == token) {
                            if (target.menuReturn && focused) onContentFocused()
                            verticalFocusTarget = null
                        }
                    },
                    onContentFocused = onContentFocused,
                    onMoveVertical = { focusedRow, focusedIndex, delta ->
                        moveVertical(focusedRow, focusedIndex, delta)
                    },
                    onLeftAtRowStart = onLeftAtRowStart,
                    onFocused = { focusedRow, index, entry ->
                        alignFocusedRow(focusedRow)
                        onFocused(focusedRow, index, entry)
                    },
                    onOpen = onOpen,
                    onPosterLongClick = onPosterLongClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvModernHomeRow(
    row: TvHomeRow,
    rowVisible: Boolean,
    rowFocusRequester: FocusRequester,
    contentFocusEnabled: Boolean,
    verticalFocusTarget: HomeVerticalFocusTarget?,
    onFocusTargetConsumed: (Int, Boolean) -> Unit,
    onContentFocused: () -> Unit,
    onMoveVertical: (TvHomeRow, Int, Int) -> Boolean,
    onLeftAtRowStart: (() -> Unit)?,
    onFocused: (TvHomeRow, Int, TvHomeEntry) -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    onPosterLongClick: (TvHomeEntry) -> Unit,
) {
    val savedIndex = (TvHomeFocusMemory.focusedIndexByRow[row.key] ?: 0)
        .coerceIn(0, row.entries.lastIndex)
    var focusedIndex by remember(row.key) { mutableIntStateOf(savedIndex) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = savedIndex)
    val itemFocusRequesters = remember(row.key) { mutableMapOf<Int, FocusRequester>() }
    val currentContentFocusEnabled by rememberUpdatedState(contentFocusEnabled)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val rowHorizontalPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    // Reserve paint space for the 1.022x focus scale without moving the cards.
    val focusPaintInset = if (row.kind == TvHomeRowKind.CONTINUE_WATCHING) 4.dp else 0.dp

    val horizontalBringIntoViewSpec = remember(
        density,
        layoutDirection,
        defaultBringIntoViewSpec,
        rowHorizontalPadding,
        focusPaintInset,
    ) {
        val startInsetPx = with(density) { focusPaintInset.toPx() }
        val rtl = layoutDirection == LayoutDirection.Rtl
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = defaultBringIntoViewSpec.scrollAnimationSpec

            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val childSize = abs(size)
                return if (rtl) {
                    val initialTarget = containerSize - startInsetPx
                    val target = if (childSize <= containerSize && initialTarget < childSize) childSize else initialTarget
                    (offset + size) - target
                } else {
                    val initialTarget = startInsetPx
                    val available = containerSize - initialTarget
                    val target = if (childSize <= containerSize && available < childSize) containerSize - childSize else initialTarget
                    offset - target
                }
            }
        }
    }

    LaunchedEffect(verticalFocusTarget?.token, contentFocusEnabled) {
        val target = verticalFocusTarget ?: return@LaunchedEffect
        if (!contentFocusEnabled || target.rowKey != row.key || row.entries.isEmpty()) return@LaunchedEffect
        val targetIndex = target.cardIndex.coerceIn(0, row.entries.lastIndex)
        if (target.initialReset) {
            rowState.scrollToItem(targetIndex, 0)
            withFrameNanos { }
            if (!currentContentFocusEnabled) return@LaunchedEffect
        }
        val immediate = itemFocusRequesters[targetIndex]?.let { requester ->
            runCatching { requester.requestFocus() }.getOrDefault(false)
        } == true
        if (immediate) {
            onFocusTargetConsumed(target.token, true)
            return@LaunchedEffect
        }
        try {
            rowState.animateScrollToItem(targetIndex)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        repeat(4) {
            withFrameNanos { }
            if (!currentContentFocusEnabled) return@LaunchedEffect
            val focused = itemFocusRequesters[targetIndex]?.let { requester ->
                runCatching { requester.requestFocus() }.getOrDefault(false)
            } == true
            if (focused) {
                onFocusTargetConsumed(target.token, true)
                return@LaunchedEffect
            }
        }
        // An expired command must not steal focus when this row is later re-composed.
        onFocusTargetConsumed(target.token, false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .94f),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = rowHorizontalPadding, end = 32.dp),
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoViewSpec) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = rowHorizontalPadding - focusPaintInset)
                    .clipToBounds(),
            ) {
                LazyRow(
                    state = rowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        onMoveVertical(row, focusedIndex, -1)
                                    } else {
                                        true
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        onMoveVertical(row, focusedIndex, 1)
                                    } else {
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                        .focusRequester(rowFocusRequester)
                        .focusRestorer {
                            val preferredIndex = (TvHomeFocusMemory.focusedIndexByRow[row.key] ?: focusedIndex)
                                .coerceIn(0, row.entries.lastIndex)
                            itemFocusRequesters[preferredIndex]
                                ?: itemFocusRequesters[focusedIndex]
                                ?: itemFocusRequesters[0]
                                ?: FocusRequester.Default
                        }
                        .focusGroup(),
                    contentPadding = PaddingValues(
                        start = focusPaintInset,
                        top = focusPaintInset,
                        end = 32.dp,
                        bottom = focusPaintInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = row.entries,
                        key = { _, entry -> entry.key },
                    ) { index, entry ->
                        val itemRequester = itemFocusRequesters.getOrPut(index) { FocusRequester() }
                        val openPosterActions = { onPosterLongClick(entry) }
                        val cardVisible by remember(rowState, index) {
                            derivedStateOf {
                                rowState.layoutInfo.visibleItemsInfo.any {
                                    it.index == index && it.offset < rowState.layoutInfo.viewportEndOffset &&
                                        it.offset + it.size > rowState.layoutInfo.viewportStartOffset
                                }
                            }
                        }
                        val imageActivated = remember(entry.key) { booleanArrayOf(false) }
                        if (rowVisible && cardVisible) imageActivated[0] = true
                        TvModernHomeCard(
                            loadImage = imageActivated[0],
                            entry = entry,
                            kind = row.kind,
                            requester = itemRequester,
                            contentFocusEnabled = contentFocusEnabled,
                            onLeftAtStart = onLeftAtRowStart.takeIf { index == 0 },
                            onFocused = {
                                if (currentContentFocusEnabled) {
                                    focusedIndex = index
                                    TvHomeFocusMemory.activeRowKey = row.key
                                    TvHomeFocusMemory.focusedIndexByRow[row.key] = index
                                    onContentFocused()
                                    onFocused(row, index, entry)
                                }
                            },
                            onOpen = { onOpen(entry) },
                            onHold = openPosterActions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvModernHomeCard(
    entry: TvHomeEntry,
    loadImage: Boolean,
    kind: TvHomeRowKind,
    requester: FocusRequester,
    contentFocusEnabled: Boolean,
    onLeftAtStart: (() -> Unit)?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onHold: () -> Unit,
) {
    var focused by remember(entry.key) { mutableStateOf(false) }
    // Nuvio's catalog cards keep their geometry on focus. Preserve the
    // existing Continue Watching treatment outside the poster comparison.
    val animatedScale by animateFloatAsState(
        targetValue = if (focused && kind == TvHomeRowKind.CONTINUE_WATCHING) 1.022f else 1f,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "modernHomeCardScale",
    )

    val width = if (kind == TvHomeRowKind.CONTINUE_WATCHING) ContinueWatchingWidth else PosterWidth
    val height = if (kind == TvHomeRowKind.CONTINUE_WATCHING) ContinueWatchingHeight else PosterHeight
    val shape = if (kind == TvHomeRowKind.CONTINUE_WATCHING) ContinueShape else PosterShape

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .focusRequester(requester)
            .focusProperties { canFocus = contentFocusEnabled }
            .onPreviewKeyEvent { event ->
                if (
                    onLeftAtStart != null &&
                    event.key == Key.DirectionLeft
                ) {
                    if (event.type == KeyEventType.KeyDown) onLeftAtStart()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { state ->
                val becameFocused = state.isFocused
                if (becameFocused && !focused) onFocused()
                focused = becameFocused
            }
            .clip(shape)
            .background(TvDesign.Surface)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .96f) else Color.Transparent,
                shape = shape,
            )
            .tvPosterActivation(
                onClick = onOpen,
                onLongClick = onHold,
            )
            .clickable(onClick = onOpen),
    ) {
        when (kind) {
            TvHomeRowKind.CONTINUE_WATCHING -> ContinueWatchingCardContent(entry, loadImage || focused, focused)
            TvHomeRowKind.POSTERS -> PosterCardContent(entry, loadImage || focused, focused)
        }
    }
}

@Composable
private fun PosterCardContent(entry: TvHomeEntry, loadImage: Boolean, focused: Boolean) {
    TvNetworkImage(
        url = entry.media.poster ?: entry.media.background,
        contentDescription = entry.media.name,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        fallback = TvDesign.SurfaceRaised,
        loadEnabled = loadImage,
        highPriority = focused,
    )
}

@Composable
private fun ContinueWatchingCardContent(entry: TvHomeEntry, loadImage: Boolean, focused: Boolean) {
    val resume = entry as? TvHomeEntry.Resume
    val progress = resume?.playback?.progressFraction ?: 0f

    Box(Modifier.fillMaxSize()) {
        TvNetworkImage(
            url = entry.media.background ?: entry.media.poster,
            contentDescription = entry.media.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.SurfaceRaised,
            loadEnabled = loadImage,
            highPriority = focused,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .50f to Color.Transparent,
                        1f to TvDesign.Black.copy(alpha = .90f),
                    )
                )
        )

        entry.remainingText()?.let { remaining ->
            Text(
                text = remaining,
                color = TvDesign.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(TvDesign.Black.copy(alpha = .72f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 11.dp, end = 11.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            entry.episodeText()?.let { episode ->
                Text(
                    text = episode,
                    color = TvDesign.White.copy(alpha = .78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(TvDesign.White.copy(alpha = .20f), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(TvDesign.Accent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
