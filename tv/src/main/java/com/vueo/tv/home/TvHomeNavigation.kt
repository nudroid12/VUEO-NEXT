package com.vueo.tv.home

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign

internal data class VueoHomeDestination(
    val label: String,
    val icon: ImageVector,
)

internal val VueoHomeDestinations = listOf(
    VueoHomeDestination("Home", Icons.Default.Home),
    VueoHomeDestination("Search", Icons.Default.Search),
    VueoHomeDestination("Library", Icons.Default.VideoLibrary),
    VueoHomeDestination("Settings", Icons.Default.Settings),
)

private val ExpandedSidebarShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp)
private val SidebarItemShape = RoundedCornerShape(10.dp)

/**
 * Home-only navigation shell for the fresh Home presentation.
 *
 * One set of primary focus targets exists in both collapsed and expanded
 * states. Collapsed state is a quiet icon rail. Focusing the rail expands the
 * same targets into a labelled panel. No hidden duplicate focus tree exists.
 */
@Composable
internal fun VueoHomeSidebar(
    expanded: Boolean,
    selected: String,
    requesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    onExpanded: () -> Unit,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReturnToContent: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 214.dp else 62.dp,
        animationSpec = tween(170),
        label = "homeSidebarWidth",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (expanded) .96f else 0f,
        animationSpec = tween(130),
        label = "homeSidebarPanelAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    color = TvDesign.Black.copy(alpha = panelAlpha),
                    shape = ExpandedSidebarShape,
                )
                .border(
                    width = if (expanded) 1.dp else 0.dp,
                    color = TvDesign.White.copy(alpha = .08f * panelAlpha),
                    shape = ExpandedSidebarShape,
                ),
        )

        if (expanded) {
            Text(
                text = "VUEO",
                color = TvDesign.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.3.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp, top = 28.dp),
            )
        }

        Box(
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                VueoHomeDestinations.forEachIndexed { index, destination ->
                    VueoHomeSidebarItem(
                        destination = destination,
                        selected = destination.label == selected,
                        expanded = expanded,
                        requester = requesters.getValue(destination.label),
                        onFocused = onExpanded,
                        onClick = {
                            if (destination.label == selected) {
                                onReturnToContent()
                            } else {
                                onNavigate(destination.label)
                            }
                        },
                        onRight = onReturnToContent,
                        onUp = {
                            if (index > 0) {
                                runCatching {
                                    requesters.getValue(VueoHomeDestinations[index - 1].label).requestFocus()
                                }
                            }
                            true
                        },
                        onDown = {
                            if (index < VueoHomeDestinations.lastIndex) {
                                runCatching {
                                    requesters.getValue(VueoHomeDestinations[index + 1].label).requestFocus()
                                }
                            } else if (expanded) {
                                runCatching { profileRequester.requestFocus() }
                            }
                            true
                        },
                    )
                }
            }
        }

        if (expanded) {
            VueoHomeSidebarItem(
                destination = VueoHomeDestination("Profile", Icons.Default.Person),
                selected = false,
                expanded = true,
                requester = profileRequester,
                onFocused = onExpanded,
                onClick = onProfile,
                onRight = onReturnToContent,
                onUp = {
                    runCatching { requesters.getValue("Settings").requestFocus() }
                    true
                },
                onDown = { true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 26.dp),
            )
        }
    }
}

@Composable
private fun VueoHomeSidebarItem(
    destination: VueoHomeDestination,
    selected: Boolean,
    expanded: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember(destination.label) { mutableStateOf(false) }
    val itemWidth = if (expanded) 190.dp else 46.dp
    val iconTint = when {
        focused -> TvDesign.White
        selected -> TvDesign.Accent.copy(alpha = .92f)
        else -> TvDesign.White.copy(alpha = .72f)
    }

    Row(
        modifier = modifier
            .width(itemWidth)
            .height(44.dp)
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .11f) else Color.Transparent,
                shape = SidebarItemShape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .16f) else Color.Transparent,
                shape = SidebarItemShape,
            )
            .focusRequester(requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                    KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        if (expanded) {
            Text(
                text = destination.label,
                color = if (focused || selected) TvDesign.White else TvDesign.White.copy(alpha = .72f),
                fontSize = 14.sp,
                fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}
