package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.storage.ProfileStore
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
    val profileTop: Dp,
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
        profileTop = 28.dp,
    )
    TvSidebarStyle.FLOATING_GLASS -> SidebarMetrics(
        collapsedWidth = 88.dp,
        expandedWidth = 252.dp,
        collapsedItemWidth = 68.dp,
        expandedItemWidth = 220.dp,
        iconColumnWidth = 52.dp,
        iconSize = 23.dp,
        itemHeight = 44.dp,
        itemSpacing = 7.dp,
        expandedStartPadding = 6.dp,
        profileTop = 20.dp,
    )
    TvSidebarStyle.MINIMAL_EDGE -> SidebarMetrics(
        collapsedWidth = 58.dp,
        expandedWidth = 220.dp,
        collapsedItemWidth = 58.dp,
        expandedItemWidth = 196.dp,
        iconColumnWidth = 48.dp,
        iconSize = 22.dp,
        itemHeight = 43.dp,
        itemSpacing = 5.dp,
        expandedStartPadding = 6.dp,
        profileTop = 22.dp,
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
    val context = LocalContext.current
    val profileStore = remember(context.applicationContext) { ProfileStore(context.applicationContext) }
    val activeProfile = remember(profileStore) { profileStore.activeProfile() }
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
        targetValue = if (expanded) 1f else 0f,
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

    val panelBrush = sidebarPanelBrush(sidebarStyle, expanded, panelAlpha)
    val containerModifier = when (sidebarStyle) {
        TvSidebarStyle.CLASSIC -> modifier
            .fillMaxHeight()
            .width(width)
            .clipToBounds()
            .background(panelBrush)

        TvSidebarStyle.FLOATING_GLASS -> {
            val shape = RoundedCornerShape(24.dp)
            modifier
                .fillMaxHeight()
                .width(width)
                .padding(start = 12.dp, end = 8.dp, top = 18.dp, bottom = 18.dp)
                .clip(shape)
                .background(panelBrush)
                .border(1.dp, TvDesign.White.copy(alpha = .10f), shape)
                .clipToBounds()
        }

        TvSidebarStyle.MINIMAL_EDGE -> if (expanded) {
            val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 0.dp)
            modifier
                .fillMaxHeight()
                .width(width)
                .padding(top = 14.dp, bottom = 14.dp)
                .clip(shape)
                .background(panelBrush)
                .border(1.dp, TvDesign.White.copy(alpha = .08f), shape)
                .clipToBounds()
        } else {
            modifier
                .fillMaxHeight()
                .width(width)
                .clipToBounds()
                .background(panelBrush)
        }
    }

    Box(modifier = containerModifier) {
        SidebarProfileItem(
            profileName = activeProfile.name,
            avatarId = activeProfile.avatar,
            style = sidebarStyle,
            metrics = metrics,
            expanded = expanded,
            labelAlpha = labelAlpha,
            requester = profileRequester,
            onFocused = onFocused,
            onClick = onProfile,
            onLeft = { true },
            onRight = onReturnToContent,
            onUp = { true },
            onDown = { request(navRequesters.getValue(TvPrimaryDestinations.first())) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = metrics.profileTop),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = if (sidebarStyle == TvSidebarStyle.CLASSIC) 10.dp else 6.dp)
                .fillMaxWidth(),
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
                            request(profileRequester)
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

    TvSidebarStyle.FLOATING_GLASS -> Brush.verticalGradient(
        listOf(
            TvDesign.Surface.copy(alpha = if (expanded) .96f else .91f),
            TvDesign.Black.copy(alpha = if (expanded) .94f else .88f),
        )
    )

    TvSidebarStyle.MINIMAL_EDGE -> if (expanded) {
        Brush.horizontalGradient(
            0f to TvDesign.Black.copy(alpha = .97f),
            .78f to TvDesign.Black.copy(alpha = .91f),
            1f to TvDesign.Black.copy(alpha = .68f),
        )
    } else {
        Brush.horizontalGradient(
            0f to TvDesign.Black.copy(alpha = .58f),
            .70f to TvDesign.Black.copy(alpha = .22f),
            1f to Color.Transparent,
        )
    }
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
    val itemShape = RoundedCornerShape(if (style == TvSidebarStyle.MINIMAL_EDGE) 12.dp else 14.dp)

    Row(
        modifier = Modifier
            .padding(start = if (expanded) metrics.expandedStartPadding else 0.dp)
            .width(if (expanded) metrics.expandedItemWidth else metrics.collapsedItemWidth)
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
            modifier = Modifier.width(if (expanded) metrics.iconColumnWidth else metrics.collapsedItemWidth),
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

        Text(
            text = label,
            color = when {
                expanded && selected -> TvDesign.Black
                focused -> TvDesign.White
                else -> TvDesign.White.copy(alpha = .68f)
            },
            fontSize = if (style == TvSidebarStyle.CLASSIC) 15.sp else 14.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp, end = if (style == TvSidebarStyle.CLASSIC) 20.dp else 18.dp)
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}

private fun sidebarItemBrush(
    style: TvSidebarStyle,
    expanded: Boolean,
    selected: Boolean,
    focused: Boolean,
): Brush {
    if (!expanded) return Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    return when {
        selected && style == TvSidebarStyle.CLASSIC -> Brush.horizontalGradient(
            0f to TvDesign.White,
            .52f to TvDesign.White.copy(alpha = .92f),
            .82f to TvDesign.White.copy(alpha = .46f),
            1f to Color.Transparent,
        )
        selected -> Brush.horizontalGradient(
            0f to TvDesign.White.copy(alpha = .98f),
            .72f to TvDesign.White.copy(alpha = .88f),
            1f to TvDesign.White.copy(alpha = .60f),
        )
        focused -> Brush.horizontalGradient(
            0f to TvDesign.White.copy(alpha = .18f),
            .66f to TvDesign.White.copy(alpha = .10f),
            1f to Color.Transparent,
        )
        else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
}

@Composable
private fun SidebarProfileItem(
    profileName: String,
    avatarId: String,
    style: TvSidebarStyle,
    metrics: SidebarMetrics,
    expanded: Boolean,
    labelAlpha: Float,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember(profileName, avatarId) { mutableStateOf(false) }
    val avatarDrawable = ProfileAvatarCatalog.drawableRes(avatarId)
    val avatarScale by animateFloatAsState(
        targetValue = if (focused) 1.10f else 1f,
        animationSpec = tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        ),
        label = "vueoSidebarProfileScale",
    )
    val focusBrush = if (expanded && focused) {
        Brush.horizontalGradient(
            0f to TvDesign.White.copy(alpha = .18f),
            .58f to TvDesign.White.copy(alpha = .11f),
            1f to Color.Transparent,
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val itemShape = RoundedCornerShape(if (style == TvSidebarStyle.MINIMAL_EDGE) 12.dp else 14.dp)

    Row(
        modifier = modifier
            .padding(start = if (expanded) metrics.expandedStartPadding else 0.dp)
            .width(if (expanded) metrics.expandedItemWidth else metrics.collapsedItemWidth)
            .height(metrics.itemHeight)
            .clip(itemShape)
            .background(focusBrush)
            .focusRequester(requester)
            .focusProperties { canFocus = expanded }
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
        if (style == TvSidebarStyle.CLASSIC) {
            Spacer(Modifier.width(10.dp))
            SidebarAvatar(
                profileName = profileName,
                avatarDrawable = avatarDrawable,
                avatarScale = avatarScale,
                modifier = Modifier.width(57.dp),
            )
        } else {
            SidebarAvatar(
                profileName = profileName,
                avatarDrawable = avatarDrawable,
                avatarScale = avatarScale,
                modifier = Modifier.width(if (expanded) metrics.iconColumnWidth else metrics.collapsedItemWidth),
            )
        }

        Text(
            text = profileName,
            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .78f),
            fontSize = if (style == TvSidebarStyle.CLASSIC) 15.sp else 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(
                    start = if (style == TvSidebarStyle.CLASSIC) 4.dp else 6.dp,
                    end = if (style == TvSidebarStyle.CLASSIC) 20.dp else 18.dp,
                )
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}

@Composable
private fun SidebarAvatar(
    profileName: String,
    avatarDrawable: Int?,
    avatarScale: Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (avatarDrawable != null) {
            Image(
                painter = painterResource(avatarDrawable),
                contentDescription = profileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                    }
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(TvDesign.White.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profileName.trim().firstOrNull()?.uppercase() ?: "V",
                    color = TvDesign.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
