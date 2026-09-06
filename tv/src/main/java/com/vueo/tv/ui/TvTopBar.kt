package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Root destinations stay VUEO-owned. Only their TV presentation is rebuilt. */
val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

private val SidebarCollapsedWidth = 72.dp
private val SidebarExpandedWidth = 238.dp
private val SidebarItemHeight = 50.dp
private val SidebarIconSize = 24.dp
private val SidebarIndicatorWidth = 3.dp

/**
 * 33A root sidebar.
 *
 * Rebuilt from a blank presentation using the interaction model of Nuvio's
 * modern TV navigation: content -> current destination -> expanded drawer ->
 * content, while preserving VUEO routes and screen-owned focus restoration.
 *
 * The collapsed state is a clean icon rail. There is no floating pill, no
 * rounded drawer capsule and no per-item card/pill background.
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
        targetValue = if (expanded) SidebarExpandedWidth else SidebarCollapsedWidth,
        animationSpec = tween(durationMillis = if (expanded) 190 else 145),
        label = "vueoSidebarWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 155 else 90),
        label = "vueoSidebarLabelAlpha",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (expanded) .98f else .86f,
        animationSpec = tween(durationMillis = 150),
        label = "vueoSidebarPanelAlpha",
    )

    val panelBrush = Brush.horizontalGradient(
        0f to TvDesign.Black.copy(alpha = panelAlpha),
        .72f to TvDesign.Black.copy(alpha = if (expanded) panelAlpha * .97f else panelAlpha * .82f),
        1f to Color.Transparent,
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .clipToBounds()
            .background(panelBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 24.dp),
        ) {
            SidebarBrand(
                expanded = expanded,
                labelAlpha = labelAlpha,
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TvPrimaryDestinations.forEachIndexed { index, label ->
                    SidebarNavigationItem(
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
                                index > 0 -> request(navRequesters.getValue(TvPrimaryDestinations[index - 1]))
                                else -> true
                            }
                        },
                        onDown = {
                            if (index < TvPrimaryDestinations.lastIndex) {
                                request(navRequesters.getValue(TvPrimaryDestinations[index + 1]))
                            } else if (expanded) {
                                request(profileRequester)
                            } else {
                                true
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            SidebarNavigationItem(
                label = "Profile",
                icon = Icons.Default.Person,
                selected = false,
                expanded = expanded,
                labelAlpha = labelAlpha,
                requester = profileRequester,
                canFocusWhenCollapsed = false,
                onFocused = onFocused,
                onClick = onProfile,
                onLeft = { true },
                onRight = onReturnToContent,
                onUp = { request(navRequesters.getValue(TvPrimaryDestinations.last())) },
                onDown = { true },
            )
        }
    }
}

@Composable
private fun SidebarBrand(
    expanded: Boolean,
    labelAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(TvDesign.Accent),
        )

        Text(
            text = "VUEO",
            color = TvDesign.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 14.dp)
                .graphicsLayer { alpha = if (expanded) labelAlpha else 0f },
        )
    }
}

@Composable
private fun SidebarNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    labelAlpha: Float,
    requester: FocusRequester,
    canFocusWhenCollapsed: Boolean = selected,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (focused) 1.10f else 1f,
        animationSpec = tween(durationMillis = if (focused) 120 else 90),
        label = "vueoSidebarIconScale:$label",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (focused || selected) 1f else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "vueoSidebarIndicator:$label",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SidebarItemHeight)
            .focusRequester(requester)
            .focusProperties {
                // While collapsed only the current destination participates in
                // spatial focus. This makes LEFT from content deterministic;
                // once it receives focus the drawer expands and every route is
                // enabled, mirroring the selected-route entry behaviour of
                // Nuvio's modern scaffold without a floating pill.
                canFocus = expanded || canFocusWhenCollapsed
            }
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> onLeft()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    KeyEvent.KEYCODE_BACK -> onRight()
                    else -> false
                }
            }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(SidebarIndicatorWidth)
                .height(24.dp)
                .graphicsLayer { alpha = indicatorAlpha }
                .background(TvDesign.Accent),
        )

        Box(
            modifier = Modifier.width(57.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    focused -> TvDesign.White
                    selected -> TvDesign.Accent
                    else -> TvDesign.White.copy(alpha = .70f)
                },
                modifier = Modifier
                    .size(SidebarIconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }

        Text(
            text = label,
            color = when {
                focused -> TvDesign.White
                selected -> TvDesign.White.copy(alpha = .94f)
                else -> TvDesign.White.copy(alpha = .68f)
            },
            fontSize = 15.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp, end = 20.dp)
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}

private fun request(requester: FocusRequester): Boolean =
    runCatching { requester.requestFocus() }.isSuccess

private fun destinationIcon(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "Search" -> Icons.Default.Search
    "Library" -> Icons.Default.VideoLibrary
    "Settings" -> Icons.Default.Settings
    else -> Icons.Default.Home
}
