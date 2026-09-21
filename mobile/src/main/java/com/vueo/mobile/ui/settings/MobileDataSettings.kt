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
internal fun AboutVueoSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateStore = remember(context) { VueoUpdateStore(context.applicationContext) }
    var release by remember { mutableStateOf(updateStore.latestRelease()) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var updateMessage by remember { mutableStateOf<String?>(updateStore.lastError()) }
    val availableRelease = release?.takeIf { it.isNewerThanCurrent() }

    val updateAction: () -> Unit = updateAction@{
        if (checking || downloading) return@updateAction

        if (availableRelease != null) {
            if (VueoUpdateManager.needsInstallPermission(context)) {
                VueoUpdateManager.openInstallPermissionSettings(context)
                updateMessage = "Allow installs for VUEO, then tap Update again."
                return@updateAction
            }

            downloading = true
            downloadProgress = 0
            updateMessage = null
            scope.launch {
                VueoUpdateManager.downloadAndInstall(
                    context = context.applicationContext,
                    release = availableRelease,
                    onProgress = { downloadProgress = it },
                ).onFailure {
                    updateMessage = it.message ?: "Unable to install update."
                }
                downloading = false
            }
            return@updateAction
        }

        checking = true
        updateMessage = null
        scope.launch {
            val result = VueoUpdateManager.check(
                context = context.applicationContext,
                force = true,
            )
            release = result.release
            checking = false
            updateMessage = when {
                result.error != null -> result.error
                result.release?.isNewerThanCurrent() == true ->
                    "VUEO ${result.release.versionName} is ready to install."
                else -> "You're up to date."
            }
        }
    }

    VueoSettingsPage(
        title = "About VUEO",
        subtitle = "Version, updates and app information.",
        onBack = onBack,
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = VueoPalette.Surface,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("VUEO", modifier = Modifier.weight(1f), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(BuildConfig.VERSION_NAME, color = VueoPalette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("A universal media player.", color = VueoPalette.Muted, fontSize = 10.5.sp)
                    }

                    VueoAboutDivider()
                    VueoAboutRow(
                        title = "Check for updates",
                        detail = updateMessage,
                        value = when {
                            checking -> "Checking…"
                            downloading -> "$downloadProgress%"
                            availableRelease != null && VueoUpdateManager.needsInstallPermission(context) -> "Allow"
                            availableRelease != null -> "Update ${availableRelease.versionName}"
                            else -> "Up to date"
                        },
                        onClick = updateAction,
                    )
                    VueoAboutDivider()
                    VueoAboutRow(
                        title = "Privacy",
                        detail = "Settings and API keys stay on this device. API keys are excluded from backups unless you include them.",
                    )
                    VueoAboutDivider()
                    VueoAboutRow(
                        title = "TMDB Attribution",
                        detail = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoAboutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(VueoPalette.Stroke.copy(alpha = .55f))
    )
}

@Composable
private fun VueoAboutRow(
    title: String,
    detail: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = VueoPalette.Muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2)
            }
        }
        value?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, color = VueoPalette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
