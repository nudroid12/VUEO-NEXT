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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import kotlinx.coroutines.delay

private val PanelBlack = Color(0xFF050706)
private val PanelSurface = Color(0xFF101412)
private val PanelRaised = Color(0xFF151A17)
private val PanelMuted = Color(0xFFAAB2AD)

private data class PanelSetting(
    val key: String,
    val symbol: String,
    val title: String,
    val subtitle: String,
    val contentManager: Boolean = false,
)

private val panelSettings =
    listOf(
        PanelSetting("PERSONALIZATION", "✦", "Personalization", "DNA & recommendations"),
        PanelSetting("CONTENT_MANAGER", "▦", "Content Manager", "Addons & providers", contentManager = true),
        PanelSetting("ENHANCEMENTS", "✧", "Enhancements", "Metadata & services"),
        PanelSetting("PLAYBACK", "▶", "Playback", "Player preferences"),
        PanelSetting("SUBTITLES", "CC", "Subtitles", "Language & display"),
        PanelSetting("SOURCES", "↗", "Sources", "Source preferences"),
        PanelSetting("APPEARANCE", "◐", "Appearance", "Interface options"),
        PanelSetting("DATA_STORAGE", "▤", "Data & Storage", "Backup & app data"),
        PanelSetting("UPDATES", "↻", "Updates", "Version & updates"),
        PanelSetting("ABOUT", "ⓘ", "About VUEO", "App & privacy"),
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
    val context = LocalContext.current
    val activeProfile = remember(visible) { profileStore.activeProfile() }
    val tvLibrary = remember(context) { TvLibraryStore(context.applicationContext) }
    val dnaPreferences =
        remember(context) {
            UserDnaPreferences(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }
    val dnaEnabled = remember(visible, activeProfile.id) { dnaPreferences.userDnaEnabled(activeProfile.id) }
    val dnaSnapshot =
        remember(visible, activeProfile.id) {
            runCatching {
                UserDnaEngine(
                    LibraryStore(
                        context = context.applicationContext,
                        prefsName = TvLibraryStore.PREFS_NAME,
                        watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
                        profileStore = profileStore,
                    )
                ).build()
            }.getOrNull()
        }
    val myListCount = remember(visible, activeProfile.id) { tvLibrary.items().size }
    val watchedCount = remember(visible, activeProfile.id) { tvLibrary.history().size }
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
                        .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )

                TvSettingsProfileCard(
                    profileName = activeProfile.name,
                    isKids = activeProfile.isKids,
                    myListCount = myListCount,
                    watchedCount = watchedCount,
                    dnaPercent = if (dnaEnabled) dnaSnapshot?.confidencePercent else null,
                    tastePreview =
                        dnaSnapshot
                            ?.topGenres
                            ?.take(2)
                            ?.joinToString(" • ") { it.name }
                            .orEmpty(),
                    profileRequester = profileRequester,
                    switchRequester = switchRequester,
                    firstSettingRequester = settingRequesters.first(),
                    onExitLeft = onDismiss,
                    onOpenPersonalization = { onOpenSettings("PERSONALIZATION") },
                    onSwitchProfile = onSwitchProfile,
                )

                panelSettings.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    myListCount: Int,
    watchedCount: Int,
    dnaPercent: Int?,
    tastePreview: String,
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
        targetValue = if (profileFocused) 1.018f else 1f,
        label = "settingsProfileScale",
    )
    val switchScale by animateFloatAsState(
        targetValue = if (switchFocused) 1.025f else 1f,
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
                .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
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
                    .padding(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(50.dp)
                        .height(50.dp)
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
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = if (isKids) "Kids profile" else "Active profile",
                    color = PanelMuted,
                    fontSize = 11.sp,
                )
                if (tastePreview.isNotBlank()) {
                    Text(
                        text = tastePreview,
                        color = PanelMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "›",
                color = PanelMuted,
                fontSize = 22.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactProfileStat("My List", myListCount.toString(), Modifier.weight(1f))
            CompactProfileStat("Watched", watchedCount.toString(), Modifier.weight(1f))
            CompactProfileStat("DNA", dnaPercent?.let { "$it%" } ?: "Off", Modifier.weight(1f))
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
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
                        RoundedCornerShape(9.dp),
                    )
                    .border(
                        width = if (switchFocused) 2.dp else 1.dp,
                        color = if (switchFocused) Color.White else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(9.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Switch Profiles",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(34.dp)
                .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = PanelMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
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
        targetValue = if (focused) 1.035f else 1f,
        label = "settingsPanelCardScale",
    )

    Row(
        modifier =
            modifier
                .height(88.dp)
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
                    RoundedCornerShape(11.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(34.dp)
                    .height(34.dp)
                    .background(Color.White.copy(alpha = if (focused) 0.14f else 0.07f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = setting.symbol,
                color = Color.White,
                fontSize = if (setting.symbol == "CC") 10.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = setting.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = setting.subtitle,
                color = PanelMuted,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
