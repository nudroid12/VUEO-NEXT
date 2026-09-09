package com.vueo.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.core.TvSourceDiscoverySnapshot
import com.vueo.tv.detail.TvDetailScreen
import com.vueo.tv.home.TvHomeScreen
import com.vueo.tv.library.TvLibraryScreen
import com.vueo.tv.player.TvPlayerScreen
import com.vueo.tv.profile.TvProfilePickerScreen
import com.vueo.tv.profile.TvUserDnaScreen
import com.vueo.tv.search.TvSearchScreen
import com.vueo.tv.search.TvSearchSession
import com.vueo.tv.settings.TvConfirmDialog
import com.vueo.tv.settings.TvSettingsScreen
import com.vueo.tv.source.TvSourceScreen
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdatePrompt
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class TvRoute {
    STARTUP,
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
    DNA,
    PROFILE,
    DETAIL,
    SOURCE,
    PLAYER,
}

@Composable
fun VueoTvApp(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val runtime = remember { TvRuntime(context.applicationContext) }

    var route by remember { mutableStateOf(TvRoute.STARTUP) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    var sourceBundle by remember { mutableStateOf<TvSourceBundle?>(null) }
    var selectedSource by remember { mutableStateOf<StreamSource?>(null) }
    var initialPositionMs by remember { mutableLongStateOf(0L) }
    var updatePromptRelease by remember { mutableStateOf<TvUpdateRelease?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var detailBackStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var profileReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var dnaReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var detailReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var sourceReturnRoute by remember { mutableStateOf(TvRoute.DETAIL) }
    val searchSession = remember { TvSearchSession() }
    val sourceDiscoveryScope = rememberCoroutineScope()
    var sourceDiscoverySnapshot by remember { mutableStateOf<TvSourceDiscoverySnapshot?>(null) }
    var sourceDiscoveryError by remember { mutableStateOf<String?>(null) }
    var sourceDiscoveryKey by remember { mutableStateOf<String?>(null) }
    var sourceDiscoveryJob by remember { mutableStateOf<Job?>(null) }
    var sourceDiscoveryGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(runtime) {
        TvDesign.applyTheme(runtime.settingsStore.appTheme())
        TvDesign.applyAccent(runtime.settingsStore.appAccent())

        // Resolve the first destination using local state only. Network addon
        // manifests must never keep the TV app parked on its startup artwork.
        runtime.boot()
        route =
            if (runtime.profileStore.shouldShowPickerOnStartup()) {
                TvRoute.PROFILE
            } else {
                TvRoute.HOME
            }
        profileReturnRoute = TvRoute.HOME

        // Cache first, then prepare addons. Home can render the restored rows as
        // soon as disk IO completes, while the network refresh remains off the
        // startup critical path.
        launch {
            try {
                runtime.restoreHomeCache()
                refreshToken++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A broken cache must not block startup or addon preparation.
            }

            try {
                runtime.prepareAddonsInBackground()
                runtime.requestHomeRefreshAfterAddonPreparation()
                refreshToken++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep cached/local Home usable if addon preparation fails.
            }
        }

        launch { runtime.prepareProvidersInBackground() }
        if (runtime.settingsStore.automaticUpdateChecksEnabled()) {
            launch {
                val result = TvUpdateManager.check(context.applicationContext, force = false)
                updatePromptRelease = result.release?.takeIf { it.isNewerThanCurrent() }
            }
        }
    }

    fun navigate(label: String) {
        route = when (label) {
            "Home" -> TvRoute.HOME
            "Search" -> TvRoute.SEARCH
            "Library" -> TvRoute.LIBRARY
            "Settings" -> TvRoute.SETTINGS
            else -> route
        }
    }

    fun openDna(from: TvRoute) {
        dnaReturnRoute = from
        route = TvRoute.DNA
    }

    fun openProfilePicker(from: TvRoute) {
        profileReturnRoute = from
        route = TvRoute.PROFILE
    }

    fun openDetail(media: MediaItem, from: TvRoute) {
        selectedMedia = media
        selectedEpisode = null
        initialPositionMs = 0L
        detailBackStack = emptyList()
        detailReturnRoute = from
        route = TvRoute.DETAIL
    }

    fun closeDetail() {
        val previous = detailBackStack.lastOrNull()
        if (previous != null) {
            detailBackStack = detailBackStack.dropLast(1)
            selectedMedia = previous
            selectedEpisode = null
            initialPositionMs = 0L
        } else {
            route = detailReturnRoute
        }
    }

    fun resume(entry: LibraryPlaybackEntry, from: TvRoute) {
        selectedMedia = entry.media
        val resumeSeason = entry.season
        val resumeEpisode = entry.episode
        selectedEpisode =
            if (
                entry.media.type.lowercase() in setOf("series", "tv") &&
                resumeSeason != null &&
                resumeEpisode != null
            ) {
                EpisodeItem(
                    id = entry.videoId,
                    title = entry.episodeTitle ?: "Episode $resumeEpisode",
                    season = resumeSeason,
                    episode = resumeEpisode,
                )
            } else null
        initialPositionMs = entry.positionMs
        sourceReturnRoute = from
        route = TvRoute.SOURCE
    }

    fun sourceSessionKey(media: MediaItem, episode: EpisodeItem?): String =
        "${media.type}:${media.id}:${episode?.id ?: media.id}"

    fun stopSourceDiscovery(markStopped: Boolean) {
        sourceDiscoveryGeneration += 1
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryJob = null
        if (markStopped) {
            sourceDiscoverySnapshot = sourceDiscoverySnapshot?.let { current ->
                if (!current.searching) current
                else current.copy(
                    searching = false,
                    progress = if (current.bundle.sources.isEmpty()) {
                        "Discovery stopped"
                    } else {
                        "Discovery stopped • ${current.bundle.sources.size} unique sources"
                    },
                )
            }
        } else {
            sourceDiscoveryKey = null
            sourceDiscoverySnapshot = null
            sourceDiscoveryError = null
            sourceBundle = null
            selectedSource = null
        }
    }

    fun startSourceDiscovery(
        media: MediaItem,
        episode: EpisodeItem?,
        force: Boolean = false,
    ) {
        val key = sourceSessionKey(media, episode)
        if (
            !force &&
            sourceDiscoveryKey == key &&
            (sourceDiscoveryJob?.isActive == true || sourceDiscoverySnapshot != null)
        ) {
            return
        }

        sourceDiscoveryGeneration += 1
        val generation = sourceDiscoveryGeneration
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryKey = key
        sourceDiscoverySnapshot = null
        sourceDiscoveryError = null
        sourceBundle = null
        selectedSource = null

        sourceDiscoveryJob = sourceDiscoveryScope.launch {
            try {
                val finalBundle = runtime.discover(
                    item = media,
                    episode = episode,
                    onUpdate = { snapshot ->
                        if (
                            sourceDiscoveryGeneration == generation &&
                            sourceDiscoveryKey == key
                        ) {
                            sourceDiscoverySnapshot = snapshot
                            sourceBundle = snapshot.bundle
                        }
                    },
                )
                if (
                    sourceDiscoveryGeneration == generation &&
                    sourceDiscoveryKey == key
                ) {
                    sourceBundle = finalBundle
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (
                    sourceDiscoveryGeneration == generation &&
                    sourceDiscoveryKey == key
                ) {
                    sourceDiscoveryError = throwable.message ?: "Source discovery failed"
                    sourceDiscoverySnapshot = sourceDiscoverySnapshot?.copy(searching = false)
                }
            } finally {
                if (sourceDiscoveryGeneration == generation) {
                    sourceDiscoveryJob = null
                }
            }
        }
    }

    LaunchedEffect(route, selectedMedia?.id, selectedMedia?.type, selectedEpisode?.id) {
        when (route) {
            TvRoute.SOURCE -> selectedMedia?.let { media ->
                startSourceDiscovery(media, selectedEpisode)
            }
            TvRoute.PLAYER -> Unit
            else -> if (sourceDiscoveryJob?.isActive == true) {
                stopSourceDiscovery(markStopped = false)
            }
        }
    }

    BackHandler(
        enabled = route == TvRoute.HOME && updatePromptRelease == null && !showExitConfirm,
    ) {
        showExitConfirm = true
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvDesign.White,
            background = TvDesign.Black,
            surface = TvDesign.Surface,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(TvDesign.Black),
        ) {
            when (route) {
                TvRoute.STARTUP -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.vueo_tv_banner_art),
                            contentDescription = "Vueo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                TvRoute.HOME -> {
                    TvHomeScreen(
                        runtime = runtime,
                        refreshToken = refreshToken,
                        onNavigate = ::navigate,
                        onOpenMedia = { openDetail(it, TvRoute.HOME) },
                        onResume = { resume(it, TvRoute.HOME) },
                        onProfile = { openDna(TvRoute.HOME) },
                    )
                }

                TvRoute.SEARCH -> {
                    TvSearchScreen(
                        runtime = runtime,
                        contentVersion = refreshToken,
                        session = searchSession,
                        onNavigate = ::navigate,
                        onProfile = { openDna(TvRoute.SEARCH) },
                        onOpenMedia = { openDetail(it, TvRoute.SEARCH) },
                        onBack = { route = TvRoute.HOME },
                    )
                }

                TvRoute.LIBRARY -> {
                    TvLibraryScreen(
                        runtime = runtime,
                        refreshToken = refreshToken,
                        onNavigate = ::navigate,
                        onProfile = { openDna(TvRoute.LIBRARY) },
                        onOpenMedia = { openDetail(it, TvRoute.LIBRARY) },
                        onResume = { resume(it, TvRoute.LIBRARY) },
                        onBack = { route = TvRoute.HOME },
                    )
                }

                TvRoute.SETTINGS -> {
                    TvSettingsScreen(
                        runtime = runtime,
                        onNavigate = ::navigate,
                        onProfile = { openDna(TvRoute.SETTINGS) },
                        onBack = { route = TvRoute.HOME },
                        onDataChanged = { refreshToken++ },
                    )
                }

                TvRoute.DNA -> {
                    TvUserDnaScreen(
                        runtime = runtime,
                        dataVersion = refreshToken,
                        onSwitchProfiles = { openProfilePicker(TvRoute.DNA) },
                        onBack = { route = dnaReturnRoute },
                    )
                }

                TvRoute.PROFILE -> {
                    // Explicit preservation exception for the clean TV rebuild:
                    // Who’s Watching, Manage Profiles and Add/Edit Profile keep
                    // the approved TV experience while the app runtime around
                    // them is rebuilt from Mobile/Shared Core behavior.
                    TvProfilePickerScreen(
                        profileStore = runtime.profileStore,
                        onProfileSelected = {
                            refreshToken++
                                                route = profileReturnRoute
                        },
                        onProfilesChanged = {
                            refreshToken++
                        },
                    )
                }

                TvRoute.DETAIL -> {
                    val media = selectedMedia
                    if (media == null) {
                        route = detailReturnRoute
                    } else {
                        TvDetailScreen(
                            runtime = runtime,
                            initial = media,
                            onBack = ::closeDetail,
                            onWatch = { enriched, episode ->
                                selectedMedia = enriched
                                selectedEpisode = episode
                                initialPositionMs = 0L
                                sourceReturnRoute = TvRoute.DETAIL
                                route = TvRoute.SOURCE
                            },
                            onOpenRelated = { related ->
                                selectedMedia?.let { current ->
                                    detailBackStack = detailBackStack + current
                                }
                                selectedMedia = related
                                selectedEpisode = null
                                initialPositionMs = 0L
                            },
                            onLibraryChanged = { refreshToken++ },
                        )
                    }
                }

                TvRoute.SOURCE -> {
                    val media = selectedMedia
                    if (media == null) {
                        route = sourceReturnRoute
                    } else {
                        TvSourceScreen(
                            runtime = runtime,
                            media = media,
                            episode = selectedEpisode,
                            discovery = sourceDiscoverySnapshot.takeIf {
                                sourceDiscoveryKey == sourceSessionKey(media, selectedEpisode)
                            },
                            discoveryError = sourceDiscoveryError.takeIf {
                                sourceDiscoveryKey == sourceSessionKey(media, selectedEpisode)
                            },
                            onBack = {
                                stopSourceDiscovery(markStopped = false)
                                route = sourceReturnRoute
                            },
                            onRefresh = {
                                startSourceDiscovery(media, selectedEpisode, force = true)
                            },
                            onPlay = { bundle, source ->
                                sourceBundle = bundle
                                selectedSource = source
                                route = TvRoute.PLAYER
                            },
                        )
                    }
                }

                TvRoute.PLAYER -> {
                    val media = selectedMedia
                    val bundle = sourceBundle
                    val source = selectedSource
                    if (media == null || bundle == null || source == null) {
                        route = TvRoute.SOURCE
                    } else {
                        TvPlayerScreen(
                            runtime = runtime,
                            media = media,
                            episode = selectedEpisode,
                            bundle = bundle,
                            source = source,
                            initialPositionMs = initialPositionMs,
                            onBack = {
                                stopSourceDiscovery(markStopped = true)
                                route = TvRoute.SOURCE
                            },
                            onLibraryChanged = { refreshToken++ },
                            onPlayNextEpisode = { nextEpisode ->
                                stopSourceDiscovery(markStopped = false)
                                selectedEpisode = nextEpisode
                                initialPositionMs = 0L
                                sourceReturnRoute = TvRoute.DETAIL
                                route = TvRoute.SOURCE
                            },
                        )
                    }
                }
            }

            updatePromptRelease?.let { release ->
                TvUpdatePrompt(
                    release = release,
                    onLater = { updatePromptRelease = null },
                )
            }

            if (showExitConfirm) {
                TvConfirmDialog(
                    title = "Exit VUEO?",
                    message = "Close VUEO on this TV?",
                    confirmLabel = "Exit",
                    onDismiss = { showExitConfirm = false },
                    onConfirm = {
                        showExitConfirm = false
                        onExit()
                    },
                )
            }
        }
    }
}
