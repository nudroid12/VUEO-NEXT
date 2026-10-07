package com.vueo.mobile.ui

import com.vueo.shared.core.storage.ContinueWatchingPolicy

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
import androidx.compose.material.icons.filled.Check
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
import com.vueo.shared.core.source.SourceDiscoveryControl
import com.vueo.shared.core.source.SourceDiscoveryEngine
import com.vueo.shared.core.source.SourceDiscoveryActivity
import com.vueo.shared.core.source.SourceDiscoveryRequest
import com.vueo.shared.core.source.SourceDiscoveryBundle
import com.vueo.shared.core.player.NextEpisodeSourcePolicy
import com.vueo.shared.core.player.NextEpisodePrefetchValidator
import com.vueo.shared.core.player.PrefetchedSourceValidation
import com.vueo.shared.core.player.PlayerSourceDisplay
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.mobile.core.dna.UserDnaEngine
import com.vueo.mobile.core.dna.UserDnaSnapshot
import com.vueo.mobile.core.dna.UserDnaPreferences
import com.vueo.mobile.core.model.CatalogRow
import com.vueo.mobile.BuildConfig
import com.vueo.mobile.R
import com.vueo.mobile.core.storage.PlaybackStore
import com.vueo.mobile.core.storage.LibraryStore
import com.vueo.mobile.core.storage.ProfileStore
import com.vueo.mobile.core.storage.VueoProfile
import com.vueo.mobile.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryDetailSnapshot
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun MediaDetailsScreen(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    active: Boolean,
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

    // Keep the first Details composition storage-free. The DNA engine reads
    // history + My List JSON, so hydrate it with the rest of the local library
    // state after the shell has already been published.
    var detailsDnaSnapshot by remember(
        detailsProfileId,
        showDnaMatch,
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<UserDnaSnapshot?>(null)
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

    // Do not parse LibraryStore JSON during the first composition. Use the
    // navigation payload immediately, then hydrate the complete local snapshot
    // from Dispatchers.IO. This mirrors the responsiveness pattern already used
    // by TV Details.
    var inWatchlist by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf(false)
    }
    var watchlistUserOverride by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<Boolean?>(null)
    }

    var detailPlaybackEntries by remember(
        initialItem.id,
        initialItem.type,
        initialLibraryEntry,
    ) {
        mutableStateOf(
            listOfNotNull(
                initialLibraryEntry
            )
        )
    }

    var detailEpisodePlaybackEntries by remember(
        initialItem.id,
        initialItem.type,
        initialLibraryEntry,
    ) {
        mutableStateOf(
            listOfNotNull(
                initialLibraryEntry
            )
        )
    }

    var detailLibraryHydrationGeneration by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableIntStateOf(0)
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
    val sourcePickerSubtitlesState = remember {
        mutableStateOf<List<SubtitleTrack>>(emptyList())
    }
    var sourcePickerSubtitles by sourcePickerSubtitlesState
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
    var sourcePickerActivityLog by remember {
        mutableStateOf<List<SourceDiscoveryActivity>>(emptyList())
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
    var sourceDiscoveryControl by remember {
        mutableStateOf<SourceDiscoveryControl?>(null)
    }
    var sourcePickerPluginsStopped by remember {
        mutableStateOf(false)
    }
    var sourcePickerSourcesStopped by remember {
        mutableStateOf(false)
    }
    var sourceDiscoveryGeneration by remember {
        mutableIntStateOf(0)
    }
    var episodePrefetch by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<MobileEpisodePrefetch?>(null)
    }
    var playingEpisodePrefetch by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<MobileEpisodePrefetch?>(null)
    }
    var failedSourceVideoIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var selectedPlaybackSource by remember {
        mutableStateOf<StreamSource?>(null)
    }
    var returnToSourcesOnPlayerExit by remember {
        mutableStateOf(false)
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
        entries: List<LibraryPlaybackEntry>,
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
                entries = entries,
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

    fun applyDetailLibrarySnapshot(
        media: MediaItem,
        snapshot: LibraryDetailSnapshot,
        dnaSnapshot: UserDnaSnapshot?,
        preserveCurrentEpisode: Boolean,
    ) {
        val storedInWatchlist =
            snapshot.watchlist.any { stored ->
                stored.id == media.id &&
                    stored.type == media.type
            }
        inWatchlist = watchlistUserOverride ?: storedInWatchlist

        detailPlaybackEntries =
            (
                snapshot.continueWatching +
                    snapshot.history +
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
                    ).joinToString(":")
                }

        detailEpisodePlaybackEntries =
            (
                snapshot.playbackEntries +
                    listOfNotNull(
                        initialLibraryEntry
                    )
            ).distinctBy {
                it.mediaKey
            }

        detailsDnaSnapshot =
            dnaSnapshot

        syncEpisodeSelection(
            media = media,
            entries = detailEpisodePlaybackEntries,
            preserveCurrent = preserveCurrentEpisode,
        )
    }

    suspend fun loadDetailLibraryState(): Pair<LibraryDetailSnapshot, UserDnaSnapshot?> {
        val snapshot =
            withContext(Dispatchers.IO) {
                libraryStore.detailSnapshot()
            }

        val dnaSnapshot =
            if (showDnaMatch) {
                withContext(Dispatchers.Default) {
                    detailsDnaEngine.analyze(
                        history = snapshot.history,
                        myList = snapshot.watchlist,
                    )
                }
            } else {
                null
            }

        return snapshot to dnaSnapshot
    }

    fun refreshDetailLibraryState() {
        val mediaAtRequest = item
        val generation =
            ++detailLibraryHydrationGeneration

        scope.launch {
            val (snapshot, dnaSnapshot) =
                loadDetailLibraryState()

            if (
                generation != detailLibraryHydrationGeneration ||
                item.id != mediaAtRequest.id ||
                item.type != mediaAtRequest.type
            ) {
                return@launch
            }

            applyDetailLibrarySnapshot(
                media = item,
                snapshot = snapshot,
                dnaSnapshot = dnaSnapshot,
                preserveCurrentEpisode = true,
            )
        }
    }

    LaunchedEffect(active) {
        if (!active) {
            // AnimatedContent keeps the outgoing Details tree alive briefly.
            // Invalidate any local-library refresh launched from rememberCoroutineScope
            // immediately instead of waiting for the exit animation to dispose it.
            detailLibraryHydrationGeneration++
            sourceDiscoveryGeneration++
            sourceDiscoveryJob?.cancel()
            sourceDiscoveryJob = null
            sourceDiscoveryControl = null
        }
    }

    LaunchedEffect(
        active,
        initialItem.id,
        initialItem.type,
        initialItem.sourceExtensionId,
    ) {
        if (!active) return@LaunchedEffect

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
            entries = detailEpisodePlaybackEntries,
            preserveCurrent = false,
        )

        // Hydrate all local library/DNA state off the UI thread. LibraryStore's
        // detailSnapshot() parses watchlist/history/cursors once and shares the
        // result across watchlist, resume, episode targeting and DNA.
        val libraryHydrationGeneration =
            ++detailLibraryHydrationGeneration
        launch {
            val (snapshot, dnaSnapshot) =
                loadDetailLibraryState()

            if (
                libraryHydrationGeneration == detailLibraryHydrationGeneration
            ) {
                applyDetailLibrarySnapshot(
                    media = item,
                    snapshot = snapshot,
                    dnaSnapshot = dnaSnapshot,
                    preserveCurrentEpisode = true,
                )
            }
        }

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
            entries = detailEpisodePlaybackEntries,
            preserveCurrent = true,
        )

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
                    entries = detailEpisodePlaybackEntries,
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


    fun subtitleLanguageCodesForDiscovery(): Set<String>? =
        if (
            settingsStore.subtitleVisibility() ==
            com.vueo.shared.core.storage.SubtitleVisibility.PREFERRED_ONLY
        ) {
            setOfNotNull(
                settingsStore.preferredSubtitleLanguage().languageCode,
                settingsStore.secondarySubtitleLanguage().languageCode,
            ).takeIf { it.isNotEmpty() }
        } else {
            null
        }

    suspend fun refreshPlayerSubtitles(
        expectedVideoId: String,
    ): Int {
        val beforeUrls = sourcePickerSubtitles.mapTo(linkedSetOf()) { it.url }
        RuntimeDiagnostics.recordPlayerEvent(
            "Mobile",
            "SUBTITLE_REFRESH_START",
            "video=$expectedVideoId existing=${beforeUrls.size}",
        )

        val refreshed = sourceDiscoveryEngine.discoverSubtitlesOnly(
            item = item,
            videoId = expectedVideoId,
            subtitleLanguageCodes = subtitleLanguageCodesForDiscovery(),
            onProgress = { discovered ->
                if (selectedPlaybackVideoId == expectedVideoId) {
                    sourcePickerSubtitles =
                        (sourcePickerSubtitles + discovered).distinctBy { it.url }
                }
            },
        )

        if (selectedPlaybackVideoId != expectedVideoId) return 0
        sourcePickerSubtitles =
            (sourcePickerSubtitles + refreshed).distinctBy { it.url }
        val added = sourcePickerSubtitles.count { it.url !in beforeUrls }
        RuntimeDiagnostics.recordPlayerEvent(
            "Mobile",
            "SUBTITLE_REFRESH_DONE",
            "video=$expectedVideoId added=$added total=${sourcePickerSubtitles.size}",
        )
        return added
    }

    fun cancelEpisodePrefetch() {
        playingEpisodePrefetch?.cancel()
        playingEpisodePrefetch = null
        episodePrefetch?.cancel()
        episodePrefetch = null
    }

    DisposableEffect(
        initialItem.id,
        initialItem.type,
    ) {
        onDispose {
            episodePrefetch?.cancel()
            playingEpisodePrefetch?.cancel()
        }
    }

    fun startEpisodePrefetch(
        target: EpisodeItem,
        current: StreamSource,
        seedSubtitles: List<SubtitleTrack> = emptyList(),
    ) {
        val originVideoId = selectedPlaybackVideoId ?: return
        if (pendingPlaybackEpisode != null) return

        val mediaKey = "${item.type}:${item.id}"
        val existing = episodePrefetch
        if (
            existing != null &&
            !existing.claimed &&
            !existing.failed &&
            existing.mediaKey == mediaKey &&
            existing.originVideoId == originVideoId &&
            existing.target.id == target.id &&
            NextEpisodeSourcePolicy.sameServer(existing.preferredSource, current)
        ) {
            return
        }

        if (episodePrefetch?.claimed == true) {
            playingEpisodePrefetch?.cancel()
            playingEpisodePrefetch = episodePrefetch
            episodePrefetch = null
        } else {
            episodePrefetch?.cancel()
        }

        val targetVideoId = selectedVideoId(
            media = item,
            episode = target,
        ) ?: return
        val pending = MobileEpisodePrefetch(
            mediaKey = mediaKey,
            originVideoId = originVideoId,
            target = target,
            preferredSource = current,
            seedSubtitles = seedSubtitles,
        )
        episodePrefetch = pending
        RuntimeDiagnostics.recordPlayerEvent(
            "Mobile",
            "NEXT_PREFETCH_START",
            "episode=S${target.season}E${target.episode} provider=${current.providerName}",
        )

        pending.job = scope.launch {
            try {
                sourceDiscoveryEngine.discover(
                    request = SourceDiscoveryRequest(
                        item = item,
                        episode = target,
                        videoId = targetVideoId,
                        preferredQuality = preferredSourceQuality,
                        forceRefresh = true,
                        subtitleLanguageCodes = subtitleLanguageCodesForDiscovery(),
                        sourceProviderName = current.providerName,
                        discoverSubtitles = seedSubtitles.isEmpty(),
                    ),
                    control = pending.control,
                    onUpdate = prefetchUpdate@ { snapshot ->
                        if (
                            episodePrefetch !== pending &&
                            playingEpisodePrefetch !== pending
                        ) {
                            return@prefetchUpdate
                        }

                        pending.subtitlesResolved = snapshot.subtitlesResolved
                        val previous = pending.bundle
                        pending.bundle = snapshot.bundle.copy(
                            subtitles =
                                (
                                    pending.seedSubtitles +
                                        previous?.subtitles.orEmpty() +
                                        snapshot.bundle.subtitles
                                ).distinctBy { it.url },
                        )

                        if (pending.matchedSource == null) {
                            pending.matchedSource =
                                NextEpisodeSourcePolicy.matchingServer(
                                    sources = snapshot.bundle.sources,
                                    current = current,
                                    preferredQuality = preferredSourceQuality,
                                    originalLanguage = item.originalLanguage,
                                )
                            if (pending.matchedSource != null) {
                                pending.matchedAtMs = SystemClock.elapsedRealtime()
                                pending.control.stopSources()
                                pending.sourcesReady.complete(Unit)
                                RuntimeDiagnostics.recordPlayerEvent(
                                    "Mobile",
                                    "NEXT_PREFETCH_READY",
                                    "episode=S${target.season}E${target.episode} " +
                                        "server=${PlayerSourceDisplay.title(requireNotNull(pending.matchedSource))}",
                                )
                            }
                        }

                        pending.bundle = pending.bundle?.copy(
                            sources = listOfNotNull(pending.matchedSource),
                        )
                        if (!snapshot.searching) {
                            pending.sourcesReady.complete(Unit)
                        }
                        pending.onSubtitles?.invoke()
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
        // A seek can cross more than one checkpoint at once. Re-read the active
        // prefetch after waiting on its mutex so a newer refresh is never probed
        // through a stale object.
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
                    !NextEpisodeSourcePolicy.sameServer(pending.preferredSource, current)
                ) {
                    return@withLock
                }

                handled = true
                val matched = pending.matchedSource
                if (matched == null) {
                    // The T-5 scan may still be running. Do not duplicate it. If
                    // it already finished without the requested server, rerun the
                    // exact same filtered prefetch pipeline.
                    if (pending.job?.isActive != true) {
                        restart = true
                        restartSubtitles = pending.bundle?.subtitles.orEmpty()
                    }
                    RuntimeDiagnostics.recordPlayerEvent(
                        "Mobile",
                        "NEXT_PREFETCH_VALIDATE",
                        "checkpoint=${checkpointSeconds}s state=${if (restart) "missing" else "pending"}",
                    )
                    return@withLock
                }

                val result = NextEpisodePrefetchValidator.validate(matched)
                RuntimeDiagnostics.recordPlayerEvent(
                    "Mobile",
                    "NEXT_PREFETCH_VALIDATE",
                    "checkpoint=${checkpointSeconds}s result=${result.name.lowercase()} " +
                        "server=${PlayerSourceDisplay.title(matched)}",
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
                    "Mobile",
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

    fun startSourceDiscovery(
        targetEpisode: EpisodeItem?,
        startPositionMs: Long = 0L,
        autoPlayFirst: Boolean = false,
        forceRefresh: Boolean = false,
        preferredSource: StreamSource? = null,
        prefetched: MobileEpisodePrefetch? = null,
    ) {
        selectedPlaybackStartPositionMs = startPositionMs.coerceAtLeast(0L)
        if (autoPlayFirst) returnToSourcesOnPlayerExit = false

        val targetVideoId = selectedVideoId(
            media = item,
            episode = targetEpisode,
        ) ?: return
        val effectiveForceRefresh =
            forceRefresh || targetVideoId in failedSourceVideoIds
        val retainPlaybackSources =
            selectedPlaybackSource != null &&
                selectedPlaybackVideoId == targetVideoId
        val retainedStreams =
            sourcePickerStreams.orEmpty().takeIf { retainPlaybackSources }.orEmpty()
        val retainedSubtitles =
            sourcePickerSubtitles.takeIf { retainPlaybackSources }.orEmpty()
        val retainedProviderOrder =
            sourcePickerProviderOrder.takeIf { retainPlaybackSources }.orEmpty()

        sourceDiscoveryGeneration += 1
        val discoveryGeneration = sourceDiscoveryGeneration
        sourceDiscoveryJob?.cancel()
        val discoveryControl = SourceDiscoveryControl()
        sourceDiscoveryControl = discoveryControl

        var autoPlayCommitted = false
        var sourceDiscoveryCompleted = false
        var latestAutoPlayCandidates = emptyList<StreamSource>()

        fun withPrefetchedSubtitles(
            bundle: SourceDiscoveryBundle,
        ): SourceDiscoveryBundle =
            bundle.copy(
                subtitles =
                    (
                        prefetched?.bundle?.subtitles.orEmpty() +
                            bundle.subtitles
                    ).distinctBy { it.url },
            )

        fun commitAutoPlayIfReady(
            candidates: List<StreamSource>,
            completed: Boolean = false,
            preferredProviderDone: Boolean = true,
        ) {
            latestAutoPlayCandidates = candidates
            if (!autoPlayFirst || autoPlayCommitted) return

            val candidate =
                if (preferredSource != null) {
                    NextEpisodeSourcePolicy.select(
                        sources = candidates,
                        current = preferredSource,
                        preferredProviderDone = preferredProviderDone,
                        completed = completed,
                        preferredQuality = preferredSourceQuality,
                        originalLanguage = item.originalLanguage,
                    )
                } else {
                    val directCandidates =
                        candidates
                            .filter { it.isDirectPlayable }
                            .sortedWith(
                                PlayerSourcePolicy.comparator(
                                    preferredQuality = preferredSourceQuality,
                                    originalLanguage = item.originalLanguage,
                                )
                            )
                    directCandidates.firstOrNull { source ->
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
                        ?.takeIf { completed }
                } ?: return

            autoPlayCommitted = true
            discoveryControl.stopPlugins()
            sourcePickerPluginsStopped = discoveryControl.pluginsStopped
            selectedSeason = targetEpisode?.season ?: selectedSeason
            selectedEpisode = targetEpisode
            selectedPlaybackVideoId = targetVideoId
            returnToSourcesOnPlayerExit = false
            selectedPlaybackSource = candidate
        }

        sourcePickerStreams = if (retainPlaybackSources) retainedStreams else emptyList()
        sourcePickerProviderOrder = if (retainPlaybackSources) retainedProviderOrder else emptyList()
        sourcePickerSubtitles =
            (retainedSubtitles + prefetched?.bundle?.subtitles.orEmpty()).distinctBy { it.url }
        if (!retainPlaybackSources) sourcePickerRawCount = 0
        sourcePickerNotice = if (retainPlaybackSources) sourcePickerNotice else null
        sourcePickerSearching = true
        sourcePickerPluginsStopped = false
        sourcePickerSourcesStopped = false
        sourcePickerFirstResultMs = if (retainPlaybackSources) sourcePickerFirstResultMs else null
        sourcePickerProgress =
            when {
                retainPlaybackSources -> "Refreshing sources…"
                prefetched != null -> "Preparing prefetched next episode…"
                else -> "Starting source discovery…"
            }
        sourcePickerActivityLog = emptyList()
        loadingStreams = true
        sourceStatus = null

        prefetched?.onSubtitles = {
            if (sourceDiscoveryGeneration == discoveryGeneration) {
                sourcePickerSubtitles =
                    (
                        prefetched.bundle?.subtitles.orEmpty() +
                            sourcePickerSubtitles
                    ).distinctBy { it.url }
            }
        }

        sourceDiscoveryJob = scope.launch {
            try {
                if (prefetched != null) {
                    prefetched.sourcesReady.await()
                    if (sourceDiscoveryGeneration != discoveryGeneration) {
                        return@launch
                    }

                    val standby = prefetched.bundle
                    val matched = prefetched.matchedSource
                    if (
                        !prefetched.failed &&
                        standby != null &&
                        matched != null
                    ) {
                        val readyBundle = standby.copy(
                            sources = listOf(matched),
                        )
                        sourcePickerStreams = readyBundle.sources
                        sourcePickerProviderOrder = listOf(matched.providerName)
                        sourcePickerSubtitles = readyBundle.subtitles
                        sourcePickerRawCount = readyBundle.sources.size
                        sourcePickerNotice = null
                        sourcePickerSearching = false
                        sourcePickerPluginsStopped = true
                        sourcePickerSourcesStopped = true
                        sourcePickerFirstResultMs = 0L
                        sourcePickerProgress = "Next episode ready"
                        sourcePickerActivityLog = emptyList()
                        loadingStreams = false
                        sourceDiscoveryCompleted = true
                        failedSourceVideoIds = failedSourceVideoIds - targetVideoId
                        commitAutoPlayIfReady(
                            candidates = readyBundle.sources,
                            completed = true,
                            preferredProviderDone = true,
                        )
                        RuntimeDiagnostics.recordPlayerEvent(
                            "Mobile",
                            "NEXT_PREFETCH_USED",
                            "episode=S${targetEpisode?.season ?: 0}E${targetEpisode?.episode ?: 0} " +
                                "server=${PlayerSourceDisplay.title(matched)}",
                        )
                        // Subtitle discovery is independent of the stopped source
                        // branch. Keep accepting late tracks for the new episode.
                        prefetched.job?.join()
                        return@launch
                    }

                    RuntimeDiagnostics.recordPlayerEvent(
                        "Mobile",
                        "NEXT_PREFETCH_FALLBACK",
                        "episode=S${targetEpisode?.season ?: 0}E${targetEpisode?.episode ?: 0} matchingServer=false",
                    )
                }

                val result = sourceDiscoveryEngine.discover(
                    request = SourceDiscoveryRequest(
                        item = item,
                        episode = targetEpisode,
                        videoId = targetVideoId,
                        preferredQuality = preferredSourceQuality,
                        forceRefresh = effectiveForceRefresh,
                        subtitleLanguageCodes = subtitleLanguageCodesForDiscovery(),
                        discoverSubtitles = prefetched == null || prefetched.failed,
                    ),
                    control = discoveryControl,
                    onUpdate = sourceUpdate@ { snapshot ->
                        if (sourceDiscoveryGeneration != discoveryGeneration) {
                            return@sourceUpdate
                        }

                        val mergedBundle = withPrefetchedSubtitles(snapshot.bundle)
                        val visibleStreams =
                            (retainedStreams + mergedBundle.sources).distinctBy { it.url }
                        val visibleSubtitles =
                            (retainedSubtitles + mergedBundle.subtitles).distinctBy { it.url }
                        sourcePickerStreams = visibleStreams
                        sourcePickerProviderOrder =
                            (retainedProviderOrder + snapshot.providerOrder +
                                visibleStreams.map { it.providerName }).distinct()
                        sourcePickerSubtitles = visibleSubtitles
                        sourcePickerRawCount = maxOf(sourcePickerRawCount, snapshot.rawCount)
                        sourcePickerNotice = snapshot.notice ?: sourcePickerNotice
                        sourcePickerSearching = snapshot.searching
                        sourcePickerPluginsStopped = snapshot.pluginsStopped
                        sourcePickerSourcesStopped = snapshot.sourcesStopped
                        sourcePickerFirstResultMs = snapshot.firstResultMs ?: sourcePickerFirstResultMs
                        sourcePickerProgress = snapshot.progress
                        sourcePickerActivityLog = snapshot.activityLog
                        loadingStreams = snapshot.searching

                        sourceDiscoveryCompleted = !snapshot.searching
                        if (sourceDiscoveryCompleted) {
                            failedSourceVideoIds =
                                if (visibleStreams.any { it.isDirectPlayable }) {
                                    failedSourceVideoIds - targetVideoId
                                } else {
                                    failedSourceVideoIds + targetVideoId
                                }
                        }

                        val preferredProviderFinished =
                            preferredSource == null ||
                                snapshot.completedSourceProviders.any {
                                    it.trim().equals(
                                        preferredSource.providerName.trim(),
                                        ignoreCase = true,
                                    )
                                } ||
                                snapshot.plannedSourceProviders.none {
                                    it.trim().equals(
                                        preferredSource.providerName.trim(),
                                        ignoreCase = true,
                                    )
                                }

                        commitAutoPlayIfReady(
                            candidates = mergedBundle.sources,
                            completed = sourceDiscoveryCompleted,
                            preferredProviderDone = preferredProviderFinished,
                        )
                    },
                )

                if (sourceDiscoveryGeneration != discoveryGeneration) {
                    return@launch
                }
                val mergedResult = withPrefetchedSubtitles(result)
                sourcePickerStreams =
                    (retainedStreams + mergedResult.sources).distinctBy { it.url }
                sourcePickerProviderOrder =
                    (retainedProviderOrder + sourcePickerProviderOrder +
                        sourcePickerStreams.orEmpty().map { it.providerName }).distinct()
                sourcePickerSubtitles =
                    (retainedSubtitles + mergedResult.subtitles).distinctBy { it.url }
                sourcePickerSearching = false
                sourcePickerPluginsStopped = discoveryControl.pluginsStopped
                sourcePickerSourcesStopped = discoveryControl.sourcesStopped
                loadingStreams = false
                sourceDiscoveryCompleted = true
                failedSourceVideoIds =
                    if (sourcePickerStreams.orEmpty().any { it.isDirectPlayable }) {
                        failedSourceVideoIds - targetVideoId
                    } else {
                        failedSourceVideoIds + targetVideoId
                    }
                commitAutoPlayIfReady(
                    candidates = mergedResult.sources,
                    completed = true,
                    preferredProviderDone = true,
                )
                prefetched?.job?.join()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (sourceDiscoveryGeneration != discoveryGeneration) return@launch
                sourceDiscoveryCompleted = true
                failedSourceVideoIds = failedSourceVideoIds + targetVideoId
                sourcePickerSearching = false
                sourcePickerPluginsStopped = discoveryControl.pluginsStopped
                sourcePickerSourcesStopped = discoveryControl.sourcesStopped
                loadingStreams = false
                sourcePickerProgress =
                    if (latestAutoPlayCandidates.isEmpty()) {
                        "Search complete • no sources found"
                    } else {
                        "Search complete • ${latestAutoPlayCandidates.size} unique sources"
                    }
                commitAutoPlayIfReady(
                    candidates = latestAutoPlayCandidates,
                    completed = true,
                    preferredProviderDone = true,
                )
            } finally {
                if (sourceDiscoveryGeneration == discoveryGeneration) {
                    sourceDiscoveryJob = null
                }
            }
        }
    }

    fun startEpisodeSwitch(
        target: EpisodeItem,
        forceRefresh: Boolean = false,
    ) {
        val preferredSource = selectedPlaybackSource
        val originVideoId = selectedPlaybackVideoId
        val prefetched = episodePrefetch?.takeIf {
            !forceRefresh &&
                originVideoId != null &&
                it.reusable(
                    media = "${item.type}:${item.id}",
                    origin = originVideoId,
                    episode = target,
                    source = preferredSource,
                    nowMs = SystemClock.elapsedRealtime(),
                )
        }

        playingEpisodePrefetch?.cancel()
        playingEpisodePrefetch = null
        if (prefetched == null) {
            episodePrefetch?.cancel()
            episodePrefetch = null
        } else {
            prefetched.claimed = true
        }

        pendingPlaybackEpisode = target
        pendingPlaybackFailed = false
        RuntimeDiagnostics.recordPlayerEvent(
            "Mobile",
            "NEXT_START",
            "episode=S${target.season}E${target.episode} " +
                "provider=${preferredSource?.providerName.orEmpty()} " +
                "server=${preferredSource?.let(PlayerSourceDisplay::title).orEmpty()} " +
                "prefetched=${prefetched != null}",
        )
        startSourceDiscovery(
            targetEpisode = target,
            autoPlayFirst = true,
            forceRefresh = forceRefresh,
            preferredSource = preferredSource,
            prefetched = prefetched,
        )
    }

    val playbackSource = selectedPlaybackSource
    val playbackVideoId = selectedPlaybackVideoId

    BackHandler(
        enabled =
            playbackSource == null &&
                sourcePickerStreams == null,
    ) {
        sourceDiscoveryGeneration += 1
        sourceDiscoveryJob?.cancel()
        sourceDiscoveryJob = null
        cancelEpisodePrefetch()
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
            when {
                initialState == DetailSurface.PLAYER ||
                    targetState == DetailSurface.PLAYER -> vueoPlayerRouteTransition()
                initialState == DetailSurface.SOURCES &&
                    targetState == DetailSurface.DETAILS -> vueoScreenBackTransition()
                else -> vueoScreenForwardTransition()
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
                        sourcesSearching = sourcePickerSearching,
                        pluginsStopped = sourcePickerPluginsStopped,
                        sourcesStopped = sourcePickerSourcesStopped,
                        onRefreshSources = {
                            cancelEpisodePrefetch()
                            startSourceDiscovery(
                                targetEpisode = selectedEpisode,
                                startPositionMs = selectedPlaybackStartPositionMs,
                                forceRefresh = true,
                            )
                        },
                        onStopSources = {
                            sourceDiscoveryControl?.stopSources()
                            sourcePickerPluginsStopped = true
                            sourcePickerSourcesStopped = true
                        },
                        onRefreshSubtitles = {
                            refreshPlayerSubtitles(playerVideoId)
                        },
                        subtitlesState = sourcePickerSubtitlesState,
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
                            refreshDetailLibraryState()
                            onLibraryChanged()
                        },
                        onSwitchSource = {
                            nextSource,
                            positionMs ->
                            selectedPlaybackStartPositionMs =
                                positionMs
                            selectedPlaybackSource = nextSource
                        },
                        onPrefetchNextEpisode = { target, current ->
                            startEpisodePrefetch(target, current)
                        },
                        onValidateNextEpisodePrefetch = { target, current, checkpointSeconds ->
                            validateEpisodePrefetch(target, current, checkpointSeconds)
                        },
                        onActiveSourceChanged = { current ->
                            episodePrefetch
                                ?.takeIf {
                                    !it.claimed &&
                                        !NextEpisodeSourcePolicy.sameServer(
                                            it.preferredSource,
                                            current,
                                        )
                                }
                                ?.let { stale ->
                                    stale.cancel()
                                    if (episodePrefetch === stale) {
                                        episodePrefetch = null
                                    }
                                }
                        },
                        onNextEpisode = { next ->
                            startEpisodeSwitch(next)
                        },
                        onEpisodeSelected = { selected ->
                            val retryingFailedEpisode =
                                pendingPlaybackEpisode?.id == selected.id &&
                                    (
                                        pendingPlaybackFailed ||
                                            (
                                                !sourcePickerSearching &&
                                                    sourcePickerStreams != null &&
                                                    sourcePickerStreams
                                                        .orEmpty()
                                                        .none { it.isDirectPlayable }
                                            )
                                    )
                            startEpisodeSwitch(
                                target = selected,
                                forceRefresh = retryingFailedEpisode,
                            )
                        },
                        onBack = {
                            sourceDiscoveryGeneration += 1
                            sourceDiscoveryJob?.cancel()
                            sourceDiscoveryJob = null
                            sourceDiscoveryControl = null
                            cancelEpisodePrefetch()
                            sourcePickerSearching = false
                            if (!returnToSourcesOnPlayerExit) {
                                sourcePickerStreams = null
                            }
                            loadingStreams = false
                            pendingPlaybackEpisode = null
                            pendingPlaybackFailed = false
                            selectedPlaybackSource = null
                            selectedPlaybackVideoId = null
                            selectedPlaybackStartPositionMs = 0L
                            returnToSourcesOnPlayerExit = false
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
                    activityLog = sourcePickerActivityLog,
                    firstResultMs = sourcePickerFirstResultMs,
                    providerOrder = sourcePickerProviderOrder,
                    originalLanguage = item.originalLanguage,
                    showTechnicalDetails =
                        showSourceTechnicalDetails,
                    onRefresh = {
                        startSourceDiscovery(
                            targetEpisode = selectedEpisode,
                            startPositionMs = selectedPlaybackStartPositionMs,
                            forceRefresh = true,
                        )
                    },
                    onBack = {
                        sourceDiscoveryGeneration += 1
                        sourceDiscoveryJob?.cancel()
                        sourceDiscoveryJob = null
                        sourceDiscoveryControl = null
                        sourcePickerSearching = false
                        sourcePickerPluginsStopped = false
                        sourcePickerSourcesStopped = false
                        loadingStreams = false
                        sourcePickerStreams = null
                    },
                    onPlay = { source, returnToSources ->
                        if (sourcePickerSearching) {
                            sourceDiscoveryControl?.stopPlugins()
                            sourcePickerPluginsStopped =
                                sourceDiscoveryControl?.pluginsStopped == true
                        }

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
                            returnToSourcesOnPlayerExit = returnToSources
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

    val canResume = activePlaybackEntry?.let(ContinueWatchingPolicy::canResume) == true

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

    // Snapshot the delegated Compose state into a stable local before checking it.
    // Kotlin cannot smart-cast delegated properties across the hasUsefulData check.
    val currentDnaSnapshot = detailsDnaSnapshot
    val dnaMatchPercent =
        if (
            !loadingMeta &&
            showDnaMatch &&
            currentDnaSnapshot
                ?.hasUsefulData ==
                true
        ) {
            detailsDnaEngine
                .matchPercent(
                    media = item,
                    dna =
                        currentDnaSnapshot,
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
                            // Keep an explicit user override so an in-flight startup
                            // snapshot cannot repaint the button with its pre-click state.
                            val updated =
                                libraryStore
                                    .toggleWatchlist(
                                        item
                                    )
                            watchlistUserOverride = updated
                            inWatchlist = updated

                            onLibraryChanged()
                        },
                    ) {
                        Icon(
                            if (inWatchlist) {
                                Icons.Default.Check
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
                                    ?.takeIf(ContinueWatchingPolicy::canResume)
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
