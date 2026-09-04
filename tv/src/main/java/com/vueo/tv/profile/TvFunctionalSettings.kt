package com.vueo.tv.profile

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.enrichment.GeminiClient
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.player.TvSourceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FunctionalPanel = Color(0xFF101612)
private val FunctionalRaised = Color(0xFF151A17)
private val FunctionalMuted = Color(0xFFAAB2AD)
private val FunctionalDanger = Color(0xFFFF8A80)

private enum class EnhancementSection {
    TMDB,
    MDBLIST,
    GEMINI,
}

private enum class DataClearAction(
    val title: String,
    val message: String,
    val action: String,
) {
    CATALOG_CACHE(
        "Clear catalog & search cache?",
        "Home, browse and search snapshots will be rebuilt the next time you open them.",
        "Clear",
    ),
    SOURCE_CACHE(
        "Clear recent source cache?",
        "Short-lived source and subtitle results will be discarded. The next playback will search again.",
        "Clear",
    ),
    CONTINUE_WATCHING(
        "Clear Continue Watching?",
        "Unfinished playback entries for the active profile will be removed.",
        "Clear",
    ),
    WATCH_HISTORY(
        "Clear Watch History?",
        "Watch History for the active profile will be removed. My List is not changed.",
        "Clear",
    ),
    RESET(
        "Reset VUEO data?",
        "Profiles, Content Manager configuration, API keys, preferences, Library, playback progress and temporary TV data will be cleared.",
        "Reset",
    ),
}

private sealed interface FunctionalPinFlow {
    data class SetFirst(val profileId: String, val profileName: String) : FunctionalPinFlow
    data class SetConfirm(val profileId: String, val profileName: String, val firstPin: String) : FunctionalPinFlow
    data class ChangeVerify(val profileId: String, val profileName: String) : FunctionalPinFlow
    data class RemoveVerify(val profileId: String, val profileName: String) : FunctionalPinFlow
}

private data class ChoiceItem(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

@Composable
internal fun TvFunctionalSettingsPage(
    categoryKey: String,
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    libraryStore: TvLibraryStore,
    pluginStore: PluginStore,
    firstRequester: FocusRequester,
    onProfileChanged: (String) -> Unit,
    onAccentChanged: () -> Unit,
    onCheckForUpdates: ((String) -> Unit) -> Unit,
) {
    when (categoryKey) {
        "PERSONALIZATION" -> FunctionalPersonalizationPage(
            profileStore = profileStore,
            firstRequester = firstRequester,
        )
        "ENHANCEMENTS" -> FunctionalEnhancementsPage(
            pluginStore = pluginStore,
            settingsStore = settingsStore,
            firstRequester = firstRequester,
        )
        "PLAYBACK" -> FunctionalPlaybackPage(
            settingsStore = settingsStore,
            firstRequester = firstRequester,
        )
        "SUBTITLES" -> FunctionalSubtitlesPage(
            settingsStore = settingsStore,
            firstRequester = firstRequester,
        )
        "SOURCES" -> FunctionalSourcesPage(
            settingsStore = settingsStore,
            firstRequester = firstRequester,
        )
        "APPEARANCE" -> FunctionalAppearancePage(
            settingsStore = settingsStore,
            firstRequester = firstRequester,
            onAccentChanged = onAccentChanged,
        )
        "DATA_STORAGE" -> FunctionalDataStoragePage(
            profileStore = profileStore,
            settingsStore = settingsStore,
            libraryStore = libraryStore,
            firstRequester = firstRequester,
            onProfileChanged = onProfileChanged,
        )
        "UPDATES" -> FunctionalUpdatesPage(
            settingsStore = settingsStore,
            firstRequester = firstRequester,
            onCheckForUpdates = onCheckForUpdates,
        )
        "ABOUT" -> FunctionalAboutPage(firstRequester)
        else -> FunctionalInfoCard(
            title = "Unavailable",
            text = "This Settings section is not available on VUEO TV.",
            requester = firstRequester,
        )
    }
}

@Composable
private fun FunctionalPersonalizationPage(
    profileStore: ProfileStore,
    firstRequester: FocusRequester,
) {
    val context = LocalContext.current
    val activeProfile = profileStore.activeProfile()
    val preferences = remember(context) {
        UserDnaPreferences(
            context.applicationContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    }
    var dnaOn by remember(activeProfile.id) { mutableStateOf(preferences.userDnaEnabled(activeProfile.id)) }
    var showMatch by remember(activeProfile.id) { mutableStateOf(preferences.showDnaMatchEnabled(activeProfile.id)) }
    var recommendations by remember(activeProfile.id) { mutableStateOf(preferences.personalizedRecommendationsEnabled(activeProfile.id)) }
    var askWho by remember(activeProfile.id) { mutableStateOf(profileStore.askWhoIsWatchingOnStartup()) }
    var showDnaDetails by remember { mutableStateOf(false) }
    var pinFlow by remember { mutableStateOf<FunctionalPinFlow?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }
    var securityRevision by remember { mutableIntStateOf(0) }
    val hasPin = remember(activeProfile.id, securityRevision) { profileStore.hasProfilePin(activeProfile.id) }

    val snapshot = remember(activeProfile.id, dnaOn, showDnaDetails) {
        if (!dnaOn) {
            null
        } else {
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
    }

    BackHandler(enabled = showDnaDetails) { showDnaDetails = false }

    if (showDnaDetails) {
        FunctionalList {
            item {
                FunctionalInfoCard(
                    title = "${activeProfile.name} • VUEO DNA",
                    text = snapshot?.let { "DNA confidence ${it.confidencePercent}%" } ?: "Not enough viewing signals yet.",
                    requester = firstRequester,
                )
            }
            snapshot?.topGenres?.take(8)?.forEach { genre ->
                item {
                    FunctionalInfoCard(
                        title = genre.name,
                        text = "Taste affinity ${genre.percent}%",
                    )
                }
            }
            item {
                FunctionalInfoCard(
                    title = "Local by design",
                    text = "VUEO DNA is built from this profile's local History, playback progress and My List.",
                )
            }
        }
    } else {
        FunctionalList {
            item {
                FunctionalInfoCard(
                    title = activeProfile.name,
                    text = if (dnaOn) "User DNA is enabled for this profile." else "User DNA is disabled for this profile.",
                    requester = firstRequester,
                )
            }
            item { FunctionalSectionLabel("USER DNA") }
            item {
                FunctionalRow(
                    title = "Your DNA",
                    subtitle = if (dnaOn) "View genres, taste signals and DNA strength." else "Enable User DNA to build a local taste profile.",
                    value = if (dnaOn) "View" else "Unavailable",
                    enabled = dnaOn,
                    onClick = { showDnaDetails = true },
                )
            }
            item {
                FunctionalToggleRow(
                    title = "User DNA",
                    subtitle = "Use History, playback progress and My List to build a local taste profile.",
                    checked = dnaOn,
                    onToggle = {
                        dnaOn = it
                        preferences.setUserDnaEnabled(activeProfile.id, it)
                    },
                )
            }
            item {
                FunctionalToggleRow(
                    title = "Show DNA Match",
                    subtitle = "Show a local taste-match score on supported movie and series details.",
                    checked = showMatch,
                    enabled = dnaOn,
                    onToggle = {
                        showMatch = it
                        preferences.setShowDnaMatchEnabled(activeProfile.id, it)
                    },
                )
            }
            item {
                FunctionalToggleRow(
                    title = "Personalized Recommendations",
                    subtitle = "Use User DNA for profile-based recommendations.",
                    checked = recommendations,
                    enabled = dnaOn,
                    onToggle = {
                        recommendations = it
                        preferences.setPersonalizedRecommendationsEnabled(activeProfile.id, it)
                    },
                )
            }
            item { FunctionalSectionLabel("TV PROFILE") }
            item {
                FunctionalToggleRow(
                    title = "Ask Who's Watching",
                    subtitle = "Show the profile picker at launch when multiple profiles exist.",
                    checked = askWho,
                    onToggle = {
                        askWho = it
                        profileStore.setAskWhoIsWatchingOnStartup(it)
                    },
                )
            }
            item(key = "profile-pin-$securityRevision") {
                FunctionalRow(
                    title = "Profile PIN",
                    subtitle = "Protect ${activeProfile.name} with a 4-digit TV PIN.",
                    value = if (hasPin) "Change" else "Set",
                    onClick = {
                        pinError = null
                        pinFlow = if (hasPin) {
                            FunctionalPinFlow.ChangeVerify(activeProfile.id, activeProfile.name)
                        } else {
                            FunctionalPinFlow.SetFirst(activeProfile.id, activeProfile.name)
                        }
                    },
                )
            }
            if (hasPin) {
                item(key = "remove-profile-pin-$securityRevision") {
                    FunctionalRow(
                        title = "Remove Profile PIN",
                        subtitle = "Verify the current PIN before removing the profile lock.",
                        value = "Remove",
                        danger = true,
                        onClick = {
                            pinError = null
                            pinFlow = FunctionalPinFlow.RemoveVerify(activeProfile.id, activeProfile.name)
                        },
                    )
                }
            }
            item {
                FunctionalInfoCard(
                    title = "Local by design",
                    text = "Turning User DNA off does not delete History, My List or playback progress.",
                )
            }
        }
    }

    val flow = pinFlow
    if (flow != null) {
        key(flow, pinResetToken) {
            val title = when (flow) {
                is FunctionalPinFlow.SetFirst -> "Set PIN for ${flow.profileName}"
                is FunctionalPinFlow.SetConfirm -> "Confirm PIN"
                is FunctionalPinFlow.ChangeVerify -> "Verify ${flow.profileName}"
                is FunctionalPinFlow.RemoveVerify -> "Remove PIN from ${flow.profileName}"
            }
            val subtitle = when (flow) {
                is FunctionalPinFlow.SetFirst -> "Choose a new 4-digit PIN"
                is FunctionalPinFlow.SetConfirm -> "Enter the same PIN again"
                is FunctionalPinFlow.ChangeVerify -> "Enter the current PIN first"
                is FunctionalPinFlow.RemoveVerify -> "Enter the current PIN to remove the lock"
            }
            TvPinEntryOverlay(
                title = title,
                subtitle = subtitle,
                errorText = pinError,
                onComplete = { pin ->
                    when (flow) {
                        is FunctionalPinFlow.SetFirst -> {
                            pinError = null
                            pinFlow = FunctionalPinFlow.SetConfirm(flow.profileId, flow.profileName, pin)
                        }
                        is FunctionalPinFlow.SetConfirm -> {
                            if (pin == flow.firstPin && profileStore.setProfilePin(flow.profileId, pin)) {
                                pinFlow = null
                                pinError = null
                                securityRevision += 1
                            } else {
                                pinError = if (pin != flow.firstPin) "PINs do not match" else "Unable to save PIN"
                                pinResetToken += 1
                            }
                        }
                        is FunctionalPinFlow.ChangeVerify -> {
                            if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                pinError = null
                                pinFlow = FunctionalPinFlow.SetFirst(flow.profileId, flow.profileName)
                            } else {
                                pinError = "Incorrect PIN"
                                pinResetToken += 1
                            }
                        }
                        is FunctionalPinFlow.RemoveVerify -> {
                            if (profileStore.verifyProfilePin(flow.profileId, pin)) {
                                profileStore.clearProfilePin(flow.profileId)
                                pinFlow = null
                                pinError = null
                                securityRevision += 1
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

@Composable
private fun FunctionalEnhancementsPage(
    pluginStore: PluginStore,
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var section by remember { mutableStateOf<EnhancementSection?>(null) }
    BackHandler(enabled = section != null) { section = null }

    LaunchedEffect(section) {
        delay(70)
        runCatching { firstRequester.requestFocus() }
    }

    when (section) {
        null -> FunctionalList {
            item {
                FunctionalInfoCard(
                    title = "External and optional",
                    text = "Enhancements add metadata, ratings or AI features. Core playback, Content Manager and local Personalization work without them.",
                    requester = firstRequester,
                )
            }
            item {
                FunctionalRow(
                    title = "TMDB",
                    subtitle = "Metadata, artwork and discovery enhancement.",
                    value = if (pluginStore.tmdbApiKey().isNotBlank()) "Configured" else "Set up",
                    onClick = { section = EnhancementSection.TMDB },
                )
            }
            item {
                FunctionalRow(
                    title = "MDBList",
                    subtitle = "IMDb, Rotten Tomatoes, Metacritic and more.",
                    value = if (settingsStore.mdblistApiKey().isNotBlank()) "Configured" else "Set up",
                    onClick = { section = EnhancementSection.MDBLIST },
                )
            }
            item {
                FunctionalRow(
                    title = "Gemini",
                    subtitle = "Optional AI insight service.",
                    value = if (settingsStore.geminiApiKey().isNotBlank()) "Configured" else "Set up",
                    onClick = { section = EnhancementSection.GEMINI },
                )
            }
        }
        EnhancementSection.TMDB -> FunctionalTmdbPage(pluginStore, settingsStore, firstRequester)
        EnhancementSection.MDBLIST -> FunctionalMdblistPage(settingsStore, firstRequester)
        EnhancementSection.GEMINI -> FunctionalGeminiPage(settingsStore, firstRequester)
    }
}

@Composable
private fun FunctionalTmdbPage(
    pluginStore: PluginStore,
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf(pluginStore.tmdbApiKey()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled()) }
    var recommendations by remember { mutableStateOf(settingsStore.tmdbRecommendationsEnabled()) }
    var similar by remember { mutableStateOf(settingsStore.tmdbSimilarTitlesEnabled()) }
    var artwork by remember { mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled()) }

    FunctionalList {
        item { FunctionalSectionLabel("TMDB") }
        item {
            FunctionalApiKeyField(
                title = "TMDB v3 API Key",
                value = keyText,
                requester = firstRequester,
                onValueChange = {
                    keyText = it
                    status = null
                },
            )
        }
        item {
            FunctionalRow(
                title = "Save API Key",
                subtitle = "Store the TMDB key locally on this TV.",
                value = "Save",
                enabled = keyText.isNotBlank(),
                onClick = {
                    pluginStore.setTmdbApiKey(keyText.trim())
                    status = "TMDB key saved locally."
                },
            )
        }
        item {
            FunctionalRow(
                title = "Test Connection",
                subtitle = "Verify the current TMDB key.",
                value = if (testing) "Testing…" else "Test",
                enabled = keyText.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    status = "Testing TMDB…"
                    scope.launch {
                        val ok = TmdbEnhancementClient.testConnection(keyText.trim())
                        status = if (ok) "TMDB connection successful." else "TMDB connection failed. Check the API key."
                        testing = false
                    }
                },
            )
        }
        status?.let { message -> item { FunctionalInfoCard("Status", message) } }
        item { FunctionalSectionLabel("DISCOVERY & METADATA") }
        item {
            FunctionalToggleRow("Metadata Enrichment", "Allow richer title information when TMDB is available.", metadata) {
                metadata = it
                settingsStore.setTmdbMetadataEnrichmentEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Recommendations", "Use TMDB recommendations as a discovery signal.", recommendations) {
                recommendations = it
                settingsStore.setTmdbRecommendationsEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Similar Titles", "Use TMDB similar titles as an additional discovery signal.", similar) {
                similar = it
                settingsStore.setTmdbSimilarTitlesEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Artwork Enrichment", "Allow better poster and backdrop fallback when available.", artwork) {
                artwork = it
                settingsStore.setTmdbArtworkEnrichmentEnabled(it)
            }
        }
    }
}

@Composable
private fun FunctionalMdblistPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf(settingsStore.mdblistApiKey()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var master by remember { mutableStateOf(settingsStore.mdblistRatingsEnabled()) }
    var imdb by remember { mutableStateOf(settingsStore.mdblistImdbEnabled()) }
    var tomatoes by remember { mutableStateOf(settingsStore.mdblistRottenTomatoesEnabled()) }
    var metacritic by remember { mutableStateOf(settingsStore.mdblistMetacriticEnabled()) }
    var tmdb by remember { mutableStateOf(settingsStore.mdblistTmdbRatingEnabled()) }
    var trakt by remember { mutableStateOf(settingsStore.mdblistTraktEnabled()) }

    FunctionalList {
        item { FunctionalSectionLabel("MDBLIST") }
        item {
            FunctionalApiKeyField(
                title = "MDBList API Key",
                value = keyText,
                requester = firstRequester,
                onValueChange = {
                    keyText = it
                    status = null
                },
            )
        }
        item {
            FunctionalRow("Save API Key", "Store the MDBList key locally on this TV.", "Save", enabled = keyText.isNotBlank()) {
                settingsStore.setMdblistApiKey(keyText.trim())
                status = "MDBList key saved locally."
            }
        }
        item {
            FunctionalRow("Test Connection", "Verify the current MDBList key.", if (testing) "Testing…" else "Test", enabled = keyText.isNotBlank() && !testing) {
                testing = true
                status = "Testing MDBList…"
                scope.launch {
                    val ok = MdblistClient.testConnection(keyText.trim())
                    status = if (ok) "MDBList connection successful." else "MDBList connection failed. Check the API key."
                    testing = false
                }
            }
        }
        status?.let { message -> item { FunctionalInfoCard("Status", message) } }
        item { FunctionalSectionLabel("RATINGS") }
        item {
            FunctionalToggleRow("Ratings Enrichment", "Master switch for MDBList rating information.", master) {
                master = it
                settingsStore.setMdblistRatingsEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("IMDb", "Show IMDb score when available.", imdb, enabled = master) {
                imdb = it
                settingsStore.setMdblistImdbEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Rotten Tomatoes", "Show Rotten Tomatoes score when available.", tomatoes, enabled = master) {
                tomatoes = it
                settingsStore.setMdblistRottenTomatoesEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Metacritic", "Show Metacritic score when available.", metacritic, enabled = master) {
                metacritic = it
                settingsStore.setMdblistMetacriticEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("TMDB Rating", "Show TMDB rating through MDBList when available.", tmdb, enabled = master) {
                tmdb = it
                settingsStore.setMdblistTmdbRatingEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Trakt", "Show Trakt score when available.", trakt, enabled = master) {
                trakt = it
                settingsStore.setMdblistTraktEnabled(it)
            }
        }
    }
}

@Composable
private fun FunctionalGeminiPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf(settingsStore.geminiApiKey()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf(settingsStore.geminiInsightsEnabled()) }

    FunctionalList {
        item { FunctionalSectionLabel("GEMINI") }
        item {
            FunctionalApiKeyField(
                title = "Gemini API Key",
                value = keyText,
                requester = firstRequester,
                onValueChange = {
                    keyText = it
                    status = null
                },
            )
        }
        item {
            FunctionalRow("Save API Key", "Store the Gemini key locally on this TV.", "Save", enabled = keyText.isNotBlank()) {
                settingsStore.setGeminiApiKey(keyText.trim())
                status = "Gemini key saved locally."
            }
        }
        item {
            FunctionalRow("Test Connection", "Verify the current Gemini key.", if (testing) "Testing…" else "Test", enabled = keyText.isNotBlank() && !testing) {
                testing = true
                status = "Testing Gemini…"
                scope.launch {
                    val result = GeminiClient.testConnection(keyText.trim())
                    status = result.message
                    testing = false
                }
            }
        }
        status?.let { message -> item { FunctionalInfoCard("Status", message) } }
        item {
            FunctionalToggleRow(
                title = "AI Insights",
                subtitle = "Enable optional Gemini insight capability when a key is configured.",
                checked = insights,
                enabled = settingsStore.geminiApiKey().isNotBlank() || keyText.isNotBlank(),
            ) {
                insights = it
                settingsStore.setGeminiInsightsEnabled(it)
            }
        }
        item {
            FunctionalInfoCard(
                title = "Privacy & usage",
                text = "Gemini is optional. API keys are stored locally. Core VUEO playback and Personalization do not depend on Gemini.",
            )
        }
    }
}

@Composable
private fun FunctionalPlaybackPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var resume by remember { mutableStateOf(settingsStore.resumePlaybackEnabled()) }
    var quality by remember { mutableStateOf(settingsStore.preferredQuality()) }
    var speed by remember { mutableStateOf(settingsStore.playerPlaybackSpeed()) }
    var videoFit by remember { mutableStateOf(settingsStore.playerVideoFit()) }
    var autoRecovery by remember { mutableStateOf(settingsStore.autoSourceRecoveryEnabled()) }
    var autoNext by remember { mutableStateOf(settingsStore.autoPlayNextEpisodeEnabled()) }
    var skipSegments by remember { mutableStateOf(settingsStore.skipSegmentsEnabled()) }
    var warnings by remember { mutableStateOf(settingsStore.contentWarningsEnabled()) }
    var chooser by remember { mutableStateOf<String?>(null) }

    FunctionalList {
        item {
            FunctionalToggleRow(
                "Resume Playback",
                "Ask to Resume or Start Over when saved progress exists.",
                resume,
                requester = firstRequester,
            ) {
                resume = it
                settingsStore.setResumePlaybackEnabled(it)
            }
        }
        item {
            FunctionalRow("Preferred Quality", "Preferred resolution used by Smart Source ranking.", quality.label) {
                chooser = "quality"
            }
        }
        item {
            FunctionalRow("Playback Speed", "Default playback speed used by the TV Player.", "${speed}x") {
                chooser = "speed"
            }
        }
        item {
            FunctionalRow("Video Fit", "Choose how video fills the television screen.", videoFit.label) {
                chooser = "fit"
            }
        }
        item {
            FunctionalToggleRow("Content Warnings", "Show a short parents-guide warning when supported metadata is available.", warnings) {
                warnings = it
                settingsStore.setContentWarningsEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Skip Intro & Ending", "Show skip controls when verified timestamps are available.", skipSegments) {
                skipSegments = it
                settingsStore.setSkipSegmentsEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Auto-play Next Episode", "Start the next episode after the player countdown.", autoNext) {
                autoNext = it
                settingsStore.setAutoPlayNextEpisodeEnabled(it)
            }
        }
        item {
            FunctionalToggleRow("Auto Source Recovery", "Try ranked alternatives after playback errors while keeping the timestamp.", autoRecovery) {
                autoRecovery = it
                settingsStore.setAutoSourceRecoveryEnabled(it)
            }
        }
        item {
            FunctionalInfoCard(
                "Player session controls",
                "In the Player, press Down from the bottom control row to open Playback Options for speed, video fit, Sleep Timer and warnings without adding another button to the main overlay.",
            )
        }
    }

    when (chooser) {
        "quality" -> FunctionalChoiceOverlay(
            title = "Preferred Quality",
            items = PreferredQuality.entries.map { option ->
                ChoiceItem(option.label, option == quality) {
                    quality = option
                    settingsStore.setPreferredQuality(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "speed" -> FunctionalChoiceOverlay(
            title = "Playback Speed",
            items = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { option ->
                ChoiceItem("${option}x", option == speed) {
                    speed = option
                    settingsStore.setPlayerPlaybackSpeed(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "fit" -> FunctionalChoiceOverlay(
            title = "Video Fit",
            items = PlayerVideoFit.entries.map { option ->
                ChoiceItem(option.label, option == videoFit) {
                    videoFit = option
                    settingsStore.setPlayerVideoFit(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
    }
}

@Composable
private fun FunctionalSubtitlesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var preferred by remember { mutableStateOf(settingsStore.preferredSubtitleLanguage()) }
    var secondary by remember { mutableStateOf(settingsStore.secondarySubtitleLanguage()) }
    var defaultOn by remember { mutableStateOf(settingsStore.subtitlesOnByDefault()) }
    var autoSelect by remember { mutableStateOf(settingsStore.autoSelectPreferredSubtitle()) }
    var embedded by remember { mutableStateOf(settingsStore.embeddedSubtitlePriority()) }
    var fontSize by remember { mutableIntStateOf(settingsStore.subtitleFontSizeSp()) }
    var bold by remember { mutableStateOf(settingsStore.subtitleBold()) }
    var textColor by remember { mutableIntStateOf(settingsStore.subtitleTextColor()) }
    var opacity by remember { mutableIntStateOf(settingsStore.subtitleTextOpacityPercent()) }
    var outline by remember { mutableStateOf(settingsStore.subtitleOutlineEnabled()) }
    var outlineColor by remember { mutableIntStateOf(settingsStore.subtitleOutlineColor()) }
    var bottomPadding by remember { mutableIntStateOf(settingsStore.subtitleBottomPaddingPercent()) }
    var chooser by remember { mutableStateOf<String?>(null) }

    FunctionalList {
        item {
            FunctionalRow("Preferred Language", "First subtitle language VUEO should prefer.", preferred.label, requester = firstRequester) {
                chooser = "preferred"
            }
        }
        item {
            FunctionalRow("Secondary Language", "Fallback language when the preferred language is unavailable.", secondary.label) {
                chooser = "secondary"
            }
        }
        item {
            FunctionalToggleRow("Subtitles On by Default", "Prefer showing subtitles when a suitable track exists.", defaultOn) {
                defaultOn = it
                settingsStore.setSubtitlesOnByDefault(it)
            }
        }
        item {
            FunctionalToggleRow("Auto Select Preferred Language", "Prioritise your preferred subtitle language automatically.", autoSelect) {
                autoSelect = it
                settingsStore.setAutoSelectPreferredSubtitle(it)
            }
        }
        item {
            FunctionalToggleRow("Embedded Subtitle Priority", "Prefer subtitle tracks included in the stream before external tracks.", embedded) {
                embedded = it
                settingsStore.setEmbeddedSubtitlePriority(it)
            }
        }
        item {
            FunctionalRow("Font Size", "Exact subtitle text size.", "${fontSize}sp") {
                chooser = "fontSize"
            }
        }
        item {
            FunctionalToggleRow("Bold Text", "Use a heavier subtitle typeface.", bold) {
                bold = it
                settingsStore.setSubtitleBold(it)
            }
        }
        item {
            FunctionalRow("Text Colour", "Subtitle foreground colour.", subtitleColourLabel(textColor)) {
                chooser = "textColor"
            }
        }
        item {
            FunctionalRow("Text Opacity", "Subtitle foreground opacity.", "$opacity%") {
                chooser = "opacity"
            }
        }
        item {
            FunctionalToggleRow("Outline", "Draw an outline around subtitle text for readability.", outline) {
                outline = it
                settingsStore.setSubtitleOutlineEnabled(it)
            }
        }
        item {
            FunctionalRow("Outline Colour", "Colour used by the subtitle outline.", subtitleColourLabel(outlineColor), enabled = outline) {
                chooser = "outlineColor"
            }
        }
        item {
            FunctionalRow("Bottom Offset", "Move subtitle text higher or lower from the bottom edge.", "$bottomPadding%") {
                chooser = "bottomPadding"
            }
        }
        item {
            FunctionalRow("Reset Subtitle Style", "Restore VUEO subtitle display defaults.", "Reset") {
                fontSize = 20
                bold = false
                textColor = 0xFFFFFFFF.toInt()
                opacity = 100
                outline = true
                outlineColor = 0xFF000000.toInt()
                bottomPadding = 22
                settingsStore.setSubtitleSize(SubtitleSize.MEDIUM)
                settingsStore.setSubtitleFontSizeSp(fontSize)
                settingsStore.setSubtitleBold(bold)
                settingsStore.setSubtitleTextColor(textColor)
                settingsStore.setSubtitleTextOpacityPercent(opacity)
                settingsStore.setSubtitleOutlineEnabled(outline)
                settingsStore.setSubtitleOutlineColor(outlineColor)
                settingsStore.setSubtitleBottomPaddingPercent(bottomPadding)
            }
        }
        item {
            FunctionalInfoCard("Subtitle Sync", "Per-title subtitle delay is available from the Player subtitle panel. Subtitle providers remain in Content Manager.")
        }
    }

    val colourChoices = listOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Yellow" to 0xFFFFEB3B.toInt(),
        "Cyan" to 0xFF80DEEA.toInt(),
        "Black" to 0xFF000000.toInt(),
    )

    when (chooser) {
        "preferred" -> FunctionalChoiceOverlay(
            title = "Preferred Language",
            items = SubtitleLanguage.entries.map { option ->
                ChoiceItem(option.label, option == preferred) {
                    preferred = option
                    settingsStore.setPreferredSubtitleLanguage(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "secondary" -> FunctionalChoiceOverlay(
            title = "Secondary Language",
            items = SubtitleLanguage.entries.map { option ->
                ChoiceItem(option.label, option == secondary) {
                    secondary = option
                    settingsStore.setSecondarySubtitleLanguage(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "fontSize" -> FunctionalChoiceOverlay(
            title = "Subtitle Font Size",
            items = listOf(14, 16, 18, 20, 22, 24, 28, 32, 36).map { option ->
                ChoiceItem("${option}sp", option == fontSize) {
                    fontSize = option
                    settingsStore.setSubtitleFontSizeSp(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "textColor" -> FunctionalChoiceOverlay(
            title = "Subtitle Text Colour",
            items = colourChoices.map { (label, value) ->
                ChoiceItem(label, value == textColor) {
                    textColor = value
                    settingsStore.setSubtitleTextColor(value)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "opacity" -> FunctionalChoiceOverlay(
            title = "Subtitle Text Opacity",
            items = listOf(40, 60, 80, 100).map { option ->
                ChoiceItem("$option%", option == opacity) {
                    opacity = option
                    settingsStore.setSubtitleTextOpacityPercent(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "outlineColor" -> FunctionalChoiceOverlay(
            title = "Subtitle Outline Colour",
            items = colourChoices.map { (label, value) ->
                ChoiceItem(label, value == outlineColor) {
                    outlineColor = value
                    settingsStore.setSubtitleOutlineColor(value)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
        "bottomPadding" -> FunctionalChoiceOverlay(
            title = "Subtitle Bottom Offset",
            items = listOf(8, 12, 16, 22, 28, 34, 40).map { option ->
                ChoiceItem("$option%", option == bottomPadding) {
                    bottomPadding = option
                    settingsStore.setSubtitleBottomPaddingPercent(option)
                    chooser = null
                }
            },
            onDismiss = { chooser = null },
        )
    }
}

private fun subtitleColourLabel(value: Int): String =
    when (value) {
        0xFFFFFFFF.toInt() -> "White"
        0xFFFFEB3B.toInt() -> "Yellow"
        0xFF80DEEA.toInt() -> "Cyan"
        0xFF000000.toInt() -> "Black"
        else -> "Custom"
    }

@Composable
private fun FunctionalSourcesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
) {
    var technical by remember { mutableStateOf(settingsStore.showSourceTechnicalDetails()) }
    FunctionalList {
        item {
            FunctionalToggleRow(
                "Technical Source Details",
                "Show codec, HDR and audio information on Source Picker cards.",
                technical,
                requester = firstRequester,
            ) {
                technical = it
                settingsStore.setShowSourceTechnicalDetails(it)
            }
        }
        item {
            FunctionalInfoCard("Smart Source Ranking", "VUEO ranks direct playability, resolution, HDR, codec information, provider health, response latency and preferred quality.")
        }
        item {
            FunctionalInfoCard("Progressive discovery", "Source Picker opens immediately and updates while providers continue searching.")
        }
    }
}

@Composable
private fun FunctionalAppearancePage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
    onAccentChanged: () -> Unit,
) {
    var accent by remember { mutableStateOf(settingsStore.appAccent()) }
    var chooseAccent by remember { mutableStateOf(false) }

    FunctionalList {
        item {
            FunctionalInfoCard(
                title = "Theme",
                text = "VUEO Dark stays fixed for comfortable living-room viewing.",
                requester = firstRequester,
            )
        }
        item {
            FunctionalRow(
                title = "Accent Colour",
                subtitle = "Choose the highlight colour used by VUEO status and selection accents.",
                value = accent.label,
                onClick = { chooseAccent = true },
            )
        }
        item {
            FunctionalInfoCard(
                title = "TV Focus",
                text = "D-pad focus stays white + scale for maximum visibility from a sofa, regardless of accent colour.",
            )
        }
        item {
            FunctionalInfoCard(
                title = "Cinematic identity",
                text = "Dark surfaces stay fixed while TV uses the same Shared Core accent choices as mobile.",
            )
        }
    }

    if (chooseAccent) {
        FunctionalChoiceOverlay(
            title = "Accent Colour",
            items = AppAccent.entries.map { option ->
                ChoiceItem(
                    label = option.label,
                    selected = option == accent,
                    onSelect = {
                        accent = option
                        settingsStore.setAppAccent(option)
                        onAccentChanged()
                        chooseAccent = false
                    },
                )
            },
            onDismiss = { chooseAccent = false },
        )
    }
}

@Composable
private fun FunctionalDataStoragePage(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    libraryStore: TvLibraryStore,
    firstRequester: FocusRequester,
    onProfileChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeCredentials by remember { mutableStateOf(settingsStore.includeCredentialsInBackup()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<DataClearAction?>(null) }
    var continueCount by remember { mutableIntStateOf(libraryStore.continueWatching().size) }
    var historyCount by remember { mutableIntStateOf(libraryStore.history().size) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                feedback = "Creating backup…"
                runCatching {
                    VueoBackupManager.exportToUri(context.applicationContext, uri, includeCredentials)
                }.onSuccess { summary ->
                    feedback = "Backup created • ${summary.valueCount} saved values${if (summary.includesCredentials) " • API keys included" else ""}"
                }.onFailure { error ->
                    feedback = error.message ?: "Unable to create backup."
                }
                busy = false
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    FunctionalList {
        feedback?.let { message -> item { FunctionalInfoCard(if (busy) "Working" else "Status", message, requester = if (busy) firstRequester else null) } }
        item { FunctionalSectionLabel("BACKUP & RESTORE") }
        item {
            FunctionalInfoCard(
                "What gets backed up",
                "Profiles, Content Manager configuration, provider preferences, Settings, My List, Continue Watching, Watch History and playback progress. Caches are rebuilt.",
                requester = if (feedback == null) firstRequester else null,
            )
        }
        item {
            FunctionalToggleRow("Include API Keys", "Off by default. Enable only for a personal migration file you control.", includeCredentials, enabled = !busy) {
                includeCredentials = it
                settingsStore.setIncludeCredentialsInBackup(it)
            }
        }
        item {
            FunctionalRow("Create Backup", if (includeCredentials) "Export VUEO data as JSON including configured API keys." else "Export VUEO data as JSON without API keys.", "Export", enabled = !busy) {
                exportLauncher.launch("vueo-tv-backup-${System.currentTimeMillis()}.json")
            }
        }
        item {
            FunctionalRow("Restore Backup", "Choose a VUEO JSON backup from Mobile or TV.", "Restore", enabled = !busy) {
                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        item { FunctionalSectionLabel("CACHE & HISTORY") }
        item {
            FunctionalRow("Catalog & Search Cache", "Clear Home, browse and search snapshots.", "Clear", enabled = !busy) {
                confirmAction = DataClearAction.CATALOG_CACHE
            }
        }
        item {
            FunctionalRow("Recent Source Cache", "Discard short-lived source and subtitle results.", "Clear", enabled = !busy) {
                confirmAction = DataClearAction.SOURCE_CACHE
            }
        }
        item {
            FunctionalRow("Continue Watching", "$continueCount unfinished ${if (continueCount == 1) "entry" else "entries"} for this profile.", if (continueCount > 0) "Clear" else "Empty", enabled = !busy && continueCount > 0) {
                confirmAction = DataClearAction.CONTINUE_WATCHING
            }
        }
        item {
            FunctionalRow("Watch History", "$historyCount saved ${if (historyCount == 1) "entry" else "entries"} for this profile.", if (historyCount > 0) "Clear" else "Empty", enabled = !busy && historyCount > 0) {
                confirmAction = DataClearAction.WATCH_HISTORY
            }
        }
        item { FunctionalSectionLabel("RESET") }
        item {
            FunctionalRow("Reset VUEO Data", "Return local TV configuration and Library data to a fresh state.", "Reset", enabled = !busy, danger = true) {
                confirmAction = DataClearAction.RESET
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        FunctionalConfirmOverlay(
            title = "Restore VUEO backup?",
            message = "Current VUEO profiles, configuration, Library and playback progress will be replaced by the selected backup.",
            actionLabel = "Restore",
            onDismiss = { if (!busy) pendingRestoreUri = null },
            onConfirm = {
                if (!busy) {
                    scope.launch {
                        busy = true
                        feedback = "Restoring backup…"
                        runCatching {
                            VueoBackupManager.restoreFromUri(context.applicationContext, uri)
                        }.onSuccess { summary ->
                            continueCount = libraryStore.continueWatching().size
                            historyCount = libraryStore.history().size
                            profileStore.ensureDefaultProfile()
                            onProfileChanged(profileStore.activeProfileId())
                            feedback = "Backup restored • ${summary.valueCount} values"
                            pendingRestoreUri = null
                        }.onFailure { error ->
                            feedback = error.message ?: "Unable to restore backup."
                        }
                        busy = false
                    }
                }
            },
        )
    }

    confirmAction?.let { action ->
        FunctionalConfirmOverlay(
            title = action.title,
            message = action.message,
            actionLabel = action.action,
            danger = action == DataClearAction.RESET,
            onDismiss = { if (!busy) confirmAction = null },
            onConfirm = {
                if (!busy) {
                    scope.launch {
                        busy = true
                        when (action) {
                            DataClearAction.CATALOG_CACHE -> {
                                clearTvCatalogAndSearchCaches(context)
                                feedback = "Catalog and search cache cleared."
                            }
                            DataClearAction.SOURCE_CACHE -> {
                                TvSourceEngine.clearAllCaches()
                                feedback = "Recent source cache cleared."
                            }
                            DataClearAction.CONTINUE_WATCHING -> {
                                libraryStore.clearContinueWatching()
                                continueCount = 0
                                feedback = "Continue Watching cleared."
                            }
                            DataClearAction.WATCH_HISTORY -> {
                                libraryStore.clearHistory()
                                historyCount = 0
                                feedback = "Watch History cleared."
                            }
                            DataClearAction.RESET -> {
                                resetTvUserData(context)
                                profileStore.ensureDefaultProfile()
                                onProfileChanged(profileStore.activeProfileId())
                                includeCredentials = false
                                continueCount = 0
                                historyCount = 0
                                feedback = "VUEO TV data reset to a fresh state."
                            }
                        }
                        confirmAction = null
                        busy = false
                    }
                }
            },
        )
    }
}

@Composable
private fun FunctionalUpdatesPage(
    settingsStore: SettingsStore,
    firstRequester: FocusRequester,
    onCheckForUpdates: ((String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    var automatic by remember { mutableStateOf(settingsStore.automaticUpdateChecksEnabled()) }
    var status by remember { mutableStateOf<String?>(null) }
    FunctionalList {
        item {
            FunctionalToggleRow(
                "Automatic Update Checks",
                "Check the VUEO TV release feed automatically when the app starts.",
                automatic,
                requester = firstRequester,
            ) {
                automatic = it
                settingsStore.setAutomaticUpdateChecksEnabled(it)
            }
        }
        item {
            FunctionalRow(
                "Check for Updates",
                "Current version ${version?.versionName ?: "Unknown"} • build ${versionCode(version)}",
                "Check Now",
            ) {
                status = "Checking for updates…"
                onCheckForUpdates { result -> status = result }
            }
        }
        status?.let { message -> item { FunctionalInfoCard("Update Status", message) } }
        item {
            FunctionalInfoCard("In-place updates", "VUEO TV updates install over the existing app and keep local profiles, settings and Library data.")
        }
    }
}

@Composable
private fun FunctionalAboutPage(firstRequester: FocusRequester) {
    val context = LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    FunctionalList {
        item {
            FunctionalInfoCard(
                "VUEO TV ${version?.versionName ?: "Unknown"}",
                "Build ${versionCode(version)} • package ${context.packageName}",
                requester = firstRequester,
            )
        }
        item {
            FunctionalInfoCard("Architecture", "Content Manager handles addons, repositories and provider plugins. Smart Source ranks discovered playback candidates independently of the interface.")
        }
        item {
            FunctionalInfoCard("Privacy", "Settings and API keys are stored locally. Backups exclude API keys unless you explicitly enable them.")
        }
        item {
            FunctionalInfoCard("TMDB Attribution", "This product uses the TMDB API but is not endorsed or certified by TMDB.")
        }
    }
}

private fun versionCode(info: android.content.pm.PackageInfo?): Long {
    if (info == null) return 0L
    @Suppress("DEPRECATION")
    return info.versionCode.toLong()
}

private suspend fun clearTvCatalogAndSearchCaches(context: Context) {
    withContext(Dispatchers.IO) {
        listOf("vueo_tv_home", "vueo_tv_browse", "vueo_tv_search").forEach { name ->
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}

private suspend fun resetTvUserData(context: Context) {
    VueoBackupManager.resetUserData(context.applicationContext)
    TvSourceEngine.clearAllCaches()
}

@Composable
private fun FunctionalList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun FunctionalRow(
    title: String,
    subtitle: String,
    value: String,
    requester: FocusRequester? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && enabled) 1.014f else 1f, label = "functionalRowScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .background(if (focused && enabled) Color.White.copy(alpha = 0.15f) else FunctionalRaised, RoundedCornerShape(18.dp))
            .border(if (focused && enabled) 2.dp else 1.dp, if (focused && enabled) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = if (enabled) Color.White else FunctionalMuted.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = FunctionalMuted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(18.dp))
        Box(
            modifier = Modifier
                .background(
                    when {
                        danger && enabled -> FunctionalDanger.copy(alpha = 0.10f)
                        focused && enabled -> Color.White.copy(alpha = 0.12f)
                        else -> Color.White.copy(alpha = 0.06f)
                    },
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Text(
                value,
                color = when {
                    danger && enabled -> FunctionalDanger
                    focused && enabled -> Color.White
                    enabled -> Color.White
                    else -> FunctionalMuted.copy(alpha = 0.45f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FunctionalToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    requester: FocusRequester? = null,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && enabled) 1.014f else 1f, label = "functionalToggleScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled) { onToggle(!checked) }
            .focusable(enabled)
            .background(if (focused && enabled) Color.White.copy(alpha = 0.15f) else FunctionalRaised, RoundedCornerShape(18.dp))
            .border(if (focused && enabled) 2.dp else 1.dp, if (focused && enabled) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = if (enabled) Color.White else FunctionalMuted.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = FunctionalMuted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(18.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun FunctionalInfoCard(
    title: String,
    text: String,
    requester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(requester != null)
            .background(if (focused) Color.White.copy(alpha = 0.12f) else FunctionalRaised, RoundedCornerShape(18.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(text, color = FunctionalMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun FunctionalSectionLabel(text: String) {
    Text(
        text = text,
        color = FunctionalMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun FunctionalApiKeyField(
    title: String,
    value: String,
    requester: FocusRequester? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(title) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester) else Modifier),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = FunctionalMuted,
            cursorColor = Color.White,
        ),
    )
}

@Composable
private fun FunctionalChoiceOverlay(
    title: String,
    items: List<ChoiceItem>,
    onDismiss: () -> Unit,
) {
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(items.size) {
        delay(70)
        val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
        requesters.getOrNull(selected)?.let { runCatching { it.requestFocus() } }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .background(FunctionalRaised, RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            items.forEachIndexed { index, item ->
                var focused by remember(index) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .focusRequester(requesters[index])
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(onClick = item.onSelect)
                        .focusable()
                        .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(18.dp).height(18.dp).background(if (item.selected) Color.White else Color.Transparent, CircleShape).border(2.dp, Color.White.copy(alpha = if (item.selected) 1f else 0.35f), CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(item.label, color = Color.White, fontSize = 14.sp, fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium)
                }
                if (index < items.lastIndex) Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun FunctionalConfirmOverlay(
    title: String,
    message: String,
    actionLabel: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelRequester = remember { FocusRequester() }
    val confirmRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        delay(70)
        runCatching { cancelRequester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(FunctionalRaised, RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(26.dp),
        ) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(message, color = FunctionalMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FunctionalDialogButton("Cancel", cancelRequester, onRight = { confirmRequester.requestFocus() }, onClick = onDismiss)
                FunctionalDialogButton(actionLabel, confirmRequester, danger = danger, onLeft = { cancelRequester.requestFocus() }, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun FunctionalDialogButton(
    text: String,
    requester: FocusRequester,
    danger: Boolean = false,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(48.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
                    Key.DirectionRight -> onRight?.let { it(); true } ?: false
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) FunctionalDanger else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
