package com.vueo.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay
import com.vueo.tv.ui.motion.tvFocusSpec

private val PickerBlack = Color(0xFF050706)
private val PickerPanel = Color(0xE9101411)
private val PickerFocus = Color.White
private val PickerMuted = Color(0xFFAAB2AD)

@Composable
fun TvSourcePickerScreen(
    request: TvPlaybackRequest,
    sourceEngine: TvSourceEngine,
    onPlay: (SourceCandidate, List<SubtitleCandidate>) -> Unit,
    onBack: () -> Unit,
) {
    var playableSources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var allSources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var subtitles by remember(request.cacheKey) { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var loading by remember(request.cacheKey) { mutableStateOf(true) }
    var subtitleLoading by remember(request.cacheKey) { mutableStateOf(true) }
    var status by remember(request.cacheKey) { mutableStateOf("Finding sources…") }
    var notice by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var providerFilter by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settingsStore = remember(context) {
        SettingsStore(
            context = context.applicationContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    }
    val showTechnicalDetails = remember(request.cacheKey) {
        settingsStore.showSourceTechnicalDetails()
    }

    val playBestRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val filterRequester = remember { FocusRequester() }
    val firstSourceRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(request.cacheKey) {
        delay(90)
        runCatching { backRequester.requestFocus() }
    }

    LaunchedEffect(request.cacheKey) {
        loading = true
        playableSources = emptyList()
        allSources = emptyList()
        notice = null
        status = "Finding sources…"

        val discovery = sourceEngine.discoverProgressive(request) { progress ->
            playableSources = progress.sources
            allSources = progress.allSources
            status =
                "${progress.completedResolvers}/${progress.totalResolvers} engines • " +
                    "${progress.sources.size} playable"
        }

        playableSources = discovery.sources
        allSources = discovery.allSources
        notice = discovery.notice
        loading = false
        status =
            if (discovery.allSources.isNotEmpty()) {
                "${discovery.sources.size} playable • ${discovery.allSources.size} total"
            } else {
                discovery.notice ?: "No sources found"
            }
    }

    LaunchedEffect(request.cacheKey) {
        subtitleLoading = true
        subtitles = sourceEngine.discoverSubtitles(request)
        subtitleLoading = false
    }

    LaunchedEffect(playableSources.isNotEmpty(), loading) {
        delay(90)
        if (playableSources.isNotEmpty()) {
            runCatching { playBestRequester.requestFocus() }
        } else if (!loading) {
            runCatching { backRequester.requestFocus() }
        }
    }

    val providerNames =
        allSources
            .filter { it.isDirectPlayable }
            .map { it.providerName }
            .filter { it.isNotBlank() }
            .distinct()
    val visibleSources =
        if (providerFilter == null) allSources
        else allSources.filter { it.providerName == providerFilter }

    Box(
        modifier = Modifier.fillMaxSize().background(PickerBlack),
    ) {
        TvNetworkImage(
            url = request.media.background ?: request.media.poster,
            contentDescription = request.media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                PickerBlack,
                                PickerBlack.copy(alpha = 0.96f),
                                PickerBlack.copy(alpha = 0.68f),
                            ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent,
                                PickerBlack.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 58.dp, end = 58.dp, top = 48.dp, bottom = 34.dp),
        ) {
            Text(
                text = "Choose source",
                color = TvAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = request.displayTitle,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = TvAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(18.dp).height(18.dp),
                    )
                }
                Text(status, color = PickerMuted, fontSize = 14.sp)
                Text(
                    text = if (subtitleLoading) "Subtitles…" else "${subtitles.size} subtitles",
                    color = PickerMuted.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerButton(
                    text = "▶  Play Best",
                    requester = playBestRequester,
                    primary = true,
                    enabled = playableSources.isNotEmpty(),
                    onRight = { backRequester.requestFocus() },
                    onDown = {
                        if (allSources.isNotEmpty()) {
                            filterRequester.requestFocus()
                        }
                    },
                    onClick = {
                        playableSources.firstOrNull()?.let { onPlay(it, subtitles) }
                    },
                )
                PickerButton(
                    text = "Back",
                    requester = backRequester,
                    onLeft = {
                        if (playableSources.isNotEmpty()) playBestRequester.requestFocus()
                    },
                    onDown = {
                        if (allSources.isNotEmpty()) {
                            filterRequester.requestFocus()
                        }
                    },
                    onClick = onBack,
                )
            }

            notice?.takeIf { playableSources.isEmpty() }?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = PickerMuted,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Sources",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            if (allSources.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .width(900.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickerProviderChip(
                        label = "All providers",
                        selected = providerFilter == null,
                        requester = filterRequester,
                        onUp = { playBestRequester.requestFocus() },
                        onDown = {
                            if (visibleSources.any { it.isDirectPlayable }) {
                                firstSourceRequester.requestFocus()
                            }
                        },
                        onClick = { providerFilter = null },
                    )
                    providerNames.forEach { provider ->
                        PickerProviderChip(
                            label = provider,
                            selected = providerFilter == provider,
                            onUp = { playBestRequester.requestFocus() },
                            onDown = {
                                if (visibleSources.any { it.isDirectPlayable }) {
                                    firstSourceRequester.requestFocus()
                                }
                            },
                            onClick = { providerFilter = provider },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.width(900.dp),
                contentPadding = PaddingValues(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = visibleSources,
                    key = { _, source -> source.id },
                ) { index, source ->
                    val firstPlayableIndex = visibleSources.indexOfFirst { it.isDirectPlayable }
                    SourcePickerRow(
                        source = source,
                        recommended = source.id == playableSources.firstOrNull()?.id,
                        showTechnicalDetails = showTechnicalDetails,
                        requester = if (index == firstPlayableIndex) firstSourceRequester else null,
                        onUp = if (index == firstPlayableIndex) {
                            { filterRequester.requestFocus() }
                        } else {
                            null
                        },
                        onClick = if (source.isDirectPlayable) {
                            { onPlay(source, subtitles) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerProviderChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tvFocusSpec(),
        label = "sourceFilterFocusScale",
    )
    Box(
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> onUp?.let { it(); true } ?: false
                        Key.DirectionDown -> onDown?.let { it(); true } ?: false
                        else -> false
                    }
                }
            }
            .scale(focusScale)
            .background(
                when {
                    focused -> Color.White
                    selected -> TvAccent.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(999.dp),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else if (selected) TvAccent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PickerButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(),
        label = "sourceActionFocusScale",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
                            Key.DirectionRight -> onRight?.let { it(); true } ?: false
                            Key.DirectionDown -> onDown?.let { it(); true } ?: false
                            else -> false
                        }
                    }
                }
                .scale(focusScale)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) PickerFocus else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.13f),
                contentColor = if (primary) Color.Black else Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.07f),
                disabledContentColor = Color.White.copy(alpha = 0.36f),
            ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SourcePickerRow(
    source: SourceCandidate,
    recommended: Boolean,
    showTechnicalDetails: Boolean,
    requester: FocusRequester? = null,
    onUp: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    var focused by remember(source.id) { mutableStateOf(false) }
    val assessment = SourceRanker.assess(source)
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.015f else 1f,
        animationSpec = tvFocusSpec(),
        label = "sourcePickerScale",
    )
    val availability = when {
        source.isDirectPlayable -> assessment.summary.ifBlank { "Direct" }
        source.isTorrent -> "Torrent • playback with debrid comes later"
        source.url?.startsWith("http://") == true -> "HTTP source • not direct-playable in VUEO"
        else -> "Not direct-playable"
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && onUp != null) {
                        onUp()
                        true
                    } else {
                        false
                    }
                }
                .background(
                    when {
                        focused -> Color.White.copy(alpha = 0.16f)
                        recommended -> TvAccent.copy(alpha = 0.10f)
                        else -> PickerPanel
                    },
                    RoundedCornerShape(12.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        focused -> PickerFocus
                        recommended -> TvAccent.copy(alpha = 0.60f)
                        else -> Color.White.copy(alpha = 0.06f)
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick).focusable()
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (showTechnicalDetails) {
                        if (recommended) "Recommended • $availability" else availability
                    } else {
                        if (recommended) "Recommended • Direct" else if (source.isDirectPlayable) "Direct" else "Unavailable"
                    },
                color = if (source.isDirectPlayable) Color.White else PickerMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (showTechnicalDetails) source.name else source.providerName,
                color = PickerMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTechnicalDetails) {
            Spacer(Modifier.width(18.dp))
            Text(
                text = source.providerName,
                color = if (source.isDirectPlayable) TvAccent else PickerMuted.copy(alpha = 0.68f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
