package com.vueo.tv.ui

import android.view.KeyEvent
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

private val SidebarPanelShape = RoundedCornerShape(22.dp)
private val SidebarItemShape = RoundedCornerShape(14.dp)

/**
 * Clean TV sidebar shell.
 *
 * One set of focus targets is kept on screen at all times. When content owns
 * focus it is a quiet icon rail. Entering the rail expands the same targets
 * into a labelled panel, avoiding the duplicate hidden/visible targets that
 * caused earlier top-left focus artifacts.
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
    val width by animateDpAsState(
        targetValue = if (expanded) 224.dp else 64.dp,
        animationSpec = tween(180),
        label = "tvSidebarWidth",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (expanded) .97f else 0f,
        animationSpec = tween(140),
        label = "tvSidebarPanelAlpha",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(120),
        label = "tvSidebarLabelAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .padding(start = 12.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width - 12.dp)
                .background(
                    color = TvDesign.Black.copy(alpha = panelAlpha),
                    shape = SidebarPanelShape,
                )
                .border(
                    width = if (expanded) 1.dp else 0.dp,
                    color = TvDesign.White.copy(alpha = .10f * panelAlpha),
                    shape = SidebarPanelShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(width - 12.dp)
                .padding(vertical = if (expanded) 12.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (expanded) {
                SidebarEntry(
                    label = "Profile",
                    icon = Icons.Default.Person,
                    selected = false,
                    expanded = true,
                    labelAlpha = labelAlpha,
                    requester = profileRequester,
                    onFocused = onFocused,
                    onClick = onProfile,
                    onLeft = { true },
                    onRight = onReturnToContent,
                    onUp = { true },
                    onDown = {
                        runCatching { navRequesters.getValue("Home").requestFocus() }
                        true
                    },
                )
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TvPrimaryDestinations.forEachIndexed { index, label ->
                    SidebarEntry(
                        label = label,
                        icon = destinationIcon(label),
                        selected = selected == label,
                        expanded = expanded,
                        labelAlpha = labelAlpha,
                        requester = navRequesters.getValue(label),
                        onFocused = onFocused,
                        onClick = { onNavigate(label) },
                        onLeft = { true },
                        onRight = onReturnToContent,
                        onUp = {
                            when {
                                index > 0 -> {
                                    runCatching {
                                        navRequesters.getValue(TvPrimaryDestinations[index - 1]).requestFocus()
                                    }
                                    true
                                }
                                expanded -> {
                                    runCatching { profileRequester.requestFocus() }
                                    true
                                }
                                else -> true
                            }
                        },
                        onDown = {
                            if (index < TvPrimaryDestinations.lastIndex) {
                                runCatching {
                                    navRequesters.getValue(TvPrimaryDestinations[index + 1]).requestFocus()
                                }
                            }
                            true
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SidebarEntry(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    labelAlpha: Float,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val itemWidth = if (expanded) 190.dp else 44.dp
    val background = when {
        focused -> TvDesign.White.copy(alpha = .17f)
        selected -> TvDesign.White.copy(alpha = .095f)
        else -> Color.Transparent
    }
    val border = when {
        focused -> TvDesign.White.copy(alpha = .72f)
        selected -> TvDesign.White.copy(alpha = .12f)
        else -> Color.Transparent
    }
    val content = if (focused || selected) TvDesign.White else TvDesign.White.copy(alpha = .72f)

    Row(
        modifier = Modifier
            .width(itemWidth)
            .height(46.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> onLeft()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    else -> false
                }
            }
            .background(background, SidebarItemShape)
            .border(1.dp, border, SidebarItemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (focused || selected) TvDesign.White.copy(alpha = .08f) else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(21.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.width(11.dp))
            Text(
                text = label,
                color = content.copy(alpha = labelAlpha),
                fontSize = 15.sp,
                fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun destinationIcon(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "Search" -> Icons.Default.Search
    "Library" -> Icons.Default.VideoLibrary
    "Settings" -> Icons.Default.Settings
    else -> Icons.Default.Home
}
