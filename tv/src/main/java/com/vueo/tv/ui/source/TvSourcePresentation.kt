package com.vueo.tv.source

import android.os.SystemClock
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.StreamSource
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.delay

private val SourceHorizontalOuterPadding = 52.dp
private val SourceRightTopPadding = 48.dp
private val SourcePanelShape = RoundedCornerShape(18.dp)
private val SourceCardShape = RoundedCornerShape(12.dp)
private val SourceChipShape = RoundedCornerShape(20.dp)
private const val SourceKeyRepeatThrottleMs = 112L

/** Vueo-inspired 40/60 TV source-selection composition. */
@Composable
internal fun TvSourcePresentation(
    active: Boolean = true,
    initialFocusedSourceKey: String? = null,
    state: TvSourcePresentationState,
    onSelectProvider: (String) -> Unit,
    onToggleDetails: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onSourceFocused: (StreamSource) -> Unit,
    onPlay: (StreamSource) -> Unit,
) {
    val listState = rememberLazyListState()
    val refreshRequester = remember(state.media.id, state.episode?.id) { FocusRequester() }
    val retryRequester = remember(state.media.id, state.episode?.id) { FocusRequester() }
    val allRequester = remember(state.media.id, state.episode?.id) { FocusRequester() }
    val detailsRequester = remember(state.media.id, state.episode?.id) { FocusRequester() }
    val providerRequesters = remember(state.media.id, state.episode?.id) {
        mutableMapOf<String, FocusRequester>()
    }
    val sourceRequesters = remember(state.media.id, state.episode?.id) {
        mutableMapOf<String, FocusRequester>()
    }
    var sourceFocusAssigned by remember(state.media.id, state.episode?.id) { mutableStateOf(false) }
    var userInteracted by remember(state.media.id, state.episode?.id) { mutableStateOf(false) }
    var focusFirstAfterProviderCycle by remember(state.media.id, state.episode?.id) { mutableStateOf(false) }
    var lastProviderCycleMs by remember { mutableLongStateOf(0L) }

    fun providerRequester(provider: String): FocusRequester =
        if (provider == SOURCE_PROVIDER_ALL) allRequester
        else providerRequesters.getOrPut(provider) { FocusRequester() }

    fun sourceRequester(source: StreamSource): FocusRequester =
        sourceRequesters.getOrPut(sourceStableKey(source)) { FocusRequester() }

    fun selectedProviderRequester(): FocusRequester =
        providerRequester(
            state.selectedProvider.takeIf { it in state.visibleProviders }
                ?: SOURCE_PROVIDER_ALL
        )

    fun cycleProvider(delta: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastProviderCycleMs < SourceKeyRepeatThrottleMs) return true
        lastProviderCycleMs = now

        val options = listOf(SOURCE_PROVIDER_ALL) + state.visibleProviders
        if (options.size <= 1) return false
        val current = options.indexOf(state.selectedProvider).coerceAtLeast(0)
        val target = current + delta
        if (target !in options.indices) return false
        userInteracted = true
        focusFirstAfterProviderCycle = true
        onSelectProvider(options[target])
        return true
    }

    LaunchedEffect(state.filteredSources, sourceFocusAssigned, userInteracted, active) {
        if (!active) return@LaunchedEffect
        if (userInteracted || sourceFocusAssigned || state.filteredSources.isEmpty()) {
            return@LaunchedEffect
        }
        val restoredIndex = state.filteredSources.indexOfFirst {
            sourceStableKey(it) == initialFocusedSourceKey
        }.coerceAtLeast(0)
        val target = state.filteredSources[restoredIndex]
        listState.scrollToItem(restoredIndex)
        delay(90)
        if (userInteracted) return@LaunchedEffect
        runCatching { sourceRequester(target).requestFocus() }
        sourceFocusAssigned = true
    }

    LaunchedEffect(state.selectedProvider, state.filteredSources, focusFirstAfterProviderCycle, active) {
        if (!active) return@LaunchedEffect
        if (!focusFirstAfterProviderCycle || state.filteredSources.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(0)
        delay(60)
        if (!focusFirstAfterProviderCycle) return@LaunchedEffect
        runCatching { sourceRequester(state.filteredSources.first()).requestFocus() }
        focusFirstAfterProviderCycle = false
    }

    LaunchedEffect(state.searching, state.error, state.rankedSources.isEmpty(), userInteracted, active) {
        if (!active) return@LaunchedEffect
        if (userInteracted || state.searching) return@LaunchedEffect
        if (state.rankedSources.isEmpty()) {
            delay(90)
            val target = if (state.error != null) retryRequester else refreshRequester
            runCatching { target.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        SourceBackdrop(
            url = state.media.background ?: state.media.poster,
            loading = state.searching,
        )

        Row(Modifier.fillMaxSize()) {
            SourceIdentitySection(
                state = state,
                detailsRequester = detailsRequester,
                refreshRequester = refreshRequester,
                onToggleDetails = onToggleDetails,
                modifier = Modifier
                    .weight(.40f)
                    .fillMaxHeight(),
            )

            SourceResultsSection(
                state = state,
                listState = listState,
                refreshRequester = refreshRequester,
                retryRequester = retryRequester,
                allRequester = allRequester,
                providerRequester = ::providerRequester,
                sourceRequester = ::sourceRequester,
                selectedProviderRequester = ::selectedProviderRequester,
                onInteraction = {
                    userInteracted = true
                    focusFirstAfterProviderCycle = false
                },
                onSelectProvider = onSelectProvider,
                onRefresh = {
                    userInteracted = true
                    sourceFocusAssigned = false
                    onRefresh()
                },
                onStop = {
                    userInteracted = true
                    onStop()
                },
                onSourceFocused = onSourceFocused,
                onCycleProvider = ::cycleProvider,
                onPlay = onPlay,
                modifier = Modifier
                    .weight(.60f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SourceBackdrop(
    url: String?,
    loading: Boolean,
) {
    val alpha by animateFloatAsState(
        targetValue = if (loading) .70f else .50f,
        animationSpec = tween(
            durationMillis = TvMotion.BACKDROP_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "sourceBackdropAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        TvNetworkImage(
            url = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to TvDesign.Black,
                            .15f to TvDesign.Black.copy(alpha = .85f),
                            .30f to TvDesign.Black.copy(alpha = .40f),
                            .50f to TvDesign.Black.copy(alpha = .15f),
                            .70f to TvDesign.Black.copy(alpha = .40f),
                            .85f to TvDesign.Black.copy(alpha = .85f),
                            1.0f to TvDesign.Black,
                        )
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to TvDesign.Black.copy(alpha = .10f),
                        .62f to Color.Transparent,
                        1f to TvDesign.Black.copy(alpha = .58f),
                    )
                ),
        )
    }
}

@Composable
private fun SourceIdentitySection(
    state: TvSourcePresentationState,
    detailsRequester: FocusRequester,
    refreshRequester: FocusRequester,
    onToggleDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeLabel = sourceEpisodeLabel(state.episode)
    val mediaInfo = remember(state.media) {
        listOfNotNull(
            state.media.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" • "),
            state.media.releaseInfo?.takeIf(String::isNotBlank),
            state.media.runtimeMinutes?.takeIf { it > 0 }?.let(::formatRuntimeMinutes),
        ).joinToString(" • ")
    }

    Box(
        modifier = modifier.padding(start = SourceHorizontalOuterPadding, end = 28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(.82f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.media.name,
                color = TvDesign.White,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            episodeLabel?.let { label ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    color = TvDesign.White.copy(alpha = .84f),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            if (mediaInfo.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = mediaInfo,
                    color = TvDesign.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))
            SourceDiscoveryStatus(state)

            Spacer(Modifier.height(15.dp))
            SourceDetailsButton(
                selected = state.showEngineDetails,
                requester = detailsRequester,
                rightRequester = refreshRequester,
                onClick = onToggleDetails,
            )
        }
    }
}

@Composable
private fun SourceDetailsButton(
    selected: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(34.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    runCatching { rightRequester.requestFocus() }
                    true
                } else {
                    false
                }
            }
            .background(
                color = when {
                    focused -> TvDesign.White.copy(alpha = .18f)
                    selected -> TvDesign.Accent.copy(alpha = .14f)
                    else -> TvDesign.Surface.copy(alpha = .72f)
                },
                shape = SourceChipShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.White.copy(alpha = .92f)
                    selected -> TvDesign.Accent.copy(alpha = .46f)
                    else -> TvDesign.White.copy(alpha = .10f)
                },
                shape = SourceChipShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Details",
            color = when {
                focused -> TvDesign.White
                selected -> TvDesign.Accent
                else -> TvDesign.White.copy(alpha = .72f)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SourceDiscoveryStatus(state: TvSourcePresentationState) {
    val discoveryActive = state.searching || state.loadingProviders.isNotEmpty()
    if (discoveryActive) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(.76f)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = TvDesign.Accent,
            trackColor = TvDesign.White.copy(alpha = .10f),
        )
        Spacer(Modifier.height(10.dp))
    }

    Text(
        text = when {
            discoveryActive -> state.progress.ifBlank {
                "Searching • ${state.rankedSources.size} found"
            }
            state.rankedSources.isNotEmpty() ->
                "${state.rankedSources.size} playable • ${state.visibleProviders.size} providers"
            else -> "No playable sources"
        },
        color = if (discoveryActive) TvDesign.White.copy(alpha = .82f) else TvDesign.Muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )

    if (state.showEngineDetails) {
        Spacer(Modifier.height(11.dp))
        val detailLines = buildList {
            state.firstResultMs?.let { add("First source in $it ms") }
            val uniqueCount = state.bundle?.sources.orEmpty().size
            if (state.rawCount > uniqueCount) {
                add("${state.rawCount} raw • ${state.rawCount - uniqueCount} duplicates removed")
            }
            if (state.fromCache && state.searching) add("Showing cache while fresh providers respond")
            state.notice?.takeIf(String::isNotBlank)?.let(::add)
        }
        detailLines.forEach { line ->
            Text(
                text = line,
                color = TvDesign.Dim,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SourceResultsSection(
    state: TvSourcePresentationState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    refreshRequester: FocusRequester,
    retryRequester: FocusRequester,
    allRequester: FocusRequester,
    providerRequester: (String) -> FocusRequester,
    sourceRequester: (StreamSource) -> FocusRequester,
    selectedProviderRequester: () -> FocusRequester,
    onInteraction: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onSourceFocused: (StreamSource) -> Unit,
    onCycleProvider: (Int) -> Boolean,
    onPlay: (StreamSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val discoveryActive = state.searching || state.loadingProviders.isNotEmpty()
    val selectedProviderLoading =
        state.selectedProvider != SOURCE_PROVIDER_ALL &&
            state.selectedProvider in state.loadingProviders
    val sourceFocusScope = rememberCoroutineScope()
    val latestFilteredSources = rememberUpdatedState(state.filteredSources)
    val sourceEntryJob = remember { arrayOfNulls<Job>(1) }

    fun focusFirstSource(): Boolean {
        val target = latestFilteredSources.value.firstOrNull() ?: return false
        val targetKey = sourceStableKey(target)
        sourceEntryJob[0]?.cancel()
        sourceEntryJob[0] = sourceFocusScope.launch {
            try {
                repeat(6) {
                    val latest = latestFilteredSources.value
                    val index = latest.indexOfFirst { sourceStableKey(it) == targetKey }
                    if (index < 0) return@launch
                    if (listState.layoutInfo.visibleItemsInfo.none { it.key == targetKey }) {
                        listState.scrollToItem(index)
                    }
                    withFrameNanos { }
                    val current = latestFilteredSources.value
                        .firstOrNull { sourceStableKey(it) == targetKey }
                        ?: return@launch
                    if (runCatching { sourceRequester(current).requestFocus() }.getOrDefault(false)) {
                        return@launch
                    }
                    delay(16L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("VUEO_SOURCE_FOCUS", "Source-list entry focus failed", error)
            }
        }
        return true
    }

    DisposableEffect(Unit) {
        onDispose { sourceEntryJob[0]?.cancel() }
    }

    Column(
        modifier = modifier.padding(
            top = SourceRightTopPadding,
            end = SourceHorizontalOuterPadding,
            bottom = 44.dp,
        ),
    ) {
        SourceFilterRow(
            state = state,
            refreshRequester = refreshRequester,
            allRequester = allRequester,
            providerRequester = providerRequester,
            onFocusFirstSource = ::focusFirstSource,
            onInteraction = onInteraction,
            onSelectProvider = onSelectProvider,
            onRefresh = onRefresh,
            onStop = onStop,
        )

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(SourcePanelShape)
                .background(TvDesign.Surface.copy(alpha = .52f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.error != null && state.rankedSources.isEmpty() -> {
                    SourceMessageState(
                        title = "Source discovery failed",
                        message = state.error,
                        actionLabel = "Retry",
                        actionRequester = retryRequester,
                        onAction = onRefresh,
                    )
                }

                discoveryActive && state.rankedSources.isEmpty() -> {
                    SourceSkeletonList()
                }

                !discoveryActive && state.rankedSources.isEmpty() -> {
                    SourceMessageState(
                        title = "No playable sources",
                        message = if (state.bundle?.sources.orEmpty().isEmpty()) {
                            "No sources were returned for this title."
                        } else {
                            "Sources were found, but none can be played directly by VUEO."
                        },
                        actionLabel = "Retry",
                        actionRequester = retryRequester,
                        onAction = onRefresh,
                    )
                }

                selectedProviderLoading && state.filteredSources.isEmpty() -> {
                    SourceSkeletonList()
                }

                state.filteredSources.isEmpty() -> {
                    SourceMessageState(
                        title = "No sources from this provider",
                        message = "Choose another provider above.",
                    )
                }

                else -> {
                    SourceList(
                        state = state,
                        listState = listState,
                        sourceRequester = sourceRequester,
                        selectedProviderRequester = selectedProviderRequester,
                        onInteraction = onInteraction,
                        onSourceFocused = onSourceFocused,
                        onCycleProvider = onCycleProvider,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    state: TvSourcePresentationState,
    refreshRequester: FocusRequester,
    allRequester: FocusRequester,
    providerRequester: (String) -> FocusRequester,
    onFocusFirstSource: () -> Boolean,
    onInteraction: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
) {
    val discoveryActive = state.searching || state.loadingProviders.isNotEmpty()
    val chips = buildList {
        if (state.rankedSources.isNotEmpty() || state.visibleProviders.isNotEmpty()) {
            add(
                SourceChip(
                    id = SOURCE_PROVIDER_ALL,
                    label = "All",
                    selected = state.selectedProvider == SOURCE_PROVIDER_ALL,
                    loading = false,
                    requester = allRequester,
                    action = { onSelectProvider(SOURCE_PROVIDER_ALL) },
                )
            )
            state.visibleProviders.forEach { provider ->
                add(
                    SourceChip(
                        id = provider,
                        label = sourceProviderDisplayName(provider),
                        selected = state.selectedProvider == provider,
                        loading = provider in state.loadingProviders,
                        requester = providerRequester(provider),
                        action = { onSelectProvider(provider) },
                    )
                )
            }
        }
    }

    val chipListState = rememberLazyListState()
    val navigationScope = rememberCoroutineScope()
    val latestChips = rememberUpdatedState(chips)
    val navigationJob = remember { arrayOfNulls<Job>(1) }
    var focusedChipId by remember { mutableStateOf<String?>(null) }
    var pendingChipId by remember { mutableStateOf<String?>(null) }
    var navigationGeneration by remember { mutableLongStateOf(0L) }

    fun cancelNavigation() {
        navigationGeneration += 1
        navigationJob[0]?.cancel()
        navigationJob[0] = null
        pendingChipId = null
    }

    fun moveChip(delta: Int): Boolean {
        onInteraction()
        val currentChips = latestChips.value
        val originId = pendingChipId ?: focusedChipId
        val origin = currentChips.indexOfFirst { it.id == originId }
        val targetIndex = origin + delta
        if (targetIndex < 0) {
            cancelNavigation()
            focusedChipId = null
            return if (origin >= 0) {
                runCatching { refreshRequester.requestFocus() }
                true
            } else false
        }
        val target = currentChips.getOrNull(targetIndex) ?: return true
        cancelNavigation()
        pendingChipId = target.id
        val generation = navigationGeneration
        navigationJob[0] = navigationScope.launch {
            try {
                // An offscreen lazy item has no attached FocusRequester yet.
                repeat(4) {
                    val index = latestChips.value.indexOfFirst { it.id == target.id }
                    if (index < 0) return@launch
                    if (chipListState.layoutInfo.visibleItemsInfo.none { it.key == target.id }) {
                        chipListState.scrollToItem(index)
                    }
                    withFrameNanos { }
                    if (navigationGeneration != generation ||
                        latestChips.value.none { it.id == target.id }
                    ) return@launch
                    if (runCatching { target.requester.requestFocus() }.getOrDefault(false)) {
                        return@launch
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("VUEO_SOURCE_FOCUS", "Provider tab navigation failed", error)
            } finally {
                if (navigationGeneration == generation) pendingChipId = null
            }
        }
        return true
    }

    DisposableEffect(Unit) {
        onDispose { navigationJob[0]?.cancel() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> moveChip(-1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveChip(1)
                    else -> {
                        // A newer nonhorizontal intent must never be followed by
                        // a late tab focus request from an earlier key press.
                        cancelNavigation()
                        false
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceRefreshButton(
            searching = discoveryActive,
            requester = refreshRequester,
            rightRequester = chips.firstOrNull()?.requester,
            onDown = onFocusFirstSource,
            onInteraction = onInteraction,
            onClick = if (discoveryActive) onStop else onRefresh,
        )
        Spacer(Modifier.width(8.dp))
        LazyRow(
            state = chipListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(chips, key = { _, chip -> chip.id }) { index, chip ->
                SourceFilterChip(
                    label = chip.label,
                    selected = chip.selected,
                    loading = chip.loading,
                    requester = chip.requester,
                    leftRequester = chips.getOrNull(index - 1)?.requester ?: refreshRequester,
                    rightRequester = chips.getOrNull(index + 1)?.requester,
                    onDown = onFocusFirstSource,
                    onInteraction = onInteraction,
                    onFocused = {
                        focusedChipId = chip.id
                        chip.action()
                    },
                    onClick = chip.action,
                )
            }
        }
    }
}

@Composable
private fun SourceRefreshButton(
    searching: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester?,
    onDown: () -> Boolean,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(34.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> rightRequester?.let {
                        onInteraction()
                        runCatching { it.requestFocus() }
                        true
                    } ?: false
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onInteraction()
                        onDown()
                    }
                    else -> false
                }
            }
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .18f)
                else TvDesign.Surface.copy(alpha = .74f),
                shape = SourceChipShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .92f)
                else TvDesign.White.copy(alpha = .10f),
                shape = SourceChipShape,
            )
            .clickable {
                onInteraction()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = if (searching) "Stop source scan" else "Refresh sources",
            tint = when {
                searching -> TvDesign.Accent
                focused -> TvDesign.White
                else -> TvDesign.White.copy(alpha = .72f)
            },
            modifier = Modifier
                .width(17.dp)
                .height(17.dp),
        )
    }
}

private data class SourceChip(
    val id: String,
    val label: String,
    val selected: Boolean,
    val loading: Boolean,
    val requester: FocusRequester,
    val action: () -> Unit,
)

@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    loading: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    onDown: () -> Boolean,
    onInteraction: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(34.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> leftRequester?.let {
                        onInteraction()
                        runCatching { it.requestFocus() }
                        true
                    } ?: false
                    KeyEvent.KEYCODE_DPAD_RIGHT -> rightRequester?.let {
                        onInteraction()
                        runCatching { it.requestFocus() }
                        true
                    } ?: false
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onInteraction()
                        onDown()
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    focused || selected -> TvDesign.White
                    else -> TvDesign.Surface.copy(alpha = .74f)
                },
                shape = SourceChipShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused || selected -> TvDesign.White
                    else -> TvDesign.White.copy(alpha = .10f)
                },
                shape = SourceChipShape,
            )
            .clickable {
                onInteraction()
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.width(11.dp).height(11.dp),
                color = if (focused || selected) TvDesign.Black.copy(alpha = .64f)
                else TvDesign.Accent,
                strokeWidth = 1.4.dp,
            )
        }
        Text(
            text = label,
            color = if (focused || selected) TvDesign.Black.copy(alpha = .78f)
            else TvDesign.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceList(
    state: TvSourcePresentationState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    sourceRequester: (StreamSource) -> FocusRequester,
    selectedProviderRequester: () -> FocusRequester,
    onInteraction: () -> Unit,
    onSourceFocused: (StreamSource) -> Unit,
    onCycleProvider: (Int) -> Boolean,
    onPlay: (StreamSource) -> Unit,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onInteraction()
                        onCycleProvider(if (isRtl) 1 else -1)
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onInteraction()
                        onCycleProvider(if (isRtl) -1 else 1)
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        itemsIndexed(
            items = state.filteredSources,
            key = { _, source -> sourceStableKey(source) },
        ) { index, source ->
            val providerLogoUrl = state.providerLogos[
                sourceProviderKey(source).trim().lowercase()
            ] ?: state.providerLogos[source.providerId.trim().lowercase()]
            val cardModel = remember(
                source,
                providerLogoUrl,
            ) {
                source.toTvSourceCardModel(
                    logoUrl = providerLogoUrl,
                )
            }
            SourceCard(
                model = cardModel,
                showEngineDetails = state.showEngineDetails,
                requester = sourceRequester(source),
                onFocused = {
                    onInteraction()
                    onSourceFocused(source)
                },
                onUpFromFirst = if (index == 0) {
                    {
                        runCatching { selectedProviderRequester().requestFocus() }
                        Unit
                    }
                } else null,
                onClick = { onPlay(source) },
            )
        }
    }
}

@Composable
private fun SourceCard(
    model: TvSourceCardModel,
    showEngineDetails: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onUpFromFirst: (() -> Unit)?,
    onClick: () -> Unit,
) {
    var focused by remember(model.detailUrl, model.providerName) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (
                    onUpFromFirst != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    onUpFromFirst()
                    true
                } else {
                    false
                }
            }
            .background(
                color = if (focused) {
                    TvDesign.SurfaceRaised.copy(alpha = .96f)
                } else {
                    TvDesign.SurfaceRaised.copy(alpha = .72f)
                },
                shape = SourceCardShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    TvDesign.White.copy(alpha = .90f)
                } else {
                    TvDesign.White.copy(alpha = .07f)
                },
                shape = SourceCardShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = model.providerName,
                color = TvDesign.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            model.serverName?.let { server ->
                Text(
                    text = server,
                    color = if (focused) TvDesign.White.copy(alpha = .86f) else TvDesign.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp,
                )
            }

            Text(
                text = model.title,
                color = if (focused) {
                    TvDesign.White.copy(alpha = .90f)
                } else {
                    TvDesign.White.copy(alpha = .74f)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
            )

            model.metadataLabel?.let { line ->
                Text(
                    text = line,
                    color = if (focused) TvDesign.White.copy(alpha = .72f) else TvDesign.Dim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp,
                )
            }

            model.detailUrl
                ?.takeIf { showEngineDetails }
                ?.let { url ->
                    Text(
                        text = url,
                        color = if (focused) {
                            TvDesign.White.copy(alpha = .58f)
                        } else {
                            TvDesign.Dim
                        },
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }

        model.logoUrl?.let { logoUrl ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TvNetworkImage(
                    url = logoUrl,
                    contentDescription = model.providerName,
                    modifier = Modifier
                        .width(38.dp)
                        .height(30.dp),
                    contentScale = ContentScale.Fit,
                    fallback = Color.Transparent,
                )
                Text(
                    text = model.providerName,
                    color = TvDesign.Dim,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(68.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SourceSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(SourceCardShape)
                    .background(TvDesign.SurfaceRaised.copy(alpha = .48f)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 17.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .width((132 + index * 11).dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TvDesign.White.copy(alpha = .11f))
                    )
                    Box(
                        Modifier
                            .width((208 + index * 8).dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(TvDesign.White.copy(alpha = .07f))
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceMessageState(
    title: String,
    message: String,
    actionLabel: String? = null,
    actionRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(30.dp),
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = TvDesign.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp),
        )

        if (actionLabel != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .then(actionRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .background(
                        if (focused) TvDesign.White.copy(alpha = .18f)
                        else TvDesign.SurfaceRaised.copy(alpha = .84f),
                        SourceChipShape,
                    )
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) TvDesign.White.copy(alpha = .90f)
                        else TvDesign.White.copy(alpha = .10f),
                        SourceChipShape,
                    )
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = TvDesign.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun formatRuntimeMinutes(minutes: Int): String =
    if (minutes >= 60) {
        val hours = minutes / 60
        val rest = minutes % 60
        if (rest > 0) "${hours}h ${rest}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
