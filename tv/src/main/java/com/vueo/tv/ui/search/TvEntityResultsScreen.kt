package com.vueo.tv.search

import com.vueo.tv.ui.motion.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.search.EntityResultOrder
import com.vueo.shared.core.search.MediaEntityKind
import com.vueo.shared.core.search.MediaEntityTarget
import com.vueo.shared.core.search.SearchOrchestrator
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val ENTITY_COLUMNS = 6

@Composable
internal fun TvEntityResultsScreen(
    runtime: TvRuntime,
    target: MediaEntityTarget,
    active: Boolean = true,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    BackHandler(enabled = active, onBack = onBack)

    var results by remember(target) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember(target) { mutableStateOf(true) }
    var resultOrder by remember(target) { mutableStateOf(EntityResultOrder.NEWEST) }
    val orderedResults = remember(results, resultOrder) {
        SearchOrchestrator.orderEntityResults(results, resultOrder)
    }
    val newestRequester = remember(target) { FocusRequester() }
    val popularRequester = remember(target) { FocusRequester() }
    val selectedOrderRequester =
        if (resultOrder == EntityResultOrder.NEWEST) newestRequester else popularRequester
    val gridState = rememberLazyGridState()
    var focusedMediaKey by remember(target) { mutableStateOf<String?>(null) }
    val resultKeys = remember(orderedResults) {
        orderedResults.map { "${it.type}:${it.id}" }
    }
    // Keep one requester per title while partial discovery and sorting update
    // the result list. Requesters must not move between lazy grid cells.
    val requesters = remember(target) { mutableMapOf<String, FocusRequester>() }
    resultKeys.forEach { key -> requesters.getOrPut(key) { FocusRequester() } }
    val latestActive = rememberUpdatedState(active)
    val focusScope = rememberCoroutineScope()
    var focusMoveJob by remember(target) { mutableStateOf<Job?>(null) }
    var restoreFocusPending by remember(target) { mutableStateOf(true) }

    suspend fun focusPosterAt(index: Int): Boolean {
        if (!latestActive.value || index !in resultKeys.indices) return false
        val key = resultKeys[index]
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.scrollToItem(index)
        }
        // A visible index can still be waiting for composition after scroll/data
        // changes. Confirm actual focus success, not merely absence of exception.
        repeat(5) {
            withFrameNanos { }
            if (!latestActive.value) return false
            if (runCatching { requesters[key]?.requestFocus() == true }.getOrDefault(false)) return true
        }
        return false
    }

    fun moveToPoster(index: Int) {
        if (!active || index !in resultKeys.indices || focusMoveJob?.isActive == true) return
        restoreFocusPending = false
        focusMoveJob = focusScope.launch { focusPosterAt(index) }
    }

    fun moveFromPoster(index: Int, direction: Int) {
        when (direction) {
            KeyEvent.KEYCODE_DPAD_UP -> if (index < ENTITY_COLUMNS) {
                restoreFocusPending = false
                runCatching { selectedOrderRequester.requestFocus() }
            } else moveToPoster(index - ENTITY_COLUMNS)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (index / ENTITY_COLUMNS < resultKeys.lastIndex / ENTITY_COLUMNS) {
                moveToPoster((index + ENTITY_COLUMNS).coerceAtMost(resultKeys.lastIndex))
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (index % ENTITY_COLUMNS > 0) moveToPoster(index - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (index % ENTITY_COLUMNS < ENTITY_COLUMNS - 1) moveToPoster(index + 1)
        }
    }

    LaunchedEffect(target) {
        loading = true
        results = emptyList()
        results = SearchOrchestrator.entityResults(
            engine = runtime.engine,
            target = target,
            tmdbApiKey = runtime.pluginStore.tmdbApiKey(),
            onPartial = { partial ->
                if (partial.isNotEmpty()) results = partial
            },
        )
        loading = false
    }

    LaunchedEffect(active, resultKeys.isEmpty()) {
        if (active && resultKeys.isEmpty()) {
            withFrameNanos { }
            if (latestActive.value) runCatching { selectedOrderRequester.requestFocus() }
        }
    }

    LaunchedEffect(active, resultKeys, restoreFocusPending) {
        if (!active) {
            focusMoveJob?.cancel()
            restoreFocusPending = true
            return@LaunchedEffect
        }
        if (!restoreFocusPending || resultKeys.isEmpty()) return@LaunchedEffect
        val index = resultKeys.indexOf(focusedMediaKey).takeIf { it >= 0 } ?: 0
        if (focusPosterAt(index)) restoreFocusPending = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp, end = 52.dp, top = 34.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = target.name,
                    color = TvDesign.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (target.kind) {
                        MediaEntityKind.ACTOR -> "Cast titles"
                        MediaEntityKind.COMPANY -> "Production titles"
                        MediaEntityKind.NETWORK -> "Network titles"
                    },
                    color = TvDesign.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvEntitySortTab(
                        label = "Newest",
                        selected = resultOrder == EntityResultOrder.NEWEST,
                        requester = newestRequester,
                        leftRequester = FocusRequester.Cancel,
                        rightRequester = popularRequester,
                        onDown = { moveToPoster(0) },
                        onClick = {
                            focusedMediaKey = null
                            restoreFocusPending = true
                            resultOrder = EntityResultOrder.NEWEST
                        },
                    )
                    TvEntitySortTab(
                        label = "Popular",
                        selected = resultOrder == EntityResultOrder.POPULAR,
                        requester = popularRequester,
                        leftRequester = newestRequester,
                        rightRequester = FocusRequester.Cancel,
                        onDown = { moveToPoster(0) },
                        onClick = {
                            focusedMediaKey = null
                            restoreFocusPending = true
                            resultOrder = EntityResultOrder.POPULAR
                        },
                    )
                }
            }
            Text(
                text = if (loading) "Loading…" else "${results.size} titles",
                color = TvDesign.Muted,
                fontSize = 11.sp,
            )
        }

        when {
            orderedResults.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ENTITY_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 68.dp,
                        end = 52.dp,
                        top = 4.dp,
                        bottom = 36.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(
                        items = orderedResults,
                        key = { _, item -> "${target.kind}:${item.type}:${item.id}" },
                    ) { index, item ->
                        val mediaKey = "${item.type}:${item.id}"
                        TvEntityPosterTile(
                            item = item,
                            requester = requesters.getValue(mediaKey),
                            upRequester = if (index < ENTITY_COLUMNS) {
                                selectedOrderRequester
                            } else {
                                null
                            },
                            onDirection = { direction -> moveFromPoster(index, direction) },
                            onFocused = { focusedMediaKey = mediaKey },
                            onClick = {
                                focusedMediaKey = mediaKey
                                onOpenMedia(item)
                            },
                        )
                    }
                }
            }

            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = TvDesign.White,
                        strokeWidth = 2.dp,
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (target.kind) {
                            MediaEntityKind.ACTOR -> "No titles found for this cast member."
                            MediaEntityKind.COMPANY -> "No titles found for this production company."
                            MediaEntityKind.NETWORK -> "No titles found for this network."
                        },
                        color = TvDesign.Muted,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvEntitySortTab(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) onDown()
                    true
                } else false
            }
            .background(
                when {
                    focused -> TvDesign.SurfaceRaised
                    selected -> Color.White.copy(alpha = .12f)
                    else -> Color.White.copy(alpha = .04f)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.Focus
                    selected -> Color.White.copy(alpha = .26f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = TvDesign.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TvEntityPosterTile(
    item: MediaItem,
    requester: FocusRequester,
    upRequester: FocusRequester? = null,
    onDirection: (Int) -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.035f) else 1f,
        animationSpec = tvTunedSpec(TvMotionGroup.FOCUS, focused, tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        )),
        label = "entityPosterScale",
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 8.dp.toPx() else 0f
            }
            .focusRequester(requester)
            .focusProperties {
                if (upRequester != null) {
                    up = upRequester
                }
            }
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val direction = event.nativeKeyEvent.keyCode
                if (direction in listOf(
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                )) {
                    if (event.type == KeyEventType.KeyDown) onDirection(direction)
                    true
                } else false
            }
            .clickable(onClick = onClick),
    ) {
        TvNetworkImage(
            url = item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.Focus else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name,
            color = TvDesign.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let { release ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = release,
                color = TvDesign.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
