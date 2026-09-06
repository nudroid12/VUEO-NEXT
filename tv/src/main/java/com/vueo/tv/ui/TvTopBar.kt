package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.focus.focusProperties
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
 * 29C.4 global TV sidebar shell.
 *
 * Interaction and composition are intentionally referenced from the Nuvio TV
 * source supplied with the project: a quiet floating route pill while content
 * owns focus, then a rounded overlay panel with profile at the top and the
 * primary destinations vertically centered. VUEO keeps its own routes,
 * palette, focus contract and one-OK activation rule.
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
    val panelWidth by animateDpAsState(
        targetValue = if (expanded) 262.dp else 184.dp,
        animationSpec = if (expanded) {
            keyframes {
                durationMillis = 365
                274.dp at 175
            }
        } else {
            tween(durationMillis = 385, easing = LinearOutSlowInEasing)
        },
        label = "sidebarPanelWidth",
    )
    val panelProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 345 else 385,
            easing = if (expanded) FastOutSlowInEasing else LinearOutSlowInEasing,
        ),
        label = "sidebarPanelProgress",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 125 else 145,
            easing = if (expanded) FastOutSlowInEasing else LinearOutSlowInEasing,
        ),
        label = "sidebarLabelAlpha",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (expanded) 1f else .92f,
        animationSpec = tween(145, easing = FastOutSlowInEasing),
        label = "sidebarIconScale",
    )

    var pillIconOnly by remember(selected) { mutableStateOf(false) }
    LaunchedEffect(expanded, selected) {
        if (expanded) {
            pillIconOnly = false
            // The expanded panel is intentionally not kept alive at rest anymore.
            // Give Compose one frame to materialize its focus targets, then enter
            // the current destination deterministically.
            delay(16L)
            runCatching { navRequesters.getValue(selected).requestFocus() }
        } else {
            pillIconOnly = false
            if (selected != "Settings") {
                delay(3200L)
                pillIconOnly = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(286.dp),
    ) {
        // Do not leave an invisible focusable sidebar sitting over Home.
        // Keep it only while open or while its close animation is still visible.
        if (expanded || panelProgress > .01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .padding(start = 18.dp, top = 28.dp, end = 8.dp, bottom = 20.dp)
                    .offset(
                        x = (-8f * (1f - panelProgress)).dp,
                        y = (-4f * (1f - panelProgress)).dp,
                    )
                    .graphicsLayer {
                        alpha = panelProgress
                        val scale = .92f + (.08f * panelProgress)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, .5f)
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                TvDesign.SurfaceRaised.copy(alpha = .965f),
                                TvDesign.Surface.copy(alpha = .945f),
                                TvDesign.Black.copy(alpha = .975f),
                            )
                        ),
                        shape = SidebarPanelShape,
                    )
                    .border(
                        width = 1.dp,
                        color = TvDesign.White.copy(alpha = .13f),
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
                    iconScale = iconScale,
                    requester = profileRequester,
                    onFocused = onFocused,
                    onClick = onProfile,
                    onUp = { true },
                    onDown = {
                        runCatching { navRequesters.getValue("Home").requestFocus() }
                        true
                    },
                    onRight = onReturnToContent,
                    profile = true,
                    modifier = Modifier.fillMaxWidth(.92f),
                )

                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-12).dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TvPrimaryDestinations.forEachIndexed { index, label ->
                        TvSidebarItem(
                            label = label,
                            icon = destinationIcon(label),
                            selected = selected == label,
                            labelAlpha = labelAlpha,
                            iconScale = iconScale,
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
                            modifier = Modifier.fillMaxWidth(.92f),
                        )
                    }
                }

                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!expanded && selected != "Search") {
            TvCollapsedRoutePill(
                label = selected,
                icon = destinationIcon(selected),
                iconOnly = pillIconOnly,
                progress = 1f - panelProgress,
                onExpand = {
                    onFocused()
                    runCatching { navRequesters.getValue(selected).requestFocus() }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp),
            )
        }
    }
}

private val SidebarPanelShape = RoundedCornerShape(30.dp)
private val SidebarItemShape = RoundedCornerShape(999.dp)

@Composable
private fun TvSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    labelAlpha: Float,
    iconScale: Float,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    onRight: () -> Boolean,
    modifier: Modifier = Modifier,
    profile: Boolean = false,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val fillAlpha = when {
        selected -> .13f
        focused -> .18f
        else -> 0f
    }
    val edgeAlpha = if (focused) .38f else 0f
    val contentColor = when {
        focused || selected -> TvDesign.White
        else -> TvDesign.White.copy(alpha = .82f)
    }
    val iconWell = when {
        selected -> TvDesign.White.copy(alpha = .13f)
        focused -> TvDesign.White.copy(alpha = .11f)
        else -> TvDesign.SurfaceRaised.copy(alpha = .90f)
    }

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
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(iconWell, CircleShape)
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
                modifier = Modifier.size(if (profile) 18.dp else 20.dp),
            )
        }

        Spacer(Modifier.width(if (profile) 16.dp else 14.dp))
        Text(
            text = label,
            color = contentColor.copy(alpha = labelAlpha),
            fontSize = 15.sp,
            fontWeight = if (profile || focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun TvCollapsedRoutePill(
    label: String,
    icon: ImageVector,
    iconOnly: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (iconOnly) 46.dp else 154.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarPillWidth",
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                val scale = .90f + (.10f * progress)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .height(46.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            TvDesign.SurfaceRaised.copy(alpha = .95f),
                            TvDesign.Surface.copy(alpha = .92f),
                        )
                    ),
                    shape = SidebarItemShape,
                )
                .border(
                    width = 1.dp,
                    color = TvDesign.White.copy(alpha = .14f),
                    shape = SidebarItemShape,
                )
                .clickable(onClick = onExpand)
                .focusProperties { canFocus = false }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(TvDesign.SurfaceRaised.copy(alpha = .92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TvDesign.White.copy(alpha = .92f),
                    modifier = Modifier.size(20.dp),
                )
            }

            if (!iconOnly) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = label,
                    color = TvDesign.White.copy(alpha = .92f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
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

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
