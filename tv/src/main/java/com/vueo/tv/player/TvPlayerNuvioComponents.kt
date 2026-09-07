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
import androidx.compose.foundation.layout.offset
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal val VueoTvPlayerAccent = Color(0xFFB9FF3A)

@Composable
internal fun MobileTvPlayerProgressRail(
    positionMs: Long,
    durationMs: Long,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onInteraction: () -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
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
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 4.dp else 3.dp)
                .background(
                    Color.White.copy(alpha = if (focused) .42f else .30f),
                    RoundedCornerShape(50),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth * progress)
                    .background(VueoTvPlayerAccent, RoundedCornerShape(50)),
            )
        }
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - 12.dp) * progress)
                .background(VueoTvPlayerAccent, CircleShape)
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                ),
        )
    }
}

@Composable
internal fun MobileTvPlayerRoundAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    primary: Boolean = false,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val size = if (primary) 78.dp else 62.dp
    val iconSize = if (primary) 52.dp else 42.dp

    Box(
        modifier = Modifier
            .size(size)
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                if (focused) Color.White else Color.Transparent,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = if (focused) Color.Black else Color.White,
        )
    }
}

@Composable
internal fun MobileTvPlayerTopAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && enabled) onClick()
                true
            }
            .focusable(enabled)
            .background(
                if (focused && enabled) Color.White else Color.Black.copy(alpha = .42f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = when {
                !enabled -> Color.White.copy(alpha = .38f)
                focused -> Color.Black
                else -> Color.White.copy(alpha = .94f)
            },
        )
    }
}

@Composable
internal fun MobileTvPlayerPanelAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    upRequester: FocusRequester,
    enabled: Boolean = true,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                up = upRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && enabled) onClick()
                true
            }
            .focusable(enabled)
            .background(if (focused && enabled) Color.White else Color.Transparent, shape)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = when {
                !enabled -> Color.White.copy(alpha = .38f)
                focused -> Color.Black
                else -> Color.White.copy(alpha = .94f)
            },
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> Color.White.copy(alpha = .38f)
                focused -> Color.Black
                else -> Color.White.copy(alpha = .88f)
            },
        )
    }
}

@Composable
internal fun MobileTvPlayerUnlockAction(
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Text(
        text = "Unlock",
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(if (focused) Color.White else Color.Black.copy(alpha = .62f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (focused) Color.Black else Color.White,
        fontWeight = FontWeight.Bold,
    )
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
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .focusRequester(requester)
            .focusProperties { down = downRequester }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(if (focused) Color.White else Color(0xE6161719), shape)
            .border(1.dp, Color.White.copy(alpha = if (focused) .95f else .22f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
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
    PlayerSkipKind.INTRO -> "Skip Intro"
    PlayerSkipKind.RECAP -> "Skip Recap"
    PlayerSkipKind.ENDING -> "Skip Ending"
    else -> "Skip"
}

internal fun nuvioPlayerFormatReleaseDate(raw: String?): String? {
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

@Composable
internal fun MobileTvPlayerChoiceAction(
    label: String,
    requester: FocusRequester,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    primary: Boolean = false,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isVueoActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    focused -> Color.White
                    primary -> VueoTvPlayerAccent.copy(alpha = .14f)
                    else -> Color.White.copy(alpha = .035f)
                },
                shape,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    primary -> VueoTvPlayerAccent.copy(alpha = .58f)
                    else -> Color.White.copy(alpha = .18f)
                },
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = if (focused) Color.Black else Color.White,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
