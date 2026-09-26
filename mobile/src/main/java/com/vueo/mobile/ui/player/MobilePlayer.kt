package com.vueo.mobile.ui

import android.app.Activity
import android.net.Uri
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.graphics.Typeface
import android.util.TypedValue
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem as Media3MediaItem
import com.vueo.mobile.core.extensions.AddonCategory
import com.vueo.mobile.core.extensions.ExtensionInstaller
import com.vueo.mobile.core.extensions.primaryAddonCategory
import com.vueo.mobile.core.extensions.ExtensionKind
import com.vueo.mobile.core.extensions.MediaExtension
import com.vueo.mobile.core.extensions.UnifiedMediaEngine
import com.vueo.mobile.core.extensions.SourceRanker
import com.vueo.mobile.core.extensions.SourceDiscoveryCache
import com.vueo.mobile.core.extensions.CatalogDiscoveryCache
import com.vueo.mobile.core.enrichment.MdblistClient
import com.vueo.mobile.core.enrichment.MediaRating
import com.vueo.mobile.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.enrichment.ContentWarningRepository
import com.vueo.shared.core.detail.DetailPeoplePolicy
import com.vueo.shared.core.detail.DetailUpstreamPolicy
import com.vueo.shared.core.home.HomeCatalogPolicy
import com.vueo.shared.core.home.HomeRecommendationPolicy
import com.vueo.shared.core.search.DiscoverCatalogPolicy
import com.vueo.shared.core.search.DiscoverSortMode
import com.vueo.shared.core.search.SearchResultOrderPolicy
import com.vueo.shared.core.search.SearchMediaFilter
import com.vueo.shared.core.recommendation.RelatedContentOrchestrator
import com.vueo.shared.core.search.SearchOrchestrator
import com.vueo.shared.core.search.MediaEntityKind
import com.vueo.shared.core.search.MediaEntityTarget
import com.vueo.shared.core.source.SourceDiscoveryEngine
import com.vueo.shared.core.source.SourceDiscoveryRequest
import com.vueo.mobile.core.dna.UserDnaEngine
import com.vueo.mobile.core.dna.UserDnaPreferences
import com.vueo.mobile.core.model.CatalogRow
import com.vueo.mobile.BuildConfig
import com.vueo.mobile.R
import com.vueo.mobile.core.storage.PlaybackStore
import com.vueo.mobile.core.storage.LibraryStore
import com.vueo.mobile.core.storage.ProfileStore
import com.vueo.mobile.core.storage.VueoProfile
import com.vueo.mobile.core.storage.LibraryPlaybackEntry
import com.vueo.mobile.core.storage.PreferredQuality
import com.vueo.mobile.core.storage.PlayerOrientation
import com.vueo.mobile.core.storage.PlayerVideoFit
import com.vueo.mobile.core.storage.SettingsStore
import com.vueo.mobile.core.player.PlayerSkipKind
import com.vueo.mobile.core.player.PlayerSkipRepository
import com.vueo.mobile.core.player.PlayerSkipSegment
import com.vueo.mobile.core.player.PlayerPlaybackPhase
import com.vueo.mobile.core.player.PlayerSourceAssessment
import com.vueo.mobile.core.player.PlayerSourceAudioMatch
import com.vueo.mobile.core.player.PlayerSourcePolicy
import com.vueo.shared.core.player.PlayerTrackPolicy
import com.vueo.shared.core.player.PlayerSubtitleUpdatePolicy
import com.vueo.shared.core.player.SubtitleReadinessProbe
import com.vueo.shared.core.player.SubtitleSessionDataSource
import com.vueo.mobile.core.player.PlayerSourceRecoverySession
import com.vueo.mobile.core.player.PLAYER_REBUFFER_TIMEOUT_MS
import com.vueo.mobile.core.player.PLAYER_RECOVERY_SOURCE_TIMEOUT_MS
import com.vueo.mobile.core.player.PLAYER_STARTUP_TIMEOUT_MS
import com.vueo.mobile.core.storage.VueoDataMigration
import com.vueo.mobile.core.update.VueoUpdateManager
import com.vueo.mobile.core.model.SubtitleTrack
import com.vueo.mobile.core.plugin.PluginStore
import com.vueo.mobile.core.plugin.ProviderCodeSyncManager
import com.vueo.mobile.core.plugin.ProviderCodeStore
import com.vueo.mobile.core.plugin.ProviderHealthStatus
import com.vueo.mobile.core.plugin.ProviderHealthRecord
import com.vueo.mobile.core.plugin.PluginHealthStore
import com.vueo.mobile.core.plugin.PluginSourceEngine
import com.vueo.mobile.core.plugin.PluginRepositoryDescriptor
import com.vueo.mobile.core.plugin.PluginRepositoryClient
import com.vueo.mobile.core.model.EpisodeItem
import com.vueo.mobile.core.model.MediaCompany
import com.vueo.mobile.core.model.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.mobile.core.model.MediaPerson
import com.vueo.mobile.core.model.StreamSource
import com.vueo.mobile.core.storage.AddonStore
import com.vueo.mobile.ui.components.NetworkImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun extractPlayerImdbId(value: String?): String? =
    ContentWarningRepository.extractImdbId(value)

private fun Context.playerSubtitleDelayMs(mediaKey: String): Int =
    getSharedPreferences(
        "vueo_player_subtitles",
        Context.MODE_PRIVATE,
    ).getInt("subtitle_delay_ms:$mediaKey", 0)
        .coerceIn(-60_000, 60_000)

private fun Context.setPlayerSubtitleDelayMs(
    mediaKey: String,
    delayMs: Int,
) {
    getSharedPreferences(
        "vueo_player_subtitles",
        Context.MODE_PRIVATE,
    ).edit()
        .putInt(
            "subtitle_delay_ms:$mediaKey",
            delayMs.coerceIn(-60_000, 60_000),
        )
        .apply()
}

private const val LATE_SUBTITLE_TRACK_REFRESH_ATTEMPTS = 60
private const val LATE_SUBTITLE_TRACK_REFRESH_INTERVAL_MS = 100L

@Composable
private fun PlayerContentWarningsOverlay(
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
                .background(VueoPlayerAccent),
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

internal val VueoPlayerAccent =
    Color(0xFFB9FF3A)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerScreen(
    settingsStore: SettingsStore,
    title: String,
    mediaKey: String,
    media: MediaItem,
    videoId: String,
    episode: EpisodeItem?,
    nextEpisode: EpisodeItem?,
    episodes: List<EpisodeItem>,
    source: StreamSource,
    availableSources: List<StreamSource>,
    sourceProviderOrder: List<String>,
    subtitles: List<SubtitleTrack>,
    initialPositionMs: Long,
    episodeSwitchingTo: EpisodeItem?,
    episodeSwitchFailed: Boolean,
    onEpisodeSwitchCompleted: () -> Unit,
    onEpisodeSwitchFailed: () -> Unit,
    onLibraryChanged: () -> Unit,
    onSwitchSource: (StreamSource, Long) -> Unit,
    onNextEpisode: (EpisodeItem) -> Unit,
    onEpisodeSelected: (EpisodeItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val entryConfigurationOrientation = remember {
        context.resources.configuration.orientation
    }
    val previousRequestedOrientation = remember(activity) {
        activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val currentConfigurationOrientation =
        LocalConfiguration.current.orientation
    val latestOnBack = rememberUpdatedState(onBack)
    var playerExitRequested by remember {
        mutableStateOf(false)
    }
    var playerExitCommitted by remember {
        mutableStateOf(false)
    }

    fun commitPlayerExit() {
        if (!playerExitCommitted) {
            playerExitCommitted = true
            latestOnBack.value()
        }
    }

    fun requestPlayerExit() {
        if (playerExitRequested) return

        playerExitRequested = true
        activity?.requestedOrientation =
            when (previousRequestedOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ->
                    when (entryConfigurationOrientation) {
                        android.content.res.Configuration
                            .ORIENTATION_LANDSCAPE ->
                            ActivityInfo
                                .SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                        else ->
                            ActivityInfo
                                .SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }

                else -> previousRequestedOrientation
            }

        if (
            activity == null ||
            currentConfigurationOrientation ==
                entryConfigurationOrientation
        ) {
            commitPlayerExit()
        }
    }

    LaunchedEffect(
        playerExitRequested,
        currentConfigurationOrientation,
        entryConfigurationOrientation,
    ) {
        if (
            playerExitRequested &&
            !playerExitCommitted &&
            currentConfigurationOrientation ==
                entryConfigurationOrientation
        ) {
            commitPlayerExit()
        }
    }
    val latestEpisodeSwitchingTo =
        rememberUpdatedState(episodeSwitchingTo)
    val latestOnEpisodeSwitchCompleted =
        rememberUpdatedState(onEpisodeSwitchCompleted)
    val latestOnEpisodeSwitchFailed =
        rememberUpdatedState(onEpisodeSwitchFailed)
    val audioManager = remember {
        context.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager
    }
    val seekGestureSensitivity = remember {
        context.seekGestureSensitivity()
    }
    val playbackStore = remember {
        PlaybackStore(
            context.applicationContext
        )
    }
    val libraryStore = remember {
        LibraryStore(
            context.applicationContext
        )
    }

    val playerPluginStore = remember {
        PluginStore(
            context.applicationContext
        )
    }
    var contentWarningsEnabled by remember(settingsStore) {
        context.migrateLegacyContentWarningsToShared(settingsStore)
        mutableStateOf(settingsStore.contentWarningsEnabled())
    }

    val savedPositionMs = remember(mediaKey) {
        playbackStore.positionMs(mediaKey)
    }
    val resumePlaybackEnabled = remember(mediaKey) {
        settingsStore.resumePlaybackEnabled()
    }
    val sourceSwitchPosition = initialPositionMs
        .coerceAtLeast(0L)
    val shouldPromptResume =
        sourceSwitchPosition <= 5_000L &&
            resumePlaybackEnabled &&
            savedPositionMs > 5_000L
    val initialPlaybackPositionMs =
        if (sourceSwitchPosition > 5_000L) {
            sourceSwitchPosition
        } else if (!shouldPromptResume && resumePlaybackEnabled) {
            savedPositionMs
        } else {
            0L
        }

    var resumePromptVisible by remember(
        mediaKey,
    ) {
        mutableStateOf(shouldPromptResume)
    }
    var playbackError by remember {
        mutableStateOf<String?>(null)
    }
    var playbackPhase by remember(mediaKey) {
        mutableStateOf(PlayerPlaybackPhase.LOADING)
    }
    var hasRenderedFirstFrame by remember(mediaKey) {
        mutableStateOf(false)
    }
    var recoveryInProgress by remember(mediaKey) {
        mutableStateOf(false)
    }
    var retryGeneration by remember(mediaKey) {
        mutableIntStateOf(0)
    }
    val sourceRecoverySession = remember(mediaKey) {
        PlayerSourceRecoverySession()
    }
    var failedSourceUrls by remember(mediaKey) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var isBuffering by remember {
        mutableStateOf(false)
    }
    var isPlaying by remember {
        mutableStateOf(false)
    }
    var currentPositionMs by remember {
        mutableStateOf(initialPlaybackPositionMs)
    }
    var lastValidPlaybackPositionMs by remember(mediaKey) {
        mutableStateOf(
            maxOf(
                initialPlaybackPositionMs,
                savedPositionMs,
            ).takeIf { it > 5_000L } ?: 0L
        )
    }
    var durationMs by remember {
        mutableStateOf(0L)
    }
    var controlsVisible by remember {
        mutableStateOf(true)
    }
    var controlsLocked by remember {
        mutableStateOf(false)
    }
    var videoFit by remember {
        mutableStateOf(
            settingsStore.playerVideoFit()
        )
    }
    var gestureMessage by remember {
        mutableStateOf<String?>(null)
    }
    var gestureActive by remember {
        mutableStateOf(false)
    }
    var gestureSeekPositionMs by remember {
        mutableStateOf<Long?>(null)
    }
    var audioTracks by remember {
        mutableStateOf<List<PlayerTrackChoice>>(
            emptyList()
        )
    }
    var textTracks by remember {
        mutableStateOf<List<PlayerTrackChoice>>(
            emptyList()
        )
    }
    var subtitleStyle by remember {
        mutableStateOf(
            PlayerSubtitleStyleState(
                fontSizeSp = settingsStore.subtitleFontSizeSp(),
                bold = settingsStore.subtitleBold(),
                textColor = settingsStore.subtitleTextColor(),
                outlineEnabled = settingsStore.subtitleOutlineEnabled(),
                outlineColor = settingsStore.subtitleOutlineColor(),
                bottomPaddingPercent =
                    settingsStore.subtitleBottomPaddingPercent(),
            )
        )
    }
    var subtitleDelayMs by remember(mediaKey) {
        mutableIntStateOf(
            context.playerSubtitleDelayMs(mediaKey)
        )
    }
    val latestSubtitleDelayMs =
        rememberUpdatedState(subtitleDelayMs)
    var subtitlesDisabled by remember(mediaKey) {
        mutableStateOf(
            !playerSubtitlesEnabledAtStart(settingsStore)
        )
    }
    var selectedSubtitleIsExternal by remember(mediaKey) {
        mutableStateOf(false)
    }
    val subtitleSelectionScope = rememberCoroutineScope()
    val latestSubtitles = rememberUpdatedState(subtitles)
    var pendingSubtitleSelectionId by remember(mediaKey) {
        mutableStateOf(
            settingsStore.subtitleSelection(mediaKey)
                ?.takeIf {
                    settingsStore.lastSubtitleSelection() != PLAYER_SUBTITLE_OFF &&
                        it.startsWith("external:")
                }
        )
    }
    var translatingSubtitleSelectionId by remember(mediaKey) {
        mutableStateOf<String?>(null)
    }
    var requestedSubtitleSelectionId by remember(mediaKey) {
        mutableStateOf<String?>(null)
    }
    var subtitlePreparationJob by remember(mediaKey) {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    val latestSelectedSubtitleIsExternal =
        rememberUpdatedState(selectedSubtitleIsExternal)
    var showAudioDialog by remember {
        mutableStateOf(false)
    }
    var showSubtitleDialog by remember {
        mutableStateOf(false)
    }
    var showSubtitleStyleOverlay by remember {
        mutableStateOf(false)
    }
    var showSourceDialog by remember {
        mutableStateOf(false)
    }
    var switchingSourceUrl by remember(mediaKey) {
        mutableStateOf<String?>(null)
    }
    var showEpisodeDialog by remember {
        mutableStateOf(false)
    }
    var showMoreDialog by remember {
        mutableStateOf(false)
    }
    val playerPanelVisible =
        showAudioDialog ||
            showSubtitleDialog ||
            showSubtitleStyleOverlay ||
            showSourceDialog ||
            showEpisodeDialog ||
            showMoreDialog
    var playbackSpeed by remember {
        mutableStateOf(
            settingsStore.playerPlaybackSpeed()
        )
    }
    var nextEpisodeCountdown by remember {
        mutableStateOf<Int?>(null)
    }
    var nextEpisodeCardSwitchTarget by remember {
        mutableStateOf<EpisodeItem?>(null)
    }
    var nextEpisodeSwitching by remember(mediaKey) {
        mutableStateOf(false)
    }
    var showNextEpisodeCard by remember(mediaKey) {
        mutableStateOf(false)
    }
    var nextEpisodeCardDismissed by remember(mediaKey) {
        mutableStateOf(false)
    }
    val nextEpisodeCardVisible =
        showNextEpisodeCard ||
            nextEpisodeCardSwitchTarget != null
    var autoPlayNextEpisode by remember {
        mutableStateOf(
            settingsStore.autoPlayNextEpisodeEnabled()
        )
    }
    var contentWarnings by remember(mediaKey) {
        mutableStateOf<List<ContentWarning>>(emptyList())
    }
    var showContentWarnings by remember(mediaKey) {
        mutableStateOf(false)
    }
    var contentWarningsShown by remember(mediaKey) {
        mutableStateOf(false)
    }
    var skipSegmentsEnabled by remember(mediaKey) {
        mutableStateOf(settingsStore.skipSegmentsEnabled())
    }
    var skipSegments by remember(mediaKey) {
        mutableStateOf<List<PlayerSkipSegment>>(emptyList())
    }
    var dismissedSkipSegmentKey by remember(mediaKey) {
        mutableStateOf<String?>(null)
    }

    val playableSources = remember(
        availableSources,
        source.url,
        media.originalLanguage,
    ) {
        (listOf(source) + availableSources)
            .filter {
                it.isDirectPlayable
            }
            .distinctBy {
                it.url
            }
            .sortedWith(
                SourceRanker.comparator(
                    settingsStore
                        .preferredQuality()
                        .rankKey,
                    originalLanguage = media.originalLanguage,
                )
            )
    }

    BackHandler {
        when {
            showAudioDialog ->
                showAudioDialog = false

            showSubtitleStyleOverlay -> {
                showSubtitleStyleOverlay = false
                controlsVisible = true
            }

            showSubtitleDialog -> {
                showSubtitleDialog = false
                controlsVisible = true
            }

            showSourceDialog -> {
                showSourceDialog = false
                switchingSourceUrl = null
            }

            showEpisodeDialog ->
                showEpisodeDialog = false

            showMoreDialog ->
                showMoreDialog = false

            controlsLocked ->
                controlsLocked = false

            else -> requestPlayerExit()
        }
    }

    val player = remember(
        source.url,
        source.headers,
        mediaKey,
        initialPositionMs,
    ) {
        val httpFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "VUEO/${BuildConfig.VERSION_NAME}"
                )
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(
                    source.headers
                )

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(SubtitleSessionDataSource.Factory(httpFactory))

        ExoPlayer.Builder(
            context,
            VueoSubtitleOffsetRenderersFactory(
                context = context,
                subtitleDelayUsProvider = {
                    latestSubtitleDelayMs.value.toLong() * 1_000L
                },
                shouldNormalizeCuePositionProvider = {
                    latestSelectedSubtitleIsExternal.value
                },
            ),
        )
            .setMediaSourceFactory(
                mediaSourceFactory
            )
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.DEFAULT,
                    true,
                )

                val playerMediaItem =
                    buildPlayerMediaItem(
                        sourceUrl =
                            requireNotNull(
                                source.url
                            ),
                        subtitles = subtitles,
                        preferredLanguageCode =
                            playerPreferredSubtitleLanguageCode(
                                settingsStore
                            ),
                        secondaryLanguageCode =
                            settingsStore
                                .secondarySubtitleLanguage()
                                .languageCode,
                        subtitlesOnByDefault =
                            playerSubtitlesEnabledAtStart(
                                settingsStore
                            ),
                        autoSelectPreferred =
                            settingsStore
                                .autoSelectPreferredSubtitle(),
                        embeddedPriority =
                            settingsStore
                                .embeddedSubtitlePriority(),
                    )

                setMediaItem(
                    playerMediaItem,
                    initialPlaybackPositionMs,
                )

                val initialTrackParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_TEXT,
                            subtitlesDisabled ||
                                pendingSubtitleSelectionId != null,
                        )
                if (settingsStore.autoSelectPreferredSubtitle()) {
                    val preferredTextLanguages = listOfNotNull(
                        playerPreferredSubtitleLanguageCode(settingsStore),
                        settingsStore.secondarySubtitleLanguage().languageCode,
                    ).mapNotNull(com.vueo.shared.core.language.LanguagePolicy::canonicalCode)
                        .distinct()
                    if (preferredTextLanguages.isNotEmpty()) {
                        initialTrackParameters.setPreferredTextLanguages(
                            *preferredTextLanguages.toTypedArray()
                        )
                    }
                }
                PlayerSourcePolicy
                    .canonicalLanguageCode(media.originalLanguage)
                    ?.let {
                        initialTrackParameters
                            .setPreferredAudioLanguages(it)
                    }
                trackSelectionParameters =
                    initialTrackParameters.build()

                prepare()
                playWhenReady =
                    !resumePromptVisible
            }
    }

    var appliedSubtitleUrls by remember(player) {
        mutableStateOf(
            PlayerSubtitleUpdatePolicy.sourceKeys(subtitles)
        )
    }
    var subtitleTrackRefreshInProgress by remember(player) {
        mutableStateOf(false)
    }
    var subtitlePreferenceRestored by remember(player) {
        mutableStateOf(false)
    }
    var audioPreferenceRestored by remember(player) {
        mutableStateOf(false)
    }
    var audioAutomaticSelected by remember(player) {
        mutableStateOf(true)
    }

    // The subtitle workspace is a discovery UI, not an ExoPlayer track
    // inspector. Publish newly discovered external choices immediately;
    // ExoPlayer may expose their TrackGroups a little later.
    LaunchedEffect(player, subtitles) {
        val discoveredBySelectionId =
            subtitles.associateBy(
                PlayerTrackPolicy::externalSubtitleSelectionId
            )
        val availablePlayerChoices = playerTrackChoices(
            tracks = player.currentTracks,
            trackType = C.TRACK_TYPE_TEXT,
            externalSubtitles = discoveredBySelectionId,
        )
        textTracks = mergeDiscoveredSubtitleChoices(
            playerChoices = availablePlayerChoices,
            discoveredSubtitles = subtitles,
        )
    }

    LaunchedEffect(
        player,
        subtitles,
    ) {
        val latestSubtitleUrls =
            PlayerSubtitleUpdatePolicy.sourceKeys(subtitles)

        if (latestSubtitleUrls != appliedSubtitleUrls) {
            delay(350L)
            val positionMs = PlayerSubtitleUpdatePolicy.stableResumePositionMs(
                currentPositionMs = player.currentPosition,
                lastKnownPositionMs = lastValidPlaybackPositionMs,
            )

            audioPreferenceRestored = false
            subtitlePreferenceRestored = false

            val updatedMediaItem = buildPlayerMediaItem(
                    sourceUrl = requireNotNull(source.url),
                    subtitles = subtitles,
                    preferredLanguageCode =
                        playerPreferredSubtitleLanguageCode(
                            settingsStore
                        ),
                    secondaryLanguageCode =
                        settingsStore
                            .secondarySubtitleLanguage()
                            .languageCode,
                    subtitlesOnByDefault =
                        !subtitlesDisabled,
                    autoSelectPreferred =
                        settingsStore
                            .autoSelectPreferredSubtitle(),
                    embeddedPriority =
                        settingsStore
                            .embeddedSubtitlePriority(),
                )
            val currentIndex = player.currentMediaItemIndex
                .takeIf { it in 0 until player.mediaItemCount }
                ?: 0
            subtitleTrackRefreshInProgress = true
            player.replaceMediaItem(currentIndex, updatedMediaItem)
            player.seekTo(currentIndex, positionMs)
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(
                        C.TRACK_TYPE_TEXT,
                        subtitlesDisabled ||
                            pendingSubtitleSelectionId != null,
                    )
                    .build()
            appliedSubtitleUrls = latestSubtitleUrls

            // ExoPlayer can publish an empty/intermediate track snapshot when
            // external subtitles are added to the currently playing item.
            // onTracksChanged is therefore not sufficient on its own: keep
            // reconciling the workspace until every late subtitle is visible,
            // while the video continues from the preserved position above.
            val expectedExternalSelectionIds =
                subtitles
                    .map(PlayerTrackPolicy::externalSubtitleSelectionId)
                    .toSet()
            var refreshAttempts = 0
            while (refreshAttempts < LATE_SUBTITLE_TRACK_REFRESH_ATTEMPTS) {
                val latestExternalSubtitles =
                    latestSubtitles.value
                        .associateBy(PlayerTrackPolicy::externalSubtitleSelectionId)
                val refreshedTextTracks = playerTrackChoices(
                    tracks = player.currentTracks,
                    trackType = C.TRACK_TYPE_TEXT,
                    externalSubtitles = latestExternalSubtitles,
                )

                textTracks = mergeDiscoveredSubtitleChoices(
                    playerChoices = refreshedTextTracks,
                    discoveredSubtitles = latestSubtitles.value,
                )

                val visibleExternalSelectionIds =
                    refreshedTextTracks
                        .asSequence()
                        .filter { it.externalSubtitle != null }
                        .map { it.selectionId }
                        .toSet()
                if (
                    expectedExternalSelectionIds.isEmpty() ||
                    expectedExternalSelectionIds.all(visibleExternalSelectionIds::contains)
                ) {
                    break
                }

                delay(LATE_SUBTITLE_TRACK_REFRESH_INTERVAL_MS)
                refreshAttempts += 1
            }

            subtitleTrackRefreshInProgress = false

            // One final read covers devices that publish their last Tracks
            // snapshot on the same frame as the final polling interval.
            val finalExternalSubtitles =
                latestSubtitles.value
                    .associateBy(PlayerTrackPolicy::externalSubtitleSelectionId)
            val finalTextTracks = playerTrackChoices(
                tracks = player.currentTracks,
                trackType = C.TRACK_TYPE_TEXT,
                externalSubtitles = finalExternalSubtitles,
            )
            textTracks = mergeDiscoveredSubtitleChoices(
                playerChoices = finalTextTracks,
                discoveredSubtitles = latestSubtitles.value,
            )
        }
    }

    fun stablePlaybackSnapshot(): Pair<Long, Long> {
        val livePositionMs =
            player.currentPosition.coerceAtLeast(0L)
        val liveDurationMs =
            player.duration.coerceAtLeast(0L)

        if (livePositionMs > 5_000L) {
            lastValidPlaybackPositionMs = livePositionMs
        }

        val stablePositionMs =
            if (livePositionMs > 5_000L) {
                livePositionMs
            } else {
                lastValidPlaybackPositionMs
            }

        return stablePositionMs to liveDurationMs
    }

    fun recordLibraryProgress(
        positionMs: Long,
        durationMs: Long,
    ) {
        libraryStore.recordPlayback(
            media = media,
            videoId = videoId,
            episodeTitle = episode?.title,
            season = episode?.season,
            episode = episode?.episode,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun savePosition() {
        val (positionMs, durationMs) =
            stablePlaybackSnapshot()

        // Pause/exit/dispose can briefly report 0 ms. Never let that
        // transient value clear an already-valid resume point.
        if (positionMs <= 5_000L) {
            return
        }

        playbackStore.savePositionMs(
            mediaKey = mediaKey,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        recordLibraryProgress(
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun startNextEpisode() {
        val next = nextEpisode ?: return
        if (nextEpisodeSwitching) {
            return
        }
        nextEpisodeSwitching = true
        nextEpisodeCardSwitchTarget = next
        nextEpisodeCountdown = null
        showNextEpisodeCard = false
        controlsVisible = false
        savePosition()
        onNextEpisode(next)
    }

    fun handleSourceFailure(
        message: String,
    ) {
        if (recoveryInProgress) {
            return
        }

        sourceRecoverySession.markFailed(source)
        failedSourceUrls =
            sourceRecoverySession.failedSourceUrls()

        val alternate = if (
            settingsStore.autoSourceRecoveryEnabled()
        ) {
            sourceRecoverySession.next(
                rankedSources = playableSources,
                originalLanguage = media.originalLanguage,
            )
        } else {
            null
        }

        if (alternate != null) {
            recoveryInProgress = true
            playbackPhase = PlayerPlaybackPhase.RECOVERING
            playbackError = null
            controlsVisible = true
            gestureMessage = "Trying next source"
            val position = player.currentPosition
                .coerceAtLeast(currentPositionMs)
                .coerceAtLeast(0L)
            savePosition()
            if (showSourceDialog) {
                switchingSourceUrl = alternate.url
            }
            hasRenderedFirstFrame = false
            onSwitchSource(alternate, position)
        } else {
            playbackPhase = PlayerPlaybackPhase.FAILED
            val failedCount =
                sourceRecoverySession.failedSourceCount()
            playbackError =
                if (failedCount > 1) {
                    "$failedCount ranked sources failed to start. " +
                        "Choose Source to try one manually."
                } else {
                    "$message No other suitable automatic source " +
                        "was available."
                }
            controlsVisible = true
            if (
                latestEpisodeSwitchingTo
                    .value
                    ?.id == episode?.id
            ) {
                latestOnEpisodeSwitchFailed
                    .value
                    .invoke()
            }
        }
    }

    fun refreshTrackChoices(
        tracks: Tracks = player.currentTracks,
        acceptEmptyTextTracks: Boolean = !subtitleTrackRefreshInProgress,
    ) {
        val externalSubtitles =
            latestSubtitles.value
                .associateBy(PlayerTrackPolicy::externalSubtitleSelectionId)
        audioTracks = playerTrackChoices(
            tracks = tracks,
            trackType = C.TRACK_TYPE_AUDIO,
        )
        val refreshedTextTracks = playerTrackChoices(
            tracks = tracks,
            trackType = C.TRACK_TYPE_TEXT,
            externalSubtitles = externalSubtitles,
        )
        val workspaceTextTracks = mergeDiscoveredSubtitleChoices(
            playerChoices = refreshedTextTracks,
            discoveredSubtitles = latestSubtitles.value,
        )
        if (workspaceTextTracks.isNotEmpty() || acceptEmptyTextTracks) {
            textTracks = workspaceTextTracks
        }
        val effectiveTextTracks =
            if (refreshedTextTracks.isEmpty() && !acceptEmptyTextTracks) {
                textTracks
            } else {
                refreshedTextTracks
            }
        val confirmedSubtitleSelectionId = effectiveTextTracks
            .firstOrNull { it.selected }
            ?.selectionId
        if (
            requestedSubtitleSelectionId != null &&
            requestedSubtitleSelectionId == confirmedSubtitleSelectionId
        ) {
            requestedSubtitleSelectionId = null
        }
        selectedSubtitleIsExternal =
            !subtitlesDisabled &&
            effectiveTextTracks
                .firstOrNull { it.selected }
                ?.selectionId
                ?.startsWith("external:") == true

        if (!audioPreferenceRestored && audioTracks.isNotEmpty()) {
            val globalSelection =
                settingsStore.lastAudioSelection()
            val savedSelection = globalSelection
                ?: settingsStore.audioSelection(media.id)
            val savedTrack = findSavedAudioTrack(
                tracks = audioTracks,
                savedSelection = savedSelection,
            )

            when {
                savedSelection == PLAYER_AUDIO_AUTO -> {
                    if (globalSelection == null) {
                        settingsStore.setLastAudioSelection(
                            PLAYER_AUDIO_AUTO
                        )
                    }
                    audioPreferenceRestored = true
                    audioAutomaticSelected = true
                    clearTrackOverride(
                        player = player,
                        trackType = C.TRACK_TYPE_AUDIO,
                        disable = false,
                    )
                }

                savedTrack != null -> {
                    if (globalSelection == null && savedSelection != null) {
                        settingsStore.setLastAudioSelection(
                            savedSelection
                        )
                    }
                    audioPreferenceRestored = true
                    audioAutomaticSelected = false
                    applyTrackChoice(
                        player = player,
                        trackType = C.TRACK_TYPE_AUDIO,
                        choice = savedTrack,
                    )
                }

                else -> {
                    audioPreferenceRestored = true
                    audioAutomaticSelected = true
                }
            }
        }

        if (!subtitlePreferenceRestored && refreshedTextTracks.isNotEmpty()) {
            val globalSelection =
                settingsStore.lastSubtitleSelection()
            val contentSelection =
                settingsStore.subtitleSelection(mediaKey)
            val savedSelection =
                PlayerTrackPolicy.resolvedSubtitleSelection(
                    globalSelection = globalSelection,
                    contentSelection = contentSelection,
                )
            val savedLanguage =
                (globalSelection ?: contentSelection)
                ?.takeIf {
                    it.startsWith(
                        PLAYER_SUBTITLE_LANGUAGE_PREFIX
                    )
                }
                ?.removePrefix(
                    PLAYER_SUBTITLE_LANGUAGE_PREFIX
                )
            val savedTrack =
                contentSelection
                    ?.let { selectionId ->
                        textTracks.firstOrNull {
                            it.selectionId == selectionId
                        }
                    }
                    ?: savedLanguage?.let { language ->
                        textTracks.firstOrNull {
                            canonicalSubtitleLanguage(it.language) ==
                                canonicalSubtitleLanguage(language)
                        }
                    }
                    ?: textTracks.firstOrNull {
                        it.selectionId == savedSelection
                    }

            when {
                savedSelection == PLAYER_SUBTITLE_OFF -> {
                    if (globalSelection == null) {
                        settingsStore.setLastSubtitleSelection(
                            PLAYER_SUBTITLE_OFF
                        )
                    }
                    subtitlePreferenceRestored = true
                    clearTrackOverride(
                        player = player,
                        trackType = C.TRACK_TYPE_TEXT,
                        disable = true,
                    )
                    subtitlesDisabled = true
                    selectedSubtitleIsExternal = false
                    pendingSubtitleSelectionId = null
                    translatingSubtitleSelectionId = null
                    requestedSubtitleSelectionId = null
                }

                savedTrack?.externalSubtitle != null -> {
                    subtitlePreferenceRestored = true
                    clearTrackOverride(
                        player = player,
                        trackType = C.TRACK_TYPE_TEXT,
                        disable = true,
                    )
                    subtitlesDisabled = false
                    pendingSubtitleSelectionId = savedTrack.selectionId
                    translatingSubtitleSelectionId = null
                    subtitlePreparationJob?.cancel()
                    subtitlePreparationJob = subtitleSelectionScope.launch {
                        val ready = SubtitleReadinessProbe.awaitReady(
                            url = requireNotNull(savedTrack.externalSubtitle).url,
                            onWaiting = {
                                if (pendingSubtitleSelectionId == savedTrack.selectionId) {
                                    translatingSubtitleSelectionId = savedTrack.selectionId
                                }
                            },
                        )
                        var refreshWaitAttempts = 0
                        while (
                            ready &&
                            subtitleTrackRefreshInProgress &&
                            refreshWaitAttempts < 200
                        ) {
                            delay(50L)
                            refreshWaitAttempts += 1
                        }
                        var latestChoice: PlayerTrackChoice? = null
                        var trackWaitAttempts = 0
                        while (
                            ready &&
                            pendingSubtitleSelectionId == savedTrack.selectionId &&
                            latestChoice == null &&
                            trackWaitAttempts < 200
                        ) {
                            val latestExternalSubtitles = latestSubtitles.value
                                .associateBy(PlayerTrackPolicy::externalSubtitleSelectionId)
                            latestChoice = playerTrackChoices(
                                tracks = player.currentTracks,
                                trackType = C.TRACK_TYPE_TEXT,
                                externalSubtitles = latestExternalSubtitles,
                            ).firstOrNull {
                                it.selectionId == savedTrack.selectionId
                            }
                            if (latestChoice == null) {
                                delay(50L)
                                trackWaitAttempts += 1
                            }
                        }
                        if (
                            ready &&
                            pendingSubtitleSelectionId == savedTrack.selectionId &&
                            latestChoice != null
                        ) {
                            requestedSubtitleSelectionId = latestChoice.selectionId
                            applyTrackChoice(
                                player = player,
                                trackType = C.TRACK_TYPE_TEXT,
                                choice = latestChoice,
                            )
                            pendingSubtitleSelectionId = null
                            translatingSubtitleSelectionId = null
                            subtitlesDisabled = false
                            selectedSubtitleIsExternal = true
                        }
                        if (translatingSubtitleSelectionId == savedTrack.selectionId) {
                            translatingSubtitleSelectionId = null
                        }
                        subtitlePreparationJob = null
                    }
                }

                savedTrack != null -> {
                    if (globalSelection == null) {
                        settingsStore.setLastSubtitleSelection(
                            PlayerTrackPolicy.subtitleLanguageSelectionId(
                                savedTrack.language
                            )
                        )
                    }
                    subtitlePreferenceRestored = true
                    pendingSubtitleSelectionId = null
                    translatingSubtitleSelectionId = null
                    requestedSubtitleSelectionId = savedTrack.selectionId
                    applyTrackChoice(
                        player = player,
                        trackType = C.TRACK_TYPE_TEXT,
                        choice = savedTrack,
                    )
                    subtitlesDisabled = false
                    selectedSubtitleIsExternal =
                        savedTrack.selectionId
                            .startsWith("external:")
                }

                savedSelection == null -> {
                    subtitlePreferenceRestored = true
                }

                subtitles.isNotEmpty() -> {
                    subtitlePreferenceRestored = true
                }
            }
        }
    }

    LaunchedEffect(mediaKey) {
        libraryStore.recordPlayback(
            media = media,
            videoId = videoId,
            episodeTitle = episode?.title,
            season = episode?.season,
            episode = episode?.episode,
            positionMs = initialPlaybackPositionMs,
            durationMs =
                playbackStore.durationMs(mediaKey),
        )
    }

    LaunchedEffect(
        media.id,
        videoId,
        episode?.id,
        contentWarningsEnabled,
    ) {
        contentWarnings = emptyList()
        showContentWarnings = false
        contentWarningsShown = false

        if (!contentWarningsEnabled) {
            return@LaunchedEffect
        }

        val directImdbId =
            extractPlayerImdbId(media.id)
                ?: extractPlayerImdbId(videoId)
                ?: extractPlayerImdbId(episode?.id)
        val imdbId = directImdbId ?: runCatching {
            TmdbEnhancementClient.prepareForCore(
                item = media,
                apiKey = playerPluginStore.tmdbApiKey(),
            ).id
        }.getOrNull()?.let(::extractPlayerImdbId)

        if (imdbId != null) {
            contentWarnings =
                ContentWarningRepository.get(imdbId)
        }
    }

    LaunchedEffect(
        media.id,
        videoId,
        episode?.id,
        episode?.season,
        episode?.episode,
        skipSegmentsEnabled,
    ) {
        skipSegments = emptyList()
        dismissedSkipSegmentKey = null
        val currentEpisode = episode
        if (!skipSegmentsEnabled || currentEpisode == null) {
            return@LaunchedEffect
        }

        val directImdbId =
            extractPlayerImdbId(media.id)
                ?: extractPlayerImdbId(videoId)
                ?: extractPlayerImdbId(currentEpisode.id)
        val imdbId = directImdbId ?: runCatching {
            TmdbEnhancementClient.prepareForCore(
                item = media,
                apiKey = playerPluginStore.tmdbApiKey(),
            ).id
        }.getOrNull()?.let(::extractPlayerImdbId)

        if (imdbId != null) {
            skipSegments = PlayerSkipRepository.segments(
                imdbId = imdbId,
                season = currentEpisode.season,
                episode = currentEpisode.episode,
            )
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

    DisposableEffect(
        player,
        mediaKey,
        source.url,
    ) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(
                    error: PlaybackException,
                ) {
                    isBuffering = false
                    handleSourceFailure(
                        friendlyPlaybackError(error)
                    )
                }

                override fun onTracksChanged(
                    tracks: Tracks,
                ) {
                    refreshTrackChoices(tracks)
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int,
                ) {
                    isBuffering =
                        playbackState ==
                            Player.STATE_BUFFERING

                    if (
                        playbackState == Player.STATE_BUFFERING
                    ) {
                        playbackPhase =
                            if (hasRenderedFirstFrame) {
                                PlayerPlaybackPhase.BUFFERING
                            } else {
                                PlayerPlaybackPhase.LOADING
                            }
                    }

                    if (
                        playbackState ==
                            Player.STATE_READY
                    ) {
                        if (subtitleTrackRefreshInProgress) {
                            subtitleTrackRefreshInProgress = false
                            refreshTrackChoices(
                                tracks = player.currentTracks,
                                acceptEmptyTextTracks = true,
                            )
                        }
                        sourceRecoverySession.markReady()
                        playbackError = null
                        playbackPhase = PlayerPlaybackPhase.READY
                        recoveryInProgress = false
                    }

                    if (
                        playbackState ==
                            Player.STATE_ENDED
                    ) {
                        playbackStore.clearPosition(
                            mediaKey
                        )
                        libraryStore.recordPlayback(
                            media = media,
                            videoId = videoId,
                            episodeTitle =
                                episode?.title,
                            season = episode?.season,
                            episode = episode?.episode,
                            positionMs =
                                player.duration
                                    .coerceAtLeast(0L),
                            durationMs =
                                player.duration
                                    .coerceAtLeast(0L),
                        )
                        onLibraryChanged()
                        if (
                            nextEpisode != null &&
                            !nextEpisodeCardDismissed
                        ) {
                            showNextEpisodeCard = true
                            nextEpisodeCountdown =
                                if (autoPlayNextEpisode) {
                                    8
                                } else {
                                    null
                                }
                            controlsVisible = true
                        }
                    }
                }

                override fun onIsPlayingChanged(
                    playing: Boolean,
                ) {
                    isPlaying = playing
                    if (!playing) {
                        savePosition()
                        controlsVisible = true
                    }
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                    playbackPhase = PlayerPlaybackPhase.READY
                    playbackError = null
                    recoveryInProgress = false
                    if (
                        latestEpisodeSwitchingTo
                            .value
                            ?.id == episode?.id
                    ) {
                        if (
                            nextEpisodeCardSwitchTarget
                                ?.id == episode?.id
                        ) {
                            nextEpisodeCardSwitchTarget = null
                        }
                        showEpisodeDialog = false
                        latestOnEpisodeSwitchCompleted
                            .value
                            .invoke()
                    }
                }
            }

        player.addListener(listener)
        refreshTrackChoices()

        onDispose {
            player.removeListener(listener)
            savePosition()
            onLibraryChanged()
            player.release()
        }
    }

    LaunchedEffect(
        source.url,
    ) {
        sourceRecoverySession.begin(source)
        playbackPhase = PlayerPlaybackPhase.LOADING
        playbackError = null
        isBuffering = false
        hasRenderedFirstFrame = false
        recoveryInProgress = false
    }

    LaunchedEffect(
        showSourceDialog,
        source.url,
        hasRenderedFirstFrame,
        switchingSourceUrl,
    ) {
        val pendingUrl = switchingSourceUrl
        if (
            showSourceDialog &&
            pendingUrl != null &&
            source.url == pendingUrl &&
            hasRenderedFirstFrame
        ) {
            showSourceDialog = false
            switchingSourceUrl = null
            controlsVisible = true
        }
    }

    LaunchedEffect(
        player,
        retryGeneration,
        resumePromptVisible,
        hasRenderedFirstFrame,
    ) {
        if (
            resumePromptVisible ||
            hasRenderedFirstFrame ||
            playbackError != null
        ) {
            return@LaunchedEffect
        }

        val startupTimeoutMs =
            if (sourceRecoverySession.isAutomaticRecoveryActive()) {
                PLAYER_RECOVERY_SOURCE_TIMEOUT_MS
            } else {
                PLAYER_STARTUP_TIMEOUT_MS
            }
        delay(startupTimeoutMs)
        if (
            !hasRenderedFirstFrame &&
            playbackError == null &&
            !recoveryInProgress
        ) {
            handleSourceFailure(
                "This source did not start within " +
                    "${startupTimeoutMs / 1_000L} seconds."
            )
        }
    }

    LaunchedEffect(
        player,
        isBuffering,
        hasRenderedFirstFrame,
        retryGeneration,
    ) {
        if (
            !isBuffering ||
            !hasRenderedFirstFrame ||
            playbackError != null
        ) {
            return@LaunchedEffect
        }

        delay(PLAYER_REBUFFER_TIMEOUT_MS)
        if (
            isBuffering &&
            hasRenderedFirstFrame &&
            playbackError == null &&
            !recoveryInProgress
        ) {
            handleSourceFailure(
                "Playback remained stuck buffering for 25 seconds."
            )
        }
    }

    LaunchedEffect(
        player,
        playbackSpeed,
    ) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(
        player,
        mediaKey,
    ) {
        var librarySaveTicks = 0
        while (true) {
            delay(500L)
            val sampledPositionMs =
                player.currentPosition
                    .coerceAtLeast(0L)
            val sampledDurationMs =
                player.duration
                    .coerceAtLeast(0L)

            currentPositionMs = sampledPositionMs
            durationMs = sampledDurationMs

            if (sampledPositionMs > 5_000L) {
                lastValidPlaybackPositionMs =
                    sampledPositionMs
            }

            librarySaveTicks++
            if (librarySaveTicks >= 20) {
                val stablePositionMs =
                    if (sampledPositionMs > 5_000L) {
                        sampledPositionMs
                    } else {
                        lastValidPlaybackPositionMs
                    }

                if (stablePositionMs > 5_000L) {
                    playbackStore.savePositionMs(
                        mediaKey = mediaKey,
                        positionMs = stablePositionMs,
                        durationMs = sampledDurationMs,
                    )
                }
                librarySaveTicks = 0
            }
        }
    }

    LaunchedEffect(
        controlsVisible,
        isPlaying,
        controlsLocked,
        gestureActive,
        playerPanelVisible,
        nextEpisodeCardVisible,
    ) {
        if (
            controlsVisible &&
            isPlaying &&
            !controlsLocked &&
            !gestureActive &&
            !playerPanelVisible &&
            !nextEpisodeCardVisible
        ) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(
        currentPositionMs,
        durationMs,
        nextEpisode?.id,
        nextEpisodeCardDismissed,
        skipSegments,
    ) {
        if (
            nextEpisode != null &&
            !nextEpisodeCardDismissed &&
            durationMs > 0L
        ) {
            val endingStartMs = skipSegments
                .firstOrNull {
                    it.kind == PlayerSkipKind.ENDING
                }
                ?.startMs
            val remainingMs =
                (durationMs - currentPositionMs)
                    .coerceAtLeast(0L)
            val progress =
                currentPositionMs.toDouble() /
                    durationMs.toDouble()

            val reachedNextEpisodePoint =
                endingStartMs?.let {
                    currentPositionMs >= it
                } ?: (
                    progress >= .95 &&
                        remainingMs <= 60_000L
                    )

            if (reachedNextEpisodePoint) {
                showNextEpisodeCard = true
                controlsVisible = true
            }
        }
    }

    LaunchedEffect(nextEpisodeCountdown) {
        val count = nextEpisodeCountdown
        if (count != null && count > 0) {
            delay(1_000L)
            nextEpisodeCountdown = count - 1
        } else if (
            count == 0 &&
            nextEpisode != null
        ) {
            nextEpisodeCountdown = null
            startNextEpisode()
        }
    }

    LaunchedEffect(
        gestureMessage,
        gestureActive,
    ) {
        val message = gestureMessage
        if (message != null && !gestureActive) {
            delay(700L)
            if (
                gestureMessage == message &&
                !gestureActive
            ) {
                gestureMessage = null
            }
        }
    }

    PlayerFullscreenEffect(
        context = context,
        orientation =
            settingsStore.playerOrientation(),
    )

    if (resumePromptVisible) {
        PlayerResumePrompt(
            positionLabel =
                formatPlaybackTime(savedPositionMs),
            onResume = {
                player.seekTo(savedPositionMs)
                player.playWhenReady = true
                resumePromptVisible = false
            },
            onStartOver = {
                playbackStore.clearPosition(mediaKey)
                lastValidPlaybackPositionMs = 0L
                player.seekTo(0L)
                player.playWhenReady = true
                resumePromptVisible = false
            },
            onDismiss = {
                requestPlayerExit()
            },
        )
    }

    PlayerAudioWorkspace(
            visible = showAudioDialog,
            tracks = audioTracks,
            automaticSelected = audioAutomaticSelected,
            onAutomatic = {
                clearTrackOverride(
                    player = player,
                    trackType = C.TRACK_TYPE_AUDIO,
                    disable = false,
                )
                audioAutomaticSelected = true
                settingsStore.setLastAudioSelection(
                    PLAYER_AUDIO_AUTO
                )
                showAudioDialog = false
            },
            onSelect = { choice ->
                applyTrackChoice(
                    player = player,
                    trackType = C.TRACK_TYPE_AUDIO,
                    choice = choice,
                )
                audioAutomaticSelected = false
                settingsStore.setLastAudioSelection(
                    choice.selectionId
                )
                showAudioDialog = false
            },
            onDismiss = {
                showAudioDialog = false
            },
        )

    fun requestSubtitleChoice(choice: PlayerTrackChoice) {
        requestedSubtitleSelectionId = choice.selectionId
        fun commitSelection(selected: PlayerTrackChoice) {
            applyTrackChoice(
                player = player,
                trackType = C.TRACK_TYPE_TEXT,
                choice = selected,
            )
            subtitlesDisabled = false
            selectedSubtitleIsExternal =
                selected.selectionId.startsWith("external:")
            settingsStore.setSubtitleSelection(
                contentId = mediaKey,
                selectionId = selected.selectionId,
            )
            settingsStore.setLastSubtitleSelection(
                PlayerTrackPolicy.subtitleLanguageSelectionId(
                    selected.language
                )
            )
        }

        subtitlePreparationJob?.cancel()
        val externalSubtitle = choice.externalSubtitle
        if (externalSubtitle == null) {
            subtitlePreparationJob = null
            pendingSubtitleSelectionId = null
            translatingSubtitleSelectionId = null
            commitSelection(choice)
        } else {
            pendingSubtitleSelectionId = choice.selectionId
            translatingSubtitleSelectionId = null
            subtitlesDisabled = false
            // Keep Media3 away from a not-yet-ready external URL. The shared
            // readiness probe owns the single network request and the track is
            // enabled only after its bytes have entered the session cache.
            clearTrackOverride(
                player = player,
                trackType = C.TRACK_TYPE_TEXT,
                disable = true,
            )
            settingsStore.setSubtitleSelection(
                contentId = mediaKey,
                selectionId = choice.selectionId,
            )
            settingsStore.setLastSubtitleSelection(
                PlayerTrackPolicy.subtitleLanguageSelectionId(
                    choice.language
                )
            )
            subtitlePreparationJob = subtitleSelectionScope.launch {
                val ready = SubtitleReadinessProbe.awaitReady(
                    url = externalSubtitle.url,
                    onWaiting = {
                        if (pendingSubtitleSelectionId == choice.selectionId) {
                            translatingSubtitleSelectionId = choice.selectionId
                        }
                    },
                )
                var refreshWaitAttempts = 0
                while (
                    ready &&
                    subtitleTrackRefreshInProgress &&
                    refreshWaitAttempts < 200
                ) {
                    delay(50L)
                    refreshWaitAttempts += 1
                }
                var latestChoice: PlayerTrackChoice? = null
                var trackWaitAttempts = 0
                while (
                    ready &&
                    pendingSubtitleSelectionId == choice.selectionId &&
                    latestChoice == null &&
                    trackWaitAttempts < 200
                ) {
                    val latestExternalSubtitles = latestSubtitles.value
                        .associateBy(PlayerTrackPolicy::externalSubtitleSelectionId)
                    latestChoice = playerTrackChoices(
                        tracks = player.currentTracks,
                        trackType = C.TRACK_TYPE_TEXT,
                        externalSubtitles = latestExternalSubtitles,
                    ).firstOrNull {
                        it.selectionId == choice.selectionId
                    }
                    if (latestChoice == null) {
                        delay(50L)
                        trackWaitAttempts += 1
                    }
                }
                if (
                    ready &&
                    pendingSubtitleSelectionId == choice.selectionId &&
                    latestChoice != null
                ) {
                    commitSelection(latestChoice)
                    pendingSubtitleSelectionId = null
                    translatingSubtitleSelectionId = null
                }
                if (translatingSubtitleSelectionId == choice.selectionId) {
                    translatingSubtitleSelectionId = null
                }
                subtitlePreparationJob = null
            }
        }
    }

    PlayerSubtitleWorkspace(
            visible = showSubtitleDialog,
            tracks = textTracks,
            subtitlesDisabled = subtitlesDisabled,
            pendingSelectionId = pendingSubtitleSelectionId,
            translatingSelectionId = translatingSubtitleSelectionId,
            requestedSelectionId = requestedSubtitleSelectionId,
            secondaryLanguageCode = settingsStore
                .secondarySubtitleLanguage()
                .languageCode,
            visibilityPreferredLanguageCode = settingsStore
                .preferredSubtitleLanguage()
                .languageCode,
            preferredLanguageOnly =
                settingsStore.subtitleVisibility() ==
                    com.vueo.shared.core.storage.SubtitleVisibility.PREFERRED_ONLY,
            subtitleDelayMs = subtitleDelayMs,
            style = subtitleStyle,
            onDisable = {
                subtitlePreparationJob?.cancel()
                subtitlePreparationJob = null
                pendingSubtitleSelectionId = null
                translatingSubtitleSelectionId = null
                requestedSubtitleSelectionId = null
                clearTrackOverride(
                    player = player,
                    trackType = C.TRACK_TYPE_TEXT,
                    disable = true,
                )
                subtitlesDisabled = true
                selectedSubtitleIsExternal = false
                settingsStore.setSubtitleSelection(
                    contentId = mediaKey,
                    selectionId = PLAYER_SUBTITLE_OFF,
                )
                settingsStore.setLastSubtitleSelection(
                    PLAYER_SUBTITLE_OFF
                )
            },
            onSelect = { choice ->
                requestSubtitleChoice(choice)
            },
            onSubtitleDelayChange = { delayMs ->
                subtitleDelayMs =
                    delayMs.coerceIn(-60_000, 60_000)
                context.setPlayerSubtitleDelayMs(
                    mediaKey = mediaKey,
                    delayMs = subtitleDelayMs,
                )
            },
            onStyleChange = { updated ->
                subtitleStyle = updated
                settingsStore.setSubtitleFontSizeSp(
                    updated.fontSizeSp
                )
                settingsStore.setSubtitleBold(
                    updated.bold
                )
                settingsStore.setSubtitleTextColor(
                    updated.textColor
                )
                settingsStore.setSubtitleOutlineEnabled(
                    updated.outlineEnabled
                )
                settingsStore.setSubtitleOutlineColor(
                    updated.outlineColor
                )
                settingsStore.setSubtitleBottomPaddingPercent(
                    updated.bottomPaddingPercent
                )
            },
            onOpenStyle = {
                showSubtitleDialog = false
                showSubtitleStyleOverlay = true
                controlsVisible = false
            },
            onDismiss = {
                showSubtitleDialog = false
                controlsVisible = true
            },
        )

    if (showSubtitleStyleOverlay) {
        PlayerSubtitleStyleOverlay(
            subtitleDelayMs = subtitleDelayMs,
            style = subtitleStyle,
            onSubtitleDelayChange = { delayMs ->
                subtitleDelayMs =
                    delayMs.coerceIn(-60_000, 60_000)
                context.setPlayerSubtitleDelayMs(
                    mediaKey = mediaKey,
                    delayMs = subtitleDelayMs,
                )
            },
            onStyleChange = { updated ->
                subtitleStyle = updated
                settingsStore.setSubtitleFontSizeSp(updated.fontSizeSp)
                settingsStore.setSubtitleBold(updated.bold)
                settingsStore.setSubtitleTextColor(updated.textColor)
                settingsStore.setSubtitleOutlineEnabled(updated.outlineEnabled)
                settingsStore.setSubtitleOutlineColor(updated.outlineColor)
                settingsStore.setSubtitleBottomPaddingPercent(
                    updated.bottomPaddingPercent
                )
            },
            onDismiss = {
                showSubtitleStyleOverlay = false
                controlsVisible = true
            },
        )
    }

    PlayerSourcesWorkspace(
            visible = showSourceDialog,
            title = episode?.let {
                "S${it.season} E${it.episode} • ${it.title}"
            } ?: title,
            sources = playableSources,
            currentSource = source,
            currentPlaybackFailed = playbackError != null,
            failedSourceUrls = failedSourceUrls,
            providerOrder = sourceProviderOrder,
            originalLanguage = media.originalLanguage,
            switchingSourceUrl = switchingSourceUrl,
            onSelect = { candidate ->
                val switchPosition = player.currentPosition
                    .coerceAtLeast(0L)
                savePosition()
                switchingSourceUrl = candidate.url
                hasRenderedFirstFrame = false
                playbackPhase = PlayerPlaybackPhase.LOADING
                onSwitchSource(candidate, switchPosition)
            },
            onDismiss = {
                showSourceDialog = false
                switchingSourceUrl = null
            },
        )

    val episodeHistory = libraryStore.history()
            .filter { entry ->
                entry.media.type == media.type &&
                    entry.media.id == media.id
            }
        val progressByEpisodeId = episodes.associate { candidate ->
            val stored = episodeHistory.firstOrNull { entry ->
                entry.videoId == candidate.id ||
                    (
                        entry.season == candidate.season &&
                            entry.episode == candidate.episode
                    )
            }
            val isCurrent = candidate.id == episode?.id
            val candidateDurationMs = if (isCurrent) {
                durationMs
            } else {
                stored?.durationMs ?: 0L
            }
            val candidatePositionMs = if (isCurrent) {
                currentPositionMs
            } else {
                stored?.positionMs ?: 0L
            }
            val fraction = if (candidateDurationMs > 0L) {
                (
                    candidatePositionMs.toDouble() /
                        candidateDurationMs.toDouble()
                ).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            }

            candidate.id to PlayerEpisodeProgress(
                fraction = fraction,
                watched = stored?.isCompleted == true,
                positionMs = candidatePositionMs,
            )
        }

    PlayerEpisodesWorkspace(
            visible = showEpisodeDialog,
            seriesTitle = media.name,
            episodes = episodes,
            currentEpisode = episode,
            progressByEpisodeId = progressByEpisodeId,
            switchingEpisodeId =
                episodeSwitchingTo?.id,
            switchingFailed =
                episodeSwitchFailed,
            onEpisodeSelected = { candidate ->
                savePosition()
                nextEpisodeCountdown = null
                showNextEpisodeCard = false
                nextEpisodeCardDismissed = true
                onEpisodeSelected(candidate)
            },
            onDismiss = { showEpisodeDialog = false },
        )

    PlayerMoreWorkspace(
            visible = showMoreDialog,
            playbackSpeed = playbackSpeed,
            videoFit = videoFit,
            autoPlayNextEpisode = autoPlayNextEpisode,
            skipSegmentsEnabled = skipSegmentsEnabled,
            contentWarningsEnabled = contentWarningsEnabled,
            onPlaybackSpeedChange = { speed ->
                playbackSpeed = speed
                settingsStore.setPlayerPlaybackSpeed(speed)
                player.setPlaybackSpeed(speed)
            },
            onVideoFitChange = { fit ->
                videoFit = fit
                settingsStore.setPlayerVideoFit(fit)
            },
            onAutoPlayNextEpisodeChange = { enabled ->
                autoPlayNextEpisode = enabled
                settingsStore.setAutoPlayNextEpisodeEnabled(enabled)
                if (!enabled) {
                    nextEpisodeCountdown = null
                }
            },
            onSkipSegmentsChange = { enabled ->
                skipSegmentsEnabled = enabled
                settingsStore.setSkipSegmentsEnabled(enabled)
            },
            onContentWarningsChange = { enabled ->
                contentWarningsEnabled = enabled
                settingsStore.setContentWarningsEnabled(enabled)
                if (!enabled) {
                    showContentWarnings = false
                }
            },
            onReset = {
                playbackSpeed = 1f
                player.setPlaybackSpeed(1f)
                settingsStore.setPlayerPlaybackSpeed(1f)
                videoFit = PlayerVideoFit.FIT
                settingsStore.setPlayerVideoFit(PlayerVideoFit.FIT)
                autoPlayNextEpisode = true
                settingsStore.setAutoPlayNextEpisodeEnabled(true)
                skipSegmentsEnabled = true
                settingsStore.setSkipSegmentsEnabled(true)
                contentWarningsEnabled = true
                settingsStore.setContentWarningsEnabled(true)
                gestureMessage = "Player controls reset"
            },
            onDismiss = { showMoreDialog = false },
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { playerContext ->
                PlayerView(playerContext).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    applyVueoSubtitleStyle(
                        style = subtitleStyle,
                        fontScale = 1f,
                    )
                    resizeMode = videoFit.toMedia3ResizeMode()
                }
            },
            update = { view ->
                view.player = player
                view.useController = false
                view.applyVueoSubtitleStyle(
                    style = subtitleStyle,
                    fontScale = 1f,
                )
                view.resizeMode = videoFit.toMedia3ResizeMode()
            },
        )

        if (!controlsLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        player,
                        durationMs,
                        controlsLocked,
                    ) {
                        try {
                            awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var mode = 0
                            var zoomAmount = 1f
                            var seekPreview: Long? = null
                            val startX = down.position.x
                            val startPosition =
                                player.currentPosition
                            val startVolume =
                                audioManager
                                    .getStreamVolume(
                                        AudioManager.STREAM_MUSIC
                                    )
                            val startBrightness =
                                activity?.window
                                    ?.attributes
                                    ?.screenBrightness
                                    ?.takeIf {
                                        it >= 0f
                                    }
                                    ?: 0.5f

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed =
                                    event.changes.filter {
                                        it.pressed
                                    }

                                if (pressed.size >= 2) {
                                    if (mode != 3) {
                                        mode = 3
                                        gestureActive = true
                                        gestureSeekPositionMs = null
                                    }
                                    zoomAmount *=
                                        event.calculateZoom()

                                    if (zoomAmount > 1.06f) {
                                        videoFit = PlayerVideoFit.ZOOM
                                        settingsStore.setPlayerVideoFit(
                                            PlayerVideoFit.ZOOM
                                        )
                                        gestureMessage = "Zoom"
                                    } else if (
                                        zoomAmount < .94f
                                    ) {
                                        videoFit = PlayerVideoFit.FIT
                                        settingsStore.setPlayerVideoFit(
                                            PlayerVideoFit.FIT
                                        )
                                        gestureMessage = "Fit"
                                    }

                                    event.changes.forEach {
                                        it.consume()
                                    }
                                } else if (
                                    pressed.size == 1 &&
                                    mode != 3
                                ) {
                                    val change = pressed.first()
                                    val movement =
                                        change.positionChange()
                                    totalX += movement.x
                                    totalY += movement.y

                                    if (
                                        mode == 0 &&
                                        (
                                            kotlin.math.abs(totalX) >
                                                viewConfiguration.touchSlop ||
                                                kotlin.math.abs(totalY) >
                                                viewConfiguration.touchSlop
                                        )
                                    ) {
                                        mode =
                                            if (
                                                kotlin.math.abs(totalX) >=
                                                kotlin.math.abs(totalY)
                                            ) {
                                                1
                                            } else {
                                                2
                                            }
                                        gestureActive = true
                                    }

                                    when (mode) {
                                        1 -> {
                                            val span =
                                                durationMs
                                                    .takeIf {
                                                        it > 0L
                                                    }
                                                    ?: 60L * 60L * 1000L
                                            val seekWindowMs =
                                                minOf(
                                                    span / 4L,
                                                    seekGestureSensitivity
                                                        .maxSeekMinutes *
                                                        60L * 1000L,
                                                )
                                            val preview =
                                                (startPosition +
                                                    (
                                                        seekWindowMs *
                                                            (totalX / size.width)
                                                    ).toLong())
                                                    .coerceIn(
                                                        0L,
                                                        durationMs
                                                            .takeIf {
                                                                it > 0L
                                                            }
                                                            ?: Long.MAX_VALUE,
                                                    )
                                            seekPreview = preview
                                            gestureSeekPositionMs = preview
                                            gestureMessage =
                                                formatPlaybackTime(preview) +
                                                    " / " +
                                                    formatPlaybackTime(
                                                        durationMs
                                                    )
                                            change.consume()
                                        }

                                        2 -> {
                                            val delta =
                                                -totalY / size.height
                                            if (
                                                startX < size.width / 2f
                                            ) {
                                                val brightness =
                                                    (startBrightness + delta)
                                                        .coerceIn(
                                                            .02f,
                                                            1f,
                                                        )
                                                activity?.window?.let {
                                                    window ->
                                                    val attributes =
                                                        window.attributes
                                                    attributes.screenBrightness =
                                                        brightness
                                                    window.attributes =
                                                        attributes
                                                }
                                                gestureMessage =
                                                    "Brightness " +
                                                        "${(brightness * 100).toInt()}%"
                                            } else {
                                                val maxVolume =
                                                    audioManager
                                                        .getStreamMaxVolume(
                                                            AudioManager.STREAM_MUSIC
                                                        )
                                                val volume =
                                                    (startVolume +
                                                        delta * maxVolume)
                                                        .toInt()
                                                        .coerceIn(
                                                            0,
                                                            maxVolume,
                                                        )
                                                audioManager
                                                    .setStreamVolume(
                                                        AudioManager.STREAM_MUSIC,
                                                        volume,
                                                        0,
                                                    )
                                                gestureMessage =
                                                    "Volume " +
                                                        "${(volume * 100 / maxVolume.coerceAtLeast(1))}%"
                                            }
                                            change.consume()
                                        }
                                    }
                                }

                                if (
                                    event.changes.none {
                                        it.pressed
                                    }
                                ) {
                                    break
                                }
                            }

                            if (mode == 1) {
                                seekPreview?.let {
                                    player.seekTo(it)
                                }
                            }
                                gestureSeekPositionMs = null
                                gestureActive = false
                            }
                        } finally {
                            gestureSeekPositionMs = null
                            gestureActive = false
                        }
                    }
                    .pointerInput(
                        player,
                        controlsLocked,
                        showNextEpisodeCard,
                        nextEpisodeCardSwitchTarget,
                    ) {
                        detectTapGestures(
                            onTap = {
                                if (
                                    showNextEpisodeCard &&
                                    nextEpisodeCardSwitchTarget == null
                                ) {
                                    nextEpisodeCountdown = null
                                    showNextEpisodeCard = false
                                    nextEpisodeCardDismissed = true
                                } else {
                                    controlsVisible =
                                        !controlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                when {
                                    offset.x <
                                        size.width * .34f -> {
                                        player.seekTo(
                                            (player.currentPosition -
                                                10_000L)
                                                .coerceAtLeast(0L)
                                        )
                                        gestureMessage = "-10 sec"
                                    }

                                    offset.x >
                                        size.width * .66f -> {
                                        player.seekTo(
                                            player.currentPosition +
                                                10_000L
                                        )
                                        gestureMessage = "+10 sec"
                                    }

                                    else -> {
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                    }
                                }
                                controlsVisible = true
                            },
                        )
                    },
            )
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
                PlayerContentWarningsOverlay(
                    warnings = contentWarnings,
                    onAnimationComplete = {
                        showContentWarnings = false
                    },
                )
            }
        }

        val activeSkipSegment = skipSegments
            .firstOrNull {
                currentPositionMs >= it.startMs &&
                    currentPositionMs < it.endMs
            }
            ?.takeUnless {
                it.key == dismissedSkipSegmentKey ||
                    (
                        it.kind == PlayerSkipKind.ENDING &&
                            nextEpisode != null
                        )
            }

        if (!controlsLocked) {
            PlayerSkipControl(
                segment = activeSkipSegment,
                onSkip = {
                    activeSkipSegment?.let { segment ->
                        dismissedSkipSegmentKey = segment.key
                        player.seekTo(
                            segment.endMs.coerceAtMost(
                                durationMs.takeIf { it > 0L }
                                    ?: segment.endMs
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 118.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !controlsLocked,
            enter = vueoSoftEnter(
                durationMillis = 220,
                initialScale = 0.996f,
            ),
            exit = vueoSoftExit(
                durationMillis = 150,
                targetScale = 0.998f,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(
                                    alpha = .62f
                                ),
                                Color.Transparent,
                                Color.Black.copy(
                                    alpha = .70f
                                ),
                            )
                        )
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                if (showContentWarnings) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        episode?.let {
                            "S${it.season} E${it.episode} • ${it.title}"
                        } ?: title,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (
                    playbackPhase == PlayerPlaybackPhase.LOADING ||
                    playbackPhase == PlayerPlaybackPhase.BUFFERING ||
                    playbackPhase == PlayerPlaybackPhase.RECOVERING
                ) {
                    Text(
                        when (playbackPhase) {
                            PlayerPlaybackPhase.LOADING -> "LOADING SOURCE"
                            PlayerPlaybackPhase.BUFFERING -> "BUFFERING"
                            PlayerPlaybackPhase.RECOVERING -> "TRYING NEXT SOURCE"
                            else -> ""
                        },
                        color = VueoPalette.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (nextEpisode != null) {
                    PlayerTopAction(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Next episode",
                        enabled = !nextEpisodeSwitching,
                        onClick = {
                            startNextEpisode()
                        },
                    )

                    Spacer(Modifier.width(8.dp))
                }

                PlayerTopAction(
                    icon = Icons.Default.Lock,
                    contentDescription =
                        "Lock controls",
                    onClick = {
                        controlsLocked = true
                        controlsVisible = false
                    },
                )

                Spacer(Modifier.width(8.dp))

                PlayerTopAction(
                    icon = Icons.Default.MoreHoriz,
                    contentDescription = "More controls",
                    onClick = {
                        showMoreDialog = true
                    },
                )

                Spacer(Modifier.width(8.dp))

                PlayerTopAction(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick = {
                        savePosition()
                        requestPlayerExit()
                    },
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(24.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                PlayerRoundAction(
                    icon = Icons.Default.Replay10,
                    contentDescription =
                        "Rewind 10 seconds",
                    onClick = {
                        player.seekTo(
                            (player.currentPosition -
                                10_000L)
                                .coerceAtLeast(0L)
                        )
                    },
                )
                PlayerRoundAction(
                    icon =
                        if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                    contentDescription =
                        if (isPlaying) "Pause" else "Play",
                    primary = true,
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        controlsVisible = true
                    },
                )
                PlayerRoundAction(
                    icon = Icons.Default.Forward10,
                    contentDescription =
                        "Forward 10 seconds",
                    onClick = {
                        player.seekTo(
                            player.currentPosition +
                                10_000L
                        )
                    },
                )
            }

            val nextEpisodeCardEpisode =
                nextEpisodeCardSwitchTarget
                    ?: nextEpisode
            AnimatedVisibility(
                visible =
                    nextEpisodeCardEpisode != null &&
                        nextEpisodeCardVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = 126.dp,
                    ),
                enter = vueoSoftEnter(
                    durationMillis = 240,
                    initialScale = 0.975f,
                ),
                exit = vueoSoftExit(
                    durationMillis = 150,
                    targetScale = 0.99f,
                ),
            ) {
                nextEpisodeCardEpisode?.let { cardEpisode ->
                PlayerNextEpisodeCard(
                    episode = cardEpisode,
                    countdownSeconds =
                        nextEpisodeCountdown,
                    switching =
                        nextEpisodeCardSwitchTarget != null &&
                            !episodeSwitchFailed,
                    failed =
                        nextEpisodeCardSwitchTarget != null &&
                            episodeSwitchFailed,
                    onPlay = {
                        if (
                            episodeSwitchFailed &&
                            nextEpisodeCardSwitchTarget != null
                        ) {
                            nextEpisodeSwitching = true
                            nextEpisodeCountdown = null
                            savePosition()
                            onEpisodeSelected(
                                cardEpisode
                            )
                        } else {
                            startNextEpisode()
                        }
                    },
                    modifier = Modifier,
                )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp),
            ) {
                playbackError?.let { error ->
                    PlayerPlaybackErrorCard(
                        error = error,
                        canChooseSource =
                            playableSources.size > 1,
                        onRetry = {
                            sourceRecoverySession
                                .allowRetry(source)
                            failedSourceUrls =
                                sourceRecoverySession
                                    .failedSourceUrls()
                            playbackError = null
                            playbackPhase =
                                PlayerPlaybackPhase.LOADING
                            recoveryInProgress = false
                            hasRenderedFirstFrame = false
                            retryGeneration++
                            player.prepare()
                            player.play()
                        },
                        onChooseSource = {
                            switchingSourceUrl = null
                            showSourceDialog = true
                        },
                        modifier = Modifier.align(
                            Alignment.End
                        ),
                    )
                }

                val displayedPosition =
                    gestureSeekPositionMs
                        ?: currentPositionMs
                val progressFraction =
                    if (durationMs > 0L) {
                        (
                            displayedPosition.toFloat() /
                                durationMs.toFloat()
                        ).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                Slider(
                    value = displayedPosition
                        .toFloat()
                        .coerceIn(
                            0f,
                            durationMs
                                .coerceAtLeast(1L)
                                .toFloat(),
                        ),
                    onValueChange = { value ->
                        gestureSeekPositionMs =
                            value.toLong()
                    },
                    onValueChangeFinished = {
                        gestureSeekPositionMs
                            ?.let {
                                player.seekTo(it)
                            }
                        gestureSeekPositionMs = null
                    },
                    valueRange =
                        0f..durationMs
                            .coerceAtLeast(1L)
                            .toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    VueoPlayerAccent,
                                    CircleShape,
                                )
                        )
                    },
                    track = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Color.White.copy(
                                        alpha = .30f
                                    )
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        progressFraction
                                    )
                                    .fillMaxHeight()
                                    .background(
                                        VueoPlayerAccent
                                    )
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        formatPlaybackTime(
                            displayedPosition
                        ),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatPlaybackTime(durationMs),
                        color = Color.White.copy(
                            alpha = .72f
                        ),
                        fontSize = 11.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-6).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(
                                alpha = .16f
                            ),
                            shape = RoundedCornerShape(30.dp),
                        ),
                        shape = RoundedCornerShape(30.dp),
                        color = Color(
                            0xD9161719
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 3.dp,
                            ),
                            horizontalArrangement =
                                Arrangement.Center,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            PlayerPanelAction(
                                icon = Icons.Default.ClosedCaption,
                                label = "Subs",
                                onClick = {
                                    controlsVisible = false
                                    showSubtitleStyleOverlay = false
                                    showSubtitleDialog = true
                                },
                            )
                            PlayerPanelAction(
                                icon = Icons.Default.VolumeUp,
                                label = "Audio",
                                onClick = {
                                    showAudioDialog = true
                                },
                            )
                            PlayerPanelAction(
                                icon = Icons.Default.Dns,
                                label = "Sources",
                                enabled =
                                    playableSources.isNotEmpty(),
                                onClick = {
                                    switchingSourceUrl = null
                                    showSourceDialog = true
                                },
                            )
                            if (episodes.isNotEmpty()) {
                                PlayerPanelAction(
                                    icon = Icons.Default.VideoLibrary,
                                    label = "Episodes",
                                    onClick = {
                                        showEpisodeDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        AnimatedVisibility(
            visible = controlsLocked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            enter = vueoSoftEnter(
                durationMillis = 200,
                initialScale = 0.97f,
            ),
            exit = vueoSoftExit(
                durationMillis = 140,
                targetScale = 0.985f,
            ),
        ) {
            Surface(
                modifier = Modifier.clickable {
                    controlsLocked = false
                    controlsVisible = true
                },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(
                    alpha = .62f
                ),
            ) {
                Text(
                    "Unlock",
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 10.dp,
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedContent(
            targetState = gestureMessage,
            transitionSpec = {
                vueoPlayerFadeThrough(
                    enterDurationMillis = 170,
                    exitDurationMillis = 120,
                    enterDelayMillis = 0,
                )
            },
            modifier = Modifier.align(Alignment.Center),
            label = "VUEO player gesture feedback",
        ) { message ->
            if (message != null) {
                Surface(
                    modifier = Modifier.padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = .74f),
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 12.dp,
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
