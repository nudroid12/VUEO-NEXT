package com.vueo.tv.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
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

    var backupResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    var saveDialog by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupChoices by remember { mutableStateOf<List<TvLocalBackup>?>(null) }
    var backupListError by remember { mutableStateOf<String?>(null) }
    var selectedBackup by remember { mutableStateOf<TvLocalBackup?>(null) }

    fun restoreBackup(uri: Uri) {
        if (backupBusy) return
        backupBusy = true
        backupProgress = "Restoring backup…"
        status = null
        scope.launch {
            try {
                val result = VueoBackupManager.restoreFromUri(context, uri)
                runtime.reloadPersistentConfiguration()
                TvDesign.applyTheme(runtime.settingsStore.appTheme())
                TvDesign.applyAccent(runtime.settingsStore.appAccent())
                includeCredentials = runtime.settingsStore.includeCredentialsInBackup()
                backupResult = "Backup restored" to "${result.valueCount} values restored."
                onDataChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                backupResult = "Restore failed" to (error.message ?: "Unable to restore backup.")
            } finally {
                backupProgress = null
                backupBusy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreSettingsFocus()
        if (uri != null) selectedBackup = TvLocalBackup("The selected backup", uri)
    }
    backupChoices?.let { choices ->
        TvRestoreBackupPicker(
            files = choices,
            message = backupListError,
            onPick = { backupChoices = null; selectedBackup = it },
            onBrowse = {
                backupChoices = null
                try {
                    restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                } catch (_: ActivityNotFoundException) {
                    backupResult = "File picker unavailable" to "Choose a backup saved by VUEO in Restore Backup."
                    restoreSettingsFocus()
                }
            },
            onDismiss = { backupChoices = null; restoreSettingsFocus() },
        )
    }
    selectedBackup?.let { backup ->
        TvConfirmDialog(
            title = "Restore this backup?",
            message = "${backup.name} will replace current local VUEO data.",
            confirmLabel = "Restore",
            onDismiss = { selectedBackup = null },
            onConfirm = { selectedBackup = null; restoreBackup(backup.uri) },
        )
    }

    if (saveDialog) {
        TvSaveBackupDialog(
            includeCredentials = includeCredentials,
            onDismiss = { saveDialog = false; restoreSettingsFocus() },
            onSave = { name, folder, destination ->
                saveDialog = false
                if (!backupBusy) {
                    backupBusy = true
                    backupProgress = "Saving backup…"
                    val include = includeCredentials
                    scope.launch {
                        try {
                            val backup = TvLocalBackups.save(context, name, folder, include)
                            backupResult = "Backup saved" to "$destination/${backup.name}"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            backupResult = "Backup failed" to (error.message ?: "Unable to create backup.")
                        } finally {
                            backupProgress = null
                            backupBusy = false
                        }
                    }
                }
            },
        )
    }

    backupProgress?.let { TvBackupProgressDialog(it) }

    backupResult?.let { (title, message) ->
        TvBackupResultDialog(title, message) {
            backupResult = null
            restoreSettingsFocus()
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
        add(TvSettingsEntry("export", "Create Backup", "Save a JSON backup to ${TvLocalBackups.locationLabel}.",
            if (backupBusy) "Please wait…" else "Export", onActivate = {
                if (!backupBusy) saveDialog = true
            }, section = "BACKUP & RESTORE"))
        add(TvSettingsEntry("restore", "Restore Backup", "Choose a saved VUEO backup or browse for a JSON file.", "Restore", onActivate = {
            if (!backupBusy) {
                backupBusy = true
                backupListError = null
                scope.launch {
                    try {
                        backupChoices = TvLocalBackups.list(context)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        backupListError = "Unable to read backups in ${TvLocalBackups.locationLabel}."
                        backupChoices = emptyList()
                    } finally {
                        backupBusy = false
                    }
                }
            }
        }, section = "BACKUP & RESTORE"))
        add(TvSettingsEntry("clear-cache", "Catalog & Source Cache", "Clear catalog and source-discovery caches; configuration is preserved.", "Clear", onActivate = { confirmAction = "cache" }, section = "LOCAL DATA", icon = Icons.Default.SettingsInputComponent))
        add(TvSettingsEntry("clear-continue", "Continue Watching", "Remove unfinished playback entries for the active profile only.", "Clear", onActivate = { confirmAction = "continue" }, section = "LOCAL DATA"))
        add(TvSettingsEntry("clear-history", "Watch History", "Clear playback history for the active profile without changing Watchlist.", "Clear", onActivate = { confirmAction = "history" }, section = "LOCAL DATA"))
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

    var details by remember { mutableStateOf<TvAboutDetails?>(null) }
    val entries = listOf(
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
        ),
        TvSettingsEntry("privacy", "Privacy", "View how VUEO handles app data.",
            onActivate = { details = TvAboutDetails.PRIVACY }),
        TvSettingsEntry("licenses", "Licenses & Attribution", "View data source credits and open source licenses.",
            onActivate = { details = TvAboutDetails.LICENSES }),
    )
    val host = LocalTvSettingsEmbeddedHost.current
    if (host != null) {
        TvAboutPanel(entries, host)
    } else {
        TvSettingsListScreen("About VUEO", "App information, updates, and legal links",
            entries, onNavigate, onProfile, onBack, preferredFocusId = "update")
    }

    details?.let { page ->
        TvAboutDetailsDialog(page) {
            details = null
            restoreSettingsFocus()
        }
    }
}
