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

internal enum class TvPlayerSleepTimerOption(
    val label: String,
    val minutes: Int? = null,
    val endOfEpisode: Boolean = false,
) {
    OFF("Off"),
    MINUTES_15("15 min", minutes = 15),
    MINUTES_30("30 min", minutes = 30),
    MINUTES_45("45 min", minutes = 45),
    MINUTES_60("60 min", minutes = 60),
    END_OF_EPISODE("End of episode", endOfEpisode = true),
}

@Composable
internal fun VueoPlayerMoreWorkspace(
    playbackSpeed: Float,
    videoFit: PlayerVideoFit,
    sleepTimer: TvPlayerSleepTimerOption,
    sleepTimerRemainingSeconds: Long?,
    autoPlayNextEpisode: Boolean,
    skipSegmentsEnabled: Boolean,
    contentWarningsEnabled: Boolean,
    onInteraction: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoFitChange: (PlayerVideoFit) -> Unit,
    onSleepTimerChange: (TvPlayerSleepTimerOption) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
    onSkipSegmentsChange: (Boolean) -> Unit,
    onContentWarningsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val speeds = remember { listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f) }
    val sleepOptions = remember { TvPlayerSleepTimerOption.entries.toList() }
    val speedRequesters = remember { List(speeds.size) { FocusRequester() } }
    val fitRequesters = remember { List(PlayerVideoFit.entries.size) { FocusRequester() } }
    val sleepRequesters = remember { List(sleepOptions.size) { FocusRequester() } }
    val autoPlayRequester = remember { FocusRequester() }
    val skipRequester = remember { FocusRequester() }
    val warningRequester = remember { FocusRequester() }
    val resetRequester = remember { FocusRequester() }
    var initialFocusAssigned by remember { mutableStateOf(false) }

    val selectedSpeedIndex = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
    val selectedFitIndex = PlayerVideoFit.entries.indexOf(videoFit).coerceAtLeast(0)
    val selectedSleepIndex = sleepOptions.indexOf(sleepTimer).coerceAtLeast(0)
    val playbackReturnRequester = speedRequesters[selectedSpeedIndex]
    val sleepReturnRequester = sleepRequesters[selectedSleepIndex]

    LaunchedEffect(initialFocusAssigned, playbackSpeed) {
        if (!initialFocusAssigned) {
            initialFocusAssigned = speedRequesters[selectedSpeedIndex].requestTvFocus()
        }
    }

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
                "More",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Playback and session controls",
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Playback")
                    Spacer(Modifier.height(18.dp))
                    VueoMoreLabel("Speed")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        speeds.forEachIndexed { index, speed ->
                            VueoMoreChoiceChip(
                                label = formatMoreSpeed(speed),
                                selected = playbackSpeed == speed,
                                requester = speedRequesters[index],
                                upRequester = FocusRequester.Cancel,
                                downRequester = fitRequesters[selectedFitIndex],
                                leftRequester = if (index == 0) FocusRequester.Cancel else speedRequesters[index - 1],
                                rightRequester = if (index == speeds.lastIndex) sleepReturnRequester else speedRequesters[index + 1],
                                onInteraction = onInteraction,
                                compact = true,
                            ) { onPlaybackSpeedChange(speed) }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    VueoMoreLabel("Video fit")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PlayerVideoFit.entries.forEachIndexed { index, fit ->
                            VueoMoreChoiceChip(
                                label = fit.label,
                                selected = videoFit == fit,
                                requester = fitRequesters[index],
                                upRequester = playbackReturnRequester,
                                downRequester = FocusRequester.Cancel,
                                leftRequester = if (index == 0) FocusRequester.Cancel else fitRequesters[index - 1],
                                rightRequester = if (index == PlayerVideoFit.entries.lastIndex) sleepReturnRequester else fitRequesters[index + 1],
                                onInteraction = onInteraction,
                            ) { onVideoFitChange(fit) }
                        }
                    }
                    Text(
                        text = when (videoFit) {
                            PlayerVideoFit.FIT -> "Shows the complete frame."
                            PlayerVideoFit.FILL -> "Fills the screen dimensions."
                            PlayerVideoFit.ZOOM -> "Crops edges to fill without stretching."
                        },
                        color = Color.White.copy(alpha = .42f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(.90f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Sleep timer")
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = moreSleepTimerStatus(sleepTimer, sleepTimerRemainingSeconds),
                        color = TvDesign.Accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        sleepOptions.forEachIndexed { index, option ->
                            VueoMoreOptionRow(
                                label = option.label,
                                selected = sleepTimer == option,
                                requester = sleepRequesters[index],
                                upRequester = if (index == 0) FocusRequester.Cancel else sleepRequesters[index - 1],
                                downRequester = if (index == sleepOptions.lastIndex) FocusRequester.Cancel else sleepRequesters[index + 1],
                                leftRequester = playbackReturnRequester,
                                rightRequester = when (index) {
                                    0, 1 -> autoPlayRequester
                                    2, 3 -> skipRequester
                                    else -> warningRequester
                                },
                                onInteraction = onInteraction,
                            ) { onSleepTimerChange(option) }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(cardBackground)
                        .border(1.dp, cardBorder, PanelShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    VueoSubtitleColumnTitle("Behaviour")
                    Spacer(Modifier.height(16.dp))
                    VueoMoreToggleRow(
                        label = "Auto-play next episode",
                        checked = autoPlayNextEpisode,
                        requester = autoPlayRequester,
                        upRequester = FocusRequester.Cancel,
                        downRequester = skipRequester,
                        leftRequester = sleepReturnRequester,
                        onInteraction = onInteraction,
                    ) { onAutoPlayNextEpisodeChange(!autoPlayNextEpisode) }
                    VueoMoreToggleRow(
                        label = "Skip intro and ending",
                        checked = skipSegmentsEnabled,
                        requester = skipRequester,
                        upRequester = autoPlayRequester,
                        downRequester = warningRequester,
                        leftRequester = sleepReturnRequester,
                        onInteraction = onInteraction,
                    ) { onSkipSegmentsChange(!skipSegmentsEnabled) }
                    VueoMoreToggleRow(
                        label = "Content warnings",
                        checked = contentWarningsEnabled,
                        requester = warningRequester,
                        upRequester = skipRequester,
                        downRequester = resetRequester,
                        leftRequester = sleepReturnRequester,
                        onInteraction = onInteraction,
                    ) { onContentWarningsChange(!contentWarningsEnabled) }
                    Spacer(Modifier.weight(1f))
                    VueoMoreResetButton(
                        requester = resetRequester,
                        upRequester = warningRequester,
                        leftRequester = sleepReturnRequester,
                        onInteraction = onInteraction,
                        onClick = onReset,
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoMoreLabel(label: String) {
    Text(
        label,
        color = Color.White.copy(alpha = .60f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun VueoMoreChoiceChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
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
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> Color.White
                    selected -> TvDesign.Accent
                    else -> Color.White.copy(alpha = .06f)
                },
                shape,
            )
            .border(
                1.dp,
                when {
                    focused -> Color.White
                    selected -> TvDesign.Accent.copy(alpha = .70f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape,
            )
            .padding(
                horizontal = if (compact) 7.dp else 13.dp,
                vertical = if (compact) 8.dp else 9.dp,
            ),
    ) {
        Text(
            label,
            color = if (focused || selected) Color(0xFF151A11) else Color.White.copy(alpha = .74f),
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VueoMoreOptionRow(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> Color.White.copy(alpha = .13f)
                    selected -> TvDesign.Accent.copy(alpha = .11f)
                    else -> Color.White.copy(alpha = .035f)
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    selected -> TvDesign.Accent.copy(alpha = .48f)
                    else -> Color.White.copy(alpha = .06f)
                },
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(Modifier.size(7.dp).background(TvDesign.Accent, CircleShape))
        }
    }
}

@Composable
private fun VueoMoreToggleRow(
    label: String,
    checked: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
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
            .clickable(onClick = onClick)
            .background(if (focused) Color.White.copy(alpha = .10f) else Color.Transparent, shape)
            .border(if (focused) 1.dp else 0.dp, if (focused) Color.White.copy(alpha = .45f) else Color.Transparent, shape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(20.dp)
                .background(
                    if (checked) TvDesign.Accent.copy(alpha = .88f) else Color.White.copy(alpha = .16f),
                    RoundedCornerShape(999.dp),
                )
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(14.dp)
                    .background(if (checked) Color(0xFF151A11) else Color.White.copy(alpha = .82f), CircleShape),
            )
        }
    }
}

@Composable
private fun VueoMoreResetButton(
    requester: FocusRequester,
    upRequester: FocusRequester,
    leftRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = FocusRequester.Cancel
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
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = .22f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Reset player controls",
            color = if (focused) Color.Black else Color.White.copy(alpha = .78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMoreSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun moreSleepTimerStatus(
    option: TvPlayerSleepTimerOption,
    remainingSeconds: Long?,
): String = when {
    option == TvPlayerSleepTimerOption.END_OF_EPISODE -> "Stops when this episode ends"
    remainingSeconds != null -> {
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L
        "%d:%02d remaining".format(minutes, seconds)
    }
    else -> "Not active"
}

