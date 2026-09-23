package com.vueo.tv.settings

import androidx.activity.compose.BackHandler
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
            "provider-health", "Provider Health",
            "Historical performance, ranking, hit rate, timing and current status.",
            "${healthSummary.online} online • ${healthSummary.slow} slow • ${healthSummary.noResults} no results",
            onActivate = { onOpen(TvSettingsPage.CONTENT_PROVIDER_HEALTH) },
            section = "CONTENT",
            icon = Icons.Default.SettingsInputComponent,
            accented = true,
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
    var revision by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var removeUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshingAddons by remember { mutableStateOf(false) }
    val manifests = remember(revision) { runtime.content.manifestUrls() }
    val installedAddons = remember(revision) { runtime.engine.stremioAddons() }
    val addonByManifest = remember(installedAddons) {
        installedAddons.associateBy { it.descriptor.baseUrl.trim() }
    }

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
            message = "Remove ${addonByManifest[url.trim()]?.descriptor?.name ?: shortUrl(url)} from VUEO?",
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
        add(
            TvSettingsEntry(
                id = "add",
                title = "Add Addon",
                subtitle = "Install an HTTPS addon manifest URL.",
                onActivate = { showAdd = true },
                section = "ADDONS",
                icon = Icons.Default.Extension,
                accented = true,
            )
        )
        add(
            TvSettingsEntry(
                id = "refresh-addons",
                title = "Refresh Addons",
                subtitle = "Reload installed manifests while keeping your enable and disable choices.",
                value = if (refreshingAddons) "Refreshing…" else "Refresh",
                onActivate = {
                    if (!refreshingAddons) {
                        refreshingAddons = true
                        status = null
                        scope.launch {
                            runCatching { runtime.refreshAddons() }
                                .onSuccess { summary ->
                                    revision++
                                    status = when {
                                        summary.failed == 0 -> "${summary.refreshed} addon manifests refreshed."
                                        summary.refreshed == 0 -> "Refresh failed for ${summary.failed} addons. Existing runtime copies were kept."
                                        else -> "${summary.refreshed} refreshed • ${summary.failed} failed; existing copies were kept."
                                    }
                                    onDataChanged()
                                }
                                .onFailure { error -> status = error.message ?: "Unable to refresh addons." }
                            refreshingAddons = false
                        }
                    }
                },
                section = "ADDONS",
                icon = Icons.Default.Refresh,
                accented = true,
            )
        )

        val orderedManifests = manifests
            .map { url -> url to addonByManifest[url.trim()] }
            .sortedWith(
                compareBy<Pair<String, com.vueo.shared.core.extensions.MediaExtension?>> { (_, addon) ->
                    addon?.descriptor?.primaryAddonCategory()?.ordinal ?: Int.MAX_VALUE
                }.thenBy { (url, addon) ->
                    addon?.descriptor?.name?.lowercase() ?: url.lowercase()
                }
            )

        orderedManifests.forEachIndexed { index, (url, addon) ->
            val descriptor = addon?.descriptor
            val enabled = runtime.content.isAddonEnabled(url)
            add(
                TvSettingsEntry(
                    id = "addon-$index-${url.hashCode()}",
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
                    onActivate = {
                        scope.launch {
                            runtime.setAddonEnabled(url, !enabled)
                            revision++
                            onDataChanged()
                        }
                    },
                    section = descriptor?.primaryAddonCategory()?.label?.uppercase() ?: "INSTALLED ADDONS",
                    icon = Icons.Default.Extension,
                    onRightAction = { removeUrl = url },
                    accented = true,
                    rightActionLabel = "Remove",
                )
            )
        }
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
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
        metrics = listOf(
            TvSettingsMetric(manifests.size.toString(), "Installed"),
            TvSettingsMetric(enabledCount.toString(), "Enabled"),
            TvSettingsMetric(catalogCount.toString(), "Catalogs"),
            TvSettingsMetric(resourceCount.toString(), "Resources"),
        ),
        footer = "Select an addon to enable or disable it. Press right on an addon for Remove.",
    )
}

@Composable
internal fun TvProviderSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
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
    var selectedRepositoryUrl by remember { mutableStateOf<String?>(null) }
    var removeRepo by remember { mutableStateOf<PluginRepositoryDescriptor?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val repositories = remember(revision) { runtime.pluginStore.repositories() }
    val selectedRepository = repositories.firstOrNull { it.manifestUrl == selectedRepositoryUrl }
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val providerCodeStore = remember(context) { ProviderCodeStore(context.applicationContext) }
    var diagnosticTarget by remember { mutableStateOf<Pair<PluginRepositoryDescriptor, PluginProviderDescriptor>?>(null) }
    var showRuntimeDiagnostics by remember { mutableStateOf(false) }
    var refreshingRepositoryUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repositories, selectedRepositoryUrl) {
        if (selectedRepositoryUrl != null && selectedRepository == null) {
            selectedRepositoryUrl = null
        }
    }

    BackHandler(enabled = selectedRepositoryUrl != null) {
        selectedRepositoryUrl = null
    }

    if (showAdd) {
        TvTextEntryDialog(
            title = "Add Provider Repository",
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
                            status = "Installed ${repository.name}."
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
            onDismiss = { removeRepo = null },
            onConfirm = {
                removeRepo = null
                scope.launch {
                    runtime.removePluginRepository(repository)
                    if (selectedRepositoryUrl == repository.manifestUrl) {
                        selectedRepositoryUrl = null
                    }
                    revision++
                    status = "Removed ${repository.name}."
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
        if (selectedRepository == null) {
            add(toggleEntry("plugins-master", "Provider Plugins", "Master switch for plugin provider discovery.", pluginsEnabled) {
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
                    title = "Add Repository",
                    subtitle = "Install an HTTPS provider repository manifest.",
                    onActivate = {
                        addMessage = null
                        showAdd = true
                    },
                    section = "PROVIDER SYSTEM",
                    icon = Icons.Default.SettingsInputComponent,
                    accented = true,
                )
            )
            add(
                TvSettingsEntry(
                    id = "runtime-diagnostics",
                    title = "Performance & Crash Diagnostics",
                    subtitle = "Source scan timing, UI stalls, memory and crash evidence.",
                    value = "Open",
                    onActivate = { showRuntimeDiagnostics = true },
                    section = "PROVIDER SYSTEM",
                    icon = Icons.Default.SettingsInputComponent,
                    accented = true,
                )
            )
            repositories.forEach { repository ->
                val repoEnabled = runtime.pluginStore.isRepositoryEnabled(repository)
                val readyProviders = providerCodeStore.readyCount(repository)
                add(
                    TvSettingsEntry(
                        id = "repo-${repository.manifestUrl.hashCode()}",
                        title = repository.name,
                        subtitle = "v${repository.version} • ${repository.providers.size} providers • $readyProviders ready",
                        detail = repository.description?.trim()?.takeIf { it.isNotBlank() }
                            ?: shortUrl(repository.manifestUrl),
                        value = if (repoEnabled) "On" else "Off",
                        onActivate = {
                            selectedRepositoryUrl = repository.manifestUrl
                        },
                        section = "REPOSITORIES",
                        icon = Icons.Default.SettingsInputComponent,
                        onRightAction = { removeRepo = repository },
                        accented = true,
                        rightActionLabel = "Remove",
                    )
                )
            }
        } else {
            val repository = selectedRepository
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
                section = "REPOSITORY",
                icon = Icons.Default.SettingsInputComponent,
                accented = true,
                detail = "v${repository.version} • ${repository.providers.size} providers • ${providerCodeStore.readyCount(repository)} ready",
            ))
            add(
                TvSettingsEntry(
                    id = "refresh-repository",
                    title = "Refresh Repository",
                    subtitle = "Reload this manifest and refresh its provider code without changing preferences.",
                    value = if (refreshing) "Refreshing…" else "Refresh",
                    onActivate = {
                        if (!refreshing) {
                            refreshingRepositoryUrl = repository.manifestUrl
                            status = null
                            scope.launch {
                                runCatching { runtime.refreshPluginRepository(repository) }
                                    .onSuccess { refreshed ->
                                        selectedRepositoryUrl = refreshed.manifestUrl
                                        revision++
                                        status = "Refreshed ${refreshed.name} • ${providerCodeStore.readyCount(refreshed)} provider code ready."
                                        onDataChanged()
                                    }
                                    .onFailure { status = it.message ?: "Unable to refresh repository." }
                                refreshingRepositoryUrl = null
                            }
                        }
                    },
                    section = "REPOSITORY",
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
                    section = "REPOSITORY",
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

            rankedProviders.forEach { (provider, health) ->
                val providerEnabled = runtime.pluginStore.isProviderEnabled(repository, provider)
                add(
                    TvSettingsEntry(
                        id = "provider-${repository.manifestUrl.hashCode()}-${provider.id}",
                        title = provider.name,
                        subtitle = buildString {
                            append(if (providerEnabled) health?.status?.label ?: "No diagnostic yet" else "Disabled")
                            append(" • v").append(provider.version)
                            provider.supportedTypes.takeIf { it.isNotEmpty() }?.let { types ->
                                append(" • ").append(types.joinToString(" / ") { it.replaceFirstChar { char -> char.uppercase() } })
                            }
                        },
                        detail = provider.description?.trim()?.takeIf { it.isNotBlank() },
                        value = if (providerEnabled) "On" else "Off",
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
        status?.let { add(TvSettingsEntry("status", "Status", it, enabled = false, section = "STATUS")) }
    }

    val providerCount = repositories.sumOf { it.providers.size }
    val enabledProviderCount = if (pluginsEnabled) runtime.pluginStore.enabledProviderCount() else 0
    val readyProviderCount = repositories.sumOf { providerCodeStore.readyCount(it) }

    TvSettingsListScreen(
        title = selectedRepository?.name ?: "Plugins & Providers",
        subtitle = if (selectedRepository == null) {
            "Repositories, runtime providers and diagnostics."
        } else {
            "Repository controls and saved provider preferences."
        },
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = {
            if (selectedRepositoryUrl != null) selectedRepositoryUrl = null else onBack()
        },
        topLabel = "Content Manager",
        metrics = if (selectedRepository == null) {
            listOf(
                TvSettingsMetric(repositories.size.toString(), "Repos"),
                TvSettingsMetric(providerCount.toString(), "Providers"),
                TvSettingsMetric(enabledProviderCount.toString(), "Active"),
                TvSettingsMetric(readyProviderCount.toString(), "Ready"),
            )
        } else {
            listOf(
                TvSettingsMetric(selectedRepository.providers.size.toString(), "Providers"),
                TvSettingsMetric(
                    selectedRepository.providers.count {
                        runtime.pluginStore.isProviderEnabled(selectedRepository, it)
                    }.toString(),
                    "Enabled",
                ),
                TvSettingsMetric(providerCodeStore.readyCount(selectedRepository).toString(), "Ready"),
            )
        },
        footer = if (selectedRepository == null) {
            "OK opens a repository. Press right to remove it."
        } else {
            "OK changes a saved preference. Press right on a provider for diagnostics. Parent switches control discovery only."
        },
        preferredFocusId = selectedRepository?.let { "repo-${it.manifestUrl.hashCode()}" },
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
    val availableCatalogs = remember(revision, installedAddons) {
        installedAddons.flatMap { extension ->
            extension.descriptor.catalogs
                .filter { it.canLoadWithoutExtras }
                .map { catalog ->
                    TvCatalogDescriptorEntry(
                        key = "${extension.descriptor.id}:${catalog.type}:${catalog.id}",
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
            detail = "D-pad left/right reorders • OK ${if (enabled) "hide" else "show"}",
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
            badge = "${index + 1}",
            accented = true,
        )
    }

    val shownCount = order.count { runtime.content.isCatalogEnabled(it) }

    TvSettingsListScreen(
        title = "Catalog Order",
        subtitle = "Control Home visibility and ordering. Hidden catalogs keep their position.",
        entries = entries.ifEmpty {
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
        metrics = listOf(
            TvSettingsMetric(order.size.toString(), "Catalogs"),
            TvSettingsMetric(shownCount.toString(), "Shown"),
            TvSettingsMetric((order.size - shownCount).toString(), "Hidden"),
        ),
        footer = "The numbered badge is the Home position. Hidden catalogs keep their saved place in the order.",
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
