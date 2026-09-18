package com.vueo.mobile.ui

// VUEO DNA OPTIONAL SETTINGS PATCH - 2026-08-29

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.mobile.BuildConfig
import com.vueo.mobile.core.extensions.CatalogDiscoveryCache
import com.vueo.mobile.core.extensions.SourceDiscoveryCache
import com.vueo.mobile.core.extensions.UnifiedMediaEngine
import com.vueo.mobile.core.enrichment.MdblistClient
import com.vueo.mobile.core.enrichment.TmdbEnhancementClient
import com.vueo.mobile.core.dna.UserDnaEngine
import com.vueo.mobile.core.dna.UserDnaPreferences
import com.vueo.mobile.core.dna.UserDnaSnapshot
import com.vueo.mobile.core.plugin.PluginStore
import com.vueo.mobile.core.storage.AppAccent
import com.vueo.mobile.core.storage.AppTheme
import com.vueo.mobile.core.storage.LibraryStore
import com.vueo.mobile.core.storage.ProfileStore
import com.vueo.mobile.core.storage.PreferredQuality
import com.vueo.mobile.core.storage.PlayerOrientation
import com.vueo.mobile.core.storage.SettingsStore
import com.vueo.mobile.core.storage.SubtitleLanguage
import com.vueo.mobile.core.storage.SubtitleSize
import com.vueo.mobile.core.storage.SubtitleVisibility
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.mobile.core.update.VueoUpdateManager
import com.vueo.mobile.core.update.VueoUpdateStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DataStorageSettingsScreen(
    libraryStore: LibraryStore,
    settingsStore: SettingsStore,
    onLibraryChanged: () -> Unit,
    onCatalogCacheCleared: () -> Unit,
    onPersistentDataChanged: suspend () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmAction by remember {
        mutableStateOf<DataClearAction?>(null)
    }
    var pendingRestoreUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var showResetConfirm by remember {
        mutableStateOf(false)
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var includeCredentials by remember {
        mutableStateOf(
            settingsStore.includeCredentialsInBackup()
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/json"
        ),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                feedback = null

                runCatching {
                    VueoBackupManager.exportToUri(
                        context = context.applicationContext,
                        uri = uri,
                        includeCredentials = includeCredentials,
                    )
                }.onSuccess { summary ->
                    feedback = buildString {
                        append("Backup created with ")
                        append(summary.valueCount)
                        append(" saved values")
                        if (summary.includesCredentials) {
                            append(", including API keys.")
                        } else {
                            append(". API keys were excluded.")
                        }
                    }
                }.onFailure { error ->
                    feedback = error.message
                        ?: "Unable to create backup."
                }

                busy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            DataClearAction.CATALOG_CACHE -> {
                                scope.launch {
                                    CatalogDiscoveryCache.clearAll(
                                        context.applicationContext
                                    )
                                    onCatalogCacheCleared()
                                    feedback = "Catalog and search cache cleared."
                                }
                            }

                            DataClearAction.SOURCE_CACHE -> {
                                SourceDiscoveryCache.clearAll()
                                feedback = "Recent source cache cleared."
                            }

                            DataClearAction.CONTINUE_WATCHING -> {
                                libraryStore.clearContinueWatching()
                                onLibraryChanged()
                                feedback = "Continue Watching cleared."
                            }

                            DataClearAction.WATCH_HISTORY -> {
                                libraryStore.clearHistory()
                                onLibraryChanged()
                                feedback = "Watch History cleared."
                            }
                        }
                        confirmAction = null
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingRestoreUri = null
                }
            },
            title = {
                Text("Restore VUEO backup?")
            },
            text = {
                Text(
                    "Current VUEO profiles, configuration, Library and playback progress will be replaced by the selected backup. Temporary caches are rebuilt automatically."
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            feedback = null

                            runCatching {
                                val summary =
                                    VueoBackupManager.restoreFromUri(
                                        context = context.applicationContext,
                                        uri = uri,
                                    )

                                onPersistentDataChanged()
                                summary
                            }.onSuccess { summary ->
                                feedback = buildString {
                                    append("Backup restored")
                                    summary.sourceVersion
                                        ?.let {
                                            append(" from VUEO ")
                                            append(it)
                                        }
                                    append(". ")
                                    append(summary.valueCount)
                                    append(" values restored.")
                                }
                                pendingRestoreUri = null
                            }.onFailure { error ->
                                feedback = error.message
                                    ?: "Unable to restore this backup."
                            }

                            busy = false
                        }
                    },
                ) {
                    Text(
                        if (busy) {
                            "Restoring..."
                        } else {
                            "Restore"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingRestoreUri = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showResetConfirm = false
                }
            },
            title = {
                Text("Reset VUEO data?")
            },
            text = {
                Text(
                    "This clears profiles, addons, plugin repositories, API keys, preferences, My List, Continue Watching, history, playback progress and temporary data. Development defaults will be seeded again."
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            feedback = null

                            runCatching {
                                VueoBackupManager.resetUserData(
                                    context.applicationContext
                                )
                                onPersistentDataChanged()
                            }.onSuccess {
                                includeCredentials = false
                                feedback = "VUEO data reset to a fresh state."
                                showResetConfirm = false
                            }.onFailure { error ->
                                feedback = error.message
                                    ?: "Unable to reset VUEO data."
                            }

                            busy = false
                        }
                    },
                ) {
                    Text(
                        if (busy) {
                            "Resetting..."
                        } else {
                            "Reset"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showResetConfirm = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Data & Storage",
        subtitle = "Backup, restore, cache, history, and local data controls.",
        onBack = onBack,
    ) {
        feedback?.let { message ->
            item {
                VueoInfoCard(
                    title = if (busy) "Working" else "Status",
                    text = message,
                )
            }
        }

        if (busy) {
            item {
                VueoInfoCard(
                    title = "Working",
                    text = "Keep VUEO open while this data operation finishes.",
                )
            }
        }

        item {
            VueoSectionLabel("BACKUP & RESTORE")
        }

        item {
            VueoInfoCard(
                title = "What gets backed up",
                text = "Profiles, Content Manager configuration, provider preferences, Settings, My List, Continue Watching, Watch History and playback progress. Cache, provider scripts and health diagnostics are rebuilt instead of copied.",
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Include API Keys",
                subtitle = "Off by default. Enable only when you want TMDB and MDBList keys written into the backup file.",
                checked = includeCredentials,
                onCheckedChange = {
                    includeCredentials = it
                    settingsStore.setIncludeCredentialsInBackup(it)
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Create Backup",
                subtitle = if (includeCredentials) {
                    "Export VUEO data as JSON. This backup will include configured API keys."
                } else {
                    "Export VUEO data as JSON without API keys."
                },
                action = "Export",
                onClick = {
                    if (!busy) {
                        exportLauncher.launch(
                            vueoBackupFileName()
                        )
                    }
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Restore Backup",
                subtitle = "Choose a VUEO JSON backup. Restored sources are reloaded without requiring an app reinstall.",
                action = "Restore",
                onClick = {
                    if (!busy) {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/json",
                                "text/plain",
                                "application/octet-stream",
                            )
                        )
                    }
                },
            )
        }

        item {
            VueoSectionLabel("CACHE & HISTORY")
        }

        item {
            VueoSettingsActionCard(
                title = "Catalog & Search Cache",
                subtitle = "Clear the persistent Home snapshot and in-memory search cache.",
                action = "Clear",
                onClick = {
                    if (!busy) {
                        confirmAction = DataClearAction.CATALOG_CACHE
                    }
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Recent Source Cache",
                subtitle = "Discard short-lived source results used to speed up repeat searches.",
                action = "Clear",
                onClick = {
                    if (!busy) {
                        confirmAction = DataClearAction.SOURCE_CACHE
                    }
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Continue Watching",
                subtitle = "Remove unfinished playback entries for the active profile only.",
                action = "Clear",
                onClick = {
                    if (!busy) {
                        confirmAction = DataClearAction.CONTINUE_WATCHING
                    }
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Watch History",
                subtitle = "Clear playback history for the active profile without changing My List.",
                action = "Clear",
                onClick = {
                    if (!busy) {
                        confirmAction = DataClearAction.WATCH_HISTORY
                    }
                },
            )
        }

        item {
            VueoSectionLabel("RESET")
        }

        item {
            VueoSettingsActionCard(
                title = "Reset VUEO Data",
                subtitle = "Return local configuration and Library data to a fresh state without uninstalling the APK.",
                action = "Reset",
                onClick = {
                    if (!busy) {
                        showResetConfirm = true
                    }
                },
            )
        }
    }
}

@Composable
internal fun UpdatesSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context =
        LocalContext.current
    val scope =
        rememberCoroutineScope()
    val updateStore =
        remember(context) {
            VueoUpdateStore(
                context.applicationContext
            )
        }

    var automaticChecks by remember {
        mutableStateOf(
            settingsStore
                .automaticUpdateChecksEnabled()
        )
    }
    var release by remember {
        mutableStateOf(
            updateStore.latestRelease()
        )
    }
    var checking by remember {
        mutableStateOf(false)
    }
    var downloading by remember {
        mutableStateOf(false)
    }
    var downloadProgress by remember {
        mutableStateOf(0)
    }
    var statusMessage by remember {
        mutableStateOf<String?>(
            null
        )
    }
    var errorMessage by remember {
        mutableStateOf<String?>(
            updateStore.lastError()
        )
    }

    val updateAvailable: Boolean =
        release
            ?.isNewerThanCurrent()
            ?: false

    VueoSettingsPage(
        title = "Updates",
        subtitle =
            "Fast VUEO development updates.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title =
                    "Current Version",
                value =
                    BuildConfig.VERSION_NAME,
                text =
                    "Build ${BuildConfig.VERSION_CODE}. Updates install over the existing app and keep local VUEO data.",
            )
        }

        item {
            VueoSettingsToggleCard(
                title =
                    "Automatic Update Checks",
                subtitle =
                    "Check the VUEO Dev channel in the background. Checks are rate-limited to avoid unnecessary network use.",
                checked =
                    automaticChecks,
                onCheckedChange = {
                    automaticChecks = it
                    settingsStore
                        .setAutomaticUpdateChecksEnabled(
                            it
                        )
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title =
                    "Check for Updates",
                subtitle =
                    if (
                        updateAvailable
                    ) {
                        "VUEO ${release?.versionName} is available."
                    } else {
                        "Check the latest green VUEO Dev build."
                    },
                action =
                    if (checking) {
                        "Checking..."
                    } else {
                        "Check"
                    },
                onClick = {
                    if (
                        !checking
                    ) {
                        checking = true
                        statusMessage =
                            null
                        errorMessage =
                            null

                        scope.launch {
                            val result =
                                VueoUpdateManager
                                    .check(
                                        context =
                                            context.applicationContext,
                                        force =
                                            true,
                                    )

                            release =
                                result.release
                            checking = false

                            val hasNewerRelease: Boolean =
                                result.release
                                    ?.isNewerThanCurrent()
                                    ?: false

                            if (
                                result.error !=
                                null
                            ) {
                                errorMessage =
                                    result.error
                            } else if (
                                hasNewerRelease
                            ) {
                                statusMessage =
                                    "Update ready."
                            } else {
                                statusMessage =
                                    "You're up to date."
                            }
                        }
                    }
                },
            )
        }

        val availableRelease =
            release
                ?.takeIf {
                    it.isNewerThanCurrent()
                }

        if (
            availableRelease != null
        ) {
            item {
                VueoStatusCard(
                    title =
                        "Update Available",
                    value =
                        availableRelease
                            .versionName,
                    text =
                        availableRelease
                            .changelog
                            .take(4)
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.joinToString(
                                "\n• ",
                                prefix = "• ",
                            )
                            ?: "Latest green VUEO development build.",
                )
            }

            item {
                VueoSettingsActionCard(
                    title =
                        "Download & Install",
                    subtitle =
                        if (
                            downloading
                        ) {
                            "Downloading signed APK. Keep VUEO open until Android's installer appears."
                        } else {
                            "Download the verified APK and hand it to Android's system installer."
                        },
                    action =
                        when {
                            downloading ->
                                "$downloadProgress%"

                            VueoUpdateManager
                                .needsInstallPermission(
                                    context
                                ) ->
                                "Allow"

                            else ->
                                "Update"
                        },
                    onClick = downloadClick@{
                        if (downloading) {
                            return@downloadClick
                        }

                        if (
                            VueoUpdateManager
                                .needsInstallPermission(
                                    context
                                )
                        ) {
                            VueoUpdateManager
                                .openInstallPermissionSettings(
                                    context
                                )
                            statusMessage =
                                "Allow installs for VUEO, then return and tap Update again."
                            return@downloadClick
                        }

                        val target =
                            availableRelease

                        downloading =
                            true
                        downloadProgress =
                            0
                        errorMessage =
                            null
                        statusMessage =
                            null

                        scope.launch {
                            val result =
                                VueoUpdateManager
                                    .downloadAndInstall(
                                        context =
                                            context.applicationContext,
                                        release =
                                            target,
                                        onProgress = {
                                            progress ->
                                            downloadProgress =
                                                progress
                                        },
                                    )

                            downloading =
                                false

                            result
                                .onFailure {
                                    failure ->
                                    errorMessage =
                                        failure.message
                                            ?: "Unable to install update."
                                }
                        }
                    },
                )
            }
        }

        statusMessage
            ?.let {
                message ->
                item {
                    VueoInfoCard(
                        title = "Status",
                        text = message,
                    )
                }
            }

        errorMessage
            ?.let {
                message ->
                item {
                    VueoInfoCard(
                        title =
                            "Update Error",
                        text =
                            message,
                    )
                }
            }

        item {
            VueoInfoCard(
                title =
                    "Android confirmation",
                text =
                    "Android requires a final system confirmation before an APK update is installed. The first update may also ask you to allow installs from VUEO.",
            )
        }
    }
}

@Composable
internal fun AboutVueoSettingsScreen(
    onBack: () -> Unit,
) {
    VueoSettingsPage(
        title = "About VUEO",
        subtitle = "App and architecture information.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "VUEO",
                value = BuildConfig.VERSION_NAME,
                text = "A universal media frontend built around open content sources, progressive source discovery, and direct playback.",
            )
        }

        item {
            VueoInfoCard(
                title = "Architecture",
                text = "Built-in VUEO features, addons, provider plugins, Unified Source Engine, Smart Source Ranking, and modern playback.",
            )
        }

        item {
            VueoInfoCard(
                title = "Privacy",
                text = "Settings and API keys are stored locally on the device. VUEO backups exclude API keys by default and include them only when the user explicitly enables that option.",
            )
        }

        item {
            VueoInfoCard(
                title = "TMDB Attribution",
                text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            )
        }
    }
}

