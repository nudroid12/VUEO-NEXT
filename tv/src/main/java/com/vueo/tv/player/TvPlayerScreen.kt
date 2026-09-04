package com.vueo.tv.player

import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.enrichment.ContentWarningRepository
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.player.PlayerSkipRepository
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.shared.core.storage.SubtitleSize
import java.util.concurrent.TimeUnit
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val PlayerBlack = Color(0xFF030403)
private val PlayerPanel = Color(0xF20A0D0B)
private val PlayerFocus = Color.White
private val PlayerMuted = Color(0xFFAAB2AD)
private val PlayerDanger = Color(0xFFFFB4AB)

private enum class PlayerSidePanel {
    SOURCES,
    AUDIO,
    SUBTITLES,
    EPISODES,
    PLAYBACK,
}

private enum class TvSleepTimerOption(
    val label: String,
    val minutes: Int? = null,
    val endOfEpisode: Boolean = false,
) {
    OFF("Off"),
    MINUTES_15("15 min", minutes = 15),
    MINUTES_30("30 min", minutes = 30),
    MINUTES_45("45 min", minutes = 45),
    MINUTES_60("60 min", minutes = 60),
    END_OF_EPISODE("End of episode", endOfEpisode = true),
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
    val pluginStore =
        remember(context) {
            PluginStore(context.applicationContext)
        }
    val subtitleDelayUs = remember(request.cacheKey) {
        java.util.concurrent.atomic.AtomicLong(
            settingsStore.subtitleDelayMs(request.cacheKey).toLong() * 1_000L,
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
        ExoPlayer.Builder(
            context,
            TvSubtitleOffsetRenderersFactory(
                context = context,
                subtitleDelayUsProvider = { subtitleDelayUs.get() },
            ),
        )
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setPlaybackSpeed(settingsStore.playerPlaybackSpeed())
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
    var controlsLocked by remember(request.cacheKey) { mutableStateOf(false) }
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
    var playbackSpeed by remember(request.cacheKey) { mutableStateOf(settingsStore.playerPlaybackSpeed()) }
    var videoFit by remember(request.cacheKey) { mutableStateOf(settingsStore.playerVideoFit()) }
    var subtitleDelayMs by remember(request.cacheKey) { mutableIntStateOf(settingsStore.subtitleDelayMs(request.cacheKey)) }
    var contentWarningsEnabled by remember(request.cacheKey) { mutableStateOf(settingsStore.contentWarningsEnabled()) }
    var contentWarnings by remember(request.cacheKey) { mutableStateOf<List<ContentWarning>>(emptyList()) }
    var showContentWarnings by remember(request.cacheKey) { mutableStateOf(false) }
    var contentWarningsShown by remember(request.cacheKey) { mutableStateOf(false) }
    var resumePromptVisible by remember(request.cacheKey) { mutableStateOf(false) }
    var pendingResumePositionMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var sleepTimerOption by remember(request.cacheKey) { mutableStateOf(TvSleepTimerOption.OFF) }
    var sleepTimerDeadlineMs by remember(request.cacheKey) { mutableStateOf<Long?>(null) }
    var sleepTimerRemainingSeconds by remember(request.cacheKey) { mutableStateOf<Long?>(null) }
    var sessionNotice by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    val failedSourceUrls = remember(request.cacheKey) { mutableSetOf<String>() }

    val seekRequester = remember { FocusRequester() }
    val rewindRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val forwardRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val lockRequester = remember { FocusRequester() }
    val moreRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val unlockRequester = remember { FocusRequester() }
    val firstPanelRequester = remember { FocusRequester() }
    val problemRequester = remember { FocusRequester() }
    val autoNextPlayRequester = remember { FocusRequester() }
    val autoNextCancelRequester = remember { FocusRequester() }
    val resumeRequester = remember { FocusRequester() }
    val startOverRequester = remember { FocusRequester() }

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
        autoPlay: Boolean = true,
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
        player.playWhenReady = autoPlay
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

                    if (sleepTimerOption == TvSleepTimerOption.END_OF_EPISODE) {
                        sleepTimerOption = TvSleepTimerOption.OFF
                        sleepTimerDeadlineMs = null
                        sleepTimerRemainingSeconds = null
                        sessionNotice = "Sleep timer finished"
                    } else {
                        val next = request.nextRequest()
                        if (settingsStore.autoPlayNextEpisodeEnabled() && next != null) {
                            pendingAutoNext = next
                            autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
                        }
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
            controlsLocked -> {
                controlsLocked = false
                touchControls()
            }
            resumePromptVisible -> {
                resumePromptVisible = false
                saveProgress()
                onBack()
            }
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
        controlsLocked = false
        trackPreferencesApplied = false
        activeSkipSegment = null
        quickSeekDeltaMs = 0L
        pendingAutoNext = null
        autoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS
        playbackSpeed = settingsStore.playerPlaybackSpeed()
        videoFit = settingsStore.playerVideoFit()
        subtitleDelayMs = settingsStore.subtitleDelayMs(request.cacheKey)
        subtitleDelayUs.set(subtitleDelayMs.toLong() * 1_000L)
        contentWarningsEnabled = settingsStore.contentWarningsEnabled()
        contentWarnings = emptyList()
        showContentWarnings = false
        contentWarningsShown = false
        sleepTimerOption = TvSleepTimerOption.OFF
        sleepTimerDeadlineMs = null
        sleepTimerRemainingSeconds = null
        sessionNotice = null
        pendingResumePositionMs =
            if (settingsStore.resumePlaybackEnabled()) playbackStore.resumePositionMs(request) else 0L
        resumePromptVisible = pendingResumePositionMs > RESUME_PROMPT_THRESHOLD_MS
        player.setPlaybackSpeed(playbackSpeed)

        if (initialSource.isDirectPlayable) {
            sources = listOf(initialSource)
            allSources = listOf(initialSource)
            playSource(
                source = initialSource,
                resumeMs = if (resumePromptVisible) 0L else pendingResumePositionMs,
                autoPlay = !resumePromptVisible,
            )
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
                    val resume =
                        if (!hasStartedPlayback) {
                            if (resumePromptVisible) 0L else pendingResumePositionMs
                        } else {
                            positionMs
                        }
                    playSource(candidate, resume, autoPlay = !resumePromptVisible)
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
            playSource(
                source = sources.first(),
                resumeMs = if (resumePromptVisible) 0L else pendingResumePositionMs,
                autoPlay = !resumePromptVisible,
            )
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


    LaunchedEffect(
        request.media.id,
        request.videoId,
        contentWarningsEnabled,
    ) {
        contentWarnings = emptyList()
        showContentWarnings = false
        contentWarningsShown = false

        if (!contentWarningsEnabled) {
            return@LaunchedEffect
        }

        val directImdbId =
            ContentWarningRepository.extractImdbId(request.media.id)
                ?: ContentWarningRepository.extractImdbId(request.videoId)
        val imdbId = directImdbId ?: runCatching {
            TmdbEnhancementClient.prepareForCore(
                item = request.media,
                apiKey = pluginStore.tmdbApiKey(),
            ).id
        }.getOrNull()?.let(ContentWarningRepository::extractImdbId)

        if (imdbId != null) {
            contentWarnings = withContext(Dispatchers.IO) {
                ContentWarningRepository.get(imdbId)
            }
        }
    }

    LaunchedEffect(
        isPlaying,
        contentWarnings,
        contentWarningsEnabled,
    ) {
        if (!isPlaying || !contentWarningsEnabled) {
            showContentWarnings = false
            return@LaunchedEffect
        }

        if (
            contentWarnings.isNotEmpty() &&
            !contentWarningsShown
        ) {
            contentWarningsShown = true
            showContentWarnings = true
        }
    }

    LaunchedEffect(sleepTimerDeadlineMs) {
        val deadline = sleepTimerDeadlineMs ?: return@LaunchedEffect
        while (isActive && sleepTimerDeadlineMs == deadline) {
            val remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            sleepTimerRemainingSeconds = (remainingMs + 999L) / 1_000L
            if (remainingMs <= 0L) {
                sleepTimerDeadlineMs = null
                sleepTimerRemainingSeconds = null
                sleepTimerOption = TvSleepTimerOption.OFF
                player.pause()
                controlsVisible = true
                interactionToken += 1
                sessionNotice = "Sleep timer finished"
                break
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(sessionNotice) {
        val notice = sessionNotice ?: return@LaunchedEffect
        delay(1_800L)
        if (sessionNotice == notice) sessionNotice = null
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

    LaunchedEffect(controlsVisible, controlsLocked, sidePanel, interactionToken, isPlaying, playerError, resumePromptVisible) {
        if (
            controlsVisible &&
            !controlsLocked &&
            sidePanel == null &&
            isPlaying &&
            playerError == null &&
            !resumePromptVisible
        ) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, controlsLocked, sidePanel, playerError, pendingAutoNext, resumePromptVisible) {
        delay(90)
        when {
            controlsLocked -> runCatching { unlockRequester.requestFocus() }
            resumePromptVisible -> runCatching { resumeRequester.requestFocus() }
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
                        PlayerSidePanel.PLAYBACK -> moreRequester
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
                    if (resumePromptVisible) return@onPreviewKeyEvent false

                    if (controlsVisible && !controlsLocked && sidePanel == null && playerError == null && pendingAutoNext == null) {
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
                            if (!controlsVisible && !controlsLocked && sidePanel == null && pendingAutoNext == null) {
                                quickSeek(-SEEK_STEP_MS)
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (!controlsVisible && !controlsLocked && sidePanel == null && pendingAutoNext == null) {
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
                            if (!controlsVisible && !controlsLocked && sidePanel == null && pendingAutoNext == null) {
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
                    resizeMode = videoFit.toMedia3ResizeMode()
                    applyTvSubtitleStyle(this, settingsStore)
                }
            },
            update = {
                it.player = player
                it.resizeMode = videoFit.toMedia3ResizeMode()
                applyTvSubtitleStyle(it, settingsStore)
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
            visible = controlsVisible && !controlsLocked && sidePanel == null && sources.isNotEmpty(),
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
                rewindRequester = rewindRequester,
                playRequester = playRequester,
                forwardRequester = forwardRequester,
                subtitleRequester = subtitleRequester,
                audioRequester = audioRequester,
                sourcesRequester = sourcesRequester,
                episodesRequester = episodesRequester,
                nextRequester = nextRequester,
                lockRequester = lockRequester,
                moreRequester = moreRequester,
                backRequester = backRequester,
                onSeekBackward = {
                    quickSeek(-SEEK_STEP_MS, showControls = true)
                },
                onSeekForward = {
                    quickSeek(SEEK_STEP_MS, showControls = true)
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
                onPlaybackOptions = {
                    panelReturnFocus = PlayerSidePanel.PLAYBACK
                    sidePanel = PlayerSidePanel.PLAYBACK
                    interactionToken += 1
                },
                onNext = request.nextRequest()?.let { next ->
                    {
                        saveProgress()
                        onPlayRequest(next)
                    }
                },
                onLock = {
                    controlsLocked = true
                    controlsVisible = false
                    interactionToken += 1
                },
                onBack = {
                    saveProgress()
                    onBack()
                },
            )
        }

        if (controlsLocked) {
            PlayerUnlockAction(
                requester = unlockRequester,
                onUnlock = {
                    controlsLocked = false
                    touchControls()
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (!controlsLocked) {
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
        }

        if (
            showContentWarnings &&
            contentWarnings.isNotEmpty()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 32.dp,
                        top = 20.dp,
                    ),
            ) {
                ContentWarningsOverlay(
                    warnings = contentWarnings,
                    onAnimationComplete = {
                        showContentWarnings = false
                    },
                )
            }
        }

        sessionNotice?.let { notice ->
            SessionNoticeOverlay(
                message = notice,
                modifier = Modifier.align(Alignment.Center),
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
                SubtitlePickerPanel(
                    options = trackOptions(player.currentTracks, C.TRACK_TYPE_TEXT, allowOff = true),
                    subtitleDelayMs = subtitleDelayMs,
                    firstRequester = firstPanelRequester,
                    onDelayChange = { updated ->
                        subtitleDelayMs = updated.coerceIn(-60_000, 60_000)
                        subtitleDelayUs.set(subtitleDelayMs.toLong() * 1_000L)
                        settingsStore.setSubtitleDelayMs(request.cacheKey, subtitleDelayMs)
                    },
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

            PlayerSidePanel.PLAYBACK ->
                PlaybackOptionsPanel(
                    playbackSpeed = playbackSpeed,
                    videoFit = videoFit,
                    sleepTimerOption = sleepTimerOption,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    contentWarningsEnabled = contentWarningsEnabled,
                    firstRequester = firstPanelRequester,
                    onPlaybackSpeedChange = { speed ->
                        playbackSpeed = speed
                        settingsStore.setPlayerPlaybackSpeed(speed)
                        player.setPlaybackSpeed(speed)
                        sessionNotice = "Playback ${speed}x"
                    },
                    onVideoFitChange = { fit ->
                        videoFit = fit
                        settingsStore.setPlayerVideoFit(fit)
                        sessionNotice = "Video ${fit.label}"
                    },
                    onSleepTimerChange = { option ->
                        sleepTimerOption = option
                        sleepTimerDeadlineMs = option.minutes?.let { minutes ->
                            SystemClock.elapsedRealtime() + minutes * 60_000L
                        }
                        sleepTimerRemainingSeconds = option.minutes?.let { it * 60L }
                        sessionNotice = when (option) {
                            TvSleepTimerOption.OFF -> "Sleep timer off"
                            TvSleepTimerOption.END_OF_EPISODE -> "Sleep after this episode"
                            else -> "Sleep timer ${option.label}"
                        }
                    },
                    onContentWarningsChange = { enabled ->
                        contentWarningsEnabled = enabled
                        settingsStore.setContentWarningsEnabled(enabled)
                        if (!enabled) {
                            showContentWarnings = false
                        }
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            null -> Unit
        }

        if (resumePromptVisible && sidePanel == null) {
            ResumePromptOverlay(
                positionMs = pendingResumePositionMs,
                resumeRequester = resumeRequester,
                startOverRequester = startOverRequester,
                onResume = {
                    player.seekTo(pendingResumePositionMs)
                    player.playWhenReady = true
                    resumePromptVisible = false
                    touchControls()
                },
                onStartOver = {
                    playbackStore.clear(request)
                    pendingResumePositionMs = 0L
                    player.seekTo(0L)
                    player.playWhenReady = true
                    resumePromptVisible = false
                    touchControls()
                },
            )
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(end = 44.dp, bottom = 150.dp)
                    .width(520.dp)
                    .background(Color(0xED181A1C), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(TvAccent, RoundedCornerShape(999.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next Episode",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = next.displayTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "Playing in ${seconds.coerceAtLeast(1)}s",
                color = PlayerMuted,
                fontSize = 12.sp,
            )
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
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth((seconds / AUTO_NEXT_COUNTDOWN_SECONDS.toFloat()).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(TvAccent, RoundedCornerShape(999.dp)),
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
            CircularProgressIndicator(color = TvAccent)
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
                    start = 42.dp,
                    bottom = if (controlsVisible) 150.dp else 48.dp,
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
    rewindRequester: FocusRequester,
    playRequester: FocusRequester,
    forwardRequester: FocusRequester,
    subtitleRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    nextRequester: FocusRequester,
    lockRequester: FocusRequester,
    moreRequester: FocusRequester,
    backRequester: FocusRequester,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayPause: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onEpisodes: () -> Unit,
    onPlaybackOptions: () -> Unit,
    onNext: (() -> Unit)?,
    onLock: () -> Unit,
    onBack: () -> Unit,
) {
    val hasEpisodes = request.episodeQueue.isNotEmpty()
    val topTitle =
        if (request.season != null && request.episode != null) {
            buildString {
                append("S${request.season} E${request.episode}")
                request.episodeTitle
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(" • $it") }
            }
        } else {
            request.media.name
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f),
                            ),
                        ),
                    ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 42.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = topTitle,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (waitingForRecovery || isBuffering) {
                Text(
                    text = if (waitingForRecovery) "TRYING NEXT SOURCE" else "BUFFERING",
                    color = TvAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(18.dp))
            }

            if (onNext != null) {
                MobilePlayerTopAction(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next episode",
                    requester = nextRequester,
                    onClick = onNext,
                    onLeft = { nextRequester.requestFocus() },
                    onRight = { lockRequester.requestFocus() },
                    onDown = { rewindRequester.requestFocus() },
                )
                Spacer(Modifier.width(10.dp))
            }

            MobilePlayerTopAction(
                icon = Icons.Default.Lock,
                contentDescription = "Lock controls",
                requester = lockRequester,
                onClick = onLock,
                onLeft = {
                    if (onNext != null) nextRequester.requestFocus() else lockRequester.requestFocus()
                },
                onRight = { moreRequester.requestFocus() },
                onDown = { playRequester.requestFocus() },
            )
            Spacer(Modifier.width(10.dp))

            MobilePlayerTopAction(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "More controls",
                requester = moreRequester,
                onClick = onPlaybackOptions,
                onLeft = { lockRequester.requestFocus() },
                onRight = { backRequester.requestFocus() },
                onDown = { playRequester.requestFocus() },
            )
            Spacer(Modifier.width(10.dp))

            MobilePlayerTopAction(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                requester = backRequester,
                onClick = onBack,
                onLeft = { moreRequester.requestFocus() },
                onRight = { backRequester.requestFocus() },
                onDown = { forwardRequester.requestFocus() },
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MobilePlayerRoundAction(
                icon = Icons.Default.Replay10,
                contentDescription = "Rewind 10 seconds",
                requester = rewindRequester,
                onClick = onSeekBackward,
                onLeft = { rewindRequester.requestFocus() },
                onRight = { playRequester.requestFocus() },
                onUp = {
                    if (onNext != null) nextRequester.requestFocus() else lockRequester.requestFocus()
                },
                onDown = { seekRequester.requestFocus() },
            )
            MobilePlayerRoundAction(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                requester = playRequester,
                primary = true,
                onClick = onPlayPause,
                onLeft = { rewindRequester.requestFocus() },
                onRight = { forwardRequester.requestFocus() },
                onUp = { moreRequester.requestFocus() },
                onDown = { seekRequester.requestFocus() },
            )
            MobilePlayerRoundAction(
                icon = Icons.Default.Forward10,
                contentDescription = "Forward 10 seconds",
                requester = forwardRequester,
                onClick = onSeekForward,
                onLeft = { playRequester.requestFocus() },
                onRight = { forwardRequester.requestFocus() },
                onUp = { backRequester.requestFocus() },
                onDown = { seekRequester.requestFocus() },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 44.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PlayerSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                requester = seekRequester,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                onPlayPause = onPlayPause,
                onUp = { playRequester.requestFocus() },
                onDown = { subtitleRequester.requestFocus() },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(positionMs),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(durationMs),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier =
                        Modifier
                            .background(Color(0xD9161719), RoundedCornerShape(999.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobilePlayerPanelAction(
                        icon = Icons.Default.ClosedCaption,
                        label = "Subs",
                        requester = subtitleRequester,
                        onClick = onSubtitles,
                        onLeft = { subtitleRequester.requestFocus() },
                        onRight = { audioRequester.requestFocus() },
                        onUp = { seekRequester.requestFocus() },
                    )
                    MobilePlayerPanelAction(
                        icon = Icons.Default.VolumeUp,
                        label = "Audio",
                        requester = audioRequester,
                        onClick = onAudio,
                        onLeft = { subtitleRequester.requestFocus() },
                        onRight = { sourcesRequester.requestFocus() },
                        onUp = { seekRequester.requestFocus() },
                    )
                    MobilePlayerPanelAction(
                        icon = Icons.Default.Dns,
                        label = "Sources",
                        requester = sourcesRequester,
                        onClick = onSources,
                        onLeft = { audioRequester.requestFocus() },
                        onRight = {
                            if (hasEpisodes) episodesRequester.requestFocus() else sourcesRequester.requestFocus()
                        },
                        onUp = { seekRequester.requestFocus() },
                    )
                    if (hasEpisodes) {
                        MobilePlayerPanelAction(
                            icon = Icons.Default.VideoLibrary,
                            label = "Episodes",
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
}

@Composable
private fun PlayerUnlockAction(
    requester: FocusRequester,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .padding(top = 26.dp, end = 42.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick = onUnlock)
                .scale(if (focused) 1.06f else 1f)
                .background(
                    if (focused) Color.White else Color.Black.copy(alpha = 0.62f),
                    RoundedCornerShape(999.dp),
                )
                .border(
                    1.dp,
                    if (focused) Color.Transparent else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onUnlock)
                .focusable()
                .padding(horizontal = 17.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (focused) Color.Black else Color.White,
        )
        Text(
            text = "Unlock",
            color = if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
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
                    .height(if (focused) 6.dp else 4.dp)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.30f),
                        RoundedCornerShape(999.dp),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 7.dp else 5.dp)
                    .background(TvAccent, RoundedCornerShape(999.dp)),
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
                            .size(if (focused) 17.dp else 13.dp)
                            .background(TvAccent, CircleShape),
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
private fun MobilePlayerRoundAction(
    icon: ImageVector,
    contentDescription: String,
    requester: FocusRequester,
    primary: Boolean = false,
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val buttonSize = if (primary) 92.dp else 76.dp
    val iconSize = if (primary) 58.dp else 46.dp

    Box(
        modifier =
            Modifier
                .size(buttonSize)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.09f else 1f)
                .background(
                    color =
                        when {
                            focused -> Color.White
                            primary -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (focused) Color.Black else Color.White,
        )
    }
}

@Composable
private fun MobilePlayerTopAction(
    icon: ImageVector,
    contentDescription: String,
    requester: FocusRequester,
    enabled: Boolean = true,
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
                .size(54.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.08f else 1f)
                .background(
                    if (focused) Color.White else Color.Black.copy(alpha = 0.42f),
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (focused) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    CircleShape,
                )
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(27.dp),
            tint =
                if (focused) {
                    Color.Black
                } else {
                    Color.White.copy(alpha = if (enabled) 0.94f else 0.38f)
                },
        )
    }
}

@Composable
private fun MobilePlayerPanelAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember(label) { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .height(48.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick, onLeft, onRight, onUp, onDown)
                .scale(if (focused) 1.045f else 1f)
                .background(
                    if (focused) Color.White else Color.Transparent,
                    RoundedCornerShape(22.dp),
                )
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint =
                if (focused) {
                    Color.Black
                } else {
                    Color.White.copy(alpha = if (enabled) 0.94f else 0.38f)
                },
        )
        Text(
            text = label,
            color =
                if (focused) {
                    Color.Black
                } else {
                    Color.White.copy(alpha = if (enabled) 0.88f else 0.38f)
                },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
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
private fun PlayerWideButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember(text) { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .playerRemoteKeys(onClick = onClick, onUp = onUp, onDown = onDown)
                .scale(if (focused) 1.025f else 1f)
                .background(
                    color =
                        when {
                            focused -> Color.White
                            primary -> TvAccent.copy(alpha = 0.90f)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                    shape = RoundedCornerShape(14.dp),
                )
                .border(
                    1.dp,
                    if (focused) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (focused) Color.Black else Color.White,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
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

    var providerFilter by remember { mutableStateOf<String?>(null) }
    val providers =
        sources
            .filter { it.isDirectPlayable }
            .map { it.providerName }
            .filter { it.isNotBlank() }
            .distinct()
    val visibleSources =
        if (providerFilter == null) {
            sources
        } else {
            sources.filter { it.providerName == providerFilter }
        }

    RightPanel(
        title = "Sources",
        subtitle = buildString {
            append("VUEO ranked for fast direct playback")
            providerFilter?.let { append(" • $it") }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "PROVIDER",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            item {
                PanelOptionRow(
                    label = "All providers",
                    secondary = "${sources.count { it.isDirectPlayable }} playable sources",
                    selected = providerFilter == null,
                    requester = firstRequester,
                    onClick = { providerFilter = null },
                )
            }
            providers.forEach { provider ->
                item(key = "provider:$provider") {
                    PanelOptionRow(
                        label = provider,
                        secondary = "${sources.count { it.providerName == provider && it.isDirectPlayable }} playable",
                        selected = providerFilter == provider,
                        onClick = { providerFilter = provider },
                    )
                }
            }
            item {
                Text(
                    text = "SOURCES",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            itemsIndexed(visibleSources, key = { _, source -> source.id }) { index, source ->
                var focused by remember(source.id) { mutableStateOf(false) }
                val active = selected?.id == source.id
                val assessment = SourceRanker.assess(source, originalLanguage = originalLanguage)

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .background(
                                when {
                                    focused -> Color.White.copy(alpha = 0.16f)
                                    active -> TvAccent.copy(alpha = 0.10f)
                                    else -> Color.White.copy(alpha = 0.055f)
                                },
                                RoundedCornerShape(11.dp),
                            )
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = when {
                                    focused -> PlayerFocus
                                    active -> TvAccent.copy(alpha = 0.75f)
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
                        color = TvAccent.copy(alpha = 0.92f),
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
                                        active -> TvAccent.copy(alpha = 0.10f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = when {
                                        focused -> PlayerFocus
                                        active -> TvAccent.copy(alpha = 0.75f)
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
                            color = if (active) TvAccent else Color.White,
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
                                        option.selected -> TvAccent.copy(alpha = 0.10f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = when {
                                        focused -> PlayerFocus
                                        option.selected -> TvAccent.copy(alpha = 0.75f)
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
private fun SubtitlePickerPanel(
    options: List<TrackOption>,
    subtitleDelayMs: Int,
    firstRequester: FocusRequester,
    onDelayChange: (Int) -> Unit,
    onSelect: (TrackOption) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    RightPanel(
        title = "Subtitles",
        subtitle = "Track selection • Sync ${formatSubtitleDelay(subtitleDelayMs)}",
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PanelOptionRow(
                    label = "Sync earlier",
                    secondary = "Move subtitles 0.5 seconds earlier",
                    requester = firstRequester,
                    onClick = { onDelayChange(subtitleDelayMs - 500) },
                )
            }
            item {
                PanelOptionRow(
                    label = "Reset sync",
                    secondary = "Current ${formatSubtitleDelay(subtitleDelayMs)}",
                    selected = subtitleDelayMs == 0,
                    onClick = { onDelayChange(0) },
                )
            }
            item {
                PanelOptionRow(
                    label = "Sync later",
                    secondary = "Move subtitles 0.5 seconds later",
                    onClick = { onDelayChange(subtitleDelayMs + 500) },
                )
            }
            item {
                Text(
                    text = "TRACKS",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            if (options.isEmpty()) {
                item {
                    Text(
                        text = "No subtitle tracks are available in this source.",
                        color = PlayerMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                itemsIndexed(options) { _, option ->
                    PanelOptionRow(
                        label = if (option.selected) "✓  ${option.label}" else option.label,
                        secondary = option.secondary,
                        selected = option.selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun PlaybackOptionsPanel(
    playbackSpeed: Float,
    videoFit: PlayerVideoFit,
    sleepTimerOption: TvSleepTimerOption,
    sleepTimerRemainingSeconds: Long?,
    contentWarningsEnabled: Boolean,
    firstRequester: FocusRequester,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoFitChange: (PlayerVideoFit) -> Unit,
    onSleepTimerChange: (TvSleepTimerOption) -> Unit,
    onContentWarningsChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val timerStatus =
        when {
            sleepTimerRemainingSeconds != null ->
                "${(sleepTimerRemainingSeconds / 60).coerceAtLeast(0)} min remaining"
            sleepTimerOption == TvSleepTimerOption.END_OF_EPISODE ->
                "End of episode"
            else -> "Off"
        }

    RightPanel(
        title = "More",
        subtitle = "Playback controls • Sleep timer $timerStatus",
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "SPEED",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEachIndexed { index, speed ->
                item(key = "speed:$speed") {
                    PanelOptionRow(
                        label = "${speed}x",
                        secondary = if (speed == 1f) "Normal speed" else null,
                        selected = playbackSpeed == speed,
                        requester = if (index == 0) firstRequester else null,
                        onClick = { onPlaybackSpeedChange(speed) },
                    )
                }
            }
            item {
                Text(
                    "VIDEO FIT",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            PlayerVideoFit.entries.forEach { fit ->
                item(key = "fit:${fit.name}") {
                    PanelOptionRow(
                        label = fit.label,
                        secondary = when (fit) {
                            PlayerVideoFit.FIT -> "Show the complete frame"
                            PlayerVideoFit.FILL -> "Fill the television screen"
                            PlayerVideoFit.ZOOM -> "Crop edges to fill without stretching"
                        },
                        selected = videoFit == fit,
                        onClick = { onVideoFitChange(fit) },
                    )
                }
            }
            item {
                Text(
                    "SLEEP TIMER",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            TvSleepTimerOption.entries.forEach { option ->
                item(key = "timer:${option.name}") {
                    PanelOptionRow(
                        label = option.label,
                        selected = sleepTimerOption == option,
                        onClick = { onSleepTimerChange(option) },
                    )
                }
            }
            item {
                Text(
                    "BEHAVIOUR",
                    color = PlayerMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            item {
                PanelOptionRow(
                    label = "Content warnings",
                    secondary = "Parents-guide warning at playback start when available",
                    selected = contentWarningsEnabled,
                    onClick = { onContentWarningsChange(!contentWarningsEnabled) },
                )
            }
            item { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun PanelOptionRow(
    label: String,
    secondary: String? = null,
    selected: Boolean = false,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .scale(if (focused) 1.025f else 1f)
                .background(
                    when {
                        focused -> Color.White.copy(alpha = 0.16f)
                        selected -> TvAccent.copy(alpha = 0.10f)
                        else -> Color.White.copy(alpha = 0.055f)
                    },
                    RoundedCornerShape(11.dp),
                )
                .border(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> PlayerFocus
                        selected -> TvAccent.copy(alpha = 0.70f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(11.dp),
                )
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (selected) "✓  $label" else label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        secondary?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                text = it,
                color = PlayerMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResumePromptOverlay(
    positionMs: Long,
    resumeRequester: FocusRequester,
    startOverRequester: FocusRequester,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .background(Color(0xF2131416), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .background(TvAccent.copy(alpha = 0.14f), CircleShape)
                            .border(1.dp, TvAccent.copy(alpha = 0.42f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = TvAccent,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Resume watching?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Continue from ${formatTime(positionMs)}",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 14.sp,
                    )
                }
            }

            PlayerWideButton(
                text = "Resume ${formatTime(positionMs)}",
                icon = Icons.Default.PlayArrow,
                requester = resumeRequester,
                primary = true,
                onDown = { startOverRequester.requestFocus() },
                onClick = onResume,
            )
            PlayerWideButton(
                text = "Start Over",
                requester = startOverRequester,
                onUp = { resumeRequester.requestFocus() },
                onClick = onStartOver,
            )
        }
    }
}

@Composable
private fun ContentWarningsOverlay(
    warnings: List<ContentWarning>,
    onAnimationComplete: () -> Unit,
) {
    val count = warnings.size
    val totalLineHeight = (count * 14) + ((count - 1) * 2)
    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val itemAlphas = remember(count) {
        List(count) { Animatable(0f) }
    }

    LaunchedEffect(warnings) {
        containerAlpha.animateTo(1f, tween(300))
        lineHeightFraction.animateTo(
            1f,
            tween(400, easing = FastOutSlowInEasing),
        )

        for (index in 0 until count) {
            delay(80L)
            itemAlphas[index].animateTo(1f, tween(200))
        }

        delay(5_000L)

        for (index in (count - 1) downTo 0) {
            delay(60L)
            itemAlphas[index].animateTo(0f, tween(150))
        }

        delay(100L)
        lineHeightFraction.animateTo(
            0f,
            tween(300, easing = FastOutSlowInEasing),
        )
        delay(200L)
        containerAlpha.animateTo(0f, tween(200))
        onAnimationComplete()
    }

    if (containerAlpha.value <= 0f) {
        return
    }

    Row(
        modifier = Modifier.alpha(containerAlpha.value),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(
                    (
                        totalLineHeight *
                            lineHeightFraction.value
                    ).dp
                )
                .clip(RoundedCornerShape(50))
                .background(VueoPlayerWarningAccent),
        )
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            warnings.forEachIndexed { index, warning ->
                Row(
                    modifier = Modifier
                        .alpha(
                            itemAlphas
                                .getOrNull(index)
                                ?.value
                                ?: 0f
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        warning.label,
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        " · ${warning.severity}",
                        color = Color.White.copy(alpha = .56f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
    }
}

private val VueoPlayerWarningAccent =
    Color(0xFFB9FF3A)

@Composable
private fun SessionNoticeOverlay(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(999.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RightPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(760.dp)
                    .fillMaxHeight(0.82f)
                    .background(Color(0xF2131416), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(top = 24.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
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
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(end = 44.dp, bottom = 150.dp)
                    .width(520.dp)
                    .background(Color(0xED181A1C), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(TvAccent, RoundedCornerShape(999.dp)),
                )
                Text(
                    text = "Playback problem",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerButton(
                    text = "Retry",
                    requester = requester,
                    onClick = onRetry,
                    primary = true,
                    onRight = { sourcesRequester.requestFocus() },
                )
                PlayerButton(
                    text = "Choose Source",
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

private fun PlayerVideoFit.toMedia3ResizeMode(): Int =
    when (this) {
        PlayerVideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerVideoFit.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        PlayerVideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

private fun applyTvSubtitleStyle(
    playerView: PlayerView,
    settingsStore: SettingsStore,
) {
    val opacity = settingsStore.subtitleTextOpacityPercent().coerceIn(20, 100)
    val alpha = ((opacity / 100f) * 255f).toInt().coerceIn(0, 255)
    val textColor =
        (settingsStore.subtitleTextColor() and 0x00FFFFFF) or (alpha shl 24)

    playerView.subtitleView?.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            settingsStore.subtitleFontSizeSp().toFloat(),
        )
        setBottomPaddingFraction(
            settingsStore.subtitleBottomPaddingPercent() / 100f,
        )
        setStyle(
            CaptionStyleCompat(
                textColor,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                if (settingsStore.subtitleOutlineEnabled()) {
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    CaptionStyleCompat.EDGE_TYPE_NONE
                },
                settingsStore.subtitleOutlineColor(),
                if (settingsStore.subtitleBold()) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                },
            ),
        )
    }
}

private fun formatSubtitleDelay(delayMs: Int): String =
    if (delayMs == 0) {
        "0.0s"
    } else {
        val seconds = delayMs / 1_000f
        String.format("%+.1fs", seconds)
    }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TvSubtitleOffsetRenderersFactory(
    context: android.content.Context,
    private val subtitleDelayUsProvider: () -> Long,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: android.content.Context,
        output: TextOutput,
        outputLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val firstTextRenderer = out.size
        super.buildTextRenderers(
            context,
            output,
            outputLooper,
            extensionRendererMode,
            out,
        )
        for (index in firstTextRenderer until out.size) {
            out[index] = TvSubtitleOffsetRenderer(
                baseRenderer = out[index],
                subtitleDelayUsProvider = subtitleDelayUsProvider,
            )
        }
    }
}

private class TvSubtitleOffsetRenderer(
    baseRenderer: Renderer,
    private val subtitleDelayUsProvider: () -> Long,
) : ForwardingRenderer(baseRenderer) {
    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        val subtitlePositionUs =
            (positionUs - subtitleDelayUsProvider()).coerceAtLeast(0L)
        super.render(subtitlePositionUs, elapsedRealtimeUs)
    }
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
private const val RESUME_PROMPT_THRESHOLD_MS = 5_000L
private const val BUFFER_RECOVERY_TIMEOUT_MS = 18_000L
private const val SEEK_STEP_MS = 10_000L
private const val TRACK_SELECTION_OFF = "__off__"
private val IMDB_ID_REGEX = Regex("tt\\d{5,10}", RegexOption.IGNORE_CASE)
