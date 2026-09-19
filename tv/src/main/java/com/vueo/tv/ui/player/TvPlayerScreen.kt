package com.vueo.tv.player

import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.enrichment.ContentWarningRepository
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem as VueoMediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.PlayerTrackPolicy
import com.vueo.shared.core.player.IndependentSubtitleCueChannel
import com.vueo.shared.core.player.IndependentSubtitleRepository
import com.vueo.shared.core.player.TimedSubtitleCue
import com.vueo.shared.core.player.PlayerSkipRepository
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.shared.core.source.SOURCE_RECOVERY_SOURCE_TIMEOUT_MS
import com.vueo.shared.core.source.SOURCE_REBUFFER_TIMEOUT_MS
import com.vueo.shared.core.source.SOURCE_STARTUP_TIMEOUT_MS
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRecoverySession
import com.vueo.shared.core.source.SourceSelector
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.SubtitleVisibility
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.motion.tvPanelEnter
import com.vueo.tv.ui.motion.tvPanelExit
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class TvPlayerPanel {
    NONE,
    SUBTITLES,
    AUDIO,
    SOURCES,
    EPISODES,
    MORE,
}

internal data class TvPlayerOption(
    val key: String,
    val title: String,
    val meta: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun TvPlayerScreen(
    runtime: TvRuntime,
    media: VueoMediaItem,
    episode: EpisodeItem?,
    bundle: TvSourceBundle,
    availableSubtitles: List<SubtitleTrack>,
    source: StreamSource,
    initialPositionMs: Long,
    playerSessionId: Int,
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit,
    onPlayNextEpisode: (EpisodeItem) -> Unit = {},
) {
    val context = LocalContext.current
    val rootRequester = remember { FocusRequester() }
    val restartRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val subtitlesRequester = remember { FocusRequester() }
    val subtitleWorkspaceRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }
    val moreRequester = remember { FocusRequester() }
    val skipRequester = remember { FocusRequester() }
    val nextContextRequester = remember { FocusRequester() }
    val errorRequester = remember { FocusRequester() }

    val mediaKey = "${media.type}:${media.id}:${bundle.videoId}"
    val settings = runtime.settingsStore

    val savedPosition = remember(mediaKey) { runtime.playbackStore.positionMs(mediaKey) }
    val startPosition = remember(mediaKey, initialPositionMs) {
        when {
            initialPositionMs > 5_000L -> initialPositionMs
            settings.resumePlaybackEnabled() && savedPosition > 5_000L -> savedPosition
            else -> 0L
        }
    }

    val playableSources = remember(bundle.sources, source.url) {
        (listOf(source) + bundle.sources)
            .filter { it.isDirectPlayable }
            .distinctBy { SourceSelector.identityKey(it.toSourceCandidateForPlayer()) }
    }
    val latestPlayableSources = androidx.compose.runtime.rememberUpdatedState(playableSources)
    val initialMediaSubtitles = remember(bundle.videoId, source.url, playerSessionId) {
        bundle.subtitles
            .filterNot(PlayerTrackPolicy::isOnDemandTranslation)
            .toList()
    }
    val externalSubtitlesBySelectionId = remember(initialMediaSubtitles) {
        initialMediaSubtitles.associateBy(::tvExternalSubtitleSelectionId)
    }
    val latestExternalSubtitlesBySelectionId =
        androidx.compose.runtime.rememberUpdatedState(externalSubtitlesBySelectionId)
    var activeSource by remember(bundle.videoId, source.url) { mutableStateOf(source) }
    var resumeTargetMs by remember(bundle.videoId) { mutableLongStateOf(startPosition) }
    val sourceRecoverySession = remember(bundle.videoId) { SourceRecoverySession() }
    var hasRenderedFirstFrame by remember(bundle.videoId) { mutableStateOf(false) }
    var isBuffering by remember(bundle.videoId) { mutableStateOf(false) }
    var recoveryInProgress by remember(bundle.videoId) { mutableStateOf(false) }
    var playbackError by remember(bundle.videoId) { mutableStateOf<String?>(null) }
    var retryGeneration by remember(bundle.videoId) { mutableIntStateOf(0) }
    var autoNextCancelled by remember(bundle.videoId) { mutableStateOf(false) }

    var subtitleDelayMs by remember(mediaKey) { mutableIntStateOf(settings.subtitleDelayMs(mediaKey)) }
    val latestSubtitleDelayMs = androidx.compose.runtime.rememberUpdatedState(subtitleDelayMs)
    val storedSubtitleFontSizeSp = remember {
        settings.migrateTvSubtitlePresentationDefaults()
        settings.subtitleFontSizeSp()
    }
    val storedSubtitleBottomPaddingPercent = remember { settings.subtitleBottomPaddingPercent() }
    val storedSubtitleTextColor = remember { settings.subtitleTextColor() }
    val storedSubtitleTextOpacityPercent = remember { settings.subtitleTextOpacityPercent() }
    var subtitleStyle by remember {
        mutableStateOf(
            TvPlayerSubtitleStyleState(
                fontSizeSp = storedSubtitleFontSizeSp,
                bold = settings.subtitleBold(),
                textColor = if ((storedSubtitleTextColor ushr 24) != 0xFF) {
                    storedSubtitleTextColor
                } else {
                    withAlpha(storedSubtitleTextColor, storedSubtitleTextOpacityPercent)
                },
                outlineEnabled = settings.subtitleOutlineEnabled(),
                outlineColor = settings.subtitleOutlineColor(),
                bottomPaddingPercent = storedSubtitleBottomPaddingPercent,
            )
        )
    }
    var selectedSubtitleIsExternal by remember(mediaKey) { mutableStateOf(false) }
    val latestSelectedSubtitleIsExternal = androidx.compose.runtime.rememberUpdatedState(selectedSubtitleIsExternal)

    val httpFactory = remember(bundle.videoId) {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VUEO-TV")
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember(bundle.videoId) {
        ExoPlayer.Builder(
            context,
            TvSubtitleOffsetRenderersFactory(
                context = context,
                subtitleDelayUsProvider = {
                    latestSubtitleDelayMs.value.toLong() * 1_000L
                },
                shouldNormalizeCuePositionProvider = {
                    latestSelectedSubtitleIsExternal.value
                },
            ),
        )
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var activePanel by remember { mutableStateOf(TvPlayerPanel.NONE) }
    var restorePanelFocus by remember { mutableStateOf<TvPlayerPanel?>(null) }
    var endedFocusAssigned by remember(mediaKey) { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(startPosition) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(0) }
    var resolvedImdbId by remember(mediaKey) { mutableStateOf<String?>(null) }
    var skipSegments by remember(mediaKey) { mutableStateOf<List<PlayerSkipSegment>>(emptyList()) }
    var contentWarnings by remember(mediaKey) { mutableStateOf<List<ContentWarning>>(emptyList()) }
    var warningVisible by remember(playerSessionId) { mutableStateOf(false) }
    var warningShown by remember(playerSessionId) { mutableStateOf(false) }
    var textTracks by remember(bundle.videoId) { mutableStateOf<List<TvPlayerTrackChoice>>(emptyList()) }
    var audioTracks by remember(bundle.videoId) { mutableStateOf<List<TvPlayerTrackChoice>>(emptyList()) }
    var subtitlesDisabled by remember(mediaKey) {
        mutableStateOf(
            when (settings.lastSubtitleSelection()) {
                TV_SUBTITLE_OFF -> true
                null -> !settings.subtitlesOnByDefault()
                else -> false
            }
        )
    }
    var audioAutomaticSelected by remember(mediaKey) { mutableStateOf(true) }
    var subtitlePreferenceRestored by remember(bundle.videoId, activeSource.url) { mutableStateOf(false) }
    var audioPreferenceRestored by remember(bundle.videoId, activeSource.url) { mutableStateOf(false) }
    val initialSubtitleKeys = remember(initialMediaSubtitles) {
        initialMediaSubtitles.map(PlayerTrackPolicy::externalSubtitleKey).toSet()
    }
    val independentSubtitleTracks = remember(availableSubtitles, initialSubtitleKeys) {
        availableSubtitles
            .filter { it.url.startsWith("https://") &&
                PlayerTrackPolicy.externalSubtitleKey(it) !in initialSubtitleKeys }
            .distinctBy(PlayerTrackPolicy::externalSubtitleKey)
    }
    val latestIndependentSubtitleTracks =
        androidx.compose.runtime.rememberUpdatedState(independentSubtitleTracks)
    var selectedIndependentSubtitleSelectionId by remember(bundle.videoId, activeSource.url) {
        mutableStateOf<String?>(null)
    }
    // Sidecar cues belong to this ExoPlayer session, not the video MediaItem
    // or player-wide Compose state.
    val independentSubtitleCueChannel = remember(player) {
        IndependentSubtitleCueChannel()
    }
    var subtitleLoadRetryToken by remember(player) { mutableIntStateOf(0) }
    var subtitleLoadingSelectionId by remember(player) { mutableStateOf<String?>(null) }
    var subtitleLoadError by remember(player) { mutableStateOf<String?>(null) }
    var playbackSpeed by remember(bundle.videoId) { mutableStateOf(settings.playerPlaybackSpeed()) }
    var videoFit by remember(bundle.videoId) { mutableStateOf(settings.playerVideoFit()) }
    var sleepTimerOption by remember(bundle.videoId) { mutableStateOf(TvPlayerSleepTimerOption.OFF) }
    var sleepTimerDeadlineMs by remember(bundle.videoId) { mutableStateOf<Long?>(null) }
    var sleepTimerRemainingSeconds by remember(bundle.videoId) { mutableStateOf<Long?>(null) }
    var autoPlayNextEpisode by remember { mutableStateOf(settings.autoPlayNextEpisodeEnabled()) }
    var skipSegmentsEnabled by remember(mediaKey) { mutableStateOf(settings.skipSegmentsEnabled()) }
    var contentWarningsEnabled by remember(mediaKey) { mutableStateOf(settings.contentWarningsEnabled()) }

    val nextEpisode = remember(media.episodes, episode?.id) { nextEpisode(media.episodes, episode) }
    val activeSkip = remember(positionMs, skipSegments) {
        skipSegments.firstOrNull { segment ->
            positionMs in segment.startMs until segment.endMs && segment.endMs - positionMs > 800L
        }
    }
    val hasSubtitleControl = textTracks.isNotEmpty() || availableSubtitles.isNotEmpty()
    val hasAudioControl = audioTracks.isNotEmpty() || !activeSource.audio.isNullOrBlank()
    val hasSourcesControl = playableSources.isNotEmpty()
    val hasEpisodesControl = media.episodes.isNotEmpty()

    val focusScope = rememberCoroutineScope()
    var pendingFocusJob by remember { mutableStateOf<Job?>(null) }

    fun noteInteraction() {
        interactionToken += 1
    }

    fun requestFocusReliably(requester: FocusRequester) {
        pendingFocusJob?.cancel()
        pendingFocusJob = focusScope.launch {
            requester.requestTvFocus()
        }
    }

    suspend fun requestFocusNow(requester: FocusRequester): Boolean {
        pendingFocusJob?.cancel()
        pendingFocusJob = null
        return requester.requestTvFocus()
    }

    fun requestControlFocus(requester: FocusRequester = progressRequester) {
        controlsVisible = true
        noteInteraction()
        requestFocusReliably(requester)
    }

    fun seekBy(deltaMs: Long) {
        val target = player.currentPosition + deltaMs
        val max = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
        player.seekTo(
            if (max != null) target.coerceIn(0L, max)
            else target.coerceAtLeast(0L),
        )
        positionMs = player.currentPosition.coerceAtLeast(0L)
        noteInteraction()
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        playing = player.isPlaying
        noteInteraction()
    }

    fun saveProgress() {
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        runtime.playbackStore.savePositionMs(mediaKey = mediaKey, positionMs = position, durationMs = duration)
        runtime.libraryStore.recordPlayback(
            media = media,
            videoId = bundle.videoId,
            episodeTitle = episode?.title,
            season = episode?.season,
            episode = episode?.episode,
            positionMs = position,
            durationMs = duration,
        )
        onLibraryChanged()
    }

    fun closePanel(restoreFocus: Boolean = true) {
        val closingPanel = activePanel
        activePanel = TvPlayerPanel.NONE
        noteInteraction()
        restorePanelFocus = closingPanel.takeIf { restoreFocus && it != TvPlayerPanel.NONE }
    }

    fun exitPlayer() {
        saveProgress()
        onBack()
    }

    fun selectSubtitleChoiceForPlayer(
        choice: TvPlayerTrackChoice,
    ) {
        if (choice.override == null) {
            tvClearTrackOverride(
                player = player,
                trackType = C.TRACK_TYPE_TEXT,
                disable = true,
            )
            // Re-selecting the same AI track retries a failed/pending download.
            if (selectedIndependentSubtitleSelectionId == choice.selectionId &&
                subtitleLoadingSelectionId == null
            ) {
                subtitleLoadRetryToken++
            }
            selectedIndependentSubtitleSelectionId =
                choice.selectionId
            textTracks = textTracks.map { track ->
                track.copy(
                    selected =
                        track.selectionId == choice.selectionId
                )
            }
            selectedSubtitleIsExternal = true
        } else {
            selectedIndependentSubtitleSelectionId = null
            tvApplyTrackChoice(
                player = player,
                trackType = C.TRACK_TYPE_TEXT,
                choice = choice,
            )
            selectedSubtitleIsExternal =
                choice.selectionId.startsWith("external:")
        }
        subtitlesDisabled = false
    }

    fun handleSourceFailure(message: String) {
        if (recoveryInProgress) return

        sourceRecoverySession.markFailed(activeSource.toSourceCandidateForPlayer())
        val latestSources = latestPlayableSources.value
        val alternateCandidate = if (settings.autoSourceRecoveryEnabled()) {
            sourceRecoverySession.next(
                rankedSources = latestSources.map { it.toSourceCandidateForPlayer() },
                originalLanguage = media.originalLanguage,
            )
        } else {
            null
        }
        val alternateKey = alternateCandidate?.let(SourceSelector::identityKey)
        val alternate = alternateKey?.let { key ->
            latestSources.firstOrNull {
                SourceSelector.identityKey(it.toSourceCandidateForPlayer()) == key
            }
        }

        if (alternate != null) {
            resumeTargetMs = player.currentPosition.coerceAtLeast(positionMs).coerceAtLeast(0L)
            saveProgress()
            recoveryInProgress = true
            hasRenderedFirstFrame = false
            isBuffering = false
            playbackError = null
            activeSource = alternate
            requestControlFocus(progressRequester)
        } else {
            val failedCount = sourceRecoverySession.failedSourceCount()
            playbackError = if (failedCount > 1) {
                "$failedCount ranked sources failed. Choose Sources to try one manually."
            } else {
                "$message No other suitable automatic source was available."
            }
            requestControlFocus(progressRequester)
        }
    }

    BackHandler {
        when {
            nextCountdown > 0 -> {
                autoNextCancelled = true
                nextCountdown = 0
                requestControlFocus(progressRequester)
            }
            activePanel != TvPlayerPanel.NONE -> closePanel()
            controlsVisible -> {
                controlsVisible = false
                requestFocusReliably(rootRequester)
            }
            else -> exitPlayer()
        }
    }

    LaunchedEffect(activeSource.url, bundle.videoId) {
        val url = activeSource.url ?: return@LaunchedEffect
        sourceRecoverySession.begin(activeSource.toSourceCandidateForPlayer())
        recoveryInProgress = false
        hasRenderedFirstFrame = false
        isBuffering = false
        httpFactory.setDefaultRequestProperties(activeSource.headers)
        playbackError = null
        textTracks = emptyList()
        audioTracks = emptyList()
        selectedSubtitleIsExternal = false

        val primaryLanguage = settings.preferredSubtitleLanguage().languageCode
        val secondaryLanguage = settings.secondarySubtitleLanguage().languageCode
        val languages = listOfNotNull(primaryLanguage, secondaryLanguage).distinct()

        player.setMediaItem(
            buildMediaItem(
                sourceUrl = url,
                subtitles = initialMediaSubtitles,
                preferredLanguages = languages,
                subtitlesOnByDefault = !subtitlesDisabled,
                autoSelectPreferred = settings.autoSelectPreferredSubtitle(),
                preferEmbedded = settings.embeddedSubtitlePriority(),
            ),
            resumeTargetMs.coerceAtLeast(0L),
        )
        var params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitlesDisabled)
        if (settings.autoSelectPreferredSubtitle() && languages.isNotEmpty()) {
            params = params.setPreferredTextLanguages(*languages.toTypedArray())
        }
        PlayerSourcePolicy.canonicalLanguageCode(media.originalLanguage)?.let { originalLanguage ->
            params = params.setPreferredAudioLanguages(originalLanguage)
        }
        player.trackSelectionParameters = params.build()
        player.setPlaybackSpeed(playbackSpeed)
        player.prepare()
        player.playWhenReady = true
    }

    // Discovery updates may arrive while AI is translating. Select the exact
    // track once; unrelated addon list changes must not cancel its download.
    val selectedIndependentTrack = availableSubtitles.firstOrNull { track ->
        PlayerTrackPolicy.externalSubtitleSelectionId(track) ==
            selectedIndependentSubtitleSelectionId
    }
    LaunchedEffect(
        selectedIndependentSubtitleSelectionId,
        subtitleLoadRetryToken,
        selectedIndependentTrack?.url,
        selectedIndependentTrack?.headers,
    ) {
        val track = selectedIndependentTrack ?: run {
            independentSubtitleCueChannel.cues = emptyList()
            subtitleLoadingSelectionId = null
            subtitleLoadError = null
            return@LaunchedEffect
        }

        independentSubtitleCueChannel.cues = emptyList()
        subtitleLoadError = null
        subtitleLoadingSelectionId = selectedIndependentSubtitleSelectionId
        try {
            val loaded = IndependentSubtitleRepository.load(
                track = track,
                fallbackHeaders = activeSource.headers,
            )
            independentSubtitleCueChannel.cues = loaded
            if (loaded.isEmpty()) subtitleLoadError = "Subtitle file is empty or unsupported"
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            subtitleLoadError = "Unable to load subtitle"
        } finally {
            subtitleLoadingSelectionId = null
        }
    }

    LaunchedEffect(
        independentSubtitleTracks.map(PlayerTrackPolicy::externalSubtitleKey),
    ) {
        if (independentSubtitleTracks.isNotEmpty()) {
            subtitlePreferenceRestored = false
        }
    }

    DisposableEffect(player, activeSource.url, settings.autoSourceRecoveryEnabled()) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val playerTextTracks = tvPlayerTrackChoices(
                    tracks = tracks,
                    trackType = C.TRACK_TYPE_TEXT,
                    externalSubtitles = latestExternalSubtitlesBySelectionId.value,
                )
                val independentTextTracks =
                    tvIndependentSubtitleTrackChoices(
                        subtitles = latestIndependentSubtitleTracks.value,
                        selectedSelectionId =
                            selectedIndependentSubtitleSelectionId,
                    )
                textTracks =
                    (playerTextTracks + independentTextTracks)
                        .distinctBy { it.selectionId }
                audioTracks = tvPlayerTrackChoices(
                    tracks = tracks,
                    trackType = C.TRACK_TYPE_AUDIO,
                )
                selectedSubtitleIsExternal =
                    !subtitlesDisabled &&
                        (
                            selectedIndependentSubtitleSelectionId != null ||
                                playerTextTracks
                                    .firstOrNull { it.selected }
                                    ?.selectionId
                                    ?.startsWith("external:") == true
                        )
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                handleSourceFailure(error.message ?: "Playback failed.")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    sourceRecoverySession.markReady()
                    recoveryInProgress = false
                    playbackError = null
                }
                if (playbackState == Player.STATE_ENDED) {
                    val completedDuration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                    runtime.playbackStore.clearPosition(mediaKey)
                    runtime.libraryStore.recordPlayback(
                        media = media, videoId = bundle.videoId, episodeTitle = episode?.title,
                        season = episode?.season, episode = episode?.episode,
                        positionMs = completedDuration, durationMs = completedDuration,
                    )
                    onLibraryChanged()
                    controlsVisible = true
                    playing = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (!isPlaying && player.playbackState != Player.STATE_ENDED) {
                    saveProgress()
                    requestControlFocus(progressRequester)
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
                sourceRecoverySession.markReady()
                recoveryInProgress = false
                playbackError = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(activeSource.url, hasRenderedFirstFrame, playbackError, retryGeneration) {
        if (hasRenderedFirstFrame || playbackError != null) return@LaunchedEffect

        val timeoutMs = if (sourceRecoverySession.isAutomaticRecoveryActive()) {
            SOURCE_RECOVERY_SOURCE_TIMEOUT_MS
        } else {
            SOURCE_STARTUP_TIMEOUT_MS
        }
        delay(timeoutMs)
        if (!hasRenderedFirstFrame && playbackError == null && !recoveryInProgress) {
            handleSourceFailure(
                "This source did not start within ${timeoutMs / 1_000L} seconds."
            )
        }
    }

    LaunchedEffect(activeSource.url, isBuffering, hasRenderedFirstFrame, playbackError, retryGeneration) {
        if (!isBuffering || !hasRenderedFirstFrame || playbackError != null) {
            return@LaunchedEffect
        }

        delay(SOURCE_REBUFFER_TIMEOUT_MS)
        if (isBuffering && hasRenderedFirstFrame && playbackError == null && !recoveryInProgress) {
            handleSourceFailure(
                "Playback remained stuck buffering for ${SOURCE_REBUFFER_TIMEOUT_MS / 1_000L} seconds."
            )
        }
    }

    LaunchedEffect(media.id, bundle.videoId, episode?.id, runtime.pluginStore.tmdbApiKey()) {
        resolvedImdbId =
            ContentWarningRepository.extractImdbId(media.id)
                ?: ContentWarningRepository.extractImdbId(bundle.videoId)
                ?: ContentWarningRepository.extractImdbId(episode?.id)
                ?: runCatching {
                    TmdbEnhancementClient.prepareForCore(
                        item = media,
                        apiKey = runtime.pluginStore.tmdbApiKey(),
                    ).id
                }.getOrNull()?.let(ContentWarningRepository::extractImdbId)
    }

    LaunchedEffect(
        resolvedImdbId,
        episode?.season,
        episode?.episode,
        skipSegmentsEnabled,
    ) {
        skipSegments = emptyList()
        val imdbId = resolvedImdbId
        if (skipSegmentsEnabled && episode != null && imdbId != null) {
            skipSegments = runCatching {
                PlayerSkipRepository.segments(imdbId, episode.season, episode.episode)
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(resolvedImdbId, contentWarningsEnabled, playerSessionId) {
        contentWarnings = emptyList()
        warningVisible = false
        warningShown = false

        val imdbId = resolvedImdbId
        if (contentWarningsEnabled && imdbId != null) {
            contentWarnings = runCatching {
                ContentWarningRepository.get(imdbId)
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(
        hasRenderedFirstFrame,
        contentWarnings,
        contentWarningsEnabled,
        playerSessionId,
    ) {
        if (!contentWarningsEnabled) {
            warningVisible = false
            return@LaunchedEffect
        }

        if (hasRenderedFirstFrame && contentWarnings.isNotEmpty() && !warningShown) {
            warningShown = true
            warningVisible = true
        }
    }

    LaunchedEffect(player, activeSource.url, bundle.videoId) {
        requestFocusNow(progressRequester)
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            ended = player.playbackState == Player.STATE_ENDED

            val playerTextTracks = tvPlayerTrackChoices(
                tracks = player.currentTracks,
                trackType = C.TRACK_TYPE_TEXT,
                externalSubtitles = latestExternalSubtitlesBySelectionId.value,
            )
            val independentTextTracks =
                tvIndependentSubtitleTrackChoices(
                    subtitles = latestIndependentSubtitleTracks.value,
                    selectedSelectionId =
                        selectedIndependentSubtitleSelectionId,
                )
            val currentTextTracks =
                (playerTextTracks + independentTextTracks)
                    .distinctBy { it.selectionId }
            val currentAudioTracks = tvPlayerTrackChoices(
                tracks = player.currentTracks,
                trackType = C.TRACK_TYPE_AUDIO,
            )
            textTracks = currentTextTracks
            audioTracks = currentAudioTracks
            selectedSubtitleIsExternal =
                !subtitlesDisabled &&
                    (
                        selectedIndependentSubtitleSelectionId != null ||
                            playerTextTracks
                                .firstOrNull { it.selected }
                                ?.selectionId
                                ?.startsWith("external:") == true
                    )

            val tracksBelongToActiveSource =
                player.currentMediaItem?.localConfiguration?.uri?.toString() == activeSource.url

            if (tracksBelongToActiveSource && !audioPreferenceRestored && currentAudioTracks.isNotEmpty()) {
                val globalSelection = settings.lastAudioSelection()
                val savedSelection = globalSelection ?: settings.audioSelection(mediaKey)
                val savedTrack = tvFindSavedAudioTrack(currentAudioTracks, savedSelection)
                when {
                    savedSelection == TV_AUDIO_AUTO -> {
                        if (globalSelection == null) settings.setLastAudioSelection(TV_AUDIO_AUTO)
                        tvClearTrackOverride(player, C.TRACK_TYPE_AUDIO, disable = false)
                        audioAutomaticSelected = true
                    }
                    savedTrack != null -> {
                        tvApplyTrackChoice(player, C.TRACK_TYPE_AUDIO, savedTrack)
                        audioAutomaticSelected = false
                    }
                    else -> audioAutomaticSelected = true
                }
                audioPreferenceRestored = true
            }

            if (tracksBelongToActiveSource && !subtitlePreferenceRestored && currentTextTracks.isNotEmpty()) {
                val globalSelection = settings.lastSubtitleSelection()
                val contentSelection = settings.subtitleSelection(mediaKey)
                val savedSelection = contentSelection ?: globalSelection
                val savedLanguage = (globalSelection ?: contentSelection)
                    ?.takeIf { it.startsWith(TV_SUBTITLE_LANGUAGE_PREFIX) }
                    ?.removePrefix(TV_SUBTITLE_LANGUAGE_PREFIX)
                val savedTrack = contentSelection
                    ?.let { selectionId -> currentTextTracks.firstOrNull { it.selectionId == selectionId } }
                    ?: savedLanguage?.let { language ->
                        currentTextTracks.firstOrNull {
                            tvCanonicalLanguage(it.language) == tvCanonicalLanguage(language)
                        }
                    }
                    ?: currentTextTracks.firstOrNull { it.selectionId == savedSelection }

                when {
                    savedSelection == TV_SUBTITLE_OFF -> {
                        selectedIndependentSubtitleSelectionId = null
                        tvClearTrackOverride(player, C.TRACK_TYPE_TEXT, disable = true)
                        subtitlesDisabled = true
                        selectedSubtitleIsExternal = false
                    }
                    savedTrack != null -> {
                        selectSubtitleChoiceForPlayer(savedTrack)
                    }
                    savedSelection == null -> {
                        subtitlesDisabled = !settings.subtitlesOnByDefault()
                        val preferredLanguage =
                            settings.preferredSubtitleLanguage().languageCode
                        val preferredIndependent =
                            if (
                                !subtitlesDisabled &&
                                settings.autoSelectPreferredSubtitle() &&
                                !settings.embeddedSubtitlePriority() &&
                                currentTextTracks.none { it.selected }
                            ) {
                                independentTextTracks.firstOrNull { track ->
                                    tvCanonicalLanguage(track.language) ==
                                        tvCanonicalLanguage(preferredLanguage)
                                }
                            } else {
                                null
                            }
                        if (preferredIndependent != null && independentSubtitleTracks.none { track ->
                                PlayerTrackPolicy.externalSubtitleSelectionId(track) ==
                                    preferredIndependent.selectionId &&
                                    PlayerTrackPolicy.isOnDemandTranslation(track)
                            }) {
                            selectSubtitleChoiceForPlayer(preferredIndependent)
                        }
                    }
                }
                subtitlePreferenceRestored = true
            }

            delay(400)
        }
    }

    LaunchedEffect(player, sleepTimerDeadlineMs) {
        val deadline = sleepTimerDeadlineMs ?: return@LaunchedEffect
        while (true) {
            val remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            sleepTimerRemainingSeconds = (remainingMs + 999L) / 1_000L
            if (remainingMs <= 0L) {
                player.pause()
                playing = false
                sleepTimerOption = TvPlayerSleepTimerOption.OFF
                sleepTimerDeadlineMs = null
                sleepTimerRemainingSeconds = null
                controlsVisible = true
                break
            }
            delay(minOf(1_000L, remainingMs))
        }
    }

    LaunchedEffect(ended, sleepTimerOption) {
        if (!ended || sleepTimerOption != TvPlayerSleepTimerOption.END_OF_EPISODE) {
            return@LaunchedEffect
        }
        autoNextCancelled = true
        nextCountdown = 0
        sleepTimerOption = TvPlayerSleepTimerOption.OFF
        sleepTimerDeadlineMs = null
        sleepTimerRemainingSeconds = null
        controlsVisible = true
    }

    LaunchedEffect(player, mediaKey) {
        while (true) {
            delay(10_000)
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            val currentDuration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            if (currentPosition > 0L) {
                runtime.playbackStore.savePositionMs(
                    mediaKey = mediaKey,
                    positionMs = currentPosition,
                    durationMs = currentDuration,
                )
            }
        }
    }

    LaunchedEffect(activePanel, controlsVisible, restorePanelFocus) {
        val panel = restorePanelFocus ?: return@LaunchedEffect
        if (activePanel != TvPlayerPanel.NONE || !controlsVisible) return@LaunchedEffect
        val requester = when (panel) {
            TvPlayerPanel.SUBTITLES -> subtitlesRequester
            TvPlayerPanel.AUDIO -> audioRequester
            TvPlayerPanel.SOURCES -> sourcesRequester
            TvPlayerPanel.EPISODES -> episodesRequester
            TvPlayerPanel.MORE -> moreRequester
            TvPlayerPanel.NONE -> progressRequester
        }
        val restored = requestFocusNow(requester)
        if (!restored && requester != progressRequester) {
            requestFocusNow(progressRequester)
        }
        restorePanelFocus = null
    }

    LaunchedEffect(ended, nextEpisode?.id, autoPlayNextEpisode, autoNextCancelled, sleepTimerOption) {
        if (
            !ended ||
            nextEpisode == null ||
            !autoPlayNextEpisode ||
            autoNextCancelled ||
            sleepTimerOption == TvPlayerSleepTimerOption.END_OF_EPISODE
        ) {
            nextCountdown = 0
            return@LaunchedEffect
        }
        for (remaining in 8 downTo 1) {
            nextCountdown = remaining
            delay(1_000)
            if (player.playbackState != Player.STATE_ENDED || autoNextCancelled) {
                nextCountdown = 0
                return@LaunchedEffect
            }
        }
        nextCountdown = 0
        saveProgress()
        onPlayNextEpisode(nextEpisode)
    }

    LaunchedEffect(playbackError) {
        if (playbackError == null) return@LaunchedEffect
        controlsVisible = true
        if (!requestFocusNow(errorRequester)) {
            requestFocusNow(progressRequester)
        }
    }

    LaunchedEffect(ended, activePanel, playbackError) {
        if (!ended) {
            endedFocusAssigned = false
            return@LaunchedEffect
        }
        if (
            endedFocusAssigned ||
            activePanel != TvPlayerPanel.NONE ||
            playbackError != null
        ) {
            return@LaunchedEffect
        }
        controlsVisible = true
        endedFocusAssigned = requestFocusNow(progressRequester)
    }

    LaunchedEffect(nextCountdown, ended, activePanel, playbackError) {
        if (
            ended &&
            nextCountdown == 8 &&
            activePanel == TvPlayerPanel.NONE &&
            playbackError == null
        ) {
            controlsVisible = true
            if (!requestFocusNow(nextContextRequester)) {
                requestFocusNow(progressRequester)
            }
        }
    }

    LaunchedEffect(controlsVisible, activePanel, interactionToken, playing) {
        if (controlsVisible && activePanel == TvPlayerPanel.NONE && playing) {
            val token = interactionToken
            delay(4_500)
            if (token == interactionToken && activePanel == TvPlayerPanel.NONE) {
                controlsVisible = false
                requestFocusReliably(rootRequester)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching { saveProgress() }
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (
                    controlsVisible || activePanel != TvPlayerPanel.NONE
                ) {
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> noteInteraction()
                    }
                }

                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        togglePlayback()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        playing = true
                        noteInteraction()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        playing = false
                        noteInteraction()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(10_000L)
                        true
                    }
                    else -> {
                        if (activePanel != TvPlayerPanel.NONE || controlsVisible) {
                            false
                        } else {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    requestControlFocus(
                                        if (playbackError != null) errorRequester else progressRequester
                                    )
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    requestControlFocus(progressRequester)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    when {
                                        playbackError != null -> requestControlFocus(errorRequester)
                                        activeSkip != null -> requestControlFocus(skipRequester)
                                        nextCountdown > 0 && nextEpisode != null -> requestControlFocus(nextContextRequester)
                                        else -> requestControlFocus(restartRequester)
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    val bottomRequester = when {
                                        hasSubtitleControl -> subtitlesRequester
                                        hasAudioControl -> audioRequester
                                        hasSourcesControl -> sourcesRequester
                                        hasEpisodesControl -> episodesRequester
                                        else -> progressRequester
                                    }
                                    requestControlFocus(bottomRequester)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }
            }
            .focusable(
                enabled = !controlsVisible && activePanel == TvPlayerPanel.NONE,
            ),
    ) {
        val exoPlayer = player
        val appliedSubtitleStyle = CaptionStyleCompat(
            subtitleStyle.textColor,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            if (subtitleStyle.outlineEnabled) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
            subtitleStyle.outlineColor,
            if (subtitleStyle.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
        )
        val resizeMode = when (videoFit) {
            PlayerVideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            // A TV-style Fill should crop while preserving the source aspect ratio.
            // Media3 RESIZE_MODE_FILL stretches the picture, so use ZOOM instead.
            PlayerVideoFit.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            PlayerVideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        val baseSubtitleBottomPaddingFraction = subtitleStyle.bottomPaddingPercent / 100f
        val subtitleBottomPaddingFraction = if (controlsVisible && activePanel == TvPlayerPanel.NONE) {
            maxOf(baseSubtitleBottomPaddingFraction, 0.18f)
        } else {
            baseSubtitleBottomPaddingFraction
        }

        var nativePlayerView by remember(exoPlayer) {
            mutableStateOf<PlayerView?>(null)
        }
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                    this.player = exoPlayer
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = resizeMode
                    subtitleView?.setApplyEmbeddedStyles(false)
                    subtitleView?.setApplyEmbeddedFontSizes(false)
                    subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleStyle.fontSizeSp.toFloat())
                    subtitleView?.setStyle(appliedSubtitleStyle)
                    subtitleView?.setBottomPaddingFraction(subtitleBottomPaddingFraction)
                    nativePlayerView = this
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) view.player = exoPlayer
                if (nativePlayerView !== view) nativePlayerView = view
                view.isFocusable = false
                view.isFocusableInTouchMode = false
                view.keepScreenOn = true
                view.resizeMode = resizeMode
                view.subtitleView?.setApplyEmbeddedStyles(false)
                view.subtitleView?.setApplyEmbeddedFontSizes(false)
                view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleStyle.fontSizeSp.toFloat())
                view.subtitleView?.setStyle(appliedSubtitleStyle)
                view.subtitleView?.setBottomPaddingFraction(subtitleBottomPaddingFraction)
            },
            modifier = Modifier.fillMaxSize(),
        )

        TvBindIndependentSubtitleCues(
            playerView = nativePlayerView,
            player = exoPlayer,
            cueChannel = independentSubtitleCueChannel,
            delayMs = subtitleDelayMs,
            visible =
                !subtitlesDisabled &&
                    selectedIndependentSubtitleSelectionId != null,
        )

        val orderedEpisodes = remember(media.episodes) {
            media.episodes.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
        }
        val panelOptions = when (activePanel) {
            TvPlayerPanel.SUBTITLES, TvPlayerPanel.AUDIO -> emptyList()
            TvPlayerPanel.SOURCES -> playableSources.map { item ->
                TvPlayerOption(
                    key = item.url.orEmpty(),
                    title = item.providerName,
                    meta = sourceTechnicalLine(item) ?: item.name,
                    selected = item.url == activeSource.url,
                )
            }
            TvPlayerPanel.EPISODES -> orderedEpisodes.map { item ->
                TvPlayerOption(
                    key = item.id,
                    title = item.title.ifBlank { "Episode ${item.episode}" },
                    meta = "S${item.season}E${item.episode}" + item.released?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty(),
                    selected = episode?.let { current ->
                        current.id == item.id || (current.season == item.season && current.episode == item.episode)
                    } == true,
                )
            }
            TvPlayerPanel.MORE -> emptyList()
            TvPlayerPanel.NONE -> emptyList()
        }

        VueoPlayerPresentation(
            media = media,
            episode = episode,
            activeSource = activeSource,
            controlsVisible = controlsVisible,
            activePanel = activePanel,
            playing = playing,
            positionMs = positionMs,
            durationMs = durationMs,
            nextEpisode = nextEpisode,
            activeSkip = activeSkip,
            nextCountdown = nextCountdown,
            contentWarnings = contentWarnings,
            warningVisible = warningVisible,
            onWarningComplete = { warningVisible = false },
            playbackError = playbackError,
            panelOptions = panelOptions,
            episodes = orderedEpisodes,
            hasSubtitles = hasSubtitleControl,
            hasAudio = hasAudioControl,
            hasSources = hasSourcesControl,
            hasEpisodes = hasEpisodesControl,
            restartRequester = restartRequester,
            progressRequester = progressRequester,
            nextRequester = nextRequester,
            subtitlesRequester = subtitlesRequester,
            audioRequester = audioRequester,
            sourcesRequester = sourcesRequester,
            episodesRequester = episodesRequester,
            moreRequester = moreRequester,
            skipRequester = skipRequester,
            nextContextRequester = nextContextRequester,
            errorRequester = errorRequester,
            onInteraction = ::noteInteraction,
            onPlayPause = ::togglePlayback,
            onRetryPlayback = {
                saveProgress()
                sourceRecoverySession.allowRetry(activeSource.toSourceCandidateForPlayer())
                resumeTargetMs = player.currentPosition.coerceAtLeast(positionMs).coerceAtLeast(0L)
                playbackError = null
                recoveryInProgress = false
                hasRenderedFirstFrame = false
                isBuffering = false
                retryGeneration += 1
                player.prepare()
                player.play()
                requestControlFocus(progressRequester)
            },
            onRestart = {
                autoNextCancelled = false
                player.seekTo(0L)
                positionMs = 0L
                if (!player.isPlaying) player.play()
                playing = true
                noteInteraction()
            },
            onSeekBy = ::seekBy,
            onNext = {
                nextEpisode?.let {
                    saveProgress()
                    onPlayNextEpisode(it)
                }
            },
            onOpenPanel = { panel ->
                activePanel = panel
                noteInteraction()
            },
            onDismissPanel = { closePanel() },
            onSkip = { segment ->
                player.seekTo(segment.endMs)
                positionMs = segment.endMs
                requestControlFocus(progressRequester)
            },
            onPlayEpisode = { target ->
                val isCurrent = episode?.let { current ->
                    current.id == target.id || (current.season == target.season && current.episode == target.episode)
                } == true
                if (isCurrent) closePanel()
                else {
                    saveProgress()
                    onPlayNextEpisode(target)
                }
            },
            onPanelSelected = { option ->
                when (activePanel) {
                    TvPlayerPanel.SUBTITLES, TvPlayerPanel.AUDIO -> Unit
                    TvPlayerPanel.SOURCES -> {
                        val target = playableSources.firstOrNull { it.url == option.key }
                        if (target != null) {
                            if (target.url != activeSource.url) {
                                resumeTargetMs = player.currentPosition.coerceAtLeast(0L)
                                saveProgress()
                                sourceRecoverySession.allowRetry(target.toSourceCandidateForPlayer())
                                activeSource = target
                            }
                            closePanel()
                        }
                    }
                    TvPlayerPanel.EPISODES -> {
                        val target = orderedEpisodes.firstOrNull { it.id == option.key }
                        if (target != null) {
                            val isCurrent = episode?.let { current ->
                                current.id == target.id || (current.season == target.season && current.episode == target.episode)
                            } == true
                            if (isCurrent) closePanel()
                            else {
                                saveProgress()
                                onPlayNextEpisode(target)
                            }
                        }
                    }
                    TvPlayerPanel.MORE -> Unit
                    TvPlayerPanel.NONE -> Unit
                }
            },
        )

        AnimatedVisibility(
            visible = activePanel == TvPlayerPanel.SUBTITLES,
            enter = fadeIn(tween(TvMotion.ELEMENT_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            VueoPlayerSubtitleWorkspace(
                tracks = textTracks,
                subtitlesDisabled = subtitlesDisabled,
                entryFocusRequester = subtitleWorkspaceRequester,
                preferredLanguageCode = settings.preferredSubtitleLanguage().languageCode,
                secondaryLanguageCode = settings.secondarySubtitleLanguage().languageCode,
                preferredLanguageOnly =
                    settings.subtitleVisibility() == SubtitleVisibility.PREFERRED_ONLY,
                subtitleDelayMs = subtitleDelayMs,
                style = subtitleStyle,
                loadingSelectionId = subtitleLoadingSelectionId,
                loadError = subtitleLoadError,
                onInteraction = ::noteInteraction,
                onDisable = {
                    selectedIndependentSubtitleSelectionId = null
                    independentSubtitleCueChannel.cues = emptyList()
                    tvClearTrackOverride(player, C.TRACK_TYPE_TEXT, disable = true)
                    textTracks = textTracks.map { it.copy(selected = false) }
                    subtitlesDisabled = true
                    selectedSubtitleIsExternal = false
                    settings.setSubtitleSelection(mediaKey, TV_SUBTITLE_OFF)
                    settings.setLastSubtitleSelection(TV_SUBTITLE_OFF)
                },
                onSelect = { choice ->
                    selectSubtitleChoiceForPlayer(choice)
                    settings.setSubtitleSelection(mediaKey, choice.selectionId)
                    settings.setLastSubtitleSelection(
                        PlayerTrackPolicy.subtitleLanguageSelectionId(choice.language)
                    )
                },
                onSubtitleDelayChange = { updated ->
                    subtitleDelayMs = updated.coerceIn(-60_000, 60_000)
                    settings.setSubtitleDelayMs(mediaKey, subtitleDelayMs)
                },
                onStyleChange = { updated ->
                    subtitleStyle = updated
                    settings.setSubtitleFontSizeSp(updated.fontSizeSp)
                    settings.setSubtitleBold(updated.bold)
                    settings.setSubtitleTextColor(updated.textColor)
                    settings.setSubtitleTextOpacityPercent(alphaPercent(updated.textColor))
                    settings.setSubtitleOutlineEnabled(updated.outlineEnabled)
                    settings.setSubtitleOutlineColor(updated.outlineColor)
                    settings.setSubtitleBottomPaddingPercent(updated.bottomPaddingPercent)
                },
            )
        }

        AnimatedVisibility(
            visible = activePanel == TvPlayerPanel.AUDIO,
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
        ) {
            VueoPlayerAudioWorkspace(
                tracks = audioTracks,
                automaticSelected = audioAutomaticSelected,
                activeSourceLabel = activeSource.audio,
                onInteraction = ::noteInteraction,
                onAutomatic = {
                    tvClearTrackOverride(player, C.TRACK_TYPE_AUDIO, disable = false)
                    audioAutomaticSelected = true
                    settings.setAudioSelection(mediaKey, TV_AUDIO_AUTO)
                    settings.setLastAudioSelection(TV_AUDIO_AUTO)
                    closePanel()
                },
                onSelect = { choice ->
                    tvApplyTrackChoice(player, C.TRACK_TYPE_AUDIO, choice)
                    audioAutomaticSelected = false
                    settings.setAudioSelection(mediaKey, choice.selectionId)
                    settings.setLastAudioSelection(choice.selectionId)
                    closePanel()
                },
            )
        }


        AnimatedVisibility(
            visible = activePanel == TvPlayerPanel.MORE,
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
        ) {
            VueoPlayerMoreWorkspace(
                playbackSpeed = playbackSpeed,
                videoFit = videoFit,
                sleepTimer = sleepTimerOption,
                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                autoPlayNextEpisode = autoPlayNextEpisode,
                skipSegmentsEnabled = skipSegmentsEnabled,
                contentWarningsEnabled = contentWarningsEnabled,
                onInteraction = ::noteInteraction,
                onPlaybackSpeedChange = { speed ->
                    playbackSpeed = speed
                    player.setPlaybackSpeed(speed)
                    settings.setPlayerPlaybackSpeed(speed)
                },
                onVideoFitChange = { fit ->
                    videoFit = fit
                    settings.setPlayerVideoFit(fit)
                },
                onSleepTimerChange = { option ->
                    sleepTimerOption = option
                    sleepTimerDeadlineMs = option.minutes?.let { minutes ->
                        SystemClock.elapsedRealtime() + minutes * 60_000L
                    }
                    sleepTimerRemainingSeconds = option.minutes?.let { it * 60L }
                    noteInteraction()
                },
                onAutoPlayNextEpisodeChange = { enabled ->
                    autoPlayNextEpisode = enabled
                    settings.setAutoPlayNextEpisodeEnabled(enabled)
                    if (!enabled) nextCountdown = 0
                    noteInteraction()
                },
                onSkipSegmentsChange = { enabled ->
                    skipSegmentsEnabled = enabled
                    settings.setSkipSegmentsEnabled(enabled)
                    noteInteraction()
                },
                onContentWarningsChange = { enabled ->
                    contentWarningsEnabled = enabled
                    settings.setContentWarningsEnabled(enabled)
                    if (!enabled) warningVisible = false
                    noteInteraction()
                },
                onReset = {
                    playbackSpeed = 1f
                    player.setPlaybackSpeed(1f)
                    settings.setPlayerPlaybackSpeed(1f)
                    videoFit = PlayerVideoFit.FIT
                    settings.setPlayerVideoFit(PlayerVideoFit.FIT)
                    sleepTimerOption = TvPlayerSleepTimerOption.OFF
                    sleepTimerDeadlineMs = null
                    sleepTimerRemainingSeconds = null
                    autoPlayNextEpisode = true
                    settings.setAutoPlayNextEpisodeEnabled(true)
                    autoNextCancelled = false
                    skipSegmentsEnabled = true
                    settings.setSkipSegmentsEnabled(true)
                    contentWarningsEnabled = true
                    settings.setContentWarningsEnabled(true)
                    noteInteraction()
                },
            )
        }
    }
}


private fun StreamSource.toSourceCandidateForPlayer(): SourceCandidate =
    SourceCandidate(
        id = buildString {
            append(providerId)
            append(':')
            append(url ?: infoHash ?: name)
            fileIndex?.let {
                append(':')
                append(it)
            }
        },
        name = name,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIndex,
        quality = quality,
        codec = codec,
        hdr = hdr,
        audio = audio,
        language = language,
        sizeBytes = sizeBytes,
        headers = headers,
        rankBoost = rankBoost,
        providerId = providerId,
        providerName = providerName,
    )

private fun buildMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleTrack>,
    preferredLanguages: List<String>,
    subtitlesOnByDefault: Boolean,
    autoSelectPreferred: Boolean,
    preferEmbedded: Boolean,
): MediaItem {
    val normalizedPreferredLanguages = preferredLanguages
        .map(::tvCanonicalLanguage)
        .distinct()

    val ordered = subtitles
        .filter { it.url.startsWith("https://") }
        .distinctBy(PlayerTrackPolicy::externalSubtitleKey)
        .sortedBy { subtitle ->
            normalizedPreferredLanguages.indexOf(tvCanonicalLanguage(subtitle.language))
                .let { if (it < 0) Int.MAX_VALUE else it }
        }

    val configurations = ordered.mapIndexed { index, subtitle ->
        val selectionId = tvExternalSubtitleSelectionId(subtitle)
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setId(selectionId)
            .setLanguage(tvCanonicalLanguage(subtitle.language).takeUnless { it == "und" })
            .setLabel(tvExternalSubtitleLabel(subtitle))
            .setMimeType(subtitleMimeType(subtitle.url))
            .setSelectionFlags(
                if (subtitlesOnByDefault && autoSelectPreferred && !preferEmbedded && (
                    normalizedPreferredLanguages.indexOf(tvCanonicalLanguage(subtitle.language)) == 0 ||
                        (normalizedPreferredLanguages.isEmpty() && index == 0)
                )) C.SELECTION_FLAG_DEFAULT else 0
            )
            .build()
    }

    return MediaItem.Builder()
        .setUri(Uri.parse(sourceUrl))
        .setSubtitleConfigurations(configurations)
        .build()
}

private fun sourceTechnicalLine(source: StreamSource): String? =
    listOfNotNull(
        source.quality?.takeIf { it.isNotBlank() },
        source.codec?.takeIf { it.isNotBlank() },
        source.hdr?.takeIf { it.isNotBlank() },
        source.audio?.takeIf { it.isNotBlank() },
    ).distinct().takeIf { it.isNotEmpty() }?.joinToString("  •  ")

private fun nextEpisode(episodes: List<EpisodeItem>, current: EpisodeItem?): EpisodeItem? {
    current ?: return null
    val ordered = episodes.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
    val index = ordered.indexOfFirst { it.id == current.id || (it.season == current.season && it.episode == current.episode) }
    return ordered.getOrNull(index + 1)
}

private fun withAlpha(argb: Int, percent: Int): Int {
    val alpha = (255 * percent.coerceIn(0, 100) / 100) shl 24
    return (argb and 0x00FFFFFF) or alpha
}

private fun alphaPercent(argb: Int): Int =
    (((argb ushr 24) * 100) + 127) / 255

private fun subtitleMimeType(url: String): String =
    when (url.substringBefore("?").substringAfterLast(".", "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

private fun playbackTitle(media: VueoMediaItem, episode: EpisodeItem?): String =
    if (episode == null) media.name
    else "${media.name}  •  S${episode.season}E${episode.episode}  •  ${episode.title}"

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
