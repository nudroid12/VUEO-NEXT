package com.vueo.tv.player

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
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
import com.vueo.tv.ui.motion.TvMotion
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal val LocalPlayerChromeInteractive = staticCompositionLocalOf { true }

@Composable
internal fun VueoPlayerProgressRail(
    positionMs: Long,
    durationMs: Long,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onInteraction: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    onTogglePlayback: () -> Unit,
    interactive: Boolean = true,
) {
    val acceptsInput = interactive && LocalPlayerChromeInteractive.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus && acceptsInput
    val targetProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 380, easing = LinearEasing),
        label = "playerProgress",
    )
    val railHeight by animateDpAsState(
        targetValue = if (focused) 5.dp else 3.dp,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "playerProgressHeight",
    )
    val markerRadius by animateDpAsState(
        targetValue = if (focused) 5.dp else 3.5.dp,
        animationSpec = tween(TvMotion.FOCUS_IN_MS, easing = TvMotion.EaseOut),
        label = "playerProgressMarker",
    )

    val inputModifier = if (acceptsInput) {
        Modifier
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeekBy(
                            tvLongPressSeekDeltaMs(
                                direction = -1,
                                heldDurationMs = if (event.nativeKeyEvent.repeatCount == 0) 0L else
                                    (event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime).coerceAtLeast(0L),
                            )
                        )
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeekBy(
                            tvLongPressSeekDeltaMs(
                                direction = 1,
                                heldDurationMs = if (event.nativeKeyEvent.repeatCount == 0) 0L else
                                    (event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime).coerceAtLeast(0L),
                            )
                        )
                        true
                    }
                    event.type == KeyEventType.KeyUp &&
                        (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) -> {
                        onSeekCommit()
                        true
                    }
                    event.isVueoActivationKey() -> {
                        onInteraction()
                        if (event.type == KeyEventType.KeyUp) onTogglePlayback()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    } else Modifier

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(railHeight)
            .then(inputModifier),
    ) {
        val centerY = size.height / 2f
        val endX = size.width * progress
        val accent = TvDesign.Accent.copy(alpha = 1f)
        // No full-width white border: it obscures the played segment on a thin rail.
        drawLine(Color.Black.copy(alpha = .65f), Offset(0f, centerY),
            Offset(size.width, centerY), strokeWidth = size.height + 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .20f), Offset(0f, centerY),
            Offset(size.width, centerY), strokeWidth = size.height, cap = StrokeCap.Round)
        if (endX > 0f) drawLine(accent, Offset(0f, centerY), Offset(endX, centerY),
            strokeWidth = size.height, cap = StrokeCap.Round)
        val radius = markerRadius.toPx()
        val markerX = endX.coerceIn(radius, (size.width - radius).coerceAtLeast(radius))
        drawCircle(Color.Black.copy(alpha = .75f), radius + 1.dp.toPx(), Offset(markerX, centerY))
        drawCircle(accent, radius, Offset(markerX, centerY))
    }

}

@Composable
internal fun VueoPlayerTopAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val acceptsInput = enabled && LocalPlayerChromeInteractive.current
    var hasFocus by remember(requester) { mutableStateOf(false) }
    val focused = hasFocus && acceptsInput
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.08f else 1f,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "playerTopActionScale",
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties {
                canFocus = acceptsInput
                up = FocusRequester.Cancel
                down = downRequester
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused && acceptsInput) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!acceptsInput) return@onPreviewKeyEvent true
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && enabled) onClick()
                true
            }
            .focusable(acceptsInput)
            .background(
                if (focused && enabled) Color.White else TvPlayerTopActionBackground,
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
                else -> Color.White
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
internal fun VueoPlayerPillAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    val acceptsInput = LocalPlayerChromeInteractive.current
    var hasFocus by remember(requester) { mutableStateOf(false) }
    val focused = hasFocus && acceptsInput
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "playerPillActionScale",
    )
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties {
                canFocus = acceptsInput
                up = upRequester
                down = FocusRequester.Cancel
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused && acceptsInput) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!acceptsInput) return@onPreviewKeyEvent true
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable(acceptsInput)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
        )
    }
}

internal enum class TvPlayerPromptTarget { NONE, SKIP, NEXT }

@Composable
internal fun VueoPlayerPromptButton(
    text: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    var focused by remember(requester) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .widthIn(max = 320.dp)
            .heightIn(min = 44.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = downRequester
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(if (focused) Color.White else Color(0xFF303030), shape)
            .border(1.dp, Color.White.copy(alpha = if (focused) .92f else .18f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun vueoPlayerRemainingTime(positionMs: Long, durationMs: Long): String =
    if (durationMs > 0L) "-${vueoPlayerTime((durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L))}"
    else "--:--"

internal fun vueoPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal fun vueoSkipLabel(segment: PlayerSkipSegment): String = when (segment.kind) {
    PlayerSkipKind.INTRO -> "Skip intro"
    PlayerSkipKind.RECAP -> "Skip recap"
    PlayerSkipKind.ENDING -> "Skip credits"
    else -> "Skip"
}

internal fun vueoPlayerFormatReleaseDate(raw: String?): String? {
    val input = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(input)
        }.getOrNull() ?: continue
        return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(parsed)
    }
    return input.substringBefore('T').takeIf { it != input } ?: input
}

private fun androidx.compose.ui.input.key.KeyEvent.isVueoActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
