package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.TvTopNav
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HubBlack = Color(0xFF050706)
private val HubPanel = Color(0xFF101412)
private val HubYellow = Color(0xFFD6FF00)
private val HubGreen = Color(0xFF84E100)
private val HubMuted = Color(0xFFAAB2AD)

private sealed interface HubPinFlow {
    data class UnlockProfile(
        val profile: VueoProfile,
    ) : HubPinFlow

    data class SetFirst(
        val profileId: String,
        val profileName: String,
    ) : HubPinFlow

    data class SetConfirm(
        val profileId: String,
        val profileName: String,
        val firstPin: String,
    ) : HubPinFlow

    data class ChangeVerify(
        val profileId: String,
        val profileName: String,
    ) : HubPinFlow

    data class RemoveVerify(
        val profileId: String,
        val profileName: String,
    ) : HubPinFlow
}

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginStore =
        remember(context) {
            PluginStore(context.applicationContext)
        }
    val dnaPreferences =
        remember(context) {
            UserDnaPreferences(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
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
    var skipSegmentsEnabled by remember(activeProfileId) {
        mutableStateOf(settingsStore.skipSegmentsEnabled())
    }
    var preferredSubtitle by remember(activeProfileId) {
        mutableStateOf(settingsStore.preferredSubtitleLanguage())
    }
    var subtitlesByDefault by remember(activeProfileId) {
        mutableStateOf(settingsStore.subtitlesOnByDefault())
    }
    var autoSelectSubtitle by remember(activeProfileId) {
        mutableStateOf(settingsStore.autoSelectPreferredSubtitle())
    }
    var userDnaEnabled by remember(activeProfileId) {
        mutableStateOf(dnaPreferences.userDnaEnabled(activeProfileId))
    }
    var personalizedRecommendations by remember(activeProfileId) {
        mutableStateOf(dnaPreferences.personalizedRecommendationsEnabled(activeProfileId))
    }
    var showDnaMatch by remember(activeProfileId) {
        mutableStateOf(dnaPreferences.showDnaMatchEnabled(activeProfileId))
    }
    var askOnStartup by remember {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }
    var autoUpdates by remember {
        mutableStateOf(settingsStore.automaticUpdateChecksEnabled())
    }
    var tmdbApiKey by remember { mutableStateOf(pluginStore.tmdbApiKey()) }
    var mdblistApiKey by remember { mutableStateOf(settingsStore.mdblistApiKey()) }
    var geminiApiKey by remember { mutableStateOf(settingsStore.geminiApiKey()) }
    var tmdbMetadataEnabled by remember {
        mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled())
    }
    var tmdbArtworkEnabled by remember {
        mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled())
    }
    var ratingsEnabled by remember {
        mutableStateOf(settingsStore.mdblistRatingsEnabled())
    }
    var geminiEnabled by remember {
        mutableStateOf(settingsStore.geminiInsightsEnabled())
    }
    var metadataStatus by remember { mutableStateOf<String?>(null) }
    var includeCredentialsInBackup by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var pinFlow by remember { mutableStateOf<HubPinFlow?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    fun refreshActiveProfileState(
        profileId: String,
    ) {
        activeProfileId = profileId
        profiles = profileStore.profiles()
        history = libraryStore.history()
        resumeEnabled = settingsStore.resumePlaybackEnabled()
        recoveryEnabled = settingsStore.autoSourceRecoveryEnabled()
        autoNextEnabled = settingsStore.autoPlayNextEpisodeEnabled()
        quality = settingsStore.preferredQuality()
        skipSegmentsEnabled = settingsStore.skipSegmentsEnabled()
        preferredSubtitle = settingsStore.preferredSubtitleLanguage()
        subtitlesByDefault = settingsStore.subtitlesOnByDefault()
        autoSelectSubtitle = settingsStore.autoSelectPreferredSubtitle()
        userDnaEnabled = dnaPreferences.userDnaEnabled(profileId)
        personalizedRecommendations = dnaPreferences.personalizedRecommendationsEnabled(profileId)
        showDnaMatch = dnaPreferences.showDnaMatchEnabled(profileId)
        askOnStartup = profileStore.askWhoIsWatchingOnStartup()
        onProfileChanged(profileId)
    }

    fun activateProfile(
        profileId: String,
    ) {
        if (profileStore.setActiveProfile(profileId)) {
            refreshActiveProfileState(profileId)
        }
    }

    val createBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    backupStatus = "Saving backup…"
                    runCatching {
                        VueoBackupManager.exportToUri(
                            context = context.applicationContext,
                            uri = uri,
                            includeCredentials = includeCredentialsInBackup,
                        )
                    }.onSuccess { summary ->
                        backupStatus =
                            "Backup saved • ${summary.valueCount} values"
                    }.onFailure { failure ->
                        backupStatus = failure.message ?: "Unable to save backup"
                    }
                }
            }
        }

    val restoreBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    backupStatus = "Restoring backup…"
                    runCatching {
                        VueoBackupManager.restoreFromUri(
                            context = context.applicationContext,
                            uri = uri,
                        )
                    }.onSuccess { summary ->
                        val restoredProfileId = profileStore.activeProfileId()
                        refreshActiveProfileState(restoredProfileId)
                        tmdbApiKey = pluginStore.tmdbApiKey()
                        mdblistApiKey = settingsStore.mdblistApiKey()
                        geminiApiKey = settingsStore.geminiApiKey()
                        tmdbMetadataEnabled = settingsStore.tmdbMetadataEnrichmentEnabled()
                        tmdbArtworkEnabled = settingsStore.tmdbArtworkEnrichmentEnabled()
                        ratingsEnabled = settingsStore.mdblistRatingsEnabled()
                        geminiEnabled = settingsStore.geminiInsightsEnabled()
                        backupStatus =
                            "Restored ${summary.valueCount} values" +
                                (summary.sourceVersion?.let { " • VUEO $it" } ?: "")
                    }.onFailure { failure ->
                        backupStatus = failure.message ?: "Unable to restore backup"
                    }
                }
            }
        }

    BackHandler {
        if (pinFlow != null) {
            pinFlow = null
            pinError = null
        } else {
            onNavigate("Home")
        }
    }

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
                                locked = profileStore.hasProfilePin(profile.id),
                                modifier =
                                    if (index == 0) {
                                        Modifier.focusRequester(firstProfileRequester)
                                    } else {
                                        Modifier
                                    },
                                onClick = {
                                    when {
                                        profile.id == activeProfileId -> Unit
                                        profileStore.hasProfilePin(profile.id) -> {
                                            pinError = null
                                            pinFlow = HubPinFlow.UnlockProfile(profile)
                                        }
                                        else -> activateProfile(profile.id)
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
                                        profileStore.setActiveProfile(created.id)
                                        refreshActiveProfileState(created.id)
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
                        title = "Skip Intro / Recap / Ending",
                        value = if (skipSegmentsEnabled) "On" else "Off",
                        onClick = {
                            skipSegmentsEnabled = !skipSegmentsEnabled
                            settingsStore.setSkipSegmentsEnabled(skipSegmentsEnabled)
                        },
                    )

                    SettingButton(
                        title = "Subtitles by default",
                        value = if (subtitlesByDefault) "On" else "Off",
                        onClick = {
                            subtitlesByDefault = !subtitlesByDefault
                            settingsStore.setSubtitlesOnByDefault(subtitlesByDefault)
                        },
                    )

                    SettingButton(
                        title = "Preferred subtitle",
                        value = preferredSubtitle.label,
                        onClick = {
                            val values = SubtitleLanguage.entries
                            val next = values[(values.indexOf(preferredSubtitle) + 1) % values.size]
                            preferredSubtitle = next
                            settingsStore.setPreferredSubtitleLanguage(next)
                        },
                    )

                    SettingButton(
                        title = "Auto select preferred subtitle",
                        value = if (autoSelectSubtitle) "On" else "Off",
                        onClick = {
                            autoSelectSubtitle = !autoSelectSubtitle
                            settingsStore.setAutoSelectPreferredSubtitle(autoSelectSubtitle)
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
                        text = "VUEO DNA",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingButton(
                        title = "User DNA",
                        value = if (userDnaEnabled) "On" else "Off",
                        onClick = {
                            userDnaEnabled = !userDnaEnabled
                            dnaPreferences.setUserDnaEnabled(activeProfileId, userDnaEnabled)
                        },
                    )

                    SettingButton(
                        title = "Personalized recommendations",
                        value = if (personalizedRecommendations) "On" else "Off",
                        onClick = {
                            personalizedRecommendations = !personalizedRecommendations
                            dnaPreferences.setPersonalizedRecommendationsEnabled(
                                activeProfileId,
                                personalizedRecommendations,
                            )
                        },
                    )

                    SettingButton(
                        title = "DNA Match on details",
                        value = if (showDnaMatch) "On" else "Off",
                        onClick = {
                            showDnaMatch = !showDnaMatch
                            dnaPreferences.setShowDnaMatchEnabled(activeProfileId, showDnaMatch)
                        },
                    )

                    Text(
                        text = "DNA is calculated locally from this profile's History and My List.",
                        color = HubMuted,
                        fontSize = 12.sp,
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

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Metadata & Enhancements",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    ApiKeyField(
                        label = "TMDB API key",
                        value = tmdbApiKey,
                        onValueChange = { tmdbApiKey = it },
                    )
                    ApiKeyField(
                        label = "MDBList API key",
                        value = mdblistApiKey,
                        onValueChange = { mdblistApiKey = it },
                    )
                    ApiKeyField(
                        label = "Gemini API key",
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                    )

                    SettingButton(
                        title = "Save API keys",
                        value = "Save",
                        onClick = {
                            pluginStore.setTmdbApiKey(tmdbApiKey)
                            settingsStore.setMdblistApiKey(mdblistApiKey)
                            settingsStore.setGeminiApiKey(geminiApiKey)
                            metadataStatus = "API keys saved"
                        },
                    )

                    SettingButton(
                        title = "TMDB metadata",
                        value = if (tmdbMetadataEnabled) "On" else "Off",
                        onClick = {
                            tmdbMetadataEnabled = !tmdbMetadataEnabled
                            settingsStore.setTmdbMetadataEnrichmentEnabled(tmdbMetadataEnabled)
                        },
                    )
                    SettingButton(
                        title = "TMDB artwork",
                        value = if (tmdbArtworkEnabled) "On" else "Off",
                        onClick = {
                            tmdbArtworkEnabled = !tmdbArtworkEnabled
                            settingsStore.setTmdbArtworkEnrichmentEnabled(tmdbArtworkEnabled)
                        },
                    )
                    SettingButton(
                        title = "MDBList ratings",
                        value = if (ratingsEnabled) "On" else "Off",
                        onClick = {
                            ratingsEnabled = !ratingsEnabled
                            settingsStore.setMdblistRatingsEnabled(ratingsEnabled)
                        },
                    )
                    SettingButton(
                        title = "Gemini insights",
                        value = if (geminiEnabled) "On" else "Off",
                        onClick = {
                            geminiEnabled = !geminiEnabled
                            settingsStore.setGeminiInsightsEnabled(geminiEnabled)
                        },
                    )

                    metadataStatus?.let { status ->
                        Text(
                            text = status,
                            color = HubMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Backup & Migration",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingButton(
                        title = "Include API keys in backup",
                        value = if (includeCredentialsInBackup) "Yes" else "No",
                        onClick = {
                            includeCredentialsInBackup = !includeCredentialsInBackup
                        },
                    )

                    SettingButton(
                        title = "Create VUEO backup",
                        value = "Export",
                        onClick = {
                            createBackupLauncher.launch(
                                "vueo-backup-${System.currentTimeMillis()}.json"
                            )
                        },
                    )

                    SettingButton(
                        title = "Restore VUEO backup",
                        value = "Import",
                        onClick = {
                            restoreBackupLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream",
                                )
                            )
                        },
                    )

                    Text(
                        text =
                            "Backups can migrate profiles, library, playback, settings and content configuration between VUEO Mobile and TV.",
                        color = HubMuted,
                        fontSize = 12.sp,
                    )

                    backupStatus?.let { status ->
                        Text(
                            text = status,
                            color = HubMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item {
                val activeProfile =
                    profiles.firstOrNull { it.id == activeProfileId }
                        ?: profileStore.activeProfile()
                val hasPin = profileStore.hasProfilePin(activeProfile.id)

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Profile Security",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingButton(
                        title = "Profile PIN",
                        value = if (hasPin) "Change PIN" else "Set PIN",
                        onClick = {
                            pinError = null
                            pinFlow =
                                if (hasPin) {
                                    HubPinFlow.ChangeVerify(
                                        profileId = activeProfile.id,
                                        profileName = activeProfile.name,
                                    )
                                } else {
                                    HubPinFlow.SetFirst(
                                        profileId = activeProfile.id,
                                        profileName = activeProfile.name,
                                    )
                                }
                        },
                    )

                    if (hasPin) {
                        SettingButton(
                            title = "Remove profile PIN",
                            value = "Verify PIN",
                            onClick = {
                                pinError = null
                                pinFlow =
                                    HubPinFlow.RemoveVerify(
                                        profileId = activeProfile.id,
                                        profileName = activeProfile.name,
                                    )
                            },
                        )
                    }

                    SettingButton(
                        title = "Kids profile",
                        value = if (activeProfile.isKids) "On" else "Off",
                        onClick = {
                            profileStore.updateProfile(
                                profileId = activeProfile.id,
                                name = activeProfile.name,
                                avatar = activeProfile.avatar,
                                isKids = !activeProfile.isKids,
                            )
                            profiles = profileStore.profiles()
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

        val flow = pinFlow
        if (flow != null) {
            key(flow, pinResetToken) {
                val title =
                    when (flow) {
                        is HubPinFlow.UnlockProfile -> "Unlock ${flow.profile.name}"
                        is HubPinFlow.SetFirst -> "Set PIN for ${flow.profileName}"
                        is HubPinFlow.SetConfirm -> "Confirm PIN"
                        is HubPinFlow.ChangeVerify -> "Verify ${flow.profileName}"
                        is HubPinFlow.RemoveVerify -> "Remove PIN from ${flow.profileName}"
                    }

                val subtitle =
                    when (flow) {
                        is HubPinFlow.UnlockProfile -> "Enter the 4-digit profile PIN"
                        is HubPinFlow.SetFirst -> "Choose a new 4-digit PIN"
                        is HubPinFlow.SetConfirm -> "Enter the same PIN again"
                        is HubPinFlow.ChangeVerify -> "Enter the current PIN before changing it"
                        is HubPinFlow.RemoveVerify -> "Enter the current PIN to remove the lock"
                    }

                TvPinEntryOverlay(
                    title = title,
                    subtitle = subtitle,
                    errorText = pinError,
                    onComplete = { pin ->
                        when (flow) {
                            is HubPinFlow.UnlockProfile -> {
                                if (profileStore.verifyProfilePin(flow.profile.id, pin)) {
                                    pinFlow = null
                                    pinError = null
                                    activateProfile(flow.profile.id)
                                } else {
                                    pinError = "Incorrect PIN"
                                    pinResetToken += 1
                                }
                            }

                            is HubPinFlow.SetFirst -> {
                                pinError = null
                                pinFlow =
                                    HubPinFlow.SetConfirm(
                                        profileId = flow.profileId,
                                        profileName = flow.profileName,
                                        firstPin = pin,
                                    )
                            }

                            is HubPinFlow.SetConfirm -> {
                                if (pin == flow.firstPin) {
                                    if (profileStore.setProfilePin(flow.profileId, pin)) {
                                        profiles = profileStore.profiles()
                                        pinFlow = null
                                        pinError = null
                                    } else {
                                        pinError = "Unable to save PIN"
                                        pinResetToken += 1
                                    }
                                } else {
                                    pinError = "PINs did not match"
                                    pinResetToken += 1
                                }
                            }

                            is HubPinFlow.ChangeVerify -> {
                                if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                    pinError = null
                                    pinFlow =
                                        HubPinFlow.SetFirst(
                                            profileId = flow.profileId,
                                            profileName = flow.profileName,
                                        )
                                } else {
                                    pinError = "Incorrect PIN"
                                    pinResetToken += 1
                                }
                            }

                            is HubPinFlow.RemoveVerify -> {
                                if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                    profileStore.clearProfilePin(flow.profileId)
                                    profiles = profileStore.profiles()
                                    pinFlow = null
                                    pinError = null
                                } else {
                                    pinError = "Incorrect PIN"
                                    pinResetToken += 1
                                }
                            }
                        }
                    },
                    onCancel = {
                        pinFlow = null
                        pinError = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = HubYellow,
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedLabelColor = HubYellow,
                unfocusedLabelColor = HubMuted,
                cursorColor = HubYellow,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileCard(
    profile: VueoProfile,
    selected: Boolean,
    locked: Boolean,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Text(
                    text = "ACTIVE",
                    color = HubGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            if (locked) {
                Text(
                    text = "PIN",
                    color = HubYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            if (profile.isKids) {
                Text(
                    text = "KIDS",
                    color = HubGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
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
