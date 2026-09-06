package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

/**
 * 30C TV navigation shell.
 *
 * Rest state is a stable icon rail: no floating route pill, no delayed shape change,
 * and no invisible focusable panel. LEFT from content opens the full panel; RIGHT
 * restores the screen's exact last content target through [onReturnToContent].
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
    val panelProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 220 else 150,
            easing = FastOutSlowInEasing,
        ),
        label = "tvSidebarPanelProgress",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 130 else 90),
        label = "tvSidebarLabelAlpha",
    )

    LaunchedEffect(expanded, selected) {
        if (expanded) {
            delay(24L)
            runCatching { navRequesters.getValue(selected).requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(268.dp),
    ) {
        if (!expanded && panelProgress < .01f) {
            TvCollapsedIconRail(
                selected = selected,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        if (expanded || panelProgress > .01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 24.dp, bottom = 24.dp)
                    .width(244.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = panelProgress
                        val s = .97f + (.03f * panelProgress)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, .5f)
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                TvDesign.SurfaceRaised.copy(alpha = .96f),
                                TvDesign.Surface.copy(alpha = .95f),
                                TvDesign.Black.copy(alpha = .98f),
                            )
                        ),
                        shape = SidebarPanelShape,
                    )
                    .border(
                        width = 1.dp,
                        color = TvDesign.White.copy(alpha = .12f),
                        shape = SidebarPanelShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TvSidebarItem(
                        label = "Profile",
                        icon = Icons.Default.Person,
                        selected = false,
                        labelAlpha = labelAlpha,
                        requester = profileRequester,
                        onFocused = onFocused,
                        onClick = onProfile,
                        onUp = { true },
                        onDown = {
                            runCatching { navRequesters.getValue("Home").requestFocus() }
                            true
                        },
                        onRight = onReturnToContent,
                        modifier = Modifier.fillMaxWidth(.94f),
                    )

                    Spacer(Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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
                                    if (index == 0) {
                                        runCatching { profileRequester.requestFocus() }
                                    } else {
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
                                    }
                                    true
                                },
                                onRight = onReturnToContent,
                                modifier = Modifier.fillMaxWidth(.94f),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TvCollapsedIconRail(
    selected: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(64.dp)
            .padding(start = 10.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPrimaryDestinations.forEach { label ->
            val isSelected = label == selected
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .height(22.dp)
                            .background(TvDesign.Accent.copy(alpha = .92f), RoundedCornerShape(999.dp))
                    )
                }
                Icon(
                    imageVector = destinationIcon(label),
                    contentDescription = label,
                    tint = if (isSelected) {
                        TvDesign.White
                    } else {
                        TvDesign.White.copy(alpha = .70f)
                    },
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

private val SidebarPanelShape = RoundedCornerShape(28.dp)
private val SidebarItemShape = RoundedCornerShape(999.dp)

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
    modifier: Modifier = Modifier,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(120),
        label = "tvSidebarIconScale",
    )

    val fillAlpha = when {
        focused -> .18f
        selected -> .10f
        else -> 0f
    }
    val edgeAlpha = if (focused) .34f else 0f
    val contentColor = if (focused || selected) TvDesign.White else TvDesign.White.copy(alpha = .78f)

    Row(
        modifier = modifier
            .height(52.dp)
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
            .background(TvDesign.White.copy(alpha = fillAlpha), SidebarItemShape)
            .border(1.dp, TvDesign.White.copy(alpha = edgeAlpha), SidebarItemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (selected || focused) TvDesign.White.copy(alpha = .10f)
                    else TvDesign.SurfaceRaised.copy(alpha = .86f),
                    CircleShape,
                )
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = contentColor.copy(alpha = labelAlpha),
            fontSize = 15.sp,
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
