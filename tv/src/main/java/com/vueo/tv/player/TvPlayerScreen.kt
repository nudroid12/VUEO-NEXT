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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
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

private enum class TvPlayerPanel {
    NONE,
    SUBTITLES,
    AUDIO,
    SOURCES,
    EPISODES,
    MORE,
}

private data class TvPlayerOption(
    val key: String,
    val title: String,
    val meta: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

private data class TvAudioLanguageOption(
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
    val playRequester = remember { FocusRequester() }
    val rewindRequester = remember { FocusRequester() }
    val forwardRequester = remember { FocusRequester() }
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

    fun requestControlFocus(requester: FocusRequester = playRequester) {
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
            TvPlayerPanel.NONE -> playRequester
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
                requestControlFocus(playRequester)
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
        runCatching { playRequester.requestFocus() }
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
                                    requestControlFocus(playRequester)
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
                                    else requestControlFocus(playRequester)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    requestControlFocus(playRequester)
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

        if (controlsVisible || activePanel != TvPlayerPanel.NONE || playbackError != null || activeSkip != null || nextCountdown > 0 || warningVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .50f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = .88f),
                        )
                    )
                )
            )
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(.70f)
                    .padding(start = 44.dp, top = 32.dp),
            ) {
                Text(
                    text = playbackTitle(media, episode),
                    color = TvDesign.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(activeSource.providerName)
                        sourceTechnicalLine(activeSource)?.let { append("  •  $it") }
                        if (recoveryAttempts > 0) append("  •  Recovery $recoveryAttempts/2")
                    },
                    color = TvDesign.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp, vertical = 30.dp),
            ) {
                val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeLabel(positionMs),
                        color = TvDesign.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(TvDesign.White.copy(alpha = .22f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .background(TvDesign.Accent, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = timeLabel(durationMs),
                        color = TvDesign.Muted,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvPlayerControl(
                        icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        label = if (playing) "Pause" else "Play",
                        requester = playRequester,
                        left = moreRequester,
                        right = rewindRequester,
                        up = if (activeSkip != null) skipRequester else if (nextCountdown > 0 && nextEpisode != null) nextContextRequester else FocusRequester.Cancel,
                        onInteraction = ::noteInteraction,
                        onClick = ::togglePlayback,
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.FastRewind,
                        label = "10s",
                        requester = rewindRequester,
                        left = playRequester,
                        right = forwardRequester,
                        onInteraction = ::noteInteraction,
                        onClick = { seekBy(-10_000L) },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.FastForward,
                        label = "10s",
                        requester = forwardRequester,
                        left = rewindRequester,
                        right = nextRequester,
                        onInteraction = ::noteInteraction,
                        onClick = { seekBy(10_000L) },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.SkipNext,
                        label = "Next",
                        requester = nextRequester,
                        left = forwardRequester,
                        right = subtitlesRequester,
                        enabled = nextEpisode != null,
                        onInteraction = ::noteInteraction,
                        onClick = {
                            nextEpisode?.let {
                                saveProgress()
                                onPlayNextEpisode(it)
                            }
                        },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.Subtitles,
                        label = "Subs",
                        requester = subtitlesRequester,
                        left = nextRequester,
                        right = audioRequester,
                        onInteraction = ::noteInteraction,
                        onClick = { activePanel = TvPlayerPanel.SUBTITLES },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.VolumeUp,
                        label = "Audio",
                        requester = audioRequester,
                        left = subtitlesRequester,
                        right = sourcesRequester,
                        onInteraction = ::noteInteraction,
                        onClick = { activePanel = TvPlayerPanel.AUDIO },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.List,
                        label = "Sources",
                        requester = sourcesRequester,
                        left = audioRequester,
                        right = episodesRequester,
                        enabled = playableSources.isNotEmpty(),
                        onInteraction = ::noteInteraction,
                        onClick = { activePanel = TvPlayerPanel.SOURCES },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.List,
                        label = "Episodes",
                        requester = episodesRequester,
                        left = sourcesRequester,
                        right = moreRequester,
                        enabled = media.episodes.isNotEmpty(),
                        onInteraction = ::noteInteraction,
                        onClick = { activePanel = TvPlayerPanel.EPISODES },
                    )
                    TvPlayerControl(
                        icon = Icons.Rounded.MoreHoriz,
                        label = "More",
                        requester = moreRequester,
                        left = episodesRequester,
                        right = playRequester,
                        onInteraction = ::noteInteraction,
                        onClick = { activePanel = TvPlayerPanel.MORE },
                    )
                }
            }
        }

        if (warningVisible) {
            Text(
                text = "Content guidance  •  ${media.certification}",
                color = TvDesign.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 44.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        activeSkip?.let { segment ->
            TvPlayerContextAction(
                label = skipLabel(segment.kind),
                requester = skipRequester,
                down = playRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 44.dp, bottom = 112.dp),
                onInteraction = ::noteInteraction,
                onClick = {
                    player.seekTo(segment.endMs)
                    positionMs = segment.endMs
                },
            )
        }

        if (nextCountdown > 0 && nextEpisode != null) {
            TvPlayerContextAction(
                label = "Next in $nextCountdown  •  ${nextEpisode.title}",
                requester = nextContextRequester,
                down = playRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 44.dp, bottom = if (activeSkip != null) 164.dp else 112.dp),
                onInteraction = ::noteInteraction,
                onClick = {
                    saveProgress()
                    onPlayNextEpisode(nextEpisode)
                },
            )
        }

        playbackError?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFFA0A0),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .84f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        when (activePanel) {
            TvPlayerPanel.SUBTITLES -> {
                val subtitleOptions = buildList {
                    add(TvPlayerOption("off", "Off", "Disable subtitles", selectedSubtitleKey == "off"))
                    add(TvPlayerOption("auto", "Auto", "Use your preferred subtitle languages", selectedSubtitleKey == "auto"))
                    bundle.subtitles
                        .filter { it.url.startsWith("https://") }
                        .distinctBy { it.id.ifBlank { it.url } }
                        .forEach { track ->
                            add(
                                TvPlayerOption(
                                    key = track.id.ifBlank { track.url },
                                    title = track.name?.takeIf { it.isNotBlank() } ?: friendlyLanguage(track.language),
                                    meta = listOf(friendlyLanguage(track.language), track.providerName)
                                        .distinct()
                                        .joinToString("  •  "),
                                    selected = selectedSubtitleKey == track.id.ifBlank { track.url },
                                )
                            )
                        }
                }
                TvPlayerSidePanel(
                    title = "Subtitles",
                    subtitle = "D-pad to choose. Back returns to player controls.",
                    options = subtitleOptions,
                    onInteraction = ::noteInteraction,
                    onSelected = { option ->
                        when (option.key) {
                            "off" -> {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            }
                            "auto" -> {
                                val languages = listOfNotNull(
                                    settings.preferredSubtitleLanguage().languageCode,
                                    settings.secondarySubtitleLanguage().languageCode,
                                ).distinct()
                                var params = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                if (languages.isNotEmpty()) {
                                    params = params.setPreferredTextLanguages(*languages.toTypedArray())
                                }
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
                    },
                )
            }

            TvPlayerPanel.AUDIO -> {
                val options = if (audioLanguages.isEmpty()) {
                    listOf(
                        TvPlayerOption(
                            key = "stream-default",
                            title = "Stream default",
                            meta = activeSource.audio ?: "No alternate audio tracks exposed by this stream",
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
                TvPlayerSidePanel(
                    title = "Audio",
                    subtitle = "Choose an audio language exposed by the current stream.",
                    options = options,
                    onInteraction = ::noteInteraction,
                    onSelected = { option ->
                        if (option.enabled) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setPreferredAudioLanguage(option.key)
                                .build()
                            closePanel()
                        }
                    },
                )
            }

            TvPlayerPanel.SOURCES -> {
                val options = playableSources.map { item ->
                    TvPlayerOption(
                        key = item.url.orEmpty(),
                        title = item.providerName,
                        meta = sourceTechnicalLine(item) ?: item.name,
                        selected = item.url == activeSource.url,
                    )
                }
                TvPlayerSidePanel(
                    title = "Sources",
                    subtitle = "Switch source and continue from your current position.",
                    options = options,
                    onInteraction = ::noteInteraction,
                    onSelected = { option ->
                        val target = playableSources.firstOrNull { it.url == option.key }
                        if (target != null) {
                            if (target.url != activeSource.url) {
                                resumeTargetMs = player.currentPosition.coerceAtLeast(0L)
                                recoveryAttempts = 0
                                activeSource = target
                            }
                            closePanel()
                        }
                    },
                )
            }

            TvPlayerPanel.EPISODES -> {
                val orderedEpisodes = remember(media.episodes) {
                    media.episodes.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
                }
                val options = orderedEpisodes.map { item ->
                    TvPlayerOption(
                        key = item.id,
                        title = "S${item.season}E${item.episode}  ${item.title}",
                        meta = item.released,
                        selected = episode?.let { current ->
                            current.id == item.id || (current.season == item.season && current.episode == item.episode)
                        } == true,
                    )
                }
                TvPlayerSidePanel(
                    title = "Episodes",
                    subtitle = media.name,
                    options = options,
                    onInteraction = ::noteInteraction,
                    onSelected = { option ->
                        val target = orderedEpisodes.firstOrNull { it.id == option.key }
                        if (target != null) {
                            val isCurrent = episode?.let { current ->
                                current.id == target.id || (current.season == target.season && current.episode == target.episode)
                            } == true
                            if (isCurrent) {
                                closePanel()
                            } else {
                                saveProgress()
                                onPlayNextEpisode(target)
                            }
                        }
                    },
                )
            }

            TvPlayerPanel.MORE -> {
                val speedValues = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
                val options = buildList {
                    speedValues.forEach { speed ->
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
                TvPlayerSidePanel(
                    title = "More",
                    subtitle = "Playback and picture options.",
                    options = options,
                    onInteraction = ::noteInteraction,
                    onSelected = { option ->
                        when {
                            option.key.startsWith("speed:") -> {
                                val speed = option.key.substringAfter(':').toFloatOrNull()
                                if (speed != null) {
                                    playbackSpeed = speed
                                    player.setPlaybackSpeed(speed)
                                    settings.setPlayerPlaybackSpeed(speed)
                                }
                            }
                            option.key.startsWith("fit:") -> {
                                val fit = runCatching { PlayerVideoFit.valueOf(option.key.substringAfter(':')) }.getOrNull()
                                if (fit != null) {
                                    videoFit = fit
                                    settings.setPlayerVideoFit(fit)
                                }
                            }
                        }
                        noteInteraction()
                    },
                )
            }

            TvPlayerPanel.NONE -> Unit
        }
    }
}

@Composable
private fun TvPlayerControl(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    left: FocusRequester,
    right: FocusRequester,
    up: FocusRequester = FocusRequester.Cancel,
    enabled: Boolean = true,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(92.dp)
            .focusRequester(requester)
            .focusProperties {
                this.left = left
                this.right = right
                this.up = up
                this.down = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && enabled) onClick()
                true
            }
            .focusable()
            .background(
                when {
                    focused -> TvDesign.White.copy(alpha = .18f)
                    else -> Color.Black.copy(alpha = .36f)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Accent.copy(alpha = .92f) else TvDesign.White.copy(alpha = .10f),
                shape = shape,
            )
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> TvDesign.Dim
                focused -> TvDesign.White
                else -> TvDesign.White.copy(alpha = .86f)
            },
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            color = when {
                !enabled -> TvDesign.Dim
                focused -> TvDesign.White
                else -> TvDesign.Muted
            },
            fontSize = 10.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TvPlayerContextAction(
    label: String,
    requester: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .focusRequester(requester)
            .focusProperties { this.down = down }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp) onClick()
                true
            }
            .focusable()
            .background(
                if (focused) TvDesign.White else Color.Black.copy(alpha = .76f),
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Accent else TvDesign.White.copy(alpha = .14f),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else TvDesign.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvPlayerSidePanel(
    title: String,
    subtitle: String,
    options: List<TvPlayerOption>,
    onInteraction: () -> Unit,
    onSelected: (TvPlayerOption) -> Unit,
) {
    val listState = rememberLazyListState()
    val requesters = remember(options.map { it.key }) {
        List(options.size.coerceAtLeast(1)) { FocusRequester() }
    }

    LaunchedEffect(options) {
        val targetIndex = options.indexOfFirst { it.selected && it.enabled }
            .takeIf { it >= 0 }
            ?: options.indexOfFirst { it.enabled }.coerceAtLeast(0)
        if (options.isNotEmpty()) {
            listState.scrollToItem(targetIndex)
            runCatching { requesters[targetIndex].requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .42f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(430.dp)
                .background(TvDesign.Black.copy(alpha = .97f))
                .border(
                    width = 1.dp,
                    color = TvDesign.White.copy(alpha = .10f),
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                )
                .padding(start = 24.dp, end = 22.dp, top = 34.dp, bottom = 28.dp),
        ) {
            Text(
                text = title,
                color = TvDesign.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))

            if (options.isEmpty()) {
                Text(
                    text = "Nothing available for this stream.",
                    color = TvDesign.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = options,
                        key = { index, option -> "${option.key}:$index" },
                    ) { index, option ->
                        TvPlayerOptionRow(
                            option = option,
                            requester = requesters[index],
                            onInteraction = onInteraction,
                            onClick = { onSelected(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlayerOptionRow(
    option: TvPlayerOption,
    requester: FocusRequester,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(option.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .onPreviewKeyEvent { event ->
                if (!event.isTvActivationKey()) return@onPreviewKeyEvent false
                onInteraction()
                if (event.type == KeyEventType.KeyUp && option.enabled) onClick()
                true
            }
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .focusable()
            .background(
                when {
                    focused -> TvDesign.SurfaceRaised.copy(alpha = .98f)
                    option.selected -> TvDesign.Accent.copy(alpha = .10f)
                    else -> TvDesign.Surface.copy(alpha = .78f)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> TvDesign.Accent.copy(alpha = .92f)
                    option.selected -> TvDesign.Accent.copy(alpha = .34f)
                    else -> TvDesign.White.copy(alpha = .08f)
                },
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    when {
                        option.selected -> TvDesign.Accent
                        focused -> TvDesign.White.copy(alpha = .28f)
                        else -> Color.Transparent
                    },
                    CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = if (option.selected || focused) TvDesign.White.copy(alpha = .48f) else TvDesign.White.copy(alpha = .18f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (option.selected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Color.Black.copy(alpha = .78f), CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                color = if (option.enabled) TvDesign.White else TvDesign.Dim,
                fontSize = 13.sp,
                fontWeight = if (focused || option.selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                Text(
                    text = meta,
                    color = if (option.enabled) TvDesign.Muted else TvDesign.Dim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.selected) {
            Text(
                text = "Active",
                color = TvDesign.Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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
