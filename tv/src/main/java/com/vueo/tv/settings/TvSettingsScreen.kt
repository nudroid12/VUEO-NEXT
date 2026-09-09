package com.vueo.tv.settings

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.ProviderCodeStore
import com.vueo.shared.core.plugin.ProviderHealthStatus
import com.vueo.shared.core.plugin.providerHealthSortKey
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.source.SourceDiscoveryCache
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.tv.BuildConfig
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TvSettingsPage {
    PROFILE,
    PROFILE_CHOOSER,
    PERSONALIZATION,
    CONTENT_MANAGER,
    CONTENT_ADDONS,
    CONTENT_PROVIDERS,
    CONTENT_CATALOGS,
    ENHANCEMENTS,
    ENHANCEMENT_TMDB,
    ENHANCEMENT_MDBLIST,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    UPDATES,
    ABOUT,
}

private data class TvSettingsRootDestination(
    val page: TvSettingsPage,
    val id: String,
    val title: String,
    val section: String? = null,
)

private val TvSettingsRootDestinations = listOf(
    TvSettingsRootDestination(TvSettingsPage.PROFILE, "profile", "Profile", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PERSONALIZATION, "personalization", "Personalization", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.CONTENT_MANAGER, "content-manager", "Content Manager", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.ENHANCEMENTS, "enhancements", "Enhancements", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PLAYBACK, "playback", "Playback", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SUBTITLES, "subtitles", "Subtitles", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SOURCES, "sources", "Sources", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.APPEARANCE, "appearance", "Appearance", "APP"),
    TvSettingsRootDestination(TvSettingsPage.DATA_STORAGE, "data-storage", "Data & Storage", "APP"),
    TvSettingsRootDestination(TvSettingsPage.UPDATES, "updates", "Updates", "APP"),
    TvSettingsRootDestination(TvSettingsPage.ABOUT, "about", "About VUEO", "APP"),
)

private fun TvSettingsPage.rootPage(): TvSettingsPage = when (this) {
    TvSettingsPage.PROFILE_CHOOSER -> TvSettingsPage.PROFILE
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST -> TvSettingsPage.ENHANCEMENTS
    else -> this
}

private fun TvSettingsPage.hasPanelParent(): Boolean = when (this) {
    TvSettingsPage.PROFILE_CHOOSER,
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_CATALOGS,
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST -> true
    else -> false
}

@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    onDataChanged: () -> Unit = {},
) {
    var page by remember { mutableStateOf(TvSettingsPage.PROFILE) }
    var panelAutoFocusToken by remember { mutableIntStateOf(0) }

    val rootPage = page.rootPage()
    val selectedRoot = TvSettingsRootDestinations.firstOrNull { it.page == rootPage }
        ?: TvSettingsRootDestinations.first()

    fun openPanel(next: TvSettingsPage) {
        page = next
        panelAutoFocusToken += 1
    }

    fun backPanel() {
        page = when (page) {
            TvSettingsPage.PROFILE_CHOOSER -> TvSettingsPage.PROFILE
            TvSettingsPage.CONTENT_ADDONS,
            TvSettingsPage.CONTENT_PROVIDERS,
            TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
            TvSettingsPage.ENHANCEMENT_TMDB,
            TvSettingsPage.ENHANCEMENT_MDBLIST -> TvSettingsPage.ENHANCEMENTS
            else -> page
        }
        panelAutoFocusToken += 1
    }

    TvSettingsMasterDetailShell(
        categories = TvSettingsRootDestinations.map { TvSettingsNavItem(it.id, it.title, it.section) },
        selectedCategoryId = selectedRoot.id,
        panelKey = page.name,
        panelAutoFocusToken = panelAutoFocusToken,
        panelHasBack = page.hasPanelParent(),
        onCategorySelected = { id ->
            TvSettingsRootDestinations.firstOrNull { it.id == id }?.let { destination ->
                if (page != destination.page) page = destination.page
            }
        },
        onPanelBack = ::backPanel,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
    ) {
        when (page) {
            TvSettingsPage.PROFILE -> TvProfileSettings(
                runtime = runtime,
                onOpenDna = onProfile,
                onOpenProfiles = { openPanel(TvSettingsPage.PROFILE_CHOOSER) },
            )
            TvSettingsPage.PROFILE_CHOOSER -> TvProfileChooserSettings(
                runtime = runtime,
                onNavigate = onNavigate,
                onProfile = onProfile,
                onDataChanged = onDataChanged,
                onProfileSelected = { openPanel(TvSettingsPage.PROFILE) },
                onBack = ::backPanel,
            )
            TvSettingsPage.PERSONALIZATION -> TvPersonalizationSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.CONTENT_MANAGER -> TvContentManagerHub(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.CONTENT_ADDONS -> TvAddonSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.CONTENT_PROVIDERS -> TvProviderSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.CONTENT_CATALOGS -> TvCatalogSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.ENHANCEMENTS -> TvEnhancementSettings(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.ENHANCEMENT_TMDB -> TvTmdbEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.ENHANCEMENT_MDBLIST -> TvMdblistEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
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
            TvSettingsPage.DATA_STORAGE -> TvDataStorageSettings(
                runtime, onNavigate, onProfile, onDataChanged, onBack
            )
            TvSettingsPage.UPDATES -> TvUpdatesSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.ABOUT -> TvAboutSettings(
                onNavigate, onProfile, onBack
            )
        }
    }
}

@Composable
private fun TvProfileSettings(
    runtime: TvRuntime,
    onOpenDna: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val profile = runtime.profileStore.activeProfile()
    val dnaEnabled = runtime.dnaPreferences.userDnaEnabled(profile.id)
    val dnaSnapshot = if (dnaEnabled) runtime.dnaEngine.build() else null
    val myListCount = runtime.libraryStore.watchlist().size
    val watchedTitlesCount = runtime.libraryStore
        .history()
        .filter { it.positionMs > 5_000L }
        .map { "${it.media.type}:${it.media.id}" }
        .distinct()
        .size
    val viewingClass = tvViewingClass(watchedTitlesCount)
    val dnaClass = when {
        !dnaEnabled -> "DNA Off"
        dnaSnapshot == null -> "Finding Your Taste"
        else -> tvDnaClass(dnaSnapshot)
    }
    val tastePreview = dnaSnapshot
        ?.topGenres
        ?.take(3)
        ?.joinToString(" • ") { "${it.name} ${it.percent}%" }
        .orEmpty()
        .ifBlank {
            if (dnaEnabled) "Keep watching to shape your DNA class."
            else "Enable User DNA in Personalization."
        }

    TvSettingsProfilePanel(
        profileName = profile.name,
        profileSubtitle = "$viewingClass • $dnaClass",
        avatarDrawableRes = ProfileAvatarCatalog.drawableRes(profile.avatar),
        myListCount = myListCount,
        watchedCount = watchedTitlesCount,
        dnaValue = dnaSnapshot?.let { "${it.confidencePercent}%" } ?: "Off",
        tastePreview = tastePreview,
        onOpenDna = onOpenDna,
        onSwitchProfiles = onOpenProfiles,
    )
}

private fun tvViewingClass(watchedTitles: Int): String = when {
    watchedTitles < 10 -> "Baby VUEO"
    watchedTitles < 30 -> "Explorer"
    watchedTitles < 75 -> "Binger"
    watchedTitles < 150 -> "Cinephile"
    watchedTitles < 300 -> "Screen Veteran"
    else -> "VUEO Legend"
}

private fun tvDnaClass(snapshot: UserDnaSnapshot): String {
    if (snapshot.confidencePercent < 20 || snapshot.topGenres.isEmpty()) {
        return "Finding Your Taste"
    }

    val genres = snapshot.topGenres.associate {
        it.name.lowercase(Locale.US) to it.percent
    }
    fun score(vararg names: String): Int = names.sumOf {
        genres[it.lowercase(Locale.US)] ?: 0
    }

    val topGenrePercent = snapshot.topGenres.firstOrNull()?.percent ?: 0
    if (snapshot.topGenres.size >= 5 && topGenrePercent <= 30) {
        return "The Explorer"
    }

    val classes = listOf(
        "The Adventurer" to score("Action", "Adventure", "Fantasy"),
        "The Detective" to score("Crime", "Mystery", "Thriller"),
        "The Thrill Seeker" to score("Horror", "Thriller", "Action"),
        "The Romantic" to score("Romance", "Drama"),
        "The Dreamer" to score("Science Fiction", "Fantasy", "Animation"),
        "The Mood Lifter" to score("Comedy", "Family", "Animation"),
        "The Story Hunter" to score("Drama", "History", "Documentary"),
    )
    val best = classes.maxByOrNull { it.second }
    if (best != null && best.second >= 20) return best.first

    return when (snapshot.topGenres.firstOrNull()?.name?.lowercase(Locale.US)) {
        "crime", "mystery" -> "The Detective"
        "horror", "thriller" -> "The Thrill Seeker"
        "romance" -> "The Romantic"
        "science fiction", "fantasy" -> "The Dreamer"
        "comedy" -> "The Mood Lifter"
        "action", "adventure" -> "The Adventurer"
        else -> "The Story Hunter"
    }
}

@Composable
private fun TvProfileChooserSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onProfileSelected: () -> Unit,
    onBack: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { runtime.profileStore.profiles() }
    val activeProfileId = remember(revision) { runtime.profileStore.activeProfileId() }
    var askStartup by remember { mutableStateOf(runtime.profileStore.askWhoIsWatchingOnStartup()) }
    var lockedProfileId by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    val lockedProfile = lockedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    if (lockedProfile != null) {
        androidx.compose.runtime.key(lockedProfile.id, pinResetToken) {
            com.vueo.tv.profile.TvPinEntryOverlay(
                title = "Unlock ${lockedProfile.name}",
                subtitle = "Enter the 4-digit profile PIN",
                errorText = pinError,
                onComplete = { pin ->
                    if (runtime.profileStore.verifyProfilePin(lockedProfile.id, pin)) {
                        pinError = null
                        lockedProfileId = null
                        if (runtime.profileStore.setActiveProfile(lockedProfile.id)) {
                            revision += 1
                            onDataChanged()
                            onProfileSelected()
                        }
                    } else {
                        pinError = "Incorrect PIN"
                        pinResetToken += 1
                    }
                },
                onCancel = {
                    pinError = null
                    lockedProfileId = null
                },
            )
        }
    }

    val entries = buildList {
        add(
            toggleEntry(
                id = "startup-picker",
                title = "Ask who’s watching on startup",
                subtitle = "Show profile selection before Home opens.",
                checked = askStartup,
            ) {
                askStartup = it
                runtime.profileStore.setAskWhoIsWatchingOnStartup(it)
            }.copy(section = "PROFILE STARTUP", icon = Icons.Default.AccountCircle)
        )
        profiles.forEach { profile ->
            val active = profile.id == activeProfileId
            val locked = runtime.profileStore.hasProfilePin(profile.id)
            add(
                TvSettingsEntry(
                    id = "profile-${profile.id}",
                    title = profile.name,
                    subtitle = buildString {
                        append(if (profile.isKids) "Kids profile" else "Standard profile")
                        if (locked) append(" • PIN protected")
                    },
                    value = if (active) "Active" else "Switch",
                    onActivate = {
                        if (active) {
                            onProfileSelected()
                        } else if (locked) {
                            pinError = null
                            lockedProfileId = profile.id
                        } else if (runtime.profileStore.setActiveProfile(profile.id)) {
                            revision += 1
                            onDataChanged()
                            onProfileSelected()
                        }
                    },
                    section = "PROFILES",
                    icon = Icons.Default.AccountCircle,
                )
            )
        }
    }

    TvSettingsListScreen(
        title = "Profiles",
        subtitle = "Switch the active profile without leaving Settings.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Profile",
    )
}

@Composable
private fun TvPersonalizationSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val profile = runtime.profileStore.activeProfile()
    val dna = remember { UserDnaPreferences(context.applicationContext) }
    var dnaEnabled by remember(profile.id) { mutableStateOf(dna.userDnaEnabled(profile.id)) }
    var showMatch by remember(profile.id) { mutableStateOf(dna.showDnaMatchEnabled(profile.id)) }
    var recommendations by remember(profile.id) { mutableStateOf(dna.personalizedRecommendationsEnabled(profile.id)) }
    val entries = listOf(
        toggleEntry("dna", "User DNA", "Build a local taste profile from History, playback progress and My List.", dnaEnabled) {
            dnaEnabled = it
            dna.setUserDnaEnabled(profile.id, it)
        }.copy(section = "USER DNA"),
        toggleEntry("dna-match", "Show DNA Match", "Show local taste-match information on supported titles.", showMatch, enabled = dnaEnabled) {
            showMatch = it
            dna.setShowDnaMatchEnabled(profile.id, it)
        }.copy(section = "USER DNA"),
        toggleEntry("dna-recs", "Personalized Recommendations", "Use User DNA for For You and Because You Watched recommendations.", recommendations, enabled = dnaEnabled) {
            recommendations = it
            dna.setPersonalizedRecommendationsEnabled(profile.id, it)
        }.copy(section = "USER DNA"),
    )

    TvSettingsListScreen(
        "Personalization",
        "Local, per-profile controls for how VUEO adapts to you. These settings stay on this device.",
        entries,
        onNavigate,
        onProfile,
        onBack,
        topLabel = null,
    )
}

@Composable
private fun TvContentManagerHub(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpen: (TvSettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val health = healthStore.records()
    val onlineCount = health.count {
        it.status == ProviderHealthStatus.ONLINE || it.status == ProviderHealthStatus.SLOW
    }
    val slowCount = health.count {
        it.status == ProviderHealthStatus.SLOW || it.status == ProviderHealthStatus.TIMEOUT
    }
    val failedCount = health.count {
        it.status == ProviderHealthStatus.FAILED ||
            it.status == ProviderHealthStatus.BLOCKED ||
            it.status == ProviderHealthStatus.UNAVAILABLE
    }
    val addons = runtime.engine.stremioAddons()
    val installedAddons = addons.size
    val repositoryCount = runtime.pluginStore.repositories().size
    val providerCount = runtime.pluginStore.totalProviderCount()
    val catalogCount = addons.sumOf { extension ->
        extension.descriptor.catalogs.count { it.canLoadWithoutExtras }
    }

    val entries = listOf(
        TvSettingsEntry(
            "addons", "Addons", "Catalogs, metadata, streams and subtitles.",
            "$installedAddons installed",
            onActivate = { onOpen(TvSettingsPage.CONTENT_ADDONS) },
            section = "CONTENT",
            icon = Icons.Default.Extension,
        ),
        TvSettingsEntry(
            "providers", "Plugins & Providers", "Repositories, runtime providers, health and diagnostics.",
            "$repositoryCount repos • $providerCount providers",
            onActivate = { onOpen(TvSettingsPage.CONTENT_PROVIDERS) },
            section = "CONTENT",
            icon = Icons.Default.SettingsInputComponent,
        ),
        TvSettingsEntry(
            "catalogs", "Catalog Order", "Choose the order catalogs appear on Home.",
            "$catalogCount catalogs",
            onActivate = { onOpen(TvSettingsPage.CONTENT_CATALOGS) },
            section = "CONTENT",
            icon = Icons.Default.VideoLibrary,
        ),
    )
    TvSettingsListScreen(
        title = "Content Manager",
        subtitle = "Manage addons, providers and catalogs.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = null,
        metrics = listOf(
            TvSettingsMetric(installedAddons.toString(), "Installed"),
            TvSettingsMetric(onlineCount.toString(), "Online"),
            TvSettingsMetric(slowCount.toString(), "Slow"),
            TvSettingsMetric(failedCount.toString(), "Failed"),
        ),
        footer = "Provider health feeds Smart Source ranking, so slower or unavailable providers do not need to block faster sources.",
    )
}

@Composable
private fun TvAddonSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var removeUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val manifests = remember(revision) { runtime.content.manifestUrls() }

    if (showAdd) {
        TvTextEntryDialog(
            title = "Add Addon",
            initialValue = "",
            placeholder = "https://…/manifest.json",
            onDismiss = { showAdd = false },
            onSave = { url ->
                showAdd = false
                scope.launch {
                    runCatching { runtime.addAddon(url) }
                        .onSuccess {
                            revision++
                            status = "Addon installed."
                            onDataChanged()
                        }
                        .onFailure { status = it.message ?: "Unable to install addon." }
                }
            },
        )
    }

    removeUrl?.let { url ->
        TvConfirmDialog(
            title = "Remove addon?",
            message = "Remove ${shortUrl(url)} from VUEO?",
            confirmLabel = "Remove",
            onDismiss = { removeUrl = null },
            onConfirm = {
                removeUrl = null
                scope.launch {
                    runtime.removeAddon(url)
                    revision++
                    onDataChanged()
                }
            },
        )
    }

    val entries = buildList {
        add(TvSettingsEntry("add", "Add Addon", "Install an HTTPS addon manifest URL.", onActivate = { showAdd = true }, section = "ADDONS", icon = Icons.Default.Extension))
        manifests.forEachIndexed { index, url ->
            val enabled = runtime.content.isAddonEnabled(url)
            add(
                TvSettingsEntry(
                    id = "addon-$index-${url.hashCode()}",
                    title = shortUrl(url),
                    subtitle = "$url  •  OK enable or disable  •  → remove",
                    value = if (enabled) "On" else "Off",
                    onActivate = {
                        scope.launch { runtime.setAddonEnabled(url, !enabled); revision++; onDataChanged() }
                    },
                    section = "INSTALLED ADDONS",
                    icon = Icons.Default.Extension,
                    onRightAction = { removeUrl = url },
                )
            )
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
    }

    TvSettingsListScreen("Addons", "Install, disable or remove open content addons.", entries, onNavigate, onProfile, onBack, topLabel = "Content Manager")
}

@Composable
private fun TvProviderSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var pluginsEnabled by remember { mutableStateOf(runtime.pluginStore.pluginsEnabled()) }
    var showAdd by remember { mutableStateOf(false) }
    var removeRepo by remember { mutableStateOf<PluginRepositoryDescriptor?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val repositories = remember(revision) { runtime.pluginStore.repositories() }
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val providerCodeStore = remember(context) { ProviderCodeStore(context.applicationContext) }
    var diagnosticTarget by remember { mutableStateOf<Pair<PluginRepositoryDescriptor, PluginProviderDescriptor>?>(null) }
    var showRuntimeDiagnostics by remember { mutableStateOf(false) }

    if (showAdd) {
        TvTextEntryDialog(
            title = "Add Provider Repository",
            initialValue = "",
            placeholder = "https://…/manifest.json",
            onDismiss = { showAdd = false },
            onSave = { url ->
                showAdd = false
                scope.launch {
                    runCatching { runtime.addPluginRepository(url) }
                        .onSuccess {
                            revision++
                            status = "Repository added and provider code synced."
                            onDataChanged()
                        }
                        .onFailure { status = it.message ?: "Unable to add repository." }
                }
            },
        )
    }

    removeRepo?.let { repository ->
        TvConfirmDialog(
            title = "Remove repository?",
            message = "Remove ${repository.name} and its provider configuration?",
            confirmLabel = "Remove",
            onDismiss = { removeRepo = null },
            onConfirm = {
                removeRepo = null
                scope.launch {
                    runtime.removePluginRepository(repository)
                    revision++
                    onDataChanged()
                }
            },
        )
    }

    diagnosticTarget?.let { (repository, provider) ->
        TvProviderDiagnosticDialog(
            repository = repository,
            provider = provider,
            health = healthStore.record(repository.manifestUrl, provider.id),
            currentlyEnabled = runtime.pluginStore.isProviderEnabled(repository, provider),
            providerCodeReady = providerCodeStore.isReady(repository, provider),
            onDismiss = { diagnosticTarget = null },
        )
    }

    if (showRuntimeDiagnostics) {
        TvRuntimeDiagnosticsDialog(
            onDismiss = { showRuntimeDiagnostics = false },
        )
    }

    val entries = buildList {
        add(toggleEntry("plugins-master", "Provider Plugins", "Master switch for plugin provider discovery.", pluginsEnabled) {
            pluginsEnabled = it
            runtime.pluginStore.setPluginsEnabled(it)
            revision++
            onDataChanged()
        }.copy(section = "PROVIDER SYSTEM", icon = Icons.Default.SettingsInputComponent))
        add(TvSettingsEntry("add-repo", "Add Repository", "Install an HTTPS provider repository manifest.", onActivate = { showAdd = true }, section = "PROVIDER SYSTEM"))
        add(
            TvSettingsEntry(
                id = "runtime-diagnostics",
                title = "Performance & Crash Diagnostics",
                subtitle = "Source scan timing, UI stalls, memory and crash evidence.",
                value = "Open",
                onActivate = { showRuntimeDiagnostics = true },
                section = "PROVIDER SYSTEM",
            )
        )
        repositories.forEach { repository ->
            val repoEnabled = runtime.pluginStore.isRepositoryEnabled(repository)
            add(
                TvSettingsEntry(
                    id = "repo-${repository.manifestUrl.hashCode()}",
                    title = repository.name,
                    subtitle = "${repository.version} • ${repository.providers.size} providers • OK enable or disable • → remove",
                    value = if (repoEnabled) "On" else "Off",
                    onActivate = {
                        runtime.pluginStore.setRepositoryEnabled(repository, !repoEnabled)
                        revision++
                        onDataChanged()
                    },
                    section = "REPOSITORIES",
                    icon = Icons.Default.SettingsInputComponent,
                    onRightAction = { removeRepo = repository },
                )
            )
            val rankedProviders =
                repository.providers
                    .map { provider ->
                        provider to healthStore.record(repository.manifestUrl, provider.id)
                    }
                    .sortedWith(
                        compareBy<Pair<PluginProviderDescriptor, com.vueo.shared.core.plugin.ProviderHealthRecord?>> { (_, health) ->
                            providerHealthSortKey(health).availabilityTier
                        }.thenByDescending { (_, health) ->
                            providerHealthSortKey(health).performanceScore
                        }.thenBy { (_, health) ->
                            providerHealthSortKey(health).statusTier
                        }.thenBy { (_, health) ->
                            providerHealthSortKey(health).responseMs
                        }.thenBy { (provider, _) ->
                            provider.name.lowercase()
                        }
                    )
            rankedProviders.forEachIndexed { index, (provider, health) ->
                val enabled = runtime.pluginStore.isProviderEnabled(repository, provider)
                val performance = healthStore.performance(health)
                val performanceSummary = buildList {
                    add("Score ${performance.score}")
                    performance.hitRatePercent?.let { add("$it% hit") }
                    performance.averageResponseMs?.let { add(formatTvProviderAverageResponse(it)) }
                    if (performance.historyRuns > 0) add("${performance.historyRuns} runs") else add("no history")
                }.joinToString(" • ")
                add(
                    TvSettingsEntry(
                        id = "provider-${repository.manifestUrl.hashCode()}-${provider.id}",
                        title = "#${index + 1}  ${provider.name}",
                        subtitle = buildString {
                            append(performanceSummary)
                            append(" • ")
                            append(health?.status?.label ?: "Unknown")
                            provider.description?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                            append(" • OK enable or disable • → diagnostics")
                        },
                        value = if (enabled) "On" else "Off",
                        enabled = repoEnabled && pluginsEnabled,
                        onActivate = {
                            runtime.pluginStore.setProviderEnabled(repository, provider, !enabled)
                            revision++
                            onDataChanged()
                        },
                        section = "PROVIDERS",
                        onRightAction = { diagnosticTarget = repository to provider },
                    )
                )
            }
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
    }

    TvSettingsListScreen(
        "Plugins & Providers",
        "Repositories, runtime providers, health and diagnostics.",
        entries,
        onNavigate,
        onProfile,
        onBack,
        topLabel = "Content Manager",
    )
}

private fun formatTvProviderAverageResponse(responseMs: Long): String =
    if (responseMs < 1_000L) {
        "${responseMs} ms avg"
    } else {
        val tenths = ((responseMs + 50L) / 100L) / 10.0
        "${tenths}s avg"
    }

@Composable
private fun TvCatalogSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf(emptyMap<String, String>()) }
    var order by remember { mutableStateOf(runtime.content.catalogOrder()) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(revision) {
        val loaded = runCatching { runtime.homeRows(forceRefresh = false) }.getOrDefault(emptyList())
        rows = loaded.associate { it.id to it.title }
        order = runtime.content.reconcileCatalogOrder(loaded.map { it.id })
    }

    val entries = order.mapIndexed { index, key ->
        val enabled = runtime.content.isCatalogEnabled(key)
        TvSettingsEntry(
            id = "catalog-$key",
            title = rows[key] ?: key,
            subtitle = "${index + 1} of ${order.size} • ←/→ reorder • OK show/hide",
            value = if (enabled) "Shown" else "Hidden",
            onPrevious = {
                if (index > 0) {
                    val next = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    order = next
                    runtime.content.setCatalogOrder(next)
                    onDataChanged()
                }
            },
            onNext = {
                if (index < order.lastIndex) {
                    val next = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    order = next
                    runtime.content.setCatalogOrder(next)
                    onDataChanged()
                }
            },
            onActivate = {
                runtime.content.setCatalogEnabled(key, !enabled)
                revision++
                onDataChanged()
            },
            section = "HOME CATALOGS",
            icon = Icons.Default.VideoLibrary,
        )
    }

    TvSettingsListScreen(
        "Catalog Order",
        "Control Home visibility and ordering. Hidden catalogs keep their position.",
        entries.ifEmpty { listOf(TvSettingsEntry("loading", "Catalogs", "Open Home once if no catalogs have been discovered yet.", enabled = false)) },
        onNavigate, onProfile, onBack, topLabel = "Content Manager",
    )
}

@Composable
private fun TvEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpen: (TvSettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    val entries = listOf(
        TvSettingsEntry(
            id = "tmdb",
            title = "TMDB",
            subtitle = "Metadata, discovery, recommendations, similar titles and artwork.",
            value = configuredLabel(runtime.pluginStore.tmdbApiKey()),
            onActivate = { onOpen(TvSettingsPage.ENHANCEMENT_TMDB) },
            section = "METADATA & RATINGS",
            icon = Icons.Default.SettingsInputComponent,
        ),
        TvSettingsEntry(
            id = "mdblist",
            title = "MDBList",
            subtitle = "Ratings and score enrichment for title details.",
            value = configuredLabel(store.mdblistApiKey()),
            onActivate = { onOpen(TvSettingsPage.ENHANCEMENT_MDBLIST) },
            section = "METADATA & RATINGS",
            icon = Icons.Default.SettingsInputComponent,
        ),
    )

    TvSettingsListScreen(
        title = "Enhancements",
        subtitle = "Optional services for richer metadata and ratings.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        footer = "VUEO core playback, Library and local Personalization continue to work without these services.",
    )
}

@Composable
private fun TvTmdbEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var apiKey by remember { mutableStateOf(runtime.pluginStore.tmdbApiKey()) }
    var editing by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf(store.tmdbMetadataEnrichmentEnabled()) }
    var artwork by remember { mutableStateOf(store.tmdbArtworkEnrichmentEnabled()) }
    var recommendations by remember { mutableStateOf(store.tmdbRecommendationsEnabled()) }
    var similar by remember { mutableStateOf(store.tmdbSimilarTitlesEnabled()) }

    if (editing) {
        TvTextEntryDialog(
            title = "TMDB API Key",
            initialValue = apiKey,
            secret = true,
            onDismiss = { editing = false },
            onSave = { value ->
                apiKey = value
                runtime.pluginStore.setTmdbApiKey(value)
                editing = false
            },
        )
    }

    val entries = listOf(
        TvSettingsEntry(
            id = "api-key",
            title = "API Key",
            subtitle = "Stored locally on this TV and used only for TMDB requests.",
            value = configuredLabel(apiKey),
            onActivate = { editing = true },
            section = "CONNECTION",
            icon = Icons.Default.SettingsInputComponent,
        ),
        toggleEntry(
            "metadata", "Metadata", "Enrich details with runtime, cast, genres and production metadata.", metadata,
        ) { metadata = it; store.setTmdbMetadataEnrichmentEnabled(it) }.copy(section = "FEATURES"),
        toggleEntry(
            "artwork", "Artwork", "Use richer backdrop and poster artwork when available.", artwork,
        ) { artwork = it; store.setTmdbArtworkEnrichmentEnabled(it) }.copy(section = "FEATURES"),
        toggleEntry(
            "recommendations", "Recommendations", "Allow recommendation surfaces to use TMDB recommendations.", recommendations,
        ) { recommendations = it; store.setTmdbRecommendationsEnabled(it) }.copy(section = "FEATURES"),
        toggleEntry(
            "similar", "Similar Titles", "Allow recommendation surfaces to use similar-title results.", similar,
        ) { similar = it; store.setTmdbSimilarTitlesEnabled(it) }.copy(section = "FEATURES"),
    )

    TvSettingsListScreen(
        title = "TMDB",
        subtitle = "Metadata, discovery, recommendations, similar titles and artwork.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Enhancements",
    )
}

@Composable
private fun TvMdblistEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var apiKey by remember { mutableStateOf(store.mdblistApiKey()) }
    var editing by remember { mutableStateOf(false) }
    var ratings by remember { mutableStateOf(store.mdblistRatingsEnabled()) }
    var imdb by remember { mutableStateOf(store.mdblistImdbEnabled()) }
    var rt by remember { mutableStateOf(store.mdblistRottenTomatoesEnabled()) }
    var metacritic by remember { mutableStateOf(store.mdblistMetacriticEnabled()) }
    var tmdb by remember { mutableStateOf(store.mdblistTmdbRatingEnabled()) }
    var trakt by remember { mutableStateOf(store.mdblistTraktEnabled()) }

    if (editing) {
        TvTextEntryDialog(
            title = "MDBList API Key",
            initialValue = apiKey,
            secret = true,
            onDismiss = { editing = false },
            onSave = { value ->
                apiKey = value
                store.setMdblistApiKey(value)
                editing = false
            },
        )
    }

    val entries = listOf(
        TvSettingsEntry(
            id = "api-key",
            title = "API Key",
            subtitle = "Stored locally on this TV and used only for MDBList requests.",
            value = configuredLabel(apiKey),
            onActivate = { editing = true },
            section = "CONNECTION",
            icon = Icons.Default.SettingsInputComponent,
        ),
        toggleEntry(
            "ratings", "Ratings", "Fetch supported rating sources when title details load.", ratings,
        ) { ratings = it; store.setMdblistRatingsEnabled(it) }.copy(section = "RATINGS"),
        toggleEntry(
            "imdb", "IMDb Rating", "Allow IMDb rating from MDBList.", imdb, enabled = ratings,
        ) { imdb = it; store.setMdblistImdbEnabled(it) }.copy(section = "RATINGS"),
        toggleEntry(
            "rt", "Rotten Tomatoes", "Allow Rotten Tomatoes rating from MDBList.", rt, enabled = ratings,
        ) { rt = it; store.setMdblistRottenTomatoesEnabled(it) }.copy(section = "RATINGS"),
        toggleEntry(
            "metacritic", "Metacritic", "Allow Metacritic rating from MDBList.", metacritic, enabled = ratings,
        ) { metacritic = it; store.setMdblistMetacriticEnabled(it) }.copy(section = "RATINGS"),
        toggleEntry(
            "tmdb", "TMDB Rating", "Allow TMDB rating from MDBList.", tmdb, enabled = ratings,
        ) { tmdb = it; store.setMdblistTmdbRatingEnabled(it) }.copy(section = "RATINGS"),
        toggleEntry(
            "trakt", "Trakt Rating", "Allow Trakt rating from MDBList.", trakt, enabled = ratings,
        ) { trakt = it; store.setMdblistTraktEnabled(it) }.copy(section = "RATINGS"),
    )

    TvSettingsListScreen(
        title = "MDBList",
        subtitle = "Ratings and score enrichment for title details.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Enhancements",
    )
}


@Composable
private fun TvPlaybackSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var resume by remember { mutableStateOf(store.resumePlaybackEnabled()) }
    var quality by remember { mutableStateOf(store.preferredQuality()) }
    var speed by remember { mutableStateOf(store.playerPlaybackSpeed()) }
    var fit by remember { mutableStateOf(store.playerVideoFit()) }
    var warnings by remember { mutableStateOf(store.contentWarningsEnabled()) }
    var skip by remember { mutableStateOf(store.skipSegmentsEnabled()) }
    var autoplay by remember { mutableStateOf(store.autoPlayNextEpisodeEnabled()) }
    var recovery by remember { mutableStateOf(store.autoSourceRecoveryEnabled()) }
    val speeds = listOf(.75f, 1f, 1.25f, 1.5f, 2f)

    val entries = listOf(
        toggleEntry("resume", "Resume Playback", "Ask to continue from a saved position when reopening a title.", resume) { resume = it; store.setResumePlaybackEnabled(it) }
            .copy(section = "PLAYBACK", icon = Icons.Default.PlayArrow),
        choiceEntry("quality", "Preferred Quality", "Prefer this resolution when Smart Source ranks playable streams.", quality.label, { quality = cycle(PreferredQuality.entries, quality, -1); store.setPreferredQuality(quality) }, { quality = cycle(PreferredQuality.entries, quality, 1); store.setPreferredQuality(quality) })
            .copy(section = "PLAYBACK"),
        choiceEntry("speed", "Playback Speed", "Default speed used by the TV player.", "${speed}×", { speed = cycle(speeds, speed, -1); store.setPlayerPlaybackSpeed(speed) }, { speed = cycle(speeds, speed, 1); store.setPlayerPlaybackSpeed(speed) })
            .copy(section = "PLAYER"),
        choiceEntry("fit", "Video Fit", "Choose how video fills the TV canvas.", fit.label, { fit = cycle(PlayerVideoFit.entries, fit, -1); store.setPlayerVideoFit(fit) }, { fit = cycle(PlayerVideoFit.entries, fit, 1); store.setPlayerVideoFit(fit) })
            .copy(section = "PLAYER"),
        toggleEntry("warnings", "Content Warnings", "Show available parental guidance briefly when playback starts.", warnings) { warnings = it; store.setContentWarningsEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("skip", "Skip Intro & Ending", "Show contextual skip controls when verified timestamps are available.", skip) { skip = it; store.setSkipSegmentsEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("autoplay", "Auto-play Next Episode", "Start the next episode after an 8-second countdown when playback ends.", autoplay) { autoplay = it; store.setAutoPlayNextEpisodeEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("recovery", "Auto Source Recovery", "Try up to two ranked alternatives after an error or timeout while keeping the timestamp.", recovery) { recovery = it; store.setAutoSourceRecoveryEnabled(it) }
            .copy(section = "BEHAVIOR"),
    )

    TvSettingsListScreen("Playback", "Player behavior and quality preference.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvSubtitleSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var primary by remember { mutableStateOf(store.preferredSubtitleLanguage()) }
    var secondary by remember { mutableStateOf(store.secondarySubtitleLanguage()) }
    var defaultOn by remember { mutableStateOf(store.subtitlesOnByDefault()) }
    var autoSelect by remember { mutableStateOf(store.autoSelectPreferredSubtitle()) }
    var embedded by remember { mutableStateOf(store.embeddedSubtitlePriority()) }
    var size by remember { mutableStateOf(store.subtitleSize()) }
    var bold by remember { mutableStateOf(store.subtitleBold()) }
    var outline by remember { mutableStateOf(store.subtitleOutlineEnabled()) }
    var bottomPadding by remember { mutableIntStateOf(store.subtitleBottomPaddingPercent()) }
    var opacity by remember { mutableIntStateOf(store.subtitleTextOpacityPercent()) }

    val entries = listOf(
        choiceEntry("primary", "Preferred Language", "First language VUEO should prefer when subtitle tracks are available.", primary.label, { primary = cycle(SubtitleLanguage.entries, primary, -1); store.setPreferredSubtitleLanguage(primary) }, { primary = cycle(SubtitleLanguage.entries, primary, 1); store.setPreferredSubtitleLanguage(primary) })
            .copy(section = "LANGUAGE & BEHAVIOR", icon = Icons.Default.VideoLibrary),
        choiceEntry("secondary", "Secondary Language", "Fallback language when the preferred language is unavailable.", secondary.label, { secondary = cycle(SubtitleLanguage.entries, secondary, -1); store.setSecondarySubtitleLanguage(secondary) }, { secondary = cycle(SubtitleLanguage.entries, secondary, 1); store.setSecondarySubtitleLanguage(secondary) })
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("default", "Subtitles On by Default", "Prefer showing subtitles automatically when a suitable track exists.", defaultOn) { defaultOn = it; store.setSubtitlesOnByDefault(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("auto", "Auto Select Preferred Language", "Prioritize preferred and secondary languages automatically.", autoSelect) { autoSelect = it; store.setAutoSelectPreferredSubtitle(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("embedded", "Embedded Subtitle Priority", "Prefer subtitle tracks already included in the stream before external tracks when possible.", embedded) { embedded = it; store.setEmbeddedSubtitlePriority(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        choiceEntry("size", "Subtitle Size", "Saved display size preference for the VUEO player.", size.label, { size = cycle(SubtitleSize.entries, size, -1); store.setSubtitleSize(size) }, { size = cycle(SubtitleSize.entries, size, 1); store.setSubtitleSize(size) })
            .copy(section = "DISPLAY"),
        toggleEntry("bold", "Bold Subtitles", "Use heavier subtitle text.", bold) { bold = it; store.setSubtitleBold(it) }
            .copy(section = "DISPLAY"),
        toggleEntry("outline", "Subtitle Outline", "Draw an outline for contrast over bright video.", outline) { outline = it; store.setSubtitleOutlineEnabled(it) }
            .copy(section = "DISPLAY"),
        choiceEntry("padding", "Bottom Position", "Distance from the bottom edge of the screen.", "$bottomPadding%", { bottomPadding = (bottomPadding - 2).coerceAtLeast(5); store.setSubtitleBottomPaddingPercent(bottomPadding) }, { bottomPadding = (bottomPadding + 2).coerceAtMost(40); store.setSubtitleBottomPaddingPercent(bottomPadding) })
            .copy(section = "DISPLAY"),
        choiceEntry("opacity", "Text Opacity", "Subtitle text opacity.", "$opacity%", { opacity = (opacity - 10).coerceAtLeast(20); store.setSubtitleTextOpacityPercent(opacity) }, { opacity = (opacity + 10).coerceAtMost(100); store.setSubtitleTextOpacityPercent(opacity) })
            .copy(section = "DISPLAY"),
    )

    TvSettingsListScreen("Subtitles", "Subtitle behavior is separate from subtitle providers in Content Manager.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvSourceSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var details by remember { mutableStateOf(runtime.settingsStore.showSourceTechnicalDetails()) }
    val entries = listOf(
        TvSettingsEntry("ranking", "Smart Source Ranking", "Direct playability, preferred quality and provider health are ranked before selection.", "Active", enabled = false, section = "DISCOVERY", icon = Icons.Default.SettingsInputComponent),
        TvSettingsEntry("progressive", "Progressive Discovery", "Fast providers can return results while slower providers continue searching.", "Active", enabled = false, section = "DISCOVERY"),
        toggleEntry("details", "Technical Source Details", "Show codec, HDR, audio, size and provider information when available.", details) {
            details = it
            runtime.settingsStore.setShowSourceTechnicalDetails(it)
        }.copy(section = "DISPLAY"),
    )
    TvSettingsListScreen("Sources", "Discovery and Smart Source behavior.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvAppearanceSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var theme by remember { mutableStateOf(store.appTheme()) }
    var accent by remember { mutableStateOf(store.appAccent()) }

    val entries = listOf(
        choiceEntry("theme", "Theme", "Choose the dark cinematic base palette.", theme.label, {
            theme = cycle(AppTheme.entries, theme, -1); store.setAppTheme(theme); TvDesign.applyTheme(theme)
        }, {
            theme = cycle(AppTheme.entries, theme, 1); store.setAppTheme(theme); TvDesign.applyTheme(theme)
        }).copy(section = "LOOK & FEEL", icon = Icons.Default.Settings),
        choiceEntry("accent", "Accent", "Selection, progress and semantic accents.", accent.label, {
            accent = cycle(AppAccent.entries, accent, -1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }, {
            accent = cycle(AppAccent.entries, accent, 1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }).copy(section = "LOOK & FEEL"),
    )
    TvSettingsListScreen("Appearance", "Choose a dark VUEO palette and tune the interactive accent.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvDataStorageSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = runtime.settingsStore
    var includeCredentials by remember { mutableStateOf(store.includeCredentialsInBackup()) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { VueoBackupManager.exportToUri(context, uri, includeCredentials) }
                .onSuccess { status = "Backup created: ${it.valueCount} values across ${it.preferenceGroups} groups." }
                .onFailure { status = it.message ?: "Unable to create backup." }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val result = VueoBackupManager.restoreFromUri(context, uri)
                runtime.reloadPersistentConfiguration()
                result
            }
                .onSuccess {
                    TvDesign.applyTheme(runtime.settingsStore.appTheme())
                    TvDesign.applyAccent(runtime.settingsStore.appAccent())
                    status = "Backup restored: ${it.valueCount} values."
                    onDataChanged()
                }
                .onFailure { status = it.message ?: "Unable to restore backup." }
        }
    }

    confirmAction?.let { action ->
        val (title, message) = when (action) {
            "history" -> "Clear watch history?" to "Remove Watch History for the active profile?"
            "continue" -> "Clear Continue Watching?" to "Remove all Continue Watching entries for the active profile?"
            "cache" -> "Clear cache?" to "Rebuild Home and source-discovery caches on next use?"
            else -> "Reset VUEO?" to "Clear profiles, configuration, Library and playback data on this TV?"
        }
        TvConfirmDialog(
            title = title,
            message = message,
            confirmLabel = if (action == "reset") "Reset" else "Clear",
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                scope.launch {
                    when (action) {
                        "history" -> runtime.libraryStore.clearHistory()
                        "continue" -> runtime.libraryStore.clearContinueWatching()
                        "cache" -> {
                            CatalogDiscoveryCache.clearAll(context.applicationContext)
                            SourceDiscoveryCache.clearAll()
                        }
                        "reset" -> {
                            VueoBackupManager.resetUserData(context.applicationContext)
                            runtime.reloadPersistentConfiguration()
                            TvDesign.applyTheme(runtime.settingsStore.appTheme())
                            TvDesign.applyAccent(runtime.settingsStore.appAccent())
                        }
                    }
                    status = when (action) {
                        "history" -> "Watch History cleared."
                        "continue" -> "Continue Watching cleared."
                        "cache" -> "Caches cleared."
                        else -> "VUEO local data reset."
                    }
                    onDataChanged()
                }
            },
        )
    }

    val entries = buildList {
        add(toggleEntry("credentials", "Include API Keys in Backup", "Off by default. Enable only when you explicitly want credentials in the JSON backup.", includeCredentials) {
            includeCredentials = it
            store.setIncludeCredentialsInBackup(it)
        }.copy(section = "BACKUP & RESTORE", icon = Icons.Default.VideoLibrary))
        add(TvSettingsEntry("export", "Create Backup", "Save profiles, Content Manager configuration, Settings, Library and playback progress.", "Export", onActivate = {
            exportLauncher.launch(backupFileName())
        }, section = "BACKUP & RESTORE"))
        add(TvSettingsEntry("restore", "Restore Backup", "Choose a VUEO JSON backup. Current local data will be replaced.", "Restore", onActivate = {
            restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
        }, section = "BACKUP & RESTORE"))
        add(TvSettingsEntry("clear-cache", "Catalog & Source Cache", "Clear catalog and source-discovery caches; configuration is preserved.", "Clear", onActivate = { confirmAction = "cache" }, section = "LOCAL DATA", icon = Icons.Default.SettingsInputComponent))
        add(TvSettingsEntry("clear-continue", "Continue Watching", "Remove unfinished playback entries for the active profile only.", "Clear", onActivate = { confirmAction = "continue" }, section = "LOCAL DATA"))
        add(TvSettingsEntry("clear-history", "Watch History", "Clear playback history for the active profile without changing My List.", "Clear", onActivate = { confirmAction = "history" }, section = "LOCAL DATA"))
        add(TvSettingsEntry("reset", "Reset VUEO Data", "Return local configuration and Library data to a fresh state without uninstalling the APK.", "Reset", onActivate = { confirmAction = "reset" }, section = "RESET"))
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
    }

    TvSettingsListScreen("Data & Storage", "Backup, restore, cache, history and local data controls.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvUpdatesSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoChecks by remember { mutableStateOf(runtime.settingsStore.automaticUpdateChecksEnabled()) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var release by remember { mutableStateOf<TvUpdateRelease?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun checkNow() {
        if (checking) return
        checking = true
        status = null
        scope.launch {
            val result = TvUpdateManager.check(context.applicationContext, force = true)
            release = result.release
            checking = false
            status = when {
                result.error != null -> result.error
                result.release?.isNewerThanCurrent() == true -> "VUEO ${result.release.versionName} is available."
                else -> "You're up to date."
            }
        }
    }

    val available = release?.takeIf { it.isNewerThanCurrent() }
    val entries = buildList {
        add(TvSettingsEntry("version", "Current Version", "Build ${BuildConfig.VERSION_CODE}. Updates install over the existing app and keep local VUEO data.", BuildConfig.VERSION_NAME, enabled = false, section = "VERSION", icon = Icons.Default.Refresh))
        add(toggleEntry("auto", "Automatic Update Checks", "Check the VUEO Dev channel in the background with rate limiting.", autoChecks) {
            autoChecks = it
            runtime.settingsStore.setAutomaticUpdateChecksEnabled(it)
        }.copy(section = "UPDATES"))
        add(TvSettingsEntry("check", "Check for Updates", "Check the latest green VUEO development build.", if (checking) "Checking…" else "Check", enabled = !checking, onActivate = ::checkNow, section = "UPDATES"))
        if (available != null) {
            add(
                TvSettingsEntry(
                    "install", "Download & Install", available.title,
                    when {
                        downloading -> "$progress%"
                        TvUpdateManager.needsInstallPermission(context) -> "Allow"
                        else -> "Update"
                    },
                    enabled = !downloading,
                    onActivate = {
                        if (TvUpdateManager.needsInstallPermission(context)) {
                            TvUpdateManager.openInstallPermissionSettings(context)
                            status = "Allow installs for VUEO, then return and choose Update again."
                        } else {
                            downloading = true
                            progress = 0
                            scope.launch {
                                TvUpdateManager.downloadAndInstall(context.applicationContext, available) { progress = it }
                                    .onFailure { status = it.message ?: "Unable to install update." }
                                downloading = false
                            }
                        }
                    },
                    section = "UPDATES",
                )
            )
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
    }

    TvSettingsListScreen("Updates", "Fast VUEO development updates.", entries, onNavigate, onProfile, onBack, footer = "Android requires a final system confirmation before an APK update is installed.")
}

@Composable
private fun TvAboutSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val entries = listOf(
        TvSettingsEntry("vueo", "VUEO", "Universal media frontend built around open content sources, progressive discovery and direct playback.", BuildConfig.VERSION_NAME, enabled = false, section = "APP", icon = Icons.Default.Settings),
        TvSettingsEntry("architecture", "Architecture", "Shared Core owns data and behavior; TV owns the 10-foot experience.", "Shared Core + TV", enabled = false, section = "APP"),
        TvSettingsEntry("privacy", "Privacy", "Profiles, settings and API keys are stored locally on the device. Credentials are excluded from backups by default.", "Local-first", enabled = false, section = "PRIVACY"),
        TvSettingsEntry("tmdb", "TMDB Attribution", "This product uses the TMDB API but is not endorsed or certified by TMDB.", "TMDB", enabled = false, section = "ATTRIBUTION"),
    )
    TvSettingsListScreen("About VUEO", "App, privacy and architecture information.", entries, onNavigate, onProfile, onBack)
}

private fun toggleEntry(
    id: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChanged: (Boolean) -> Unit,
): TvSettingsEntry = TvSettingsEntry(
    id = id,
    title = title,
    subtitle = subtitle,
    value = if (checked) "On" else "Off",
    enabled = enabled,
    onActivate = { onChanged(!checked) },
)

private fun choiceEntry(
    id: String,
    title: String,
    subtitle: String,
    value: String,
    previous: () -> Unit,
    next: () -> Unit,
): TvSettingsEntry = TvSettingsEntry(
    id = id,
    title = title,
    subtitle = subtitle,
    value = value,
    onPrevious = previous,
    onNext = next,
    onActivate = next,
)

private fun enhancementSummary(runtime: TvRuntime): String = buildString {
    append("TMDB ")
    append(if (runtime.pluginStore.tmdbApiKey().isBlank()) "optional" else "configured")
    append(" • MDBList ")
    append(if (runtime.settingsStore.mdblistApiKey().isBlank()) "optional" else "configured")
}

private fun configuredLabel(value: String): String = if (value.isBlank()) "Not configured" else "Configured"

private fun shortUrl(value: String): String = runCatching {
    URI(value).host?.removePrefix("www.")?.takeIf(String::isNotBlank) ?: value
}.getOrDefault(value).take(44)

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "VUEO-TV-backup-$stamp.json"
}
