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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.TvTopNav
import com.vueo.tv.library.TvLibraryStore
import kotlinx.coroutines.delay

private val HubBlack = Color(0xFF050706)
private val HubPanel = Color(0xFF101412)
private val HubYellow = Color(0xFFD6FF00)
private val HubGreen = Color(0xFF84E100)
private val HubMuted = Color(0xFFAAB2AD)

@Composable
fun TvUserHubScreen(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    libraryStore: TvLibraryStore,
    onNavigate: (String) -> Unit,
    onProfileChanged: (String) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
) {
    val navRequesters =
        remember {
            listOf("Home", "Search", "Library", "Content Manager", "Luckez")
                .associateWith { FocusRequester() }
        }

    val firstProfileRequester = remember { FocusRequester() }
    var profiles by remember { mutableStateOf(profileStore.profiles()) }
    var activeProfileId by remember { mutableStateOf(profileStore.activeProfileId()) }
    var history by remember(activeProfileId) { mutableStateOf(libraryStore.history()) }
    var resumeEnabled by remember(activeProfileId) {
        mutableStateOf(settingsStore.resumePlaybackEnabled())
    }
    var recoveryEnabled by remember(activeProfileId) {
        mutableStateOf(settingsStore.autoSourceRecoveryEnabled())
    }
    var autoNextEnabled by remember(activeProfileId) {
        mutableStateOf(settingsStore.autoPlayNextEpisodeEnabled())
    }
    var quality by remember(activeProfileId) {
        mutableStateOf(settingsStore.preferredQuality())
    }
    var askOnStartup by remember {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }
    var autoUpdates by remember {
        mutableStateOf(settingsStore.automaticUpdateChecksEnabled())
    }

    BackHandler { onNavigate("Home") }

    LaunchedEffect(activeProfileId) {
        delay(90)
        runCatching {
            firstProfileRequester.requestFocus()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A100C),
                            HubBlack,
                            HubBlack,
                        )
                    )
                ),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 92.dp),
            contentPadding =
                PaddingValues(
                    start = 58.dp,
                    end = 58.dp,
                    bottom = 48.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "Profiles & Settings",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Active profile • ${profileStore.activeProfile().name}",
                        color = HubMuted,
                        fontSize = 14.sp,
                    )
                }
            }

            item {
                Column {
                    Text(
                        text = "Who's Watching",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            profiles,
                            key = { _, profile -> profile.id },
                        ) { index, profile ->
                            ProfileCard(
                                profile = profile,
                                selected = profile.id == activeProfileId,
                                modifier =
                                    if (index == 0) {
                                        Modifier.focusRequester(firstProfileRequester)
                                    } else {
                                        Modifier
                                    },
                                onClick = {
                                    if (profileStore.setActiveProfile(profile.id)) {
                                        activeProfileId = profile.id
                                        history = libraryStore.history()
                                        resumeEnabled = settingsStore.resumePlaybackEnabled()
                                        recoveryEnabled = settingsStore.autoSourceRecoveryEnabled()
                                        autoNextEnabled = settingsStore.autoPlayNextEpisodeEnabled()
                                        quality = settingsStore.preferredQuality()
                                        onProfileChanged(profile.id)
                                    }
                                },
                            )
                        }

                        item(key = "add-profile") {
                            AddProfileCard(
                                onClick = {
                                    if (profiles.size < ProfileStore.MAX_PROFILES) {
                                        val created =
                                            profileStore.createProfile(
                                                name = "Profile ${profiles.size + 1}",
                                                avatar = "avatar_man_1",
                                                isKids = false,
                                            )
                                        profiles = profileStore.profiles()
                                        profileStore.setActiveProfile(created.id)
                                        activeProfileId = created.id
                                        history = libraryStore.history()
                                        resumeEnabled = settingsStore.resumePlaybackEnabled()
                                        recoveryEnabled = settingsStore.autoSourceRecoveryEnabled()
                                        autoNextEnabled = settingsStore.autoPlayNextEpisodeEnabled()
                                        quality = settingsStore.preferredQuality()
                                        askOnStartup = profileStore.askWhoIsWatchingOnStartup()
                                        onProfileChanged(created.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Playback",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingButton(
                        title = "Resume playback",
                        value = if (resumeEnabled) "On" else "Off",
                        onClick = {
                            resumeEnabled = !resumeEnabled
                            settingsStore.setResumePlaybackEnabled(resumeEnabled)
                        },
                    )

                    SettingButton(
                        title = "Automatic source recovery",
                        value = if (recoveryEnabled) "On" else "Off",
                        onClick = {
                            recoveryEnabled = !recoveryEnabled
                            settingsStore.setAutoSourceRecoveryEnabled(recoveryEnabled)
                        },
                    )

                    SettingButton(
                        title = "Auto play next episode",
                        value = if (autoNextEnabled) "On" else "Off",
                        onClick = {
                            autoNextEnabled = !autoNextEnabled
                            settingsStore.setAutoPlayNextEpisodeEnabled(autoNextEnabled)
                        },
                    )

                    SettingButton(
                        title = "Preferred quality",
                        value = quality.label,
                        onClick = {
                            val values = PreferredQuality.entries
                            val next =
                                values[
                                    (values.indexOf(quality) + 1) %
                                        values.size
                                ]
                            quality = next
                            settingsStore.setPreferredQuality(next)
                        },
                    )
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "App",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingButton(
                        title = "Who's Watching on startup",
                        value = if (askOnStartup) "On" else "Off",
                        onClick = {
                            askOnStartup = !askOnStartup
                            profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                        },
                    )

                    SettingButton(
                        title = "Automatic update checks",
                        value = if (autoUpdates) "On" else "Off",
                        onClick = {
                            autoUpdates = !autoUpdates
                            settingsStore.setAutomaticUpdateChecksEnabled(autoUpdates)
                        },
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent History",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            onClick = {
                                libraryStore.clearHistory()
                                history = emptyList()
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    contentColor = Color.White,
                                ),
                            shape = RoundedCornerShape(9.dp),
                        ) {
                            Text("Clear")
                        }
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(
                            history.take(12),
                            key = { _, entry -> entry.mediaKey },
                        ) { _, entry ->
                            HistoryCard(
                                entry = entry,
                                onClick = { onResume(entry) },
                            )
                        }
                    }
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = firstProfileRequester,
            selectedLabel = "Luckez",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun ProfileCard(
    profile: VueoProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.07f else 1f,
        label = "profileScale",
    )

    Column(
        modifier =
            modifier
                .width(142.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .width(104.dp)
                    .height(104.dp)
                    .background(
                        if (selected) HubGreen.copy(alpha = 0.18f) else HubPanel,
                        CircleShape,
                    )
                    .border(
                        width = if (focused || selected) 2.dp else 1.dp,
                        color =
                            if (focused) HubYellow
                            else if (selected) HubGreen
                            else Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    profile.name
                        .trim()
                        .firstOrNull()
                        ?.uppercase()
                        ?: "V",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Text(
                text = "Active",
                color = HubGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AddProfileCard(
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.07f else 1f,
        label = "addProfileScale",
    )

    Column(
        modifier =
            Modifier
                .width(142.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .width(104.dp)
                    .height(104.dp)
                    .background(HubPanel, CircleShape)
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) HubYellow else Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add Profile",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingButton(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.015f else 1f,
        label = "settingScale",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .background(
                    if (focused) Color.White.copy(alpha = 0.10f) else HubPanel,
                    RoundedCornerShape(11.dp),
                )
                .border(
                    1.dp,
                    if (focused) HubYellow.copy(alpha = 0.72f)
                    else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(11.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = if (focused) HubYellow else HubMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HistoryCard(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.05f else 1f,
        label = "historyScale",
    )

    Column(
        modifier =
            Modifier
                .width(220.dp)
                .scale(scale)
                .background(HubPanel, RoundedCornerShape(11.dp))
                .border(
                    1.dp,
                    if (focused) HubYellow else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(11.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .padding(14.dp),
    ) {
        Text(
            text = entry.media.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                entry.episodeTitle
                    ?: entry.media.displayType,
            color = HubMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { entry.progressFraction.coerceIn(0f, 1f) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text =
                if (entry.isCompleted) {
                    "Watched"
                } else {
                    "${(entry.progressFraction * 100).toInt()}% watched"
                },
            color = if (entry.isCompleted) HubGreen else HubMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
