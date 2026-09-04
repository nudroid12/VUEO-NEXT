package com.vueo.tv.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.player.PlayerSkipRepository
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.SubtitleSize
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val PlayerBlack = Color(0xFF030403)
private val PlayerPanel = Color(0xF20A0D0B)
private val PlayerGreen = Color(0xFF84E100)
private val PlayerFocus = Color.White
private val PlayerMuted = Color(0xFFAAB2AD)
private val PlayerDanger = Color(0xFFFFB4AB)

private enum class PlayerSidePanel {
    SOURCES,
    AUDIO,
    SUBTITLES,
    EPISODES,
}

@Composable
fun TvPlayerScreen(
    request: TvPlaybackRequest,
    initialSource: SourceCandidate,
    externalSubtitles: List<SubtitleCandidate>,
    sourceEngine: TvSourceEngine,
    playbackStore: TvPlaybackStore,
    onPlayRequest: (TvPlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settingsStore =
        remember(context) {
            SettingsStore(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }
    val httpFactory = remember(context, request.cacheKey) {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VUEO-TV/0.7")
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember(context, request.cacheKey, httpFactory) {
        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var sources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var allSources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var currentSource by remember(request.cacheKey) { mutableStateOf<SourceCandidate?>(null) }
    var sourceLoading by remember(request.cacheKey) { mutableStateOf(true) }
    var sourceNotice by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var sourceProgress by remember(request.cacheKey) { mutableStateOf("Finding sources…") }
    var playerError by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var isPlaying by remember(request.cacheKey) { mutableStateOf(false) }
    var isBuffering by remember(request.cacheKey) { mutableStateOf(false) }
    var positionMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var durationMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var controlsVisible by remember(request.cacheKey) { mutableStateOf(true) }
    var sidePanel by remember(request.cacheKey) { mutableStateOf<PlayerSidePanel?>(null) }
    var panelReturnFocus by remember(request.cacheKey) { mutableStateOf<PlayerSidePanel?>(null) }
    var interactionToken by remember(request.cacheKey) { mutableIntStateOf(0) }
    var hasStartedPlayback by remember(request.cacheKey) { mutableStateOf(false) }
    var waitingForRecovery by remember(request.cacheKey) { mutableStateOf(false) }
    var audioLabel by remember(request.cacheKey) { mutableStateOf("Audio") }
    var subtitleLabel by remember(request.cacheKey) { mutableStateOf("Subtitles") }
    var skipSegments by remember(request.cacheKey) { mutableStateOf<List<PlayerSkipSegment>>(emptyList()) }
    var activeSkipSegment by remember(request.cacheKey) { mutableStateOf<PlayerSkipSegment?>(null) }
    var trackPreferencesApplied by remember(request.cacheKey) { mutableStateOf(false) }
    var quickSeekDeltaMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var quickSeekToken by remember(request.cacheKey) { mutableIntStateOf(0) }
    var pendingAutoNext by remember(request.cacheKey) { mutableStateOf<TvPlaybackRequest?>(null) }
    var autoNextSeconds by remember(request.cacheKey) { mutableIntStateOf(AUTO_NEXT_COUNTDOWN_SECONDS) }
    val failedSourceUrls = remember(request.cacheKey) { mutableSetOf<String>() }

    val seekRequester = remember { FocusRequester() }
    val restartRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val firstPanelRequester = remember { FocusRequester() }
    val problemRequester = remember { FocusRequester() }
    val autoNextPlayRequester = remember { FocusRequester() }
    val autoNextCancelRequester = remember { FocusRequester() }

    fun touchControls() {
        controlsVisible = true
        interactionToken += 1
    }

    fun quickSeek(deltaMs: Long, showControls: Boolean = false) {
        val current = player.currentPosition.coerceAtLeast(0L)
        val target =
            if (durationMs > 0L) {
                (current + deltaMs).coerceIn(0L, durationMs)
            } else {
                (current + deltaMs).coerceAtLeast(0L)
            }
        player.seekTo(target)
        quickSeekDeltaMs = deltaMs
        quickSeekToken += 1
        if (showControls) touchControls()
    }

    fun playSource(
        source: SourceCandidate,
        resumeMs: Long = positionMs,
    ) {
        val sourceUrl = source.url ?: return
        httpFactory.setDefaultRequestProperties(source.headers)
        currentSource = source
        playerError = null
        waitingForRecovery = false
        pendingAutoNext = null
        trackPreferencesApplied = false
        player.setMediaItem(
            buildPlayerMediaItem(
                sourceUrl = sourceUrl,
                subtitles = externalSubtitles,
            ),
        )
        player.prepare()
        if (resumeMs > 0L) player.seekTo(resumeMs)
        player.playWhenReady = true
        hasStartedPlayback = true
        touchControls()
    }

    fun nextRecoveryCandidate(): SourceCandidate? =
        SourceRanker.automaticRecoveryCandidates(
            rankedSources = sources,
            attemptedUrls = failedSourceUrls,
            originalLanguage = request.originalLanguage,
        ).firstOrNull()

    fun recoverOrWait(message: String?) {
        currentSource?.url?.let { failedSourceUrls += it }

        if (!settingsStore.autoSourceRecoveryEnabled()) {
            waitingForRecovery = false
            playerError = message ?: "Playback failed."
            touchControls()
            return
        }

        val next = nextRecoveryCandidate()
        when {
            next != null -> playSource(next, positionMs)
            sourceLoading -> {
                waitingForRecovery = true
                playerError = null
                sourceProgress = "Trying another source…"
                touchControls()
            }
            else -> {
                waitingForRecovery = false
                playerError = message ?: "Playback failed and no alternate source is available."
                touchControls()
            }
        }
    }

    fun saveProgress() {
        playbackStore.save(
            request = request,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                if (!value && player.currentPosition > 0L) {
                    saveProgress()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    val completedDuration =
                        player.duration
                            .takeIf { it > 0L }
                            ?: durationMs

                    playbackStore.complete(
                        request = request,
                        durationMs = completedDuration,
                    )
                    controlsVisible = true
                    interactionToken += 1

                    val next = request.nextRequest()
                    if (settingsStore.autoPlayNextEpisodeEnabled() && next != null) {
                        pendingAutoNext = next
                        autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (!trackPreferencesApplied) {
                    trackPreferencesApplied = true
                    applyStoredTrackPreferences(
                        player = player,
                        tracks = tracks,
                        settingsStore = settingsStore,
                        contentId = request.cacheKey,
                        externalSubtitleIds = externalSubtitles.map { it.id }.toSet(),
                    )
                }
                audioLabel = selectedTrackLabel(tracks, C.TRACK_TYPE_AUDIO, "Audio")
                subtitleLabel = selectedTrackLabel(tracks, C.TRACK_TYPE_TEXT, "Subtitles")
            }

            override fun onPlayerError(error: PlaybackException) {
                recoverOrWait(error.message)
            }
        }

        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler {
        when {
            pendingAutoNext != null -> {
                pendingAutoNext = null
                autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
                touchControls()
            }
            sidePanel != null -> {
                sidePanel = null
                touchControls()
            }
            playerError != null -> {
                saveProgress()
                onBack()
            }
            controlsVisible -> {
                controlsVisible = false
            }
            else -> {
                saveProgress()
                onBack()
            }
        }
    }

    LaunchedEffect(request.cacheKey) {
        player.stop()
        sourceLoading = true
        sourceNotice = null
        sourceProgress = "Finding sources…"
        sources = emptyList()
        allSources = emptyList()
        currentSource = null
        failedSourceUrls.clear()
        hasStartedPlayback = false
        waitingForRecovery = false
        playerError = null
        positionMs = 0L
        durationMs = 0L
        sidePanel = null
        panelReturnFocus = null
        controlsVisible = true
        trackPreferencesApplied = false
        activeSkipSegment = null
        quickSeekDeltaMs = 0L
        pendingAutoNext = null
        autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS

        if (initialSource.isDirectPlayable) {
            sources = listOf(initialSource)
            allSources = listOf(initialSource)
            playSource(initialSource, playbackStore.resumePositionMs(request))
        }

        val discovery = sourceEngine.discoverProgressive(request) { progress ->
            withContext(Dispatchers.Main.immediate) {
                sources = mergePlayableSources(initialSource, progress.sources)
                allSources = mergeAllSources(initialSource, progress.allSources)
                sourceProgress =
                    "Sources ${progress.completedResolvers}/${progress.totalResolvers} • ${progress.sources.size} playable"

                val candidate = when {
                    !hasStartedPlayback -> progress.sources.firstOrNull()
                    waitingForRecovery -> nextRecoveryCandidate()
                    else -> null
                }

                if (candidate != null) {
                    val resume = if (!hasStartedPlayback) playbackStore.resumePositionMs(request) else positionMs
                    playSource(candidate, resume)
                }
            }
        }

        sources = mergePlayableSources(initialSource, discovery.sources)
        allSources = mergeAllSources(initialSource, discovery.allSources)
        sourceNotice = discovery.notice
        sourceLoading = false
        sourceProgress =
            if (discovery.sources.isNotEmpty()) {
                "${discovery.sources.size} sources • ${discovery.successfulResolvers}/${discovery.attemptedResolvers} engines"
            } else {
                discovery.notice ?: "No playable source"
            }

        if (!hasStartedPlayback && sources.isNotEmpty()) {
            playSource(sources.first(), playbackStore.resumePositionMs(request))
        } else if (waitingForRecovery) {
            val next = nextRecoveryCandidate()
            if (next != null) {
                playSource(next, positionMs)
            } else {
                waitingForRecovery = false
                playerError = "Playback failed and no alternate source is available."
            }
        }
    }

    LaunchedEffect(request.cacheKey) {
        skipSegments = emptyList()
        activeSkipSegment = null
        if (!settingsStore.skipSegmentsEnabled()) return@LaunchedEffect

        val season = request.season ?: return@LaunchedEffect
        val episode = request.episode ?: return@LaunchedEffect
        val imdbId = request.imdbIdForSkip() ?: return@LaunchedEffect

        skipSegments =
            withContext(Dispatchers.IO) {
                PlayerSkipRepository.segments(
                    imdbId = imdbId,
                    season = season,
                    episode = episode,
                )
            }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            activeSkipSegment =
                skipSegments.firstOrNull { segment ->
                    positionMs >= segment.startMs && positionMs < segment.endMs
                }
            if (positionMs > 0L) saveProgress()
            delay(1_000)
        }
    }

    LaunchedEffect(quickSeekToken) {
        if (quickSeekToken <= 0) return@LaunchedEffect
        val token = quickSeekToken
        delay(900)
        if (quickSeekToken == token) quickSeekDeltaMs = 0L
    }

    LaunchedEffect(pendingAutoNext) {
        val next = pendingAutoNext ?: return@LaunchedEffect
        autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
        for (remaining in AUTO_NEXT_COUNTDOWN_SECONDS downTo 1) {
            if (pendingAutoNext != next) return@LaunchedEffect
            autoNextSeconds = remaining
            delay(1_000)
        }
        if (pendingAutoNext == next) {
            pendingAutoNext = null
            saveProgress()
            onPlayRequest(next)
        }
    }

    LaunchedEffect(isBuffering, currentSource?.id, request.cacheKey) {
        if (!isBuffering || currentSource == null || playerError != null) return@LaunchedEffect
        delay(BUFFER_RECOVERY_TIMEOUT_MS)
        if (isBuffering && currentSource != null && playerError == null) {
            recoverOrWait("Playback is taking too long to continue.")
        }
    }

    LaunchedEffect(controlsVisible, sidePanel, interactionToken, isPlaying, playerError) {
        if (controlsVisible && sidePanel == null && isPlaying && playerError == null) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, sidePanel, playerError, pendingAutoNext) {
        delay(90)
        when {
            pendingAutoNext != null -> runCatching { autoNextPlayRequester.requestFocus() }
            sidePanel != null -> runCatching { firstPanelRequester.requestFocus() }
            playerError != null -> {
                panelReturnFocus = null
                runCatching { problemRequester.requestFocus() }
            }
            controlsVisible -> {
                val target =
                    when (panelReturnFocus) {
                        PlayerSidePanel.AUDIO -> audioRequester
                        PlayerSidePanel.SUBTITLES -> subtitleRequester
                        PlayerSidePanel.SOURCES -> sourcesRequester
                        PlayerSidePanel.EPISODES -> episodesRequester
                        null -> playRequester
                    }
                runCatching { target.requestFocus() }
                panelReturnFocus = null
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PlayerBlack)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    if (controlsVisible && sidePanel == null && playerError == null && pendingAutoNext == null) {
                        interactionToken += 1
                    }

                    when (event.key) {
                        Key.MediaPlayPause -> {
                            if (player.isPlaying) player.pause() else player.play()
                            touchControls()
                            true
                        }

                        Key.MediaRewind -> {
                            quickSeek(-SEEK_STEP_MS, showControls = true)
                            true
                        }

                        Key.MediaFastForward -> {
                            quickSeek(SEEK_STEP_MS, showControls = true)
                            true
                        }

                        Key.DirectionLeft -> {
                            if (!controlsVisible && sidePanel == null && pendingAutoNext == null) {
                                quickSeek(-SEEK_STEP_MS)
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (!controlsVisible && sidePanel == null && pendingAutoNext == null) {
                                quickSeek(SEEK_STEP_MS)
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            if (!controlsVisible && sidePanel == null && pendingAutoNext == null) {
                                touchControls()
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    keepScreenOn = true
                    this.player = player
                    subtitleView?.setFractionalTextSize(
                        subtitleFraction(settingsStore.subtitleSize())
                    )
                }
            },
            update = {
                it.player = player
                it.subtitleView?.setFractionalTextSize(
                    subtitleFraction(settingsStore.subtitleSize())
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isBuffering && hasStartedPlayback && playerError == null && pendingAutoNext == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        if (quickSeekDeltaMs != 0L) {
            QuickSeekOverlay(
                deltaMs = quickSeekDeltaMs,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }

        if (!hasStartedPlayback && sourceLoading) {
            LoadingPlayerState(
                title = request.displayTitle,
                message = sourceProgress,
            )
        }

        if (!sourceLoading && sources.isEmpty()) {
            EmptyPlayerState(
                title = request.displayTitle,
                message = sourceNotice ?: "No direct playable source was found.",
                requester = problemRequester,
                onBack = onBack,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && sidePanel == null && sources.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                request = request,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                waitingForRecovery = waitingForRecovery,
                positionMs = positionMs,
                durationMs = durationMs,
                seekRequester = seekRequester,
                restartRequester = restartRequester,
                playRequester = playRequester,
                subtitleRequester = subtitleRequester,
                audioRequester = audioRequester,
                sourcesRequester = sourcesRequester,
                episodesRequester = episodesRequester,
                nextRequester = nextRequester,
                onSeekBackward = {
                    quickSeek(-SEEK_STEP_MS, showControls = true)
                },
                onSeekForward = {
                    quickSeek(SEEK_STEP_MS, showControls = true)
                },
                onRestart = {
                    pendingAutoNext = null
                    player.seekTo(0L)
                    player.play()
                    touchControls()
                },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    touchControls()
                },
                onAudio = {
                    panelReturnFocus = PlayerSidePanel.AUDIO
                    sidePanel = PlayerSidePanel.AUDIO
                    interactionToken += 1
                },
                onSubtitles = {
                    panelReturnFocus = PlayerSidePanel.SUBTITLES
                    sidePanel = PlayerSidePanel.SUBTITLES
                    interactionToken += 1
                },
                onSources = {
                    panelReturnFocus = PlayerSidePanel.SOURCES
                    sidePanel = PlayerSidePanel.SOURCES
                    interactionToken += 1
                },
                onEpisodes = {
                    panelReturnFocus = PlayerSidePanel.EPISODES
                    sidePanel = PlayerSidePanel.EPISODES
                    interactionToken += 1
                },
                onNext = request.nextRequest()?.let { next ->
                    {
                        saveProgress()
                        onPlayRequest(next)
                    }
                },
            )
        }

        activeSkipSegment?.let { segment ->
            SkipSegmentButton(
                segment = segment,
                controlsVisible = controlsVisible,
                onSkip = {
                    player.seekTo(segment.endMs)
                    activeSkipSegment = null
                    touchControls()
                },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        when (sidePanel) {
            PlayerSidePanel.SOURCES ->
                SourcePickerPanel(
                    sources = allSources,
                    selected = currentSource,
                    firstRequester = firstPanelRequester,
                    originalLanguage = request.originalLanguage,
                    onSelect = { source ->
                        source.url?.let { failedSourceUrls.remove(it) }
                        playSource(source, positionMs)
                        sidePanel = null
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            PlayerSidePanel.AUDIO ->
                TrackPickerPanel(
                    title = "Audio",
                    options = trackOptions(player.currentTracks, C.TRACK_TYPE_AUDIO, allowOff = false),
                    firstRequester = firstPanelRequester,
                    onSelect = { option ->
                        option.choice?.let { choice ->
                            applyTrackChoice(player, C.TRACK_TYPE_AUDIO, choice)
                            val selectionId = trackSelectionId(choice)
                            settingsStore.setAudioSelection(request.cacheKey, selectionId)
                            settingsStore.setLastAudioSelection(selectionId)
                        }
                        audioLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_AUDIO, "Audio")
                        sidePanel = null
                        touchControls()
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            PlayerSidePanel.SUBTITLES ->
                TrackPickerPanel(
                    title = "Subtitles",
                    options = trackOptions(player.currentTracks, C.TRACK_TYPE_TEXT, allowOff = true),
                    firstRequester = firstPanelRequester,
                    onSelect = { option ->
                        if (option.choice == null) {
                            disableTrackType(player, C.TRACK_TYPE_TEXT)
                            settingsStore.setSubtitleSelection(request.cacheKey, TRACK_SELECTION_OFF)
                            settingsStore.setLastSubtitleSelection(TRACK_SELECTION_OFF)
                        } else {
                            applyTrackChoice(player, C.TRACK_TYPE_TEXT, option.choice)
                            val selectionId = trackSelectionId(option.choice)
                            settingsStore.setSubtitleSelection(request.cacheKey, selectionId)
                            settingsStore.setLastSubtitleSelection(selectionId)
                        }
                        subtitleLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_TEXT, "Subtitles")
                        sidePanel = null
                        touchControls()
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            PlayerSidePanel.EPISODES ->
                EpisodePickerPanel(
                    episodes = request.episodeQueue,
                    currentVideoId = request.videoId,
                    firstRequester = firstPanelRequester,
                    onSelect = { episode ->
                        saveProgress()
                        sidePanel = null
                        onPlayRequest(
                            request.copy(
                                videoId = episode.videoId,
                                episodeTitle = episode.title,
                                season = episode.season,
                                episode = episode.episode,
                            ),
                        )
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            null -> Unit
        }

        pendingAutoNext?.let { next ->
            AutoNextOverlay(
                next = next,
                seconds = autoNextSeconds,
                playRequester = autoNextPlayRequester,
                cancelRequester = autoNextCancelRequester,
                onPlayNow = {
                    pendingAutoNext = null
                    saveProgress()
                    onPlayRequest(next)
                },
                onCancel = {
                    pendingAutoNext = null
                    autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
                    touchControls()
                },
            )
        }

        if (playerError != null && sidePanel == null && sources.isNotEmpty()) {
            PlaybackProblemPanel(
                message = playerError ?: "Playback problem",
                requester = problemRequester,
                onRetry = {
                    val retry = currentSource ?: sources.firstOrNull()
                    if (retry != null) {
                        retry.url?.let { failedSourceUrls.remove(it) }
                        playSource(retry, positionMs)
                    }
                },
                onSources = {
                    panelReturnFocus = PlayerSidePanel.SOURCES
                    sidePanel = PlayerSidePanel.SOURCES
                    interactionToken += 1
                },
                onExit = {
                    saveProgress()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun QuickSeekOverlay(
    deltaMs: Long,
    positionMs: Long,
    durationMs: Long,
) {
    val forward = deltaMs > 0L
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 92.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (forward) "+10s" else "−10s",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = PlayerMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun AutoNextOverlay(
    next: TvPlaybackRequest,
    seconds: Int,
    playRequester: FocusRequester,
    cancelRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(end = 48.dp)
                    .width(430.dp)
                    .background(PlayerPanel, RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .padding(24.dp),
        ) {
            Text(
                text = "Up next",
                color = PlayerMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = next.displayTitle,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Playing in ${seconds.coerceAtLeast(1)}s",
                color = PlayerMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerButton(
                    text = "Play Now",
                    requester = playRequester,
                    onClick = onPlayNow,
                    primary = true,
                    onRight = { cancelRequester.requestFocus() },
                )
                PlayerButton(
                    text = "Cancel",
                    requester = cancelRequester,
                    onClick = onCancel,
                    onLeft = { playRequester.requestFocus() },
                )
            }
        }
    }
}

@Composable
private fun LoadingPlayerState(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PlayerGreen)
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = PlayerMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SkipSegmentButton(
    segment: PlayerSkipSegment,
    controlsVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(segment.key) { mutableStateOf(false) }
    val label =
        when (segment.kind.name) {
            "INTRO" -> "Skip Intro"
            "RECAP" -> "Skip Recap"
            "ENDING" -> "Skip Ending"
            else -> "Skip"
        }

    Box(
        modifier =
            modifier
                .padding(
                    start = 56.dp,
                    bottom = if (controlsVisible) 186.dp else 48.dp,
                )
                .scale(if (focused) 1.06f else 1f)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) PlayerFocus else Color.White.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(999.dp),
                )
                .background(if (focused) PlayerFocus else PlayerPanel, RoundedCornerShape(999.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onSkip)
                .focusable()
                .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlayerControls(
    request: TvPlaybackRequest,
    isPlaying: Boolean,
    isBuffering: Boolean,
    waitingForRecovery: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekRequester: FocusRequester,
    restartRequester: FocusRequester,
    playRequester: FocusRequester,
    subtitleRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    nextRequester: FocusRequester,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onRestart: () -> Unit,
    onPlayPause: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onEpisodes: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val episodeLine =
        buildList {
            if (request.season != null && request.episode != null) {
                add("S${request.season.toString().padStart(2, '0')}E${request.episode.toString().padStart(2, '0')}")
            }
            request.episodeTitle?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("  •  ")
    val hasEpisodes = request.episodeQueue.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(188.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.84f),
                                Color.Black.copy(alpha = 0.46f),
                                Color.Transparent,
                            ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 56.dp, top = 42.dp)
                    .width(760.dp),
        ) {
            Text(
                text = request.media.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeLine.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = episodeLine,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (waitingForRecovery) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Switching source…",
                    color = PlayerGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 56.dp, top = 38.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                text = "↻",
                requester = restartRequester,
                onClick = onRestart,
                onLeft = { restartRequester.requestFocus() },
                onRight = {
                    if (onNext != null) nextRequester.requestFocus() else restartRequester.requestFocus()
                },
                onDown = { seekRequester.requestFocus() },
            )
            if (onNext != null) {
                PlayerIconButton(
                    text = "⏭",
                    requester = nextRequester,
                    onClick = onNext,
                    onLeft = { restartRequester.requestFocus() },
                    onRight = { nextRequester.requestFocus() },
                    onDown = { seekRequester.requestFocus() },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(214.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.92f),
                                Color.Black.copy(alpha = 0.98f),
                            ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 56.dp, bottom = 30.dp),
        ) {
            PlayerSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                requester = seekRequester,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                onPlayPause = onPlayPause,
                onUp = { restartRequester.requestFocus() },
                onDown = { playRequester.requestFocus() },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(positionMs),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatRemainingTime(positionMs, durationMs),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerIconButton(
                    text =
                        when {
                            isBuffering -> "…"
                            isPlaying -> "Ⅱ"
                            else -> "▶"
                        },
                    requester = playRequester,
                    onClick = onPlayPause,
                    primary = true,
                    onLeft = { playRequester.requestFocus() },
                    onRight = { subtitleRequester.requestFocus() },
                    onUp = { seekRequester.requestFocus() },
                )
                PlayerTextButton(
                    text = "Subtitles",
                    requester = subtitleRequester,
                    onClick = onSubtitles,
                    onLeft = { playRequester.requestFocus() },
                    onRight = { audioRequester.requestFocus() },
                    onUp = { seekRequester.requestFocus() },
                )
                PlayerTextButton(
                    text = "Audio",
                    requester = audioRequester,
                    onClick = onAudio,
                    onLeft = { subtitleRequester.requestFocus() },
                    onRight = { sourcesRequester.requestFocus() },
                    onUp = { seekRequester.requestFocus() },
                )
                PlayerTextButton(
                    text = "Sources",
                    requester = sourcesRequester,
                    onClick = onSources,
                    onLeft = { audioRequester.requestFocus() },
                    onRight = {
                        if (hasEpisodes) episodesRequester.requestFocus() else sourcesRequester.requestFocus()
                    },
                    onUp = { seekRequester.requestFocus() },
                )
                if (hasEpisodes) {
                    PlayerTextButton(
                        text = "Episodes",
                        requester = episodesRequester,
                        onClick = onEpisodes,
                        onLeft = { sourcesRequester.requestFocus() },
                        onRight = { episodesRequester.requestFocus() },
                        onUp = { seekRequester.requestFocus() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    requester: FocusRequester,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayPause: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(
                    onClick = onPlayPause,
                    onLeft = onSeekBackward,
                    onRight = onSeekForward,
                    onUp = onUp,
                    onDown = onDown,
                )
                .scale(if (focused) 1.004f else 1f)
                .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (focused) 8.dp else 5.dp)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.30f),
                        RoundedCornerShape(999.dp),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 9.dp else 6.dp)
                    .background(PlayerGreen, RoundedCornerShape(999.dp)),
        )
        if (durationMs > 0L) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(32.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (focused) 18.dp else 14.dp)
                            .background(Color.White, CircleShape),
                )
            }
        }
    }
}

private fun Modifier.playerRemoteKeys(
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
                Key.DirectionRight -> onRight?.let { it(); true } ?: false
                Key.DirectionUp -> onUp?.let { it(); true } ?: false
                Key.DirectionDown -> onDown?.let { it(); true } ?: false
                Key.DirectionCenter,
                Key.Enter,
                Key.NumPadEnter -> {
                    onClick()
                    true
                }
                else -> false
            }
        }
    }

@Composable
private fun PlayerIconButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    primary: Boolean = false,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val buttonSize = if (primary) 58.dp else 54.dp

    Box(
        modifier =
            Modifier
                .size(buttonSize)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.08f else 1f)
                .background(
                    color =
                        when {
                            focused -> PlayerFocus
                            primary -> Color.White.copy(alpha = 0.16f)
                            else -> Color.Black.copy(alpha = 0.42f)
                        },
                    shape = CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = if (focused) Color.Transparent else Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = if (primary) 23.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerTextButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .height(52.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.045f else 1f)
                .background(
                    color = if (focused) PlayerFocus else Color.Black.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(999.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (focused) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    primary: Boolean = false,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.05f else 1f)
                .background(
                    color =
                        when {
                            focused -> PlayerFocus
                            primary -> Color.White.copy(alpha = 0.90f)
                            else -> Color.White.copy(alpha = 0.11f)
                        },
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (focused) Color.Transparent else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused || primary) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourcePickerPanel(
    sources: List<SourceCandidate>,
    selected: SourceCandidate?,
    firstRequester: FocusRequester,
    originalLanguage: String?,
    onSelect: (SourceCandidate) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val restoreIndex =
        sources.indexOfFirst { source -> source.id == selected?.id && source.isDirectPlayable }
            .takeIf { it >= 0 }
            ?: sources.indexOfFirst { it.isDirectPlayable }.coerceAtLeast(0)

    RightPanel(title = "Sources", subtitle = "VUEO ranked for fast direct playback") {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                var focused by remember(source.id) { mutableStateOf(false) }
                val active = selected?.id == source.id
                val assessment = SourceRanker.assess(source, originalLanguage = originalLanguage)

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == restoreIndex) Modifier.focusRequester(firstRequester) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .background(
                                when {
                                    focused -> Color.White.copy(alpha = 0.16f)
                                    active -> PlayerGreen.copy(alpha = 0.10f)
                                    else -> Color.White.copy(alpha = 0.055f)
                                },
                                RoundedCornerShape(11.dp),
                            )
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = when {
                                    focused -> PlayerFocus
                                    active -> PlayerGreen.copy(alpha = 0.75f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(11.dp),
                            )
                            .then(
                                if (source.isDirectPlayable) {
                                    Modifier.clickable { onSelect(source) }.focusable()
                                } else {
                                    Modifier
                                },
                            )
                            .padding(14.dp),
                ) {
                    val sourceSummary = when {
                        source.isDirectPlayable -> assessment.summary
                        source.isTorrent -> "Torrent • debrid playback comes later"
                        source.url?.startsWith("http://") == true -> "HTTP • not direct-playable"
                        else -> "Not direct-playable"
                    }
                    Text(
                        text = if (index == 0 && source.isDirectPlayable) "Recommended • $sourceSummary" else sourceSummary,
                        color = if (source.isDirectPlayable) Color.White else PlayerMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = source.providerName,
                        color = PlayerGreen.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = source.name,
                        color = PlayerMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodePickerPanel(
    episodes: List<TvEpisodeRef>,
    currentVideoId: String,
    firstRequester: FocusRequester,
    onSelect: (TvEpisodeRef) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val restoreIndex = episodes.indexOfFirst { it.videoId == currentVideoId }.coerceAtLeast(0)

    RightPanel(title = "Episodes", subtitle = "Choose an episode") {
        if (episodes.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("No episode list is available for this title.", color = PlayerMuted, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                PlayerButton("Close", firstRequester, onClose, primary = true)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.videoId }) { index, episode ->
                    var focused by remember(episode.videoId) { mutableStateOf(false) }
                    val active = episode.videoId == currentVideoId
                    val number = if (episode.season != null && episode.episode != null) {
                        "S${episode.season.toString().padStart(2, '0')}E${episode.episode.toString().padStart(2, '0')}"
                    } else {
                        "Episode ${index + 1}"
                    }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (index == restoreIndex) Modifier.focusRequester(firstRequester) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .background(
                                    when {
                                        focused -> Color.White.copy(alpha = 0.16f)
                                        active -> PlayerGreen.copy(alpha = 0.10f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = when {
                                        focused -> PlayerFocus
                                        active -> PlayerGreen.copy(alpha = 0.75f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(11.dp),
                                )
                                .clickable { onSelect(episode) }
                                .focusable()
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = number,
                            color = if (active) PlayerGreen else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = episode.title.ifBlank { number },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class TrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
)

private data class TrackOption(
    val label: String,
    val secondary: String?,
    val selected: Boolean,
    val choice: TrackChoice?,
)

@Composable
private fun TrackPickerPanel(
    title: String,
    options: List<TrackOption>,
    firstRequester: FocusRequester,
    onSelect: (TrackOption) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val restoreIndex = options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0

    RightPanel(
        title = title,
        subtitle = if (options.isEmpty()) "No tracks available" else "Select with your remote",
    ) {
        if (options.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "No $title tracks are available in this source.",
                    color = PlayerMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))
                PlayerButton(
                    text = "Close",
                    requester = firstRequester,
                    onClick = onClose,
                    primary = true,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    var focused by remember(index, option.label) { mutableStateOf(false) }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (index == restoreIndex) Modifier.focusRequester(firstRequester) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .background(
                                    when {
                                        focused -> Color.White.copy(alpha = 0.16f)
                                        option.selected -> PlayerGreen.copy(alpha = 0.10f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = when {
                                        focused -> PlayerFocus
                                        option.selected -> PlayerGreen.copy(alpha = 0.75f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(11.dp),
                                )
                                .clickable { onSelect(option) }
                                .focusable()
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = if (option.selected) "✓  ${option.label}" else option.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        option.secondary?.let { secondary ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = secondary,
                                color = PlayerMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RightPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .background(PlayerPanel)
                    .padding(top = 36.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = subtitle,
                color = PlayerMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PlaybackProblemPanel(
    message: String,
    requester: FocusRequester,
    onRetry: () -> Unit,
    onSources: () -> Unit,
    onExit: () -> Unit,
) {
    val sourcesRequester = remember { FocusRequester() }
    val exitRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(end = 34.dp)
                    .width(390.dp)
                    .background(PlayerPanel, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(22.dp),
        ) {
            Text(
                text = "Playback problem",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = PlayerMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerButton(
                    text = "Retry",
                    requester = requester,
                    onClick = onRetry,
                    primary = true,
                    onRight = { sourcesRequester.requestFocus() },
                )
                PlayerButton(
                    text = "Change Source",
                    requester = sourcesRequester,
                    onClick = onSources,
                    onLeft = { requester.requestFocus() },
                    onRight = { exitRequester.requestFocus() },
                )
                PlayerButton(
                    text = "Exit",
                    requester = exitRequester,
                    onClick = onExit,
                    onLeft = { sourcesRequester.requestFocus() },
                )
            }
        }
    }
}

@Composable
private fun EmptyPlayerState(
    title: String,
    message: String,
    requester: FocusRequester,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(90)
        runCatching { requester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(PlayerBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(720.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = PlayerMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(22.dp))
            PlayerButton("Back to details", requester, onBack, primary = true)
        }
    }
}

private fun trackChoices(
    tracks: Tracks,
    type: Int,
): List<TrackChoice> =
    tracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).map { index -> TrackChoice(group, index) }
        }

private fun trackOptions(
    tracks: Tracks,
    type: Int,
    allowOff: Boolean,
): List<TrackOption> {
    val choices = trackChoices(tracks, type)
    val result = mutableListOf<TrackOption>()

    if (allowOff) {
        val anySelected = choices.any { it.group.isTrackSelected(it.trackIndex) }
        result += TrackOption(
            label = "Off",
            secondary = null,
            selected = !anySelected,
            choice = null,
        )
    }

    choices.forEachIndexed { index, choice ->
        val format = choice.group.getTrackFormat(choice.trackIndex)
        val label =
            format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.takeIf { it.isNotBlank() }
                ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${index + 1}" else "Subtitle ${index + 1}"
        val secondary = buildList {
            format.language?.takeIf { it.isNotBlank() }?.let(::add)
            format.codecs?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct().joinToString(" • ").takeIf { it.isNotBlank() }

        result += TrackOption(
            label = label,
            secondary = secondary,
            selected = choice.group.isTrackSelected(choice.trackIndex),
            choice = choice,
        )
    }

    return result
}

private fun applyTrackChoice(
    player: Player,
    type: Int,
    choice: TrackChoice,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(
                TrackSelectionOverride(
                    choice.group.mediaTrackGroup,
                    choice.trackIndex,
                ),
            )
            .build()
}

private fun disableTrackType(
    player: Player,
    type: Int,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, true)
            .build()
}

private fun applyStoredTrackPreferences(
    player: Player,
    tracks: Tracks,
    settingsStore: SettingsStore,
    contentId: String,
    externalSubtitleIds: Set<String>,
) {
    val audioChoices = trackChoices(tracks, C.TRACK_TYPE_AUDIO)
    val savedAudio = settingsStore.audioSelection(contentId)
    audioChoices
        .firstOrNull { trackSelectionId(it) == savedAudio }
        ?.let { applyTrackChoice(player, C.TRACK_TYPE_AUDIO, it) }

    val subtitleChoices = trackChoices(tracks, C.TRACK_TYPE_TEXT)
    val savedSubtitle = settingsStore.subtitleSelection(contentId)

    when {
        !settingsStore.subtitlesOnByDefault() ->
            disableTrackType(player, C.TRACK_TYPE_TEXT)

        savedSubtitle == TRACK_SELECTION_OFF ->
            disableTrackType(player, C.TRACK_TYPE_TEXT)

        !savedSubtitle.isNullOrBlank() ->
            subtitleChoices
                .firstOrNull { trackSelectionId(it) == savedSubtitle }
                ?.let { applyTrackChoice(player, C.TRACK_TYPE_TEXT, it) }

        settingsStore.autoSelectPreferredSubtitle() -> {
            val ordered =
                if (settingsStore.embeddedSubtitlePriority()) {
                    subtitleChoices.sortedBy { choice ->
                        val id = choice.group.getTrackFormat(choice.trackIndex).id
                        if (id != null && id in externalSubtitleIds) 1 else 0
                    }
                } else {
                    subtitleChoices
                }

            val preferred = settingsStore.preferredSubtitleLanguage().languageCode
            val secondary = settingsStore.secondarySubtitleLanguage().languageCode
            val selected =
                findSubtitleForLanguage(ordered, preferred)
                    ?: findSubtitleForLanguage(ordered, secondary)

            selected?.let { applyTrackChoice(player, C.TRACK_TYPE_TEXT, it) }
        }
    }
}

private fun findSubtitleForLanguage(
    choices: List<TrackChoice>,
    languageCode: String?,
): TrackChoice? {
    if (languageCode.isNullOrBlank()) return null
    return choices.firstOrNull { choice ->
        choice.group
            .getTrackFormat(choice.trackIndex)
            .language
            ?.lowercase()
            ?.let { language ->
                language == languageCode || language.startsWith("$languageCode-")
            } == true
    }
}

private fun subtitleFraction(size: SubtitleSize): Float =
    when (size) {
        SubtitleSize.SMALL -> 0.044f
        SubtitleSize.MEDIUM -> 0.0533f
        SubtitleSize.LARGE -> 0.066f
    }

private fun trackSelectionId(
    choice: TrackChoice,
): String {
    val format = choice.group.getTrackFormat(choice.trackIndex)
    return format.id
        ?.takeIf { it.isNotBlank() }
        ?: listOf(
            format.language.orEmpty(),
            format.label.orEmpty(),
            format.codecs.orEmpty(),
            choice.trackIndex.toString(),
        ).joinToString("|")
}

private fun TvPlaybackRequest.imdbIdForSkip(): String? =
    sequenceOf(media.id, videoId)
        .mapNotNull { value ->
            IMDB_ID_REGEX.find(value)?.value?.lowercase()
        }
        .firstOrNull()

private fun selectedTrackLabel(
    tracks: Tracks,
    type: Int,
    fallback: String,
): String {
    val selected = trackChoices(tracks, type).firstOrNull { choice ->
        choice.group.isTrackSelected(choice.trackIndex)
    } ?: return if (type == C.TRACK_TYPE_TEXT) "Subtitles Off" else fallback

    val format = selected.group.getTrackFormat(selected.trackIndex)
    val raw =
        format.label?.takeIf { it.isNotBlank() }
            ?: format.language?.takeIf { it.isNotBlank() }
            ?: fallback

    return if (type == C.TRACK_TYPE_AUDIO) "Audio • $raw" else "Subs • $raw"
}

private fun mergePlayableSources(
    initialSource: SourceCandidate,
    discovered: List<SourceCandidate>,
): List<SourceCandidate> =
    (listOf(initialSource) + discovered)
        .filter { it.isDirectPlayable }
        .distinctBy { it.url }

private fun mergeAllSources(
    initialSource: SourceCandidate,
    discovered: List<SourceCandidate>,
): List<SourceCandidate> =
    (listOf(initialSource) + discovered)
        .distinctBy { it.id }

private fun buildPlayerMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleCandidate>,
): MediaItem {
    val subtitleConfigurations =
        subtitles
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
            .map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setId(subtitle.id)
                    .setLabel(subtitle.name ?: subtitle.language.uppercase())
                    .setLanguage(subtitle.language)
                    .setMimeType(subtitleMimeType(subtitle.url))
                    .build()
            }

    return MediaItem.Builder()
        .setUri(Uri.parse(sourceUrl))
        .setSubtitleConfigurations(subtitleConfigurations)
        .build()
}

private fun subtitleMimeType(url: String): String =
    when (
        url.substringBefore("?")
            .substringAfterLast(".", "")
            .lowercase()
    ) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

private fun formatRemainingTime(
    positionMs: Long,
    durationMs: Long,
): String {
    if (durationMs <= 0L) return "--:--"
    return "-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}"
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private const val AUTO_NEXT_COUNTDOWN_SECONDS = 5
private const val BUFFER_RECOVERY_TIMEOUT_MS = 18_000L
private const val SEEK_STEP_MS = 10_000L
private const val TRACK_SELECTION_OFF = "__off__"
private val IMDB_ID_REGEX = Regex("tt\\d{5,10}", RegexOption.IGNORE_CASE)
