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
    onResetComplete: () -> Unit,
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
                    includeCredentials = runtime.settingsStore.includeCredentialsInBackup()
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
                            includeCredentials = runtime.settingsStore.includeCredentialsInBackup()
                        }
                    }
                    status = when (action) {
                        "history" -> "Watch History cleared."
                        "continue" -> "Continue Watching cleared."
                        "cache" -> "Caches cleared."
                        else -> "VUEO local data reset."
                    }
                    onDataChanged()
                    if (action == "reset") onResetComplete()
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
internal fun TvAboutSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { restoreSettingsFocus() }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var release by remember { mutableStateOf<TvUpdateRelease?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val available = release?.takeIf { it.isNewerThanCurrent() }

    fun updateAction() {
        if (checking || downloading) return
        if (available != null) {
            if (TvUpdateManager.needsInstallPermission(context)) {
                TvUpdateManager.installPermissionIntent(context)?.let { installPermissionLauncher.launch(it) }
                status = "Allow installs for VUEO, then choose Update again."
                return
            }
            downloading = true
            progress = 0
            status = null
            scope.launch {
                TvUpdateManager.downloadAndInstall(context.applicationContext, available) { progress = it }
                    .onFailure { status = it.message ?: "Unable to install update." }
                downloading = false
            }
            return
        }

        checking = true
        status = null
        scope.launch {
            val result = TvUpdateManager.check(context.applicationContext, force = true)
            release = result.release
            checking = false
            status = when {
                result.error != null -> result.error
                result.release?.isNewerThanCurrent() == true -> "VUEO ${result.release.versionName} is ready to install."
                else -> "You're up to date."
            }
        }
    }

    val entries = listOf(
        TvSettingsEntry("vueo", "VUEO", "A universal media player.", BuildConfig.VERSION_NAME, enabled = false, section = "ABOUT", icon = Icons.Default.Settings),
        TvSettingsEntry(
            "update",
            "Check for Updates",
            status ?: "Check and install the latest VUEO build.",
            when {
                checking -> "Checking…"
                downloading -> "$progress%"
                available != null && TvUpdateManager.needsInstallPermission(context) -> "Allow"
                available != null -> "Update ${available.versionName}"
                status == "You're up to date." -> "Up to date"
                else -> "Check"
            },
            onActivate = ::updateAction,
            section = "ABOUT",
            icon = Icons.Default.Refresh,
        ),
        TvSettingsEntry("privacy", "Privacy", "Settings and API keys stay on this device. API keys are excluded from backups unless you include them.", "Local", enabled = false, section = "ABOUT"),
        TvSettingsEntry("tmdb", "TMDB Attribution", "This product uses the TMDB API but is not endorsed or certified by TMDB.", "TMDB", enabled = false, section = "ABOUT"),
    )
    TvSettingsListScreen("About VUEO", "Version, updates and app information.", entries, onNavigate, onProfile, onBack)
}
