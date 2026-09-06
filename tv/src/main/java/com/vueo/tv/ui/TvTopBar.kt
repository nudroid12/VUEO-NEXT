package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

/**
 * Shared TV top navigation grammar.
 *
 * D-pad focus movement never activates a route. DPAD_CENTER/ENTER commits the
 * currently focused destination exactly once. We intentionally rely on
 * clickable's single focus target instead of stacking focusable + clickable,
 * which previously created a two-step activation path on some Android TV
 * devices.
 */
@Composable
fun TvTopBar(
    selected: String,
    expanded: Boolean,
    navRequesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    onFocused: () -> Unit,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDownFromNav: () -> Boolean,
    modifier: Modifier = Modifier,
    cinematicCollapsed: Boolean = false,
) {
    val navAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else if (cinematicCollapsed) 0f else .18f,
        animationSpec = tween(150),
        label = "topNavAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 52.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPrimaryDestinations.forEach { label ->
                TvNavItem(
                    label = label,
                    selected = selected == label,
                    contentAlpha = navAlpha,
                    requester = navRequesters.getValue(label),
                    onFocused = onFocused,
                    onClick = { onNavigate(label) },
                    onDown = onDownFromNav,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        TvProfileAnchor(
            requester = profileRequester,
            onFocused = onFocused,
            onClick = onProfile,
            onDown = onDownFromNav,
            compact = cinematicCollapsed,
        )
    }
}

@Composable
private fun TvNavItem(
    label: String,
    selected: Boolean,
    contentAlpha: Float,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onDown: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> TvDesign.White
            selected -> TvDesign.White.copy(alpha = .92f * contentAlpha)
            else -> TvDesign.Muted.copy(alpha = contentAlpha)
        },
        animationSpec = tween(120),
        label = "navTextColor",
    )

    Text(
        text = label,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown()
                    }

                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }

                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun TvProfileAnchor(
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onDown: () -> Boolean,
    compact: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (compact) 30.dp else 34.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown()
                    }

                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }

                    else -> false
                }
            }
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .20f)
                else TvDesign.White.copy(alpha = if (compact) .055f else .08f),
                shape = CircleShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = if (compact) .14f else .18f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P",
            color = TvDesign.White,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
