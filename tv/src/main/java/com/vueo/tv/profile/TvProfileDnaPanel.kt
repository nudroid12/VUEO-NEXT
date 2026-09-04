package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.storage.ProfileStore
import kotlinx.coroutines.delay

private val PanelBlack = Color(0xFF050706)
private val PanelSurface = Color(0xFF101412)
private val PanelRaised = Color(0xFF151A17)
private val PanelMuted = Color(0xFFAAB2AD)

private data class PanelSetting(
    val key: String,
    val title: String,
    val subtitle: String,
    val contentManager: Boolean = false,
)

private val panelSettings =
    listOf(
        PanelSetting("PERSONALIZATION", "Personalization", "Profile & recommendations"),
        PanelSetting("CONTENT_MANAGER", "Content Manager", "Addons, repos & providers", contentManager = true),
        PanelSetting("ENHANCEMENTS", "Enhancements", "Metadata & external services"),
        PanelSetting("PLAYBACK", "Playback", "Player & streaming preferences"),
        PanelSetting("SUBTITLES", "Subtitles", "Language & display preferences"),
        PanelSetting("SOURCES", "Sources", "Source ranking & information"),
        PanelSetting("APPEARANCE", "Appearance", "Interface preferences"),
        PanelSetting("DATA_STORAGE", "Data & Storage", "Backup, history & app data"),
        PanelSetting("UPDATES", "Updates", "Version & update preferences"),
        PanelSetting("ABOUT", "About VUEO", "Privacy & app information"),
    )

@Composable
fun TvProfileDnaPanel(
    visible: Boolean,
    profileStore: ProfileStore,
    onDismiss: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenContentManager: () -> Unit,
) {
    val activeProfile = remember(visible) { profileStore.activeProfile() }
    val profileRequester = remember { FocusRequester() }
    val switchRequester = remember { FocusRequester() }
    val settingRequesters = remember { List(panelSettings.size) { FocusRequester() } }
    val scrollState = rememberScrollState()

    BackHandler(enabled = visible, onBack = onDismiss)

    LaunchedEffect(visible) {
        if (visible) {
            delay(110)
            runCatching { profileRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(70f),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f)),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(190),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(190),
            ),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.50f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(
                                        PanelSurface.copy(alpha = 0.995f),
                                        PanelBlack,
                                    )
                                ),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        )
                        .verticalScroll(scrollState)
                        .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )

                TvSettingsProfileCard(
                    profileName = activeProfile.name,
                    isKids = activeProfile.isKids,
                    profileRequester = profileRequester,
                    switchRequester = switchRequester,
                    firstSettingRequester = settingRequesters.first(),
                    onExitLeft = onDismiss,
                    onOpenPersonalization = { onOpenSettings("PERSONALIZATION") },
                    onSwitchProfile = onSwitchProfile,
                )

                panelSettings.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEachIndexed { columnIndex, setting ->
                            val index = rowIndex * 2 + columnIndex
                            SettingsPanelCard(
                                setting = setting,
                                requester = settingRequesters[index],
                                onMove = { key ->
                                    when (key) {
                                        Key.DirectionLeft -> {
                                            if (columnIndex == 0) {
                                                onDismiss()
                                                true
                                            } else {
                                                runCatching { settingRequesters[index - 1].requestFocus() }.isSuccess
                                            }
                                        }

                                        Key.DirectionRight -> {
                                            if (columnIndex < row.lastIndex) {
                                                runCatching { settingRequesters[index + 1].requestFocus() }.isSuccess
                                            } else {
                                                false
                                            }
                                        }

                                        Key.DirectionUp -> {
                                            if (rowIndex == 0) {
                                                runCatching { switchRequester.requestFocus() }.isSuccess
                                            } else {
                                                runCatching { settingRequesters[index - 2].requestFocus() }.isSuccess
                                            }
                                        }

                                        Key.DirectionDown -> {
                                            if (index + 2 < settingRequesters.size) {
                                                runCatching { settingRequesters[index + 2].requestFocus() }.isSuccess
                                            } else {
                                                false
                                            }
                                        }

                                        else -> false
                                    }
                                },
                                onClick = {
                                    if (setting.contentManager) {
                                        onOpenContentManager()
                                    } else {
                                        onOpenSettings(setting.key)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsProfileCard(
    profileName: String,
    isKids: Boolean,
    profileRequester: FocusRequester,
    switchRequester: FocusRequester,
    firstSettingRequester: FocusRequester,
    onExitLeft: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    var profileFocused by remember { mutableStateOf(false) }
    var switchFocused by remember { mutableStateOf(false) }
    val profileScale by animateFloatAsState(
        targetValue = if (profileFocused) 1.015f else 1f,
        label = "settingsProfileScale",
    )
    val switchScale by animateFloatAsState(
        targetValue = if (switchFocused) 1.02f else 1f,
        label = "settingsSwitchScale",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(profileScale)
                .background(PanelRaised, RoundedCornerShape(15.dp))
                .border(
                    width = if (profileFocused) 2.dp else 1.dp,
                    color = if (profileFocused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(15.dp),
                )
                .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(profileRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onExitLeft()
                                    true
                                }

                                Key.DirectionDown -> runCatching { switchRequester.requestFocus() }.isSuccess
                                else -> false
                            }
                        }
                    }
                    .onFocusChanged { profileFocused = it.isFocused }
                    .clickable(onClick = onOpenPersonalization)
                    .focusable()
                    .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isKids) "Kids profile" else "Active profile",
                    color = PanelMuted,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = "›",
                color = PanelMuted,
                fontSize = 24.sp,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .focusRequester(switchRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onExitLeft()
                                    true
                                }

                                Key.DirectionUp -> runCatching { profileRequester.requestFocus() }.isSuccess
                                Key.DirectionDown -> runCatching { firstSettingRequester.requestFocus() }.isSuccess
                                else -> false
                            }
                        }
                    }
                    .scale(switchScale)
                    .onFocusChanged { switchFocused = it.isFocused }
                    .clickable(onClick = onSwitchProfile)
                    .focusable()
                    .background(
                        if (switchFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f),
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        width = if (switchFocused) 2.dp else 1.dp,
                        color = if (switchFocused) Color.White else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Switch Profiles",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SettingsPanelCard(
    setting: PanelSetting,
    requester: FocusRequester,
    onMove: (Key) -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        label = "settingsPanelCardScale",
    )

    Column(
        modifier =
            modifier
                .height(72.dp)
                .focusRequester(requester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onMove(event.key) else false
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    if (focused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = setting.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = setting.subtitle,
            color = PanelMuted,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
