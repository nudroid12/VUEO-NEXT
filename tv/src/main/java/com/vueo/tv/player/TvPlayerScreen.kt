package com.vueo.tv.player

import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem as VueoMediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.PlayerSkipKind
import com.vueo.shared.core.player.PlayerSkipRepository
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.delay

internal enum class TvPlayerPanel {
    NONE,
    SUBTITLES,
    AUDIO,
    SOURCES,
    EPISODES,
    MORE,
}

internal enum class TvPlayerPanelPlacement {
    LEFT_OVERLAY,
    RIGHT_PANEL,
}

internal data class TvPlayerOption(
    val key: String,
    val title: String,
    val meta: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

internal data class TvAudioLanguageOption(
    val code: String,
    val label: String,
    val selected: Boolean,
)

@Composable
fun TvPlayerScreen(
    runtime: TvRuntime,
    media: VueoMediaItem,
    episode: EpisodeItem?,
    bundle: TvSourceBundle,
    source: StreamSource,
    initialPositionMs: Long,
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit,
    onPlayNextEpisode: (EpisodeItem) -> Unit = {},
) {
    val context = LocalContext.current
    val rootRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val subtitlesRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }
    val moreRequester = remember { FocusRequester() }
    val skipRequester = remember { FocusRequester() }
    val nextContextRequester = remember { FocusRequester() }

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
            .distinctBy { it.url }
    }
    var activeSource by remember(bundle.videoId, source.url) { mutableStateOf(source) }
    var resumeTargetMs by remember(bundle.videoId) { mutableLongStateOf(startPosition) }
    var recoveryAttempts by remember(bundle.videoId) { mutableIntStateOf(0) }
    var playbackError by remember(bundle.videoId) { mutableStateOf<String?>(null) }

    val httpFactory = remember(bundle.videoId) {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VUEO-TV")
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember(bundle.videoId) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var activePanel by remember { mutableStateOf(TvPlayerPanel.NONE) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(startPosition) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(0) }
    var skipSegments by remember(media.id, episode?.id) { mutableStateOf<List<PlayerSkipSegment>>(emptyList()) }
    var warningVisible by remember(media.id) {
        mutableStateOf(settings.contentWarningsEnabled() && !media.certification.isNullOrBlank())
    }
    var selectedSubtitleKey by remember(bundle.videoId) {
        mutableStateOf(if (settings.subtitlesOnByDefault()) "auto" else "off")
    }
    var playbackSpeed by remember(bundle.videoId) { mutableStateOf(settings.playerPlaybackSpeed()) }
    var videoFit by remember(bundle.videoId) { mutableStateOf(settings.playerVideoFit()) }
    var audioLanguages by remember(bundle.videoId) { mutableStateOf<List<TvAudioLanguageOption>>(emptyList()) }

    val nextEpisode = remember(media.episodes, episode?.id) { nextEpisode(media.episodes, episode) }
    val activeSkip = remember(positionMs, skipSegments) {
        skipSegments.firstOrNull { segment ->
            positionMs in segment.startMs until segment.endMs && segment.endMs - positionMs > 800L
        }
    }

    fun noteInteraction() {
        interactionToken += 1
    }

    fun requestControlFocus(requester: FocusRequester = playPauseRequester) {
        controlsVisible = true
        noteInteraction()
        runCatching { requester.requestFocus() }
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
        val restoreRequester = when (activePanel) {
            TvPlayerPanel.SUBTITLES -> subtitlesRequester
            TvPlayerPanel.AUDIO -> audioRequester
            TvPlayerPanel.SOURCES -> sourcesRequester
            TvPlayerPanel.EPISODES -> episodesRequester
            TvPlayerPanel.MORE -> moreRequester
            TvPlayerPanel.NONE -> playPauseRequester
        }
        activePanel = TvPlayerPanel.NONE
        noteInteraction()
        if (restoreFocus) runCatching { restoreRequester.requestFocus() }
    }

    fun exitPlayer() {
        saveProgress()
        onBack()
    }

    BackHandler {
        when {
            activePanel != TvPlayerPanel.NONE -> closePanel()
            controlsVisible -> {
                controlsVisible = false
                runCatching { rootRequester.requestFocus() }
            }
            else -> exitPlayer()
        }
    }

    LaunchedEffect(activeSource.url, bundle.videoId) {
        val url = activeSource.url ?: return@LaunchedEffect
        httpFactory.setDefaultRequestProperties(activeSource.headers)
        playbackError = null

        val primaryLanguage = settings.preferredSubtitleLanguage().languageCode
        val secondaryLanguage = settings.secondarySubtitleLanguage().languageCode
        val languages = listOfNotNull(primaryLanguage, secondaryLanguage).distinct()

        player.setMediaItem(
            buildMediaItem(
                sourceUrl = url,
                subtitles = bundle.subtitles,
                preferredLanguages = languages,
                preferEmbedded = settings.embeddedSubtitlePriority(),
            ),
            resumeTargetMs.coerceAtLeast(0L),
        )
        var params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesOnByDefault())
        if (settings.autoSelectPreferredSubtitle() && languages.isNotEmpty()) {
            params = params.setPreferredTextLanguages(*languages.toTypedArray())
        }
        player.trackSelectionParameters = params.build()
        player.setPlaybackSpeed(playbackSpeed)
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player, activeSource.url, settings.autoSourceRecoveryEnabled()) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!settings.autoSourceRecoveryEnabled() || recoveryAttempts >= 2) {
                    playbackError = error.message ?: "Playback failed."
                    return
                }
                val currentIndex = playableSources.indexOfFirst { it.url == activeSource.url }
                val alternative = playableSources.drop((currentIndex + 1).coerceAtLeast(0))
                    .firstOrNull { it.url != activeSource.url }
                if (alternative == null) {
                    playbackError = error.message ?: "No recovery source available."
                    return
                }
                resumeTargetMs = player.currentPosition.coerceAtLeast(0L)
                recoveryAttempts += 1
                activeSource = alternative
                requestControlFocus(playPauseRequester)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(media.id, episode?.season, episode?.episode, settings.skipSegmentsEnabled()) {
        skipSegments = emptyList()
        if (
            settings.skipSegmentsEnabled() &&
            episode != null &&
            media.id.startsWith("tt", ignoreCase = true)
        ) {
            skipSegments = runCatching {
                PlayerSkipRepository.segments(media.id, episode.season, episode.episode)
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(warningVisible) {
        if (warningVisible) {
            delay(5_000)
            warningVisible = false
        }
    }

    LaunchedEffect(player) {
        runCatching { playPauseRequester.requestFocus() }
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            ended = player.playbackState == Player.STATE_ENDED
            audioLanguages = currentAudioLanguages(player)
            delay(400)
        }
    }

    LaunchedEffect(ended, nextEpisode?.id, settings.autoPlayNextEpisodeEnabled()) {
        if (!ended || nextEpisode == null || !settings.autoPlayNextEpisodeEnabled()) {
            nextCountdown = 0
            return@LaunchedEffect
        }
        for (remaining in 8 downTo 1) {
            nextCountdown = remaining
            delay(1_000)
            if (player.playbackState != Player.STATE_ENDED) {
                nextCountdown = 0
                return@LaunchedEffect
            }
        }
        nextCountdown = 0
        saveProgress()
        onPlayNextEpisode(nextEpisode)
    }

    LaunchedEffect(controlsVisible, activePanel, interactionToken, playing) {
        if (controlsVisible && activePanel == TvPlayerPanel.NONE && playing) {
            val token = interactionToken
            delay(4_500)
            if (token == interactionToken && activePanel == TvPlayerPanel.NONE) {
                controlsVisible = false
                runCatching { rootRequester.requestFocus() }
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
                                    togglePlayback()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    seekBy(-10_000L)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    seekBy(10_000L)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (activeSkip != null) requestControlFocus(skipRequester)
                                    else if (nextCountdown > 0 && nextEpisode != null) requestControlFocus(nextContextRequester)
                                    else if (nextEpisode != null) requestControlFocus(nextRequester)
                                    else requestControlFocus(moreRequester)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    requestControlFocus(playPauseRequester)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }
            }
            .focusable(),
    ) {
        val exoPlayer = player
        val subtitleFontSize = settings.subtitleFontSizeSp().toFloat()
        val subtitleForeground = withAlpha(settings.subtitleTextColor(), settings.subtitleTextOpacityPercent())
        val subtitleEdgeType = if (settings.subtitleOutlineEnabled()) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE
        val subtitleStyle = CaptionStyleCompat(
            subtitleForeground,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            subtitleEdgeType,
            settings.subtitleOutlineColor(),
            if (settings.subtitleBold()) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
        )
        val resizeMode = when (videoFit) {
            PlayerVideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerVideoFit.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerVideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    this.player = exoPlayer
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = resizeMode
                    subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSize)
                    subtitleView?.setStyle(subtitleStyle)
                    subtitleView?.setBottomPaddingFraction(settings.subtitleBottomPaddingPercent() / 100f)
                }
            },
            update = {
                it.player = exoPlayer
                it.resizeMode = resizeMode
                it.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSize)
                it.subtitleView?.setStyle(subtitleStyle)
                it.subtitleView?.setBottomPaddingFraction(settings.subtitleBottomPaddingPercent() / 100f)
            },
            modifier = Modifier.fillMaxSize(),
        )

        val orderedEpisodes = remember(media.episodes) {
            media.episodes.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
        }
        val panelOptions = when (activePanel) {
            TvPlayerPanel.SUBTITLES -> buildList {
                add(TvPlayerOption("off", "Off", "Disable subtitles", selectedSubtitleKey == "off"))
                add(TvPlayerOption("auto", "Auto", "Preferred subtitle languages", selectedSubtitleKey == "auto"))
                bundle.subtitles
                    .filter { it.url.startsWith("https://") }
                    .distinctBy { it.id.ifBlank { it.url } }
                    .forEach { track ->
                        add(
                            TvPlayerOption(
                                key = track.id.ifBlank { track.url },
                                title = track.name?.takeIf { it.isNotBlank() } ?: friendlyLanguage(track.language),
                                meta = listOf(friendlyLanguage(track.language), track.providerName)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString("  •  "),
                                selected = selectedSubtitleKey == track.id.ifBlank { it.url },
                            )
                        )
                    }
            }
            TvPlayerPanel.AUDIO -> if (audioLanguages.isEmpty()) {
                listOf(
                    TvPlayerOption(
                        key = "stream-default",
                        title = "Stream default",
                        meta = activeSource.audio ?: "No alternate audio tracks exposed",
                        selected = true,
                        enabled = false,
                    )
                )
            } else {
                audioLanguages.map { audio ->
                    TvPlayerOption(
                        key = audio.code,
                        title = audio.label,
                        meta = "Audio language",
                        selected = audio.selected,
                    )
                }
            }
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
            TvPlayerPanel.MORE -> buildList {
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    add(
                        TvPlayerOption(
                            key = "speed:$speed",
                            title = "Playback speed",
                            meta = "${formatSpeed(speed)}x",
                            selected = playbackSpeed == speed,
                        )
                    )
                }
                PlayerVideoFit.entries.forEach { fit ->
                    add(
                        TvPlayerOption(
                            key = "fit:${fit.name}",
                            title = "Video fit",
                            meta = fit.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = videoFit == fit,
                        )
                    )
                }
            }
            TvPlayerPanel.NONE -> emptyList()
        }

        NuvioPlayerPresentation(
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
            warningVisible = warningVisible,
            playbackError = playbackError,
            panelOptions = panelOptions,
            episodes = orderedEpisodes,
            hasSubtitles = bundle.subtitles.isNotEmpty(),
            hasAudio = audioLanguages.isNotEmpty() || !activeSource.audio.isNullOrBlank(),
            hasSources = playableSources.isNotEmpty(),
            hasEpisodes = media.episodes.isNotEmpty(),
            playPauseRequester = playPauseRequester,
            progressRequester = progressRequester,
            nextRequester = nextRequester,
            subtitlesRequester = subtitlesRequester,
            audioRequester = audioRequester,
            sourcesRequester = sourcesRequester,
            episodesRequester = episodesRequester,
            moreRequester = moreRequester,
            skipRequester = skipRequester,
            nextContextRequester = nextContextRequester,
            onInteraction = ::noteInteraction,
            onPlayPause = ::togglePlayback,
            onSeekBy = ::seekBy,
            onHideControls = {
                controlsVisible = false
                runCatching { rootRequester.requestFocus() }
            },
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
                noteInteraction()
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
                    TvPlayerPanel.SUBTITLES -> {
                        when (option.key) {
                            "off" -> player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            "auto" -> {
                                val languages = listOfNotNull(
                                    settings.preferredSubtitleLanguage().languageCode,
                                    settings.secondarySubtitleLanguage().languageCode,
                                ).distinct()
                                var params = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                if (languages.isNotEmpty()) params = params.setPreferredTextLanguages(*languages.toTypedArray())
                                player.trackSelectionParameters = params.build()
                            }
                            else -> {
                                val track = bundle.subtitles.firstOrNull { it.id.ifBlank { it.url } == option.key }
                                if (track != null) {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setPreferredTextLanguage(track.language)
                                        .build()
                                }
                            }
                        }
                        selectedSubtitleKey = option.key
                        closePanel()
                    }
                    TvPlayerPanel.AUDIO -> {
                        if (option.enabled) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setPreferredAudioLanguage(option.key)
                                .build()
                            closePanel()
                        }
                    }
                    TvPlayerPanel.SOURCES -> {
                        val target = playableSources.firstOrNull { it.url == option.key }
                        if (target != null) {
                            if (target.url != activeSource.url) {
                                resumeTargetMs = player.currentPosition.coerceAtLeast(0L)
                                recoveryAttempts = 0
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
                    TvPlayerPanel.MORE -> {
                        when {
                            option.key.startsWith("speed:") -> option.key.substringAfter(':').toFloatOrNull()?.let { speed ->
                                playbackSpeed = speed
                                player.setPlaybackSpeed(speed)
                                settings.setPlayerPlaybackSpeed(speed)
                            }
                            option.key.startsWith("fit:") -> runCatching {
                                PlayerVideoFit.valueOf(option.key.substringAfter(':'))
                            }.getOrNull()?.let { fit ->
                                videoFit = fit
                                settings.setPlayerVideoFit(fit)
                            }
                        }
                        noteInteraction()
                    }
                    TvPlayerPanel.NONE -> Unit
                }
            },
        )
    }
}

private fun buildMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleTrack>,
    preferredLanguages: List<String>,
    preferEmbedded: Boolean,
): MediaItem {
    val ordered = subtitles
        .filter { it.url.startsWith("https://") }
        .distinctBy { it.url }
        .sortedBy { subtitle ->
            val language = subtitle.language.lowercase()
            preferredLanguages.indexOfFirst {
                language == it.lowercase() || language.startsWith("${it.lowercase()}-")
            }.let { if (it < 0) Int.MAX_VALUE else it }
        }

    val configurations = ordered.mapIndexed { index, subtitle ->
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setId(subtitle.id)
            .setLanguage(subtitle.language)
            .setLabel(subtitle.name ?: subtitle.language)
            .setMimeType(subtitleMimeType(subtitle.url))
            .setSelectionFlags(if (!preferEmbedded && index == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }

    return MediaItem.Builder()
        .setUri(Uri.parse(sourceUrl))
        .setSubtitleConfigurations(configurations)
        .build()
}

private fun currentAudioLanguages(player: Player): List<TvAudioLanguageOption> {
    val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val options = mutableListOf<TvAudioLanguageOption>()
    groups.forEach { group ->
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val language = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: continue
            val label = format.label?.takeIf { it.isNotBlank() } ?: friendlyLanguage(language)
            options += TvAudioLanguageOption(
                code = language,
                label = label,
                selected = group.isTrackSelected(index),
            )
        }
    }
    return options.distinctBy { it.code.lowercase() }
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

private fun skipLabel(kind: PlayerSkipKind): String = when (kind) {
    PlayerSkipKind.INTRO -> "Skip Intro"
    PlayerSkipKind.RECAP -> "Skip Recap"
    PlayerSkipKind.ENDING -> "Skip Ending"
}

private fun withAlpha(argb: Int, percent: Int): Int {
    val alpha = (255 * percent.coerceIn(0, 100) / 100) shl 24
    return (argb and 0x00FFFFFF) or alpha
}

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

private fun friendlyLanguage(value: String?): String {
    val code = value?.trim()?.lowercase().orEmpty()
    return when (code.substringBefore('-')) {
        "en" -> "English"
        "ms", "may", "msa" -> "Malay"
        "id", "ind" -> "Indonesian"
        "zh", "chi", "zho" -> "Chinese"
        "ja", "jpn" -> "Japanese"
        "ko", "kor" -> "Korean"
        "th", "tha" -> "Thai"
        "es", "spa" -> "Spanish"
        "fr", "fra", "fre" -> "French"
        "de", "deu", "ger" -> "German"
        "ar", "ara" -> "Arabic"
        "hi", "hin" -> "Hindi"
        "und", "" -> "Unknown"
        else -> value?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')

private fun timeLabel(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
