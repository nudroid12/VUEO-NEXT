package com.vueo.tv.player

import android.view.KeyEvent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
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
    tracks: List<TvPlayerTrackChoice>,
    subtitlesDisabled: Boolean,
    entryFocusRequester: FocusRequester,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    preferredLanguageOnly: Boolean,
    subtitleDelayMs: Int,
    style: TvPlayerSubtitleStyleState,
    onInteraction: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
    onSubtitleDelayChange: (Int) -> Unit,
    onStyleChange: (TvPlayerSubtitleStyleState) -> Unit,
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
    val filteredTracks = remember(tracks, preferredFilterCodes, preferredFilterActive) {
        if (preferredFilterActive) {
            tracks.filter {
                tvCanonicalLanguage(it.language) in preferredFilterCodes
            }
        } else {
            tracks
        }
    }
    val groups = remember(filteredTracks, preferredLanguageCode, secondaryLanguageCode) {
        tvBuildSubtitleLanguageGroups(filteredTracks, preferredLanguageCode, secondaryLanguageCode)
    }
    val selectedTrack = tracks.firstOrNull { it.selected }
    val selectedLanguageCode = selectedTrack?.language?.let(::tvCanonicalLanguage)
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
    val languageRequesters = remember(groups.map { it.code }, entryLanguageIndex, entryFocusRequester) {
        List(groups.size + 1) { index ->
            if (index == entryLanguageIndex) entryFocusRequester else FocusRequester()
        }
    }
    val trackRequesters = remember(visibleTracks.map { it.key }) {
        List(visibleTracks.size.coerceAtLeast(1)) { FocusRequester() }
    }
    val floatRequester = remember { FocusRequester() }
    val syncRequester = remember { FocusRequester() }
    val sizeRequester = remember { FocusRequester() }
    val boldRequester = remember { FocusRequester() }
    val textColorRequester = remember { FocusRequester() }
    val opacityRequester = remember { FocusRequester() }
    val outlineRequester = remember { FocusRequester() }
    val outlineColorRequester = remember { FocusRequester() }
    val positionRequester = remember { FocusRequester() }
    val resetRequester = remember { FocusRequester() }
    val activeLanguageRequester = languageRequesters.getOrNull(
        groups.indexOfFirst { it.code == activeLanguageCode }.let { if (it < 0) 0 else it + 1 }
    ) ?: languageRequesters.first()
    val firstTrackRequester = if (visibleTracks.isNotEmpty()) trackRequesters.first() else FocusRequester.Cancel
    val selectedVisibleTrackIndex = visibleTracks.indexOfFirst { it.selected }
    var styleReturnTrackIndex by remember(visibleTracks.map { it.key }) {
        mutableIntStateOf(selectedVisibleTrackIndex.coerceAtLeast(0))
    }
    var pendingTrackFocusLanguage by remember { mutableStateOf<String?>(null) }
    val styleLeftRequester = if (styleFloatMode) {
        FocusRequester.Cancel
    } else {
        trackRequesters.getOrNull(styleReturnTrackIndex)
            ?: if (visibleTracks.isNotEmpty()) trackRequesters.first() else activeLanguageRequester
    }
    var initialFocusAssigned by remember { mutableStateOf(false) }

    LaunchedEffect(styleOpen, subtitlesDisabled) {
        if (!styleOpen || subtitlesDisabled) styleFloatMode = false
    }

    BackHandler(enabled = styleFloatMode) {
        styleFloatMode = false
        onInteraction()
    }

    LaunchedEffect(groups, entryLanguageIndex, initialFocusAssigned) {
        if (initialFocusAssigned) return@LaunchedEffect
        initialFocusAssigned = languageRequesters[
            entryLanguageIndex.coerceIn(languageRequesters.indices)
        ].requestTvFocus()
    }

    LaunchedEffect(activeLanguageCode, visibleTracks, pendingTrackFocusLanguage) {
        if (
            pendingTrackFocusLanguage != activeLanguageCode ||
            visibleTracks.isEmpty()
        ) {
            return@LaunchedEffect
        }
        if (trackRequesters.first().requestTvFocus()) {
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
    val opacity = subtitleAlphaPercent(style.textColor)
    val cardBackground = Color(0xFF17191C).copy(alpha = .92f)
    val cardBorder = Color.White.copy(alpha = .065f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .20f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = .60f),
                    .38f to Color.Black.copy(alpha = .30f),
                    .72f to Color.Black.copy(alpha = .15f),
                    1f to Color.Black.copy(alpha = .08f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, top = 24.dp, end = 44.dp, bottom = SubtitleWorkspaceBottomClearance),
        ) {
            Text(
                "Subtitles",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a language, track and style",
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        VueoSubtitleColumnTitle("Languages")
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
                                    rightRequester = if (group.code == activeLanguageCode) firstTrackRequester else FocusRequester.Cancel,
                                    onRight = if (group.code != activeLanguageCode && group.tracks.isNotEmpty()) {
                                        {
                                            activeLanguageCode = group.code
                                            styleOpen = !subtitlesDisabled && group.code == selectedLanguageCode
                                            pendingTrackFocusLanguage = group.code
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
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                itemsIndexed(visibleTracks, key = { _, track -> track.key }) { index, track ->
                                    VueoSubtitleTrackRow(
                                        title = track.label,
                                        provider = track.sourceLabel,
                                        detail = track.metadata
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { "ID: $it" }
                                            .orEmpty(),
                                        selected = !subtitlesDisabled && track.selected,
                                        requester = trackRequesters[index],
                                        blockUp = index == 0,
                                        blockDown = index == visibleTracks.lastIndex,
                                        leftRequester = activeLanguageRequester,
                                        rightRequester = if (styleOpen) syncRequester else FocusRequester.Cancel,
                                        onInteraction = onInteraction,
                                        onFocused = { styleReturnTrackIndex = index },
                                    ) {
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
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VueoSubtitleColumnTitle("Style")
                            Spacer(Modifier.weight(1f))
                            VueoSubtitleFloatButton(
                                requester = floatRequester,
                                downRequester = syncRequester,
                                leftRequester = styleLeftRequester,
                                onInteraction = onInteraction,
                            ) {
                                styleFloatMode = true
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (styleOpen && !subtitlesDisabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                VueoSubtitleStepperRow(
                                    title = "Sync",
                                    value = formatSubtitleDelayTv(subtitleDelayMs),
                                    requester = syncRequester,
                                    upRequester = floatRequester,
                                    downRequester = sizeRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        onSubtitleDelayChange((subtitleDelayMs - 250).coerceAtLeast(-60_000))
                                    },
                                    onIncrease = {
                                        onSubtitleDelayChange((subtitleDelayMs + 250).coerceAtMost(60_000))
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
                                        onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp - 2).coerceAtLeast(12)))
                                    },
                                    onIncrease = {
                                        onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp + 2).coerceAtMost(40)))
                                    },
                                )
                                VueoSubtitleToggleRow(
                                    title = "Bold",
                                    enabled = style.bold,
                                    requester = boldRequester,
                                    upRequester = sizeRequester,
                                    downRequester = textColorRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = { onStyleChange(style.copy(bold = !style.bold)) },
                                )
                                VueoSubtitleColorRow(
                                    title = "Text Color",
                                    colours = textColours,
                                    selectedColour = style.textColor,
                                    requester = textColorRequester,
                                    upRequester = boldRequester,
                                    downRequester = opacityRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) { colour ->
                                    onStyleChange(
                                        style.copy(
                                            textColor = subtitleWithAlpha(colour, opacity)
                                        )
                                    )
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
                                        onStyleChange(
                                            style.copy(
                                                textColor = subtitleWithAlpha(style.textColor, (opacity - 10).coerceAtLeast(30))
                                            )
                                        )
                                    },
                                    onIncrease = {
                                        onStyleChange(
                                            style.copy(
                                                textColor = subtitleWithAlpha(style.textColor, (opacity + 10).coerceAtMost(100))
                                            )
                                        )
                                    },
                                )
                                VueoSubtitleToggleRow(
                                    title = "Outline",
                                    enabled = style.outlineEnabled,
                                    requester = outlineRequester,
                                    upRequester = opacityRequester,
                                    downRequester = if (style.outlineEnabled) outlineColorRequester else positionRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onToggle = { onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled)) },
                                )
                                if (style.outlineEnabled) {
                                    VueoSubtitleColorRow(
                                        title = "Outline Color",
                                        colours = outlineColours,
                                        selectedColour = style.outlineColor,
                                        requester = outlineColorRequester,
                                        upRequester = outlineRequester,
                                        downRequester = positionRequester,
                                        leftRequester = styleLeftRequester,
                                        onInteraction = onInteraction,
                                    ) { colour ->
                                        onStyleChange(style.copy(outlineColor = colour))
                                    }
                                }
                                VueoSubtitleStepperRow(
                                    title = "Bottom Position",
                                    value = "${style.bottomPaddingPercent}%",
                                    requester = positionRequester,
                                    upRequester = if (style.outlineEnabled) outlineColorRequester else outlineRequester,
                                    downRequester = resetRequester,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                    onDecrease = {
                                        onStyleChange(
                                            style.copy(
                                                bottomPaddingPercent = (style.bottomPaddingPercent - 2).coerceAtLeast(5)
                                            )
                                        )
                                    },
                                    onIncrease = {
                                        onStyleChange(
                                            style.copy(
                                                bottomPaddingPercent = (style.bottomPaddingPercent + 2).coerceAtMost(40)
                                            )
                                        )
                                    },
                                )
                                VueoSubtitleActionRow(
                                    title = "Reset Style",
                                    detail = "White • 22sp • black outline • 8% bottom",
                                    requester = resetRequester,
                                    upRequester = positionRequester,
                                    downRequester = FocusRequester.Cancel,
                                    leftRequester = styleLeftRequester,
                                    onInteraction = onInteraction,
                                ) {
                                    onStyleChange(TvPlayerSubtitleStyleState())
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
private fun VueoSubtitleFloatButton(
    requester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
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
            .background(
                if (focused) TvDesign.Accent.copy(alpha = .28f) else Color.White.copy(alpha = .08f),
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) TvDesign.Accent else Color.White.copy(alpha = .10f),
                shape,
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Float",
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
                if (blockUp) up = FocusRequester.Cancel
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
                    focused -> TvDesign.Accent.copy(alpha = .26f)
                    selected -> TvDesign.Accent.copy(alpha = .10f)
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
                    focused -> TvDesign.Accent
                    selected -> TvDesign.Accent.copy(alpha = .48f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White,
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
                            focused -> TvDesign.Accent.copy(alpha = .34f)
                            selected -> TvDesign.Accent.copy(alpha = .18f)
                            else -> Color.White.copy(alpha = .09f)
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    it.toString(),
                    color = Color.White.copy(alpha = .92f),
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
                    focused -> TvDesign.Accent.copy(alpha = .24f)
                    selected -> TvDesign.Accent.copy(alpha = .10f)
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
                    focused -> TvDesign.Accent
                    selected -> TvDesign.Accent.copy(alpha = .48f)
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
                        if (selected || focused) TvDesign.Accent.copy(alpha = .12f)
                        else Color.White.copy(alpha = .055f),
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        1.dp,
                        if (selected || focused) TvDesign.Accent.copy(alpha = .38f)
                        else Color.White.copy(alpha = .09f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    provider.ifBlank { "Subtitle" },
                    color = if (selected || focused) TvDesign.Accent else Color.White.copy(alpha = .66f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    color = Color.White.copy(alpha = .50f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text(
                "✓",
                color = TvDesign.Accent,
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
) {
    val minusRequester = remember(title) { FocusRequester() }
    val internalValueRequester = remember(title) { FocusRequester() }
    val plusRequester = remember(title) { FocusRequester() }
    val valueRequester = requester ?: internalValueRequester

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VueoSubtitleStepperButton(
                label = "−",
                modifier = Modifier.width(46.dp),
                requester = minusRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = leftRequester,
                rightRequester = valueRequester,
                onInteraction = onInteraction,
                onClick = onDecrease,
            )
            VueoSubtitleStepperButton(
                label = value,
                modifier = Modifier.weight(1f),
                requester = valueRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = minusRequester,
                rightRequester = plusRequester,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
            VueoSubtitleStepperButton(
                label = "+",
                modifier = Modifier.width(46.dp),
                requester = plusRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                leftRequester = valueRequester,
                rightRequester = FocusRequester.Cancel,
                onInteraction = onInteraction,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun VueoSubtitleStepperButton(
    label: String,
    modifier: Modifier,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(requester) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)

    Box(
        modifier = modifier
            .height(38.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
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
                if (focused) TvDesign.Accent.copy(alpha = .32f) else Color.White.copy(alpha = .09f),
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) TvDesign.Accent else Color.White.copy(alpha = .08f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = if (label == "+" || label == "−") 18.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
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
    val shape = RoundedCornerShape(11.dp)

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(38.dp)
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
                .background(
                    when {
                        focused -> TvDesign.Accent.copy(alpha = .32f)
                        enabled -> TvDesign.Accent.copy(alpha = .14f)
                        else -> Color.White.copy(alpha = .09f)
                    },
                    shape,
                )
                .border(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> TvDesign.Accent
                        enabled -> TvDesign.Accent.copy(alpha = .55f)
                        else -> Color.White.copy(alpha = .08f)
                    },
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enabled) "On" else "Off",
                color = Color.White,
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
    val selectedIndex = colours.indexOfFirst {
        (selectedColour and 0x00FFFFFF) == (it and 0x00FFFFFF)
    }.coerceAtLeast(0)
    val requesters = remember(colours, selectedIndex, requester) {
        List(colours.size) { index ->
            if (index == selectedIndex) requester else FocusRequester()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            colours.forEachIndexed { index, colour ->
                var focused by remember(colour) { mutableStateOf(false) }
                val selected = (selectedColour and 0x00FFFFFF) == (colour and 0x00FFFFFF)
                val swatch = Color(colour)
                val checkColor = if (swatch.luminance() > .48f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        .border(
                            width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                            color = when {
                                focused -> TvDesign.Accent
                                selected -> Color.White.copy(alpha = .92f)
                                else -> Color.White.copy(alpha = .18f)
                            },
                            shape = CircleShape,
                        )
                        .padding(4.dp),
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
    detail: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val contentColor = if (focused) subtitleAccentContentColor() else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            .background(if (focused) TvDesign.Accent else Color.White.copy(alpha = .045f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            color = if (focused) contentColor.copy(alpha = .62f) else Color.White.copy(alpha = .44f),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

