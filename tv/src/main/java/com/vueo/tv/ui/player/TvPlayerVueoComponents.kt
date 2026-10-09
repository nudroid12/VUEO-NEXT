package com.vueo.tv.player

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
    onSeekImmediateBy: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    onTogglePlayback: () -> Unit,
    seekMotionDirect: Boolean = false,
    interactive: Boolean = true,
    emphasized: Boolean = false,
) {
    val acceptsInput = interactive && LocalPlayerChromeInteractive.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = (hasFocus && acceptsInput) || emphasized
    val targetProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (seekMotionDirect) snap() else tween(durationMillis = 380, easing = LinearEasing),
        label = "playerProgress",
    )
    // Seek previews bypass animation immediately, including the release frame.
    val progress = if (seekMotionDirect) targetProgress else animatedProgress
    val railHeight by animateDpAsState(
        targetValue = if (focused) 5.dp else 3.dp,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "playerProgressHeight",
    )
    val shape = RoundedCornerShape(50)

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
                        val repeatCount = event.nativeKeyEvent.repeatCount
                        val delta = tvLongPressSeekDeltaMs(
                            direction = -1,
                            heldDurationMs = if (repeatCount == 0) 0L else
                                (event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime).coerceAtLeast(0L),
                        )
                        if (repeatCount == 0) onSeekImmediateBy(delta) else onSeekBy(delta)
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val repeatCount = event.nativeKeyEvent.repeatCount
                        val delta = tvLongPressSeekDeltaMs(
                            direction = 1,
                            heldDurationMs = if (repeatCount == 0) 0L else
                                (event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime).coerceAtLeast(0L),
                        )
                        if (repeatCount == 0) onSeekImmediateBy(delta) else onSeekBy(delta)
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

    // Keep a small fixed visual lane so the progress thumb can sit around the
    // rail without changing its horizontal geometry. The actual line remains
    // 3dp/5dp and is centered inside this lane.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .then(inputModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Capture the BoxWithConstraints width before entering nested BoxScope
        // receivers. This avoids ambiguous/invalid implicit receiver access.
        val availableWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(railHeight)
                .background(Color.White.copy(alpha = .14f), shape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(availableWidth * progress)
                    .background(TvDesign.Accent.copy(alpha = 1f), shape),
            )
        }

        val thumbSize = if (focused) 12.dp else 10.dp
        Box(
            modifier = Modifier
                .offset(x = (availableWidth - thumbSize) * progress)
                .size(thumbSize)
                .background(TvDesign.Accent, CircleShape),
        )
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
    downRequester: FocusRequester = FocusRequester.Cancel,
    onFocusChanged: (Boolean) -> Unit = {},
    iconOnly: Boolean = false,
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
                down = downRequester
                left = leftRequester
                right = rightRequester
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                onFocusChanged(it.isFocused && acceptsInput)
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
            .then(if (iconOnly) Modifier.size(24.dp) else Modifier.padding(horizontal = 9.dp, vertical = 5.dp)),
        horizontalArrangement = if (iconOnly) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (iconOnly) label else null,
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(if (iconOnly) 24.dp else 16.dp),
        )
        if (!iconOnly) Text(
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
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
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
                val code = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP && onNavigateUp != null) {
                    onInteraction()
                    onNavigateUp()
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN && onNavigateDown != null) {
                    onInteraction()
                    onNavigateDown()
                    return@onPreviewKeyEvent true
                }
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
