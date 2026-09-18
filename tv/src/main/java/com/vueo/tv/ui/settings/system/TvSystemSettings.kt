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
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TvDataStorageSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val store = runtime.settingsStore
    var includeCredentials by remember { mutableStateOf(store.includeCredentialsInBackup()) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        restoreSettingsFocus()
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { VueoBackupManager.exportToUri(context, uri, includeCredentials) }
                .onSuccess { status = "Backup created: ${it.valueCount} values across ${it.preferenceGroups} groups." }
                .onFailure { status = it.message ?: "Unable to create backup." }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreSettingsFocus()
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
internal fun TvUpdatesSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        restoreSettingsFocus()
    }
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
        add(TvSettingsEntry("check", "Check for Updates", "Check the latest green VUEO development build.", if (checking) "Checking…" else "Check", onActivate = ::checkNow, section = "UPDATES"))
        if (available != null) {
            add(
                TvSettingsEntry(
                    "install", "Download & Install", available.title,
                    when {
                        downloading -> "$progress%"
                        TvUpdateManager.needsInstallPermission(context) -> "Allow"
                        else -> "Update"
                    },
                    onActivate = {
                        if (!downloading) {
                            if (TvUpdateManager.needsInstallPermission(context)) {
                                TvUpdateManager.installPermissionIntent(context)?.let { installPermissionLauncher.launch(it) }
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
internal fun TvAboutSettings(
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

