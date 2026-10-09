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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap

private data class TvProfilePanelStats(
    val dna: UserDnaSnapshot?,
    val myListCount: Int,
    val watchedTitlesCount: Int,
    val dataVersion: Int,
)

// Accessed on Main only. Weak runtime ownership avoids retaining an old app
// session; each runtime retains at most eight profile/toggle snapshots.
private val profilePanelCache = WeakHashMap<TvRuntime, LinkedHashMap<Pair<String, Boolean>, TvProfilePanelStats>>()

@Composable
internal fun TvProfileSettings(
    runtime: TvRuntime,
    onOpenDna: () -> Unit,
    onOpenProfiles: () -> Unit,
    dataVersion: Int,
) {
    val profile = runtime.profileStore.activeProfile()
    val dnaEnabled = runtime.dnaPreferences.userDnaEnabled(profile.id)
    val cacheKey = profile.id to dnaEnabled
    var profileStats by remember(runtime, profile.id, dnaEnabled, dataVersion) {
        mutableStateOf(profilePanelCache[runtime]?.get(cacheKey)?.takeIf { it.dataVersion == dataVersion })
    }

    LaunchedEffect(runtime, profile.id, dnaEnabled, dataVersion) {
        val resolved = withContext(Dispatchers.IO) {
            val watchlist = runtime.libraryStore.watchlist()
            val history = runtime.libraryStore.history()
            TvProfilePanelStats(
                dna = if (dnaEnabled) {
                    runtime.dnaEngine.analyze(
                        history = history,
                        myList = watchlist,
                    )
                } else {
                    null
                },
                dataVersion = dataVersion,
                myListCount = watchlist.size,
                watchedTitlesCount = history
                    .asSequence()
                    .filter { it.positionMs > 5_000L }
                    .map { "${it.media.type}:${it.media.id}" }
                    .distinct()
                    .count(),
            )
        }
        if (runtime.profileStore.activeProfileId() != profile.id) return@LaunchedEffect
        val cache = profilePanelCache.getOrPut(runtime) { LinkedHashMap() }
        cache.remove(cacheKey)
        cache[cacheKey] = resolved
        while (cache.size > 8) cache.remove(cache.keys.first())
        profileStats = resolved
    }

    val dnaSnapshot = profileStats?.dna
    val myListCount = profileStats?.myListCount
    val watchedTitlesCount = profileStats?.watchedTitlesCount
    val viewingClass = watchedTitlesCount?.let(::tvViewingClass)
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
            when {
                !dnaEnabled -> "Enable User DNA in Personalization."
                profileStats == null -> "Loading local taste…"
                else -> "Keep watching to shape your DNA class."
            }
        }

    TvSettingsProfilePanel(
        profileName = profile.name,
        profileSubtitle = viewingClass?.let { "$it • $dnaClass" } ?: "Loading profile…",
        avatarDrawableRes = ProfileAvatarCatalog.drawableRes(profile.avatar),
        myListCount = myListCount,
        watchedCount = watchedTitlesCount,
        dnaValue = when {
            !dnaEnabled -> "Off"
            dnaSnapshot != null -> "${dnaSnapshot.confidencePercent}%"
            else -> "—"
        },
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
