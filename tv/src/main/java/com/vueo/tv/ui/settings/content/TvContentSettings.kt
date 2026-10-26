package com.vueo.tv.settings

import com.vueo.shared.core.storage.RecommendationEngine
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.primaryAddonCategory
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
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
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvSidebarPreferences
import com.vueo.tv.ui.TvSidebarStyle
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TvPersonalizationSettings(
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
    var forYou by remember(profile.id) { mutableStateOf(dna.forYouEnabled(profile.id)) }
    var because by remember(profile.id) { mutableStateOf(dna.becauseYouWatchedEnabled(profile.id)) }
    val entries = listOf(
        toggleEntry("dna", "User DNA", "Build a local taste profile from History, playback progress and Watchlist.", dnaEnabled) {
            dnaEnabled = it
            dna.setUserDnaEnabled(profile.id, it)
        }.copy(section = "USER DNA"),
        toggleEntry("dna-match", "Show DNA Match", "Show local taste-match information on supported titles.", showMatch, enabled = dnaEnabled) {
            showMatch = it
            dna.setShowDnaMatchEnabled(profile.id, it)
        }.copy(section = "USER DNA"),
        toggleEntry("for-you", "For You", "Show personalized recommendations on Home.", forYou, enabled = dnaEnabled) {
            forYou = it
            dna.setForYouEnabled(profile.id, it)
        }.copy(section = "HOME RECOMMENDATIONS"),
        toggleEntry("because-you-watched", "Because You Watched", "Show related titles from your recent viewing on Home.", because, enabled = dnaEnabled) {
            because = it
            dna.setBecauseYouWatchedEnabled(profile.id, it)
        }.copy(section = "HOME RECOMMENDATIONS"),
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
internal fun TvContentManagerHub(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpen: (TvSettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val repositories = runtime.pluginStore.repositories()
    val activeRepositories = if (runtime.pluginStore.pluginsEnabled()) {
        repositories.filter(runtime.pluginStore::isRepositoryEnabled)
    } else {
        emptyList()
    }
    val healthSummary = healthStore.summary(
        repositories = activeRepositories,
        pluginStore = runtime.pluginStore,
    )
    val onlineCount = healthSummary.online
    val slowCount = healthSummary.slow
    val failedCount = healthSummary.failed + healthSummary.blocked +
        healthSummary.unavailable + healthSummary.timeout
    val addons = runtime.engine.stremioAddons()
    val installedAddons = addons.size
    val repositoryCount = repositories.size
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
            accented = true,
        ),
        TvSettingsEntry(
            "providers", "Plugins & Providers", "Repositories, runtime providers, health and diagnostics.",
            "$repositoryCount repos • $providerCount providers",
            onActivate = { onOpen(TvSettingsPage.CONTENT_PROVIDERS) },
            section = "CONTENT",
            icon = Icons.Default.SettingsInputComponent,
            accented = true,
        ),
        TvSettingsEntry(
            "collections", "Collections", "Build Home rows with folders and catalog sources.", "Custom",
            onActivate = { onOpen(TvSettingsPage.CONTENT_COLLECTIONS) },
            section = "CONTENT", icon = Icons.Default.VideoLibrary, accented = true,
        ),
        TvSettingsEntry(
            "catalogs", "Catalog Order", "Choose the order catalogs appear on Home.",
            "$catalogCount catalogs",
            onActivate = { onOpen(TvSettingsPage.CONTENT_CATALOGS) },
            section = "CONTENT",
            icon = Icons.Default.VideoLibrary,
            accented = true,
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
        contentManager = true,
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
internal fun TvAddonSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val restoreInstallFocus = rememberTvSettingsDeferredFocusRestore()
    var installDraft by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var manageUrl by remember { mutableStateOf<String?>(null) }
    var refreshingUrl by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("Catalog & Metadata") }
    var revision by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var removeUrl by remember { mutableStateOf<String?>(null) }
    var managementMessage by remember { mutableStateOf<String?>(null) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    val addonMessages = remember { mutableStateMapOf<String, String>() }
    var refreshingAddons by remember { mutableStateOf(false) }
    val manifests = remember(revision) { runtime.content.manifestUrls() }
    val installedAddons = remember(revision) { runtime.engine.stremioAddons() }
    val addonByManifest = remember(installedAddons) {
        installedAddons.associateBy { it.descriptor.baseUrl.trim() }
    }

    if (showAdd) {
        TvTextEntryDialog(
            title = "Add Addon",
            themedInstall = true,
            confirmLabel = "Install",
            initialValue = installDraft,
            busy = installing,
            restoreOnSave = false,
            message = managementMessage,
            placeholder = "https://…/manifest.json",
            onDismiss = { if (!installing) showAdd = false },
            onSave = { url ->
                installDraft = url
                if (!installing) scope.launch {
                    installing = true
                    managementMessage = "Installing addon…"
                    try {
                    runCatching { runtime.addAddon(url) }
                        .onSuccess {
                            revision++
                            installDraft = ""
                            showAdd = false
                            restoreInstallFocus()
                            managementMessage = "Addon installed."
                            onDataChanged()
                        }
                        .onFailure {
                            if (it is kotlinx.coroutines.CancellationException) throw it
                            managementMessage = com.vueo.shared.core.extensions.AddonManagementMessage.failure(it)
                        }
                    } finally { installing = false }
                }
            },
        )
    }

    removeUrl?.let { url ->
        TvConfirmDialog(
            title = "Remove addon?",
            message = "Remove ${addonByManifest[url.trim()]?.descriptor?.name ?: shortUrl(url)} from VUEO?",
            confirmLabel = "Remove",
            themedRemove = true,
            onDismiss = { removeUrl = null },
            onConfirm = {
                removeUrl = null
                scope.launch {
                    runtime.removeAddon(url)
                    addonMessages.remove(url.trim())
                    managementMessage = "Addon removed."
                    revision++
                    onDataChanged()
                }
            },
        )
    }

    fun addonCategory(descriptor: com.vueo.shared.core.extensions.ExtensionDescriptor?): String =
        descriptor?.primaryAddonCategory()?.label ?: "Other"
    val categories = listOf("Catalog & Metadata", "Streams", "Subtitles")
    val entries = buildList {
        add(
            TvSettingsEntry(
                id = "add",
                title = "Add Addon",
                subtitle = "Install an HTTP or HTTPS addon manifest URL.",
                onActivate = { if (!installing) { managementMessage = null; showAdd = true } },
                operationStatus = managementMessage,
                section = "ADDONS",
                icon = Icons.Default.Extension,
                accented = true,
            )
        )
        add(
            TvSettingsEntry(
                id = "refresh-addons",
                operationStatus = if (refreshingAddons) "Refreshing addons…" else refreshMessage,
                title = "Refresh Addons",
                subtitle = "Reload installed manifests while keeping your enable and disable choices.",
                value = if (refreshingAddons) "Refreshing…" else "Refresh",
                onActivate = {
                    if (!refreshingAddons && refreshingUrl == null) {
                        refreshingAddons = true
                        refreshMessage = null
                        scope.launch {
                            runCatching { runtime.refreshAddons() }
                                .onSuccess { summary ->
                                    revision++
                                    refreshMessage = when {
                                        summary.failed == 0 -> "${summary.refreshed} addon manifests refreshed."
                                        summary.refreshed == 0 -> "Refresh failed for ${summary.failed} addons. Existing runtime copies were kept."
                                        else -> "${summary.refreshed} refreshed • ${summary.failed} failed; existing copies were kept."
                                    }
                                    onDataChanged()
                                }
                                .onFailure { error ->
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    refreshMessage = com.vueo.shared.core.extensions.AddonManagementMessage.failure(error)
                                }
                            refreshingAddons = false
                        }
                    }
                },
                section = "ADDONS",
                icon = Icons.Default.Refresh,
                accented = true,
            )
        )

        categories.forEachIndexed { index, category ->
            add(TvSettingsEntry(
                id = "addon-category-$index",
                title = category,
                subtitle = "",
                value = if (selectedCategory == category) "Selected" else "",
                onActivate = { selectedCategory = category },
            ))
        }

        val orderedManifests = manifests
            .map { url -> url to addonByManifest[url.trim()] }
            .sortedWith(
                compareBy<Pair<String, com.vueo.shared.core.extensions.MediaExtension?>> { (url, _) ->
                    !runtime.content.isAddonEnabled(url)
                }.thenBy { (_, addon) ->
                    categories.indexOf(addonCategory(addon?.descriptor))
                }.thenBy { (url, addon) ->
                    addon?.descriptor?.name?.lowercase() ?: url.lowercase()
                }
            )

        orderedManifests.filter { (_, addon) -> addonCategory(addon?.descriptor) == selectedCategory }
            .forEachIndexed { _, (url, addon) ->
            val descriptor = addon?.descriptor
            val enabled = runtime.content.isAddonEnabled(url)
            add(
                TvSettingsEntry(
                    id = "addon-${url.hashCode()}",
                    title = descriptor?.name ?: shortUrl(url),
                    subtitle = if (descriptor != null) {
                        "v${descriptor.version} • ${descriptor.catalogs.size} catalogs • ${descriptor.resources.size} resources"
                    } else {
                        shortUrl(url)
                    },
                    detail = descriptor?.description
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: url,
                    value = if (enabled) "On" else "Off",
                    switchChecked = enabled,
                    onActivate = {
                        scope.launch {
                            runtime.setAddonEnabled(url, !enabled)
                            revision++
                            onDataChanged()
                        }
                    },
                    section = null,
                    icon = Icons.Default.Extension,
                    onRightAction = { manageUrl = url },
                    accented = true,
                    rightActionLabel = "Manage",
                    operationStatus = addonMessages[url.trim()],
                )
            )
        }
    }

    manageUrl?.let { url ->
        val descriptor = addonByManifest[url.trim()]?.descriptor
        val working = refreshingUrl == url || refreshingAddons
        val actions = buildList {
            if (descriptor?.configurable == true || descriptor?.configurationRequired == true) {
                add(TvSettingsEntry(
                    id = "configure", title = "Configure", subtitle = "Open addon settings in your browser.",
                    icon = Icons.Default.Settings, onActivate = {
                        runCatching {
                            val target = com.vueo.shared.core.stremio.StremioUrls.configuration(url)
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target)))
                        }.onFailure { addonMessages[url.trim()] = "No browser available to configure this addon." }
                    },
                ))
            }
            add(TvSettingsEntry(
                id = "refresh", title = "Refresh", subtitle = "Reload this addon's manifest.",
                icon = Icons.Default.Refresh, operationStatus = if (working) "Refreshing…" else addonMessages[url.trim()],
                onActivate = {
                    if (refreshingUrl == null && !refreshingAddons) scope.launch {
                        refreshingUrl = url
                        addonMessages.remove(url.trim())
                        try {
                            runtime.refreshAddon(url)
                            addonMessages[url.trim()] = "Addon refreshed."
                            revision++
                            onDataChanged()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (error: Exception) {
                            addonMessages[url.trim()] = com.vueo.shared.core.extensions.AddonManagementMessage.failure(error)
                        } finally { refreshingUrl = null }
                    }
                },
            ))
            add(TvSettingsEntry(
                id = "remove", title = "Remove", subtitle = "Remove this addon from VUEO.",
                icon = Icons.Default.Extension, onActivate = { if (!working) { manageUrl = null; removeUrl = url } },
            ))
        }
        TvSettingsListScreen(
            title = descriptor?.name ?: "Addon", subtitle = "Manage addon", entries = actions,
            onNavigate = onNavigate, onProfile = onProfile, onBack = { manageUrl = null },
            topLabel = "Addons", contentManager = true,
        )
        return
    }

    val enabledCount = manifests.count { runtime.content.isAddonEnabled(it) }
    val catalogCount = installedAddons.sumOf { it.descriptor.catalogs.size }
    val resourceCount = installedAddons.sumOf { it.descriptor.resources.size }

    TvSettingsListScreen(
        title = "Addons",
        subtitle = "Catalogs, metadata, streams and subtitles.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Content Manager",
        contentManager = true,
        addonLayout = true,
        metrics = listOf(
            TvSettingsMetric(manifests.size.toString(), "Installed"),
            TvSettingsMetric(enabledCount.toString(), "Enabled"),
            TvSettingsMetric(catalogCount.toString(), "Catalogs"),
            TvSettingsMetric(resourceCount.toString(), "Resources"),
        ),
        footer = "Select an addon to enable or disable it. Press right on an addon for Configure, Refresh or Remove.",
    )
}

@Composable
internal fun TvProviderSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onOpenProviderHealth: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val embeddedHost = LocalTvSettingsEmbeddedHost.current
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    var revision by remember { mutableIntStateOf(0) }
    var pluginsEnabled by remember { mutableStateOf(runtime.pluginStore.pluginsEnabled()) }
    var showAdd by remember { mutableStateOf(false) }
    var addBusy by remember { mutableStateOf(false) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var selectedRepositoryUrl by remember {
        mutableStateOf(runtime.pluginStore.repositories().sortedBy { !runtime.pluginStore.isRepositoryEnabled(it) }.firstOrNull()?.manifestUrl)
    }
    var removeRepo by remember { mutableStateOf<PluginRepositoryDescriptor?>(null) }
    var managementMessage by remember { mutableStateOf<String?>(null) }
    val repositoryMessages = remember { mutableStateMapOf<String, String>() }
    val repositories = remember(revision) {
        runtime.pluginStore.repositories().sortedBy { !runtime.pluginStore.isRepositoryEnabled(it) }
    }
    val selectedRepository = repositories.firstOrNull { it.manifestUrl == selectedRepositoryUrl }
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val providerCodeStore = remember(context) { ProviderCodeStore(context.applicationContext) }
    var diagnosticTarget by remember { mutableStateOf<Pair<PluginRepositoryDescriptor, PluginProviderDescriptor>?>(null) }
    var refreshingRepositoryUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repositories, selectedRepositoryUrl) {
        if (repositories.isEmpty()) {
            selectedRepositoryUrl = null
        } else if (selectedRepository == null) {
            selectedRepositoryUrl = repositories.first().manifestUrl
        }
    }

    if (showAdd) {
        TvTextEntryDialog(
            title = "Add Provider Repository",
            themedInstall = true,
            initialValue = "",
            placeholder = "https://…/manifest.json",
            confirmLabel = "Install",
            busy = addBusy,
            message = addMessage,
            restoreOnSave = false,
            onDismiss = {
                showAdd = false
                addMessage = null
            },
            onSave = { url ->
                addBusy = true
                addMessage = "Installing repository and preparing provider code…"
                scope.launch {
                    runCatching { runtime.addPluginRepository(url) }
                        .onSuccess { repository ->
                            revision++
                            selectedRepositoryUrl = repository.manifestUrl
                            repositoryMessages[repository.manifestUrl] = "Installed ${repository.name}."
                            showAdd = false
                            addMessage = null
                            onDataChanged()
                            restoreSettingsFocus()
                            delay(120L)
                            runCatching {
                                embeddedHost
                                    ?.requesterFor("repo-${repository.manifestUrl.hashCode()}")
                                    ?.requestFocus()
                            }
                        }
                        .onFailure {
                            addMessage = it.message ?: "Unable to install repository."
                        }
                    addBusy = false
                }
            },
        )
    }

    removeRepo?.let { repository ->
        TvConfirmDialog(
            title = "Remove repository?",
            message = "Remove ${repository.name} and its provider configuration?",
            confirmLabel = "Remove",
            themedRemove = true,
            onDismiss = { removeRepo = null },
            onConfirm = {
                removeRepo = null
                scope.launch {
                    runtime.removePluginRepository(repository)
                    revision++
                    selectedRepositoryUrl = runtime.pluginStore.repositories().sortedBy { !runtime.pluginStore.isRepositoryEnabled(it) }.firstOrNull()?.manifestUrl
                    repositoryMessages.remove(repository.manifestUrl)
                    managementMessage = "Removed ${repository.name}."
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

    val allEntries = buildList {
        add(toggleEntry("plugins-master", "Providers", "Master switch for plugin provider discovery.", pluginsEnabled) {
            pluginsEnabled = it
            runtime.pluginStore.setPluginsEnabled(it)
            revision++
            onDataChanged()
        }.copy(
            section = "PROVIDER SYSTEM",
            icon = Icons.Default.SettingsInputComponent,
            accented = true,
        ))
        add(
            TvSettingsEntry(
                id = "add-repo",
                operationStatus = managementMessage,
                title = "Add Repository",
                subtitle = "Install an HTTPS provider repository manifest.",
                onActivate = {
                    addMessage = null
                    managementMessage = null
                    showAdd = true
                },
                section = "PROVIDER SYSTEM",
                icon = Icons.Default.SettingsInputComponent,
                accented = true,
            )
        )

        if (repositories.isEmpty()) {
            add(
                TvSettingsEntry(
                    id = "no-repositories",
                    title = "No plugin repositories",
                    subtitle = "Add a provider repository URL to begin.",
                    enabled = false,
                    section = "REPOSITORIES",
                )
            )
        } else {
            val repository = selectedRepository ?: repositories.first()
            val repoEnabled = runtime.pluginStore.isRepositoryEnabled(repository)
            val refreshing = refreshingRepositoryUrl == repository.manifestUrl

            add(toggleEntry(
                id = "repo-${repository.manifestUrl.hashCode()}",
                title = repository.name,
                subtitle = if (repoEnabled) {
                    "Repository active • provider preferences applied during discovery."
                } else {
                    "Repository disabled • provider preferences are preserved."
                },
                checked = repoEnabled,
            ) { enabled ->
                runtime.pluginStore.setRepositoryEnabled(repository, enabled)
                revision++
                onDataChanged()
            }.copy(
                section = "SELECTED REPOSITORY",
                icon = Icons.Default.SettingsInputComponent,
                accented = true,
                detail = "v${repository.version} • ${repository.providers.size} providers • ${providerCodeStore.readyCount(repository)} ready",
                switchChecked = repoEnabled,
                operationStatus = if (refreshing) "Refreshing repository…" else repositoryMessages[repository.manifestUrl],
            ))
            add(
                TvSettingsEntry(
                    id = "refresh-repository",
                    title = "Refresh Repository",
                    subtitle = "Reload this manifest and refresh its provider code without changing preferences.",
                    value = if (refreshing) "Refreshing…" else "Refresh",
                    onActivate = {
                        if (refreshingRepositoryUrl == null) {
                            refreshingRepositoryUrl = repository.manifestUrl
                            repositoryMessages.remove(repository.manifestUrl)
                            scope.launch {
                                runCatching { runtime.refreshPluginRepository(repository) }
                                    .onSuccess { refreshed ->
                                        if (selectedRepositoryUrl == repository.manifestUrl) {
                                            selectedRepositoryUrl = refreshed.manifestUrl
                                        }
                                        revision++
                                        repositoryMessages[refreshed.manifestUrl] = "Refreshed • ${providerCodeStore.readyCount(refreshed)} ready"
                                        onDataChanged()
                                    }
                                    .onFailure { repositoryMessages[repository.manifestUrl] = it.message ?: "Unable to refresh repository." }
                                refreshingRepositoryUrl = null
                            }
                        }
                    },
                    section = "SELECTED REPOSITORY",
                    icon = Icons.Default.Refresh,
                    accented = true,
                )
            )
            add(
                TvSettingsEntry(
                    id = "remove-repository",
                    title = "Remove Repository",
                    subtitle = "Remove this repository, provider code and saved repository configuration.",
                    value = "Remove",
                    onActivate = { removeRepo = repository },
                    section = "SELECTED REPOSITORY",
                    icon = Icons.Default.SettingsInputComponent,
                )
            )

            val rankedProviders = repository.providers
                .map { provider -> provider to healthStore.record(repository.manifestUrl, provider.id) }
                .sortedWith(
                    compareBy<Pair<PluginProviderDescriptor, com.vueo.shared.core.plugin.ProviderHealthRecord?>> { (_, health) ->
                        providerHealthSortKey(health).availabilityTier
                    }.thenByDescending { (_, health) ->
                        providerHealthSortKey(health).performanceScore
                    }.thenBy { (_, health) ->
                        providerHealthSortKey(health).statusTier
                    }.thenBy { (_, health) ->
                        providerHealthSortKey(health).responseMs
                    }.thenBy { (provider, _) -> provider.name.lowercase() }
                )

            rankedProviders.forEachIndexed { index, (provider, health) ->
                val providerEnabled = runtime.pluginStore.isProviderEnabled(repository, provider)
                add(
                    TvSettingsEntry(
                        id = "provider-${repository.manifestUrl.hashCode()}-${provider.id}",
                        title = provider.name,
                        subtitle = buildList {
                            if (provider.supportedTypes.isNotEmpty()) add(provider.supportedTypes.sorted().joinToString("/"))
                            if (provider.formats.isNotEmpty()) add(provider.formats.take(3).joinToString(", "))
                            if (provider.limited) add("limited")
                        }.joinToString(" • "),
                        value = if (providerEnabled) "On" else "Off",
                        switchChecked = providerEnabled,
                        providerStatus = if (!providerEnabled) "Disabled" else health?.status?.label ?: "Unknown",
                        providerResponse = health?.responseMs?.let { "$it ms" },
                        providerFailed = providerEnabled && health?.status in setOf(
                            com.vueo.shared.core.plugin.ProviderHealthStatus.FAILED,
                            com.vueo.shared.core.plugin.ProviderHealthStatus.BLOCKED,
                            com.vueo.shared.core.plugin.ProviderHealthStatus.TIMEOUT,
                            com.vueo.shared.core.plugin.ProviderHealthStatus.UNAVAILABLE,
                        ),
                        providerPanelStart = index == 0,
                        providerPanelEnd = index == rankedProviders.lastIndex,
                        onActivate = {
                            runtime.pluginStore.setProviderEnabled(repository, provider, !providerEnabled)
                            revision++
                            onDataChanged()
                        },
                        section = "PROVIDERS",
                        icon = Icons.Default.SettingsInputComponent,
                        onRightAction = { diagnosticTarget = repository to provider },
                        accented = true,
                        rightActionLabel = "Diagnostics",
                    )
                )
            }
        }
    }

    val entries = buildList {
        listOf("add-repo", "plugins-master", "refresh-repository", "provider-health").forEach { id ->
            allEntries.firstOrNull { it.id == id }?.let { add(it.copy(section = null)) }
        }
        repositories.forEach { repository ->
            add(TvSettingsEntry(
                id = "repository-tab-${repository.manifestUrl.hashCode()}",
                title = repository.name,
                subtitle = "",
                value = if (repository.manifestUrl == (selectedRepository?.manifestUrl ?: repositories.firstOrNull()?.manifestUrl)) "Selected" else "",
                onActivate = { selectedRepositoryUrl = repository.manifestUrl },
            ))
        }
        allEntries.filterNot {
            it.id in setOf("add-repo", "plugins-master", "refresh-repository", "provider-health")
        }.forEach { add(it.copy(section = null)) }
    }

    val providerCount = repositories.sumOf { it.providers.size }
    val enabledProviderCount = if (pluginsEnabled) runtime.pluginStore.enabledProviderCount() else 0
    val readyProviderCount = repositories.sumOf { providerCodeStore.readyCount(it) }

    TvSettingsListScreen(
        title = "Plugins & Providers",
        subtitle = "Repositories and runtime providers.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Content Manager",
        contentManager = true,
        addonLayout = true,
        metrics = listOf(
            TvSettingsMetric(repositories.size.toString(), "Repos"),
            TvSettingsMetric(providerCount.toString(), "Providers"),
            TvSettingsMetric(enabledProviderCount.toString(), "Active"),
            TvSettingsMetric(readyProviderCount.toString(), "Ready"),
        ),
        footer = "Select a repository tab. OK changes a saved preference. Press right on a provider for diagnostics.",
        preferredFocusId = repositories.firstOrNull()?.let { "repository-tab-${it.manifestUrl.hashCode()}" } ?: "add-repo",
    )
}

@Composable
internal fun TvCatalogSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var order by remember { mutableStateOf(runtime.content.catalogOrder()) }
    var revision by remember { mutableIntStateOf(0) }

    val addonRuntimeReady = runtime.isHomeCatalogRuntimeReady()
    val installedAddons = runtime.engine.stremioAddons()
        .sortedWith(compareBy<com.vueo.shared.core.extensions.MediaExtension> { it.descriptor.baseUrl.trim() }
            .thenBy { it.descriptor.installationId })
    var randomOrder by remember { mutableStateOf(runtime.content.randomCatalogOrder()) }
    val availableCatalogs = remember(revision, installedAddons) {
        installedAddons.flatMap { extension ->
            extension.descriptor.catalogs
                .filter { it.shouldShowOnHome }
                .map { catalog ->
                    TvCatalogDescriptorEntry(
                        key = "${extension.descriptor.installationId}:${catalog.type}:${catalog.id}",
                        title = catalog.name ?: catalog.id,
                        providerName = extension.descriptor.name,
                        type = catalog.type.replaceFirstChar { it.uppercase() },
                    )
                }
        }
    }
    val availableByKey = availableCatalogs.associateBy(TvCatalogDescriptorEntry::key)
    val availableKeys = availableCatalogs.map(TvCatalogDescriptorEntry::key)

    LaunchedEffect(availableKeys, addonRuntimeReady) {
        // Startup prepares addon manifests in the background. Never reconcile
        // against a temporary empty runtime or valid saved order/hidden state
        // would be erased before the manifests finish loading.
        if (
            addonRuntimeReady &&
            (availableKeys.isNotEmpty() || runtime.content.manifestUrls().isEmpty())
        ) {
            order = runtime.content.reconcileCatalogOrder(availableKeys)
        }
    }

    val entries = order.mapIndexed { index, key ->
        val catalog = availableByKey[key]
        val enabled = runtime.content.isCatalogEnabled(key)
        TvSettingsEntry(
            id = "catalog-$key",
            title = catalog?.title ?: key,
            subtitle = listOfNotNull(
                catalog?.providerName?.takeIf { it.isNotBlank() },
                catalog?.type?.takeIf { it.isNotBlank() },
            ).joinToString(" • ").ifBlank { "Home catalog" },
            detail = "Hidden catalogs keep their saved position.",
            catalogControls = true,
            switchChecked = enabled,
            value = if (enabled) "Shown" else "Hidden",
            onPrevious = if (index > 0) {
                {
                    val next = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    order = next
                    runtime.content.setCatalogOrder(next + runtime.content.catalogOrder().filterNot { it in next })
                    onDataChanged()
                }
            } else null,
            onNext = if (index < order.lastIndex) {
                {
                    val next = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    order = next
                    runtime.content.setCatalogOrder(next + runtime.content.catalogOrder().filterNot { it in next })
                    onDataChanged()
                }
            } else null,
            onActivate = {
                runtime.content.setCatalogEnabled(key, !enabled)
                revision++
                onDataChanged()
            },
            section = "HOME CATALOGS",
            badge = "${index + 1}",
            accented = true,
        )
    }

    val shownCount = order.count { runtime.content.isCatalogEnabled(it) }

    fun changeMode(random: Boolean) {
        randomOrder = random
        runtime.content.setRandomCatalogOrder(random)
        onDataChanged()
    }
    val modeEntry = choiceEntry(
        id = "catalog-home-order", title = "Home order",
        subtitle = "Random stays stable for this app session. Your saved catalog positions are preserved.",
        value = if (randomOrder) "Random" else "Catalog Order",
        previous = { changeMode(!randomOrder) }, next = { changeMode(!randomOrder) },
    )

    TvSettingsListScreen(
        title = "Catalog Order",
        subtitle = "Control Home visibility and ordering. Hidden catalogs keep their position.",
        entries = listOf(modeEntry) + entries.ifEmpty {
            listOf(
                TvSettingsEntry(
                    "loading",
                    if (addonRuntimeReady) "No catalogs available" else "Loading addon catalogs",
                    if (addonRuntimeReady) {
                        "Refresh installed addons if their manifests are temporarily unavailable."
                    } else {
                        "Installed addon manifests are still being prepared."
                    },
                    enabled = false,
                )
            )
        },
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Content Manager",
        contentManager = true,
        metrics = listOf(
            TvSettingsMetric(order.size.toString(), "Catalogs"),
            TvSettingsMetric(shownCount.toString(), "Shown"),
            TvSettingsMetric((order.size - shownCount).toString(), "Hidden"),
        ),
        footer = "Select ON/OFF, ↑ or ↓ with left/right, then press OK. Catalog Order uses your saved positions; Random preserves them.",
    )
}

private data class TvCatalogDescriptorEntry(
    val key: String,
    val title: String,
    val providerName: String,
    val type: String,
)

@Composable
internal fun TvEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpen: (TvSettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var engine by remember { mutableStateOf(store.recommendationEngine()) }
    val traktClientId = store.traktClientId()
    val hasTmdbKey = runtime.pluginStore.tmdbApiKey().isNotBlank()
    fun selectEngine() {
        val next = if (engine == RecommendationEngine.TMDB) RecommendationEngine.TRAKT else RecommendationEngine.TMDB
        val available = if (next == RecommendationEngine.TRAKT) traktClientId.isNotBlank() else hasTmdbKey
        if (available) {
            engine = next
            store.setRecommendationEngine(next)
        }
    }
    val entries = listOf(
        TvSettingsEntry("personalization", "Personalization", "User DNA, DNA Match, For You & Because You Watched.",
            onActivate = { onOpen(TvSettingsPage.PERSONALIZATION) }, section = "PERSONALIZATION", icon = Icons.Default.Settings),
        choiceEntry(
            "recommendation-engine", "Recommendation Engine",
            "Direct Trakt or TMDB results in their original order. Trakt is ready to use; TMDB requires an API key.",
            engine.label, ::selectEngine, ::selectEngine,
        ).copy(section = "RECOMMENDATIONS"),
        TvSettingsEntry(
            id = "trakt-account", title = "Trakt",
            subtitle = "Account, library sync, scrobbling and discovery. Data from Trakt.tv.",
            value = if (com.vueo.shared.core.trakt.TraktService.get(LocalContext.current).connected()) "Connected" else "Optional login",
            onActivate = { onOpen(TvSettingsPage.ENHANCEMENT_TRAKT) }, section = "RECOMMENDATIONS",
            icon = Icons.Default.SettingsInputComponent,
        ),
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
        TvSettingsEntry(
            id = "gemini",
            title = "Gemini",
            subtitle = "Translate provider subtitles with your own Gemini API key.",
            onActivate = { onOpen(TvSettingsPage.ENHANCEMENT_GEMINI) },
            section = "SUBTITLES",
            icon = Icons.Default.SettingsInputComponent,
        ),
    )

    TvSettingsListScreen(
        title = "Enhancements",
        subtitle = "Personalization, metadata, ratings and optional services.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        footer = "VUEO core playback, Library and local Personalization continue to work without these services.",
    )
}

@Composable
internal fun TvTmdbEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(runtime.pluginStore.tmdbApiKey()) }
    var editing by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var metadata by remember { mutableStateOf(store.tmdbMetadataEnrichmentEnabled()) }
    var artwork by remember { mutableStateOf(store.tmdbArtworkEnrichmentEnabled()) }
    var recommendations by remember { mutableStateOf(store.tmdbRecommendationsEnabled()) }
    var similar by remember { mutableStateOf(store.tmdbSimilarTitlesEnabled()) }

    if (editing) {
        TvTextEntryDialog(
            title = "TMDB API Key",
            initialValue = apiKey,
            secret = true,
            allowSecretReveal = true,
            onDismiss = { editing = false },
            onSave = { value ->
                apiKey = value
                runtime.pluginStore.setTmdbApiKey(value)
                connectionStatus = null
                editing = false
            },
        )
    }

    val entries = buildList {
        add(TvSettingsEntry("api-key", "API Key", "Stored locally on this TV and used only for TMDB requests.", configuredLabel(apiKey), onActivate = { editing = true }, section = "CONNECTION", icon = Icons.Default.SettingsInputComponent))
        add(TvSettingsEntry(
            id = "test-connection", title = "Test Connection",
            subtitle = "Verify the current TMDB v3 API key without changing your saved settings.",
            value = if (testing) "Testing…" else connectionStatus ?: "Test",
            onActivate = {
                if (!testing) {
                    val key = apiKey.trim()
                    if (key.isBlank()) connectionStatus = "Enter API key" else {
                        testing = true; connectionStatus = "Testing…"
                        scope.launch {
                            val ok = runCatching { TmdbEnhancementClient.testConnection(key) }.getOrDefault(false)
                            connectionStatus = if (ok) "Connected" else "Connection failed"; testing = false
                        }
                    }
                }
            }, section = "CONNECTION", icon = Icons.Default.Refresh,
        ))
        add(toggleEntry("metadata", "Metadata", "Enrich details with runtime, cast, genres and production metadata.", metadata) { metadata = it; store.setTmdbMetadataEnrichmentEnabled(it) }.copy(section = "FEATURES"))
        add(toggleEntry("artwork", "Artwork", "Use richer backdrop and poster artwork when available.", artwork) { artwork = it; store.setTmdbArtworkEnrichmentEnabled(it) }.copy(section = "FEATURES"))
        add(toggleEntry("recommendations", "Recommendations", "Allow recommendation surfaces to use TMDB recommendations.", recommendations) { recommendations = it; store.setTmdbRecommendationsEnabled(it) }.copy(section = "FEATURES"))
        add(toggleEntry("similar", "Similar Titles", "Allow recommendation surfaces to use similar-title results.", similar) { similar = it; store.setTmdbSimilarTitlesEnabled(it) }.copy(section = "FEATURES"))
    }

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
internal fun TvMdblistEnhancementSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(store.mdblistApiKey()) }
    var editing by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
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
            allowSecretReveal = true,
            onDismiss = { editing = false },
            onSave = { value ->
                apiKey = value
                store.setMdblistApiKey(value)
                connectionStatus = null
                editing = false
            },
        )
    }

    val entries = buildList {
        add(TvSettingsEntry("api-key", "API Key", "Stored locally on this TV and used only for MDBList requests.", configuredLabel(apiKey), onActivate = { editing = true }, section = "CONNECTION", icon = Icons.Default.SettingsInputComponent))
        add(TvSettingsEntry(
            id = "test-connection", title = "Test Connection",
            subtitle = "Verify the current MDBList API key without changing your saved settings.",
            value = if (testing) "Testing…" else connectionStatus ?: "Test",
            onActivate = {
                if (!testing) {
                    val key = apiKey.trim()
                    if (key.isBlank()) connectionStatus = "Enter API key" else {
                        testing = true; connectionStatus = "Testing…"
                        scope.launch {
                            val ok = runCatching { MdblistClient.testConnection(key) }.getOrDefault(false)
                            connectionStatus = if (ok) "Connected" else "Connection failed"; testing = false
                        }
                    }
                }
            }, section = "CONNECTION", icon = Icons.Default.Refresh,
        ))
        add(toggleEntry("ratings", "Ratings", "Fetch supported rating sources when title details load.", ratings) { ratings = it; store.setMdblistRatingsEnabled(it) }.copy(section = "RATINGS"))
        add(toggleEntry("imdb", "IMDb Rating", "Allow IMDb rating from MDBList.", imdb, enabled = ratings) { imdb = it; store.setMdblistImdbEnabled(it) }.copy(section = "RATINGS"))
        add(toggleEntry("rt", "Rotten Tomatoes", "Allow Rotten Tomatoes rating from MDBList.", rt, enabled = ratings) { rt = it; store.setMdblistRottenTomatoesEnabled(it) }.copy(section = "RATINGS"))
        add(toggleEntry("metacritic", "Metacritic", "Allow Metacritic rating from MDBList.", metacritic, enabled = ratings) { metacritic = it; store.setMdblistMetacriticEnabled(it) }.copy(section = "RATINGS"))
        add(toggleEntry("tmdb", "TMDB Rating", "Allow TMDB rating from MDBList.", tmdb, enabled = ratings) { tmdb = it; store.setMdblistTmdbRatingEnabled(it) }.copy(section = "RATINGS"))
        add(toggleEntry("trakt", "Trakt Rating", "Allow Trakt rating from MDBList.", trakt, enabled = ratings) { trakt = it; store.setMdblistTraktEnabled(it) }.copy(section = "RATINGS"))
    }

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
