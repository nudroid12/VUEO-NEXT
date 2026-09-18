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
internal fun TvProfileSettings(
    runtime: TvRuntime,
    onOpenDna: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val profile = runtime.profileStore.activeProfile()
    val dnaEnabled = runtime.dnaPreferences.userDnaEnabled(profile.id)
    val dnaSnapshot = if (dnaEnabled) runtime.dnaEngine.build() else null
    val myListCount = runtime.libraryStore.watchlist().size
    val watchedTitlesCount = runtime.libraryStore
        .history()
        .filter { it.positionMs > 5_000L }
        .map { "${it.media.type}:${it.media.id}" }
        .distinct()
        .size
    val viewingClass = tvViewingClass(watchedTitlesCount)
    val dnaClass = when {
        !dnaEnabled -> "DNA Off"
        dnaSnapshot == null -> "Finding Your Taste"
        else -> tvDnaClass(dnaSnapshot)
    }
    val tastePreview = dnaSnapshot
        ?.topGenres
        ?.take(3)
        ?.joinToString(" • ") { "${it.name} ${it.percent}%" }
        .orEmpty()
        .ifBlank {
            if (dnaEnabled) "Keep watching to shape your DNA class."
            else "Enable User DNA in Personalization."
        }

    TvSettingsProfilePanel(
        profileName = profile.name,
        profileSubtitle = "$viewingClass • $dnaClass",
        avatarDrawableRes = ProfileAvatarCatalog.drawableRes(profile.avatar),
        myListCount = myListCount,
        watchedCount = watchedTitlesCount,
        dnaValue = dnaSnapshot?.let { "${it.confidencePercent}%" } ?: "Off",
        tastePreview = tastePreview,
        onOpenDna = onOpenDna,
        onSwitchProfiles = onOpenProfiles,
    )
}

internal fun tvViewingClass(watchedTitles: Int): String = when {
    watchedTitles < 10 -> "Baby VUEO"
    watchedTitles < 30 -> "Explorer"
    watchedTitles < 75 -> "Binger"
    watchedTitles < 150 -> "Cinephile"
    watchedTitles < 300 -> "Screen Veteran"
    else -> "VUEO Legend"
}

internal fun tvDnaClass(snapshot: UserDnaSnapshot): String {
    if (snapshot.confidencePercent < 20 || snapshot.topGenres.isEmpty()) {
        return "Finding Your Taste"
    }

    val genres = snapshot.topGenres.associate {
        it.name.lowercase(Locale.US) to it.percent
    }
    fun score(vararg names: String): Int = names.sumOf {
        genres[it.lowercase(Locale.US)] ?: 0
    }

    val topGenrePercent = snapshot.topGenres.firstOrNull()?.percent ?: 0
    if (snapshot.topGenres.size >= 5 && topGenrePercent <= 30) {
        return "The Explorer"
    }

    val classes = listOf(
        "The Adventurer" to score("Action", "Adventure", "Fantasy"),
        "The Detective" to score("Crime", "Mystery", "Thriller"),
        "The Thrill Seeker" to score("Horror", "Thriller", "Action"),
        "The Romantic" to score("Romance", "Drama"),
        "The Dreamer" to score("Science Fiction", "Fantasy", "Animation"),
        "The Mood Lifter" to score("Comedy", "Family", "Animation"),
        "The Story Hunter" to score("Drama", "History", "Documentary"),
    )
    val best = classes.maxByOrNull { it.second }
    if (best != null && best.second >= 20) return best.first

    return when (snapshot.topGenres.firstOrNull()?.name?.lowercase(Locale.US)) {
        "crime", "mystery" -> "The Detective"
        "horror", "thriller" -> "The Thrill Seeker"
        "romance" -> "The Romantic"
        "science fiction", "fantasy" -> "The Dreamer"
        "comedy" -> "The Mood Lifter"
        "action", "adventure" -> "The Adventurer"
        else -> "The Story Hunter"
    }
}

@Composable
internal fun TvProfileChooserSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onDataChanged: () -> Unit,
    onProfileSelected: () -> Unit,
    onBack: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { runtime.profileStore.profiles() }
    val activeProfileId = remember(revision) { runtime.profileStore.activeProfileId() }
    var askStartup by remember { mutableStateOf(runtime.profileStore.askWhoIsWatchingOnStartup()) }
    var lockedProfileId by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    val lockedProfile = lockedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    if (lockedProfile != null) {
        androidx.compose.runtime.key(lockedProfile.id, pinResetToken) {
            com.vueo.tv.profile.TvPinEntryOverlay(
                title = "Unlock ${lockedProfile.name}",
                subtitle = "Enter the 4-digit profile PIN",
                errorText = pinError,
                onComplete = { pin ->
                    if (runtime.profileStore.verifyProfilePin(lockedProfile.id, pin)) {
                        pinError = null
                        lockedProfileId = null
                        if (runtime.profileStore.setActiveProfile(lockedProfile.id)) {
                            revision += 1
                            onDataChanged()
                            onProfileSelected()
                        }
                    } else {
                        pinError = "Incorrect PIN"
                        pinResetToken += 1
                    }
                },
                onCancel = {
                    pinError = null
                    lockedProfileId = null
                },
            )
        }
    }

    val entries = buildList {
        add(
            toggleEntry(
                id = "startup-picker",
                title = "Ask who’s watching on startup",
                subtitle = "Show profile selection before Home opens.",
                checked = askStartup,
            ) {
                askStartup = it
                runtime.profileStore.setAskWhoIsWatchingOnStartup(it)
            }.copy(section = "PROFILE STARTUP", icon = Icons.Default.AccountCircle)
        )
        profiles.forEach { profile ->
            val active = profile.id == activeProfileId
            val locked = runtime.profileStore.hasProfilePin(profile.id)
            add(
                TvSettingsEntry(
                    id = "profile-${profile.id}",
                    title = profile.name,
                    subtitle = buildString {
                        append(if (profile.isKids) "Kids profile" else "Standard profile")
                        if (locked) append(" • PIN protected")
                    },
                    value = if (active) "Active" else "Switch",
                    onActivate = {
                        if (active) {
                            onProfileSelected()
                        } else if (locked) {
                            pinError = null
                            lockedProfileId = profile.id
                        } else if (runtime.profileStore.setActiveProfile(profile.id)) {
                            revision += 1
                            onDataChanged()
                            onProfileSelected()
                        }
                    },
                    section = "PROFILES",
                    icon = Icons.Default.AccountCircle,
                )
            )
        }
    }

    TvSettingsListScreen(
        title = "Profiles",
        subtitle = "Switch the active profile without leaving Settings.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Profile",
    )
}

