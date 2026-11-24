package com.vueo.tv.settings

import android.content.Context
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.ProviderCodeStore
import com.vueo.shared.core.plugin.providerHealthSortKey
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.source.SourceDiscoveryCache
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.shared.core.storage.SubtitleVisibility
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.tv.BuildConfig
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.profile.TvPinEntryOverlay
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvSidebarPreferences
import com.vueo.tv.ui.TvSidebarStyle
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdateRelease
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class TvSettingsPage {
    PROFILE,
    VUEO_SYNC,
    PERSONALIZATION,
    CONTENT_MANAGER,
    CONTENT_ADDONS,
    CONTENT_PROVIDERS,
    CONTENT_PROVIDER_HEALTH,
    CONTENT_CATALOGS,
    CONTENT_COLLECTIONS,
    ENHANCEMENTS,
    ENHANCEMENT_TRAKT,
    ENHANCEMENT_TMDB,
    ENHANCEMENT_MDBLIST,
    ENHANCEMENT_GEMINI,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DEVELOPER_OPTIONS,
    MOTION,
    DATA_STORAGE,
    DIAGNOSTICS,
    ABOUT,
}

private data class TvSettingsRootDestination(
    val page: TvSettingsPage,
    val id: String,
    val title: String,
    val section: String? = null,
)

private val TvSettingsRootDestinations = listOf(
    TvSettingsRootDestination(TvSettingsPage.VUEO_SYNC, "vueo-sync", "Account", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PROFILE, "profile", "Profile", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.CONTENT_MANAGER, "content-manager", "Content Manager", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.ENHANCEMENTS, "enhancements", "Enhancements", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PLAYBACK, "playback", "Playback", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SUBTITLES, "subtitles", "Subtitles", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SOURCES, "sources", "Sources", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.APPEARANCE, "appearance", "Appearance", "APP"),
    TvSettingsRootDestination(TvSettingsPage.DATA_STORAGE, "data-storage", "Data & Storage", "APP"),
    TvSettingsRootDestination(TvSettingsPage.ABOUT, "about", "About VUEO", "APP"),
    TvSettingsRootDestination(TvSettingsPage.DEVELOPER_OPTIONS, "developer-options", "Developer options", "APP"),
)

private const val DEVELOPER_OPTIONS_PIN = "developer_options_pin"
private const val DEVELOPER_OPTIONS_PIN_SKIPPED = "developer_options_pin_skipped"

private enum class DeveloperPinStep { UNLOCK, VERIFY_RESET, SET_NEW, CONFIRM_NEW }
private const val HIDDEN_SETTINGS_CATEGORIES = "developer_hidden_settings_categories"

private val HideableSettingsPages = setOf(
    TvSettingsPage.PROFILE,
    TvSettingsPage.CONTENT_MANAGER,
    TvSettingsPage.ENHANCEMENTS,
    TvSettingsPage.DATA_STORAGE,
    TvSettingsPage.VUEO_SYNC,
)

private fun TvSettingsPage.rootPage(): TvSettingsPage = when (this) {
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_PROVIDER_HEALTH,
    TvSettingsPage.CONTENT_COLLECTIONS,
    TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
    TvSettingsPage.ENHANCEMENT_TRAKT,
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST,
    TvSettingsPage.ENHANCEMENT_GEMINI,
    TvSettingsPage.PERSONALIZATION -> TvSettingsPage.ENHANCEMENTS
    TvSettingsPage.MOTION, TvSettingsPage.DIAGNOSTICS -> TvSettingsPage.DEVELOPER_OPTIONS
    else -> this
}

private fun TvSettingsPage.hasPanelParent(): Boolean = when (this) {
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_PROVIDER_HEALTH,
    TvSettingsPage.CONTENT_COLLECTIONS,
    TvSettingsPage.CONTENT_CATALOGS,
    TvSettingsPage.ENHANCEMENT_TRAKT,
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST,
    TvSettingsPage.ENHANCEMENT_GEMINI,
    TvSettingsPage.PERSONALIZATION, TvSettingsPage.MOTION, TvSettingsPage.DIAGNOSTICS -> true
    else -> false
}

@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    active: Boolean = true,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    onManageProfiles: () -> Unit,
    dataVersion: Int,
    onDataChanged: () -> Unit = {},
    onResetComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences("vueo_tv_browse", Context.MODE_PRIVATE)
    }
    var developerOptionsEnabled by remember { mutableStateOf(false) }
    var pinRecord by remember(preferences, dataVersion) {
        mutableStateOf(preferences.getString(DEVELOPER_OPTIONS_PIN, null))
    }
    var pinSkipped by remember(preferences, dataVersion) {
        mutableStateOf(preferences.getBoolean(DEVELOPER_OPTIONS_PIN_SKIPPED, false))
    }
    var showPinChoice by remember { mutableStateOf(false) }
    var pinStep by remember { mutableStateOf<DeveloperPinStep?>(null) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var unlockNotice by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinAttempt by remember { mutableIntStateOf(0) }
    var pinJob by remember { mutableStateOf<Job?>(null) }
    val pinScope = rememberCoroutineScope()

    fun closePinEntry() {
        pinJob?.cancel()
        pinJob = null
        showPinChoice = false
        pinStep = null
        firstPin = null
        pinError = null
    }

    var hiddenCategories by remember(preferences, dataVersion) {
        mutableStateOf(preferences.getStringSet(HIDDEN_SETTINGS_CATEGORIES, emptySet()).orEmpty().toSet())
    }
    val visibleRoots = remember(developerOptionsEnabled, hiddenCategories) {
        TvSettingsRootDestinations.filter { destination ->
            if (destination.page == TvSettingsPage.DEVELOPER_OPTIONS) {
                developerOptionsEnabled
            } else {
                destination.page !in HideableSettingsPages || destination.id !in hiddenCategories
            }
        }
    }
    var page by remember {
        mutableStateOf(visibleRoots.firstOrNull { it.page == TvSettingsPage.PROFILE }?.page ?: visibleRoots.first().page)
    }
    var panelAutoFocusToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(active) {
        if (!active) {
            closePinEntry()
            if (page.rootPage() == TvSettingsPage.DEVELOPER_OPTIONS) page = TvSettingsPage.ABOUT
            developerOptionsEnabled = false
        }
    }

    val selectedRoot = visibleRoots.firstOrNull { it.page == page.rootPage() } ?: visibleRoots.first()
    val visiblePage = if (page.rootPage() == selectedRoot.page) page else selectedRoot.page

    LaunchedEffect(active, visiblePage, unlockNotice) {
        if (active && visiblePage == TvSettingsPage.ABOUT && unlockNotice != null) {
            delay(1600)
        }
        unlockNotice = null
    }

    fun setCategoryHidden(id: String, hidden: Boolean) {
        val updated = if (hidden) hiddenCategories + id else hiddenCategories - id
        hiddenCategories = updated
        preferences.edit().putStringSet(HIDDEN_SETTINGS_CATEGORIES, updated).apply()
    }

    fun openPanel(next: TvSettingsPage) {
        page = next
        panelAutoFocusToken += 1
    }

    fun openDeveloperOptions() {
        closePinEntry()
        developerOptionsEnabled = true
        openPanel(TvSettingsPage.DEVELOPER_OPTIONS)
    }

    fun requestDeveloperOptions() {
        when {
            pinRecord != null -> pinStep = DeveloperPinStep.UNLOCK
            pinSkipped -> openDeveloperOptions()
            else -> showPinChoice = true
        }
    }

    fun advancePin(step: DeveloperPinStep, error: String? = null) {
        pinStep = step
        pinError = error
        pinAttempt += 1
    }

    fun backPanel() {
        page = when (visiblePage) {
            TvSettingsPage.CONTENT_PROVIDER_HEALTH -> TvSettingsPage.CONTENT_PROVIDERS
            TvSettingsPage.CONTENT_ADDONS,
            TvSettingsPage.CONTENT_PROVIDERS,
            TvSettingsPage.CONTENT_COLLECTIONS,
            TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
            TvSettingsPage.ENHANCEMENT_TRAKT,
            TvSettingsPage.ENHANCEMENT_TMDB,
            TvSettingsPage.ENHANCEMENT_MDBLIST,
            TvSettingsPage.ENHANCEMENT_GEMINI,
            TvSettingsPage.PERSONALIZATION -> TvSettingsPage.ENHANCEMENTS
            TvSettingsPage.MOTION, TvSettingsPage.DIAGNOSTICS -> TvSettingsPage.DEVELOPER_OPTIONS
            else -> visiblePage
        }
        panelAutoFocusToken += 1
    }

    TvSettingsMasterDetailShell(
        active = active,
        categories = visibleRoots.map { TvSettingsNavItem(it.id, it.title, it.section) },
        selectedCategoryId = selectedRoot.id,
        panelKey = visiblePage.name,
        panelAutoFocusToken = panelAutoFocusToken,
        panelHasBack = visiblePage.hasPanelParent(),
        panelUsesSharedRows = visiblePage != TvSettingsPage.CONTENT_COLLECTIONS,
        panelEntryFocusId = TV_ABOUT_UPDATE_ROW_ID.takeIf { visiblePage == TvSettingsPage.ABOUT },
        onCategoryOpened = { id ->
            visibleRoots.firstOrNull { it.id == id }?.let { openPanel(it.page) }
        },
        onPanelBack = ::backPanel,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
    ) {
        when (visiblePage) {
            TvSettingsPage.VUEO_SYNC -> TvVueoSyncSettings(onNavigate, onProfile, onBack)
            TvSettingsPage.PROFILE -> TvProfileSettings(
                runtime = runtime,
                onOpenDna = onProfile,
                onOpenProfiles = onManageProfiles,
                dataVersion = dataVersion,
            )
            TvSettingsPage.PERSONALIZATION -> TvPersonalizationSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.CONTENT_MANAGER -> TvContentManagerHub(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.CONTENT_ADDONS -> TvAddonSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.CONTENT_PROVIDERS -> TvProviderSettings(
                runtime = runtime,
                onNavigate = onNavigate,
                onProfile = onProfile,
                onDataChanged = onDataChanged,
                onOpenProviderHealth = {
                    openPanel(TvSettingsPage.CONTENT_PROVIDER_HEALTH)
                },
                onBack = ::backPanel,
            )
            TvSettingsPage.CONTENT_PROVIDER_HEALTH -> TvProviderHealthOverview(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.CONTENT_COLLECTIONS -> com.vueo.shared.collections.ui.CollectionManagerScreen(
                engine = runtime.engine, profileId = runtime.profileStore.activeProfileId(), onBack = ::backPanel,
                image = { url, label, modifier -> com.vueo.tv.ui.TvNetworkImage(url, label, modifier) },
            )
            TvSettingsPage.CONTENT_CATALOGS -> TvCatalogSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.ENHANCEMENTS -> TvEnhancementSettings(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.ENHANCEMENT_TRAKT -> TvTraktEnhancementSettings(
                onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.ENHANCEMENT_TMDB -> TvTmdbEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.ENHANCEMENT_MDBLIST -> TvMdblistEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.ENHANCEMENT_GEMINI -> TvGeminiEnhancementSettings(
                onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.PLAYBACK -> TvPlaybackSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.SUBTITLES -> TvSubtitleSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.SOURCES -> TvSourceSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.APPEARANCE -> TvAppearanceSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.DEVELOPER_OPTIONS -> {
                val entries = buildList {
                    add(TvSettingsEntry(
                        id = "developer-options-enabled",
                        title = "Developer options",
                        subtitle = "Turn off to hide this menu. Press OK 7 times on the VUEO logo in About to open it again.",
                        onActivate = {
                            developerOptionsEnabled = false
                            openPanel(TvSettingsPage.ABOUT)
                        },
                        switchChecked = true,
                    ))
                    add(TvSettingsEntry(
                        id = "developer-options-pin",
                        title = if (pinRecord == null) "Set PIN" else "Reset PIN",
                        subtitle = if (pinRecord == null) {
                            "Require a PIN to open Developer options."
                        } else {
                            "Enter your current PIN to replace it."
                        },
                        onActivate = {
                            pinStep = if (pinRecord == null) DeveloperPinStep.SET_NEW else DeveloperPinStep.VERIFY_RESET
                        },
                    ))
                    TvSettingsRootDestinations.filter { it.page in HideableSettingsPages }.forEach { destination ->
                        val hidden = destination.id in hiddenCategories
                        add(TvSettingsEntry(
                            id = "hide-${destination.id}",
                            title = "Hide ${destination.title}",
                            subtitle = "Hide this category from Settings.",
                            section = "SETTINGS VISIBILITY",
                            onActivate = { setCategoryHidden(destination.id, !hidden) },
                            switchChecked = hidden,
                        ))
                    }
                    add(TvSettingsEntry(
                        id = "motion-tuning",
                        title = "Motion Tuning",
                        subtitle = "Adjust TV animations and transitions.",
                        section = "TOOLS",
                        onActivate = { openPanel(TvSettingsPage.MOTION) },
                    ))
                    add(TvSettingsEntry(
                        id = "diagnostics",
                        title = "Diagnostics",
                        subtitle = "View crash, performance and provider diagnostics.",
                        section = "TOOLS",
                        onActivate = { openPanel(TvSettingsPage.DIAGNOSTICS) },
                    ))
                }
                TvSettingsListScreen(
                    "Developer options", "Customize what appears in Settings and access TV tools.",
                    entries, onNavigate, onProfile, onBack,
                )
            }
            TvSettingsPage.MOTION -> TvMotionSettings(onNavigate, onProfile, ::backPanel)
            TvSettingsPage.DATA_STORAGE -> TvDataStorageSettings(
                runtime, onNavigate, onProfile, onDataChanged, onResetComplete, onBack
            )
            TvSettingsPage.DIAGNOSTICS -> TvDiagnosticsSettings(
                onNavigate = onNavigate,
                onProfile = onProfile,
                onBack = ::backPanel,
            )
            TvSettingsPage.ABOUT -> TvAboutSettings(
                onNavigate = onNavigate,
                onProfile = onProfile,
                onBack = onBack,
                developerOptionsEnabled = developerOptionsEnabled,
                onUnlockDeveloperOptions = ::requestDeveloperOptions,
                unlockNotice = unlockNotice,
                onUnlockNotice = { unlockNotice = it },
            )
        }
    }

    if (active && showPinChoice) {
        TvDeveloperPinChoice(
            onCancel = ::closePinEntry,
            onSkip = {
                preferences.edit().putBoolean(DEVELOPER_OPTIONS_PIN_SKIPPED, true).apply()
                pinSkipped = true
                openDeveloperOptions()
            },
            onSetPin = {
                showPinChoice = false
                pinStep = DeveloperPinStep.SET_NEW
            },
        )
    }

    val step = pinStep
    if (active && step != null) {
        key(step, pinAttempt) {
            TvPinEntryOverlay(
                title = when (step) {
                    DeveloperPinStep.UNLOCK -> "Unlock Developer options"
                    DeveloperPinStep.VERIFY_RESET -> "Reset Developer PIN"
                    DeveloperPinStep.SET_NEW -> "Set Developer PIN"
                    DeveloperPinStep.CONFIRM_NEW -> "Confirm Developer PIN"
                },
                subtitle = when (step) {
                    DeveloperPinStep.UNLOCK -> "Enter your 4-digit PIN."
                    DeveloperPinStep.VERIFY_RESET -> "Enter your current PIN."
                    DeveloperPinStep.SET_NEW -> "Choose a 4-digit PIN."
                    DeveloperPinStep.CONFIRM_NEW -> "Enter the same PIN again."
                },
                errorText = pinError,
                onComplete = completePin@{ pin ->
                    if (pinJob != null) return@completePin
                    when (step) {
                        DeveloperPinStep.SET_NEW -> {
                            firstPin = pin
                            advancePin(DeveloperPinStep.CONFIRM_NEW)
                        }
                        DeveloperPinStep.CONFIRM_NEW -> {
                            if (firstPin != pin) {
                                firstPin = null
                                advancePin(DeveloperPinStep.SET_NEW, "PINs do not match. Try again.")
                            } else {
                                pinJob = pinScope.launch {
                                    val record = withContext(Dispatchers.Default) {
                                        runCatching { createDeveloperPinRecord(pin) }.getOrNull()
                                    }
                                    pinJob = null
                                    firstPin = null
                                    if (record == null) {
                                        advancePin(DeveloperPinStep.SET_NEW, "Could not set PIN. Try again.")
                                    } else {
                                        preferences.edit().putString(DEVELOPER_OPTIONS_PIN, record).apply()
                                        pinRecord = record
                                        openDeveloperOptions()
                                    }
                                }
                            }
                        }
                        DeveloperPinStep.UNLOCK, DeveloperPinStep.VERIFY_RESET -> {
                            val record = pinRecord
                            pinJob = pinScope.launch {
                                val verified = withContext(Dispatchers.Default) {
                                    record != null && runCatching { verifyDeveloperPin(pin, record) }.getOrDefault(false)
                                }
                                pinJob = null
                                when {
                                    !verified -> advancePin(step, "Incorrect PIN.")
                                    step == DeveloperPinStep.VERIFY_RESET -> advancePin(DeveloperPinStep.SET_NEW)
                                    else -> openDeveloperOptions()
                                }
                            }
                        }
                    }
                },
                onCancel = ::closePinEntry,
            )
        }
    }
}

private fun createDeveloperPinRecord(pin: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val hash = deriveDeveloperPin(pin, salt)
    return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash, Base64.NO_WRAP)
}

private fun verifyDeveloperPin(pin: String, record: String): Boolean {
    val parts = record.split(':', limit = 2)
    if (parts.size != 2) return false
    val salt = Base64.decode(parts[0], Base64.NO_WRAP)
    val hash = Base64.decode(parts[1], Base64.NO_WRAP)
    return MessageDigest.isEqual(hash, deriveDeveloperPin(pin, salt))
}

private fun deriveDeveloperPin(pin: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}
