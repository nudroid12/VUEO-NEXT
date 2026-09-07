package com.vueo.tv.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.player.PlayerSkipKind
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.tv.ui.TvDesign

@Composable
internal fun NuvioPlayerProgressRail(
    positionMs: Long,
    durationMs: Long,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onHideControls: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (focused) 10.dp else 6.dp)
            .focusRequester(requester)
            .focusProperties { down = downRequester }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeekBy(-10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeekBy(10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onHideControls()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .background(
                Color.White.copy(alpha = if (focused) .40f else .24f),
                RoundedCornerShape(3.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(maxWidth * progress)
                .background(TvDesign.Accent, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
internal fun NuvioPlayerControlButton(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onDown: () -> Unit,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .focusRequester(requester)
            .focusProperties { up = upRequester }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    onDown()
                    return@onPreviewKeyEvent true
                }
                if (!event.isNuvioActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && enabled) onClick()
                true
            }
            .focusable(enabled)
            .background(
                when {
                    !enabled -> Color.Transparent
                    focused -> Color.White
                    else -> Color.Transparent
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> Color.White.copy(alpha = .30f)
                focused -> Color.Black
                else -> Color.White.copy(alpha = .92f)
            },
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
internal fun NuvioPlayerPromptButton(
    text: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .focusRequester(requester)
            .focusProperties { down = downRequester }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isNuvioActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(if (focused) Color.White else Color.Black.copy(alpha = .82f), shape)
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = .18f),
                shape,
            )
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun nuvioPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal fun nuvioSkipLabel(segment: PlayerSkipSegment): String = when (segment.kind) {
    PlayerSkipKind.INTRO -> "Skip intro"
    PlayerSkipKind.RECAP -> "Skip recap"
    PlayerSkipKind.ENDING -> "Skip credits"
    else -> "Skip"
}

private fun androidx.compose.ui.input.key.KeyEvent.isNuvioActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
