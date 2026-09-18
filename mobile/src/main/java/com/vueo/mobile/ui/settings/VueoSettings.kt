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

internal fun Context.setSeekGestureSensitivity(
    sensitivity: SeekGestureSensitivity,
) {
    getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    ).edit()
        .putString(SEEK_SENSITIVITY_KEY, sensitivity.name)
        .apply()
}

internal fun Context.migrateLegacyContentWarningsToShared(
    settingsStore: SettingsStore,
) {
    if (settingsStore.hasContentWarningsPreference()) return

    val legacy = getSharedPreferences(
        PLAYER_GESTURE_PREFS,
        Context.MODE_PRIVATE,
    )
    if (legacy.contains(CONTENT_WARNINGS_KEY)) {
        settingsStore.setContentWarningsEnabled(
            legacy.getBoolean(CONTENT_WARNINGS_KEY, true)
        )
    }
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

    val settingsSurface =
        when {
            showUserDna && dnaEnabled -> 2
            showPersonalization -> 1
            else -> 0
        }

    BackHandler(enabled = settingsSurface != 0) {
        if (settingsSurface == 2) {
            showUserDna = false
        } else {
            showPersonalization = false
        }
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

    AnimatedContent(
        targetState = settingsSurface,
        transitionSpec = {
            vueoFadeThrough(
                enterDurationMillis = 320,
                exitDurationMillis = 170,
                enterDelayMillis = 24,
                initialScale = 0.992f,
                targetScale = 0.996f,
            )
        },
        label = "VUEO settings personalization transition",
        modifier = Modifier.fillMaxSize(),
    ) { surface ->
        when (surface) {
            2 ->
                UserDnaScreen(
                    profile = activeProfile,
                    libraryStore = libraryStore,
                    dataVersion = profileVersion,
                    onBack = {
                        showUserDna = false
                    },
                )

            1 ->
                PersonalizationSettingsScreen(
                    profileStore = profileStore,
                    onBack = {
                        showPersonalization = false
                    },
                    onViewDna = {
                        showUserDna = true
                    },
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(VueoPalette.Background),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    item(key = "settings-header") {
                        Text("Settings", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    }

                    item(key = "settings-profile") {
                        val profileShape = RoundedCornerShape(20.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(profileShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = .145f),
                                            Color.White.copy(alpha = .095f),
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = .12f), profileShape),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (dnaEnabled) showUserDna = true else showPersonalization = true
                                        }
                                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = .20f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (avatarDrawable != null) {
                                            Image(
                                                painter = painterResource(avatarDrawable),
                                                contentDescription = activeProfile.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Text(
                                                activeProfile.name.trim().firstOrNull()?.uppercase() ?: "P",
                                                color = Color.White,
                                                fontSize = 21.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(activeProfile.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                        Text(
                                            "$vueoClass • $dnaClass",
                                            color = Color.White.copy(alpha = .62f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Text("›", color = Color.White.copy(alpha = .58f), fontSize = 27.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    VueoProfileStat(Modifier.weight(1f), "My List", myListCount.toString())
                                    VueoProfileStat(Modifier.weight(1f), "Watched", watchedTitlesCount.toString())
                                    VueoProfileStat(
                                        modifier = Modifier.weight(1f),
                                        label = "DNA",
                                        value = dnaSnapshot?.let { "${it.confidencePercent}%" } ?: "Off",
                                    )
                                }

                                Text(
                                    text = dnaTastePreview.takeIf { it.isNotBlank() }
                                        ?: if (dnaEnabled) "Keep watching to shape your DNA class." else "Enable User DNA in Personalization.",
                                    modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 1.dp, bottom = 6.dp),
                                    color = Color.White.copy(alpha = .60f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 11.dp).clickable(onClick = onProfiles),
                                    shape = RoundedCornerShape(13.dp),
                                    color = Color.Black.copy(alpha = .23f),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("⇄", color = VueoPalette.BrandLime, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Switch Profiles", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "settings-vueo-group") {
                        VueoSettingsHubGroup("VUEO") {
                            VueoSettingsHubRow("Personalization", "User DNA, DNA Match & recommendations.", "", Icons.Default.Settings) { showPersonalization = true }
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow(
                                "Content Manager", "Addons, providers & catalogs.",
                                "${addons.size} addons • ${repositories.size} repos • $providers providers",
                                Icons.Default.Extension, onContentManager,
                            )
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow(
                                "Enhancements", "Metadata, ratings & external services.",
                                buildString {
                                    append("TMDB ${if (tmdbConfigured) "configured" else "optional"}")
                                    append(" • MDBList ${if (mdblistConfigured) "configured" else "optional"}")
                                },
                                Icons.Default.SettingsInputComponent, onEnhancements,
                            )
                        }
                    }

                    item(key = "settings-playback-group") {
                        VueoSettingsHubGroup("PLAYBACK") {
                            VueoSettingsHubRow(
                                "Playback", "Player & streaming preferences.",
                                "${if (settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"} • ${settingsStore.preferredQuality().label}",
                                Icons.Default.PlayArrow, onPlayback,
                            )
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow(
                                "Subtitles", "Language & display preferences.",
                                "${settingsStore.preferredSubtitleLanguage().label} • ${if (settingsStore.subtitlesOnByDefault()) "Default on" else "Default off"}",
                                Icons.Default.VideoLibrary, onSubtitles,
                            )
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow(
                                "Sources", "Source ranking & information.",
                                if (settingsStore.showSourceTechnicalDetails()) "Technical details on" else "Technical details off",
                                Icons.Default.SettingsInputComponent, onSources,
                            )
                        }
                    }

                    item(key = "settings-app-group") {
                        VueoSettingsHubGroup("APP") {
                            VueoSettingsHubRow(
                                "Appearance", "Interface preferences.",
                                "${settingsStore.appTheme().label} • ${settingsStore.appAccent().label} accent",
                                Icons.Default.Settings, onAppearance,
                            )
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow("Data & Storage", "Backup, history, cache & app data.", "Local device data", Icons.Default.VideoLibrary, onDataStorage)
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow(
                                "Updates", "Version & update preferences.",
                                if (latestUpdate?.isNewerThanCurrent() == true) "Update ${latestUpdate.versionName} available" else "Up to date",
                                Icons.Default.Refresh, onUpdates,
                            )
                            VueoSettingsHubDivider()
                            VueoSettingsHubRow("About VUEO", "Privacy, architecture & build information.", "Local-first app info", Icons.Default.Settings, onAbout)
                        }
                    }

                    item(key = "settings-version") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("VUEO ${BuildConfig.VERSION_NAME}", color = VueoPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

        }
    }
}

@Composable
internal fun VueoSettingsHubGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            color = VueoPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.15.sp,
            modifier = Modifier.padding(start = 3.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = VueoPalette.SurfaceElevated,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun VueoSettingsHubDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 12.dp)
            .height(1.dp)
            .background(VueoPalette.Stroke.copy(alpha = .55f))
    )
}

@Composable
internal fun VueoSettingsHubRow(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(VueoPalette.SurfaceStrong.copy(alpha = .78f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = .92f), modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = VueoPalette.Muted, fontSize = 10.sp, maxLines = 1)
            if (status.isNotBlank()) {
                Text(status, color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = VueoPalette.Muted, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

