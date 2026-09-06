package com.vueo.tv.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.ProviderCodeStore
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
    HUB,
    PERSONALIZATION,
    CONTENT_MANAGER,
    CONTENT_ADDONS,
    CONTENT_PROVIDERS,
    CONTENT_CATALOGS,
    ENHANCEMENTS,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    UPDATES,
    ABOUT,
}

@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    onDataChanged: () -> Unit = {},
) {
    var page by remember { mutableStateOf(TvSettingsPage.HUB) }

    fun backFrom(child: TvSettingsPage) {
        page = when (child) {
            TvSettingsPage.CONTENT_ADDONS,
            TvSettingsPage.CONTENT_PROVIDERS,
            TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
            else -> TvSettingsPage.HUB
        }
    }

    when (page) {
        TvSettingsPage.HUB -> TvSettingsHub(
            runtime = runtime,
            onNavigate = onNavigate,
            onProfile = onProfile,
            onOpen = { page = it },
            onBack = onBack,
        )
        TvSettingsPage.PERSONALIZATION -> TvPersonalizationSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.CONTENT_MANAGER -> TvContentManagerHub(
            runtime, onNavigate, onProfile, { page = it }, { backFrom(page) }
        )
        TvSettingsPage.CONTENT_ADDONS -> TvAddonSettings(
            runtime, onNavigate, onProfile, onDataChanged, { backFrom(page) }
        )
        TvSettingsPage.CONTENT_PROVIDERS -> TvProviderSettings(
            runtime, onNavigate, onProfile, onDataChanged, { backFrom(page) }
        )
        TvSettingsPage.CONTENT_CATALOGS -> TvCatalogSettings(
            runtime, onNavigate, onProfile, onDataChanged, { backFrom(page) }
        )
        TvSettingsPage.ENHANCEMENTS -> TvEnhancementSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.PLAYBACK -> TvPlaybackSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.SUBTITLES -> TvSubtitleSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.SOURCES -> TvSourceSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.APPEARANCE -> TvAppearanceSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.DATA_STORAGE -> TvDataStorageSettings(
            runtime, onNavigate, onProfile, onDataChanged, { backFrom(page) }
        )
        TvSettingsPage.UPDATES -> TvUpdatesSettings(
            runtime, onNavigate, onProfile, { backFrom(page) }
        )
        TvSettingsPage.ABOUT -> TvAboutSettings(
            onNavigate, onProfile, { backFrom(page) }
        )
    }
}

@Composable
private fun TvSettingsHub(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpen: (TvSettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val activeProfile = runtime.profileStore.activeProfile()
    val addonCount = runtime.content.manifestUrls().size
    val repoCount = runtime.pluginStore.repositories().size
    val providerCount = runtime.pluginStore.enabledProviderCount()

    val entries = listOf(
        TvSettingsEntry(
            id = "profile",
            title = activeProfile.name,
            subtitle = "Switch or manage the active profile.",
            value = "Profile",
            onActivate = onProfile,
        ),
        TvSettingsEntry(
            id = "personalization",
            title = "Personalization",
            subtitle = "User DNA, DNA Match and recommendations.",
            onActivate = { onOpen(TvSettingsPage.PERSONALIZATION) },
        ),
        TvSettingsEntry(
            id = "content",
            title = "Content Manager",
            subtitle = "Addons, providers and catalog order.",
            value = "$addonCount addons • $repoCount repos • $providerCount providers",
            onActivate = { onOpen(TvSettingsPage.CONTENT_MANAGER) },
        ),
        TvSettingsEntry(
            id = "enhancements",
            title = "Enhancements",
            subtitle = "Metadata, ratings and optional services.",
            value = enhancementSummary(runtime),
            onActivate = { onOpen(TvSettingsPage.ENHANCEMENTS) },
        ),
        TvSettingsEntry(
            id = "playback",
            title = "Playback",
            subtitle = "Player behavior, quality and recovery.",
            value = "${if (runtime.settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"} • ${runtime.settingsStore.preferredQuality().label}",
            onActivate = { onOpen(TvSettingsPage.PLAYBACK) },
        ),
        TvSettingsEntry(
            id = "subtitles",
            title = "Subtitles",
            subtitle = "Language and display preferences.",
            value = "${runtime.settingsStore.preferredSubtitleLanguage().label} • ${runtime.settingsStore.subtitleSize().label}",
            onActivate = { onOpen(TvSettingsPage.SUBTITLES) },
        ),
        TvSettingsEntry(
            id = "sources",
            title = "Sources",
            subtitle = "Smart ranking and source information.",
            value = if (runtime.settingsStore.showSourceTechnicalDetails()) "Technical details on" else "Technical details off",
            onActivate = { onOpen(TvSettingsPage.SOURCES) },
        ),
        TvSettingsEntry(
            id = "appearance",
            title = "Appearance",
            subtitle = "Theme and interactive accent.",
            value = "${runtime.settingsStore.appTheme().label} • ${runtime.settingsStore.appAccent().label}",
            onActivate = { onOpen(TvSettingsPage.APPEARANCE) },
        ),
        TvSettingsEntry(
            id = "storage",
            title = "Data & Storage",
            subtitle = "Backup, restore, history, cache and app data.",
            value = "Local device data",
            onActivate = { onOpen(TvSettingsPage.DATA_STORAGE) },
        ),
        TvSettingsEntry(
            id = "updates",
            title = "Updates",
            subtitle = "Version and automatic update checks.",
            value = if (runtime.settingsStore.automaticUpdateChecksEnabled()) "Automatic checks on" else "Automatic checks off",
            onActivate = { onOpen(TvSettingsPage.UPDATES) },
        ),
        TvSettingsEntry(
            id = "about",
            title = "About VUEO",
            subtitle = "Privacy, architecture and build information.",
            value = "VUEO ${BuildConfig.VERSION_NAME}",
            onActivate = { onOpen(TvSettingsPage.ABOUT) },
        ),
    )

    TvSettingsListScreen(
        title = "Settings",
        subtitle = "TV controls with the same behavior and storage model as VUEO Mobile.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        footer = "VUEO ${BuildConfig.VERSION_NAME} • Settings are stored locally on this device.",
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
    var askStartup by remember { mutableStateOf(runtime.profileStore.askWhoIsWatchingOnStartup()) }

    val entries = listOf(
        TvSettingsEntry("profile-context", "Profile", "These controls apply only to this local profile.", profile.name, onActivate = onProfile),
        toggleEntry("dna", "User DNA", "Build a local taste profile from History, playback and My List.", dnaEnabled) {
            dnaEnabled = it
            dna.setUserDnaEnabled(profile.id, it)
        },
        toggleEntry("dna-match", "Show DNA Match", "Show local taste-match information on supported titles.", showMatch, enabled = dnaEnabled) {
            showMatch = it
            dna.setShowDnaMatchEnabled(profile.id, it)
        },
        toggleEntry("dna-recs", "Personalized Recommendations", "Use User DNA when recommendation surfaces are available.", recommendations, enabled = dnaEnabled) {
            recommendations = it
            dna.setPersonalizedRecommendationsEnabled(profile.id, it)
        },
        toggleEntry("startup-picker", "Ask who’s watching on startup", "Show profile selection before Home opens.", askStartup) {
            askStartup = it
            runtime.profileStore.setAskWhoIsWatchingOnStartup(it)
        },
    )

    TvSettingsListScreen(
        "Personalization",
        "Local, per-profile controls for how VUEO adapts to you.",
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
    val entries = listOf(
        TvSettingsEntry(
            "addons", "Addons", "Catalogs, metadata, streams and subtitles.",
            "${runtime.content.manifestUrls().size} installed",
            onActivate = { onOpen(TvSettingsPage.CONTENT_ADDONS) },
        ),
        TvSettingsEntry(
            "providers", "Providers", "Plugin repositories and provider enable state.",
            "${runtime.pluginStore.enabledProviderCount()}/${runtime.pluginStore.totalProviderCount()} enabled",
            onActivate = { onOpen(TvSettingsPage.CONTENT_PROVIDERS) },
        ),
        TvSettingsEntry(
            "catalogs", "Catalog Order", "Choose visible Home catalogs and their order.",
            "${runtime.content.catalogOrder().size} known",
            onActivate = { onOpen(TvSettingsPage.CONTENT_CATALOGS) },
        ),
    )
    TvSettingsListScreen("Content Manager", "Manage content sources without restoring legacy TV UI.", entries, onNavigate, onProfile, onBack, topLabel = null)
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
        add(TvSettingsEntry("add", "Add Addon", "Install an HTTPS addon manifest URL.", onActivate = { showAdd = true }))
        manifests.forEachIndexed { index, url ->
            val enabled = runtime.content.isAddonEnabled(url)
            add(
                TvSettingsEntry(
                    id = "addon-$index-${url.hashCode()}",
                    title = shortUrl(url),
                    subtitle = "$url  •  ←/→ enable or disable  •  OK remove",
                    value = if (enabled) "On" else "Off",
                    onPrevious = {
                        scope.launch { runtime.setAddonEnabled(url, false); revision++; onDataChanged() }
                    },
                    onNext = {
                        scope.launch { runtime.setAddonEnabled(url, true); revision++; onDataChanged() }
                    },
                    onActivate = { removeUrl = url },
                )
            )
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false)) }
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

    val entries = buildList {
        add(toggleEntry("plugins-master", "Provider Plugins", "Master switch for plugin provider discovery.", pluginsEnabled) {
            pluginsEnabled = it
            runtime.pluginStore.setPluginsEnabled(it)
            revision++
            onDataChanged()
        })
        add(TvSettingsEntry("add-repo", "Add Repository", "Install an HTTPS provider repository manifest.", onActivate = { showAdd = true }))
        repositories.forEach { repository ->
            val repoEnabled = runtime.pluginStore.isRepositoryEnabled(repository)
            add(
                TvSettingsEntry(
                    id = "repo-${repository.manifestUrl.hashCode()}",
                    title = repository.name,
                    subtitle = "${repository.version} • ${repository.providers.size} providers • ←/→ enable • OK remove",
                    value = if (repoEnabled) "On" else "Off",
                    onPrevious = {
                        runtime.pluginStore.setRepositoryEnabled(repository, false)
                        revision++
                        onDataChanged()
                    },
                    onNext = {
                        runtime.pluginStore.setRepositoryEnabled(repository, true)
                        revision++
                        onDataChanged()
                    },
                    onActivate = { removeRepo = repository },
                )
            )
            repository.providers.forEach { provider ->
                val enabled = runtime.pluginStore.isProviderEnabled(repository, provider)
                val health = healthStore.record(repository.manifestUrl, provider.id)
                add(
                    TvSettingsEntry(
                        id = "provider-${repository.manifestUrl.hashCode()}-${provider.id}",
                        title = "  ${provider.name}",
                        subtitle = buildString {
                            append(health?.status?.label ?: "No diagnostic yet")
                            provider.description?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                            append(" • ←/→ enable • OK diagnostics")
                        },
                        value = if (enabled) "On" else "Off",
                        enabled = repoEnabled && pluginsEnabled,
                        onPrevious = {
                            runtime.pluginStore.setProviderEnabled(repository, provider, false)
                            revision++
                            onDataChanged()
                        },
                        onNext = {
                            runtime.pluginStore.setProviderEnabled(repository, provider, true)
                            revision++
                            onDataChanged()
                        },
                        onActivate = { diagnosticTarget = repository to provider },
                    )
                )
            }
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false)) }
    }

    TvSettingsListScreen("Providers", "Repositories and provider switches used by progressive source discovery.", entries, onNavigate, onProfile, onBack, topLabel = "Content Manager")
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
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var tmdbKey by remember { mutableStateOf(runtime.pluginStore.tmdbApiKey()) }
    var mdblistKey by remember { mutableStateOf(store.mdblistApiKey()) }
    var geminiKey by remember { mutableStateOf(store.geminiApiKey()) }
    var editing by remember { mutableStateOf<String?>(null) }
    var tmdbMetadata by remember { mutableStateOf(store.tmdbMetadataEnrichmentEnabled()) }
    var tmdbArtwork by remember { mutableStateOf(store.tmdbArtworkEnrichmentEnabled()) }
    var tmdbRecs by remember { mutableStateOf(store.tmdbRecommendationsEnabled()) }
    var tmdbSimilar by remember { mutableStateOf(store.tmdbSimilarTitlesEnabled()) }
    var ratings by remember { mutableStateOf(store.mdblistRatingsEnabled()) }
    var imdb by remember { mutableStateOf(store.mdblistImdbEnabled()) }
    var rt by remember { mutableStateOf(store.mdblistRottenTomatoesEnabled()) }
    var meta by remember { mutableStateOf(store.mdblistMetacriticEnabled()) }
    var tmdbRating by remember { mutableStateOf(store.mdblistTmdbRatingEnabled()) }
    var trakt by remember { mutableStateOf(store.mdblistTraktEnabled()) }
    var gemini by remember { mutableStateOf(store.geminiInsightsEnabled()) }

    editing?.let { target ->
        val current = when (target) { "tmdb" -> tmdbKey; "mdblist" -> mdblistKey; else -> geminiKey }
        TvTextEntryDialog(
            title = when (target) { "tmdb" -> "TMDB API Key"; "mdblist" -> "MDBList API Key"; else -> "Gemini API Key" },
            initialValue = current,
            secret = true,
            onDismiss = { editing = null },
            onSave = { value ->
                when (target) {
                    "tmdb" -> { tmdbKey = value; runtime.pluginStore.setTmdbApiKey(value) }
                    "mdblist" -> { mdblistKey = value; store.setMdblistApiKey(value) }
                    else -> { geminiKey = value; store.setGeminiApiKey(value) }
                }
                editing = null
            },
        )
    }

    val entries = listOf(
        TvSettingsEntry("tmdb-key", "TMDB", "Metadata and artwork enrichment API key.", configuredLabel(tmdbKey), onActivate = { editing = "tmdb" }),
        toggleEntry("tmdb-meta", "TMDB Metadata", "Enrich details with runtime, cast, genres and production metadata.", tmdbMetadata) { tmdbMetadata = it; store.setTmdbMetadataEnrichmentEnabled(it) },
        toggleEntry("tmdb-art", "TMDB Artwork", "Use richer backdrop and poster artwork when available.", tmdbArtwork) { tmdbArtwork = it; store.setTmdbArtworkEnrichmentEnabled(it) },
        toggleEntry("tmdb-recs", "TMDB Recommendations", "Allow recommendation surfaces to use TMDB recommendations.", tmdbRecs) { tmdbRecs = it; store.setTmdbRecommendationsEnabled(it) },
        toggleEntry("tmdb-similar", "TMDB Similar Titles", "Allow recommendation surfaces to use similar-title results.", tmdbSimilar) { tmdbSimilar = it; store.setTmdbSimilarTitlesEnabled(it) },
        TvSettingsEntry("mdblist-key", "MDBList", "Ratings service API key.", configuredLabel(mdblistKey), onActivate = { editing = "mdblist" }),
        toggleEntry("ratings", "MDBList Ratings", "Fetch supported rating sources when title details load.", ratings) { ratings = it; store.setMdblistRatingsEnabled(it) },
        toggleEntry("rating-imdb", "IMDb Rating", "Allow IMDb rating from MDBList.", imdb, enabled = ratings) { imdb = it; store.setMdblistImdbEnabled(it) },
        toggleEntry("rating-rt", "Rotten Tomatoes", "Allow Rotten Tomatoes rating from MDBList.", rt, enabled = ratings) { rt = it; store.setMdblistRottenTomatoesEnabled(it) },
        toggleEntry("rating-meta", "Metacritic", "Allow Metacritic rating from MDBList.", meta, enabled = ratings) { meta = it; store.setMdblistMetacriticEnabled(it) },
        toggleEntry("rating-tmdb", "TMDB Rating", "Allow TMDB rating from MDBList.", tmdbRating, enabled = ratings) { tmdbRating = it; store.setMdblistTmdbRatingEnabled(it) },
        toggleEntry("rating-trakt", "Trakt Rating", "Allow Trakt rating from MDBList.", trakt, enabled = ratings) { trakt = it; store.setMdblistTraktEnabled(it) },
        TvSettingsEntry("gemini-key", "Gemini", "Optional AI insight API key.", configuredLabel(geminiKey), onActivate = { editing = "gemini" }),
        toggleEntry("gemini-insights", "Gemini Insights", "Allow optional title insight surfaces when configured.", gemini, enabled = geminiKey.isNotBlank()) { gemini = it; store.setGeminiInsightsEnabled(it) },
    )

    TvSettingsListScreen("Enhancements", "Optional metadata, ratings and external services. Core playback works without them.", entries, onNavigate, onProfile, onBack, topLabel = null)
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
        toggleEntry("resume", "Resume Playback", "Continue from saved playback position when reopening a title.", resume) { resume = it; store.setResumePlaybackEnabled(it) },
        choiceEntry("quality", "Preferred Quality", "Used by Smart Source Ranking.", quality.label, { quality = cycle(PreferredQuality.entries, quality, -1); store.setPreferredQuality(quality) }, { quality = cycle(PreferredQuality.entries, quality, 1); store.setPreferredQuality(quality) }),
        choiceEntry("speed", "Playback Speed", "Default speed used by the TV player.", "${speed}×", { speed = cycle(speeds, speed, -1); store.setPlayerPlaybackSpeed(speed) }, { speed = cycle(speeds, speed, 1); store.setPlayerPlaybackSpeed(speed) }),
        choiceEntry("fit", "Video Fit", "Choose how video fills the TV canvas.", fit.label, { fit = cycle(PlayerVideoFit.entries, fit, -1); store.setPlayerVideoFit(fit) }, { fit = cycle(PlayerVideoFit.entries, fit, 1); store.setPlayerVideoFit(fit) }),
        toggleEntry("warnings", "Content Warnings", "Show available parental guidance briefly when playback starts.", warnings) { warnings = it; store.setContentWarningsEnabled(it) },
        toggleEntry("skip", "Skip Intro & Ending", "Show contextual skip action when verified timestamps are available.", skip) { skip = it; store.setSkipSegmentsEnabled(it) },
        toggleEntry("autoplay", "Auto-play Next Episode", "Start the next episode after an 8-second countdown when playback ends.", autoplay) { autoplay = it; store.setAutoPlayNextEpisodeEnabled(it) },
        toggleEntry("recovery", "Auto Source Recovery", "Try up to two ranked alternatives after a playback error, keeping the timestamp.", recovery) { recovery = it; store.setAutoSourceRecoveryEnabled(it) },
    )

    TvSettingsListScreen("Playback", "TV-native player behavior and source preference.", entries, onNavigate, onProfile, onBack, topLabel = "PLAYBACK")
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
        choiceEntry("primary", "Preferred Language", "First subtitle language to prefer.", primary.label, { primary = cycle(SubtitleLanguage.entries, primary, -1); store.setPreferredSubtitleLanguage(primary) }, { primary = cycle(SubtitleLanguage.entries, primary, 1); store.setPreferredSubtitleLanguage(primary) }),
        choiceEntry("secondary", "Secondary Language", "Fallback when the preferred language is unavailable.", secondary.label, { secondary = cycle(SubtitleLanguage.entries, secondary, -1); store.setSecondarySubtitleLanguage(secondary) }, { secondary = cycle(SubtitleLanguage.entries, secondary, 1); store.setSecondarySubtitleLanguage(secondary) }),
        toggleEntry("default", "Subtitles On by Default", "Start playback with subtitles enabled when a suitable track exists.", defaultOn) { defaultOn = it; store.setSubtitlesOnByDefault(it) },
        toggleEntry("auto", "Auto Select Preferred Language", "Prioritize preferred and secondary languages automatically.", autoSelect) { autoSelect = it; store.setAutoSelectPreferredSubtitle(it) },
        toggleEntry("embedded", "Embedded Subtitle Priority", "Prefer subtitle tracks included in the stream before external tracks when possible.", embedded) { embedded = it; store.setEmbeddedSubtitlePriority(it) },
        choiceEntry("size", "Subtitle Size", "Default subtitle text size.", size.label, { size = cycle(SubtitleSize.entries, size, -1); store.setSubtitleSize(size) }, { size = cycle(SubtitleSize.entries, size, 1); store.setSubtitleSize(size) }),
        toggleEntry("bold", "Bold Subtitles", "Use heavier subtitle text.", bold) { bold = it; store.setSubtitleBold(it) },
        toggleEntry("outline", "Subtitle Outline", "Draw an outline for contrast over bright video.", outline) { outline = it; store.setSubtitleOutlineEnabled(it) },
        choiceEntry("padding", "Bottom Position", "Distance from the bottom edge of the screen.", "$bottomPadding%", { bottomPadding = (bottomPadding - 2).coerceAtLeast(5); store.setSubtitleBottomPaddingPercent(bottomPadding) }, { bottomPadding = (bottomPadding + 2).coerceAtMost(40); store.setSubtitleBottomPaddingPercent(bottomPadding) }),
        choiceEntry("opacity", "Text Opacity", "Subtitle text opacity.", "$opacity%", { opacity = (opacity - 10).coerceAtLeast(20); store.setSubtitleTextOpacityPercent(opacity) }, { opacity = (opacity + 10).coerceAtMost(100); store.setSubtitleTextOpacityPercent(opacity) }),
    )

    TvSettingsListScreen("Subtitles", "Subtitle behavior is separate from subtitle providers in Content Manager.", entries, onNavigate, onProfile, onBack, topLabel = "PLAYBACK")
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
        TvSettingsEntry("ranking", "Smart Source Ranking", "Direct playability, preferred quality and provider signals are ranked before selection.", "Active", enabled = false),
        TvSettingsEntry("progressive", "Progressive Discovery", "Fast providers can return results while slower providers continue searching.", "Active", enabled = false),
        toggleEntry("details", "Technical Source Details", "Show codec, HDR, audio, size and provider information when available.", details) {
            details = it
            runtime.settingsStore.setShowSourceTechnicalDetails(it)
        },
    )
    TvSettingsListScreen("Sources", "Discovery and Smart Source behavior.", entries, onNavigate, onProfile, onBack, topLabel = "PLAYBACK")
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
        }),
        choiceEntry("accent", "Accent", "Interactive focus edge and selected controls.", accent.label, {
            accent = cycle(AppAccent.entries, accent, -1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }, {
            accent = cycle(AppAccent.entries, accent, 1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }),
    )
    TvSettingsListScreen("Appearance", "Choose a dark VUEO palette and tune the interactive accent.", entries, onNavigate, onProfile, onBack, topLabel = "APP")
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
            runCatching { VueoBackupManager.restoreFromUri(context, uri) }
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
                            TvDesign.applyTheme(AppTheme.CHARCOAL)
                            TvDesign.applyAccent(AppAccent.WHITE)
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
        })
        add(TvSettingsEntry("export", "Export Backup", "Save profiles, Content Manager configuration, Settings, Library and playback progress.", "Export", onActivate = {
            exportLauncher.launch(backupFileName())
        }))
        add(TvSettingsEntry("restore", "Restore Backup", "Choose a VUEO JSON backup. Current local data will be replaced.", "Restore", onActivate = {
            restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
        }))
        add(TvSettingsEntry("clear-history", "Clear Watch History", "Remove watched-history entries for the active profile.", "Clear", onActivate = { confirmAction = "history" }))
        add(TvSettingsEntry("clear-continue", "Clear Continue Watching", "Remove in-progress entries for the active profile.", "Clear", onActivate = { confirmAction = "continue" }))
        add(TvSettingsEntry("clear-cache", "Clear Cache", "Clear catalog and source-discovery caches; configuration is preserved.", "Clear", onActivate = { confirmAction = "cache" }))
        add(TvSettingsEntry("reset", "Reset VUEO Data", "Erase local VUEO profiles, settings, content configuration and Library data.", "Reset", onActivate = { confirmAction = "reset" }))
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false)) }
    }

    TvSettingsListScreen("Data & Storage", "Backup, restore and local data maintenance.", entries, onNavigate, onProfile, onBack, topLabel = "APP")
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
        add(TvSettingsEntry("version", "Installed Version", "Current TV build.", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", enabled = false))
        add(toggleEntry("auto", "Automatic Update Checks", "Check the VUEO Dev channel in the background with rate limiting.", autoChecks) {
            autoChecks = it
            runtime.settingsStore.setAutomaticUpdateChecksEnabled(it)
        })
        add(TvSettingsEntry("check", "Check for Updates", "Check the latest VUEO development release manifest.", if (checking) "Checking…" else "Check", enabled = !checking, onActivate = ::checkNow))
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
                )
            )
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false)) }
    }

    TvSettingsListScreen("Updates", "Version and update preferences.", entries, onNavigate, onProfile, onBack, topLabel = "APP", footer = "Android requires a final system confirmation before an APK update is installed.")
}

@Composable
private fun TvAboutSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val entries = listOf(
        TvSettingsEntry("vueo", "VUEO", "Universal media frontend built around open content sources, progressive discovery and direct playback.", BuildConfig.VERSION_NAME),
        TvSettingsEntry("architecture", "Architecture", "Shared Core owns data and behavior; TV owns the 10-foot experience.", "Shared Core + TV"),
        TvSettingsEntry("privacy", "Privacy", "Profiles, settings and API keys are stored locally on the device. Credentials are excluded from backups by default.", "Local-first"),
        TvSettingsEntry("tmdb", "TMDB Attribution", "This product uses the TMDB API but is not endorsed or certified by TMDB.", "TMDB"),
    )
    TvSettingsListScreen("About VUEO", "App, privacy and architecture information.", entries, onNavigate, onProfile, onBack, topLabel = "APP")
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
    onPrevious = { onChanged(false) },
    onNext = { onChanged(true) },
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
    append(" • Gemini ")
    append(if (runtime.settingsStore.geminiApiKey().isBlank()) "optional" else "configured")
}

private fun configuredLabel(value: String): String = if (value.isBlank()) "Not configured" else "Configured"

private fun shortUrl(value: String): String = runCatching {
    URI(value).host?.removePrefix("www.")?.takeIf(String::isNotBlank) ?: value
}.getOrDefault(value).take(44)

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "VUEO-TV-backup-$stamp.json"
}
