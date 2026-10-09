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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.tvPosterActivation
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

// Lazy diagnostic messages: no string construction while diagnostics is OFF.
private inline fun traceHome(build: () -> String) {
    if (PerformanceDiagnostics.isCollecting()) {
        PerformanceDiagnostics.captureRuntimeEvent(build())
    }
}

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
    // A profile change resets activeRowKey and focusedIndexByRow in the app.
    // Discard horizontal positions alongside that existing focus reset.
    if (TvHomeFocusMemory.activeRowKey == null && TvHomeFocusMemory.focusedIndexByRow.isEmpty()) {
        TvHomeFocusMemory.horizontalPositionByRow.clear()
    }
    val verticalCacheWindow = remember {
        LazyLayoutCacheWindow(ahead = VerticalRowCacheExtent, behind = VerticalRowCacheExtent)
    }
    val verticalState = rememberLazyListState(cacheWindow = verticalCacheWindow)
    // A row's horizontal position and its focused card are independent.
    // Keep LazyListState instances above the LazyColumn items so their positions
    // survive row disposal/recomposition during vertical navigation.
    val rowListStates = remember { mutableMapOf<String, LazyListState>() }
    val verticalScope = rememberCoroutineScope()
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

    // Read-only, rate-limited observation of actual LazyColumn movement.
    LaunchedEffect(verticalState) {
        if (!PerformanceDiagnostics.isCollecting()) return@LaunchedEffect
        var lastLoggedAt = 0L
        var previousIndex = -1
        var previousOffset = -1
        var previousMoving = false
        snapshotFlow {
            Triple(verticalState.firstVisibleItemIndex,
                verticalState.firstVisibleItemScrollOffset, verticalState.isScrollInProgress)
        }.collect { (index, offset, moving) ->
            val now = android.os.SystemClock.uptimeMillis()
            val changed = index != previousIndex || offset != previousOffset || moving != previousMoving
            val boundary = moving != previousMoving || index != previousIndex
            if (changed && (boundary || now - lastLoggedAt >= 220L)) {
                traceHome {
                    "HOME_SCROLL_STATE axis=vertical index=$index offset=$offset moving=$moving " +
                        "navigation=${currentNavigationVisible}"
                }
                lastLoggedAt = now
            }
            previousIndex = index
            previousOffset = offset
            previousMoving = moving
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
        // Re-enter a previously visited row at its own last focused card.
        // On the first visit, preserve Vueo's current column-aligned D-pad behavior.
        val targetCardIndex = (TvHomeFocusMemory.focusedIndexByRow[targetRow.key] ?: focusedIndex)
            .coerceIn(0, targetRow.entries.lastIndex)

        verticalFocusJob[0]?.cancel()
        verticalFocusJob[0] = verticalScope.launch {
            traceHome {
                "HOME_SCROLL_COMMAND axis=vertical cause=dpad fromRow=${row.key} toRow=${targetRow.key} " +
                    "fromCard=$focusedIndex toCard=$targetCardIndex delta=$delta " +
                    "offset=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
            // If the destination is already visible, do not re-align it.
            // This is independent of horizontal poster focus and Topbar sizing.
            val onScreen = verticalState.layoutInfo.visibleItemsInfo.any { visible ->
                if (visible.key != targetRow.key) false else {
                    val viewportStart = verticalState.layoutInfo.viewportStartOffset
                    val viewportEnd = verticalState.layoutInfo.viewportEndOffset
                    val fitsViewport = visible.size <= viewportEnd - viewportStart
                    if (fitsViewport) {
                        visible.offset >= viewportStart && visible.offset + visible.size <= viewportEnd
                    } else {
                        visible.offset < viewportEnd && visible.offset + visible.size > viewportStart
                    }
                }
            }
            if (!onScreen) {
                verticalState.animateScrollToItem(targetIndex, 0)
            }
            withFrameNanos { }
            if (currentNavigationVisible) return@launch
            verticalFocusToken += 1
            verticalFocusTarget = HomeVerticalFocusTarget(
                rowKey = targetRow.key,
                cardIndex = targetCardIndex,
                token = verticalFocusToken,
            )
            traceHome {
                "HOME_SCROLL_FINISH axis=vertical cause=dpad target=${targetRow.key} " +
                    "onScreen=$onScreen at=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
        }
        return true
    }

    DisposableEffect(verticalState) {
        onDispose {
            verticalFocusJob[0]?.cancel()
        }
    }

    // Initial Home focus is separate from horizontal LazyRow position.
    // If Source navigation disposed Home, its new composition must recover the
    // saved row/card rather than resetting to row 1 when returning from Details.
    LaunchedEffect(focusResetToken, rows.isNotEmpty(), navigationVisible) {
        if (focusResetToken <= 0 || appliedFocusResetToken == focusResetToken ||
            rows.isEmpty() || navigationVisible
        ) return@LaunchedEffect
        appliedFocusResetToken = focusResetToken
        val savedRowKey = TvHomeFocusMemory.activeRowKey
        val targetRowIndex = rows.indexOfFirst { it.key == savedRowKey && it.entries.isNotEmpty() }
            .takeIf { it >= 0 } ?: rows.indexOfFirst { it.entries.isNotEmpty() }
                .takeIf { it >= 0 } ?: return@LaunchedEffect
        val targetRow = rows[targetRowIndex]
        val targetCardIndex = (TvHomeFocusMemory.focusedIndexByRow[targetRow.key] ?: 0)
            .coerceIn(0, targetRow.entries.lastIndex)
        TvHomeFocusMemory.activeRowKey = targetRow.key
        TvHomeFocusMemory.focusedIndexByRow[targetRow.key] = targetCardIndex
        verticalFocusTarget = null
        verticalFocusJob[0]?.cancel()
        traceHome {
            "HOME_SCROLL_COMMAND axis=vertical cause=initial_focus_restore target=${targetRow.key} " +
                "card=$targetCardIndex from=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
        }
        verticalState.scrollToItem(targetRowIndex, 0)
        if (!currentNavigationVisible) {
            verticalFocusToken += 1
            verticalFocusTarget = HomeVerticalFocusTarget(
                targetRow.key, targetCardIndex, verticalFocusToken, initialReset = true,
            )
            PerformanceDiagnostics.captureRuntimeEvent(
                "HOME_FOCUS_TARGET row=${targetRow.key} card=$targetCardIndex saved=${savedRowKey != null}",
            )
        }
    }

    // Opening navigation revokes pending row commands without scrolling to a preview.
    // Keys exclude rows, so progressive catalog batches cannot restart this effect.
    LaunchedEffect(navigationVisible) {
        if (navigationVisible) {
            traceHome {
                "HOME_NAV_OPEN autoScrollCW=false activeRow=${TvHomeFocusMemory.activeRowKey} " +
                    "vertical=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
            verticalFocusTarget = null
            verticalFocusJob[0]?.cancel()
            verticalFocusJob[0] = null
            previewReturnRowKey = TvHomeFocusMemory.activeRowKey
            previewReturnCardIndex = previewReturnRowKey?.let {
                TvHomeFocusMemory.focusedIndexByRow[it]
            } ?: 0
            // Isolation test: opening the sidebar must not auto-scroll the catalog
            // to Continue Watching. Keep CW, viewport motion, and focus memory intact.
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
        traceHome {
            "HOME_SCROLL_COMMAND axis=vertical cause=nav_return target=${row.key} card=$cardIndex " +
                "from=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
        }
        try {
            verticalState.animateScrollToItem(availableRows.indexOfFirst { it.key == row.key }, 0)
            traceHome {
                "HOME_SCROLL_FINISH axis=vertical cause=nav_return target=${row.key} " +
                    "at=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
        } catch (cancelled: CancellationException) {
            traceHome { "HOME_SCROLL_FINISH axis=vertical cause=nav_return outcome=cancelled target=${row.key}" }
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
                val rowState = remember(row.key) {
                    rowListStates.getOrPut(row.key) {
                        val position = TvHomeFocusMemory.horizontalPositionByRow[row.key]
                        LazyListState(
                            firstVisibleItemIndex = (position?.firstVisibleItemIndex ?: 0)
                                .coerceIn(0, row.entries.lastIndex.coerceAtLeast(0)),
                            firstVisibleItemScrollOffset = position?.firstVisibleItemScrollOffset
                                ?.coerceAtLeast(0) ?: 0,
                        )
                    }
                }
                TvModernHomeRow(
                    rowVisible = rowVisible,
                    row = row,
                    rowState = rowState,
                    rowFocusRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() },
                    contentFocusEnabled = !navigationVisible ||
                        (verticalFocusTarget?.menuReturn == true && verticalFocusTarget?.rowKey == row.key),
                    verticalFocusTarget = verticalFocusTarget?.takeIf { it.rowKey == row.key },
                    onFocusTargetConsumed = { token, focused ->
                        val target = verticalFocusTarget
                        if (target?.token == token) {
                            traceHome {
                                "HOME_FOCUS_RESULT row=${target.rowKey} card=${target.cardIndex} " +
                                    "token=$token success=$focused menuReturn=${target.menuReturn} initial=${target.initialReset}"
                            }
                            if (target.initialReset) {
                                PerformanceDiagnostics.captureRuntimeEvent(
                                    "HOME_FOCUS_RESTORED row=${target.rowKey} card=${target.cardIndex} success=$focused",
                                )
                            }
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
                        // One focus coordinator owns vertical moves; a left/right
                        // card focus must not start another row alignment job.
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
    rowState: LazyListState,
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
    // Persist the actual viewport index + pixel offset, NOT the focused card.
    DisposableEffect(row.key, rowState) {
        onDispose {
            TvHomeFocusMemory.horizontalPositionByRow[row.key] = TvHomeHorizontalPosition(
                rowState.firstVisibleItemIndex, rowState.firstVisibleItemScrollOffset,
            )
        }
    }
    val itemFocusRequesters = remember(row.key) { mutableMapOf<Int, FocusRequester>() }
    val currentContentFocusEnabled by rememberUpdatedState(contentFocusEnabled)
    val density = LocalDensity.current
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val rowHorizontalPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    // Reserve paint space for the 1.022x focus scale without moving the cards.
    val focusPaintInset = if (row.kind == TvHomeRowKind.CONTINUE_WATCHING) 4.dp else 0.dp

    // Nuvio-style row behavior: focus and scroll position are independent.
    // A fully visible poster is NEVER repositioned by Up/Down, Left/Right,
    // focusRestorer, or Home return. Only an off-screen portion is revealed.
    val horizontalBringIntoViewSpec = remember(
        density, defaultBringIntoViewSpec, focusPaintInset,
    ) {
        val focusInsetPx = with(density) { focusPaintInset.toPx() }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                defaultBringIntoViewSpec.scrollAnimationSpec

            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val childStart = minOf(offset, offset + size)
                val childEnd = maxOf(offset, offset + size)
                val viewportStart = focusInsetPx
                val viewportEnd = (containerSize - focusInsetPx).coerceAtLeast(viewportStart)
                // Standard minimal-reveal policy instead of pinning focused
                // cards to the start edge (the source of Card 3 -> Card 1 jumps).
                return when {
                    childStart < viewportStart -> childStart - viewportStart
                    childEnd > viewportEnd -> childEnd - viewportEnd
                    else -> 0f
                }
            }
        }
    }

    // Read-only horizontal trace. It runs only for rows on screen during recording.
    LaunchedEffect(row.key, rowVisible, rowState) {
        if (!rowVisible || !PerformanceDiagnostics.isCollecting()) return@LaunchedEffect
        var lastLoggedAt = 0L
        var oldIndex = -1
        var oldOffset = -1
        var oldMoving = false
        snapshotFlow {
            Triple(rowState.firstVisibleItemIndex,
                rowState.firstVisibleItemScrollOffset, rowState.isScrollInProgress)
        }.collect { (index, offset, moving) ->
            val now = android.os.SystemClock.uptimeMillis()
            val changed = index != oldIndex || offset != oldOffset || moving != oldMoving
            val boundary = oldIndex != index || oldMoving != moving
            if (changed && (boundary || now - lastLoggedAt >= 220L)) {
                traceHome {
                    "HOME_SCROLL_STATE axis=horizontal row=${row.key} index=$index offset=$offset " +
                        "moving=$moving focusedCard=$focusedIndex"
                }
                lastLoggedAt = now
            }
            oldIndex = index
            oldOffset = offset
            oldMoving = moving
        }
    }

    LaunchedEffect(verticalFocusTarget?.token, contentFocusEnabled) {
        val target = verticalFocusTarget ?: return@LaunchedEffect
        if (!contentFocusEnabled || target.rowKey != row.key || row.entries.isEmpty()) return@LaunchedEffect
        val targetIndex = target.cardIndex.coerceIn(0, row.entries.lastIndex)

        // Lazy items can be composed ahead of the visible viewport. Their
        // FocusRequester exists but requesting it would scroll the row implicitly.
        // Wait for layout before deciding whether an explicit reveal is needed.
        for (attempt in 0 until 4) {
            if (rowState.layoutInfo.visibleItemsInfo.isNotEmpty()) break
            withFrameNanos { }
        }
        if (!currentContentFocusEnabled) return@LaunchedEffect
        val visible = rowState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!visible) {
            traceHome {
                "HOME_SCROLL_COMMAND axis=horizontal cause=target_outside_viewport row=${row.key} " +
                    "targetCard=$targetIndex from=${rowState.firstVisibleItemIndex}:${rowState.firstVisibleItemScrollOffset}"
            }
            // Only materialize when actually outside this row's viewport.
            // No rail-spanning animation just to restore a focus target.
            rowState.scrollToItem(targetIndex)
            withFrameNanos { }
        }

        repeat(6) {
            if (!currentContentFocusEnabled) return@LaunchedEffect
            val requester = itemFocusRequesters[targetIndex]
            val focused = requester?.let { runCatching { it.requestFocus() }.getOrDefault(false) } == true
            if (focused) {
                traceHome {
                    "HOME_FOCUS_RESULT row=${row.key} card=$targetIndex " +
                        "horizontal=${rowState.firstVisibleItemIndex}:${rowState.firstVisibleItemScrollOffset}"
                }
                onFocusTargetConsumed(target.token, true)
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
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
                            val preferredVisible = rowState.layoutInfo.visibleItemsInfo.any {
                                it.index == preferredIndex &&
                                    it.offset < rowState.layoutInfo.viewportEndOffset &&
                                    it.offset + it.size > rowState.layoutInfo.viewportStartOffset
                            }
                            if (preferredVisible) {
                                itemFocusRequesters[preferredIndex] ?: FocusRequester.Default
                            } else {
                                FocusRequester.Default
                            }
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
                                    traceHome {
                                        "HOME_FOCUS_CARD row=${row.key} fromCard=$focusedIndex toCard=$index " +
                                            "horizontal=${rowState.firstVisibleItemIndex}:${rowState.firstVisibleItemScrollOffset} " +
                                            "horizontalMoving=${rowState.isScrollInProgress}"
                                    }
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
