package com.vueo.tv.player

import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val focusRequester = remember { FocusRequester() }
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

    val playableSources = remember(bundle.sources) { bundle.sources.filter { it.isDirectPlayable } }
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
    var positionMs by remember { mutableLongStateOf(startPosition) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(0) }
    var skipSegments by remember(media.id, episode?.id) { mutableStateOf<List<PlayerSkipSegment>>(emptyList()) }
    var warningVisible by remember(media.id) {
        mutableStateOf(settings.contentWarningsEnabled() && !media.certification.isNullOrBlank())
    }

    val nextEpisode = remember(media.episodes, episode?.id) { nextEpisode(media.episodes, episode) }
    val activeSkip = remember(positionMs, skipSegments) {
        skipSegments.firstOrNull { segment ->
            positionMs in segment.startMs until segment.endMs && segment.endMs - positionMs > 800L
        }
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

    BackHandler {
        saveProgress()
        onBack()
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
        player.setPlaybackSpeed(settings.playerPlaybackSpeed())
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
                controlsVisible = true
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
        runCatching { focusRequester.requestFocus() }
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            ended = player.playbackState == Player.STATE_ENDED
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

    LaunchedEffect(controlsVisible, positionMs) {
        if (controlsVisible) {
            val snapshot = positionMs
            delay(3_200)
            if (positionMs >= snapshot) controlsVisible = false
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
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (player.isPlaying) player.pause() else player.play()
                        playing = player.isPlaying
                        controlsVisible = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        controlsVisible = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val target = player.currentPosition + 10_000L
                        val max = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                        player.seekTo(if (max != null) target.coerceAtMost(max) else target)
                        controlsVisible = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (activeSkip != null) {
                            player.seekTo(activeSkip.endMs)
                        }
                        controlsVisible = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
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
        val resizeMode = when (settings.playerVideoFit()) {
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

        if (controlsVisible || playbackError != null || activeSkip != null || nextCountdown > 0 || warningVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .58f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = .74f),
                        )
                    )
                )
            )
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 34.dp, end = 44.dp),
            ) {
                Text(
                    text = playbackTitle(media, episode),
                    color = TvDesign.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(activeSource.providerName)
                        if (recoveryAttempts > 0) append("  •  Recovery $recoveryAttempts/2")
                    },
                    color = TvDesign.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 44.dp, vertical = 34.dp),
            ) {
                val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .background(TvDesign.White.copy(alpha = .22f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).height(4.dp)
                            .background(TvDesign.Accent, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (playing) "Playing" else "Paused",
                        color = TvDesign.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "${timeLabel(positionMs)}  /  ${timeLabel(durationMs)}",
                        color = TvDesign.Muted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "←/→ 10s   •   OK Play/Pause",
                        color = TvDesign.Muted,
                        fontSize = 11.sp,
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
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 44.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        activeSkip?.let { segment ->
            Text(
                text = "↑  ${skipLabel(segment.kind)}",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 44.dp, bottom = 76.dp)
                    .background(TvDesign.White, RoundedCornerShape(9.dp))
                    .padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }

        if (nextCountdown > 0 && nextEpisode != null) {
            Text(
                text = "Next episode in $nextCountdown  •  ${nextEpisode.title}",
                color = TvDesign.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 44.dp, bottom = 76.dp)
                    .background(Color.Black.copy(alpha = .78f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }

        playbackError?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFFA0A0),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .82f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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

private fun timeLabel(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
