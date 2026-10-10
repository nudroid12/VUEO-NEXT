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
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

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
    val fastLanding: Boolean = false,
    val menuReturn: Boolean = false,
    val initialReset: Boolean = false,
    val routeReturn: Boolean = false,
    val restorePosition: TvHomeHorizontalPosition? = null,
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
    active: Boolean,
    routeReturnPending: Boolean,
    routeFocusAnchor: HomeRouteFocusAnchor?,
    onCaptureRouteFocusAnchor: (HomeRouteFocusAnchor) -> Unit,
    onRouteFocusRestored: () -> Unit,
    navigationVisible: Boolean,
    navigationOwnsFocus: Boolean,
    onContentFocused: () -> Unit,
    onVerticalMotionChanged: (Boolean) -> Unit,
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
    // Nuvio-style fast DPAD vertical scrolling: key repeat advances a virtual
    // row cursor immediately; do not wait for every intermediate focus handoff.
    var pendingVerticalRowIndex by remember { mutableIntStateOf(-1) }
    var fastVerticalScrolling by remember { mutableStateOf(false) }
    var lastVerticalInputAt by remember { mutableLongStateOf(0L) }
    var fastInputEpoch by remember { mutableIntStateOf(0) }
    val currentMotionCallback by rememberUpdatedState(onVerticalMotionChanged)
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
    LaunchedEffect(active, routeReturnPending, routeFocusAnchor) {
        if (!active || !routeReturnPending) return@LaunchedEffect
        val anchor = routeFocusAnchor ?: return@LaunchedEffect
        val row = currentRows.firstOrNull { it.key == anchor.rowKey && it.entries.isNotEmpty() }
            ?: run {
                onRouteFocusRestored()
                return@LaunchedEffect
            }
        val cardIndex = row.entries.indexOfFirst { it.key == anchor.cardKey }
            .takeIf { it >= 0 } ?: anchor.cardIndex.coerceIn(0, row.entries.lastIndex)
        // This also owns initial focus when Home was recreated after Sources.
        // Do not let the later menu effect replay an initial reset over this return.
        appliedFocusResetToken = focusResetToken
        verticalFocusJob[0]?.cancel()
        if (verticalState.firstVisibleItemIndex != anchor.verticalIndex ||
            verticalState.firstVisibleItemScrollOffset != anchor.verticalOffset
        ) {
            verticalState.scrollToItem(anchor.verticalIndex, anchor.verticalOffset)
        }
        verticalFocusToken += 1
        verticalFocusTarget = HomeVerticalFocusTarget(
            rowKey = row.key, cardIndex = cardIndex, token = verticalFocusToken,
            routeReturn = true, restorePosition = anchor.horizontal,
        )
        traceHome { "HOME_ROUTE_FOCUS_TARGET row=${row.key} card=$cardIndex preserveViewport=true" }
    }

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

    // Freeze the hero while the row list is moving. Nuvio keeps its last
    // committed hero scene through rapid vertical navigation and updates after
    // the selected row settles, rather than switching artwork every repeat.
    LaunchedEffect(verticalState) {
        snapshotFlow { verticalState.isScrollInProgress || fastVerticalScrolling }
            .distinctUntilChanged()
            .collect { currentMotionCallback(it) }
    }

    fun finishFastVerticalScroll() {
        if (!fastVerticalScrolling) return
        val rowIndex = pendingVerticalRowIndex
        val targetRow = currentRows.getOrNull(rowIndex) ?: run {
            fastVerticalScrolling = false
            pendingVerticalRowIndex = -1
            return
        }
        if (targetRow.entries.isEmpty()) {
            fastVerticalScrolling = false
            pendingVerticalRowIndex = -1
            return
        }
        val cardIndex = (TvHomeFocusMemory.focusedIndexByRow[targetRow.key] ?: 0)
            .coerceIn(0, targetRow.entries.lastIndex)
        verticalFocusJob[0]?.cancel()
        verticalFocusJob[0] = verticalScope.launch {
            // Final snap does not animate through intermediary rows. Only
            // the selected destination gets focus when D-pad is released.
            if (verticalState.firstVisibleItemIndex != rowIndex ||
                verticalState.firstVisibleItemScrollOffset != 0
            ) verticalState.scrollToItem(rowIndex, 0)
            withFrameNanos { }
            if (currentNavigationVisible) {
                fastVerticalScrolling = false
                pendingVerticalRowIndex = -1
                return@launch
            }
            verticalFocusToken++
            verticalFocusTarget = HomeVerticalFocusTarget(
                rowKey = targetRow.key,
                cardIndex = cardIndex,
                token = verticalFocusToken,
                fastLanding = true,
            )
            traceHome {
                "HOME_SCROLL_FINISH axis=vertical cause=nuvio_fast_release target=${targetRow.key} " +
                    "card=$cardIndex at=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
        }
    }

    // KeyUp is primary; idle timeout is the fallback for remotes that do not
    // reliably deliver key release after long press / focus detachment.
    LaunchedEffect(fastInputEpoch) {
        if (!fastVerticalScrolling) return@LaunchedEffect
        delay(190L)
        if (fastVerticalScrolling &&
            android.os.SystemClock.uptimeMillis() - lastVerticalInputAt >= 175L
        ) finishFastVerticalScroll()
    }

    fun moveVertical(delta: Int, repeatCount: Int): Boolean {
        if (currentNavigationVisible) return true
        val availableRows = currentRows
        if (availableRows.isEmpty()) return true
        val actualIndex = availableRows.indexOfFirst {
            it.key == TvHomeFocusMemory.activeRowKey
        }.takeIf { it >= 0 } ?: 0
        // A repeat continues from the previous requested row, not the still
        // focused card; otherwise Up repeats keep requesting the same row.
        val baseIndex = pendingVerticalRowIndex.takeIf { it in availableRows.indices }
            ?: actualIndex
        val requestedIndex = baseIndex + delta
        if (requestedIndex !in availableRows.indices) {
            if (requestedIndex < 0 && repeatCount == 0 && !fastVerticalScrolling &&
                actualIndex == 0
            ) onUpFromFirstRow()
            return true
        }
        val targetRow = availableRows[requestedIndex]
        if (targetRow.entries.isEmpty()) return true
        pendingVerticalRowIndex = requestedIndex
        lastVerticalInputAt = android.os.SystemClock.uptimeMillis()
        fastInputEpoch++
        val selectedCard = (TvHomeFocusMemory.focusedIndexByRow[targetRow.key]
            ?: TvHomeFocusMemory.focusedIndexByRow[availableRows[actualIndex].key] ?: 0)
            .coerceIn(0, targetRow.entries.lastIndex)

        // Nuvio-style held scroll: bypass animated per-row focus transactions;
        // a single landing focus is issued when repeat input stops.
        if (repeatCount > 0 || fastVerticalScrolling) {
            fastVerticalScrolling = true
            verticalFocusTarget = null
            verticalFocusJob[0]?.cancel()
            traceHome {
                "HOME_SCROLL_COMMAND axis=vertical cause=nuvio_fast_hold target=${targetRow.key} " +
                    "repeat=$repeatCount pendingIndex=$requestedIndex"
            }
            verticalFocusJob[0] = verticalScope.launch {
                verticalState.scrollToItem(requestedIndex, 0)
            }
            return true
        }

        verticalFocusJob[0]?.cancel()
        verticalFocusJob[0] = verticalScope.launch {
            traceHome {
                "HOME_SCROLL_COMMAND axis=vertical cause=dpad fromRow=${availableRows[actualIndex].key} " +
                    "toRow=${targetRow.key} fromCard=${TvHomeFocusMemory.focusedIndexByRow[availableRows[actualIndex].key]} " +
                    "toCard=$selectedCard delta=$delta " +
                    "offset=${verticalState.firstVisibleItemIndex}:${verticalState.firstVisibleItemScrollOffset}"
            }
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
            if (!onScreen) verticalState.animateScrollToItem(requestedIndex, 0)
            withFrameNanos { }
            if (currentNavigationVisible) return@launch
            verticalFocusToken++
            verticalFocusTarget = HomeVerticalFocusTarget(
                rowKey = targetRow.key, cardIndex = selectedCard, token = verticalFocusToken,
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
    LaunchedEffect(focusResetToken, rows.isNotEmpty(), navigationVisible, routeReturnPending) {
        if (focusResetToken <= 0 || appliedFocusResetToken == focusResetToken ||
            rows.isEmpty() || navigationVisible || routeReturnPending
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
        pendingVerticalRowIndex = -1
        fastVerticalScrolling = false
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
            pendingVerticalRowIndex = -1
            fastVerticalScrolling = false
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
        pendingVerticalRowIndex = -1
        fastVerticalScrolling = false
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
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            when (event.type) {
                                KeyEventType.KeyDown -> moveVertical(
                                    if (event.key == Key.DirectionUp) -1 else 1,
                                    event.nativeKeyEvent.repeatCount,
                                )
                                KeyEventType.KeyUp -> {
                                    finishFastVerticalScroll()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
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
                    // Do not invalidate the focused card during panel enter.
                    // Once the nav item actually owns focus, allow only an
                    // explicit menu-return target back into the catalog.
                    active = active,
                    routeReturnPending = routeReturnPending,
                    contentFocusEnabled = !navigationOwnsFocus ||
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
                            if (target.routeReturn) {
                                onRouteFocusRestored()
                                traceHome { "HOME_ROUTE_FOCUS_RESULT success=$focused row=${target.rowKey} card=${target.cardIndex}" }
                            }
                            if (target.menuReturn && focused) onContentFocused()
                            if (target.fastLanding) fastVerticalScrolling = false
                            pendingVerticalRowIndex = -1
                            verticalFocusTarget = null
                        }
                    },
                    onContentFocused = onContentFocused,
                    onLeftAtRowStart = onLeftAtRowStart,
                    onFocused = { focusedRow, index, entry ->
                        // One focus coordinator owns vertical moves; a left/right
                        // card focus must not start another row alignment job.
                        onFocused(focusedRow, index, entry)
                    },
                    onOpen = { entry ->
                        val cardIndex = row.entries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
                        onCaptureRouteFocusAnchor(HomeRouteFocusAnchor(
                            row.key, entry.key, cardIndex,
                            TvHomeHorizontalPosition(rowState.firstVisibleItemIndex, rowState.firstVisibleItemScrollOffset),
                            verticalState.firstVisibleItemIndex, verticalState.firstVisibleItemScrollOffset,
                        ))
                        onOpen(entry)
                    },
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
    active: Boolean,
    routeReturnPending: Boolean,
    verticalFocusTarget: HomeVerticalFocusTarget?,
    onFocusTargetConsumed: (Int, Boolean) -> Unit,
    onContentFocused: () -> Unit,
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
    val currentActive by rememberUpdatedState(active)
    val currentRouteReturnPending by rememberUpdatedState(routeReturnPending)
    val suppressRouteBringIntoView by rememberUpdatedState(!active || routeReturnPending)
    val density = LocalDensity.current
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val rowHorizontalPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    // Reserve paint space for the 1.022x focus scale without moving the cards.
    val focusPaintInset = if (row.kind == TvHomeRowKind.CONTINUE_WATCHING) 4.dp else 0.dp

    // Port of NuvioTV 0.8.3-beta ModernHomeRows focus alignment policy.
    // Left/Right keeps the focused poster at the row start; Up/Down must
    // restore the row position before asking Compose to focus that poster.
    // This row already has external left padding, so the local anchor is 0
    // (or focusPaintInset for scaled Continue Watching cards).
    val horizontalBringIntoViewSpec = remember(
        density, defaultBringIntoViewSpec, focusPaintInset,
    ) {
        val anchorPx = with(density) { focusPaintInset.toPx() }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                defaultBringIntoViewSpec.scrollAnimationSpec

            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                // Covered-route handoff preserves the captured viewport. Ordinary
                // Left/Right and Up/Down still use Nuvio's existing alignment.
                if (suppressRouteBringIntoView) return 0f
                // Nuvio's LTR leading-edge alignment. The focused poster
                // scrolls to the left anchor even when already fully visible.
                // Account for cards wider than the available viewport.
                val childSize = kotlin.math.abs(size)
                val available = containerSize - anchorPx
                val target = if (childSize <= containerSize && available < childSize) {
                    containerSize - childSize
                } else {
                    anchorPx
                }
                return offset - target
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

    LaunchedEffect(verticalFocusTarget?.token, contentFocusEnabled, active) {
        val target = verticalFocusTarget ?: return@LaunchedEffect
        if (!active || !contentFocusEnabled || target.rowKey != row.key || row.entries.isEmpty()) return@LaunchedEffect
        val targetIndex = target.cardIndex.coerceIn(0, row.entries.lastIndex)

        // Nuvio uses pendingRowFocus -> scrollToItem(index) before focus.
        // Snap only when necessary: if the row is already at Card 3, an
        // Up/Down return changes focus but does not animate horizontal scroll.
        // Do NOT change the policy for ordinary Left/Right navigation.
        for (attempt in 0 until 4) {
            if (rowState.layoutInfo.visibleItemsInfo.isNotEmpty()) break
            withFrameNanos { }
        }
        if (!currentContentFocusEnabled) return@LaunchedEffect
        val restoreIndex = target.restorePosition?.firstVisibleItemIndex
            ?.coerceIn(0, row.entries.lastIndex) ?: targetIndex
        val restoreOffset = target.restorePosition?.firstVisibleItemScrollOffset ?: 0
        val alreadyAnchored = rowState.firstVisibleItemIndex == restoreIndex &&
            rowState.firstVisibleItemScrollOffset == restoreOffset
        if (!alreadyAnchored && (target.routeReturn || !rowState.isScrollInProgress)) {
            traceHome {
                "HOME_SCROLL_COMMAND axis=horizontal cause=nuvio_pending_row_focus row=${row.key} " +
                    "targetCard=$targetIndex from=${rowState.firstVisibleItemIndex}:${rowState.firstVisibleItemScrollOffset}"
            }
            rowState.scrollToItem(restoreIndex, restoreOffset)
            withFrameNanos { }
        }

        repeat(if (target.routeReturn) 12 else 6) {
            if (!currentActive || !currentContentFocusEnabled) return@LaunchedEffect
            val requester = itemFocusRequesters[targetIndex]
            val focused = requester?.let { runCatching { it.requestFocus() }.getOrDefault(false) } == true
            if (focused) {
                traceHome {
                    "HOME_FOCUS_RESULT row=${row.key} card=$targetIndex " +
                        "horizontal=${rowState.firstVisibleItemIndex}:${rowState.firstVisibleItemScrollOffset}"
                }
                if (target.routeReturn) {
                    // Let focus-triggered bring-into-view settle while still suppressed.
                    repeat(2) { withFrameNanos { } }
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
                        .focusRequester(rowFocusRequester)
                        .focusRestorer {
                            // Match Nuvio: restore the last focused card in this
                            // row, not an arbitrary first visible item.
                            val saved = (TvHomeFocusMemory.focusedIndexByRow[row.key] ?: focusedIndex)
                                .coerceIn(0, row.entries.lastIndex)
                            itemFocusRequesters[saved]
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
                                val returningToThisCard = verticalFocusTarget?.let {
                                    it.routeReturn && it.cardIndex == index
                                } == true
                                if (currentActive && currentContentFocusEnabled &&
                                    (!currentRouteReturnPending || returningToThisCard)
                                ) {
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
