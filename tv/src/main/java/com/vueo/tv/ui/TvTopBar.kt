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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.motion.TvMotion

/** Root destinations stay VUEO-owned. Only their TV presentation is rebuilt. */
val TvPrimaryDestinations = listOf("Home", "Search", "Library", "Settings")

private data class SidebarMetrics(
    val collapsedWidth: Dp,
    val expandedWidth: Dp,
    val collapsedItemWidth: Dp,
    val expandedItemWidth: Dp,
    val iconColumnWidth: Dp,
    val iconSize: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp,
    val expandedStartPadding: Dp,
)

private fun sidebarMetrics(style: TvSidebarStyle): SidebarMetrics = when (style) {
    TvSidebarStyle.CLASSIC -> SidebarMetrics(
        collapsedWidth = 72.dp,
        expandedWidth = 238.dp,
        collapsedItemWidth = 72.dp,
        expandedItemWidth = 172.dp,
        iconColumnWidth = 47.dp,
        iconSize = 24.dp,
        itemHeight = 46.dp,
        itemSpacing = 6.dp,
        expandedStartPadding = 10.dp,
    )

    TvSidebarStyle.PILL_ICONS -> SidebarMetrics(
        collapsedWidth = 54.dp,
        expandedWidth = 54.dp,
        collapsedItemWidth = 42.dp,
        expandedItemWidth = 42.dp,
        iconColumnWidth = 42.dp,
        iconSize = 24.dp,
        itemHeight = 42.dp,
        itemSpacing = 4.dp,
        expandedStartPadding = 6.dp,
    )
}

/**
 * Shared TV root sidebar. The interaction/focus model is identical for every
 * style; only presentation changes.
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
    // Keep the existing route contract so no screen/navigation call site changes.
    @Suppress("UNUSED_VARIABLE")
    val unusedProfileRequester = profileRequester
    @Suppress("UNUSED_VARIABLE")
    val unusedProfileAction = onProfile

    val context = LocalContext.current
    val sidebarStyle = TvSidebarStyleState.value ?: TvSidebarPreferences.style(context)
    val metrics = sidebarMetrics(sidebarStyle)

    val width by animateDpAsState(
        targetValue = if (expanded) metrics.expandedWidth else metrics.collapsedWidth,
        animationSpec = tween(
            durationMillis = if (expanded) 180 else 130,
            easing = if (expanded) TvMotion.EaseOut else TvMotion.EaseInOut,
        ),
        label = "vueoSidebarWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (sidebarStyle == TvSidebarStyle.CLASSIC && expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 155 else 90,
            delayMillis = if (expanded) 25 else 0,
            easing = if (expanded) TvMotion.EaseOut else TvMotion.EaseInOut,
        ),
        label = "vueoSidebarLabelAlpha",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (expanded) .98f else .86f,
        animationSpec = tween(
            durationMillis = TvMotion.ELEMENT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "vueoSidebarPanelAlpha",
    )
    val pillOffsetX by animateDpAsState(
        targetValue = if (expanded) 10.dp else (-64).dp,
        animationSpec = tween(
            durationMillis = if (expanded) 180 else 145,
            easing = if (expanded) TvMotion.EaseOut else TvMotion.EaseInOut,
        ),
        label = "vueoSidebarPillOffset",
    )

    val panelBrush = sidebarPanelBrush(sidebarStyle, expanded, panelAlpha)
    val containerModifier = when (sidebarStyle) {
        TvSidebarStyle.CLASSIC -> modifier
            .fillMaxHeight()
            .width(width)
            .clipToBounds()
            .background(panelBrush)

        TvSidebarStyle.PILL_ICONS -> {
            val shape = RoundedCornerShape(28.dp)
            modifier
                .offset(x = pillOffsetX)
                .width(width)
                .clip(shape)
                .background(panelBrush)
                .border(1.dp, TvDesign.White.copy(alpha = .11f), shape)
                .clipToBounds()
        }
    }

    Box(modifier = containerModifier) {
        val navColumnModifier = when (sidebarStyle) {
            TvSidebarStyle.CLASSIC -> Modifier
                .align(Alignment.CenterStart)
                .offset(y = 10.dp)
                .fillMaxWidth()

            TvSidebarStyle.PILL_ICONS -> Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        }

        Column(
            modifier = navColumnModifier,
            verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        ) {
            TvPrimaryDestinations.forEachIndexed { index, label ->
                SidebarNavigationItem(
                    label = label,
                    icon = destinationIcon(label),
                    selected = selected == label,
                    style = sidebarStyle,
                    metrics = metrics,
                    expanded = expanded,
                    labelAlpha = labelAlpha,
                    requester = navRequesters.getValue(label),
                    onFocused = onFocused,
                    onClick = { onNavigate(label) },
                    onLeft = { true },
                    onRight = onReturnToContent,
                    onUp = {
                        if (index > 0) {
                            request(navRequesters.getValue(TvPrimaryDestinations[index - 1]))
                        } else {
                            true
                        }
                    },
                    onDown = {
                        if (index < TvPrimaryDestinations.lastIndex) {
                            request(navRequesters.getValue(TvPrimaryDestinations[index + 1]))
                        } else {
                            true
                        }
                    },
                )
            }
        }
    }
}

private fun sidebarPanelBrush(
    style: TvSidebarStyle,
    expanded: Boolean,
    panelAlpha: Float,
): Brush = when (style) {
    TvSidebarStyle.CLASSIC -> if (expanded) {
        Brush.horizontalGradient(
            0f to TvDesign.Black.copy(alpha = panelAlpha),
            .72f to TvDesign.Black.copy(alpha = panelAlpha * .97f),
            1f to Color.Transparent,
        )
    } else {
        Brush.horizontalGradient(listOf(TvDesign.Black, TvDesign.Black))
    }

    TvSidebarStyle.PILL_ICONS -> Brush.verticalGradient(
        listOf(
            TvDesign.SurfaceRaised.copy(alpha = .94f),
            TvDesign.Black.copy(alpha = .92f),
        )
    )
}

@Composable
private fun SidebarNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    style: TvSidebarStyle,
    metrics: SidebarMetrics,
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
        targetValue = when {
            focused -> 1.10f
            selected && !expanded -> 1.07f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "vueoSidebarIconScale:$label",
    )
    val itemBrush = sidebarItemBrush(style, expanded, selected, focused)
    val itemShape = if (style == TvSidebarStyle.PILL_ICONS) CircleShape else RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .padding(
                start = when {
                    style == TvSidebarStyle.PILL_ICONS -> metrics.expandedStartPadding
                    expanded -> metrics.expandedStartPadding
                    else -> 0.dp
                },
            )
            .width(
                if (style == TvSidebarStyle.PILL_ICONS) {
                    metrics.expandedItemWidth
                } else if (expanded) {
                    metrics.expandedItemWidth
                } else {
                    metrics.collapsedItemWidth
                },
            )
            .height(metrics.itemHeight)
            .clip(itemShape)
            .background(itemBrush)
            .focusRequester(requester)
            .focusProperties { canFocus = expanded || canFocusWhenCollapsed }
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
            modifier = Modifier.width(
                if (style == TvSidebarStyle.PILL_ICONS) {
                    metrics.expandedItemWidth
                } else if (expanded) {
                    metrics.iconColumnWidth
                } else {
                    metrics.collapsedItemWidth
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    expanded && selected -> TvDesign.Black
                    focused -> TvDesign.White
                    selected && !expanded -> TvDesign.White
                    else -> TvDesign.White.copy(alpha = .46f)
                },
                modifier = Modifier
                    .size(metrics.iconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }

        if (style == TvSidebarStyle.CLASSIC) {
            Text(
                text = label,
                color = when {
                    expanded && selected -> TvDesign.Black
                    focused -> TvDesign.White
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
}

private fun sidebarItemBrush(
    style: TvSidebarStyle,
    expanded: Boolean,
    selected: Boolean,
    focused: Boolean,
): Brush {
    if (!expanded) {
        return Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    return when {
        selected && style == TvSidebarStyle.CLASSIC -> Brush.horizontalGradient(
            0f to TvDesign.White,
            .52f to TvDesign.White.copy(alpha = .92f),
            .82f to TvDesign.White.copy(alpha = .46f),
            1f to Color.Transparent,
        )
        selected && style == TvSidebarStyle.PILL_ICONS -> Brush.horizontalGradient(
            listOf(
                TvDesign.White.copy(alpha = .98f),
                TvDesign.White.copy(alpha = .90f),
            )
        )
        focused && style == TvSidebarStyle.PILL_ICONS -> Brush.horizontalGradient(
            listOf(
                TvDesign.White.copy(alpha = .17f),
                TvDesign.White.copy(alpha = .11f),
            )
        )
        focused -> Brush.horizontalGradient(
            0f to TvDesign.White.copy(alpha = .18f),
            .66f to TvDesign.White.copy(alpha = .10f),
            1f to Color.Transparent,
        )
        else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
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
