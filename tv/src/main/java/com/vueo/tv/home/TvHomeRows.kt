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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlin.math.abs

private val ContinueWatchingWidth = 210.dp
private val ContinueWatchingHeight = 119.dp
private val PosterWidth = 114.dp
private val PosterHeight = 172.dp
private val ContinueShape = RoundedCornerShape(12.dp)
private val PosterShape = RoundedCornerShape(12.dp)
private val RowHorizontalPadding = 52.dp
private val RowHeaderFocusInset = 40.dp
private const val FocusedCardScale = 1.022f

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvModernHomeRows(
    rows: List<TvHomeRow>,
    rowsViewportHeight: Dp,
    contentFocusRequester: FocusRequester,
    onContentFocused: () -> Unit,
    onFocused: (TvHomeRow, Int, TvHomeEntry) -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalState = rememberLazyListState()
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val initialActiveRowKey = TvHomeFocusMemory.activeRowKey
        ?.takeIf { saved -> rows.any { it.key == saved } }
        ?: rows.firstOrNull()?.key

    val density = LocalDensity.current
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val verticalBringIntoViewSpec = remember(density, defaultBringIntoViewSpec) {
        val topInsetPx = with(density) { RowHeaderFocusInset.toPx() }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = defaultBringIntoViewSpec.scrollAnimationSpec

            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                // Mirror Nuvio Modern Home: settle each focused row around a
                // 40dp header anchor instead of snapping it flush to the top.
                val distance = offset - topInsetPx
                if (abs(distance) < 1f) return 0f
                if (distance < 0f && !verticalState.canScrollBackward) return 0f
                return distance
            }
        }
    }

    LaunchedEffect(rows) {
        val rowIndex = rows.indexOfFirst { it.key == initialActiveRowKey }.coerceAtLeast(0)
        if (rowIndex > 0) verticalState.scrollToItem(rowIndex, 0)
        delay(90)
        runCatching { contentFocusRequester.requestFocus() }
    }

    val focusRestorer = remember(rows, initialActiveRowKey) {
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
                    if (state.hasFocus) onContentFocused()
                },
            contentPadding = PaddingValues(bottom = rowsViewportHeight),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
            ) { _, row ->
                TvModernHomeRow(
                    row = row,
                    rowFocusRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() },
                    onContentFocused = onContentFocused,
                    onFocused = onFocused,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvModernHomeRow(
    row: TvHomeRow,
    rowFocusRequester: FocusRequester,
    onContentFocused: () -> Unit,
    onFocused: (TvHomeRow, Int, TvHomeEntry) -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
) {
    val savedIndex = (TvHomeFocusMemory.focusedIndexByRow[row.key] ?: 0)
        .coerceIn(0, row.entries.lastIndex)
    var focusedIndex by remember(row.key) { mutableIntStateOf(savedIndex) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = savedIndex)
    val itemFocusRequesters = remember(row.key) { mutableMapOf<Int, FocusRequester>() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current

    val horizontalBringIntoViewSpec = remember(density, layoutDirection, defaultBringIntoViewSpec) {
        val startInsetPx = with(density) { RowHorizontalPadding.toPx() }
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
            modifier = Modifier.padding(start = RowHorizontalPadding, end = 32.dp),
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoViewSpec) {
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(rowFocusRequester)
                    .focusRestorer {
                        itemFocusRequesters[focusedIndex]
                            ?: itemFocusRequesters[0]
                            ?: FocusRequester.Default
                    }
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = RowHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = row.entries,
                    key = { _, entry -> entry.key },
                ) { index, entry ->
                    val itemRequester = itemFocusRequesters.getOrPut(index) { FocusRequester() }
                    TvModernHomeCard(
                        entry = entry,
                        kind = row.kind,
                        requester = itemRequester,
                        onFocused = {
                            focusedIndex = index
                            TvHomeFocusMemory.activeRowKey = row.key
                            TvHomeFocusMemory.focusedIndexByRow[row.key] = index
                            onContentFocused()
                            onFocused(row, index, entry)
                        },
                        onOpen = { onOpen(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvModernHomeCard(
    entry: TvHomeEntry,
    kind: TvHomeRowKind,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(entry.key) { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) FocusedCardScale else 1f,
        animationSpec = tween(durationMillis = if (focused) 125 else 95),
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
            .clickable(onClick = onOpen),
    ) {
        when (kind) {
            TvHomeRowKind.CONTINUE_WATCHING -> ContinueWatchingCardContent(entry)
            TvHomeRowKind.POSTERS -> PosterCardContent(entry)
        }
    }
}

@Composable
private fun PosterCardContent(entry: TvHomeEntry) {
    TvNetworkImage(
        url = entry.media.poster ?: entry.media.background,
        contentDescription = entry.media.name,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        fallback = TvDesign.SurfaceRaised,
    )
}

@Composable
private fun ContinueWatchingCardContent(entry: TvHomeEntry) {
    val resume = entry as? TvHomeEntry.Resume
    val progress = resume?.playback?.progressFraction ?: 0f

    Box(Modifier.fillMaxSize()) {
        TvNetworkImage(
            url = entry.media.background ?: entry.media.poster,
            contentDescription = entry.media.name,
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
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
            Spacer(Modifier.height(2.dp))
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
