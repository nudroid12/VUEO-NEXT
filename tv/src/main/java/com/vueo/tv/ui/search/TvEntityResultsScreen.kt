package com.vueo.tv.search

import com.vueo.tv.ui.motion.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.vueo.shared.core.search.EntityResultsPipeline
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import kotlinx.coroutines.flow.collect
import com.vueo.shared.core.search.SearchOrchestrator
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.delay

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

    val entityEngine = runtime.engine
    val tmdbApiKey = runtime.pluginStore.tmdbApiKey()
    val requestKey = remember(entityEngine, target, tmdbApiKey) { Any() }
    val latestRequestKey by androidx.compose.runtime.rememberUpdatedState(requestKey)
    var retryGeneration by remember(requestKey) { mutableStateOf(0) }
    val loadKey = remember(requestKey, retryGeneration) { Any() }
    val latestLoadKey by androidx.compose.runtime.rememberUpdatedState(loadKey)
    var results by remember(requestKey) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember(requestKey) { mutableStateOf(true) }
    var loadFailed by remember(requestKey) { mutableStateOf(false) }
    var preparationFailed by remember(requestKey) { mutableStateOf(false) }
    var resultOrder by remember(target) { mutableStateOf(EntityResultOrder.NEWEST) }
    // Keep the current grid during same-target updates, including sort changes.
    var orderedResults by remember(requestKey) { mutableStateOf<List<MediaItem>>(emptyList()) }
    val sortKey = remember(loadKey, results, resultOrder) { Any() }
    val latestSortKey by androidx.compose.runtime.rememberUpdatedState(sortKey)
    var preparedSortKey by remember(requestKey) { mutableStateOf<Any?>(null) }
    val preparing = preparedSortKey !== sortKey
    val busy = loading || preparing
    val failed = loadFailed || preparationFailed

    LaunchedEffect(loadKey) {
        loading = true
        loadFailed = false
        results = emptyList()
        try {
            EntityResultsPipeline.batches { publish ->
                PerformanceDiagnostics.measureWork("tv_entity_load", PerformanceDiagnostics.Tab.DETAILS) {
                    SearchOrchestrator.entityResults(
                        engine = entityEngine,
                        target = target,
                        tmdbApiKey = tmdbApiKey,
                        onPartial = publish,
                    )
                }
            }.collect { batch ->
                if (latestRequestKey === requestKey && latestLoadKey === loadKey) {
                    results = batch.items
                    loading = !batch.complete
                    loadFailed = batch.failureClass != null
                    if (batch.failureClass != null) {
                        PerformanceDiagnostics.captureRuntimeEvent(
                            "ENTITY_RESULTS platform=tv event=load_failed error=${batch.failureClass}",
                        )
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (latestRequestKey === requestKey && latestLoadKey === loadKey) {
                loadFailed = true
                PerformanceDiagnostics.captureRuntimeEvent(
                    "ENTITY_RESULTS platform=tv event=load_failed error=${error.javaClass.simpleName}",
                )
            }
        } finally {
            if (latestRequestKey === requestKey && latestLoadKey === loadKey) loading = false
        }
    }

    LaunchedEffect(sortKey) {
        val inputResults = results
        val inputOrder = resultOrder
        preparationFailed = false
        try {
            val prepared = EntityResultsPipeline.prepare {
                PerformanceDiagnostics.measureWork("tv_entity_prepare", PerformanceDiagnostics.Tab.DETAILS) {
                    SearchOrchestrator.orderEntityResults(inputResults, inputOrder)
                }
            }
            if (latestRequestKey === requestKey && latestSortKey === sortKey) orderedResults = prepared
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (latestSortKey === sortKey) {
                preparationFailed = true
                PerformanceDiagnostics.captureRuntimeEvent(
                    "ENTITY_RESULTS platform=tv event=prepare_failed error=${error.javaClass.simpleName}",
                )
            }
        } finally {
            if (latestRequestKey === requestKey && latestSortKey === sortKey) preparedSortKey = sortKey
        }
    }
    val newestRequester = remember(target) { FocusRequester() }
    val popularRequester = remember(target) { FocusRequester() }
    val selectedOrderRequester =
        if (resultOrder == EntityResultOrder.NEWEST) newestRequester else popularRequester
    val gridState = rememberLazyGridState()
    var scrolledOrder by remember(target) { mutableStateOf(resultOrder) }
    LaunchedEffect(resultOrder) {
        if (scrolledOrder != resultOrder) gridState.scrollToItem(0)
        scrolledOrder = resultOrder
    }
    var focusedMediaKey by remember(target) { mutableStateOf<String?>(null) }
    val resultKeys = remember(orderedResults) {
        orderedResults.map { "${it.type}:${it.id}" }
    }
    val requesterPool = remember(requestKey) { mutableMapOf<String, FocusRequester>() }
    val requesters = remember(requestKey, resultKeys) {
        requesterPool.keys.retainAll(resultKeys.toSet())
        resultKeys.associateWith { key -> requesterPool.getOrPut(key) { FocusRequester() } }
    }
    val firstRequester = resultKeys.firstOrNull()?.let(requesters::get) ?: FocusRequester.Cancel
    var newestFocused by remember(target) { mutableStateOf(false) }
    var popularFocused by remember(target) { mutableStateOf(false) }
    val latestFocusedMediaKey by androidx.compose.runtime.rememberUpdatedState(focusedMediaKey)

    LaunchedEffect(active, resultKeys, busy, newestFocused, popularFocused) {
        if (!active || busy || newestFocused || popularFocused || resultKeys.isEmpty()) return@LaunchedEffect
        val restoreIndex = latestFocusedMediaKey?.let(resultKeys::indexOf)?.takeIf { it >= 0 } ?: 0
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == restoreIndex }) {
            gridState.scrollToItem(restoreIndex)
        }
        delay(90)
        val focusedKey = latestFocusedMediaKey
        val restored = focusedKey?.let { key ->
            requesters[key]?.let { requester ->
                runCatching { requester.requestFocus() }.isSuccess
            }
        } == true
        if (!restored) {
            runCatching { firstRequester.requestFocus() }
        }
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
                        downRequester = firstRequester,
                        onFocusChanged = { newestFocused = it },
                        onClick = { resultOrder = EntityResultOrder.NEWEST },
                    )
                    TvEntitySortTab(
                        label = "Popular",
                        selected = resultOrder == EntityResultOrder.POPULAR,
                        requester = popularRequester,
                        leftRequester = newestRequester,
                        rightRequester = FocusRequester.Cancel,
                        downRequester = firstRequester,
                        onFocusChanged = { popularFocused = it },
                        onClick = { resultOrder = EntityResultOrder.POPULAR },
                    )
                }
            }
            Text(
                text = if (busy) "Loading…" else "${orderedResults.size} titles",
                color = TvDesign.Muted,
                fontSize = 11.sp,
            )
        }

        if (failed) {
            androidx.compose.material3.TextButton(
                onClick = { retryGeneration++ },
                modifier = Modifier.padding(horizontal = 68.dp),
            ) {
                Text("Couldn't load all titles. Retry", color = TvDesign.White)
            }
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
                            onFocused = { focusedMediaKey = mediaKey },
                            onClick = {
                                focusedMediaKey = mediaKey
                                onOpenMedia(item)
                            },
                        )
                    }
                }
            }

            busy -> {
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
                        text = if (failed) "Go back or retry to load titles." else when (target.kind) {
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
    downRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                right = rightRequester
                down = downRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
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
            .focusable()
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
            .clickable(onClick = onClick)
            .focusable(),
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
