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
import android.os.SystemClock
import android.widget.Toast
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

@Composable
internal fun MediaDetailsScreen(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    initialItem: MediaItem,
    initialLibraryEntry:
        LibraryPlaybackEntry?,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onEntityClick: (MediaEntityTarget) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }
    val libraryStore = remember {
        LibraryStore(
            context.applicationContext
        )
    }
    val detailsProfileStore =
        remember {
            ProfileStore(
                context.applicationContext
            )
        }

    val detailsDnaPreferences =
        remember {
            UserDnaPreferences(
                context.applicationContext
            )
        }

    val detailsDnaEngine =
        remember(
            libraryStore
        ) {
            UserDnaEngine(
                libraryStore
            )
        }

    val detailsProfileId =
        remember {
            detailsProfileStore
                .activeProfileId()
        }

    val showDnaMatch =
        detailsDnaPreferences
            .shouldShowDnaMatch(
                detailsProfileId
            )

    val detailsDnaSnapshot =
        remember(
            detailsProfileId,
            showDnaMatch,
            initialItem.id,
            initialItem.type,
        ) {
            if (
                showDnaMatch
            ) {
                detailsDnaEngine
                    .build()
            } else {
                null
            }
        }

    val pluginEngine = remember {
        PluginSourceEngine(
            context = context,
            store = pluginStore,
        )
    }

    val sourceDiscoveryEngine = remember(
        engine,
        pluginEngine,
        pluginStore,
    ) {
        SourceDiscoveryEngine(
            mediaEngine = engine,
            pluginEngine = pluginEngine,
            pluginStore = pluginStore,
        )
    }

    val preferredSourceQuality =
        settingsStore
            .preferredQuality()
            .rankKey

    val showSourceTechnicalDetails =
        settingsStore
            .showSourceTechnicalDetails()

    var item by remember(initialItem) { mutableStateOf(initialItem) }
    var loadingMeta by remember { mutableStateOf(true) }
    var loadingStreams by remember {
        mutableStateOf(false)
    }
    var sourceStatus by remember {
        mutableStateOf<String?>(null)
    }
    var relatedItems by remember {
        mutableStateOf<
            List<MediaItem>
        >(emptyList())
    }
    var tmdbMoreLikeThisEnabled by remember {
        mutableStateOf(false)
    }
    var ratings by remember {
        mutableStateOf<
            List<MediaRating>
        >(emptyList())
    }
    var supplementalRatings by remember {
        mutableStateOf<
            List<MediaRating>
        >(emptyList())
    }

    var inWatchlist by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf(
            libraryStore
                .isWatchlisted(
                    initialItem
                )
        )
    }

    var detailPlaybackEntries by remember(
        initialItem.id,
        initialItem.type,
        initialLibraryEntry,
    ) {
        mutableStateOf(
            (
                libraryStore
                    .continueWatching() +
                    libraryStore
                        .history() +
                    listOfNotNull(
                        initialLibraryEntry
                    )
            )
                .distinctBy { entry ->
                    listOf(
                        entry.media.type,
                        entry.media.id,
                        entry.season
                            ?.toString()
                            .orEmpty(),
                        entry.episode
                            ?.toString()
                            .orEmpty(),
                    ).joinToString(
                        ":"
                    )
                }
        )
    }

    fun refreshDetailPlaybackEntries() {
        detailPlaybackEntries =
            (
                libraryStore
                    .continueWatching() +
                    libraryStore
                        .history() +
                    listOfNotNull(
                        initialLibraryEntry
                    )
            )
                .distinctBy { entry ->
                    listOf(
                        entry.media.type,
                        entry.media.id,
                        entry.season
                            ?.toString()
                            .orEmpty(),
                        entry.episode
                            ?.toString()
                            .orEmpty(),
                    ).joinToString(
                        ":"
                    )
                }
    }

    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    var episodeSelectionTouchedByUser by remember(
        initialItem.id,
        initialItem.type,
        initialItem.sourceExtensionId,
    ) {
        mutableStateOf(false)
    }

    var sourcePickerStreams by remember {
        mutableStateOf<List<StreamSource>?>(null)
    }
    var sourcePickerSubtitles by remember {
        mutableStateOf<List<SubtitleTrack>>(emptyList())
    }
    var sourcePickerNotice by remember {
        mutableStateOf<String?>(null)
    }
    var sourcePickerRawCount by remember {
        mutableIntStateOf(0)
    }
    var sourcePickerSearching by remember {
        mutableStateOf(false)
    }
    var sourcePickerProgress by remember {
        mutableStateOf("Ready")
    }
    var sourcePickerFirstResultMs by remember {
        mutableStateOf<Long?>(null)
    }
    var sourcePickerProviderOrder by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var sourceDiscoveryJob by remember {
        mutableStateOf<Job?>(null)
    }
    var deferredSubtitleDiscoveryJob by remember {
        mutableStateOf<Job?>(null)
    }
    var selectedPlaybackSource by remember {
        mutableStateOf<StreamSource?>(null)
    }
    var selectedPlaybackVideoId by remember {
        mutableStateOf<String?>(null)
    }
    var selectedPlaybackStartPositionMs by remember {
        mutableStateOf(0L)
    }
    var pendingPlaybackEpisode by remember {
        mutableStateOf<EpisodeItem?>(null)
    }
    var pendingPlaybackFailed by remember {
        mutableStateOf(false)
    }

    fun publishDetailsRatings(
        media: MediaItem,
    ) {
        ratings =
            (
                baseDetailsRatings(
                    media
                ) +
                    supplementalRatings
            )
                .associateBy {
                    it.source
                }
                .values
                .toList()
    }

    fun syncEpisodeSelection(
        media: MediaItem,
        preserveCurrent: Boolean,
    ) {
        if (
            media.type != "series" ||
            media.episodes.isEmpty()
        ) {
            selectedSeason = null
            selectedEpisode = null
            return
        }

        if (
            preserveCurrent &&
            episodeSelectionTouchedByUser
        ) {
            val current =
                selectedEpisode
                    ?.let { selected ->
                        media.episodes
                            .firstOrNull { candidate ->
                                candidate.season ==
                                    selected.season &&
                                    candidate.episode ==
                                    selected.episode
                            }
                    }

            if (current != null) {
                selectedSeason =
                    current.season
                selectedEpisode =
                    current
                return
            }
        }

        val playbackTarget =
            detailsPlaybackTargetEpisode(
                media = media,
                entries =
                    libraryStore
                        .history(),
                initialEntry =
                    initialLibraryEntry,
            )

        val target =
            playbackTarget
                ?: orderedDetailsEpisodes(
                    media.episodes
                ).firstOrNull()

        selectedSeason =
            target?.season
        selectedEpisode =
            target
    }

    LaunchedEffect(
        initialItem.id,
        initialItem.type,
        initialItem.sourceExtensionId,
    ) {
        loadingMeta = true
        relatedItems = emptyList()
        tmdbMoreLikeThisEnabled = false
        supplementalRatings = emptyList()

        // Instant detail shell: publish everything already carried by the
        // catalog/search item before any network metadata work starts.
        val shellItem =
            DetailUpstreamPolicy.normalizeSeriesEpisodes(
                initialItem
            )

        item = shellItem
        publishDetailsRatings(
            shellItem
        )
        syncEpisodeSelection(
            media = shellItem,
            preserveCurrent = false,
        )
        inWatchlist =
            libraryStore
                .isWatchlisted(
                    shellItem
                )
        refreshDetailPlaybackEntries()

        val tmdbKey =
            pluginStore
                .tmdbApiKey()

        tmdbMoreLikeThisEnabled =
            tmdbKey.isNotBlank() &&
            (
                settingsStore
                    .tmdbRecommendationsEnabled() ||
                settingsStore
                    .tmdbSimilarTitlesEnabled()
            )

        val preparedItem =
            if (
                tmdbKey.isNotBlank() &&
                initialItem.id
                    .startsWith(
                        "tmdb:"
                    )
            ) {
                runCatching {
                    DetailUpstreamPolicy.prepareForCore(
                        item = initialItem,
                        tmdbApiKey = tmdbKey,
                    )
                }.getOrDefault(
                    initialItem
                )
            } else {
                initialItem
            }

        // Core Stremio metadata is the only stage that controls the
        // "still resolving" state. The page itself remains fully visible.
        val coreItem =
            DetailUpstreamPolicy.normalizeSeriesEpisodes(
                engine.loadMeta(
                    preparedItem
                )
            )

        item = coreItem
        publishDetailsRatings(
            coreItem
        )
        syncEpisodeSelection(
            media = coreItem,
            preserveCurrent = true,
        )
        inWatchlist =
            libraryStore
                .isWatchlisted(
                    coreItem
                )
        refreshDetailPlaybackEntries()

        // Do not make TMDB/Rich Details/ratings/recommendations part of the
        // perceived page load. Core meta is enough to release the UI.
        loadingMeta = false

        val resolvedItem =
            coreItem

        val localRelated =
            RelatedContentOrchestrator.local(
                item = resolvedItem,
                limit = 18,
            )

        relatedItems =
            localRelated

        // Metadata/artwork, episode enrichment and rich credits continue in
        // the background and progressively update the already-visible page.
        launch {
            var enrichedItem =
                resolvedItem

            if (
                tmdbKey.isNotBlank() &&
                (
                    settingsStore
                        .tmdbMetadataEnrichmentEnabled() ||
                    settingsStore
                        .tmdbArtworkEnrichmentEnabled()
                )
            ) {
                enrichedItem =
                    runCatching {
                        DetailUpstreamPolicy.enrichTmdb(
                            media = enrichedItem,
                            tmdbApiKey = tmdbKey,
                            metadataEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
                            artworkEnabled = settingsStore.tmdbArtworkEnrichmentEnabled(),
                        )
                    }.getOrDefault(enrichedItem)

                item =
                    enrichedItem
                publishDetailsRatings(
                    enrichedItem
                )
                syncEpisodeSelection(
                    media = enrichedItem,
                    preserveCurrent = true,
                )
            }

            if (
                tmdbKey.isNotBlank() &&
                settingsStore
                    .tmdbMetadataEnrichmentEnabled()
            ) {
                enrichedItem =
                    runCatching {
                        DetailUpstreamPolicy.enrichRichDetails(
                            media = enrichedItem,
                            tmdbApiKey = tmdbKey,
                            enabled = true,
                        )
                    }.getOrDefault(enrichedItem)

                item =
                    enrichedItem
                publishDetailsRatings(
                    enrichedItem
                )
            }
        }

        launch {
            relatedItems =
                RelatedContentOrchestrator.mergeRemote(
                    item = resolvedItem,
                    localItems = localRelated,
                    apiKey = tmdbKey,
                    recommendationsEnabled =
                        settingsStore
                            .tmdbRecommendationsEnabled(),
                    similarEnabled =
                        settingsStore
                            .tmdbSimilarTitlesEnabled(),
                    limit = 18,
                )
        }

        launch {
            val mdblistKey =
                settingsStore
                    .mdblistApiKey()

            if (
                mdblistKey.isBlank() ||
                !settingsStore
                    .mdblistRatingsEnabled()
            ) {
                return@launch
            }

            val fetched =
                runCatching {
                    MdblistClient
                        .ratings(
                            media =
                                resolvedItem,
                            apiKey =
                                mdblistKey,
                        )
                }.getOrDefault(
                    emptyList()
                )

            supplementalRatings =
                fetched.filter {
                    rating ->
                    when (rating.source) {
                        "imdb" ->
                            settingsStore
                                .mdblistImdbEnabled()

                        "tomatoes" ->
                            settingsStore
                                .mdblistRottenTomatoesEnabled()

                        "metacritic" ->
                            settingsStore
                                .mdblistMetacriticEnabled()

                        "tmdb" ->
                            settingsStore
                                .mdblistTmdbRatingEnabled()

                        "trakt" ->
                            settingsStore
                                .mdblistTraktEnabled()

                        else -> false
                    }
                }

            publishDetailsRatings(
                item
            )
        }
    }


    fun startSourceDiscovery(
        targetEpisode: EpisodeItem?,
        startPositionMs: Long = 0L,
        autoPlayFirst: Boolean = false,
    ) {
        selectedPlaybackStartPositionMs = startPositionMs.coerceAtLeast(0L)

        val targetVideoId = selectedVideoId(
            media = item,
            episode = targetEpisode,
        ) ?: return

        sourceDiscoveryJob?.cancel()
        deferredSubtitleDiscoveryJob?.cancel()

        var autoPlayCommitted = false
        var subtitlesResolved = false
        var sourceDiscoveryCompleted = false
        var latestAutoPlayCandidates = emptyList<StreamSource>()

        fun commitAutoPlayIfReady(
            candidates: List<StreamSource>,
            allowLowQualityFallback: Boolean = false,
        ) {
            latestAutoPlayCandidates = candidates
            if (!autoPlayFirst || autoPlayCommitted || !subtitlesResolved) return

            val directCandidates = candidates
                .filter { it.isDirectPlayable }
                .sortedWith(
                    PlayerSourcePolicy.comparator(
                        preferredQuality = preferredSourceQuality,
                        originalLanguage = item.originalLanguage,
                    )
                )
            val candidate = directCandidates.firstOrNull { source ->
                PlayerSourcePolicy.assess(
                    source = source,
                    preferredQuality = preferredSourceQuality,
                    originalLanguage = item.originalLanguage,
                ).let { assessment ->
                    assessment.quality.automaticRecoveryEligible &&
                        assessment.audioMatch.recommendationEligible
                }
            } ?: directCandidates
                .firstOrNull()
                ?.takeIf { allowLowQualityFallback }
                ?: return

            autoPlayCommitted = true
            selectedSeason = targetEpisode?.season ?: selectedSeason
            selectedEpisode = targetEpisode
            selectedPlaybackVideoId = targetVideoId
            selectedPlaybackSource = candidate
        }

        sourcePickerStreams = emptyList()
        sourcePickerProviderOrder = emptyList()
        sourcePickerSubtitles = emptyList()
        sourcePickerRawCount = 0
        sourcePickerNotice = null
        sourcePickerSearching = true
        sourcePickerFirstResultMs = null
        sourcePickerProgress = "Starting source discovery…"
        loadingStreams = true
        sourceStatus = null

        sourceDiscoveryJob = scope.launch {
            try {
                sourceDiscoveryEngine.discover(
                    request = SourceDiscoveryRequest(
                        item = item,
                        episode = targetEpisode,
                        videoId = targetVideoId,
                        preferredQuality = preferredSourceQuality,
                    ),
                ) { snapshot ->
                    sourcePickerStreams = snapshot.bundle.sources
                    sourcePickerProviderOrder = snapshot.providerOrder
                    sourcePickerSubtitles =
                        (sourcePickerSubtitles + snapshot.bundle.subtitles)
                            .distinctBy { it.url }
                    sourcePickerRawCount = snapshot.rawCount
                    sourcePickerNotice = snapshot.notice
                    sourcePickerSearching = snapshot.searching
                    sourcePickerFirstResultMs = snapshot.firstResultMs
                    sourcePickerProgress = snapshot.progress
                    loadingStreams = snapshot.searching

                    subtitlesResolved = snapshot.subtitlesResolved
                    sourceDiscoveryCompleted = !snapshot.searching
                    commitAutoPlayIfReady(
                        candidates = snapshot.bundle.sources,
                        allowLowQualityFallback = sourceDiscoveryCompleted,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                sourceDiscoveryCompleted = true
                sourcePickerSearching = false
                loadingStreams = false
                sourcePickerProgress =
                    if (latestAutoPlayCandidates.isEmpty()) {
                        "Search complete • no sources found"
                    } else {
                        "Search complete • ${latestAutoPlayCandidates.size} unique sources"
                    }
                commitAutoPlayIfReady(
                    candidates = latestAutoPlayCandidates,
                    allowLowQualityFallback = true,
                )
            }
        }
    }

    fun requestDeferredSubtitles() {
        val targetVideoId = selectedPlaybackVideoId ?: return
        deferredSubtitleDiscoveryJob?.cancel()
        deferredSubtitleDiscoveryJob = scope.launch {
            sourceDiscoveryEngine.discoverSubtitles(
                type = item.type,
                videoId = targetVideoId,
            ) { discovered ->
                sourcePickerSubtitles =
                    (sourcePickerSubtitles + discovered)
                        .distinctBy { it.url }
            }.also { discovered ->
                sourcePickerSubtitles =
                    (sourcePickerSubtitles + discovered)
                        .distinctBy { it.url }
            }
        }
    }

    val playbackSource = selectedPlaybackSource
    val playbackVideoId = selectedPlaybackVideoId

    BackHandler(
        enabled =
            playbackSource == null &&
                sourcePickerStreams == null,
    ) {
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryJob = null
        loadingStreams = false
        onBack()
    }

    var transitionSourceStreams by remember {
        mutableStateOf<List<StreamSource>>(emptyList())
    }
    var transitionPlaybackSource by remember {
        mutableStateOf<StreamSource?>(null)
    }
    var transitionPlaybackVideoId by remember {
        mutableStateOf<String?>(null)
    }

    sourcePickerStreams?.let { streams ->
        transitionSourceStreams = streams
    }
    playbackSource?.let { source ->
        transitionPlaybackSource = source
    }
    playbackVideoId?.let { videoId ->
        transitionPlaybackVideoId = videoId
    }

    val detailSurface =
        when {
            playbackSource != null && playbackVideoId != null ->
                DetailSurface.PLAYER
            sourcePickerStreams != null ->
                DetailSurface.SOURCES
            else -> DetailSurface.DETAILS
        }

    AnimatedContent(
        targetState = detailSurface,
        transitionSpec = {
            if (
                initialState == DetailSurface.PLAYER ||
                targetState == DetailSurface.PLAYER
            ) {
                vueoPlayerFadeThrough()
            } else {
                vueoFadeThrough(
                    enterDurationMillis = 320,
                    exitDurationMillis = 180,
                    enterDelayMillis = 36,
                    initialScale = 0.992f,
                    targetScale = 0.996f,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "VUEO detail source player transition",
    ) { surface ->
        when (surface) {
            DetailSurface.PLAYER -> {
                val playerSource = transitionPlaybackSource
                val playerVideoId = transitionPlaybackVideoId

                if (playerSource != null && playerVideoId != null) {
                    val nextEpisode =
                        if (
                            item.type == "series" &&
                            selectedEpisode != null
                        ) {
                            val currentEpisode = selectedEpisode!!

                            item.episodes
                                .sortedWith(
                                    compareBy<EpisodeItem> {
                                        it.season
                                    }.thenBy {
                                        it.episode
                                    }
                                )
                                .firstOrNull { candidate ->
                                    candidate.season >
                                        currentEpisode.season ||
                                        (
                                            candidate.season ==
                                                currentEpisode.season &&
                                                candidate.episode >
                                                    currentEpisode.episode
                                        )
                                }
                        } else {
                            null
                        }

                    PlayerScreen(
                        settingsStore = settingsStore,
                        title = playbackTitle(
                            media = item,
                            episode = selectedEpisode,
                        ),
                        mediaKey =
                            "${item.type}:${item.id}:$playerVideoId",
                        media = item,
                        videoId = playerVideoId,
                        episode = selectedEpisode,
                        episodes = item.episodes,
                        nextEpisode = nextEpisode,
                        source = playerSource,
                        availableSources = transitionSourceStreams,
                        sourceProviderOrder =
                            sourcePickerProviderOrder,
                        subtitles = sourcePickerSubtitles,
                        initialPositionMs =
                            selectedPlaybackStartPositionMs,
                        episodeSwitchingTo =
                            pendingPlaybackEpisode,
                        episodeSwitchFailed =
                            pendingPlaybackFailed ||
                                (
                                    pendingPlaybackEpisode != null &&
                                        !sourcePickerSearching &&
                                        sourcePickerStreams != null &&
                                        sourcePickerStreams
                                            .orEmpty()
                                            .none { it.isDirectPlayable }
                                ),
                        onEpisodeSwitchCompleted = {
                            pendingPlaybackEpisode = null
                            pendingPlaybackFailed = false
                        },
                        onEpisodeSwitchFailed = {
                            pendingPlaybackFailed = true
                        },
                        onLibraryChanged = {
                            refreshDetailPlaybackEntries()
                            onLibraryChanged()
                        },
                        onSwitchSource = {
                            nextSource,
                            positionMs ->
                            selectedPlaybackStartPositionMs =
                                positionMs
                            selectedPlaybackSource = nextSource
                        },
                        onNextEpisode = { next ->
                            pendingPlaybackEpisode = next
                            pendingPlaybackFailed = false
                            startSourceDiscovery(
                                targetEpisode = next,
                                autoPlayFirst = true,
                            )
                        },
                        onEpisodeSelected = { selected ->
                            pendingPlaybackEpisode = selected
                            pendingPlaybackFailed = false
                            startSourceDiscovery(
                                targetEpisode = selected,
                                autoPlayFirst = true,
                            )
                        },
                        onDeferredSubtitleRequested = {
                            requestDeferredSubtitles()
                        },
                        onBack = {
                            sourceDiscoveryJob?.cancel()
                            sourceDiscoveryJob = null
                            deferredSubtitleDiscoveryJob?.cancel()
                            deferredSubtitleDiscoveryJob = null
                            sourcePickerSearching = false
                            sourcePickerStreams = null
                            loadingStreams = false
                            pendingPlaybackEpisode = null
                            pendingPlaybackFailed = false
                            selectedPlaybackSource = null
                            selectedPlaybackVideoId = null
                            selectedPlaybackStartPositionMs = 0L
                        },
                    )
                }
            }

            DetailSurface.SOURCES -> {
                SourcePickerScreen(
                    mediaTitle = playbackTitle(
                        media = item,
                        episode = selectedEpisode,
                    ),
                    streams = transitionSourceStreams,
                    rawCount = sourcePickerRawCount,
                    notice = sourcePickerNotice,
                    searching = sourcePickerSearching,
                    progressText = sourcePickerProgress,
                    firstResultMs = sourcePickerFirstResultMs,
                    providerOrder = sourcePickerProviderOrder,
                    originalLanguage = item.originalLanguage,
                    showTechnicalDetails =
                        showSourceTechnicalDetails,
                    onBack = {
                        sourceDiscoveryJob?.cancel()
                        sourceDiscoveryJob = null
                        sourcePickerSearching = false
                        loadingStreams = false
                        sourcePickerStreams = null
                    },
                    onPlay = { source ->
                        sourcePickerSearching = false

                        val videoId =
                            selectedVideoId(item, selectedEpisode)

                        if (
                            videoId != null &&
                            source.isDirectPlayable
                        ) {
                            selectedPlaybackStartPositionMs =
                                selectedPlaybackStartPositionMs
                                    .coerceAtLeast(0L)
                            selectedPlaybackVideoId = videoId
                            selectedPlaybackSource = source
                        }
                    },
                )
            }

            DetailSurface.DETAILS -> {
    val activePlaybackEntry =
        detailsPlaybackEntry(
            media = item,
            episode = selectedEpisode,
            entries =
                detailPlaybackEntries,
        )

    val canResume =
        activePlaybackEntry
            ?.let { entry ->
                entry.positionMs > 15_000L &&
                    (
                        entry.durationMs <= 0L ||
                            entry.positionMs <
                                (
                                    entry.durationMs *
                                        .95f
                                ).toLong()
                    )
            }
            ?: false

    val primaryActionLabel =
        when {
            loadingStreams ->
                "Finding Sources…"

            item.type == "series" &&
                selectedEpisode != null &&
                canResume ->
                "Resume S${selectedEpisode!!.season} E${selectedEpisode!!.episode}"

            item.type == "series" &&
                selectedEpisode != null ->
                "Play S${selectedEpisode!!.season} E${selectedEpisode!!.episode}"

            item.type == "series" ->
                "Select an Episode"

            canResume ->
                "Resume"

            else ->
                "Watch"
        }

    val detailSeasonCount =
        if (item.type == "series") {
            item.episodes
                .asSequence()
                .map { it.season }
                .filter { it > 0 }
                .distinct()
                .count()
                .takeIf { it > 0 }
        } else {
            null
        }

    val detailFacts =
        listOfNotNull(
            cleanDetailsReleaseInfo(
                item.releaseInfo
            ),
            detailSeasonCount
                ?.let { count ->
                    if (count == 1) {
                        "1 Season"
                    } else {
                        "$count Seasons"
                    }
                },
            item.runtimeMinutes
                ?.takeIf {
                    it > 0
                }
                ?.let(
                    ::formatDetailsRuntime
                ),
            item.certification
                ?.takeIf {
                    it.isNotBlank()
                },
        )

    val dnaMatchPercent =
        if (
            !loadingMeta &&
            showDnaMatch &&
            detailsDnaSnapshot
                ?.hasUsefulData ==
                true
        ) {
            detailsDnaEngine
                .matchPercent(
                    media = item,
                    dna =
                        detailsDnaSnapshot,
                )
        } else {
            null
        }

        LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                bottom = 32.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                20.dp
            ),
    ) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(326.dp),
            ) {
                NetworkImage(
                    url =
                        item.background
                            ?: item.poster,
                    contentDescription =
                        item.name,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentScale =
                        ContentScale.Crop,
                    fallbackText =
                        item.name,
                )

                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Black
                                                    .copy(
                                                        alpha = .18f
                                                    ),
                                                Color.Black
                                                    .copy(
                                                        alpha = .34f
                                                    ),
                                                VueoPalette
                                                    .Background
                                                    .copy(
                                                        alpha = .96f
                                                    ),
                                            )
                                    )
                            ),
                )

                Box(
                    modifier =
                        Modifier
                            .align(
                                Alignment.TopStart
                            )
                            .padding(
                                start = 16.dp,
                                top = 8.dp,
                            ),
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            Color.Black.copy(
                                alpha = .50f
                            ),
                    ) {
                        IconButton(
                            onClick = onBack,
                        ) {
                            Icon(
                                Icons.Default
                                    .ArrowBack,
                                contentDescription =
                                    "Back",
                                tint =
                                    Color.White,
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomStart
                            )
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 12.dp,
                            ),
                    verticalAlignment =
                        Alignment.Bottom,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            14.dp
                        ),
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .width(92.dp)
                                .aspectRatio(
                                    2f / 3f
                                ),
                        shape =
                            RoundedCornerShape(
                                14.dp
                            ),
                        color =
                            VueoPalette.Surface,
                    ) {
                        NetworkImage(
                            url = item.poster,
                            contentDescription =
                                item.name,
                            modifier =
                                Modifier
                                    .fillMaxSize(),
                            contentScale =
                                ContentScale.Crop,
                            fallbackText =
                                item.name,
                        )
                    }

                    Column(
                        modifier =
                            Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            ),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color =
                                VueoPalette.Accent
                                    .copy(
                                        alpha = .15f
                                    ),
                        ) {
                            Text(
                                text =
                                    if (
                                        item.type ==
                                            "series"
                                    ) {
                                        "Series"
                                    } else {
                                        "Movie"
                                    },
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            9.dp,
                                        vertical =
                                            4.dp,
                                    ),
                                color =
                                    VueoPalette.Accent,
                                fontSize = 9.sp,
                                fontWeight =
                                    FontWeight.Black,
                            )
                        }

                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 27.sp,
                            lineHeight = 29.sp,
                            fontWeight =
                                FontWeight.Black,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                        )

                        Text(
                            text =
                                item.genres
                                    .take(3)
                                    .joinToString(
                                        " • "
                                    )
                                    .ifBlank {
                                        item.releaseInfo
                                            .orEmpty()
                                    },
                            color =
                                Color.White.copy(
                                    alpha = .66f
                                ),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 18.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
            ) {
                val videoId =
                    selectedVideoId(
                        item,
                        selectedEpisode,
                    )

                val seriesNeedsEpisode =
                    item.type == "series" &&
                        item.episodes
                            .isNotEmpty()

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Button(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                        enabled =
                            !loadingStreams &&
                                videoId != null &&
                                (
                                    !seriesNeedsEpisode ||
                                        selectedEpisode != null
                                ),
                        onClick = {
                            startSourceDiscovery(
                                targetEpisode =
                                    selectedEpisode,
                                startPositionMs =
                                    if (canResume) {
                                        activePlaybackEntry
                                            ?.positionMs
                                            ?: 0L
                                    } else {
                                        0L
                                    },
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription =
                                null,
                        )
                        Spacer(
                            Modifier.width(7.dp)
                        )
                        Text(
                            text =
                                primaryActionLabel,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }

                    OutlinedButton(
                        modifier =
                            Modifier.height(
                                48.dp
                            ),
                        onClick = {
                            inWatchlist =
                                libraryStore
                                    .toggleWatchlist(
                                        item
                                    )

                            onLibraryChanged()
                        },
                    ) {
                        Icon(
                            if (inWatchlist) {
                                Icons.Default
                                    .VideoLibrary
                            } else {
                                Icons.Default.Add
                            },
                            contentDescription =
                                null,
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Text(
                            if (inWatchlist) {
                                "In My List"
                            } else {
                                "My List"
                            }
                        )
                    }
                }

                if (canResume) {
                    activePlaybackEntry
                        ?.let { entry ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        9.dp
                                    ),
                            ) {
                                LinearProgressIndicator(
                                    progress = {
                                        entry
                                            .progressFraction
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                    },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .clip(
                                                CircleShape
                                            ),
                                    color =
                                        VueoPalette.Accent,
                                    trackColor =
                                        Color.White.copy(
                                            alpha = .14f
                                        ),
                                )

                                homeRemainingTimeLabel(
                                    entry
                                )
                                    ?.let {
                                        remaining ->
                                        Text(
                                            text =
                                                remaining,
                                            color =
                                                VueoPalette.Muted,
                                            fontSize = 10.sp,
                                        )
                                    }
                            }
                        }
                }

                sourceStatus
                    ?.let {
                        Text(
                            text = it,
                            color =
                                VueoPalette.Muted,
                            fontSize = 11.sp,
                        )
                    }
            }
        }

        val imdbRating =
            ratings.firstOrNull {
                it.source == "imdb"
            }
        val secondaryRatings =
            ratings.filterNot {
                it.source == "imdb"
            }
        val showRatingsStrip =
            secondaryRatings.isNotEmpty() ||
                dnaMatchPercent != null

        if (
            detailFacts.isNotEmpty() ||
            (imdbRating != null && !showRatingsStrip)
        ) {
            item {
                DetailsFactsRow(
                    facts = detailFacts,
                    imdbRating =
                        imdbRating
                            ?.takeUnless {
                                showRatingsStrip
                            },
                )
            }
        }

        if (showRatingsStrip) {
            item {
                MediaRatingsStrip(
                    ratings =
                        listOfNotNull(
                            imdbRating
                        ) + secondaryRatings,
                    vueoMatchPercent =
                        dnaMatchPercent,
                )
            }
        }

        if (DetailPeoplePolicy.creditLines(item).isNotEmpty()) {
            item {
                MediaCreditsSummary(
                    media = item
                )
            }
        }

        item.description
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let { description ->
                item {
                    Column(
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    18.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            ),
                    ) {
                        Text(
                            text = "Overview",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight =
                                FontWeight.Black,
                        )

                        Text(
                            text = description,
                            color =
                                Color.White.copy(
                                    alpha = .68f
                                ),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }

        val detailCast = DetailPeoplePolicy.cast(item)
        if (detailCast.isNotEmpty()) {
            item {
                MediaCastSection(
                    cast = detailCast,
                    onPersonClick = { person ->
                        onEntityClick(
                            MediaEntityTarget(
                                kind = MediaEntityKind.ACTOR,
                                name = person.name,
                            )
                        )
                    },
                )
            }
        }

        if (
            item.type == "series" &&
            item.episodes.isNotEmpty()
        ) {
            item {
                SeasonSelector(
                    episodes = item.episodes,
                    selectedSeason =
                        selectedSeason,
                    onSelectSeason = { season ->
                        episodeSelectionTouchedByUser =
                            true
                        selectedSeason = season
                        selectedEpisode =
                            item.episodes
                                .asSequence()
                                .filter {
                                    it.season ==
                                        season
                                }
                                .sortedBy {
                                    it.episode
                                }
                                .firstOrNull()
                        sourceStatus = null
                    },
                )
            }

            item {
                EpisodeSelector(
                    media = item,
                    episodes =
                        item.episodes
                            .filter {
                                it.season ==
                                    selectedSeason
                            },
                    selectedEpisode =
                        selectedEpisode,
                    playbackEntries =
                        detailPlaybackEntries,
                    onEpisodeClick = { episode ->
                        episodeSelectionTouchedByUser =
                            true
                        selectedSeason =
                            episode.season
                        selectedEpisode =
                            episode
                        sourceStatus = null

                        val episodeEntry =
                            detailsPlaybackEntry(
                                media = item,
                                episode = episode,
                                entries =
                                    detailPlaybackEntries,
                            )

                        startSourceDiscovery(
                            targetEpisode =
                                episode,
                            startPositionMs =
                                episodeEntry
                                    ?.takeIf { entry ->
                                        entry.positionMs >
                                            15_000L &&
                                            (
                                                entry.durationMs <=
                                                    0L ||
                                                    entry.positionMs <
                                                        (
                                                            entry.durationMs *
                                                                .95f
                                                        ).toLong()
                                            )
                                    }
                                    ?.positionMs
                                    ?: 0L,
                        )
                    },
                )
            }
        }

        if (
            item.type == "series" &&
            item.episodes.isEmpty() &&
            !loadingMeta
        ) {
            item {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    18.dp
                            ),
                    shape =
                        RoundedCornerShape(
                            16.dp
                        ),
                    color =
                        VueoPalette
                            .SurfaceStrong,
                ) {
                    Text(
                        text =
                            "Episodes are not available for this title yet.",
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),
                        color =
                            VueoPalette.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        val featuredCompanies =
            if (item.type == "series") {
                item.networks
            } else {
                item.productionCompanies
            }

        if (featuredCompanies.isNotEmpty()) {
            item {
                MediaCompanySection(
                    title =
                        if (
                            item.type ==
                                "series"
                        ) {
                            if (featuredCompanies.size == 1) {
                                "Network"
                            } else {
                                "Networks"
                            }
                        } else {
                            "Production"
                        },
                    companies =
                        featuredCompanies,
                    onCompanyClick = { company ->
                        onEntityClick(
                            MediaEntityTarget(
                                kind = if (item.type == "series") {
                                    MediaEntityKind.NETWORK
                                } else {
                                    MediaEntityKind.COMPANY
                                },
                                name = company.name,
                                tmdbId = company.tmdbId,
                            )
                        )
                    },
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    18.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                2.dp
                            ),
                    ) {
                        Text(
                            text =
                                "More Like This",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight =
                                FontWeight.Black,
                        )

                        Text(
                            text =
                                "Recommended for you",
                            color =
                                VueoPalette.Muted,
                            fontSize = 10.sp,
                        )
                    }

                    LazyRow(
                        contentPadding =
                            PaddingValues(
                                horizontal =
                                    18.dp
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),
                    ) {
                        items(
                            relatedItems,
                            key = {
                                "${it.type}:${it.id}"
                            },
                        ) { related ->
                            MediaPoster(
                                item = related,
                                onClick = {
                                    onMediaClick(
                                        related
                                    )
                                },
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        18.dp
                                ),
                        horizontalArrangement =
                            Arrangement.End,
                    ) {
                        Text(
                            text =
                                moreLikeThisAttribution(
                                    usesTmdb =
                                        tmdbMoreLikeThisEnabled,
                                ),
                            modifier =
                                Modifier.offset(
                                    y = (-7).dp
                                ),
                            color =
                                VueoPalette.Muted
                                    .copy(
                                        alpha = .68f
                                    ),
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

    }
            }
        }
    }
}
