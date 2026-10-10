package com.vueo.tv.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

@Composable
internal fun VueoPlayerSubtitleWorkspace(
    panelModifier: Modifier = Modifier,
    refreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    tracks: List<TvPlayerTrackChoice>,
    subtitlesDisabled: Boolean,
    pendingSelectionId: String?,
    translatingSelectionId: String?,
    requestedSelectionId: String?,
    entryFocusRequester: FocusRequester,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    preferredLanguageOnly: Boolean,
    subtitleDelayMs: Int,
    onSyncByDialogue: () -> Unit,
    style: TvPlayerSubtitleStyleState,
    onInteraction: () -> Unit,
    onDismissFloat: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
    onSubtitleDelayChange: (Int) -> Unit,
    onStyleChange: ((TvPlayerSubtitleStyleState) -> TvPlayerSubtitleStyleState) -> Unit,
) {
    val preferredFilterCodes = listOfNotNull(
        preferredLanguageCode
            ?.let(::tvCanonicalLanguage)
            ?.takeUnless { it == "und" },
        secondaryLanguageCode
            ?.let(::tvCanonicalLanguage)
            ?.takeUnless { it == "und" },
    ).distinct()
    val preferredFilterActive = preferredLanguageOnly && preferredFilterCodes.isNotEmpty()
    val uiSelectionId = requestedSelectionId ?: pendingSelectionId
    val selectedTrack = tracks.firstOrNull {
        it.selectionId == uiSelectionId
    } ?: tracks.firstOrNull { it.selected }
    val selectedLanguageCode = selectedTrack?.language?.let(::tvCanonicalLanguage)
    val filteredTracks = remember(tracks, preferredFilterCodes, preferredFilterActive, selectedLanguageCode, subtitlesDisabled) {
        if (preferredFilterActive) {
            tracks.filter {
                tvCanonicalLanguage(it.language) in preferredFilterCodes ||
                    (!subtitlesDisabled && tvCanonicalLanguage(it.language) == selectedLanguageCode)
            }
        } else {
            tracks
        }
    }
    val groups = remember(filteredTracks, preferredLanguageCode, secondaryLanguageCode) {
        tvBuildSubtitleLanguageGroups(filteredTracks, preferredLanguageCode, secondaryLanguageCode)
    }
    val selectedLanguageVisible = selectedLanguageCode
        ?.takeIf { code -> groups.any { it.code == code } }
    val hasSelectedSubtitle = !subtitlesDisabled && selectedLanguageVisible != null
    var activeLanguageCode by remember(
        selectedLanguageVisible,
        subtitlesDisabled,
        preferredFilterActive,
        groups.map { it.code },
    ) {
        mutableStateOf(
            selectedLanguageVisible.takeIf { hasSelectedSubtitle }
                ?: groups.singleOrNull()
                    ?.code
                    ?.takeIf { preferredFilterActive && !subtitlesDisabled }
        )
    }
    var styleOpen by remember(selectedLanguageVisible, subtitlesDisabled) {
        mutableStateOf(hasSelectedSubtitle)
    }
    var styleFloatMode by remember { mutableStateOf(false) }
    val showSubtitlesPanel = hasSelectedSubtitle || activeLanguageCode != null
    val showStylePanel = styleOpen && !subtitlesDisabled
    val visibleTracks = groups.firstOrNull { it.code == activeLanguageCode }?.tracks.orEmpty()
    val entryLanguageIndex = when {
        subtitlesDisabled -> 0
        selectedLanguageVisible != null -> groups.indexOfFirst { it.code == selectedLanguageVisible }
            .let { if (it < 0) 0 else it + 1 }
        preferredFilterActive && groups.isNotEmpty() -> 1
        else -> 0
    }
    val refreshRequester = remember { FocusRequester() }
    val languageRequesters = remember(groups.map { it.code }, entryLanguageIndex, entryFocusRequester) {
        List(groups.size + 1) { index ->
            if (index == entryLanguageIndex && !hasSelectedSubtitle) entryFocusRequester else FocusRequester()
        }
    }
    val selectedEntryIndex = visibleTracks.indexOfFirst { it.key == selectedTrack?.key }
    val trackListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedEntryIndex.coerceAtLeast(0))
    val trackRequesters = remember(visibleTracks.map { it.key }, selectedEntryIndex, entryFocusRequester) {
        List(visibleTracks.size.coerceAtLeast(1)) { index ->
            if (hasSelectedSubtitle && index == selectedEntryIndex) entryFocusRequester else FocusRequester()
        }
    }
    val dialogueSyncRequester = remember { FocusRequester() }
    val floatRequester = remember { FocusRequester() }
    val fontRequester = remember { FocusRequester() }
    val syncRequester = remember { FocusRequester() }
    val sizeRequester = remember { FocusRequester() }
    val boldRequester = remember { FocusRequester() }
    val commentaryRequester = remember { FocusRequester() }
    val commentarySizeRequester = remember { FocusRequester() }
    val textColorRequester = remember { FocusRequester() }
    val opacityRequester = remember { FocusRequester() }
    val outlineRequester = remember { FocusRequester() }
    val outlineColorRequester = remember { FocusRequester() }
    val backgroundRequester = remember { FocusRequester() }
    val backgroundColorRequester = remember { FocusRequester() }
    val backgroundOpacityRequester = remember { FocusRequester() }
    val positionRequester = remember { FocusRequester() }
    val resetRequester = remember { FocusRequester() }
    val activeLanguageRequester = languageRequesters.getOrNull(
        groups.indexOfFirst { it.code == activeLanguageCode }.let { if (it < 0) 0 else it + 1 }
    ) ?: languageRequesters.first()
    val firstTrackRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else FocusRequester.Cancel
    val selectedVisibleTrackIndex = visibleTracks
        .indexOfFirst { it.selectionId == uiSelectionId }
        .takeIf { it >= 0 }
        ?: visibleTracks.indexOfFirst { it.selected }
    var styleReturnTrackIndex by remember(visibleTracks.map { it.key }) {
        mutableIntStateOf(selectedVisibleTrackIndex.coerceAtLeast(0))
    }
    var pendingTrackFocusLanguage by remember { mutableStateOf<String?>(null) }
    var pendingTrackFocusRequest by remember { mutableIntStateOf(0) }
    val styleLeftRequester = if (styleFloatMode) {
        FocusRequester.Cancel
    } else {
        trackRequesters.getOrNull(styleReturnTrackIndex)
            ?: if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester
    }
    var initialFocusAssigned by remember { mutableStateOf(false) }
    // The player owns the style. Apply each action to its current value so
    // rapid remote input cannot race a second, asynchronously mirrored copy.
    fun updateStyle(transform: (TvPlayerSubtitleStyleState) -> TvPlayerSubtitleStyleState) {
        onStyleChange(transform)
    }

    LaunchedEffect(styleOpen, subtitlesDisabled) {
        if (!styleOpen || subtitlesDisabled) styleFloatMode = false
    }

    BackHandler(enabled = styleFloatMode) {
        onDismissFloat()
    }

    LaunchedEffect(groups, entryLanguageIndex, selectedEntryIndex, initialFocusAssigned) {
        if (initialFocusAssigned) return@LaunchedEffect
        if (hasSelectedSubtitle && selectedEntryIndex >= 0) {
            trackListState.scrollToItem(selectedEntryIndex)
            initialFocusAssigned = trackRequesters[selectedEntryIndex].requestTvFocus()
        } else {
            initialFocusAssigned = languageRequesters[
                entryLanguageIndex.coerceIn(languageRequesters.indices)
            ].requestTvFocus()
        }
    }

    LaunchedEffect(
        activeLanguageCode,
        visibleTracks.map { it.key },
        pendingTrackFocusLanguage,
        pendingTrackFocusRequest,
    ) {
        if (
            pendingTrackFocusLanguage != activeLanguageCode ||
            visibleTracks.isEmpty()
        ) {
            return@LaunchedEffect
        }

        // Switching Languages rebuilds the track column and its FocusRequesters.
        // Scroll the first row into composition, then retry across enough frames
        // to survive the panel/layout recomposition instead of leaving D-pad focus
        // stuck in the Languages card.
        trackListState.scrollToItem(0)
        if (trackRequesters.first().requestTvFocus(attempts = 14)) {
            pendingTrackFocusLanguage = null
        }
    }

    val textColours = remember {
        listOf(
            0xFFFFFFFF.toInt(),
            0xFFDCEEFF.toInt(),
            0xFFFFCC2F.toInt(),
            0xFF18C7F5.toInt(),
            0xFFFF6B86.toInt(),
            0xFF6EE7C1.toInt(),
        )
    }
    val outlineColours = remember {
        listOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF18C7F5.toInt(),
            0xFFFF6B86.toInt(),
        )
    }
    val backgroundColours = remember {
        listOf(
            0xFF000000.toInt(),
            0xFF202124.toInt(),
            0xFF263238.toInt(),
            0xFF3B1F2B.toInt(),
        )
    }
    val opacity = subtitleAlphaPercent(style.textColor)
    val cardBackground = Color(0xFF17191C).copy(alpha = .92f)
    val cardBorder = Color.White.copy(alpha = .065f)

    Box(
        panelModifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, top = 24.dp, end = 44.dp, bottom = SubtitleWorkspaceBottomClearance),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Subtitles",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        refreshing -> "Refreshing subtitle discovery…"
                        !refreshMessage.isNullOrBlank() -> refreshMessage
                        else -> "Choose a language, track and style"
                    },
                    color = Color.White.copy(alpha = .56f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier = Modifier.weight(.30f).fillMaxHeight(),
                ) {
                    if (!styleFloatMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(PanelShape)
                            .background(cardBackground)
                            .border(1.dp, cardBorder, PanelShape)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                VueoSubtitleColumnTitle("Languages")
                            }
                            VueoSubtitleRefreshIconButton(
                                refreshing = refreshing,
                                requester = refreshRequester,
                                downRequester = languageRequesters.first(),
                                rightRequester = firstTrackRequester,
                                onInteraction = onInteraction,
                                onClick = onRefresh,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            item(key = "subtitle:none") {
                                VueoSubtitleLanguageRow(
                                    title = "Off",
                                    count = null,
                                    selected = subtitlesDisabled && activeLanguageCode == null,
                                    requester = languageRequesters[0],
                                    blockUp = true,
                                    blockDown = groups.isEmpty(),
                                    upRequester = refreshRequester,
                                    rightRequester = firstTrackRequester,
                                    onInteraction = onInteraction,
                                ) {
                                    pendingTrackFocusLanguage = null
                                    activeLanguageCode = null
                                    styleOpen = false
                                    onDisable()
                                }
                            }
                            itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                                VueoSubtitleLanguageRow(
                                    title = group.label,
                                    count = group.tracks.size,
                                    selected = group.code == activeLanguageCode ||
                                        (activeLanguageCode == null && !subtitlesDisabled && group.code == selectedLanguageCode),
                                    requester = languageRequesters[index + 1],
                                    blockUp = false,
                                    blockDown = index == groups.lastIndex,
                                    // Always route D-pad Right through the explicit
                                    // handoff below. A direct FocusRequester can point at
                                    // the previous track list for one composition frame
                                    // after changing language, which is the intermittent
                                    // "stuck in Languages" failure seen on TV.
                                    rightRequester = FocusRequester.Cancel,
                                    onRight = if (group.tracks.isNotEmpty()) {
                                        {
                                            activeLanguageCode = group.code
                                            styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                                            pendingTrackFocusLanguage = group.code
                                            pendingTrackFocusRequest += 1
                                        }
                                    } else {
                                        null
                                    },
                                    onInteraction = onInteraction,
                                ) {
                                    pendingTrackFocusLanguage = null
                                    activeLanguageCode = group.code
                                    styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                                }
                            }
                        }
                    }
                    }
                }

                Box(
                    modifier = Modifier.weight(.40f).fillMaxHeight(),
                ) {
                    if (!styleFloatMode && showSubtitlesPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(PanelShape)
                            .background(cardBackground)
                            .border(1.dp, cardBorder, PanelShape)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        VueoSubtitleColumnTitle("Subtitles")
                        Spacer(Modifier.height(10.dp))
                        when {
                            activeLanguageCode == null -> VueoSubtitleEmpty("Choose a language to see its exact subtitle tracks.")
                            visibleTracks.isNotEmpty() -> LazyColumn(
                                state = trackListState,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                itemsIndexed(visibleTracks, key = { _, track -> track.key }) { index, track ->
                                    VueoSubtitleTrackRow(
                                        title = track.label,
                                        provider = track.sourceLabel,
                                        detail = if (
                                            track.selectionId == translatingSelectionId
                                        ) {
                                            "Translating…"
                                        } else {
                                            track.metadata
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { "ID: $it" }
                                                .orEmpty()
                                        },
                                        selected = !subtitlesDisabled &&
                                            (
                                                track.selectionId == uiSelectionId ||
                                                    (
                                                        uiSelectionId == null &&
                                                            track.selected
                                                        )
                                                ),
                                        requester = trackRequesters[index],
                                        blockUp = index == 0,
                                        blockDown = index == visibleTracks.lastIndex,
                                        leftRequester = activeLanguageRequester,
                                        rightRequester = if (styleOpen) dialogueSyncRequester else FocusRequester.Cancel,
                                        onInteraction = onInteraction,
                                        onFocused = { styleReturnTrackIndex = index },
                                    ) {
                                        // Re-selecting the active track is an explicit retry/re-apply.
                                        // Only the dedicated Off row disables subtitles.
                                        styleOpen = true
                                        onSelect(track)
                                    }
                                }
                            }
                            groups.isEmpty() -> VueoSubtitleEmpty(
                                if (preferredFilterActive) {
                                    "No subtitle is available for your preferred language."
                                } else {
                                    "No subtitles available. Try another source or install a subtitle addon."
                                }
                            )
                            else -> VueoSubtitleEmpty("No subtitle track is available for this language.")
                        }
                    }
                    }
                }

                Box(
                    modifier = Modifier.weight(.30f).fillMaxHeight(),
                ) {
                    if (showStylePanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(PanelShape)
                            .background(cardBackground)
                            .border(1.dp, cardBorder, PanelShape)
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VueoSubtitleColumnTitle("Style")
                            Spacer(Modifier.weight(1f))
                            VueoSubtitleHeaderButton(
                                label = "Sync",
                                requester = dialogueSyncRequester,
                                downRequester = syncRequester,
                                leftRequester = styleLeftRequester,
                                rightRequester = floatRequester,
                                onInteraction = onInteraction,
                                onClick = onSyncByDialogue,
                            )
                            Spacer(Modifier.width(6.dp))
                            VueoSubtitleHeaderButton(
                                label = "Float",
                                requester = floatRequester,
                                downRequester = syncRequester,
                                leftRequester = dialogueSyncRequester,
                                rightRequester = FocusRequester.Cancel,
                                onInteraction = onInteraction,
                            ) {
                                styleFloatMode = true
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        if (styleOpen && !subtitlesDisabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                VueoSubtitleStepperRow(
                                    title = "Sync",
                                    value = formatSubtitleDelayTv(subtitleDelayMs),
                                    requester = syncRequester,
                                    upRequester = dialogueSyncRequester,
                                    downRequester = sizeRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        onSubtitleDelayChange((subtitleDelayMs - 100).coerceAtLeast(-60_000))
                                    },
                                    onIncrease = {
                                        onSubtitleDelayChange((subtitleDelayMs + 100).coerceAtMost(60_000))
                                    },
                                )
                                VueoSubtitleStepperRow(
                                    title = "Font Size",
                                    value = "${style.fontSizeSp}sp",
                                    requester = sizeRequester,
                                    upRequester = syncRequester,
                                    downRequester = boldRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        updateStyle { current ->
                                            current.copy(fontSizeSp = (current.fontSizeSp - 2).coerceAtLeast(12))
                                        }
                                    },
                                    onIncrease = {
                                        updateStyle { current ->
                                            current.copy(fontSizeSp = (current.fontSizeSp + 2).coerceAtMost(40))
                                        }
                                    },
                                )
                                VueoSubtitleToggleRow(
                                    title = "Bold",
                                    enabled = style.bold,
                                    requester = boldRequester,
                                    upRequester = sizeRequester,
                                    downRequester = commentaryRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = {
                                        updateStyle { current -> current.copy(bold = !current.bold) }
                                    },
                                )
                                VueoSubtitleToggleRow(
                                    title = "Commentary",
                                    enabled = style.showCommentary,
                                    requester = commentaryRequester,
                                    upRequester = boldRequester,
                                    downRequester = commentarySizeRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = {
                                        updateStyle { current ->
                                            current.copy(showCommentary = !current.showCommentary)
                                        }
                                    },
                                )
                                VueoSubtitleStepperRow(
                                    title = "Commentary Size",
                                    value = "${style.commentaryFontSizeSp}sp",
                                    requester = commentarySizeRequester,
                                    upRequester = commentaryRequester,
                                    downRequester = textColorRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                commentaryFontSizeSp = (current.commentaryFontSizeSp - 2).coerceAtLeast(12)
                                            )
                                        }
                                    },
                                    onIncrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                commentaryFontSizeSp = (current.commentaryFontSizeSp + 2).coerceAtMost(40)
                                            )
                                        }
                                    },
                                )
                                VueoSubtitleColorRow(
                                    title = "Text Color",
                                    colours = textColours,
                                    selectedColour = style.textColor,
                                    requester = textColorRequester,
                                    upRequester = commentarySizeRequester,
                                    downRequester = opacityRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) { colour ->
                                    updateStyle { current ->
                                        current.copy(
                                            textColor = subtitleWithAlpha(
                                                colour,
                                                subtitleAlphaPercent(current.textColor),
                                            )
                                        )
                                    }
                                }
                                VueoSubtitleStepperRow(
                                    title = "Text Opacity",
                                    value = "$opacity%",
                                    requester = opacityRequester,
                                    upRequester = textColorRequester,
                                    downRequester = outlineRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        updateStyle { current ->
                                            val currentOpacity = subtitleAlphaPercent(current.textColor)
                                            current.copy(
                                                textColor = subtitleWithAlpha(
                                                    current.textColor,
                                                    (currentOpacity - 10).coerceAtLeast(30),
                                                )
                                            )
                                        }
                                    },
                                    onIncrease = {
                                        updateStyle { current ->
                                            val currentOpacity = subtitleAlphaPercent(current.textColor)
                                            current.copy(
                                                textColor = subtitleWithAlpha(
                                                    current.textColor,
                                                    (currentOpacity + 10).coerceAtMost(100),
                                                )
                                            )
                                        }
                                    },
                                )
                                VueoSubtitleToggleRow(
                                    title = "Outline",
                                    enabled = style.outlineEnabled,
                                    requester = outlineRequester,
                                    upRequester = opacityRequester,
                                    downRequester = if (style.outlineEnabled) outlineColorRequester else backgroundRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = {
                                        updateStyle { current ->
                                            current.copy(outlineEnabled = !current.outlineEnabled)
                                        }
                                    },
                                )
                                if (style.outlineEnabled) {
                                    VueoSubtitleColorRow(
                                        title = "Outline Color",
                                        colours = outlineColours,
                                        selectedColour = style.outlineColor,
                                        requester = outlineColorRequester,
                                        upRequester = outlineRequester,
                                        downRequester = backgroundRequester,
                                        leftRequester = styleLeftRequester,
                                        onInteraction = onInteraction,
                                    ) { colour ->
                                        updateStyle { current ->
                                            current.copy(outlineColor = colour)
                                        }
                                    }
                                }
                                VueoSubtitleToggleRow(
                                    title = "Background",
                                    enabled = style.backgroundEnabled,
                                    requester = backgroundRequester,
                                    upRequester = if (style.outlineEnabled) outlineColorRequester else outlineRequester,
                                    downRequester = if (style.backgroundEnabled) backgroundColorRequester else positionRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = {
                                        updateStyle { current ->
                                            current.copy(backgroundEnabled = !current.backgroundEnabled)
                                        }
                                    },
                                )
                                if (style.backgroundEnabled) {
                                    VueoSubtitleColorRow(
                                        title = "Background Color",
                                        colours = backgroundColours,
                                        selectedColour = style.backgroundColor,
                                        requester = backgroundColorRequester,
                                        upRequester = backgroundRequester,
                                        downRequester = backgroundOpacityRequester,
                                        leftRequester = styleLeftRequester,
                                        onInteraction = onInteraction,
                                    ) { colour ->
                                        updateStyle { current ->
                                            current.copy(backgroundColor = colour)
                                        }
                                    }
                                    VueoSubtitleStepperRow(
                                        title = "Background Opacity",
                                        value = "${style.backgroundOpacityPercent}%",
                                        requester = backgroundOpacityRequester,
                                        upRequester = backgroundColorRequester,
                                        downRequester = positionRequester,
                                        leftRequester = styleLeftRequester,
                                        onInteraction = onInteraction,
                                        onDecrease = {
                                            updateStyle { current ->
                                                current.copy(
                                                    backgroundOpacityPercent =
                                                        (current.backgroundOpacityPercent - 10)
                                                            .coerceAtLeast(10)
                                                )
                                            }
                                        },
                                        onIncrease = {
                                            updateStyle { current ->
                                                current.copy(
                                                    backgroundOpacityPercent =
                                                        (current.backgroundOpacityPercent + 10)
                                                            .coerceAtMost(100)
                                                )
                                            }
                                        },
                                    )
                                }
                                VueoSubtitleStepperRow(
                                    title = "Bottom Position",
                                    value = "${style.bottomPaddingPercent}%",
                                    requester = positionRequester,
                                    upRequester = when {
                                        style.backgroundEnabled -> backgroundOpacityRequester
                                        else -> backgroundRequester
                                    },
                                    downRequester = fontRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                bottomPaddingPercent = (current.bottomPaddingPercent - 2).coerceAtLeast(5)
                                            )
                                        }
                                    },
                                    onIncrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                bottomPaddingPercent = (current.bottomPaddingPercent + 2).coerceAtMost(40)
                                            )
                                        }
                                    },
                                )
                                VueoSubtitleStepperRow(
                                    title = "Font",
                                    value = com.vueo.shared.core.player.SubtitleFonts.label(style.fontFamily),
                                    requester = fontRequester,
                                    upRequester = positionRequester,
                                    downRequester = resetRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                fontFamily = com.vueo.shared.core.player.SubtitleFonts.next(current.fontFamily, -1)
                                            )
                                        }
                                    },
                                    onIncrease = {
                                        updateStyle { current ->
                                            current.copy(
                                                fontFamily = com.vueo.shared.core.player.SubtitleFonts.next(current.fontFamily, 1)
                                            )
                                        }
                                    },
                                    decreaseLabel = "‹", increaseLabel = "›", controlWidth = 148.dp,
                                )
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                VueoSubtitleActionRow(
                                    title = "Reset Style",
                                    requester = resetRequester,
                                    upRequester = fontRequester,
                                    downRequester = FocusRequester.Cancel,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) {
                                    val reset = TvPlayerSubtitleStyleState()
                                    updateStyle { reset }
                                }
                                }
                            }
                        } else {
                            VueoSubtitleEmpty("Select an exact subtitle track to adjust its style.")
                        }
                    }
                    }
                }
            }
        }
    }
}



@Composable
private fun VueoSubtitleRefreshIconButton(
    refreshing: Boolean,
    requester: FocusRequester,
    downRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape

    Box(
        modifier = Modifier
            .requiredSize(32.dp)
            .focusRequester(requester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = downRequester
                left = FocusRequester.Cancel
                right = rightRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && !refreshing) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    focused -> Color(0xFF555555)
                    refreshing -> Color.White.copy(alpha = .035f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color(0xFF888888) else Color.White.copy(alpha = .10f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = if (refreshing) "Refreshing subtitles" else "Refresh subtitles",
            tint = Color.White.copy(alpha = if (refreshing) .42f else .90f),
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun VueoSubtitleHeaderButton(
    label: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = Modifier
            .height(30.dp)
            .focusRequester(requester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = downRequester
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                if (focused) Color(0xFF555555) else Color.White.copy(alpha = .08f),
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color(0xFF888888) else Color.White.copy(alpha = .10f),
                shape,
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun VueoSubtitleColumnTitle(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = .94f),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun subtitleAccentContentColor(): Color =
    if (TvDesign.Accent.luminance() >= .48f) Color.Black else Color.White

@Composable
private fun VueoSubtitleLanguageRow(
    title: String,
    count: Int?,
    selected: Boolean,
    requester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    rightRequester: FocusRequester,
    upRequester: FocusRequester? = null,
    onRight: (() -> Unit)? = null,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (upRequester != null) up = upRequester
                else if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = rightRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                    onRight != null
                ) {
                    onInteraction()
                    onRight()
                    return@onPreviewKeyEvent true
                }
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    selected -> Color.White
                    focused -> Color(0xFF555555)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> Color(0xFF888888)
                    selected -> Color.White
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Box(
                modifier = Modifier
                    .width(27.dp)
                    .height(27.dp)
                    .background(
                        when {
                            selected -> Color.Black.copy(alpha = .10f)
                            focused -> Color.White.copy(alpha = .12f)
                            else -> Color.White.copy(alpha = .09f)
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    it.toString(),
                    color = if (selected) Color.Black else Color.White.copy(alpha = .92f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VueoSubtitleTrackRow(
    title: String,
    provider: String,
    detail: String,
    selected: Boolean,
    requester: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title, provider, detail) { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    selected -> Color.White
                    focused -> Color(0xFF555555)
                    else -> Color.White.copy(alpha = .025f)
                },
                shape,
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> Color(0xFF888888)
                    selected -> Color.White
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .background(
                        if (selected) Color.Black.copy(alpha = .06f)
                        else if (focused) Color.White.copy(alpha = .12f)
                        else Color.White.copy(alpha = .055f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        if (selected) Color.Black.copy(alpha = .20f)
                        else if (focused) Color.White.copy(alpha = .38f)
                        else Color.White.copy(alpha = .09f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    provider.ifBlank { "Subtitle" },
                    color = if (selected) Color.Black else Color.White.copy(alpha = .66f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = if (selected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    color = if (selected) Color.Black.copy(alpha = .65f) else Color.White.copy(alpha = .50f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text(
                "✓",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun VueoSubtitleStepperRow(
    title: String,
    value: String,
    requester: FocusRequester? = null,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String = "−",
    increaseLabel: String = "+",
    controlWidth: androidx.compose.ui.unit.Dp = 104.dp,
) {
    val internalRequester = remember(title) { FocusRequester() }
    val valueRequester = requester ?: internalRequester
    var focused by remember(valueRequester) { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "subtitleStyleFocusScale",
    )
    val shape = RoundedCornerShape(9.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = .72f), fontSize = 11.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(
            Modifier.width(controlWidth).height(30.dp)
                .focusRequester(valueRequester)
                .focusProperties {
                    up = upRequester
                    down = downRequester
                    left = leftRequester
                    right = FocusRequester.Cancel
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onInteraction()
                }
                .onPreviewKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onInteraction()
                                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) onDecrease() else onIncrease()
                            }
                            true
                        }
                        else -> {
                            if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                            onInteraction()
                            if (event.type == KeyEventType.KeyUp) onIncrease()
                            true
                        }
                    }
                }
                .focusable()
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .clip(shape)
                .background(if (focused) TvDesign.Accent.copy(alpha = .12f) else Color.White.copy(alpha = .045f))
                .border(if (focused) 2.dp else 1.dp,
                    if (focused) TvDesign.Accent else Color.White.copy(alpha = .15f), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(decreaseLabel, color = Color.White, fontSize = 16.sp)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .10f)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .10f)))
            Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(increaseLabel, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun VueoSubtitleToggleRow(
    title: String,
    enabled: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onToggle: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "subtitleStyleFocusScale",
    )
    val shape = RoundedCornerShape(11.dp)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(30.dp)
                .focusRequester(requester)
                .focusProperties {
                    up = upRequester
                    down = downRequester
                    left = leftRequester
                    right = FocusRequester.Cancel
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onInteraction()
                }
                .onPreviewKeyEvent { event ->
                    if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                    onInteraction()
                    if (event.type == KeyEventType.KeyUp) onToggle()
                    true
                }
                .focusable()
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .background(
                    when {
                        enabled -> Color.White
                        focused -> TvDesign.Accent.copy(alpha = .18f)
                        else -> Color.White.copy(alpha = .09f)
                    },
                    shape,
                )
                .border(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> TvDesign.Accent
                        enabled -> Color.White
                        else -> Color.White.copy(alpha = .08f)
                    },
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enabled) "On" else "Off",
                color = if (enabled) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun VueoSubtitleColorRow(
    title: String,
    colours: List<Int>,
    selectedColour: Int,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    // Keep each swatch's focus node stable when selection changes. The
    // vertical entry node is always the first swatch, rather than moving the
    // shared requester from one live node to another during a key press.
    val requesters = remember(colours, requester) {
        List(colours.size) { index ->
            if (index == 0) requester else FocusRequester()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            colours.forEachIndexed { index, colour ->
                var focused by remember(colour) { mutableStateOf(false) }
                val focusScale by animateFloatAsState(
                    targetValue = if (focused) 1.10f else 1f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    label = "subtitleStyleFocusScale",
                )
                val selected = (selectedColour and 0x00FFFFFF) == (colour and 0x00FFFFFF)
                val swatch = Color(colour)
                val checkColor = if (swatch.luminance() > .48f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .requiredSize(28.dp)
                        .focusRequester(requesters[index])
                        .focusProperties {
                            up = upRequester
                            down = downRequester
                            if (index == 0) left = leftRequester
                            if (index == colours.lastIndex) right = FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onInteraction()
                        }
                        .onPreviewKeyEvent { event ->
                            if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                            onInteraction()
                            if (event.type == KeyEventType.KeyUp) onSelected(colour)
                            true
                        }
                        .focusable()
                        .graphicsLayer {
                            scaleX = focusScale
                            scaleY = focusScale
                        }
                        .border(
                            width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                            color = when {
                                focused -> Color(0xFF888888)
                                selected -> Color.White.copy(alpha = .92f)
                                else -> Color.White.copy(alpha = .18f)
                            },
                            shape = CircleShape,
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(swatch, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text(
                                "✓",
                                color = checkColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VueoSubtitleActionRow(
    title: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "subtitleStyleFocusScale",
    )
    val shape = RoundedCornerShape(11.dp)
    val contentColor = if (focused) subtitleAccentContentColor() else Color.White

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(30.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
                left = leftRequester
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvPanelActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .background(if (focused) TvDesign.Accent else Color.White.copy(alpha = .045f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )

    }
}

@Composable
private fun VueoSubtitleEmpty(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = .52f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

private fun subtitleAlphaPercent(colour: Int): Int =
    (((colour ushr 24) * 100) + 127) / 255

private fun subtitleWithAlpha(colour: Int, opacityPercent: Int): Int {
    val alpha = (255 * opacityPercent.coerceIn(0, 100) / 100) shl 24
    return (colour and 0x00FFFFFF) or alpha
}

private fun formatSubtitleDelayTv(value: Int): String {
    if (value == 0) return "0.00s"
    val seconds = value / 1000.0
    return java.lang.String.format(java.util.Locale.US, if (value > 0) "+%.2fs" else "%.2fs", seconds)
}

