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
internal fun PlaybackSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var resume by remember {
        mutableStateOf(settingsStore.resumePlaybackEnabled())
    }
    var quality by remember {
        mutableStateOf(settingsStore.preferredQuality())
    }
    var showQualityDialog by remember {
        mutableStateOf(false)
    }
    var orientation by remember {
        mutableStateOf(
            settingsStore.playerOrientation()
        )
    }
    var autoRecovery by remember {
        mutableStateOf(
            settingsStore.autoSourceRecoveryEnabled()
        )
    }
    var autoPlayNextEpisode by remember {
        mutableStateOf(
            settingsStore.autoPlayNextEpisodeEnabled()
        )
    }
    var skipSegments by remember {
        mutableStateOf(settingsStore.skipSegmentsEnabled())
    }
    var showOrientationDialog by remember {
        mutableStateOf(false)
    }
    var seekSensitivity by remember {
        mutableStateOf(context.seekGestureSensitivity())
    }
    var showSeekSensitivityDialog by remember {
        mutableStateOf(false)
    }
    var contentWarnings by remember(settingsStore) {
        context.migrateLegacyContentWarningsToShared(settingsStore)
        mutableStateOf(settingsStore.contentWarningsEnabled())
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Preferred Quality") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PreferredQuality.values().forEach { option ->
                        VueoChoiceRow(
                            label = option.label,
                            selected = quality == option,
                            onClick = {
                                quality = option
                                settingsStore.setPreferredQuality(option)
                                showQualityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showOrientationDialog) {
        AlertDialog(
            onDismissRequest = {
                showOrientationDialog = false
            },
            title = {
                Text("Player Orientation")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    PlayerOrientation.values()
                        .forEach { option ->
                            VueoChoiceRow(
                                label = option.label,
                                selected =
                                    orientation == option,
                                onClick = {
                                    orientation = option
                                    settingsStore
                                        .setPlayerOrientation(
                                            option
                                        )
                                    showOrientationDialog =
                                        false
                                },
                            )
                        }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOrientationDialog = false
                    },
                ) {
                    Text("Close")
                }
            },
        )
    }

    if (showSeekSensitivityDialog) {
        AlertDialog(
            onDismissRequest = {
                showSeekSensitivityDialog = false
            },
            title = {
                Text("Seek Gesture Sensitivity")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    SeekGestureSensitivity.values()
                        .forEach { option ->
                            VueoChoiceRow(
                                label = when (option) {
                                    SeekGestureSensitivity.LOW ->
                                        "Low • precise"
                                    SeekGestureSensitivity.NORMAL ->
                                        "Normal • balanced"
                                    SeekGestureSensitivity.HIGH ->
                                        "High • faster"
                                },
                                selected =
                                    seekSensitivity == option,
                                onClick = {
                                    seekSensitivity = option
                                    context
                                        .setSeekGestureSensitivity(
                                            option
                                        )
                                    showSeekSensitivityDialog =
                                        false
                                },
                            )
                        }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSeekSensitivityDialog = false
                    },
                ) {
                    Text("Close")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Playback",
        subtitle = "Player behavior and quality preference.",
        onBack = onBack,
    ) {
        item {
            VueoSettingsToggleCard(
                title = "Resume Playback",
                subtitle = "Ask to continue from a saved position when reopening a title.",
                checked = resume,
                onCheckedChange = {
                    resume = it
                    settingsStore.setResumePlaybackEnabled(it)
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Preferred Quality",
                subtitle = "Prefer this resolution. Auto ranking avoids known quality below 720p unless you choose it manually.",
                value = quality.label,
                onClick = { showQualityDialog = true },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Player Orientation",
                subtitle = "Choose how VUEO enters playback on mobile.",
                value = orientation.label,
                onClick = {
                    showOrientationDialog = true
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Seek Gesture Sensitivity",
                subtitle = "Control how far horizontal swipes seek through a video.",
                value = seekSensitivity.label,
                onClick = {
                    showSeekSensitivityDialog = true
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Content Warnings",
                subtitle = "Show available parental guidance briefly when playback starts.",
                checked = contentWarnings,
                onCheckedChange = { enabled ->
                    contentWarnings = enabled
                    settingsStore.setContentWarningsEnabled(enabled)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Skip Intro & Ending",
                subtitle = "Show contextual skip controls when verified intro, recap, or ending timestamps are available.",
                checked = skipSegments,
                onCheckedChange = { enabled ->
                    skipSegments = enabled
                    settingsStore.setSkipSegmentsEnabled(enabled)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Auto-play Next Episode",
                subtitle = "Start the next episode after an 8-second countdown when playback ends.",
                checked = autoPlayNextEpisode,
                onCheckedChange = { enabled ->
                    autoPlayNextEpisode = enabled
                    settingsStore
                        .setAutoPlayNextEpisodeEnabled(enabled)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Auto Source Recovery",
                subtitle = "Try up to two ranked alternatives after an error or timeout while keeping the same timestamp.",
                checked = autoRecovery,
                onCheckedChange = { enabled ->
                    autoRecovery = enabled
                    settingsStore
                        .setAutoSourceRecoveryEnabled(
                            enabled
                        )
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Deterministic source selection",
                text = "VUEO prioritises direct 1080p and 720p streams, accepts Auto or Unknown quality, and keeps lower known resolutions for manual selection.",
            )
        }
    }
}

@Composable
internal fun SubtitleSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var preferred by remember {
        mutableStateOf(settingsStore.preferredSubtitleLanguage())
    }
    var secondary by remember {
        mutableStateOf(settingsStore.secondarySubtitleLanguage())
    }
    var defaultOn by remember {
        mutableStateOf(settingsStore.subtitlesOnByDefault())
    }
    var autoSelect by remember {
        mutableStateOf(settingsStore.autoSelectPreferredSubtitle())
    }
    var embeddedPriority by remember {
        mutableStateOf(settingsStore.embeddedSubtitlePriority())
    }
    var size by remember {
        mutableStateOf(settingsStore.subtitleSize())
    }
    var visibility by remember {
        mutableStateOf(settingsStore.subtitleVisibility())
    }
    var languageDialog by remember {
        mutableStateOf<SubtitleLanguageTarget?>(null)
    }
    var showSizeDialog by remember {
        mutableStateOf(false)
    }
    var showVisibilityDialog by remember {
        mutableStateOf(false)
    }

    languageDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { languageDialog = null },
            title = {
                Text(
                    if (target == SubtitleLanguageTarget.PRIMARY) {
                        "Preferred Language"
                    } else {
                        "Secondary Language"
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    SubtitleLanguage.values().forEach { option ->
                        val selected = if (target == SubtitleLanguageTarget.PRIMARY) {
                            preferred == option
                        } else {
                            secondary == option
                        }

                        VueoChoiceRow(
                            label = option.label,
                            selected = selected,
                            onClick = {
                                if (target == SubtitleLanguageTarget.PRIMARY) {
                                    preferred = option
                                    settingsStore.setPreferredSubtitleLanguage(option)
                                } else {
                                    secondary = option
                                    settingsStore.setSecondarySubtitleLanguage(option)
                                }
                                languageDialog = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = null }) {
                    Text("Close")
                }
            },
        )
    }

    if (showVisibilityDialog) {
        AlertDialog(
            onDismissRequest = { showVisibilityDialog = false },
            title = { Text("Subtitle Visibility") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SubtitleVisibility.values().forEach { option ->
                        VueoChoiceRow(
                            label = option.label,
                            selected = visibility == option,
                            onClick = {
                                visibility = option
                                settingsStore.setSubtitleVisibility(option)
                                showVisibilityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVisibilityDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text("Subtitle Size") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SubtitleSize.values().forEach { option ->
                        VueoChoiceRow(
                            label = option.label,
                            selected = size == option,
                            onClick = {
                                size = option
                                settingsStore.setSubtitleSize(option)
                                showSizeDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Subtitles",
        subtitle = "Subtitle behavior is separate from subtitle providers in Content Manager.",
        onBack = onBack,
    ) {
        item {
            VueoSettingsValueCard(
                title = "Preferred Language",
                subtitle = "First language VUEO should prefer when subtitle tracks are available.",
                value = preferred.label,
                onClick = {
                    languageDialog = SubtitleLanguageTarget.PRIMARY
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Secondary Language",
                subtitle = "Fallback language when the preferred language is unavailable.",
                value = secondary.label,
                onClick = {
                    languageDialog = SubtitleLanguageTarget.SECONDARY
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Subtitle Visibility",
                subtitle = "Choose whether the player lists only your preferred language or every discovered subtitle language.",
                value = visibility.label,
                onClick = { showVisibilityDialog = true },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Subtitles On by Default",
                subtitle = "Prefer showing subtitles automatically when a suitable track exists.",
                checked = defaultOn,
                onCheckedChange = {
                    defaultOn = it
                    settingsStore.setSubtitlesOnByDefault(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Auto Select Preferred Language",
                subtitle = "Prioritize your preferred subtitle language automatically.",
                checked = autoSelect,
                onCheckedChange = {
                    autoSelect = it
                    settingsStore.setAutoSelectPreferredSubtitle(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Embedded Subtitle Priority",
                subtitle = "Prefer subtitle tracks already included in the stream before external tracks when possible.",
                checked = embeddedPriority,
                onCheckedChange = {
                    embeddedPriority = it
                    settingsStore.setEmbeddedSubtitlePriority(it)
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Subtitle Size",
                subtitle = "Saved display size preference for the VUEO player.",
                value = size.label,
                onClick = { showSizeDialog = true },
            )
        }

        item {
            VueoInfoCard(
                title = "Subtitle sources",
                text = "OpenSubtitles and other subtitle addons remain in Content Manager. This page only controls how VUEO chooses and displays discovered subtitle tracks.",
            )
        }
    }
}

@Composable
internal fun SourceSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var technicalDetails by remember {
        mutableStateOf(settingsStore.showSourceTechnicalDetails())
    }

    VueoSettingsPage(
        title = "Sources",
        subtitle = "Discovery and Smart Source behavior.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Smart Source Ranking",
                value = "Active",
                text = "VUEO ranks direct playability, resolution, HDR, codec information, provider health, response latency, and your preferred quality.",
            )
        }

        item {
            VueoStatusCard(
                title = "Provider Health Influence",
                value = "Active",
                text = "Healthy and responsive providers receive a ranking advantage without blocking other available sources.",
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Technical Source Details",
                subtitle = "Show codec, HDR, and audio information on Source Picker cards.",
                checked = technicalDetails,
                onCheckedChange = {
                    technicalDetails = it
                    settingsStore.setShowSourceTechnicalDetails(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Progressive discovery",
                text = "The Source Picker opens immediately and updates while providers continue searching. Slow providers do not need to block fast ones.",
            )
        }
    }
}

@Composable
internal fun AppearanceSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var theme by remember { mutableStateOf(settingsStore.appTheme()) }
    var accent by remember { mutableStateOf(settingsStore.appAccent()) }

    VueoSettingsPage(
        title = "Appearance",
        subtitle = "Choose a dark VUEO palette, then tune the interactive accent.",
        onBack = onBack,
    ) {
        item {
            VueoSectionLabel("THEME")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All three themes stay dark and cinematic. Only the surface temperature changes.",
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                    maxLines = 3,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(
                        count = AppTheme.entries.size,
                        key = { index -> AppTheme.entries[index].name },
                    ) { index ->
                        val option = AppTheme.entries[index]
                        VueoThemeOption(
                            theme = option,
                            selected = theme == option,
                            onClick = {
                                theme = option
                                settingsStore.setAppTheme(option)
                                VueoPalette.applyTheme(option)
                            },
                        )
                    }
                }
            }
        }

        item {
            VueoStatusCard(
                title = "Selected Theme",
                value = theme.label,
                text = when (theme) {
                    AppTheme.CHARCOAL -> "Neutral graphite charcoal. The default VUEO cinematic look."
                    AppTheme.MIDNIGHT -> "Cool deep navy surfaces with a premium night-screen feel."
                    AppTheme.DEEP_TEAL -> "Dark petrol teal surfaces with a distinctive VUEO character."
                },
            )
        }

        item {
            VueoSectionLabel("ACCENT COLOR")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Accent changes buttons, focus, progress and interactive highlights. VUEO brand lime remains fixed.",
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                    maxLines = 3,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(
                        count = AppAccent.entries.size,
                        key = { index -> AppAccent.entries[index].name },
                    ) { index ->
                        val option = AppAccent.entries[index]
                        VueoAccentOption(
                            accent = option,
                            selected = accent == option,
                            onClick = {
                                accent = option
                                settingsStore.setAppAccent(option)
                                VueoPalette.applyAccent(option)
                            },
                        )
                    }
                }
            }
        }

        item {
            VueoStatusCard(
                title = "Selected Accent",
                value = accent.label,
                text = "Accent applies immediately to navigation, buttons, focus states, progress and toggles.",
            )
        }

        item {
            VueoInfoCard(
                title = "VUEO identity stays consistent",
                text = "The official VUEO lime logo and white wordmark do not change with theme or accent.",
            )
        }
    }
}

@Composable
private fun VueoThemeOption(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = when (theme) {
        AppTheme.CHARCOAL -> listOf(Color(0xFF070A0D), Color(0xFF242A2E))
        AppTheme.MIDNIGHT -> listOf(Color(0xFF060A12), Color(0xFF262E3A))
        AppTheme.DEEP_TEAL -> listOf(Color(0xFF061011), Color(0xFF283130))
    }

    Card(
        modifier = Modifier.width(116.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) VueoPalette.SurfaceStrong else VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(preview))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) VueoPalette.Accent else Color.White.copy(alpha = .08f),
                        shape = RoundedCornerShape(11.dp),
                    )
            )

            Text(
                theme.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )

            Text(
                if (selected) "Selected" else "Tap to use",
                color = if (selected) VueoPalette.Accent else VueoPalette.Muted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun VueoAccentOption(
    accent: AppAccent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatch =
        accent.composeColor()

    Card(
        modifier = Modifier
            .width(92.dp)
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        swatch.copy(
                            alpha = .12f
                        )
                    } else {
                        VueoPalette.Surface
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    9.dp
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(
                        RoundedCornerShape(
                            50
                        )
                    )
                    .background(swatch),
            )

            Text(
                text = accent.label,
                color =
                    if (selected) {
                        VueoPalette.Accent
                    } else {
                        Color.White
                    },
                fontSize = 12.sp,
                fontWeight =
                    if (selected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
            )

            Text(
                text =
                    if (selected) {
                        "Selected"
                    } else {
                        "Tap to use"
                    },
                color =
                    VueoPalette.Muted,
                fontSize = 9.sp,
            )
        }
    }
}
