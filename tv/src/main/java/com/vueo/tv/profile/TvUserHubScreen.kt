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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.tv.TV_TOP_NAV_LABELS
import com.vueo.tv.TvTopNav
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SettingsBlack = Color(0xFF050706)
private val SettingsPanel = Color(0xFF101612)
private val SettingsMuted = Color(0xFFAAB2AD)
private val SettingsDanger = Color(0xFFFF8A80)


private data class TvVersionInfo(
    val name: String,
    val code: Long,
)

@Composable
private fun rememberTvVersionInfo(): TvVersionInfo {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val packageInfo =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    0,
                )
            @Suppress("DEPRECATION")
            TvVersionInfo(
                name = packageInfo.versionName.orEmpty().ifBlank { "Unknown" },
                code = packageInfo.versionCode.toLong(),
            )
        }.getOrElse {
            TvVersionInfo(
                name = "Unknown",
                code = 0L,
            )
        }
    }
}

private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val badge: String,
) {
    PERSONALIZATION(
        title = "Personalization",
        subtitle = "User DNA, DNA Match & recommendations.",
        badge = "DNA",
    ),
    CONTENT_MANAGER(
        title = "Content Manager",
        subtitle = "Addons, repos & providers.",
        badge = "CM",
    ),
    ENHANCEMENTS(
        title = "Enhancements",
        subtitle = "Metadata, ratings & external services.",
        badge = "+",
    ),
    PLAYBACK(
        title = "Playback",
        subtitle = "Player & streaming preferences.",
        badge = "PLAY",
    ),
    SUBTITLES(
        title = "Subtitles",
        subtitle = "Language & display preferences.",
        badge = "CC",
    ),
    SOURCES(
        title = "Sources",
        subtitle = "Source ranking & information.",
        badge = "SRC",
    ),
    APPEARANCE(
        title = "Appearance",
        subtitle = "Interface preferences.",
        badge = "UI",
    ),
    DATA_STORAGE(
        title = "Data & Storage",
        subtitle = "Backup, history, cache & app data.",
        badge = "DATA",
    ),
    UPDATES(
        title = "Updates",
        subtitle = "Version & update preferences.",
        badge = "UP",
    ),
    ABOUT(
        title = "About VUEO",
        subtitle = "Privacy, architecture & build information.",
        badge = "i",
    ),
}

private sealed interface SettingsPinFlow {
    data class SetFirst(
        val profileId: String,
        val profileName: String,
    ) : SettingsPinFlow

    data class SetConfirm(
        val profileId: String,
        val profileName: String,
        val firstPin: String,
    ) : SettingsPinFlow

    data class ChangeVerify(
        val profileId: String,
        val profileName: String,
    ) : SettingsPinFlow

    data class RemoveVerify(
        val profileId: String,
        val profileName: String,
    ) : SettingsPinFlow
}

@Composable
fun TvUserHubScreen(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    libraryStore: TvLibraryStore,
    initialCategoryKey: String? = null,
    onExitToPanel: (() -> Unit)? = null,
    onNavigate: (String) -> Unit,
    onProfileChanged: (String) -> Unit,
    onAccentChanged: () -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onCheckForUpdates: ((String) -> Unit) -> Unit,
) {
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS
                .associateWith { FocusRequester() }
        }
    val hubRequesters = remember { List(SettingsCategory.entries.size) { FocusRequester() } }
    val subPageFirstRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginStore = remember(context) { PluginStore(context.applicationContext) }
    val dnaPreferences =
        remember(context) {
            UserDnaPreferences(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }

    var category by remember(initialCategoryKey) {
        mutableStateOf(
            initialCategoryKey?.let { key ->
                SettingsCategory.entries.firstOrNull { it.name == key }
            }
        )
    }
    var hubFocusIndex by remember { mutableIntStateOf(0) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    var includeCredentials by remember {
        mutableStateOf(settingsStore.includeCredentialsInBackup())
    }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var historyCount by remember { mutableIntStateOf(libraryStore.history().size) }

    var pinFlow by remember { mutableStateOf<SettingsPinFlow?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    val createBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    backupStatus = "Saving backup..."
                    runCatching {
                        VueoBackupManager.exportToUri(
                            context = context.applicationContext,
                            uri = uri,
                            includeCredentials = includeCredentials,
                        )
                    }.onSuccess { summary ->
                        backupStatus = "Backup saved • ${summary.valueCount} values"
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
                    backupStatus = "Restoring backup..."
                    runCatching {
                        VueoBackupManager.restoreFromUri(
                            context = context.applicationContext,
                            uri = uri,
                        )
                    }.onSuccess { summary ->
                        historyCount = libraryStore.history().size
                        profileRevision += 1
                        onProfileChanged(profileStore.activeProfileId())
                        backupStatus =
                            "Restored ${summary.valueCount} values" +
                                (summary.sourceVersion?.let { " • VUEO $it" } ?: "")
                    }.onFailure { failure ->
                        backupStatus = failure.message ?: "Unable to restore backup"
                    }
                }
            }
        }

    BackHandler(enabled = pinFlow == null) {
        if (category != null && !initialCategoryKey.isNullOrBlank() && onExitToPanel != null) {
            onExitToPanel()
        } else if (category != null) {
            category = null
        } else if (onExitToPanel != null) {
            onExitToPanel()
        } else {
            onNavigate("Home")
        }
    }

    LaunchedEffect(category, hubFocusIndex) {
        delay(90)
        if (category == null) {
            runCatching { hubRequesters[hubFocusIndex].requestFocus() }
        } else {
            runCatching { subPageFirstRequester.requestFocus() }
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
                            SettingsBlack,
                            SettingsBlack,
                        )
                    )
                ),
    ) {
        if (category == null) {
            SettingsHub(
                profileStore = profileStore,
                settingsStore = settingsStore,
                dnaPreferences = dnaPreferences,
                pluginStore = pluginStore,
                requesters = hubRequesters,
                focusedIndex = hubFocusIndex,
                onSelect = { index, selected ->
                    hubFocusIndex = index
                    if (selected == SettingsCategory.CONTENT_MANAGER) {
                        onNavigate("Content Manager")
                    } else {
                        category = selected
                    }
                },
            )
        } else {
            SettingsSubPageShell(
                title = category!!.title,
                subtitle = category!!.subtitle,
            ) {
                if (category != SettingsCategory.CONTENT_MANAGER) {
                    TvFunctionalSettingsPage(
                        categoryKey = category!!.name,
                        profileStore = profileStore,
                        settingsStore = settingsStore,
                        libraryStore = libraryStore,
                        pluginStore = pluginStore,
                        firstRequester = subPageFirstRequester,
                        onProfileChanged = onProfileChanged,
                        onAccentChanged = onAccentChanged,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
        }

        val flow = pinFlow
        if (flow != null) {
            key(flow, pinResetToken) {
                val title =
                    when (flow) {
                        is SettingsPinFlow.SetFirst -> "Set PIN for ${flow.profileName}"
                        is SettingsPinFlow.SetConfirm -> "Confirm PIN"
                        is SettingsPinFlow.ChangeVerify -> "Verify ${flow.profileName}"
                        is SettingsPinFlow.RemoveVerify -> "Remove PIN from ${flow.profileName}"
                    }
                val subtitle =
                    when (flow) {
                        is SettingsPinFlow.SetFirst -> "Choose a new 4-digit PIN"
                        is SettingsPinFlow.SetConfirm -> "Enter the same PIN again"
                        is SettingsPinFlow.ChangeVerify -> "Enter the current PIN before changing it"
                        is SettingsPinFlow.RemoveVerify -> "Enter the current PIN to remove the lock"
                    }

                TvPinEntryOverlay(
                    title = title,
                    subtitle = subtitle,
                    errorText = pinError,
                    onComplete = { pin ->
                        when (flow) {
                            is SettingsPinFlow.SetFirst -> {
                                pinError = null
                                pinFlow =
                                    SettingsPinFlow.SetConfirm(
                                        profileId = flow.profileId,
                                        profileName = flow.profileName,
                                        firstPin = pin,
                                    )
                            }

                            is SettingsPinFlow.SetConfirm -> {
                                if (pin == flow.firstPin && profileStore.setProfilePin(flow.profileId, pin)) {
                                    pinFlow = null
                                    pinError = null
                                    profileRevision += 1
                                } else {
                                    pinError = if (pin != flow.firstPin) "PINs do not match" else "Unable to save PIN"
                                    pinResetToken += 1
                                }
                            }

                            is SettingsPinFlow.ChangeVerify -> {
                                if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                    pinError = null
                                    pinFlow =
                                        SettingsPinFlow.SetFirst(
                                            profileId = flow.profileId,
                                            profileName = flow.profileName,
                                        )
                                } else {
                                    pinError = "Incorrect PIN"
                                    pinResetToken += 1
                                }
                            }

                            is SettingsPinFlow.RemoveVerify -> {
                                if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                    profileStore.clearProfilePin(flow.profileId)
                                    pinFlow = null
                                    pinError = null
                                    profileRevision += 1
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
private fun SettingsHub(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    dnaPreferences: UserDnaPreferences,
    pluginStore: PluginStore,
    requesters: List<FocusRequester>,
    focusedIndex: Int,
    onSelect: (Int, SettingsCategory) -> Unit,
) {
    val versionInfo = rememberTvVersionInfo()
    val activeProfile = profileStore.activeProfile()
    val dnaOn = dnaPreferences.userDnaEnabled(activeProfile.id)
    val enhancementCount =
        listOf(
            pluginStore.tmdbApiKey().isNotBlank(),
            settingsStore.mdblistApiKey().isNotBlank(),
            settingsStore.geminiApiKey().isNotBlank(),
        ).count { it }

    val statuses =
        mapOf(
            SettingsCategory.PERSONALIZATION to if (dnaOn) "User DNA on" else "User DNA off",
            SettingsCategory.CONTENT_MANAGER to "Addons • repos • providers",
            SettingsCategory.ENHANCEMENTS to if (enhancementCount > 0) "$enhancementCount configured" else "Optional services",
            SettingsCategory.PLAYBACK to "${settingsStore.preferredQuality().label} • ${if (settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"}",
            SettingsCategory.SUBTITLES to "${settingsStore.preferredSubtitleLanguage().label} • ${if (settingsStore.subtitlesOnByDefault()) "Default on" else "Default off"}",
            SettingsCategory.SOURCES to if (settingsStore.showSourceTechnicalDetails()) "Technical details on" else "Smart ranking active",
            SettingsCategory.APPEARANCE to "VUEO Dark • ${settingsStore.appAccent().label}",
            SettingsCategory.DATA_STORAGE to "Local device data",
            SettingsCategory.UPDATES to if (settingsStore.automaticUpdateChecksEnabled()) "Automatic checks on" else "Manual checks",
            SettingsCategory.ABOUT to "VUEO TV ${versionInfo.name}",
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 112.dp, bottom = 42.dp),
    ) {
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Configure your VUEO experience",
            color = SettingsMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))

        SettingsCategory.entries.chunked(5).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEachIndexed { columnIndex, category ->
                    val index = rowIndex * 5 + columnIndex
                    SettingsHubCard(
                        category = category,
                        status = statuses.getValue(category),
                        requester = requesters[index],
                        onMove = { key ->
                            val next =
                                when (key) {
                                    Key.DirectionLeft -> if (columnIndex > 0) index - 1 else index
                                    Key.DirectionRight -> if (columnIndex < row.lastIndex) index + 1 else index
                                    Key.DirectionUp -> if (rowIndex > 0) index - 5 else index
                                    Key.DirectionDown -> if (rowIndex == 0 && index + 5 < requesters.size) index + 5 else index
                                    else -> index
                                }
                            if (next != index) {
                                runCatching { requesters[next].requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
                        onClick = { onSelect(index, category) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rowIndex == 0) Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "Active profile • ${activeProfile.name}",
            color = SettingsMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingsHubCard(
    category: SettingsCategory,
    status: String,
    requester: FocusRequester,
    onMove: (Key) -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        label = "settingsHubCardScale",
    )

    Column(
        modifier =
            modifier
                .height(132.dp)
                .focusRequester(requester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onMove(event.key) else false
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    if (focused) Color.White.copy(alpha = 0.16f) else SettingsPanel,
                    RoundedCornerShape(16.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = category.badge,
                color = if (focused) Color.White else TvAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = category.subtitle,
            color = SettingsMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Text(
            text = status,
            color = if (focused) Color.White else SettingsMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsSubPageShell(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 108.dp, end = 108.dp, top = 52.dp, bottom = 34.dp),
    ) {
        Text(
            text = "VUEO",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = subtitle,
            color = SettingsMuted,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun PersonalizationPage(
    profileStore: ProfileStore,
    dnaPreferences: UserDnaPreferences,
    firstRequester: FocusRequester,
    revision: Int,
    onOpenDna: () -> Unit,
    onSetPin: (SettingsPinFlow) -> Unit,
) {
    val activeProfile = remember(revision) { profileStore.activeProfile() }
    var userDnaEnabled by remember(activeProfile.id, revision) {
        mutableStateOf(dnaPreferences.userDnaEnabled(activeProfile.id))
    }
    var showDnaMatch by remember(activeProfile.id, revision) {
        mutableStateOf(dnaPreferences.showDnaMatchEnabled(activeProfile.id))
    }
    var recommendations by remember(activeProfile.id, revision) {
        mutableStateOf(dnaPreferences.personalizedRecommendationsEnabled(activeProfile.id))
    }
    var askOnStartup by remember(revision) {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }
    val hasPin = profileStore.hasProfilePin(activeProfile.id)

    SettingsList {
        item {
            SettingRow(
                title = "Your DNA",
                subtitle = "View genres, taste signals and DNA strength for ${activeProfile.name}.",
                value = if (userDnaEnabled) "Open" else "DNA off",
                requester = firstRequester,
                enabled = userDnaEnabled,
                onClick = onOpenDna,
            )
        }
        item {
            SettingToggleRow(
                title = "User DNA",
                subtitle = "Use this profile's History, playback progress and My List to build a local taste profile.",
                checked = userDnaEnabled,
                onToggle = {
                    userDnaEnabled = it
                    dnaPreferences.setUserDnaEnabled(activeProfile.id, it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Show DNA Match",
                subtitle = "Show a local taste-match score on supported movie and series details.",
                checked = showDnaMatch,
                enabled = userDnaEnabled,
                onToggle = {
                    showDnaMatch = it
                    dnaPreferences.setShowDnaMatchEnabled(activeProfile.id, it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Personalized Recommendations",
                subtitle = "Use User DNA for For You and Because You Watched recommendations.",
                checked = recommendations,
                enabled = userDnaEnabled,
                onToggle = {
                    recommendations = it
                    dnaPreferences.setPersonalizedRecommendationsEnabled(activeProfile.id, it)
                },
            )
        }
        item { SettingsSectionLabel("PROFILE") }
        item {
            SettingToggleRow(
                title = "Ask Who's Watching on startup",
                subtitle = "Show profile picker at launch when more than one profile exists.",
                checked = askOnStartup,
                onToggle = {
                    askOnStartup = it
                    profileStore.setAskWhoIsWatchingOnStartup(it)
                },
            )
        }
        item {
            SettingRow(
                title = "Profile PIN",
                subtitle = "Protect ${activeProfile.name} with a 4-digit TV PIN.",
                value = if (hasPin) "Change PIN" else "Set PIN",
                onClick = {
                    onSetPin(
                        if (hasPin) {
                            SettingsPinFlow.ChangeVerify(activeProfile.id, activeProfile.name)
                        } else {
                            SettingsPinFlow.SetFirst(activeProfile.id, activeProfile.name)
                        }
                    )
                },
            )
        }
        if (hasPin) {
            item {
                SettingRow(
                    title = "Remove Profile PIN",
                    subtitle = "Verify the current PIN before removing the profile lock.",
                    value = "Verify",
                    danger = true,
                    onClick = {
                        onSetPin(SettingsPinFlow.RemoveVerify(activeProfile.id, activeProfile.name))
                    },
                )
            }
        }
        item {
            SettingsInfoCard(
                title = "Local by design",
                text = "Turning User DNA off does not delete History, My List or playback progress. It only stops VUEO from using those signals for DNA Match and personalized recommendations.",
            )
        }
    }
}

@Composable
private fun EnhancementsPage(
    pluginStore: PluginStore,
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var tmdbKey by remember { mutableStateOf(pluginStore.tmdbApiKey()) }
    var mdblistKey by remember { mutableStateOf(settingsStore.mdblistApiKey()) }
    var geminiKey by remember { mutableStateOf(settingsStore.geminiApiKey()) }
    var tmdbMetadata by remember { mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled()) }
    var tmdbArtwork by remember { mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled()) }
    var ratings by remember { mutableStateOf(settingsStore.mdblistRatingsEnabled()) }
    var gemini by remember { mutableStateOf(settingsStore.geminiInsightsEnabled()) }

    SettingsList {
        item {
            ApiKeyField(
                title = "TMDB API Key",
                value = tmdbKey,
                requester = firstRequester,
                onValueChange = {
                    tmdbKey = it
                    pluginStore.setTmdbApiKey(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "TMDB Metadata",
                subtitle = "Use TMDB to enrich metadata when configured.",
                checked = tmdbMetadata,
                enabled = tmdbKey.isNotBlank(),
                onToggle = {
                    tmdbMetadata = it
                    settingsStore.setTmdbMetadataEnrichmentEnabled(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "TMDB Artwork",
                subtitle = "Use TMDB artwork enrichment when configured.",
                checked = tmdbArtwork,
                enabled = tmdbKey.isNotBlank(),
                onToggle = {
                    tmdbArtwork = it
                    settingsStore.setTmdbArtworkEnrichmentEnabled(it)
                },
            )
        }
        item { SettingsSectionLabel("MDBLIST") }
        item {
            ApiKeyField(
                title = "MDBList API Key",
                value = mdblistKey,
                onValueChange = {
                    mdblistKey = it
                    settingsStore.setMdblistApiKey(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Ratings",
                subtitle = "Show supported external ratings on details.",
                checked = ratings,
                enabled = mdblistKey.isNotBlank(),
                onToggle = {
                    ratings = it
                    settingsStore.setMdblistRatingsEnabled(it)
                },
            )
        }
        item { SettingsSectionLabel("GEMINI") }
        item {
            ApiKeyField(
                title = "Gemini API Key",
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    settingsStore.setGeminiApiKey(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Gemini Insights",
                subtitle = "Enable optional AI insights when a Gemini key is configured.",
                checked = gemini,
                enabled = geminiKey.isNotBlank(),
                onToggle = {
                    gemini = it
                    settingsStore.setGeminiInsightsEnabled(it)
                },
            )
        }
        item {
            SettingsInfoCard(
                title = "Optional enhancements",
                text = "VUEO core playback, Content Manager and local Personalization continue to work without external enhancement services.",
            )
        }
    }
}

@Composable
private fun PlaybackPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var resume by remember { mutableStateOf(settingsStore.resumePlaybackEnabled()) }
    var quality by remember { mutableStateOf(settingsStore.preferredQuality()) }
    var autoRecovery by remember { mutableStateOf(settingsStore.autoSourceRecoveryEnabled()) }
    var autoNext by remember { mutableStateOf(settingsStore.autoPlayNextEpisodeEnabled()) }
    var skipSegments by remember { mutableStateOf(settingsStore.skipSegmentsEnabled()) }

    SettingsList {
        item {
            SettingToggleRow(
                title = "Resume Playback",
                subtitle = "Continue supported titles from the last saved position.",
                checked = resume,
                requester = firstRequester,
                onToggle = {
                    resume = it
                    settingsStore.setResumePlaybackEnabled(it)
                },
            )
        }
        item {
            SettingRow(
                title = "Preferred Quality",
                subtitle = "Preferred resolution used by Smart Source ranking.",
                value = quality.label,
                onClick = {
                    val values = PreferredQuality.entries
                    quality = values[(values.indexOf(quality) + 1) % values.size]
                    settingsStore.setPreferredQuality(quality)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Skip Intro / Recap / Ending",
                subtitle = "Show skip controls when verified segment timestamps are available.",
                checked = skipSegments,
                onToggle = {
                    skipSegments = it
                    settingsStore.setSkipSegmentsEnabled(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Auto-play Next Episode",
                subtitle = "Start the next episode after the player countdown.",
                checked = autoNext,
                onToggle = {
                    autoNext = it
                    settingsStore.setAutoPlayNextEpisodeEnabled(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Auto Source Recovery",
                subtitle = "Try ranked alternatives after a playback error while keeping the timestamp.",
                checked = autoRecovery,
                onToggle = {
                    autoRecovery = it
                    settingsStore.setAutoSourceRecoveryEnabled(it)
                },
            )
        }
        item {
            SettingsInfoCard(
                title = "Deterministic source selection",
                text = "VUEO prioritises direct playable sources using resolution, provider health and your preferred quality while keeping manual source selection available.",
            )
        }
    }
}

@Composable
private fun SubtitlesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var preferred by remember { mutableStateOf(settingsStore.preferredSubtitleLanguage()) }
    var secondary by remember { mutableStateOf(settingsStore.secondarySubtitleLanguage()) }
    var defaultOn by remember { mutableStateOf(settingsStore.subtitlesOnByDefault()) }
    var autoSelect by remember { mutableStateOf(settingsStore.autoSelectPreferredSubtitle()) }
    var embeddedPriority by remember { mutableStateOf(settingsStore.embeddedSubtitlePriority()) }
    var size by remember { mutableStateOf(settingsStore.subtitleSize()) }

    SettingsList {
        item {
            SettingRow(
                title = "Preferred Language",
                subtitle = "First subtitle language VUEO should prefer when tracks are available.",
                value = preferred.label,
                requester = firstRequester,
                onClick = {
                    val values = SubtitleLanguage.entries
                    preferred = values[(values.indexOf(preferred) + 1) % values.size]
                    settingsStore.setPreferredSubtitleLanguage(preferred)
                },
            )
        }
        item {
            SettingRow(
                title = "Secondary Language",
                subtitle = "Fallback language when the preferred language is unavailable.",
                value = secondary.label,
                onClick = {
                    val values = SubtitleLanguage.entries
                    secondary = values[(values.indexOf(secondary) + 1) % values.size]
                    settingsStore.setSecondarySubtitleLanguage(secondary)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Subtitles On by Default",
                subtitle = "Prefer showing subtitles automatically when a suitable track exists.",
                checked = defaultOn,
                onToggle = {
                    defaultOn = it
                    settingsStore.setSubtitlesOnByDefault(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Auto Select Preferred Language",
                subtitle = "Prioritise your preferred language automatically.",
                checked = autoSelect,
                onToggle = {
                    autoSelect = it
                    settingsStore.setAutoSelectPreferredSubtitle(it)
                },
            )
        }
        item {
            SettingToggleRow(
                title = "Embedded Subtitle Priority",
                subtitle = "Prefer tracks already included in the stream before external tracks when possible.",
                checked = embeddedPriority,
                onToggle = {
                    embeddedPriority = it
                    settingsStore.setEmbeddedSubtitlePriority(it)
                },
            )
        }
        item {
            SettingRow(
                title = "Subtitle Size",
                subtitle = "Saved display size for the VUEO player.",
                value = size.label,
                onClick = {
                    val values = SubtitleSize.entries
                    size = values[(values.indexOf(size) + 1) % values.size]
                    settingsStore.setSubtitleSize(size)
                },
            )
        }
        item {
            SettingsInfoCard(
                title = "Subtitle sources",
                text = "OpenSubtitles and other subtitle addons remain in Content Manager. This page only controls how VUEO chooses and displays discovered tracks.",
            )
        }
    }
}

@Composable
private fun SourcesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var technicalDetails by remember { mutableStateOf(settingsStore.showSourceTechnicalDetails()) }

    SettingsList {
        item {
            SettingToggleRow(
                title = "Technical Source Details",
                subtitle = "Show codec, HDR and audio information on Source Picker cards.",
                checked = technicalDetails,
                requester = firstRequester,
                onToggle = {
                    technicalDetails = it
                    settingsStore.setShowSourceTechnicalDetails(it)
                },
            )
        }
        item {
            SettingsInfoCard(
                title = "Smart Source Ranking • Active",
                text = "VUEO ranks direct playability, resolution, HDR, codec information, provider health, response latency and your preferred quality.",
            )
        }
        item {
            SettingsInfoCard(
                title = "Provider Health Influence • Active",
                text = "Healthy and responsive providers receive a ranking advantage without blocking other available sources.",
            )
        }
        item {
            SettingsInfoCard(
                title = "Progressive discovery",
                text = "The Source Picker opens immediately and updates while providers continue searching, so slow providers do not block fast ones.",
            )
        }
    }
}

@Composable
private fun AppearancePage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var accent by remember { mutableStateOf(settingsStore.appAccent()) }

    SettingsList {
        item {
            SettingRow(
                title = "Brand Accent",
                subtitle = "White focus remains fixed for TV readability. Accent affects supported VUEO branding surfaces.",
                value = accent.label,
                requester = firstRequester,
                onClick = {
                    val values = AppAccent.entries
                    accent = values[(values.indexOf(accent) + 1) % values.size]
                    settingsStore.setAppAccent(accent)
                },
            )
        }
        item {
            SettingsInfoCard(
                title = "Theme • VUEO Dark",
                text = "Dark charcoal surfaces stay fixed for comfortable movie browsing and playback in a living-room environment.",
            )
        }
        item {
            SettingsInfoCard(
                title = "TV Focus • White + Scale",
                text = "Focused controls use a bright white outline and subtle scale so D-pad position stays obvious from a sofa.",
            )
        }
    }
}

@Composable
private fun DataStoragePage(
    firstRequester: FocusRequester,
    includeCredentials: Boolean,
    onIncludeCredentialsChange: (Boolean) -> Unit,
    historyCount: Int,
    backupStatus: String?,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearHistory: () -> Unit,
) {
    SettingsList {
        item {
            SettingToggleRow(
                title = "Include API Keys in Backup",
                subtitle = "Credentials are excluded by default. Enable only for a personal migration file you control.",
                checked = includeCredentials,
                requester = firstRequester,
                onToggle = onIncludeCredentialsChange,
            )
        }
        item {
            SettingRow(
                title = "Create VUEO Backup",
                subtitle = "Export profiles, Content Manager configuration, Settings, library and playback data.",
                value = "Export",
                onClick = onCreateBackup,
            )
        }
        item {
            SettingRow(
                title = "Restore VUEO Backup",
                subtitle = "Import a VUEO backup created on Mobile or TV.",
                value = "Import",
                onClick = onRestoreBackup,
            )
        }
        item {
            SettingRow(
                title = "Watch History",
                subtitle = "$historyCount saved history ${if (historyCount == 1) "entry" else "entries"} on this active profile.",
                value = if (historyCount > 0) "Clear" else "Empty",
                enabled = historyCount > 0,
                danger = historyCount > 0,
                onClick = onClearHistory,
            )
        }
        if (!backupStatus.isNullOrBlank()) {
            item {
                SettingsInfoCard(
                    title = "Backup Status",
                    text = backupStatus,
                )
            }
        }
        item {
            SettingsInfoCard(
                title = "Local device data",
                text = "Cache, downloaded provider scripts and health diagnostics are rebuilt instead of copied into backups.",
            )
        }
    }
}

@Composable
private fun UpdatesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
    updateStatus: String?,
    onCheck: () -> Unit,
) {
    val versionInfo = rememberTvVersionInfo()
    var automatic by remember { mutableStateOf(settingsStore.automaticUpdateChecksEnabled()) }

    SettingsList {
        item {
            SettingToggleRow(
                title = "Automatic Update Checks",
                subtitle = "Check the VUEO TV release feed automatically when the app starts.",
                checked = automatic,
                requester = firstRequester,
                onToggle = {
                    automatic = it
                    settingsStore.setAutomaticUpdateChecksEnabled(it)
                },
            )
        }
        item {
            SettingRow(
                title = "Check for Updates",
                subtitle = "Current version ${versionInfo.name} • build ${versionInfo.code}",
                value = "Check Now",
                onClick = onCheck,
            )
        }
        if (!updateStatus.isNullOrBlank()) {
            item {
                SettingsInfoCard(
                    title = "Update Status",
                    text = updateStatus,
                )
            }
        }
        item {
            SettingsInfoCard(
                title = "In-place updates",
                text = "VUEO TV updates install over the existing app and keep local profiles, settings and library data.",
            )
        }
    }
}

@Composable
private fun AboutPage(
    firstRequester: FocusRequester,
) {
    val versionInfo = rememberTvVersionInfo()

    SettingsList {
        item {
            SettingsInfoCard(
                title = "VUEO TV ${versionInfo.name}",
                text = "Build ${versionInfo.code} • package com.vueo.tv",
                requester = firstRequester,
            )
        }
        item {
            SettingsInfoCard(
                title = "Local-first",
                text = "Profiles, User DNA, My List, history, playback progress and preferences are stored locally on the device unless you explicitly export a backup.",
            )
        }
        item {
            SettingsInfoCard(
                title = "Content architecture",
                text = "Content Manager handles addons, repositories and provider plugins. Smart Source ranks discovered playback candidates independently of the interface.",
            )
        }
        item {
            SettingsInfoCard(
                title = "10-foot TV interface",
                text = "VUEO TV is designed for D-pad navigation with readable typography, obvious focus and no cursor requirement.",
            )
        }
    }
}

@Composable
private fun SettingsList(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    value: String,
    requester: FocusRequester? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.012f else 1f,
        label = "settingsRowScale",
    )
    val requesterModifier = if (requester != null) Modifier.focusRequester(requester) else Modifier

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(requesterModifier)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled)
                .background(
                    when {
                        focused && enabled -> Color.White.copy(alpha = 0.16f)
                        else -> SettingsPanel
                    },
                    RoundedCornerShape(13.dp),
                )
                .border(
                    width = if (focused && enabled) 2.dp else 1.dp,
                    color =
                        when {
                            focused && enabled -> Color.White
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                    shape = RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Color.White else SettingsMuted.copy(alpha = 0.55f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = SettingsMuted.copy(alpha = if (enabled) 1f else 0.50f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(22.dp))
        Text(
            text = value,
            color =
                when {
                    danger && enabled -> SettingsDanger
                    focused && enabled -> Color.White
                    enabled -> SettingsMuted
                    else -> SettingsMuted.copy(alpha = 0.45f)
                },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    requester: FocusRequester? = null,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        value = if (checked) "On" else "Off",
        requester = requester,
        enabled = enabled,
        onClick = { onToggle(!checked) },
    )
}

@Composable
private fun ApiKeyField(
    title: String,
    value: String,
    requester: FocusRequester? = null,
    onValueChange: (String) -> Unit,
) {
    val requesterModifier = if (requester != null) Modifier.focusRequester(requester) else Modifier

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(title) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(requesterModifier),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = SettingsMuted,
                cursorColor = Color.White,
            ),
    )
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = SettingsMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsInfoCard(
    title: String,
    text: String,
    requester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val requesterModifier = if (requester != null) Modifier.focusRequester(requester) else Modifier

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(requesterModifier)
                .onFocusChanged { focused = it.isFocused }
                .focusable(requester != null)
                .background(SettingsPanel, RoundedCornerShape(13.dp))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 20.dp, vertical = 15.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = text,
            color = SettingsMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}
