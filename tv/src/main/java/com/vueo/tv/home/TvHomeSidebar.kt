package com.vueo.tv.home

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import com.vueo.tv.ui.TvDesign

private data class HomeNavItem(
    val label: String,
    val icon: ImageVector,
)

private val HomeNavItems = listOf(
    HomeNavItem("Home", Icons.Default.Home),
    HomeNavItem("Search", Icons.Default.Search),
    HomeNavItem("Library", Icons.Default.VideoLibrary),
    HomeNavItem("Settings", Icons.Default.Settings),
)

private val RailItemShape = RoundedCornerShape(13.dp)

@Composable
internal fun TvHomeSidebar(
    selected: String,
    navRequesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReturnToContent: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(
                Brush.horizontalGradient(
                    0f to TvDesign.Black.copy(alpha = .94f),
                    .58f to TvDesign.Black.copy(alpha = .58f),
                    1f to Color.Transparent,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeNavItems.forEachIndexed { index, item ->
                HomeRailButton(
                    icon = item.icon,
                    label = item.label,
                    selected = selected == item.label,
                    requester = navRequesters.getValue(item.label),
                    onClick = { onNavigate(item.label) },
                    onRight = onReturnToContent,
                    onUp = {
                        if (index > 0) {
                            runCatching {
                                navRequesters.getValue(HomeNavItems[index - 1].label).requestFocus()
                            }
                        }
                        true
                    },
                    onDown = {
                        if (index < HomeNavItems.lastIndex) {
                            runCatching {
                                navRequesters.getValue(HomeNavItems[index + 1].label).requestFocus()
                            }
                        } else {
                            runCatching { profileRequester.requestFocus() }
                        }
                        true
                    },
                )
            }
        }

        HomeRailButton(
            icon = Icons.Default.Person,
            label = "Profile",
            selected = false,
            requester = profileRequester,
            onClick = onProfile,
            onRight = onReturnToContent,
            onUp = {
                runCatching {
                    navRequesters.getValue(HomeNavItems.last().label).requestFocus()
                }
                true
            },
            onDown = { true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun HomeRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember(label) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(46.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> true
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    event.type == KeyEventType.KeyUp && (
                        code == KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == KeyEvent.KEYCODE_ENTER ||
                            code == KeyEvent.KEYCODE_NUMPAD_ENTER
                    ) -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    focused -> TvDesign.White.copy(alpha = .16f)
                    selected -> TvDesign.White.copy(alpha = .08f)
                    else -> Color.Transparent
                },
                shape = RailItemShape,
            )
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                focused -> TvDesign.White
                selected -> TvDesign.Accent
                else -> TvDesign.White.copy(alpha = .72f)
            },
            modifier = Modifier.size(23.dp),
        )
    }
}
