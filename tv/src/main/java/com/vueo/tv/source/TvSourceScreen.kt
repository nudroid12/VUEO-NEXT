package com.vueo.tv.source

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceAssessment
import com.vueo.shared.core.player.PlayerSourceAudioMatch
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay

/**
 * TV 29F Source Selection.
 *
 * Behaviour is intentionally aligned with VUEO Mobile's source picker:
 * shared source ranking, cached/progressive discovery, provider filtering,
 * VUEO recommendation, engine diagnostics, technical-detail preference and
 * direct-play gating. The 10-foot composition/focus grammar is adapted from
 * the supplied Nuvio StreamScreen reference rather than copied literally.
 */
@Composable
fun TvSourceScreen(
    runtime: TvRuntime,
    media: MediaItem,
    episode: EpisodeItem?,
    onBack: () -> Unit,
    onPlay: (TvSourceBundle, StreamSource) -> Unit,
) {
    BackHandler(onBack = onBack)

    val memoryKey = remember(media.id, media.type, episode?.id) {
        "${media.type}:${media.id}:${episode?.id ?: media.id}"
    }
    val memory = remember(memoryKey) { TvSourceUiMemory.forKey(memoryKey) }

    var bundle by remember(memoryKey) { mutableStateOf<TvSourceBundle?>(null) }
    var searching by remember(memoryKey) { mutableStateOf(true) }
    var progress by remember(memoryKey) { mutableStateOf("Starting source discovery…") }
    var rawCount by remember(memoryKey) { mutableIntStateOf(0) }
    var notice by remember(memoryKey) { mutableStateOf<String?>(null) }
    var firstResultMs by remember(memoryKey) { mutableStateOf<Long?>(null) }
    var providerOrder by remember(memoryKey) { mutableStateOf(emptyList<String>()) }
    var fromCache by remember(memoryKey) { mutableStateOf(false) }
    var error by remember(memoryKey) { mutableStateOf<String?>(null) }
    var retryToken by remember(memoryKey) { mutableIntStateOf(0) }
    var selectedProvider by remember(memoryKey) {
        mutableStateOf(memory.selectedProvider ?: SOURCE_PROVIDER_ALL)
    }
    var showEngineDetails by remember(memoryKey) {
        mutableStateOf(memory.showEngineDetails)
    }
    var userInteracted by remember(memoryKey) { mutableStateOf(false) }
    var sourceFocusAssigned by remember(memoryKey) { mutableStateOf(false) }
    var focusFirstAfterFilterCycle by remember(memoryKey) { mutableStateOf(false) }

    val showTechnicalDetails = runtime.settingsStore.showSourceTechnicalDetails()
    val preferredQuality = runtime.settingsStore.preferredQuality().rankKey

    LaunchedEffect(memoryKey, retryToken) {
        searching = true
        error = null
        progress = "Starting source discovery…"
        if (retryToken > 0) {
            bundle = null
            rawCount = 0
            notice = null
            firstResultMs = null
            providerOrder = emptyList()
            fromCache = false
            sourceFocusAssigned = false
            userInteracted = false
        }

        runCatching {
            runtime.discover(
                item = media,
                episode = episode,
                onUpdate = { snapshot ->
                    bundle = snapshot.bundle
                    searching = snapshot.searching
                    progress = snapshot.progress
                    rawCount = snapshot.rawCount
                    notice = snapshot.notice
                    firstResultMs = snapshot.firstResultMs
                    providerOrder = snapshot.providerOrder
                    fromCache = snapshot.fromCache
                },
            )
        }.onFailure { throwable ->
            error = throwable.message ?: "Source discovery failed"
            searching = false
        }.onSuccess { finalBundle ->
            bundle = finalBundle
            searching = false
        }
    }

    val playable = bundle?.sources.orEmpty().filter { it.isDirectPlayable }
    val rankedAllSources = remember(
        playable,
        preferredQuality,
        media.originalLanguage,
    ) {
        playable.sortedWith(
            PlayerSourcePolicy.comparator(
                preferredQuality = preferredQuality,
                originalLanguage = media.originalLanguage,
            )
        )
    }
    val recommended = remember(
        rankedAllSources,
        preferredQuality,
        media.originalLanguage,
    ) {
        rankedAllSources.firstOrNull { source ->
            PlayerSourcePolicy.assess(
                source = source,
                preferredQuality = preferredQuality,
                originalLanguage = media.originalLanguage,
            ).let { assessment ->
                assessment.quality.automaticRecoveryEligible &&
                    assessment.audioMatch.recommendationEligible
            }
        } ?: rankedAllSources.firstOrNull()
    }

    val currentProviders = remember(rankedAllSources) {
        rankedAllSources
            .asSequence()
            .map(::sourceProviderKey)
            .distinct()
            .toList()
    }
    val visibleProviders = remember(providerOrder, currentProviders) {
        (
            providerOrder.filter { it in currentProviders } +
                currentProviders.filter { it !in providerOrder }
            ).distinct()
    }

    LaunchedEffect(visibleProviders, searching) {
        val selected = selectedProvider
        if (
            selected != SOURCE_PROVIDER_ALL &&
            selected !in visibleProviders &&
            (visibleProviders.isNotEmpty() || !searching)
        ) {
            selectedProvider = SOURCE_PROVIDER_ALL
            memory.selectedProvider = SOURCE_PROVIDER_ALL
        }
    }

    val filteredSources = remember(rankedAllSources, selectedProvider) {
        when (selectedProvider) {
            SOURCE_PROVIDER_ALL -> rankedAllSources
            else -> rankedAllSources.filter { sourceProviderKey(it) == selectedProvider }
        }
    }

    val listState = rememberLazyListState()
    val detailsFocusRequester = remember(memoryKey) { FocusRequester() }
    val retryFocusRequester = remember(memoryKey) { FocusRequester() }
    val filterFocusRequesters = remember(memoryKey) { mutableMapOf<String, FocusRequester>() }
    val sourceFocusRequesters = remember(memoryKey) { mutableMapOf<String, FocusRequester>() }

    fun filterRequester(id: String): FocusRequester =
        filterFocusRequesters.getOrPut(id) { FocusRequester() }

    fun requesterForFilter(id: String): FocusRequester =
        if (id == SOURCE_DETAILS) detailsFocusRequester else filterRequester(id)

    fun sourceRequester(source: StreamSource): FocusRequester =
        sourceFocusRequesters.getOrPut(sourceStableKey(source)) { FocusRequester() }

    fun requestSelectedFilterFocus() {
        val id = selectedProvider.takeIf { it in visibleProviders } ?: SOURCE_PROVIDER_ALL
        runCatching { filterRequester(id).requestFocus() }
    }

    fun selectProvider(provider: String) {
        selectedProvider = provider
        memory.selectedProvider = provider
    }

    fun cycleProvider(delta: Int): Boolean {
        val options = listOf(SOURCE_PROVIDER_ALL) + visibleProviders
        if (options.size <= 1) return false
        val currentIndex = options.indexOf(selectedProvider).coerceAtLeast(0)
        val nextIndex = currentIndex + delta
        if (nextIndex !in options.indices) return false
        selectProvider(options[nextIndex])
        focusFirstAfterFilterCycle = true
        return true
    }

    LaunchedEffect(filteredSources, sourceFocusAssigned, userInteracted) {
        if (sourceFocusAssigned || userInteracted || filteredSources.isEmpty()) return@LaunchedEffect

        val rememberedKey = memory.focusedSourceKey
        val targetIndex = filteredSources.indexOfFirst { sourceStableKey(it) == rememberedKey }
            .takeIf { it >= 0 }
            ?: 0
        val target = filteredSources[targetIndex]

        listState.scrollToItem(targetIndex)
        delay(70)
        runCatching { sourceRequester(target).requestFocus() }
        sourceFocusAssigned = true
    }

    LaunchedEffect(searching, filteredSources.isEmpty(), sourceFocusAssigned, userInteracted) {
        if (
            searching &&
            filteredSources.isEmpty() &&
            !sourceFocusAssigned &&
            !userInteracted
        ) {
            delay(180)
            if (filteredSources.isEmpty() && !sourceFocusAssigned && !userInteracted) {
                runCatching { detailsFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(searching, filteredSources.isEmpty(), error, sourceFocusAssigned, userInteracted) {
        if (
            !searching &&
            filteredSources.isEmpty() &&
            !sourceFocusAssigned &&
            !userInteracted
        ) {
            delay(70)
            runCatching { detailsFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(selectedProvider, filteredSources, focusFirstAfterFilterCycle) {
        if (!focusFirstAfterFilterCycle || filteredSources.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(0)
        delay(55)
        runCatching { sourceRequester(filteredSources.first()).requestFocus() }
        focusFirstAfterFilterCycle = false
    }

    val episodeLabel = episode?.let {
        buildString {
            append("S")
            append(it.season)
            append(" E")
            append(it.episode)
            if (it.title.isNotBlank()) {
                append("  •  ")
                append(it.title)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        TvNetworkImage(
            url = media.background ?: media.poster,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = .48f },
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to TvDesign.Black,
                            .22f to TvDesign.Black.copy(alpha = .90f),
                            .48f to TvDesign.Black.copy(alpha = .34f),
                            .70f to TvDesign.Black.copy(alpha = .64f),
                            1f to TvDesign.Black.copy(alpha = .96f),
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .20f),
                            Color.Transparent,
                            TvDesign.Black.copy(alpha = .72f),
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            SourceIdentityPane(
                modifier = Modifier
                    .weight(.39f)
                    .fillMaxHeight(),
                media = media,
                episodeLabel = episodeLabel,
                searching = searching,
                progress = progress,
                playableCount = rankedAllSources.size,
                providerCount = visibleProviders.size,
                rawCount = rawCount,
                uniqueCount = bundle?.sources.orEmpty().size,
                firstResultMs = firstResultMs,
                notice = notice,
                fromCache = fromCache,
                showEngineDetails = showEngineDetails,
            )

            Column(
                modifier = Modifier
                    .weight(.61f)
                    .fillMaxHeight()
                    .padding(top = 40.dp, end = 46.dp, bottom = 40.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Sources",
                            color = TvDesign.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when {
                                searching && rankedAllSources.isEmpty() -> progress
                                searching -> "${rankedAllSources.size} playable • still checking providers"
                                rankedAllSources.isNotEmpty() ->
                                    "${rankedAllSources.size} playable • ${visibleProviders.size} providers"
                                else -> "No playable sources"
                            },
                            color = TvDesign.Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    EngineStateBadge(searching = searching)
                }

                Spacer(Modifier.height(15.dp))

                val filterIds = listOf(SOURCE_DETAILS) +
                    if (rankedAllSources.isNotEmpty()) {
                        listOf(SOURCE_PROVIDER_ALL) + visibleProviders
                    } else {
                        emptyList()
                    }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp),
                ) {
                    items(
                        items = filterIds,
                        key = { "filter:$it" },
                    ) { id ->
                        val index = filterIds.indexOf(id)
                        val left = filterIds.getOrNull(index - 1)?.let(::requesterForFilter)
                        val right = filterIds.getOrNull(index + 1)?.let(::requesterForFilter)
                        val firstSourceRequester = filteredSources.firstOrNull()?.let(::sourceRequester)
                        val downRequester = firstSourceRequester
                            ?: retryFocusRequester.takeIf {
                                error != null && rankedAllSources.isEmpty()
                            }
                        SourceFilterChip(
                            label = when (id) {
                                SOURCE_DETAILS -> "Details"
                                SOURCE_PROVIDER_ALL -> "All"
                                else -> sourceProviderDisplayName(id)
                            },
                            selected = when (id) {
                                SOURCE_DETAILS -> showEngineDetails
                                else -> selectedProvider == id
                            },
                            requester = requesterForFilter(id),
                            leftRequester = left,
                            rightRequester = right,
                            downRequester = downRequester,
                            onInteraction = { userInteracted = true },
                            onClick = {
                                if (id == SOURCE_DETAILS) {
                                    showEngineDetails = !showEngineDetails
                                    memory.showEngineDetails = showEngineDetails
                                } else {
                                    selectProvider(id)
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(13.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = .48f))
                        .border(
                            width = 1.dp,
                            color = TvDesign.White.copy(alpha = .09f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        error != null && rankedAllSources.isEmpty() -> {
                            SourceMessageState(
                                title = "Source discovery failed",
                                message = error ?: "Source discovery failed",
                                actionLabel = "Retry",
                                actionRequester = retryFocusRequester,
                                onUpFromAction = {
                                    runCatching { detailsFocusRequester.requestFocus() }
                                },
                                onAction = {
                                    userInteracted = true
                                    retryToken++
                                },
                            )
                        }

                        searching && rankedAllSources.isEmpty() -> {
                            SourceLoadingState(progress = progress)
                        }

                        !searching && rankedAllSources.isEmpty() -> {
                            SourceMessageState(
                                title = "No playable sources",
                                message = if (bundle?.sources.orEmpty().isEmpty()) {
                                    "No sources were returned for this title."
                                } else {
                                    "Sources were found, but none are directly playable by the current VUEO player."
                                },
                            )
                        }

                        filteredSources.isEmpty() -> {
                            SourceMessageState(
                                title = "No sources in this provider",
                                message = "Choose another provider filter.",
                            )
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                                contentPadding = PaddingValues(bottom = 18.dp),
                            ) {
                                itemsIndexed(
                                    items = filteredSources,
                                    key = { _, source -> sourceStableKey(source) },
                                ) { index, source ->
                                    SourceRow(
                                        source = source,
                                        originalLanguage = media.originalLanguage,
                                        preferredQuality = preferredQuality,
                                        showTechnicalDetails = showTechnicalDetails,
                                        recommended = sourceStableKey(source) ==
                                            recommended?.let(::sourceStableKey),
                                        requester = sourceRequester(source),
                                        onFocused = {
                                            memory.focusedSourceKey = sourceStableKey(source)
                                            memory.selectedProvider = selectedProvider
                                        },
                                        onInteraction = { userInteracted = true },
                                        onUpFromFirst = if (index == 0) {
                                            { requestSelectedFilterFocus() }
                                        } else null,
                                        onCycleProviderLeft = { cycleProvider(-1) },
                                        onCycleProviderRight = { cycleProvider(1) },
                                        onClick = {
                                            val currentBundle = bundle
                                            if (currentBundle != null && source.isDirectPlayable) {
                                                onPlay(currentBundle, source)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceIdentityPane(
    modifier: Modifier,
    media: MediaItem,
    episodeLabel: String?,
    searching: Boolean,
    progress: String,
    playableCount: Int,
    providerCount: Int,
    rawCount: Int,
    uniqueCount: Int,
    firstResultMs: Long?,
    notice: String?,
    fromCache: Boolean,
    showEngineDetails: Boolean,
) {
    Column(
        modifier = modifier.padding(start = 58.dp, end = 34.dp, top = 76.dp, bottom = 58.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        episodeLabel?.let {
            Text(
                text = it,
                color = TvDesign.White.copy(alpha = .82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        val meta = listOfNotNull(
            media.releaseInfo?.takeIf(String::isNotBlank),
            media.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" • "),
            media.displayType.takeIf(String::isNotBlank),
        ).joinToString("  •  ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = TvDesign.Muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(30.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = .42f))
                .border(
                    1.dp,
                    TvDesign.White.copy(alpha = .09f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SMART SOURCE ENGINE",
                    modifier = Modifier.weight(1f),
                    color = TvDesign.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp,
                )
                Text(
                    text = if (searching) "LIVE" else "READY",
                    color = if (searching) TvDesign.Accent else TvDesign.White.copy(alpha = .82f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (searching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = TvDesign.Accent,
                    trackColor = TvDesign.White.copy(alpha = .10f),
                )
            }

            Text(
                text = progress,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = if (showEngineDetails) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "$playableCount playable  •  $providerCount providers",
                color = TvDesign.White.copy(alpha = .70f),
                fontSize = 10.sp,
            )

            if (showEngineDetails) {
                Spacer(Modifier.height(1.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TvDesign.White.copy(alpha = .08f))
                )

                firstResultMs?.let {
                    Text(
                        text = "First source in $it ms",
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                    )
                }
                if (rawCount > uniqueCount) {
                    Text(
                        text = "$rawCount raw results analysed • ${rawCount - uniqueCount} duplicates removed",
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                    )
                }
                if (fromCache && searching) {
                    Text(
                        text = "Recent cached sources are shown while fresh providers are checked.",
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
                notice?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStateBadge(searching: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (searching) TvDesign.Accent.copy(alpha = .13f)
                else TvDesign.White.copy(alpha = .08f)
            )
            .border(
                1.dp,
                if (searching) TvDesign.Accent.copy(alpha = .32f)
                else TvDesign.White.copy(alpha = .10f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (searching) "LIVE" else "READY",
            color = if (searching) TvDesign.Accent else TvDesign.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .7.sp,
        )
    }
}

@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .height(34.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onInteraction()
                        leftRequester?.let { runCatching { it.requestFocus() } }
                        leftRequester != null
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onInteraction()
                        rightRequester?.let { runCatching { it.requestFocus() } }
                        rightRequester != null
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onInteraction()
                        downRequester?.let { runCatching { it.requestFocus() } }
                        downRequester != null
                    }
                    event.isTvActivationKey() -> {
                        onInteraction()
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    focused -> TvDesign.White.copy(alpha = .19f)
                    selected -> TvDesign.Accent.copy(alpha = .14f)
                    else -> Color.Black.copy(alpha = .34f)
                },
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.White.copy(alpha = .82f)
                    selected -> TvDesign.Accent.copy(alpha = .48f)
                    else -> TvDesign.White.copy(alpha = .10f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                focused -> TvDesign.White
                selected -> TvDesign.Accent
                else -> TvDesign.White.copy(alpha = .74f)
            },
            fontSize = 11.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceRow(
    source: StreamSource,
    originalLanguage: String?,
    preferredQuality: String?,
    showTechnicalDetails: Boolean,
    recommended: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onInteraction: () -> Unit,
    onUpFromFirst: (() -> Unit)?,
    onCycleProviderLeft: () -> Boolean,
    onCycleProviderRight: () -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(sourceStableKey(source)) { mutableStateOf(false) }
    val assessment = remember(source, originalLanguage, preferredQuality) {
        PlayerSourcePolicy.assess(
            source = source,
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )
    }
    val shape = RoundedCornerShape(14.dp)
    val metadata = remember(source, assessment) {
        sourceMetadataLine(source, assessment)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        onUpFromFirst != null -> {
                        onInteraction()
                        onUpFromFirst()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onInteraction()
                        onCycleProviderLeft()
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onInteraction()
                        onCycleProviderRight()
                    }
                    event.isTvActivationKey() -> {
                        onInteraction()
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = if (focused) {
                    TvDesign.SurfaceRaised.copy(alpha = .96f)
                } else {
                    TvDesign.Surface.copy(alpha = .80f)
                },
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    TvDesign.Accent.copy(alpha = .86f)
                } else {
                    TvDesign.White.copy(alpha = .08f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sourceProviderDisplayName(sourceProviderKey(source)),
                    color = TvDesign.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recommended) {
                    Spacer(Modifier.width(9.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TvDesign.Accent.copy(alpha = .15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "RECOMMENDED",
                            color = TvDesign.Accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .6.sp,
                        )
                    }
                }
            }

            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    color = if (focused) TvDesign.White.copy(alpha = .78f) else TvDesign.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showTechnicalDetails && source.name.isNotBlank()) {
                Text(
                    text = source.name,
                    color = TvDesign.Dim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val quality = source.quality?.takeIf(String::isNotBlank)
                ?: assessment.quality.label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (focused) TvDesign.White.copy(alpha = .12f)
                        else Color.Black.copy(alpha = .24f)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = quality,
                    color = if (focused) TvDesign.White else TvDesign.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            source.sizeBytes?.takeIf { it > 0L }?.let {
                Text(
                    text = formatBytes(it),
                    color = TvDesign.Dim,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun SourceLoadingState(progress: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(28.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = TvDesign.White,
            strokeWidth = 2.dp,
        )
        Text(
            text = "Finding sources",
            color = TvDesign.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 15.dp),
        )
        Text(
            text = progress,
            color = TvDesign.Muted,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Results appear as providers respond.",
            color = TvDesign.Dim,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun SourceMessageState(
    title: String,
    message: String,
    actionLabel: String? = null,
    actionRequester: FocusRequester? = null,
    onUpFromAction: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(28.dp),
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            color = TvDesign.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
        if (actionLabel != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .then(
                        actionRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (focused) TvDesign.White.copy(alpha = .18f)
                        else TvDesign.SurfaceRaised.copy(alpha = .80f)
                    )
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) TvDesign.Accent else TvDesign.White.copy(alpha = .10f),
                        RoundedCornerShape(50),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val code = event.nativeKeyEvent.keyCode
                        when {
                            event.type == KeyEventType.KeyDown &&
                                code == KeyEvent.KEYCODE_DPAD_UP &&
                                onUpFromAction != null -> {
                                onUpFromAction()
                                true
                            }
                            event.isTvActivationKey() -> {
                                if (event.type == KeyEventType.KeyUp) onAction()
                                true
                            }
                            else -> false
                        }
                    }
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

private fun sourceProviderKey(source: StreamSource): String =
    source.providerName.trim().ifBlank { "Other" }

private fun sourceProviderDisplayName(provider: String): String =
    provider
        .substringAfterLast(" / ", provider)
        .trim()
        .ifBlank { "Other" }

private fun sourceRepositoryDisplayName(source: StreamSource): String? =
    source.providerName
        .takeIf { " / " in it }
        ?.substringBefore(" / ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun sourceMetadataLine(
    source: StreamSource,
    assessment: PlayerSourceAssessment,
): String =
    listOfNotNull(
        sourceRepositoryDisplayName(source),
        when (assessment.audioMatch) {
            PlayerSourceAudioMatch.ORIGINAL -> "Original audio"
            PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL -> "Original in multi audio"
            PlayerSourceAudioMatch.FOREIGN_DUB -> "Dub"
            PlayerSourceAudioMatch.UNKNOWN -> null
        },
        assessment.summary,
        source.hdr,
        source.audio,
    )
        .flatMap { value -> value.split(" • ") }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .joinToString(" • ")

private fun sourceStableKey(source: StreamSource): String =
    listOf(
        sourceProviderKey(source),
        source.url,
        source.infoHash,
        source.fileIndex?.toString(),
        source.providerId,
        source.name,
    ).joinToString(":") { it.orEmpty() }

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) "%.1f GB".format(gib)
    else "%.0f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean {
    val code = nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER
}

private const val SOURCE_PROVIDER_ALL = "__vueo_all_sources__"
private const val SOURCE_DETAILS = "__vueo_source_details__"

private data class SourceUiMemoryState(
    var selectedProvider: String? = SOURCE_PROVIDER_ALL,
    var focusedSourceKey: String? = null,
    var showEngineDetails: Boolean = false,
)

private object TvSourceUiMemory {
    private const val MAX_ENTRIES = 20
    private val entries =
        object : LinkedHashMap<String, SourceUiMemoryState>(24, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, SourceUiMemoryState>?,
            ): Boolean = size > MAX_ENTRIES
        }

    fun forKey(key: String): SourceUiMemoryState =
        entries.getOrPut(key) { SourceUiMemoryState() }
}
