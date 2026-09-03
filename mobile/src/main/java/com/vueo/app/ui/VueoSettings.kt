package com.vueo.app.ui

// VUEO DNA OPTIONAL SETTINGS PATCH - 2026-08-29
// VUEO GEMINI V1.1 FLASH-LITE - 2026-08-29

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.BuildConfig
import com.vueo.app.core.extensions.CatalogDiscoveryCache
import com.vueo.app.core.extensions.SourceDiscoveryCache
import com.vueo.app.core.extensions.UnifiedMediaEngine
import com.vueo.app.core.enrichment.GeminiClient
import com.vueo.app.core.enrichment.MdblistClient
import com.vueo.app.core.enrichment.TmdbEnhancementClient
import com.vueo.app.core.dna.UserDnaEngine
import com.vueo.app.core.dna.UserDnaPreferences
import com.vueo.app.core.dna.UserDnaSnapshot
import com.vueo.app.core.plugin.PluginStore
import com.vueo.app.core.storage.AppAccent
import com.vueo.app.core.storage.LibraryStore
import com.vueo.app.core.storage.ProfileStore
import com.vueo.app.core.storage.PreferredQuality
import com.vueo.app.core.storage.PlayerOrientation
import com.vueo.app.core.storage.SettingsStore
import com.vueo.app.core.storage.SubtitleLanguage
import com.vueo.app.core.storage.SubtitleSize
import com.vueo.app.core.storage.VueoBackupManager
import com.vueo.app.core.update.VueoUpdateManager
import com.vueo.app.core.update.VueoUpdateStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class SeekGestureSensitivity(
    val label: String,
    val maxSeekMinutes: Long,
) {
    LOW("Low", 5L),
    NORMAL("Normal", 10L),
    HIGH("High", 20L),
}

private const val PLAYER_GESTURE_PREFS = "vueo_player_gestures"
private const val SEEK_SENSITIVITY_KEY = "seek_sensitivity"
private const val CONTENT_WARNINGS_KEY = "content_warnings"

internal fun Context.seekGestureSensitivity(): SeekGestureSensitivity {
    val stored = getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    ).getString(
        SEEK_SENSITIVITY_KEY,
        SeekGestureSensitivity.NORMAL.name,
    )

    return SeekGestureSensitivity.values()
        .firstOrNull { it.name == stored }
        ?: SeekGestureSensitivity.NORMAL
}

private fun Context.setSeekGestureSensitivity(
    sensitivity: SeekGestureSensitivity,
) {
    getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    ).edit()
        .putString(SEEK_SENSITIVITY_KEY, sensitivity.name)
        .apply()
}

internal fun Context.contentWarningsEnabled(): Boolean =
    getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    ).getBoolean(CONTENT_WARNINGS_KEY, true)

private fun Context.setContentWarningsEnabled(
    enabled: Boolean,
) {
    getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    ).edit()
        .putBoolean(CONTENT_WARNINGS_KEY, enabled)
        .apply()
}

@Composable
internal fun VueoSettingsHub(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    profileStore: ProfileStore,
    profileVersion: Int,
    onProfiles: () -> Unit,
    onContentManager: () -> Unit,
    onEnhancements: () -> Unit,
    onPlayback: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onAppearance: () -> Unit,
    onDataStorage: () -> Unit,
    onUpdates: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }
    val libraryStore = remember {
        LibraryStore(context.applicationContext)
    }

    val addons = engine.stremioAddons()
    val repositories = pluginStore.repositories()
    val providers = pluginStore.totalProviderCount()
    val tmdbConfigured =
        pluginStore.tmdbApiKey().isNotBlank()
    val mdblistConfigured =
        settingsStore.mdblistApiKey().isNotBlank()
    val geminiConfigured =
        settingsStore.geminiApiKey().isNotBlank()
    val updateStore = remember {
        VueoUpdateStore(context.applicationContext)
    }
    val latestUpdate =
        updateStore.latestRelease()

    val activeProfile =
        remember(
            profileVersion
        ) {
            profileStore.activeProfile()
        }

    val userDnaPreferences =
        remember {
            UserDnaPreferences(
                context.applicationContext
            )
        }

    val dnaEnabled =
        userDnaPreferences
            .userDnaEnabled(
                activeProfile.id
            )

    val dnaSnapshot =
        if (dnaEnabled) {
            remember(
                activeProfile.id,
                profileVersion,
            ) {
                UserDnaEngine(
                    libraryStore
                ).build()
            }
        } else {
            null
        }

    val dnaTastePreview =
        dnaSnapshot
            ?.topGenres
            ?.take(3)
            ?.joinToString(
                " • "
            ) {
                "${it.name} ${it.percent}%"
            }
            .orEmpty()

    val myListCount =
        remember(
            activeProfile.id,
            profileVersion,
        ) {
            libraryStore
                .watchlist()
                .size
        }

    val watchedTitlesCount =
        remember(
            activeProfile.id,
            profileVersion,
        ) {
            libraryStore
                .history()
                .filter {
                    it.positionMs > 5_000L
                }
                .map {
                    "${it.media.type}:${it.media.id}"
                }
                .distinct()
                .size
        }

    val vueoClass =
        remember(
            watchedTitlesCount
        ) {
            vueoViewingClass(
                watchedTitlesCount
            )
        }

    val dnaClass =
        remember(
            dnaEnabled,
            dnaSnapshot,
        ) {
            when {
                !dnaEnabled ->
                    "DNA Off"

                dnaSnapshot == null ->
                    "Finding Your Taste"

                else ->
                    vueoDnaClass(
                        dnaSnapshot
                    )
            }
        }

    var showPersonalization by remember(
        activeProfile.id
    ) {
        mutableStateOf(false)
    }

    var showUserDna by remember(
        activeProfile.id
    ) {
        mutableStateOf(false)
    }

    if (showUserDna && dnaEnabled) {
        BackHandler {
            showUserDna = false
        }

        UserDnaScreen(
            profile = activeProfile,
            libraryStore = libraryStore,
            dataVersion = profileVersion,
            onBack = {
                showUserDna = false
            },
        )
        return
    }

    if (showPersonalization) {
        BackHandler {
            showPersonalization = false
        }

        PersonalizationSettingsScreen(
            profileStore = profileStore,
            onBack = {
                showPersonalization = false
            },
            onViewDna = {
                showUserDna = true
            },
        )
        return
    }

    val avatarDrawable =
        remember(
            activeProfile.avatar,
            context,
        ) {
            if (
                activeProfile.avatar
                    .startsWith("avatar_")
            ) {
                context.resources
                    .getIdentifier(
                        activeProfile.avatar,
                        "drawable",
                        context.packageName,
                    )
                    .takeIf {
                        it != 0
                    }
            } else {
                null
            }
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 32.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
    ) {
        item(
            key = "settings-header"
        ) {
            Column {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight =
                        FontWeight.Black,
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )
            }
        }

        item(
            key = "settings-profile"
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        20.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            VueoPalette
                                .SurfaceElevated,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (dnaEnabled) {
                                        showUserDna = true
                                    } else {
                                        showPersonalization = true
                                    }
                                }
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 6.dp,
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(
                                        60.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            50
                                        )
                                    )
                                    .background(
                                        VueoPalette
                                            .SurfaceStrong
                                    ),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            if (
                                avatarDrawable != null
                            ) {
                                Image(
                                    painter =
                                        painterResource(
                                            avatarDrawable
                                        ),
                                    contentDescription =
                                        activeProfile.name,
                                    contentScale =
                                        ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text =
                                        activeProfile.name
                                            .trim()
                                            .firstOrNull()
                                            ?.uppercase()
                                            ?: "P",
                                    color =
                                        Color.White,
                                    fontSize = 22.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(
                            Modifier.width(
                                14.dp
                            )
                        )

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    3.dp
                                ),
                        ) {
                            Text(
                                text =
                                    "VUEO • ${activeProfile.name}",
                                color =
                                    Color.White,
                                fontSize = 20.sp,
                                fontWeight =
                                    FontWeight.Black,
                            )

                            Text(
                                text =
                                    "$vueoClass • $dnaClass",
                                color =
                                    VueoPalette.Muted,
                                fontSize = 11.sp,
                                fontWeight =
                                    FontWeight.Medium,
                            )

                        }

                        Text(
                            text = "›",
                            color =
                                VueoPalette.Muted,
                            fontSize = 27.sp,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 7.dp,
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        VueoProfileStat(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            label = "My List",
                            value =
                                myListCount
                                    .toString(),
                        )

                        VueoProfileStat(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            label = "Watched",
                            value =
                                watchedTitlesCount
                                    .toString(),
                        )

                        VueoProfileStat(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            label = "DNA",
                            value =
                                dnaSnapshot
                                    ?.let {
                                        "${it.confidencePercent}%"
                                    }
                                    ?: "Off",
                            highlighted =
                                dnaEnabled &&
                                    dnaSnapshot !=
                                    null,
                        )
                    }

                    Text(
                        text =
                            dnaTastePreview
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?: if (
                                    dnaEnabled
                                ) {
                                    "Keep watching to shape your DNA class."
                                } else {
                                    "Enable User DNA in Personalization."
                                },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 18.dp,
                                    end = 18.dp,
                                    top = 1.dp,
                                    bottom = 8.dp,
                                ),
                        color =
                            VueoPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Medium,
                        maxLines = 2,
                    )

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 12.dp,
                                )
                                .clickable(
                                    onClick = onProfiles
                                ),
                        shape =
                            RoundedCornerShape(
                                14.dp
                            ),
                        color =
                            VueoPalette.Surface
                                .copy(
                                    alpha = .82f
                                ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 13.dp,
                                        vertical = 8.dp,
                                    ),
                            horizontalArrangement =
                                Arrangement.Center,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "⇄",
                                color =
                                    VueoPalette.Accent,
                                fontSize = 15.sp,
                                fontWeight =
                                    FontWeight.Black,
                            )

                            Spacer(
                                Modifier.width(
                                    8.dp
                                )
                            )

                            Text(
                                text = "Switch Profiles",
                                color =
                                    Color.White,
                                fontSize = 11.sp,
                                fontWeight =
                                    FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        item {
            VueoSettingsNavigationCard(
                title = "Personalization",
                subtitle =
                    "User DNA, DNA Match & recommendations.",
                status = "",
                icon =
                    Icons.Default.Settings,
                onClick = {
                    showPersonalization = true
                },
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Content Manager",
                subtitle =
                    "Addons, repos & providers.",
                status =
                    "${addons.size} addons • " +
                        "${repositories.size} repos • " +
                        "$providers providers",
                icon =
                    Icons.Default.Extension,
                onClick =
                    onContentManager,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Enhancements",
                subtitle =
                    "Metadata, ratings & external services.",
                status =
                    buildString {
                        append("TMDB ")
                        append(
                            if (
                                tmdbConfigured
                            ) {
                                "configured"
                            } else {
                                "optional"
                            }
                        )
                        append(" • MDBList ")
                        append(
                            if (
                                mdblistConfigured
                            ) {
                                "configured"
                            } else {
                                "optional"
                            }
                        )
                        append(" • Gemini ")
                        append(
                            if (
                                geminiConfigured
                            ) {
                                "configured"
                            } else {
                                "optional"
                            }
                        )
                    },
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick =
                    onEnhancements,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Playback",
                subtitle =
                    "Player & streaming preferences.",
                status =
                    "${if (settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"} • " +
                        settingsStore
                            .preferredQuality()
                            .label,
                icon =
                    Icons.Default.PlayArrow,
                onClick =
                    onPlayback,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Subtitles",
                subtitle =
                    "Language & display preferences.",
                status =
                    "${settingsStore.preferredSubtitleLanguage().label} • " +
                        if (
                            settingsStore
                                .subtitlesOnByDefault()
                        ) {
                            "Default on"
                        } else {
                            "Default off"
                        },
                icon =
                    Icons.Default.VideoLibrary,
                onClick =
                    onSubtitles,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Sources",
                subtitle =
                    "Source ranking & information.",
                status =
                    if (
                        settingsStore
                            .showSourceTechnicalDetails()
                    ) {
                        "Technical details on"
                    } else {
                        "Technical details off"
                    },
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick =
                    onSources,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Appearance",
                subtitle =
                    "Interface preferences.",
                status =
                    "VUEO Dark • " +
                        "${settingsStore.appAccent().label} accent",
                icon =
                    Icons.Default.Settings,
                onClick =
                    onAppearance,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Data & Storage",
                subtitle =
                    "Backup, history, cache & app data.",
                status =
                    "Local device data",
                icon =
                    Icons.Default.VideoLibrary,
                onClick =
                    onDataStorage,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Updates",
                subtitle =
                    "Version & update preferences.",
                status =
                    if (
                        latestUpdate
                            ?.isNewerThanCurrent() ==
                            true
                    ) {
                        "Update " +
                            "${latestUpdate.versionName} available"
                    } else {
                        "Up to date"
                    },
                icon =
                    Icons.Default.Refresh,
                onClick =
                    onUpdates,
                compact = true,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "About VUEO",
                subtitle =
                    "Privacy, architecture & build information.",
                status =
                    "Local-first app info",
                icon =
                    Icons.Default.Settings,
                onClick =
                    onAbout,
                compact = true,
            )
        }

        item(
            key = "settings-version"
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 4.dp,
                            bottom = 8.dp,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text =
                        "VUEO ${BuildConfig.VERSION_NAME}",
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun PersonalizationSettingsScreen(
    profileStore: ProfileStore,
    onBack: () -> Unit,
    onViewDna: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile =
        remember(
            profileStore
        ) {
            profileStore.activeProfile()
        }

    val userDnaPreferences =
        remember {
            UserDnaPreferences(
                context.applicationContext
            )
        }

    var userDnaEnabled by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .userDnaEnabled(
                    activeProfile.id
                )
        )
    }

    var showDnaMatch by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .showDnaMatchEnabled(
                    activeProfile.id
                )
        )
    }

    var personalizedRecommendations by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .personalizedRecommendationsEnabled(
                    activeProfile.id
                )
        )
    }

    VueoSettingsPage(
        title = "Personalization",
        subtitle =
            "Local, per-profile controls for how VUEO adapts to you.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = activeProfile.name,
                value =
                    if (userDnaEnabled) {
                        "User DNA on"
                    } else {
                        "User DNA off"
                    },
                text =
                    "These settings apply only to this profile. VUEO builds User DNA locally from viewing signals on this device.",
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Your DNA",
                subtitle =
                    if (userDnaEnabled) {
                        "View genres, taste signals and DNA strength for this profile."
                    } else {
                        "Enable User DNA below to build and view a taste profile."
                    },
                status =
                    if (userDnaEnabled) {
                        "View profile"
                    } else {
                        "Unavailable while off"
                    },
                icon =
                    Icons.Default.Settings,
                onClick = {
                    if (userDnaEnabled) {
                        onViewDna()
                    }
                },
            )
        }

        item {
            VueoSectionLabel(
                "USER DNA"
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "User DNA",
                subtitle =
                    "Use this profile's History, playback progress and My List to build a local taste profile.",
                checked =
                    userDnaEnabled,
                onCheckedChange = {
                    enabled ->
                    userDnaEnabled =
                        enabled
                    userDnaPreferences
                        .setUserDnaEnabled(
                            profileId =
                                activeProfile.id,
                            enabled =
                                enabled,
                        )
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Show DNA Match",
                subtitle =
                    "Show a local taste-match score on supported movie and series details.",
                checked =
                    showDnaMatch,
                enabled =
                    userDnaEnabled,
                onCheckedChange = {
                    enabled ->
                    showDnaMatch =
                        enabled
                    userDnaPreferences
                        .setShowDnaMatchEnabled(
                            profileId =
                                activeProfile.id,
                            enabled =
                                enabled,
                        )
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Personalized Recommendations",
                subtitle =
                    "Use User DNA for For You and Because You Watched recommendations. More Like This stays title-based.",
                checked =
                    personalizedRecommendations,
                enabled =
                    userDnaEnabled,
                onCheckedChange = {
                    enabled ->
                    personalizedRecommendations =
                        enabled
                    userDnaPreferences
                        .setPersonalizedRecommendationsEnabled(
                            profileId =
                                activeProfile.id,
                            enabled =
                                enabled,
                        )
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Local by design",
                text =
                    "Turning User DNA off does not delete History, My List or playback progress. It only stops VUEO from using those signals for DNA Match and personalized recommendations. No account or server is required.",
            )
        }
    }
}

@Composable
internal fun EnhancementsSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onTmdb: () -> Unit,
    onMdblist: () -> Unit,
    onGemini: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore =
        remember {
            PluginStore(
                context.applicationContext
            )
        }

    VueoSettingsPage(
        title = "Enhancements",
        subtitle =
            "Optional external services that enrich VUEO.",
        onBack = onBack,
    ) {
        item {
            VueoInfoCard(
                title = "External and optional",
                text =
                    "Enhancements add metadata, ratings or AI capabilities. VUEO core and local Personalization continue to work without them.",
            )
        }

        item {
            VueoSectionLabel(
                "METADATA & RATINGS"
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "TMDB",
                subtitle =
                    "Richer metadata, discovery, recommendations, similar titles, and artwork.",
                status =
                    if (
                        pluginStore
                            .tmdbApiKey()
                            .isNotBlank()
                    ) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick =
                    onTmdb,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "MDBList",
                subtitle =
                    "Optional rating and score enrichment for title details.",
                status =
                    if (
                        settingsStore
                            .mdblistApiKey()
                            .isNotBlank()
                    ) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick =
                    onMdblist,
            )
        }

        item {
            VueoSectionLabel(
                "AI"
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Gemini",
                subtitle =
                    "Optional AI insights for movie and series details.",
                status =
                    if (
                        settingsStore
                            .geminiApiKey()
                            .isNotBlank()
                    ) {
                        if (
                            settingsStore
                                .geminiInsightsEnabled()
                        ) {
                            "Configured • Insights on"
                        } else {
                            "Configured • Insights off"
                        }
                    } else {
                        "Not configured"
                    },
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick =
                    onGemini,
            )
        }
    }
}

@Composable
internal fun GeminiEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var apiKey by remember {
        mutableStateOf(
            settingsStore
                .geminiApiKey()
        )
    }

    var saved by remember {
        mutableStateOf(false)
    }

    val scope =
        rememberCoroutineScope()

    var testing by remember {
        mutableStateOf(false)
    }

    var connectionStatus by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var insightsEnabled by remember {
        mutableStateOf(
            settingsStore
                .geminiInsightsEnabled()
        )
    }

    VueoSettingsPage(
        title = "Gemini",
        subtitle =
            "Optional AI enhancement powered by Google's Gemini API.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value =
                    connectionStatus
                        ?: if (
                            apiKey.trim()
                                .isNotEmpty()
                        ) {
                            "Configured"
                        } else {
                            "Not configured"
                        },
                text =
                    "VUEO uses Gemini only after an explicit action. The API key is stored locally on this device.",
            )
        }

        item {
            Card(
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                VueoPalette.Surface,
                        ),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    Text(
                        text = "API Key",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus =
                                null
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(
                                "Gemini API Key"
                            )
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        Button(
                            onClick = {
                                settingsStore
                                    .setGeminiApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus =
                                    null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled =
                                !testing,
                            onClick = {
                                val key =
                                    apiKey.trim()

                                if (
                                    key.isBlank()
                                ) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val result =
                                            GeminiClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            result.message

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (
                                    testing
                                ) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            text =
                                "Gemini configuration saved locally.",
                            color =
                                VueoPalette.Accent,
                            fontSize =
                                11.sp,
                            fontWeight =
                                FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel(
                "FEATURES"
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "AI Insights",
                subtitle =
                    "Show an optional Gemini Insight card on movie and series details. Gemini is called only when you tap Generate Insight.",
                checked =
                    insightsEnabled,
                enabled =
                    apiKey.trim()
                        .isNotEmpty(),
                onCheckedChange = {
                    enabled ->
                    insightsEnabled =
                        enabled

                    settingsStore
                        .setGeminiInsightsEnabled(
                            enabled
                        )
                },
            )
        }

        item {
            VueoStatusCard(
                title = "Model",
                value =
                    "Gemini 3.5 Flash-Lite",
                text =
                    "VUEO uses gemini-3.5-flash-lite through the v1beta Interactions API with minimal thinking and a short output cap.",
            )
        }

        item {
            VueoInfoCard(
                title = "Privacy & usage",
                text =
                    "Generate Insight sends selected-title metadata to Google's Gemini API. If User DNA is enabled, VUEO may also send a compact taste summary such as top genres and DNA confidence. Raw History, My List and playback records are not sent. Requests can count against your Gemini API quota or billing.",
            )
        }
    }
}

@Composable
internal fun TmdbEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }

    var apiKey by remember {
        mutableStateOf(pluginStore.tmdbApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var metadata by remember {
        mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled())
    }
    var recommendations by remember {
        mutableStateOf(settingsStore.tmdbRecommendationsEnabled())
    }
    var similar by remember {
        mutableStateOf(settingsStore.tmdbSimilarTitlesEnabled())
    }
    var artwork by remember {
        mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled())
    }

    VueoSettingsPage(
        title = "TMDB",
        subtitle = "Optional metadata and discovery enhancement.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "The key is stored locally on this device. VUEO core does not depend on TMDB.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("TMDB v3 API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                pluginStore
                                    .setTmdbApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            TmdbEnhancementClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "TMDB configuration saved locally.",
                            color = VueoPalette.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("FEATURES")
        }

        item {
            VueoSettingsToggleCard(
                title = "Metadata Enrichment",
                subtitle = "Allow richer title information when TMDB enrichment is available.",
                checked = metadata,
                onCheckedChange = {
                    metadata = it
                    settingsStore.setTmdbMetadataEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Recommendations",
                subtitle = "Use TMDB recommendations for discovery when configured.",
                checked = recommendations,
                onCheckedChange = {
                    recommendations = it
                    settingsStore.setTmdbRecommendationsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Similar Titles",
                subtitle = "Use TMDB similar titles as an additional discovery signal.",
                checked = similar,
                onCheckedChange = {
                    similar = it
                    settingsStore.setTmdbSimilarTitlesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Artwork Enrichment",
                subtitle = "Allow better poster and backdrop fallback when available.",
                checked = artwork,
                onCheckedChange = {
                    artwork = it
                    settingsStore.setTmdbArtworkEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Discovery connection",
                text = "TMDB now enriches Details metadata and powers More Like This with Recommendations, Similar titles, and the VUEO catalog fallback.",
            )
        }
    }
}

@Composable
internal fun MdblistEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var apiKey by remember {
        mutableStateOf(settingsStore.mdblistApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var ratings by remember {
        mutableStateOf(settingsStore.mdblistRatingsEnabled())
    }
    var imdb by remember {
        mutableStateOf(settingsStore.mdblistImdbEnabled())
    }
    var rt by remember {
        mutableStateOf(settingsStore.mdblistRottenTomatoesEnabled())
    }
    var metacritic by remember {
        mutableStateOf(settingsStore.mdblistMetacriticEnabled())
    }
    var tmdb by remember {
        mutableStateOf(settingsStore.mdblistTmdbRatingEnabled())
    }
    var trakt by remember {
        mutableStateOf(settingsStore.mdblistTraktEnabled())
    }

    VueoSettingsPage(
        title = "MDBList",
        subtitle = "Optional ratings and score enrichment.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "MDBList is optional. Without it, VUEO simply shows the information available from core metadata sources.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("MDBList API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                settingsStore
                                    .setMdblistApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            MdblistClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "MDBList configuration saved locally.",
                            color = VueoPalette.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("RATINGS")
        }

        item {
            VueoSettingsToggleCard(
                title = "Ratings Enrichment",
                subtitle = "Master switch for MDBList rating information.",
                checked = ratings,
                onCheckedChange = {
                    ratings = it
                    settingsStore.setMdblistRatingsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "IMDb",
                subtitle = "Show IMDb score when available.",
                checked = imdb,
                onCheckedChange = {
                    imdb = it
                    settingsStore.setMdblistImdbEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Rotten Tomatoes",
                subtitle = "Show Rotten Tomatoes score when available.",
                checked = rt,
                onCheckedChange = {
                    rt = it
                    settingsStore.setMdblistRottenTomatoesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Metacritic",
                subtitle = "Show Metacritic score when available.",
                checked = metacritic,
                onCheckedChange = {
                    metacritic = it
                    settingsStore.setMdblistMetacriticEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "TMDB Rating",
                subtitle = "Show TMDB rating through MDBList when available.",
                checked = tmdb,
                onCheckedChange = {
                    tmdb = it
                    settingsStore.setMdblistTmdbRatingEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Trakt",
                subtitle = "Show Trakt score when available.",
                checked = trakt,
                onCheckedChange = {
                    trakt = it
                    settingsStore.setMdblistTraktEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Ratings connection",
                text = "MDBList ratings now appear on Details when configured. VUEO fetches one rating bundle and shows only the rating sources you enable.",
            )
        }
    }
}

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
    var contentWarnings by remember {
        mutableStateOf(context.contentWarningsEnabled())
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
                    context.setContentWarningsEnabled(enabled)
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
    var languageDialog by remember {
        mutableStateOf<SubtitleLanguageTarget?>(null)
    }
    var showSizeDialog by remember {
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
    var accent by remember {
        mutableStateOf(
            settingsStore.appAccent()
        )
    }

    VueoSettingsPage(
        title = "Appearance",
        subtitle = "Keep the interface calm, then choose the accent that fits you.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Theme",
                value = "VUEO Dark",
                text = "Dark charcoal surfaces stay fixed for comfortable movie browsing and playback.",
            )
        }

        item {
            VueoSectionLabel(
                "ACCENT COLOR"
            )
        }

        item {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
            ) {
                Text(
                    text = "White is the default. Lime Green remains VUEO's signature brand option, not a forced UI color.",
                    color =
                        VueoPalette.Muted,
                    fontSize = 12.sp,
                )

                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                    contentPadding =
                        PaddingValues(
                            vertical = 2.dp
                        ),
                ) {
                    items(
                        count =
                            AppAccent.entries.size,
                        key = { index ->
                            AppAccent.entries[
                                index
                            ].name
                        },
                    ) { index ->
                        val option =
                            AppAccent.entries[
                                index
                            ]

                        VueoAccentOption(
                            accent = option,
                            selected =
                                accent == option,
                            onClick = {
                                accent = option
                                settingsStore
                                    .setAppAccent(
                                        option
                                    )
                                VueoPalette
                                    .applyAccent(
                                        option
                                    )
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
                text = "Accent changes apply immediately to navigation, buttons, focus states, progress, toggles, and interactive highlights.",
            )
        }

        item {
            VueoInfoCard(
                title = "Brand stays consistent",
                text = "The official VUEO mark stays Lime Green and the wordmark stays White, even when the interface accent changes.",
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
                text = "Built-in VUEO features, Stremio Addons, JavaScript Provider Plugins, Unified Source Engine, Smart Source Ranking, and Media3 playback.",
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

private fun vueoBackupFileName(): String {
    val stamp = SimpleDateFormat(
        "yyyyMMdd-HHmm",
        Locale.US,
    ).format(Date())

    return "VUEO-backup-$stamp.json"
}

private fun formatUpdateCheckTime(
    epochMs: Long,
): String =
    SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault(),
    ).format(Date(epochMs))

private fun openVueoReleaseUrl(
    context: Context,
    url: String,
): Boolean {
    if (!url.startsWith("https://")) {
        return false
    }

    return runCatching {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url),
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private enum class SubtitleLanguageTarget {
    PRIMARY,
    SECONDARY,
}

private enum class DataClearAction(
    val title: String,
    val message: String,
) {
    CATALOG_CACHE(
        title = "Clear catalog cache?",
        message = "Home and Search will fetch fresh catalog data again.",
    ),
    SOURCE_CACHE(
        title = "Clear source cache?",
        message = "Recent source results will be discarded. The next Watch action will perform a fresh source search.",
    ),
    CONTINUE_WATCHING(
        title = "Clear Continue Watching?",
        message = "All unfinished playback entries will be removed from Continue Watching.",
    ),
    WATCH_HISTORY(
        title = "Clear Watch History?",
        message = "Previously watched playback history will be removed. My List remains unchanged.",
    ),
}

@Composable
private fun VueoSettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    VueoSettingsTitle(
                        title = title,
                        subtitle = subtitle,
                    )
                }
            }
        }

        content()

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VueoSettingsTitle(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "VUEO",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )

        Text(
            title,
            color = Color.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Black,
        )

        Text(
            subtitle,
            color = VueoPalette.Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun VueoProfileStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    highlighted: Boolean = false,
) {
    Surface(
        modifier =
            modifier,
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color =
            if (highlighted) {
                VueoPalette.Accent
                    .copy(
                        alpha = .10f
                    )
            } else {
                VueoPalette.Surface
            },
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 10.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                ),
        ) {
            Text(
                text = value,
                color =
                    if (highlighted) {
                        VueoPalette.Accent
                    } else {
                        Color.White
                    },
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text = label,
                color =
                    VueoPalette.Muted,
                fontSize = 9.sp,
                fontWeight =
                    FontWeight.Medium,
            )
        }
    }
}

private fun vueoViewingClass(
    watchedTitles: Int,
): String =
    when {
        watchedTitles < 10 ->
            "Baby VUEO"

        watchedTitles < 30 ->
            "Explorer"

        watchedTitles < 75 ->
            "Binger"

        watchedTitles < 150 ->
            "Cinephile"

        watchedTitles < 300 ->
            "Screen Veteran"

        else ->
            "VUEO Legend"
    }

private fun vueoDnaClass(
    snapshot: UserDnaSnapshot,
): String {
    if (
        snapshot.confidencePercent < 20 ||
        snapshot.topGenres.isEmpty()
    ) {
        return "Finding Your Taste"
    }

    val genres =
        snapshot.topGenres
            .associate {
                it.name.lowercase(
                    Locale.US
                ) to it.percent
            }

    fun score(
        vararg names: String,
    ): Int =
        names.sumOf {
            genres[
                it.lowercase(
                    Locale.US
                )
            ] ?: 0
        }

    val topGenrePercent =
        snapshot.topGenres
            .firstOrNull()
            ?.percent
            ?: 0

    if (
        snapshot.topGenres.size >= 5 &&
        topGenrePercent <= 30
    ) {
        return "The Explorer"
    }

    val classes =
        listOf(
            "The Adventurer" to
                score(
                    "Action",
                    "Adventure",
                    "Fantasy",
                ),
            "The Detective" to
                score(
                    "Crime",
                    "Mystery",
                    "Thriller",
                ),
            "The Thrill Seeker" to
                score(
                    "Horror",
                    "Thriller",
                    "Action",
                ),
            "The Romantic" to
                score(
                    "Romance",
                    "Drama",
                ),
            "The Dreamer" to
                score(
                    "Science Fiction",
                    "Fantasy",
                    "Animation",
                ),
            "The Mood Lifter" to
                score(
                    "Comedy",
                    "Family",
                    "Animation",
                ),
            "The Story Hunter" to
                score(
                    "Drama",
                    "History",
                    "Documentary",
                ),
        )

    val best =
        classes.maxByOrNull {
            it.second
        }

    if (
        best != null &&
        best.second >= 20
    ) {
        return best.first
    }

    return when (
        snapshot.topGenres
            .firstOrNull()
            ?.name
            ?.lowercase(
                Locale.US
            )
    ) {
        "crime",
        "mystery" ->
            "The Detective"

        "horror",
        "thriller" ->
            "The Thrill Seeker"

        "romance" ->
            "The Romantic"

        "science fiction",
        "fantasy" ->
            "The Dreamer"

        "comedy" ->
            "The Mood Lifter"

        "action",
        "adventure" ->
            "The Adventurer"

        else ->
            "The Story Hunter"
    }
}

@Composable
private fun VueoSettingsNavigationCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val cardRadius =
        if (compact) {
            18.dp
        } else {
            20.dp
        }

    val horizontalPadding =
        if (compact) {
            14.dp
        } else {
            16.dp
        }

    val verticalPadding =
        if (compact) {
            12.dp
        } else {
            16.dp
        }

    val iconBoxSize =
        if (compact) {
            40.dp
        } else {
            46.dp
        }

    val iconSize =
        if (compact) {
            21.dp
        } else {
            24.dp
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                ),
        shape =
            RoundedCornerShape(
                cardRadius
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated,
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal =
                        horizontalPadding,
                    vertical =
                        verticalPadding,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(
                            iconBoxSize
                        )
                        .clip(
                            RoundedCornerShape(
                                if (
                                    compact
                                ) {
                                    12.dp
                                } else {
                                    14.dp
                                }
                            )
                        )
                        .background(
                            VueoPalette
                                .SurfaceStrong
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription =
                        null,
                    tint =
                        VueoPalette.Accent,
                    modifier =
                        Modifier.size(
                            iconSize
                        ),
                )
            }

            Spacer(
                Modifier.width(
                    if (
                        compact
                    ) {
                        12.dp
                    } else {
                        13.dp
                    }
                )
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (
                            compact
                        ) {
                            2.dp
                        } else {
                            3.dp
                        }
                    ),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize =
                        if (
                            compact
                        ) {
                            15.sp
                        } else {
                            16.sp
                        },
                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    text = subtitle,
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )

                if (
                    status.isNotBlank()
                ) {
                    Text(
                        text = status,
                        color =
                            VueoPalette.Accent,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Bold,
                    )
                }
            }

            Text(
                text = "›",
                color =
                    VueoPalette.Muted,
                fontSize =
                    if (
                        compact
                    ) {
                        24.sp
                    } else {
                        28.sp
                    },
            )
        }
    }
}
@Composable
private fun VueoSettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color =
                        if (enabled) {
                            Color.White
                        } else {
                            Color.White.copy(
                                alpha = .45f
                            )
                        },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color =
                        if (enabled) {
                            VueoPalette.Muted
                        } else {
                            VueoPalette.Muted.copy(
                                alpha = .55f
                            )
                        },
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun VueoSettingsValueCard(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = VueoPalette.Accent.copy(alpha = .10f),
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp,
                    ),
                    color = VueoPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VueoSettingsActionCard(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(10.dp))

            TextButton(onClick = onClick) {
                Text(
                    action,
                    color = VueoPalette.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VueoInfoCard(
    title: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VueoStatusCard(
    title: String,
    value: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )

                Text(
                    value,
                    color = VueoPalette.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VueoChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(label)
    }
}

@Composable
private fun VueoSectionLabel(
    label: String,
) {
    Text(
        label,
        color = VueoPalette.Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
    )
}
