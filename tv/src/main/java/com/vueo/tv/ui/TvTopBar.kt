package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

/**
 * 29C.3 global TV navigation shell.
 *
 * The rail stays quiet and narrow while content owns focus. DPAD_LEFT from a
 * content edge enters the current destination. Once the rail owns focus it
 * expands just enough to reveal labels. DPAD_UP/DOWN explores destinations,
 * DPAD_RIGHT returns to the exact last content focus, and OK commits once.
 * Focus movement never changes route.
 */
@Composable
fun TvSidebar(
    selected: String,
    expanded: Boolean,
    navRequesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    onFocused: () -> Unit,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReturnToContent: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val railWidth by animateDpAsState(
        targetValue = if (expanded) 202.dp else 66.dp,
        animationSpec = tween(170),
        label = "sidebarWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 145 else 95),
        label = "sidebarLabelAlpha",
    )
    val shellAlpha by animateFloatAsState(
        targetValue = if (expanded) .94f else .68f,
        animationSpec = tween(150),
        label = "sidebarShellAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .padding(start = 14.dp, top = 18.dp, bottom = 18.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        TvDesign.Black.copy(alpha = shellAlpha),
                        TvDesign.SurfaceRaised.copy(alpha = shellAlpha * .84f),
                    )
                ),
                shape = SidebarShape,
            )
            .border(
                width = 1.dp,
                color = TvDesign.White.copy(alpha = if (expanded) .12f else .075f),
                shape = SidebarShape,
            )
            .padding(horizontal = 7.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(.72f))

            TvPrimaryDestinations.forEachIndexed { index, label ->
                TvSidebarItem(
                    label = label,
                    icon = destinationIcon(label),
                    selected = selected == label,
                    labelAlpha = labelAlpha,
                    requester = navRequesters.getValue(label),
                    onFocused = onFocused,
                    onClick = { onNavigate(label) },
                    onUp = {
                        if (index > 0) {
                            runCatching {
                                navRequesters.getValue(TvPrimaryDestinations[index - 1]).requestFocus()
                            }
                        }
                        true
                    },
                    onDown = {
                        if (index < TvPrimaryDestinations.lastIndex) {
                            runCatching {
                                navRequesters.getValue(TvPrimaryDestinations[index + 1]).requestFocus()
                            }
                        } else {
                            runCatching { profileRequester.requestFocus() }
                        }
                        true
                    },
                    onRight = onReturnToContent,
                )
                if (index != TvPrimaryDestinations.lastIndex) Spacer(Modifier.height(3.dp))
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TvDesign.White.copy(alpha = .07f)),
            )
            Spacer(Modifier.height(8.dp))

            TvSidebarItem(
                label = "Profile",
                icon = Icons.Default.Person,
                selected = false,
                labelAlpha = labelAlpha,
                requester = profileRequester,
                onFocused = onFocused,
                onClick = onProfile,
                onUp = {
                    runCatching { navRequesters.getValue("Settings").requestFocus() }
                    true
                },
                onDown = { true },
                onRight = onReturnToContent,
                profile = true,
            )
        }
    }
}

private val SidebarShape = RoundedCornerShape(24.dp)
private val SidebarItemShape = RoundedCornerShape(14.dp)

@Composable
private fun TvSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    labelAlpha: Float,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    onRight: () -> Boolean,
    profile: Boolean = false,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val fill by animateColorAsState(
        targetValue = when {
            focused -> TvDesign.White.copy(alpha = .15f)
            selected -> TvDesign.White.copy(alpha = .085f)
            else -> Color.Transparent
        },
        animationSpec = tween(110),
        label = "sidebarItemFill",
    )
    val edge by animateColorAsState(
        targetValue = if (focused) TvDesign.White.copy(alpha = .42f) else Color.Transparent,
        animationSpec = tween(100),
        label = "sidebarItemEdge",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            focused -> TvDesign.White
            selected -> TvDesign.White.copy(alpha = .94f)
            else -> TvDesign.Muted.copy(alpha = .88f)
        },
        animationSpec = tween(100),
        label = "sidebarIconTint",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> true
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(fill, SidebarItemShape)
            .border(1.dp, edge, SidebarItemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (profile) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = TvDesign.White.copy(alpha = if (focused) .18f else .09f),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = if (focused) TvDesign.White.copy(alpha = .62f)
                            else TvDesign.White.copy(alpha = .15f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = TvDesign.White.copy(alpha = labelAlpha),
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun destinationIcon(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "Search" -> Icons.Default.Search
    "Library" -> Icons.Default.VideoLibrary
    "Settings" -> Icons.Default.Settings
    else -> Icons.Default.Home
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
