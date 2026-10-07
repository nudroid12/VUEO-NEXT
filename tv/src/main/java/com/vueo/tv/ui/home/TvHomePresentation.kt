package com.vueo.tv.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import android.os.SystemClock
import com.vueo.tv.ui.tvPrefetchImage
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.tvSidebarHomeRowsViewportFraction
import com.vueo.tv.ui.tvSidebarIsPillMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.vueo.tv.core.TvTitleArtwork

@Composable
internal fun TvHomePresentation(
    rows: List<TvHomeRow>,
    artworkApiKey: String,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    navigationVisible: Boolean,
    contentFocusRequester: FocusRequester,
    onContentFocused: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    onLongClick: (TvHomeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var heroScene by remember(artworkApiKey) { mutableStateOf<TvHomeHeroScene?>(null) }
    var lastNavigationAt by remember { mutableLongStateOf(0L) }
    var heroSettleDelay by remember { mutableLongStateOf(MODERN_HOME_HERO_FOCUS_SETTLE_MS) }
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val logoSize = with(density) { IntSize(220.dp.roundToPx(), 100.dp.roundToPx()) }
    val contentStartPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    val floatingPillMode = tvSidebarIsPillMode()
    val showContinueWatchingPreview = floatingPillMode && navigationVisible
    val targetRowsViewportFraction = tvSidebarHomeRowsViewportFraction(
        classic = MODERN_HOME_ROWS_VIEWPORT_FRACTION,
        pill = if (navigationVisible) 0.32f else MODERN_HOME_ROWS_VIEWPORT_FRACTION,
    )
    val rowsViewportFraction by animateFloatAsState(
        targetValue = targetRowsViewportFraction,
        animationSpec = tween(durationMillis = 180),
        label = "homeRowsViewportFraction",
    )

    LaunchedEffect(rows) {
        val currentKey = focusedEntry?.key
        val currentStillAvailable =
            currentKey != null &&
                rows.any { row -> row.entries.any { entry -> entry.key == currentKey } }

        // Progressive catalog updates only append rows. Keep the current TV
        // focus/hero untouched unless the focused item genuinely disappeared.
        if (currentStillAvailable) return@LaunchedEffect

        val savedRowKey = TvHomeFocusMemory.activeRowKey
        val savedRow = rows.firstOrNull { it.key == savedRowKey }
        val initial = if (savedRow != null) {
            val index = (TvHomeFocusMemory.focusedIndexByRow[savedRow.key] ?: 0)
                .coerceIn(0, savedRow.entries.lastIndex)
            savedRow.entries[index]
        } else {
            rows.firstNotNullOfOrNull { it.entries.firstOrNull() }
        }
        focusedEntry = initial
    }

    // Keep the displayed scene while enrichment is pending. A newer focus
    // cancels this job, so an old lookup cannot replace the current selection.
    LaunchedEffect(focusedEntry?.key, artworkApiKey) {
        val next = focusedEntry ?: return@LaunchedEffect
        if (heroScene != null) delay(heroSettleDelay)
        val artwork = try {
            TvTitleArtwork.load(next.media, artworkApiKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null // Optional artwork failure still publishes the text title.
        }
        if (focusedEntry?.key == next.key) heroScene = TvHomeHeroScene(next, artwork)
    }

    // Preload one adjacent item only after Home has had time to paint the
    // focused hero. This stays best-effort and cancellable so optional artwork
    // work cannot compete with cold-start first-frame rendering.
    LaunchedEffect(focusedEntry?.key, artworkApiKey, rows) {
        val selected = focusedEntry ?: return@LaunchedEffect
        val row = rows.firstOrNull { it.entries.any { item -> item.key == selected.key } }
            ?: return@LaunchedEffect
        val index = row.entries.indexOfFirst { it.key == selected.key }
        val adjacent = row.entries.getOrNull(index + 1) ?: row.entries.getOrNull(index - 1)
            ?: return@LaunchedEffect
        delay(750L)
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
        val rowsViewportHeight = screenHeight * rowsViewportFraction
        val heroHeight = (screenHeight - rowsViewportHeight + 38.dp).coerceAtMost(screenHeight)

        TvModernHomeHero(
            scene = heroScene,
            heroHeight = heroHeight,
            rowsViewportHeight = rowsViewportHeight,
        )

        when {
            rows.isNotEmpty() -> {
                TvModernHomeRows(
                    rows = rows,
                    rowsViewportHeight = rowsViewportHeight,
                    showContinueWatchingPreview = showContinueWatchingPreview,
                    contentFocusRequester = contentFocusRequester,
                    onContentFocused = onContentFocused,
                    onLeftAtRowStart =
                        if (floatingPillMode) ({ /* Left boundary stays on the first card. */ }) else null,
                    onFocused = { row, index, entry ->
                        TvHomeFocusMemory.activeRowKey = row.key
                        TvHomeFocusMemory.focusedIndexByRow[row.key] = index
                        if (focusedEntry?.key != entry.key) {
                            val now = SystemClock.uptimeMillis()
                            heroSettleDelay = if (lastNavigationAt != 0L && now - lastNavigationAt < 130L) 400L
                                else MODERN_HOME_HERO_FOCUS_SETTLE_MS
                            lastNavigationAt = now
                        }
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
