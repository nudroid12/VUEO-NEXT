package com.vueo.tv

import com.vueo.shared.core.diagnostics.AppCrashReport
import com.vueo.shared.core.diagnostics.CrashReportStore
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.shared.core.search.MediaEntityTarget
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.core.TvSourceDiscoverySnapshot
import com.vueo.tv.detail.TvDetailScreen
import com.vueo.tv.home.TvHomeFocusMemory
import com.vueo.tv.home.TvHomeScreen
import com.vueo.tv.home.rememberTvHomeRetainedState
import com.vueo.tv.library.TvLibraryScreen
import com.vueo.tv.player.TvPlayerScreen
import com.vueo.tv.player.TvEpisodeSwitchOverlay
import com.vueo.tv.profile.TvProfilePickerScreen
import com.vueo.tv.profile.TvUserDnaScreen
import com.vueo.tv.search.TvSearchScreen
import com.vueo.tv.search.TvSearchSession
import com.vueo.tv.search.TvEntityResultsScreen
import com.vueo.tv.settings.TvSettingsScreen
import com.vueo.tv.source.TvSourceScreen
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.tvPlayerFadeThrough
import com.vueo.tv.ui.motion.tvImmediateCut
import com.vueo.tv.ui.motion.tvScreenFadeThrough
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdatePrompt
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvRoute {
    STARTUP,
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
    DNA,
    PROFILE,
    DETAIL,
    ENTITY_RESULTS,
    SOURCE,
    PLAYER,
}

@Composable
fun VueoTvApp(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    var pendingCrash by remember { mutableStateOf<AppCrashReport?>(null) }
    var crashRecoveryLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pendingCrash = withContext(Dispatchers.IO) {
            runCatching { CrashReportStore.pending(context.applicationContext) }.getOrNull()
        }
        crashRecoveryLoaded = true
    }

    val runtime = remember {
        // New app session starts at the first Home card. Route changes within
        // this session keep the existing row/card focus memory.
        TvHomeFocusMemory.activeRowKey = null
        TvHomeFocusMemory.focusedIndexByRow.clear()
        TvRuntime(context.applicationContext)
    }
    val homeRetainedState = rememberTvHomeRetainedState(runtime)
    val homeSaveableState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    var route by remember { mutableStateOf(TvRoute.STARTUP) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var selectedEntityTarget by remember { mutableStateOf<MediaEntityTarget?>(null) }
    var selectedLibraryEntry by remember { mutableStateOf<LibraryPlaybackEntry?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    val sourceBundleState = remember { mutableStateOf<TvSourceBundle?>(null) }
    var sourceBundle by sourceBundleState
    var selectedSource by remember { mutableStateOf<StreamSource?>(null) }
    var initialPositionMs by remember { mutableLongStateOf(0L) }
    var playerSessionId by remember { mutableIntStateOf(0) }
    var updatePromptRelease by remember { mutableStateOf<TvUpdateRelease?>(null) }
    var detailBackStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var profileReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var profilePickerOpenedFromApp by remember { mutableStateOf(false) }
    var dnaReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var detailReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var sourceReturnRoute by remember { mutableStateOf(TvRoute.DETAIL) }
    var playerReturnRoute by remember { mutableStateOf(TvRoute.SOURCE) }
    val searchSession = remember { TvSearchSession() }
    val sourceDiscoveryScope = rememberCoroutineScope()
    var sourceDiscoverySnapshot by remember { mutableStateOf<TvSourceDiscoverySnapshot?>(null) }
    var sourceDiscoveryError by remember { mutableStateOf<String?>(null) }
    var sourceDiscoveryKey by remember { mutableStateOf<String?>(null) }
    var sourceDiscoveryJob by remember { mutableStateOf<Job?>(null) }
    var sourceDiscoveryGeneration by remember { mutableIntStateOf(0) }
    var failedSourceKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var switchingEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    var switchingBundle by remember { mutableStateOf<TvSourceBundle?>(null) }
    var switchingError by remember { mutableStateOf<String?>(null) }
    var switchingShowSources by remember { mutableStateOf(false) }
    var switchingCommitted by remember { mutableStateOf(false) }


    LaunchedEffect(runtime) {
        TvDesign.applyTheme(runtime.settingsStore.appTheme())
        TvDesign.applyAccent(runtime.settingsStore.appAccent())

        // Resolve the first destination using local state only. Network addon
        // manifests must never keep the TV app parked on its startup artwork.
        val showProfilePicker = withContext(Dispatchers.IO) {
            runtime.boot()
            runtime.profileStore.shouldShowPickerOnStartup()
        }
        route = if (showProfilePicker) TvRoute.PROFILE else TvRoute.HOME
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
                // Home observes installed addons progressively; keep fresh cache valid.
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

    LaunchedEffect(route) {
        RuntimeDiagnostics.recordScreen("TV ${route.name}")
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
        profilePickerOpenedFromApp = true
        route = TvRoute.PROFILE
    }

    fun openDetail(media: MediaItem, from: TvRoute) {
        selectedMedia = media
        selectedLibraryEntry = null
        selectedEpisode = null
        initialPositionMs = 0L
        detailBackStack = emptyList()
        detailReturnRoute = from
        route = TvRoute.DETAIL
    }

    fun openPlaybackDetail(entry: LibraryPlaybackEntry, from: TvRoute) {
        selectedMedia = entry.media
        selectedLibraryEntry = entry
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
            selectedLibraryEntry = null
            selectedEpisode = null
            initialPositionMs = 0L
        } else {
            selectedLibraryEntry = null
            route = detailReturnRoute
        }
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

    fun cancelEpisodeSwitch() {
        stopSourceDiscovery(markStopped = true)
        switchingEpisode = null
        switchingBundle = null
        switchingError = null
        switchingShowSources = false
        switchingCommitted = false
    }

    fun commitEpisodeSwitch(target: EpisodeItem, nextBundle: TvSourceBundle, candidate: StreamSource) {
        if (route != TvRoute.PLAYER || switchingEpisode?.id != target.id) return
        switchingCommitted = true
        switchingError = null
        switchingShowSources = false
        selectedEpisode = target
        initialPositionMs = 0L
        sourceBundle = nextBundle
        selectedSource = candidate
        playerSessionId += 1
    }

    fun startEpisodeSwitch(target: EpisodeItem, force: Boolean = false) {
        val media = selectedMedia ?: return
        if (route != TvRoute.PLAYER) return
        if (!force && switchingEpisode != null) return
        sourceDiscoveryGeneration += 1
        val generation = sourceDiscoveryGeneration
        sourceDiscoveryJob?.cancel()
        switchingEpisode = target
        switchingBundle = null
        switchingError = null
        switchingShowSources = false
        switchingCommitted = false
        val targetKey = sourceSessionKey(media, target)
        sourceDiscoveryKey = targetKey
        sourceDiscoverySnapshot = null
        sourceDiscoveryError = null
        var committed = false
        val preferredQuality = runtime.settingsStore.preferredQuality().rankKey

        fun accept(nextBundle: TvSourceBundle, completed: Boolean) {
            if (sourceDiscoveryGeneration != generation || route != TvRoute.PLAYER) return
            switchingBundle = nextBundle
            if (committed) {
                // Late subtitle/source results belong only to the committed episode.
                if (sourceBundle?.videoId == nextBundle.videoId) sourceBundle = nextBundle
                return
            }
            val ranked = nextBundle.sources.filter { it.isDirectPlayable }
                .sortedWith(PlayerSourcePolicy.comparator(preferredQuality, media.originalLanguage))
            val candidate = ranked.firstOrNull {
                val assessment = PlayerSourcePolicy.assess(it, preferredQuality, media.originalLanguage)
                assessment.quality.automaticRecoveryEligible && assessment.audioMatch.recommendationEligible
            } ?: ranked.firstOrNull()?.takeIf { completed }
            if (candidate != null) {
                committed = true
                failedSourceKeys = failedSourceKeys - targetKey
                commitEpisodeSwitch(target, nextBundle, candidate)
            } else if (completed) {
                failedSourceKeys = failedSourceKeys + targetKey
                switchingError = "No playable source found for this episode."
            }
        }

        sourceDiscoveryJob = sourceDiscoveryScope.launch {
            try {
                val result = runtime.discover(
                    item = media,
                    episode = target,
                    forceRefresh = force || targetKey in failedSourceKeys,
                    onUpdate = { snapshot ->
                        if (sourceDiscoveryGeneration == generation && route == TvRoute.PLAYER) {
                            sourceDiscoverySnapshot = snapshot
                            accept(snapshot.bundle, completed = !snapshot.searching)
                        }
                    },
                )
                accept(result, completed = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (sourceDiscoveryGeneration == generation && route == TvRoute.PLAYER && !committed) {
                    failedSourceKeys = failedSourceKeys + targetKey
                    switchingError = error.message ?: "Episode source discovery failed."
                }
            } finally {
                if (sourceDiscoveryGeneration == generation) sourceDiscoveryJob = null
            }
        }
    }

    fun startSourceDiscovery(
        media: MediaItem,
        episode: EpisodeItem?,
        force: Boolean = false,
    ) {
        val key = sourceSessionKey(media, episode)
        val effectiveForce = force || key in failedSourceKeys
        if (
            !effectiveForce &&
            sourceDiscoveryKey == key &&
            (sourceDiscoveryJob?.isActive == true || sourceDiscoverySnapshot != null)
        ) {
            return
        }

        sourceDiscoveryGeneration += 1
        val generation = sourceDiscoveryGeneration
        val previousKey = sourceDiscoveryKey
        val previousSnapshot = sourceDiscoverySnapshot
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryKey = key
        sourceDiscoverySnapshot = previousSnapshot
            ?.takeIf { effectiveForce && previousKey == key }
            ?.copy(
                searching = true,
                progress = "Refreshing sources…",
            )
        sourceDiscoveryError = null
        sourceBundle = null
        selectedSource = null

        sourceDiscoveryJob = sourceDiscoveryScope.launch {
            try {
                val finalBundle = runtime.discover(
                    item = media,
                    episode = episode,
                    forceRefresh = effectiveForce,
                    onUpdate = { snapshot ->
                        if (
                            sourceDiscoveryGeneration == generation &&
                            sourceDiscoveryKey == key
                        ) {
                            sourceDiscoverySnapshot = snapshot
                            sourceBundle = snapshot.bundle
                            if (!snapshot.searching) {
                                failedSourceKeys =
                                    if (snapshot.bundle.sources.any { it.isDirectPlayable }) {
                                        failedSourceKeys - key
                                    } else {
                                        failedSourceKeys + key
                                    }
                            }
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
                    failedSourceKeys = failedSourceKeys + key
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
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (initialState == TvRoute.STARTUP) {
                        tvScreenFadeThrough()
                    } else if (targetState == TvRoute.HOME) {
                        tvImmediateCut()
                    } else if (initialState == TvRoute.PLAYER || targetState == TvRoute.PLAYER) {
                        tvPlayerFadeThrough()
                    } else {
                        tvScreenFadeThrough()
                    }
                },
                label = "vueoRootRoute",
            ) { displayedRoute ->
                when (displayedRoute) {
                TvRoute.STARTUP -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.vueo_tv_logo),
                            contentDescription = "VUEO",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }

                TvRoute.HOME -> {
                    homeSaveableState.SaveableStateProvider("home:${runtime.profileStore.activeProfileId()}") {
                    TvHomeScreen(
                        runtime = runtime,
                        retainedState = homeRetainedState,
                        refreshToken = refreshToken,
                        onNavigate = ::navigate,
                        onOpenMedia = { openDetail(it, TvRoute.HOME) },
                        onResume = { openPlaybackDetail(it, TvRoute.HOME) },
                        onProfile = { openDna(TvRoute.HOME) },
                        onBack = onExit,
                    )
                    }
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
                        onResume = { openPlaybackDetail(it, TvRoute.LIBRARY) },
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
                        onResetComplete = { openProfilePicker(TvRoute.HOME) },
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
                            profilePickerOpenedFromApp = false
                            route = profileReturnRoute
                        },
                        onProfilesChanged = {
                            refreshToken++
                        },
                        onBack = {
                            if (profilePickerOpenedFromApp) {
                                profilePickerOpenedFromApp = false
                                route = profileReturnRoute
                            } else {
                                onExit()
                            }
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
                            initialLibraryEntry = selectedLibraryEntry,
                            onBack = ::closeDetail,
                            onWatch = { enriched, episode, startPositionMs ->
                                selectedMedia = enriched
                                selectedEpisode = episode
                                initialPositionMs = startPositionMs
                                sourceReturnRoute = TvRoute.DETAIL
                                route = TvRoute.SOURCE
                            },
                            onOpenRelated = { related ->
                                selectedMedia?.let { current ->
                                    detailBackStack = detailBackStack + current
                                }
                                selectedMedia = related
                                selectedLibraryEntry = null
                                selectedEpisode = null
                                initialPositionMs = 0L
                            },
                            onOpenEntity = { target ->
                                selectedEntityTarget = target
                                route = TvRoute.ENTITY_RESULTS
                            },
                            onLibraryChanged = { refreshToken++ },
                        )
                    }
                }

                TvRoute.ENTITY_RESULTS -> {
                    val target = selectedEntityTarget
                    if (target == null) {
                        route = TvRoute.DETAIL
                    } else {
                        TvEntityResultsScreen(
                            runtime = runtime,
                            target = target,
                            onBack = {
                                selectedEntityTarget = null
                                route = TvRoute.DETAIL
                            },
                            onOpenMedia = { next ->
                                selectedMedia?.let { current ->
                                    detailBackStack = detailBackStack + current
                                }
                                selectedMedia = next
                                selectedLibraryEntry = null
                                selectedEpisode = null
                                initialPositionMs = 0L
                                selectedEntityTarget = null
                                route = TvRoute.DETAIL
                            },
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
                                playerReturnRoute = TvRoute.SOURCE
                                playerSessionId += 1
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
                        val playbackSession = playerSessionId
                        key(bundle.videoId, playbackSession) {
                            TvPlayerScreen(
                                runtime = runtime,
                                media = media,
                                episode = selectedEpisode,
                                bundle = bundle,
                                bundleState = sourceBundleState,
                                source = source,
                                initialPositionMs = initialPositionMs,
                                playerSessionId = playbackSession,
                                onBack = {
                                    cancelEpisodeSwitch()
                                    route = playerReturnRoute
                                },
                                onLibraryChanged = { refreshToken++ },
                                onPlayNextEpisode = { startEpisodeSwitch(it) },
                                episodeSwitching = switchingEpisode != null,
                                onEpisodeFrameReady = {
                                    if (playerSessionId == playbackSession && switchingCommitted && switchingEpisode?.id == bundle.videoId) {
                                        switchingEpisode = null
                                        switchingError = null
                                        switchingShowSources = false
                                    }
                                },
                                onEpisodePlaybackFailed = { message ->
                                    if (playerSessionId == playbackSession && switchingCommitted && switchingEpisode?.id == bundle.videoId) {
                                        switchingError = message
                                    }
                                },
                            )
                        }
                        switchingEpisode?.let { target ->
                            TvEpisodeSwitchOverlay(
                                episode = target,
                                fallbackImage = media.background,
                                error = switchingError,
                                showSources = switchingShowSources,
                                sources = switchingBundle?.sources.orEmpty().filter { it.isDirectPlayable },
                                onRetry = { startEpisodeSwitch(target, force = true) },
                                onShowSources = { switchingShowSources = true },
                                onSelectSource = { candidate ->
                                    switchingBundle?.let { commitEpisodeSwitch(target, it, candidate) }
                                },
                                onCancel = { cancelEpisodeSwitch() },
                            )
                        }
                    }
                }
                }
            }

            if (route != TvRoute.STARTUP && route != TvRoute.PROFILE) {
                pendingCrash?.let { report ->
                    TvCrashRecoveryPopup(report = report, onClosed = { pendingCrash = null })
                }
            }
            updatePromptRelease?.takeIf { route != TvRoute.PLAYER && crashRecoveryLoaded && pendingCrash == null }?.let { release ->
                TvUpdatePrompt(
                    release = release,
                    onLater = { updatePromptRelease = null },
                )
            }

        }
    }
}
