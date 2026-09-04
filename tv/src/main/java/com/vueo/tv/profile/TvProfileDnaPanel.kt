package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
private val PanelGreen = Color(0xFF84E100)

private data class PanelSetting(
    val key: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val contentManager: Boolean = false,
)

private val panelSettings =
    listOf(
        PanelSetting("PERSONALIZATION", "Personalization", "User DNA, DNA Match & recommendations.", "DNA"),
        PanelSetting("CONTENT_MANAGER", "Content Manager", "Addons, repos & providers.", "CM", contentManager = true),
        PanelSetting("ENHANCEMENTS", "Enhancements", "Metadata, ratings & external services.", "+"),
        PanelSetting("PLAYBACK", "Playback", "Player & streaming preferences.", "PLAY"),
        PanelSetting("SUBTITLES", "Subtitles", "Language & display preferences.", "CC"),
        PanelSetting("SOURCES", "Sources", "Source ranking & information.", "SRC"),
        PanelSetting("APPEARANCE", "Appearance", "Interface preferences.", "UI"),
        PanelSetting("DATA_STORAGE", "Data & Storage", "Backup, history, cache & app data.", "DATA"),
        PanelSetting("UPDATES", "Updates", "Version & update preferences.", "UP"),
        PanelSetting("ABOUT", "About VUEO", "Privacy, architecture & build information.", "i"),
    )

@Composable
fun TvProfileDnaPanel(
    profileStore: ProfileStore,
    onDismiss: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenContentManager: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile = remember { profileStore.activeProfile() }
    val tvLibrary = remember(context) { TvLibraryStore(context.applicationContext) }
    val dnaPreferences =
        remember(context) {
            UserDnaPreferences(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }
    val dnaEnabled = remember(activeProfile.id) { dnaPreferences.userDnaEnabled(activeProfile.id) }
    val dnaSnapshot =
        remember(activeProfile.id) {
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
    val myListCount = remember(activeProfile.id) { tvLibrary.items().size }
    val watchedCount = remember(activeProfile.id) { tvLibrary.history().size }
    val profileRequester = remember { FocusRequester() }
    val switchRequester = remember { FocusRequester() }
    val settingRequesters = remember { List(panelSettings.size) { FocusRequester() } }
    val scrollState = rememberScrollState()

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { profileRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(70f)
                .background(Color.Black.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(620.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                PanelSurface.copy(alpha = 0.99f),
                                PanelBlack,
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                    )
                    .verticalScroll(scrollState)
                    .padding(start = 28.dp, end = 28.dp, top = 30.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        ?.take(3)
                        ?.joinToString(" • ") { it.name }
                        .orEmpty(),
                profileRequester = profileRequester,
                switchRequester = switchRequester,
                firstSettingRequester = settingRequesters.first(),
                onOpenPersonalization = { onOpenSettings("PERSONALIZATION") },
                onSwitchProfile = onSwitchProfile,
            )

            panelSettings.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEachIndexed { columnIndex, setting ->
                        val index = rowIndex * 2 + columnIndex
                        SettingsPanelCard(
                            setting = setting,
                            requester = settingRequesters[index],
                            onMove = { key ->
                                val next =
                                    when (key) {
                                        Key.DirectionLeft -> if (columnIndex > 0) index - 1 else index
                                        Key.DirectionRight -> if (columnIndex < row.lastIndex) index + 1 else index
                                        Key.DirectionUp -> if (rowIndex == 0) -1 else index - 2
                                        Key.DirectionDown -> if (index + 2 < settingRequesters.size) index + 2 else index
                                        else -> index
                                    }
                                when {
                                    next == -1 -> runCatching { switchRequester.requestFocus() }.isSuccess
                                    next != index -> runCatching { settingRequesters[next].requestFocus() }.isSuccess
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
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Text(
                text = "Press Back to close",
                color = PanelMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
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
                .background(PanelRaised, RoundedCornerShape(18.dp))
                .border(
                    width = if (profileFocused) 2.dp else 1.dp,
                    color = if (profileFocused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(profileRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            runCatching { switchRequester.requestFocus() }.isSuccess
                        } else {
                            false
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
                        .width(60.dp)
                        .height(60.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VUEO • $profileName",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (isKids) "Kids profile" else "Active profile • Personalization",
                    color = PanelMuted,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "›",
                color = PanelMuted,
                fontSize = 27.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileStat("My List", myListCount.toString(), Modifier.weight(1f))
            ProfileStat("Watched", watchedCount.toString(), Modifier.weight(1f))
            ProfileStat("DNA", dnaPercent?.let { "$it%" } ?: "Off", Modifier.weight(1f), dnaPercent != null)
        }

        if (tastePreview.isNotBlank()) {
            Text(
                text = tastePreview,
                color = PanelMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(switchRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
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
                        if (switchFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = if (switchFocused) 2.dp else 1.dp,
                        color = if (switchFocused) Color.White else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⇄  Switch Profiles",
                color = if (switchFocused) Color.White else PanelGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = value,
            color = if (highlighted) PanelGreen else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            color = PanelMuted,
            fontSize = 9.sp,
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

    Column(
        modifier =
            modifier
                .height(102.dp)
                .focusRequester(requester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onMove(event.key) else false
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    if (focused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.055f),
                    RoundedCornerShape(14.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = setting.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = setting.badge,
                color = if (focused) Color.White else PanelGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = setting.subtitle,
            color = PanelMuted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
