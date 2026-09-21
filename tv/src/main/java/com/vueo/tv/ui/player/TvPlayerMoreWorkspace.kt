package com.vueo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.tv.ui.TvDesign

private val MorePlaybackSpeeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)

@Composable
internal fun VueoPlayerMoreWorkspace(
    playbackSpeed: Float,
    videoFit: PlayerVideoFit,
    autoPlayNextEpisode: Boolean,
    skipSegmentsEnabled: Boolean,
    contentWarningsEnabled: Boolean,
    onInteraction: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoFitChange: (PlayerVideoFit) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
    onSkipSegmentsChange: (Boolean) -> Unit,
    onContentWarningsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val speedRequester = remember { FocusRequester() }
    val fitRequester = remember { FocusRequester() }
    val autoPlayRequester = remember { FocusRequester() }
    val skipRequester = remember { FocusRequester() }
    val warningRequester = remember { FocusRequester() }
    val resetRequester = remember { FocusRequester() }
    var initialFocusAssigned by remember { mutableStateOf(false) }

    LaunchedEffect(initialFocusAssigned) {
        if (!initialFocusAssigned) {
            initialFocusAssigned = speedRequester.requestTvFocus()
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
                    0f to Color.Black.copy(alpha = .12f),
                    .46f to Color.Black.copy(alpha = .28f),
                    .72f to Color.Black.copy(alpha = .58f),
                    1f to Color.Black.copy(alpha = .94f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.44f)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(top = 24.dp, end = 44.dp, bottom = SubtitleWorkspaceBottomClearance),
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(PanelShape)
                    .background(cardBackground)
                    .border(1.dp, cardBorder, PanelShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        VueoSubtitleColumnTitle("Playback")
                        Spacer(Modifier.height(12.dp))
                        VueoMoreCycleRow(
                            label = "Speed",
                            value = formatMoreSpeed(playbackSpeed),
                            requester = speedRequester,
                            upRequester = FocusRequester.Cancel,
                            downRequester = fitRequester,
                            leftRequester = FocusRequester.Cancel,
                            rightRequester = autoPlayRequester,
                            onInteraction = onInteraction,
                        ) {
                            onPlaybackSpeedChange(nextMoreSpeed(playbackSpeed))
                        }
                        Spacer(Modifier.height(7.dp))
                        VueoMoreCycleRow(
                            label = "Video fit",
                            value = videoFit.label,
                            requester = fitRequester,
                            upRequester = speedRequester,
                            downRequester = FocusRequester.Cancel,
                            leftRequester = FocusRequester.Cancel,
                            rightRequester = skipRequester,
                            onInteraction = onInteraction,
                        ) {
                            onVideoFitChange(nextMoreVideoFit(videoFit))
                        }
                        Text(
                            text = moreVideoFitDescription(videoFit),
                            color = Color.White.copy(alpha = .42f),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = .08f))
                    )

                    Column(Modifier.weight(1.08f)) {
                        VueoSubtitleColumnTitle("Behaviour")
                        Spacer(Modifier.height(6.dp))
                        VueoMoreToggleRow(
                            label = "Auto-play next episode",
                            checked = autoPlayNextEpisode,
                            requester = autoPlayRequester,
                            upRequester = FocusRequester.Cancel,
                            downRequester = skipRequester,
                            leftRequester = speedRequester,
                            onInteraction = onInteraction,
                        ) { onAutoPlayNextEpisodeChange(!autoPlayNextEpisode) }
                        VueoMoreToggleRow(
                            label = "Skip intro and ending",
                            checked = skipSegmentsEnabled,
                            requester = skipRequester,
                            upRequester = autoPlayRequester,
                            downRequester = warningRequester,
                            leftRequester = fitRequester,
                            onInteraction = onInteraction,
                        ) { onSkipSegmentsChange(!skipSegmentsEnabled) }
                        VueoMoreToggleRow(
                            label = "Content warnings",
                            checked = contentWarningsEnabled,
                            requester = warningRequester,
                            upRequester = skipRequester,
                            downRequester = resetRequester,
                            leftRequester = fitRequester,
                            onInteraction = onInteraction,
                        ) { onContentWarningsChange(!contentWarningsEnabled) }
                        Spacer(Modifier.weight(1f))
                        VueoMoreResetButton(
                            requester = resetRequester,
                            upRequester = warningRequester,
                            leftRequester = fitRequester,
                            onInteraction = onInteraction,
                            onClick = onReset,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VueoMoreCycleRow(
    label: String,
    value: String,
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
                if (focused) Color.White.copy(alpha = .13f)
                else Color.White.copy(alpha = .055f),
                shape,
            )
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = .07f),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = if (focused) Color(0xFF151A11) else TvDesign.Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(
                    if (focused) Color.White else TvDesign.Accent.copy(alpha = .12f),
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 13.dp, vertical = 6.dp),
        )
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

private fun nextMoreSpeed(current: Float): Float {
    val index = MorePlaybackSpeeds.indexOf(current)
    return MorePlaybackSpeeds[(index + 1) % MorePlaybackSpeeds.size]
}

private fun nextMoreVideoFit(current: PlayerVideoFit): PlayerVideoFit {
    val options = PlayerVideoFit.entries
    return options[(options.indexOf(current) + 1) % options.size]
}

private fun moreVideoFitDescription(videoFit: PlayerVideoFit): String = when (videoFit) {
    PlayerVideoFit.FIT -> "Shows the complete frame."
    PlayerVideoFit.FILL -> "Fills the screen dimensions."
    PlayerVideoFit.ZOOM -> "Crops edges to fill without stretching."
}
