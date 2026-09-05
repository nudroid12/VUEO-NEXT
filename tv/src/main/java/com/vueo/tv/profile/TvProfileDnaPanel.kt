package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.motion.tvPanelEnter
import com.vueo.tv.ui.motion.tvPanelExit
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay
import com.vueo.tv.ui.motion.tvFocusSpec

private val PanelBlack = Color(0xFF050706)
private val PanelSurface = Color(0xFF101412)
private val PanelRaised = Color(0xFF151A17)
private val PanelMuted = Color(0xFFAAB2AD)

private data class PanelSetting(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val contentManager: Boolean = false,
)

private val panelSettings =
    listOf(
        PanelSetting("PERSONALIZATION", "Personalization", "User DNA, DNA Match & recommendations.", Icons.Default.Settings),
        PanelSetting("CONTENT_MANAGER", "Content Manager", "Addons, repos & providers.", Icons.Default.Extension, contentManager = true),
        PanelSetting("ENHANCEMENTS", "Enhancements", "Metadata, ratings & external services.", Icons.Default.SettingsInputComponent),
        PanelSetting("PLAYBACK", "Playback", "Player & streaming preferences.", Icons.Default.PlayArrow),
        PanelSetting("SUBTITLES", "Subtitles", "Language & display preferences.", Icons.Default.VideoLibrary),
        PanelSetting("SOURCES", "Sources", "Source ranking & information.", Icons.Default.SettingsInputComponent),
        PanelSetting("APPEARANCE", "Appearance", "Interface preferences.", Icons.Default.Settings),
        PanelSetting("DATA_STORAGE", "Data & Storage", "Backup, history, cache & app data.", Icons.Default.VideoLibrary),
        PanelSetting("UPDATES", "Updates", "Version & update preferences.", Icons.Default.Refresh),
        PanelSetting("ABOUT", "About VUEO", "Privacy, architecture & build information.", Icons.Default.Settings),
    )

@Composable
fun TvProfileDnaPanel(
    visible: Boolean,
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenContentManager: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile = remember(visible) { profileStore.activeProfile() }
    val tvLibrary = remember(context) { TvLibraryStore(context.applicationContext) }
    val pluginStore = remember(context) { PluginStore(context.applicationContext) }
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

    val enhancementCount =
        remember(visible) {
            listOf(
                pluginStore.tmdbApiKey().isNotBlank(),
                settingsStore.mdblistApiKey().isNotBlank(),
                settingsStore.geminiApiKey().isNotBlank(),
            ).count { it }
        }
    val statuses =
        remember(visible, dnaEnabled, enhancementCount) {
            mapOf(
                "PERSONALIZATION" to if (dnaEnabled) "User DNA on" else "User DNA off",
                "CONTENT_MANAGER" to "${pluginStore.repositories().size} repos • ${pluginStore.totalProviderCount()} providers",
                "ENHANCEMENTS" to if (enhancementCount > 0) "$enhancementCount configured" else "Optional services",
                "PLAYBACK" to "${settingsStore.preferredQuality().label} • ${if (settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"}",
                "SUBTITLES" to "${settingsStore.preferredSubtitleLanguage().label} • ${if (settingsStore.subtitlesOnByDefault()) "Default on" else "Default off"}",
                "SOURCES" to if (settingsStore.showSourceTechnicalDetails()) "Technical details on" else "Smart ranking active",
                "APPEARANCE" to "VUEO Dark • ${settingsStore.appAccent().label} accent",
                "DATA_STORAGE" to "Local device data",
                "UPDATES" to if (settingsStore.automaticUpdateChecksEnabled()) "Automatic checks on" else "Manual checks",
                "ABOUT" to "Local-first app info",
            )
        }

    val avatarDrawable =
        remember(activeProfile.avatar) {
            ProfileAvatarCatalog.drawableRes(activeProfile.avatar)
        }

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
            enter = fadeIn(animationSpec = tween(TvMotion.STANDARD_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(animationSpec = tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
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
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(listOf(PanelSurface.copy(alpha = 0.995f), PanelBlack)),
                            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                        )
                        .verticalScroll(scrollState)
                        .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )

                TvMobileSettingsProfileCard(
                    profileName = activeProfile.name,
                    isKids = activeProfile.isKids,
                    avatarDrawable = avatarDrawable,
                    myListCount = myListCount,
                    watchedCount = watchedCount,
                    dnaPercent = if (dnaEnabled) dnaSnapshot?.confidencePercent else null,
                    tastePreview =
                        dnaSnapshot
                            ?.topGenres
                            ?.take(3)
                            ?.joinToString(" • ") { "${it.name} ${it.percent}%" }
                            .orEmpty(),
                    profileRequester = profileRequester,
                    switchRequester = switchRequester,
                    firstSettingRequester = settingRequesters.first(),
                    onExitLeft = onDismiss,
                    onOpenPersonalization = { onOpenSettings("PERSONALIZATION") },
                    onSwitchProfile = onSwitchProfile,
                )

                panelSettings.forEachIndexed { index, setting ->
                    MobileSettingsNavigationCard(
                        setting = setting,
                        status = statuses[setting.key].orEmpty(),
                        requester = settingRequesters[index],
                        onMove = { key ->
                            when (key) {
                                Key.DirectionLeft -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionUp -> {
                                    val target = if (index == 0) switchRequester else settingRequesters[index - 1]
                                    runCatching { target.requestFocus() }.isSuccess
                                }
                                Key.DirectionDown -> {
                                    if (index < settingRequesters.lastIndex) {
                                        runCatching { settingRequesters[index + 1].requestFocus() }.isSuccess
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
                    )
                }

                Text(
                    text = "VUEO TV",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    color = PanelMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TvMobileSettingsProfileCard(
    profileName: String,
    isKids: Boolean,
    avatarDrawable: Int?,
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
    val profileScale by animateFloatAsState(if (profileFocused) 1.018f else 1f,
        animationSpec = tvFocusSpec(), label = "settingsProfileScale")
    val switchScale by animateFloatAsState(if (switchFocused) 1.022f else 1f,
        animationSpec = tvFocusSpec(), label = "settingsSwitchScale")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(profileScale)
                .background(PanelRaised, RoundedCornerShape(20.dp))
                .border(
                    width = if (profileFocused) 2.dp else 1.dp,
                    color = if (profileFocused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarDrawable != null) {
                    Image(
                        painter = painterResource(avatarDrawable),
                        contentDescription = profileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "VUEO • $profileName",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isKids) "Kids profile" else "Active profile",
                    color = PanelMuted,
                    fontSize = 12.sp,
                )
            }
            Text(text = "›", color = PanelMuted, fontSize = 28.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MobileProfileStat("My List", myListCount.toString(), Modifier.weight(1f))
            MobileProfileStat("Watched", watchedCount.toString(), Modifier.weight(1f))
            MobileProfileStat("DNA", dnaPercent?.let { "$it%" } ?: "Off", Modifier.weight(1f), highlighted = dnaPercent != null)
        }

        Text(
            text = tastePreview.ifBlank { if (dnaPercent != null) "Keep watching to shape your VUEO DNA." else "Enable User DNA in Personalization." },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            color = PanelMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
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
                        if (switchFocused) Color.White.copy(alpha = 0.16f) else PanelSurface.copy(alpha = 0.82f),
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        width = if (switchFocused) 2.dp else 1.dp,
                        color = if (switchFocused) Color.White else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                    ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "⇄", color = TvAccent, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(text = "Switch Profiles", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MobileProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .background(
                    if (highlighted) TvAccent.copy(alpha = 0.10f) else PanelSurface,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = if (highlighted) TvAccent else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Text(text = label, color = PanelMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MobileSettingsNavigationCard(
    setting: PanelSetting,
    status: String,
    requester: FocusRequester,
    onMove: (Key) -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.018f else 1f,
        animationSpec = tvFocusSpec(), label = "mobileSettingsCardScale")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onMove(event.key) else false
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    if (focused) Color.White.copy(alpha = 0.15f) else PanelRaised,
                    RoundedCornerShape(18.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.14f) else PanelSurface,
                        RoundedCornerShape(14.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = setting.icon,
                contentDescription = null,
                tint = if (focused) Color.White else TvAccent,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = setting.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = setting.subtitle,
                color = PanelMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    color = if (focused) Color.White else TvAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "›", color = PanelMuted, fontSize = 28.sp)
    }
}
