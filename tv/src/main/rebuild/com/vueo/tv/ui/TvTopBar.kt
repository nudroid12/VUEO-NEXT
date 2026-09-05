package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key

val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

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
) {
    val navAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else .18f,
        animationSpec = tween(150),
        label = "topNavAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 52.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "VUEO",
            color = TvDesign.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.width(34.dp))

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
            selected -> TvDesign.White.copy(alpha = .92f * contentAlpha.coerceAtLeast(.55f))
            else -> TvDesign.Muted.copy(alpha = contentAlpha.coerceAtLeast(.35f))
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
            .onPreviewKeyEvent {
                if (
                    it.type == KeyEventType.KeyDown &&
                    it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onDown()
                } else {
                    false
                }
            }
            .focusable()
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
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(34.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent {
                if (
                    it.type == KeyEventType.KeyDown &&
                    it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onDown()
                } else false
            }
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .20f)
                else TvDesign.White.copy(alpha = .08f),
                shape = CircleShape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .18f),
                shape = CircleShape,
            )
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P",
            color = TvDesign.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
