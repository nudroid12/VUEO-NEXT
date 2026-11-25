package com.vueo.tv.home

import com.vueo.tv.ui.motion.tvTunedSpec
import com.vueo.tv.ui.motion.TvMotionGroup
import com.vueo.tv.ui.motion.TvMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import android.os.SystemClock
import com.vueo.tv.ui.tvPrefetchImage
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.tvSidebarHomeRowsViewportFraction
import com.vueo.tv.ui.tvSidebarIsPillMode
import com.vueo.tv.ui.tvSidebarIsHiddenClassic
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.tv.core.TvTitleArtwork

@Composable
internal fun TvHomePresentation(
    rows: List<TvHomeRow>,
    artworkApiKey: String,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    active: Boolean,
    routeReturnPending: Boolean,
    routeFocusAnchor: HomeRouteFocusAnchor?,
    onCaptureRouteFocusAnchor: (HomeRouteFocusAnchor) -> Unit,
    onRouteFocusRestored: () -> Unit,
    navigationVisible: Boolean,
    navigationOwnsFocus: Boolean,
    contentFocusRequester: FocusRequester,
    focusResetToken: Int,
    contentReturnToken: Int,
    onContentFocused: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    onLongClick: (TvHomeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settledHeroDelayMs = 180L
    var focusedEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var heroScene by remember(artworkApiKey) { mutableStateOf<TvHomeHeroScene?>(null) }
    var lastNavigationAt by remember { mutableLongStateOf(0L) }
    // NuvioTV 0.8.3-beta: successive item changes in the same row within
    // 300ms are rapid horizontal navigation. Keep the last committed hero
    // visible until the focus has settled; do not change the row's scroll.
    var lastFocusedRowKey by remember {
        mutableStateOf(routeFocusAnchor?.rowKey ?: TvHomeFocusMemory.activeRowKey)
    }
    var rapidHorizontalNavigation by remember { mutableStateOf(false) }
    // Nuvio stable-hero policy: do not replace backdrop/copy while the user is
    // holding Up/Down or the vertical LazyColumn is actively scrolling.
    var verticalMotionActive by remember { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val logoSize = with(density) {
        IntSize(MODERN_HOME_LOGO_WIDTH.roundToPx(), MODERN_HOME_LOGO_HEIGHT.roundToPx())
    }
    val contentStartPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    val floatingPillMode = tvSidebarIsPillMode()
    val hideClassicSidebar = tvSidebarIsHiddenClassic()
    // Topbar focus and visibility no longer drive the Home viewport.
    val rowsViewportFraction = tvSidebarHomeRowsViewportFraction(
        classic = MODERN_HOME_ROWS_VIEWPORT_FRACTION,
        pill = MODERN_HOME_EXPANDED_ROWS_VIEWPORT_FRACTION,
    )

    LaunchedEffect(rows) {
        val currentKey = focusedEntry?.key
        val currentRow = rows.firstOrNull { it.key == lastFocusedRowKey && it.entries.isNotEmpty() }
        // Keep the committed hero and row through progressive catalog updates.
        val currentEntry = currentRow?.entries?.firstOrNull { it.key == currentKey }
        if (currentEntry != null) {
            if (currentRow?.key == NEW_EPISODES_ROW_KEY && currentEntry != focusedEntry) {
                focusedEntry = currentEntry
                heroScene?.takeIf { it.entry.key == currentKey }?.let { heroScene = it.copy(entry = currentEntry) }
            }
            return@LaunchedEffect
        }

        val savedRowKey = routeFocusAnchor?.rowKey ?: TvHomeFocusMemory.activeRowKey
        val initialRow = currentRow
            ?: rows.firstOrNull { it.key == savedRowKey && it.entries.isNotEmpty() }
            ?: rows.firstOrNull { it.entries.isNotEmpty() }
        lastFocusedRowKey = initialRow?.key
        focusedEntry = initialRow?.let { row ->
            val index = (TvHomeFocusMemory.focusedIndexByRow[row.key] ?: 0)
                .coerceIn(0, row.entries.lastIndex)
            row.entries[index]
        }
    }

    // NuvioTV 0.8.3-beta style horizontal settle detector. Re-keying the
    // effect cancels an earlier settle, so holding Left/Right never commits
    // a stream of intermediate heroes. Slow single presses are unaffected.
    LaunchedEffect(lastFocusedRowKey, focusedEntry?.key, lastNavigationAt) {
        if (!rapidHorizontalNavigation) return@LaunchedEffect
        delay(settledHeroDelayMs)
        rapidHorizontalNavigation = false
    }

    // Preserve the previous scene during rapid horizontal navigation and
    // vertical scroll. Once focus stops, finish the *remaining* debounce
    // rather than waiting twice (first for rapid mode, then for hero load).
    LaunchedEffect(lastFocusedRowKey, focusedEntry?.key, artworkApiKey, verticalMotionActive, rapidHorizontalNavigation) {
        val next = focusedEntry ?: return@LaunchedEffect
        if (next is TvHomeEntry.Folder) return@LaunchedEffect
        if (verticalMotionActive || (rapidHorizontalNavigation && heroScene != null)) {
            return@LaunchedEffect
        }
        if (heroScene != null) {
            val elapsed = (SystemClock.uptimeMillis() - lastNavigationAt).coerceAtLeast(0L)
            val remaining = (settledHeroDelayMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
        }
        if (verticalMotionActive || (rapidHorizontalNavigation && heroScene != null)) {
            return@LaunchedEffect
        }
        val artwork = try {
            TvTitleArtwork.load(next.media, artworkApiKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null // Optional artwork failure still publishes the text title.
        }
        if (!verticalMotionActive && !rapidHorizontalNavigation && focusedEntry?.key == next.key) {
            heroScene = TvHomeHeroScene(next, artwork)
        }
    }

    // Preload one adjacent item only after Home has had time to paint the
    // focused hero. This stays best-effort and cancellable so optional artwork
    // work cannot compete with cold-start first-frame rendering.
    LaunchedEffect(lastFocusedRowKey, focusedEntry?.key, artworkApiKey, rows, verticalMotionActive, rapidHorizontalNavigation) {
        if (verticalMotionActive || rapidHorizontalNavigation) return@LaunchedEffect
        val selected = focusedEntry ?: return@LaunchedEffect
        if (selected is TvHomeEntry.Folder) return@LaunchedEffect
        val row = rows.firstOrNull { it.key == lastFocusedRowKey && it.entries.any { item -> item.key == selected.key } }
            ?: rows.firstOrNull { it.entries.any { item -> item.key == selected.key } }
            ?: return@LaunchedEffect
        val index = row.entries.indexOfFirst { it.key == selected.key }
        val adjacent = row.entries.getOrNull(index + 1) ?: row.entries.getOrNull(index - 1)
            ?: return@LaunchedEffect
        if (adjacent is TvHomeEntry.Folder) return@LaunchedEffect
        delay(750L)
        if (verticalMotionActive || rapidHorizontalNavigation) return@LaunchedEffect
        try {
            val artwork = TvTitleArtwork.load(adjacent.media, artworkApiKey)
            tvPrefetchImage(context, artwork.logo, logoSize)
            // A bounded decode primes the disk cache for the large hero as well.
            tvPrefetchImage(context, adjacent.media.background ?: adjacent.media.poster, IntSize(960, 540))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Adjacent artwork is optional; navigation remains independent.
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        val screenHeight = maxHeight
        // Only a committed row controls geometry. Opening navigation or moving
        // horizontally in that row cannot change the viewport height.
        val activeRowKind = rows.firstOrNull { it.key == lastFocusedRowKey && it.entries.isNotEmpty() }?.kind
            ?: rows.firstOrNull { it.key == routeFocusAnchor?.rowKey && it.entries.isNotEmpty() }?.kind
            ?: rows.firstOrNull { it.entries.isNotEmpty() }?.kind
            ?: TvHomeRowKind.POSTERS
        val portraitContentHeight = TvHomeRowKind.POSTERS.homeRowContentHeight(density)
        val portraitRowsHeight = maxOf(
            screenHeight * rowsViewportFraction,
            portraitContentHeight + 4.dp,
        ).coerceAtMost(screenHeight * .52f)

        // Preserve the portrait row's bottom margin for the shorter landscape
        // row, using the real card height and focus inset for each row kind.
        fun viewportHeightFor(kind: TvHomeRowKind): Dp {
            val rowContentHeight = kind.homeRowContentHeight(density)
            val contentHeightDifference = (portraitContentHeight - rowContentHeight).coerceAtLeast(0.dp)
            val minimumRowsHeight = (rowContentHeight + 4.dp).coerceAtMost(portraitRowsHeight)
            return (portraitRowsHeight - contentHeightDifference).coerceAtLeast(minimumRowsHeight)
        }

        val targetRowsHeight = viewportHeightFor(activeRowKind)
        val shortestRowsHeight = minOf(
            viewportHeightFor(TvHomeRowKind.CONTINUE_WATCHING),
            viewportHeightFor(TvHomeRowKind.COLLECTIONS),
        )
        val animatedRowsHeight by animateDpAsState(
            targetValue = targetRowsHeight,
            animationSpec = tvTunedSpec(
                TvMotionGroup.TOPBAR,
                activeRowKind != TvHomeRowKind.POSTERS,
                tween(durationMillis = 280, easing = TvMotion.EaseInOut),
            ),
            label = "homeAdaptiveRowsViewport",
        )
        val rowsViewportHeight = animatedRowsHeight.coerceIn(shortestRowsHeight, portraitRowsHeight)
        val heroHeight = (screenHeight - rowsViewportHeight + 38.dp).coerceAtMost(screenHeight)
        val largestHeroHeight = (screenHeight - shortestRowsHeight + 38.dp).coerceAtMost(screenHeight)

        // Keep the enlarged logo under the top navigation on compact displays,
        // with enough room below it for the metadata and three synopsis lines.
        val copyTopClearance = if (floatingPillMode) 62.dp else 12.dp
        val copyTextHeight = with(density) { 19.sp.toDp() * 2f + 21.sp.toDp() * 3f } + 18.dp
        val logoHeight = (
            screenHeight - portraitRowsHeight - MODERN_HOME_HERO_COPY_BOTTOM_GAP -
                copyTopClearance - copyTextHeight
        ).coerceIn(48.dp, MODERN_HOME_LOGO_HEIGHT)

        LaunchedEffect(activeRowKind, targetRowsHeight, navigationVisible) {
            if (PerformanceDiagnostics.isCollecting() && screenHeight > 0.dp) {
                PerformanceDiagnostics.captureRuntimeEvent(
                    "HOME_VIEWPORT_TARGET rowKind=$activeRowKind navigation=$navigationVisible " +
                        "target=${targetRowsHeight / screenHeight}",
                )
            }
        }

        TvModernHomeHero(
            scene = heroScene,
            heroHeight = heroHeight,
            rowsViewportHeight = rowsViewportHeight,
            stableHeroHeight = largestHeroHeight,
            stableRowsViewportHeight = portraitRowsHeight,
            logoHeight = logoHeight,
        )

        when {
            rows.isNotEmpty() -> {
                TvModernHomeRows(
                    rows = rows,
                    rowsViewportHeight = rowsViewportHeight,
                    contentFocusRequester = contentFocusRequester,
                    focusResetToken = focusResetToken,
                    contentReturnToken = contentReturnToken,
                    active = active,
                    routeReturnPending = routeReturnPending,
                    routeFocusAnchor = routeFocusAnchor,
                    onCaptureRouteFocusAnchor = onCaptureRouteFocusAnchor,
                    onRouteFocusRestored = onRouteFocusRestored,
                    navigationVisible = navigationVisible,
                    navigationOwnsFocus = navigationOwnsFocus,
                    onContentFocused = onContentFocused,
                    onVerticalMotionChanged = { verticalMotionActive = it },
                    onUpFromFirstRow = onOpenNavigation,
                    onLeftAtRowStart = when {
                        floatingPillMode -> ({ /* Topbar keeps Left on the first card. */ })
                        hideClassicSidebar -> onOpenNavigation
                        else -> null
                    },
                    onFocused = { row, index, entry ->
                        TvHomeFocusMemory.activeRowKey = row.key
                        TvHomeFocusMemory.focusedIndexByRow[row.key] = index
                        val sameRow = lastFocusedRowKey == row.key
                        if (!sameRow || focusedEntry?.key != entry.key) {
                            val now = SystemClock.uptimeMillis()
                            val rapidInterval = lastNavigationAt != 0L &&
                                now - lastNavigationAt in 1L..300L
                            // Up/Down switches rows: do not misclassify those
                            // focus restorations as horizontal fast navigation.
                            rapidHorizontalNavigation = sameRow && rapidInterval
                            lastNavigationAt = now
                        }
                        lastFocusedRowKey = row.key
                        focusedEntry = entry
                    },
                    onOpen = onOpen,
                    onPosterLongClick = onLongClick,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }

            loading -> {
                TvHomeLoading(contentStartPadding, rowsViewportHeight)
            }

            error != null -> {
                TvHomeLoadFailure(
                    message = error,
                    cachedContentVisible = false,
                    contentRequester = contentFocusRequester,
                    onContentFocused = onContentFocused,
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = contentStartPadding, end = 48.dp)
                        .widthIn(max = 420.dp),
                )
            }

            else -> {
                TvHomeLoadFailure(
                    message = "No Home catalogs available. Check your connection or enable catalogs in Settings.",
                    cachedContentVisible = false,
                    contentRequester = contentFocusRequester,
                    onContentFocused = onContentFocused,
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = contentStartPadding, end = 48.dp)
                        .widthIn(max = 420.dp),
                )
            }
        }
        if (rows.isNotEmpty() && error != null) {
            TvHomeLoadFailure(
                message = error,
                cachedContentVisible = true,
                contentRequester = contentFocusRequester,
                onContentFocused = onContentFocused,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 48.dp).widthIn(max = 360.dp),
            )
        }
    }
}
