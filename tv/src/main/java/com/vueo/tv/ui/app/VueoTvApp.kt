package com.vueo.tv

import com.vueo.shared.core.diagnostics.AppCrashReport
import com.vueo.shared.core.diagnostics.CrashReportStore
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.source.SourceDiscoveryControl
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.shared.core.player.NextEpisodePrefetchValidator
import com.vueo.shared.core.player.PrefetchedSourceValidation
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
import com.vueo.tv.ui.LocalTvModalFocusHost
import com.vueo.tv.ui.TvModalFocusHost
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.tvPlayerFadeThrough
import com.vueo.tv.ui.motion.tvScreenFadeThrough
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdatePrompt
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

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
    val backgroundFocus = remember { FocusRequester() }
    val backgroundFocusManager = LocalFocusManager.current
    var modalDepth by remember { mutableIntStateOf(0) }
    val modalFocusHost = remember(backgroundFocus, backgroundFocusManager, sourceDiscoveryScope) {
        TvModalFocusHost(
            open = {
                if (modalDepth == 0) {
                    backgroundFocus.saveFocusedChild()
                    backgroundFocusManager.clearFocus(force = true)
                }
                modalDepth += 1
            },
            close = {
                modalDepth = (modalDepth - 1).coerceAtLeast(0)
                sourceDiscoveryScope.launch {
                    withFrameNanos { }
                    if (modalDepth == 0) {
                        if (!backgroundFocus.restoreFocusedChild()) backgroundFocus.requestFocus()
                    }
                }
            },
        )
    }
    var sourceDiscoverySnapshot by remember { mutableStateOf<TvSourceDiscoverySnapshot?>(null) }
    var sourceDiscoveryError by remember { mutableStateOf<String?>(null) }
    var sourceDiscoveryKey by remember { mutableStateOf<String?>(null) }
    // Keep the final Sources frame alive while AnimatedContent fades the route out.
    // Operational discovery state can be cleared immediately without flashing an empty state.
    var sourceExitSnapshot by remember { mutableStateOf<TvSourceDiscoverySnapshot?>(null) }
    var sourceExitError by remember { mutableStateOf<String?>(null) }
    var sourceExitKey by remember { mutableStateOf<String?>(null) }
    var sourceExitRunning by remember { mutableStateOf(false) }
    var sourceDiscoveryJob by remember { mutableStateOf<Job?>(null) }
    var sourceDiscoveryGeneration by remember { mutableIntStateOf(0) }
    var sourceDiscoveryControl by remember { mutableStateOf<SourceDiscoveryControl?>(null) }
    var episodePrefetch by remember { mutableStateOf<TvEpisodePrefetch?>(null) }
    var playingEpisodePrefetch by remember { mutableStateOf<TvEpisodePrefetch?>(null) }
    var failedSourceKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var switchingEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    var switchingPreferredSource by remember { mutableStateOf<StreamSource?>(null) }
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

    suspend fun refreshPlayerSubtitles(
        media: MediaItem,
        episode: EpisodeItem?,
        expectedVideoId: String,
        expectedSession: Int,
    ): Int {
        val initialBundle = sourceBundle
            ?.takeIf { it.videoId == expectedVideoId }
            ?: return 0
        val beforeUrls = initialBundle.subtitles.mapTo(linkedSetOf()) { it.url }
        RuntimeDiagnostics.recordPlayerEvent(
            "TV",
            "SUBTITLE_REFRESH_START",
            "video=$expectedVideoId existing=${beforeUrls.size}",
        )

        fun mergeIfCurrent(discovered: List<SubtitleTrack>) {
            if (
                route != TvRoute.PLAYER ||
                playerSessionId != expectedSession ||
                sourceBundle?.videoId != expectedVideoId
            ) return
            val current = sourceBundle ?: return
            sourceBundle = current.copy(
                subtitles = (current.subtitles + discovered).distinctBy { it.url },
            )
        }

        val refreshed = runtime.refreshSubtitles(
            item = media,
            episode = episode,
            onProgress = ::mergeIfCurrent,
        )
        mergeIfCurrent(refreshed)

        val current = sourceBundle
            ?.takeIf {
                route == TvRoute.PLAYER &&
                    playerSessionId == expectedSession &&
                    it.videoId == expectedVideoId
            }
            ?: return 0
        val added = current.subtitles.count { it.url !in beforeUrls }
        RuntimeDiagnostics.recordPlayerEvent(
            "TV",
            "SUBTITLE_REFRESH_DONE",
            "video=$expectedVideoId added=$added total=${current.subtitles.size}",
        )
        return added
    }

    fun cancelEpisodePrefetch() {
        playingEpisodePrefetch?.cancel()
        playingEpisodePrefetch = null
        episodePrefetch?.cancel()
        episodePrefetch = null
    }

    fun startEpisodePrefetch(
        target: EpisodeItem,
        current: StreamSource,
        seedSubtitles: List<SubtitleTrack> = emptyList(),
    ) {
        val media = selectedMedia ?: return
        if (route != TvRoute.PLAYER || switchingEpisode != null) return
        if (episodePrefetch?.originSession == playerSessionId) return
        if (episodePrefetch?.claimed == true) {
            // A short episode can reach its prefetch window while its own
            // subtitle retries are still finishing. Keep those retries alive.
            playingEpisodePrefetch?.cancel()
            playingEpisodePrefetch = episodePrefetch
            episodePrefetch = null
        } else {
            episodePrefetch?.cancel()
        }
        val pending = TvEpisodePrefetch(
            mediaKey = "${media.type}:${media.id}",
            originSession = playerSessionId,
            target = target,
            preferredSource = current,
            seedSubtitles = seedSubtitles,
        )
        episodePrefetch = pending
        RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_PREFETCH_START",
            "episode=S${target.season}E${target.episode} provider=${current.providerName}")
        pending.job = sourceDiscoveryScope.launch {
            try {
                runtime.discover(
                    item = media, episode = target, forceRefresh = true,
                    sourceProviderName = current.providerName,
                    discoverSubtitles = seedSubtitles.isEmpty(),
                    discoveryControl = pending.control,
                    onUpdate = { snapshot ->
                        if (episodePrefetch === pending || playingEpisodePrefetch === pending) {
                            pending.subtitlesResolved = snapshot.subtitlesResolved
                            val previous = pending.bundle
                            pending.bundle = snapshot.bundle.copy(subtitles =
                                (pending.seedSubtitles + previous?.subtitles.orEmpty() + snapshot.bundle.subtitles)
                                    .distinctBy { it.url })
                            if (pending.matchedSource == null) {
                                pending.matchedSource = com.vueo.shared.core.player.NextEpisodeSourcePolicy.matchingServer(
                                    snapshot.bundle.sources, current,
                                    runtime.settingsStore.preferredQuality().rankKey, media.originalLanguage,
                                )
                                if (pending.matchedSource != null) {
                                    pending.matchedAtMs = android.os.SystemClock.elapsedRealtime()
                                    pending.control.stopSources()
                                    pending.sourcesReady.complete(Unit)
                                    RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_PREFETCH_READY",
                                        "episode=S${target.season}E${target.episode} server=${com.vueo.shared.core.player.PlayerSourceDisplay.title(requireNotNull(pending.matchedSource))}")
                                }
                            }
                            // Standby retains only the exact server; other returned servers
                            // do not become background fallback candidates.
                            pending.bundle = pending.bundle?.copy(sources = listOfNotNull(pending.matchedSource))
                            if (!snapshot.searching) pending.sourcesReady.complete(Unit)
                            pending.onSubtitles?.invoke()
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                pending.failed = true
            } finally {
                pending.sourcesReady.complete(Unit)
            }
        }
    }

    suspend fun validateEpisodePrefetch(
        target: EpisodeItem,
        current: StreamSource,
        checkpointSeconds: Int,
    ) {
        repeat(2) {
            val pending = episodePrefetch
            if (pending == null) {
                startEpisodePrefetch(target, current)
                return
            }

            var restart = false
            var restartSubtitles = emptyList<SubtitleTrack>()
            var handled = false

            pending.validationMutex.withLock {
                if (episodePrefetch !== pending || pending.claimed) {
                    return@withLock
                }
                if (
                    pending.target.id != target.id ||
                    !com.vueo.shared.core.player.NextEpisodeSourcePolicy.sameServer(
                        pending.preferredSource,
                        current,
                    )
                ) {
                    return@withLock
                }

                handled = true
                val matched = pending.matchedSource
                if (matched == null) {
                    if (pending.job?.isActive != true) {
                        restart = true
                        restartSubtitles = pending.bundle?.subtitles.orEmpty()
                    }
                    RuntimeDiagnostics.recordPlayerEvent(
                        "TV",
                        "NEXT_PREFETCH_VALIDATE",
                        "checkpoint=${checkpointSeconds}s state=${if (restart) "missing" else "pending"}",
                    )
                    return@withLock
                }

                val result = NextEpisodePrefetchValidator.validate(matched)
                RuntimeDiagnostics.recordPlayerEvent(
                    "TV",
                    "NEXT_PREFETCH_VALIDATE",
                    "checkpoint=${checkpointSeconds}s result=${result.name.lowercase()} " +
                        "server=${com.vueo.shared.core.player.PlayerSourceDisplay.title(matched)}",
                )
                if (result != PrefetchedSourceValidation.VALID) {
                    restart = true
                    restartSubtitles = pending.bundle?.subtitles.orEmpty()
                }
            }

            if (!handled) return@repeat
            if (!restart) return
            if (episodePrefetch === pending) {
                pending.cancel()
                episodePrefetch = null
                RuntimeDiagnostics.recordPlayerEvent(
                    "TV",
                    "NEXT_PREFETCH_REFRESH",
                    "checkpoint=${checkpointSeconds}s provider=${current.providerName}",
                )
                startEpisodePrefetch(
                    target = target,
                    current = current,
                    seedSubtitles = restartSubtitles,
                )
            }
            return
        }
    }

    fun stopSourceDiscovery(markStopped: Boolean) {
        sourceDiscoveryGeneration += 1
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryJob = null
        sourceDiscoveryControl = null
        if (markStopped) {
            sourceDiscoverySnapshot = sourceDiscoverySnapshot?.let { current ->
                if (!current.searching) current
                else current.copy(
                    searching = false,
                    loadingProviders = emptyList(),
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
        cancelEpisodePrefetch()
        stopSourceDiscovery(markStopped = true)
        switchingEpisode = null
        switchingPreferredSource = null
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
        sourceDiscoveryControl?.stopPlugins()
        selectedSource = candidate
        playerSessionId += 1
    }

    fun startEpisodeSwitch(target: EpisodeItem, force: Boolean = false, preferredSource: StreamSource? = selectedSource) {
        val media = selectedMedia ?: return
        if (route != TvRoute.PLAYER) return
        if (!force && switchingEpisode != null) return
        val prefetched = episodePrefetch?.takeIf {
            !force && it.reusable("${media.type}:${media.id}", playerSessionId, target,
                preferredSource, android.os.SystemClock.elapsedRealtime())
        }
        playingEpisodePrefetch?.cancel()
        playingEpisodePrefetch = null
        if (prefetched == null) cancelEpisodePrefetch()
        else prefetched.claimed = true
        sourceDiscoveryGeneration += 1
        val generation = sourceDiscoveryGeneration
        sourceDiscoveryJob?.cancel()
        val discoveryControl = SourceDiscoveryControl()
        sourceDiscoveryControl = prefetched?.control ?: discoveryControl
        switchingEpisode = target
        switchingPreferredSource = preferredSource
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
        var lastSubtitleCount = -1
        var waitLogged = false

        fun publishCommittedSubtitles(nextBundle: TvSourceBundle) {
            if (
                sourceDiscoveryGeneration != generation ||
                route != TvRoute.PLAYER ||
                selectedEpisode?.id != target.id ||
                sourceBundle?.videoId != nextBundle.videoId
            ) return
            val previous = sourceBundle ?: return
            val mergedSubtitles = (previous.subtitles + nextBundle.subtitles).distinctBy { it.url }
            if (mergedSubtitles != previous.subtitles) {
                sourceBundle = previous.copy(subtitles = mergedSubtitles)
                RuntimeDiagnostics.recordPlayerEvent(
                    "TV",
                    "NEXT_SUBTITLES_HANDOFF",
                    "episode=S${target.season}E${target.episode} tracks=${mergedSubtitles.size}",
                )
            }
        }

        RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_START",
            "episode=S${target.season}E${target.episode} provider=${preferredSource?.providerName.orEmpty()} server=${preferredSource?.let(com.vueo.shared.core.player.PlayerSourceDisplay::title).orEmpty()}")

        fun accept(nextBundle: TvSourceBundle, completed: Boolean) {
            if (sourceDiscoveryGeneration != generation || route != TvRoute.PLAYER) return
            switchingBundle = nextBundle
            if (nextBundle.subtitles.size != lastSubtitleCount) {
                lastSubtitleCount = nextBundle.subtitles.size
                RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_SUBTITLES_RECEIVED",
                    "episode=S${target.season}E${target.episode} tracks=$lastSubtitleCount committed=$committed")
            }
            if (committed || (switchingCommitted && selectedEpisode?.id == target.id)) {
                // Late subtitle results belong to the committed episode even after the
                // source was selected and the new Player composable already started.
                publishCommittedSubtitles(nextBundle)
                return
            }
            val preferredProviderDone = preferredSource == null ||
                sourceDiscoverySnapshot?.completedSourceProviders.orEmpty().any {
                    it.trim().equals(preferredSource.providerName.trim(), true)
                } || sourceDiscoverySnapshot?.let { snapshot ->
                    snapshot.plannedSourceProviders.none {
                        it.trim().equals(preferredSource?.providerName?.trim(), true)
                    }
                } == true
            val candidate = com.vueo.shared.core.player.NextEpisodeSourcePolicy.select(
                sources = nextBundle.sources, current = preferredSource,
                preferredProviderDone = preferredProviderDone, completed = completed,
                preferredQuality = preferredQuality, originalLanguage = media.originalLanguage,
            )
            if (candidate == null && !preferredProviderDone && !waitLogged && nextBundle.sources.isNotEmpty()) {
                waitLogged = true
                RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_WAIT_CURRENT_PROVIDER",
                    "episode=S${target.season}E${target.episode} fallbackSources=${nextBundle.sources.size}")
            }
            if (candidate != null) {
                RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_SOURCE_SELECTED",
                    "episode=S${target.season}E${target.episode} provider=${candidate.providerName} server=${com.vueo.shared.core.player.PlayerSourceDisplay.title(candidate)} preferredProviderDone=$preferredProviderDone")
                committed = true
                failedSourceKeys = failedSourceKeys - targetKey
                commitEpisodeSwitch(target, nextBundle, candidate)
            } else if (completed) {
                failedSourceKeys = failedSourceKeys + targetKey
                switchingError = "No playable source found for this episode."
            }
        }

        fun withPrefetchedSubtitles(next: TvSourceBundle): TvSourceBundle = next.copy(
            subtitles = (prefetched?.bundle?.subtitles.orEmpty() + next.subtitles).distinctBy { it.url },
        )
        prefetched?.onSubtitles = {
            if (sourceDiscoveryGeneration == generation && route == TvRoute.PLAYER) {
                switchingBundle = switchingBundle?.let(::withPrefetchedSubtitles)
                sourceDiscoverySnapshot = sourceDiscoverySnapshot?.let {
                    it.copy(bundle = withPrefetchedSubtitles(it.bundle),
                        subtitlesResolved = prefetched?.subtitlesResolved == true)
                }
                prefetched?.bundle?.let(::publishCommittedSubtitles)
            }
        }
        sourceDiscoveryJob = sourceDiscoveryScope.launch {
            try {
                if (prefetched != null) {
                    // Await the existing filtered scan, never start a duplicate.
                    prefetched.sourcesReady.await()
                    if (sourceDiscoveryGeneration != generation || route != TvRoute.PLAYER) return@launch
                    val standby = prefetched.bundle
                    val matched = prefetched.matchedSource
                    if (!prefetched.failed && standby != null && matched != null) {
                        sourceDiscoveryControl = prefetched.control
                        sourceDiscoverySnapshot = TvSourceDiscoverySnapshot(
                            bundle = standby, rawCount = standby.sources.size, notice = null,
                            searching = false, progress = "Next episode ready", firstResultMs = null,
                            providerOrder = listOf(matched.providerName), fromCache = false,
                            subtitlesResolved = prefetched.subtitlesResolved, sourcesStopped = true, pluginsStopped = true,
                            completedSourceProviders = listOf(matched.providerName),
                            plannedSourceProviders = listOf(matched.providerName),
                        )
                        accept(standby, completed = true)
                        prefetched.job?.join()
                        prefetched.bundle?.let(::publishCommittedSubtitles)
                        return@launch
                    }
                    RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_PREFETCH_FALLBACK",
                        "episode=S${target.season}E${target.episode} matchingServer=false")
                }
                sourceDiscoveryControl = discoveryControl
                val result = runtime.discover(
                    item = media,
                    episode = target,
                    forceRefresh = force || targetKey in failedSourceKeys,
                    discoveryControl = discoveryControl,
                    // The prefetch subtitle worker remains the owner during fallback.
                    discoverSubtitles = prefetched == null || prefetched.failed,
                    onUpdate = { snapshot ->
                        if (sourceDiscoveryGeneration == generation && route == TvRoute.PLAYER) {
                            val merged = snapshot.copy(bundle = withPrefetchedSubtitles(snapshot.bundle))
                            sourceDiscoverySnapshot = merged
                            accept(merged.bundle, completed = !merged.searching)
                        }
                    },
                )
                accept(withPrefetchedSubtitles(result), completed = true)
                prefetched?.job?.join()
                RuntimeDiagnostics.recordPlayerEvent("TV", "NEXT_DISCOVERY_FINISHED",
                    "episode=S${target.season}E${target.episode} sources=${result.sources.size} subtitleTracks=${result.subtitles.size}")
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
        if (force && route == TvRoute.PLAYER) cancelEpisodePrefetch()
        val key = sourceSessionKey(media, episode)
        if (route == TvRoute.SOURCE) {
            sourceExitSnapshot = null
            sourceExitError = null
            sourceExitKey = null
            sourceExitRunning = false
        }
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
        val retainPlayback = route == TvRoute.PLAYER
        val retainedBundle = sourceBundle?.takeIf { retainPlayback && previousKey == key }
        fun visibleBundle(next: TvSourceBundle): TvSourceBundle = if (retainedBundle != null)
            next.copy(
                sources = (retainedBundle.sources + next.sources).distinctBy { it.url },
                subtitles = (retainedBundle.subtitles + next.subtitles).distinctBy { it.url },
            ) else next
        sourceDiscoveryJob?.cancel()
        val discoveryControl = SourceDiscoveryControl()
        sourceDiscoveryControl = discoveryControl
        sourceDiscoveryKey = key
        sourceDiscoverySnapshot = previousSnapshot
            ?.takeIf { effectiveForce && previousKey == key }
            ?.copy(
                searching = true,
                progress = "Refreshing sources…",
                pluginsStopped = false,
                sourcesStopped = false,
                loadingProviders = emptyList(),
            )
        sourceDiscoveryError = null
        if (!retainPlayback) {
            sourceBundle = null
            selectedSource = null
        }

        sourceDiscoveryJob = sourceDiscoveryScope.launch {
            try {
                val finalBundle = runtime.discover(
                    item = media,
                    episode = episode,
                    forceRefresh = effectiveForce,
                    discoveryControl = discoveryControl,
                    onUpdate = { snapshot ->
                        if (
                            sourceDiscoveryGeneration == generation &&
                            sourceDiscoveryKey == key
                        ) {
                            val displayedBundle = visibleBundle(snapshot.bundle)
                            sourceDiscoverySnapshot = snapshot.copy(bundle = displayedBundle)
                            sourceBundle = displayedBundle
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
                    sourceBundle = visibleBundle(finalBundle)
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
        if (route != TvRoute.PLAYER) cancelEpisodePrefetch()
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
        CompositionLocalProvider(LocalTvModalFocusHost provides modalFocusHost) {
        Box(
            modifier = Modifier.fillMaxSize().background(TvDesign.Black),
        ) {
            AnimatedContent(
                modifier = Modifier.focusRequester(backgroundFocus)
                    .focusProperties {
                        onEnter = { if (modalDepth > 0) cancelFocusChange() }
                    }
                    .focusGroup(),
                targetState = route,
                transitionSpec = {
                    if (initialState == TvRoute.PLAYER || targetState == TvRoute.PLAYER) {
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
                        val sessionKey = sourceSessionKey(media, selectedEpisode)
                        val leavingSource = route != TvRoute.SOURCE
                        val displayedSnapshot = if (leavingSource) {
                            sourceExitSnapshot.takeIf { sourceExitKey == sessionKey }
                        } else {
                            sourceDiscoverySnapshot.takeIf { sourceDiscoveryKey == sessionKey }
                        }
                        val displayedError = if (leavingSource) {
                            sourceExitError.takeIf { sourceExitKey == sessionKey }
                        } else {
                            sourceDiscoveryError.takeIf { sourceDiscoveryKey == sessionKey }
                        }
                        val displayedRunning = if (leavingSource && sourceExitKey == sessionKey) {
                            sourceExitRunning
                        } else {
                            sourceDiscoveryJob?.isActive == true && sourceDiscoveryKey == sessionKey
                        }

                        DisposableEffect(sessionKey) {
                            onDispose {
                                if (sourceExitKey == sessionKey) {
                                    sourceExitSnapshot = null
                                    sourceExitError = null
                                    sourceExitKey = null
                                    sourceExitRunning = false
                                }
                            }
                        }

                        TvSourceScreen(
                            runtime = runtime,
                            media = media,
                            episode = selectedEpisode,
                            discovery = displayedSnapshot,
                            discoveryRunning = displayedRunning,
                            discoveryError = displayedError,
                            onBack = {
                                // AnimatedContent keeps the outgoing Sources composable alive briefly.
                                // Hold its last visible state while cancelling the real discovery state,
                                // otherwise the outgoing frame flashes "No playable sources".
                                sourceExitSnapshot = sourceDiscoverySnapshot.takeIf {
                                    sourceDiscoveryKey == sessionKey
                                }
                                sourceExitError = sourceDiscoveryError.takeIf {
                                    sourceDiscoveryKey == sessionKey
                                }
                                sourceExitKey = sessionKey
                                sourceExitRunning = sourceDiscoveryJob?.isActive == true
                                stopSourceDiscovery(markStopped = false)
                                route = sourceReturnRoute
                            },
                            onRefresh = {
                                startSourceDiscovery(media, selectedEpisode, force = true)
                            },
                            onStop = {
                                stopSourceDiscovery(markStopped = true)
                            },
                            onPlay = { bundle, source ->
                                sourceBundle = bundle
                                selectedSource = source
                                sourceDiscoveryControl?.stopPlugins()
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
                                sourcesSearching = sourceDiscoverySnapshot?.searching == true,
                                pluginsStopped = sourceDiscoverySnapshot?.pluginsStopped == true,
                                onRefreshSources = { startSourceDiscovery(media, selectedEpisode, force = true) },
                                sourcesStopped = sourceDiscoverySnapshot?.sourcesStopped == true,
                                onStopSources = { sourceDiscoveryControl?.stopSources() },
                                onRefreshSubtitles = {
                                    refreshPlayerSubtitles(
                                        media = media,
                                        episode = selectedEpisode,
                                        expectedVideoId = bundle.videoId,
                                        expectedSession = playbackSession,
                                    )
                                },
                                source = source,
                                initialPositionMs = initialPositionMs,
                                playerSessionId = playbackSession,
                                onBack = {
                                    cancelEpisodeSwitch()
                                    route = playerReturnRoute
                                },
                                onLibraryChanged = { refreshToken++ },
                                onPlayNextEpisode = { target, current -> startEpisodeSwitch(target, preferredSource = current) },
                                onPrefetchNextEpisode = { target, current -> startEpisodePrefetch(target, current) },
                                onValidateNextEpisodePrefetch = { target, current, checkpointSeconds ->
                                    validateEpisodePrefetch(target, current, checkpointSeconds)
                                },
                                onActiveSourceChanged = { current ->
                                    episodePrefetch?.takeIf {
                                        !it.claimed && !com.vueo.shared.core.player.NextEpisodeSourcePolicy.sameServer(it.preferredSource, current)
                                    }?.let { cancelEpisodePrefetch() }
                                },
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
                                onRetry = { startEpisodeSwitch(target, force = true, preferredSource = switchingPreferredSource) },
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
}
