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

private enum class AppTab {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
}

private enum class AppSurface {
    STARTUP,
    ROOT,
    CATALOG,
    DETAILS,
    ENTITY_RESULTS,
    PROFILES,
}

internal enum class DetailSurface {
    DETAILS,
    SOURCES,
    PLAYER,
}

private enum class SettingsPage {
    ROOT,
    CONTENT_MANAGER,
    ADDONS,
    PLUGINS,
    CATALOG_ORDER,
    ENHANCEMENTS,
    TMDB,
    MDBLIST,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    ABOUT,
}

private fun parentSettingsPage(
    page: SettingsPage,
): SettingsPage =
    when (page) {
        SettingsPage.ADDONS,
        SettingsPage.PLUGINS,
        SettingsPage.CATALOG_ORDER ->
            SettingsPage.CONTENT_MANAGER

        SettingsPage.TMDB,
        SettingsPage.MDBLIST ->
            SettingsPage.ENHANCEMENTS

        SettingsPage.ROOT ->
            SettingsPage.ROOT

        else ->
            SettingsPage.ROOT
    }

internal enum class SearchTypeFilter(
    val label: String,
) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    ANIME("Anime"),
}

internal enum class SearchSortMode(
    val label: String,
) {
    POPULAR("Popular"),
    TRENDING("Trending"),
    NEWEST("Newest"),
}

internal enum class SearchMode(
    val label: String,
) {
    TITLE("Title"),
    ACTOR("Actor"),
}

@Composable
fun VueoApp() {
    val context = LocalContext.current
    val engine = remember { UnifiedMediaEngine() }
    val store = remember {
        AddonStore(context.applicationContext)
    }
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }
    val libraryStore = remember {
        LibraryStore(
            context.applicationContext
        )
    }
    val profileStore = remember {
        ProfileStore(
            context.applicationContext
        )
    }
    val settingsStore = remember {
        SettingsStore(
            context.applicationContext
        )
    }

    LaunchedEffect(settingsStore) {
        VueoPalette.applyTheme(
            settingsStore.appTheme()
        )
        VueoPalette.applyAccent(
            settingsStore.appAccent()
        )
    }
    val providerCodeSync = remember {
        ProviderCodeSyncManager(
            context.applicationContext
        )
    }

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var settingsPage by remember { mutableStateOf(SettingsPage.ROOT) }
    var searchQuery by remember {
        mutableStateOf("")
    }
    var searchTypeFilter by remember {
        mutableStateOf(
            SearchTypeFilter.ALL
        )
    }
    var searchSortMode by remember {
        mutableStateOf(
            SearchSortMode.POPULAR
        )
    }
    var searchGenre by remember {
        mutableStateOf<String?>(
            null
        )
    }
    val searchListState =
        rememberLazyListState()
    var contentVersion by remember { mutableIntStateOf(0) }
    var booting by remember {
        mutableStateOf(true)
    }
    var startupDestinationResolved by remember {
        mutableStateOf(false)
    }
    var selectedMedia by remember {
        mutableStateOf<MediaItem?>(null)
    }
    var selectedEntityTarget by remember {
        mutableStateOf<MediaEntityTarget?>(null)
    }
    var selectedCatalogRow by remember {
        mutableStateOf<CatalogRow?>(null)
    }
    var mediaBackStack by remember {
        mutableStateOf<List<MediaItem>>(
            emptyList()
        )
    }
    var libraryVersion by remember {
        mutableIntStateOf(0)
    }
    var profileVersion by remember {
        mutableIntStateOf(0)
    }
    var showProfilePicker by remember {
        mutableStateOf(false)
    }
    var profilePickerOpenedFromApp by remember {
        mutableStateOf(false)
    }
    var selectedLibraryEntry by remember {
        mutableStateOf<
            LibraryPlaybackEntry?
        >(null)
    }

    LaunchedEffect(Unit) {
        VueoDataMigration.migrateIfNeeded(
            context.applicationContext
        )

        profileStore.ensureDefaultProfile()
        showProfilePicker =
            profileStore
                .shouldShowPickerOnStartup()
        profilePickerOpenedFromApp = false
        profileVersion++
        startupDestinationResolved = true

        CatalogDiscoveryCache
            .restoreHome(
                context.applicationContext
            )

        SourceDiscoveryCache
            .clearExpired()

        if (
            settingsStore
                .automaticUpdateChecksEnabled()
        ) {
            launch {
                VueoUpdateManager.check(
                    context = context.applicationContext,
                    force = false,
                )
            }
        }

        contentVersion++

        store.seedDevelopmentDefaultsIfNeeded()
        pluginStore.seedDevelopmentDefaultsIfNeeded()

        launch {
            providerCodeSync.syncMissing(
                pluginStore.repositories()
            )
        }

        store.manifestUrls().forEach { manifestUrl ->
            runCatching {
                ExtensionInstaller.installStremioAddon(manifestUrl)
            }.onSuccess { extension ->
                engine.install(extension)
                engine.setExtensionEnabled(
                    id =
                        extension.descriptor.id,
                    enabled =
                        store.isAddonEnabled(
                            manifestUrl
                        ),
                )
            }
        }

        booting = false
        contentVersion++
    }

    BackHandler(
        enabled =
            !booting &&
                showProfilePicker &&
                profilePickerOpenedFromApp,
    ) {
        showProfilePicker = false
        profilePickerOpenedFromApp = false
    }

    BackHandler(
        enabled =
            !booting &&
                !showProfilePicker &&
                selectedMedia == null &&
                selectedCatalogRow != null,
    ) {
        selectedCatalogRow = null
    }

    BackHandler(
        enabled =
            !booting &&
                !showProfilePicker &&
                selectedMedia == null &&
                selectedCatalogRow == null &&
                (
                    selectedTab != AppTab.HOME ||
                        settingsPage != SettingsPage.ROOT
                ),
    ) {
        if (
            selectedTab == AppTab.SETTINGS &&
            settingsPage != SettingsPage.ROOT
        ) {
            settingsPage =
                parentSettingsPage(
                    settingsPage
                )
        } else {
            selectedTab = AppTab.HOME
            settingsPage = SettingsPage.ROOT
        }
    }

    var transitionCatalogRow by remember {
        mutableStateOf<CatalogRow?>(null)
    }
    var transitionMedia by remember {
        mutableStateOf<MediaItem?>(null)
    }
    var transitionLibraryEntry by remember {
        mutableStateOf<LibraryPlaybackEntry?>(null)
    }

    selectedCatalogRow?.let { row ->
        transitionCatalogRow = row
    }
    selectedMedia?.let { media ->
        transitionMedia = media
        transitionLibraryEntry = selectedLibraryEntry
    }

    val appSurface =
        when {
            !startupDestinationResolved -> AppSurface.STARTUP
            showProfilePicker -> AppSurface.PROFILES
            selectedEntityTarget != null -> AppSurface.ENTITY_RESULTS
            selectedMedia != null -> AppSurface.DETAILS
            selectedCatalogRow != null -> AppSurface.CATALOG
            else -> AppSurface.ROOT
        }

    AnimatedContent(
        targetState = appSurface,
        transitionSpec = {
            vueoFadeThrough(
                enterDurationMillis = 340,
                exitDurationMillis = 190,
                enterDelayMillis = 28,
                initialScale = 0.988f,
                targetScale = 0.994f,
            )
        },
        modifier = Modifier.fillMaxSize(),
        label = "VUEO app surface transition",
    ) { surface ->
        when (surface) {
            AppSurface.STARTUP -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                VueoPalette.Background
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    VueoBrandLockup()
                }
            }

            AppSurface.PROFILES -> {
                WhosWatchingScreen(
                    profileStore = profileStore,
                    profileVersion = profileVersion,
                    onProfileSelected = {
                        selectedMedia = null
                        selectedEntityTarget = null
                        selectedCatalogRow = null
                        selectedLibraryEntry = null
                        mediaBackStack = emptyList()
                        selectedTab =
                            if (profilePickerOpenedFromApp) {
                                AppTab.SETTINGS
                            } else {
                                AppTab.HOME
                            }
                        settingsPage = SettingsPage.ROOT
                        libraryVersion++
                        profileVersion++
                        showProfilePicker = false
                        profilePickerOpenedFromApp = false
                    },
                    onProfilesChanged = {
                        profileVersion++
                        libraryVersion++
                    },
                )
            }

            AppSurface.CATALOG -> {
                transitionCatalogRow?.let { row ->
                    CatalogSeeAllScreen(
                        row = row,
                        libraryStore = libraryStore,
                        libraryVersion = libraryVersion,
                        onLibraryChanged = {
                            libraryVersion++
                        },
                        onBack = {
                            selectedCatalogRow = null
                        },
                        onMediaClick = { item ->
                            selectedLibraryEntry = null
                            mediaBackStack = emptyList()
                            selectedMedia = item
                        },
                    )
                }
            }

            AppSurface.ENTITY_RESULTS -> {
                selectedEntityTarget?.let { target ->
                    MediaEntityResultsScreen(
                        engine = engine,
                        target = target,
                        tmdbApiKey = pluginStore.tmdbApiKey(),
                        onBack = {
                            selectedEntityTarget = null
                        },
                        onMediaClick = { next ->
                            selectedMedia?.let { current ->
                                mediaBackStack = mediaBackStack + current
                            }
                            selectedLibraryEntry = null
                            selectedMedia = next
                            selectedEntityTarget = null
                        },
                    )
                }
            }

            AppSurface.DETAILS -> {
                transitionMedia?.let { detailItem ->
                    MediaDetailsScreen(
                        engine = engine,
                        settingsStore = settingsStore,
                        initialItem = detailItem,
                        initialLibraryEntry = transitionLibraryEntry,
                        onLibraryChanged = {
                            libraryVersion++
                        },
                        onBack = {
                            val previous =
                                mediaBackStack.lastOrNull()

                            if (previous == null) {
                                selectedMedia = null
                                selectedLibraryEntry = null
                            } else {
                                selectedMedia = previous
                                mediaBackStack =
                                    mediaBackStack.dropLast(1)
                            }
                        },
                        onMediaClick = { next ->
                            selectedMedia?.let { current ->
                                mediaBackStack =
                                    mediaBackStack + current
                            }

                            selectedLibraryEntry = null
                            selectedMedia = next
                        },
                        onEntityClick = { target ->
                            selectedEntityTarget = target
                        },
                    )
                }
            }

            AppSurface.ROOT -> Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            top = 6.dp,
                            bottom = 10.dp,
                        ),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                    shape =
                        RoundedCornerShape(
                            34.dp
                        ),
                    color =
                        VueoPalette.Nav
                            .copy(
                                alpha = .97f
                            ),
                    border =
                        androidx.compose
                            .foundation
                            .BorderStroke(
                                width = 1.dp,
                                color =
                                    VueoPalette.Stroke
                                        .copy(
                                            alpha = .55f
                                        ),
                            ),
                    shadowElevation =
                        14.dp,
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxSize(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        BottomTab(
                            tab = AppTab.HOME,
                            selected = selectedTab,
                            icon =
                                Icons.Default.Home,
                            label = "Home",
                        ) {
                            selectedCatalogRow =
                                null
                            selectedTab = it
                            settingsPage =
                                SettingsPage.ROOT
                        }

                        BottomTab(
                            tab = AppTab.SEARCH,
                            selected = selectedTab,
                            icon =
                                Icons.Default.Search,
                            label = "Search",
                        ) {
                            selectedCatalogRow =
                                null
                            selectedTab = it
                            settingsPage =
                                SettingsPage.ROOT
                        }

                        BottomTab(
                            tab = AppTab.LIBRARY,
                            selected = selectedTab,
                            icon =
                                Icons.Default
                                    .VideoLibrary,
                            label = "Library",
                        ) {
                            selectedCatalogRow =
                                null
                            selectedTab = it
                            settingsPage =
                                SettingsPage.ROOT
                        }

                        ProfileBottomTab(
                            tab = AppTab.SETTINGS,
                            selected = selectedTab,
                            profile =
                                profileStore
                                    .activeProfile(),
                        ) {
                            selectedCatalogRow =
                                null
                            selectedTab = it
                            settingsPage =
                                SettingsPage.ROOT
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                )
                .padding(padding)
                .clipToBounds(),
        ) {
            AnimatedContent(
                targetState = selectedTab to settingsPage,
                transitionSpec = {
                    vueoFadeThrough(
                        enterDurationMillis = 300,
                        exitDurationMillis = 170,
                        enterDelayMillis = 22,
                        initialScale = 0.992f,
                        targetScale = 0.996f,
                    )
                },
                modifier = Modifier.fillMaxSize(),
                label = "VUEO main navigation transition",
            ) { (currentTab, currentSettingsPage) ->
                when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    engine = engine,
                    contentVersion = contentVersion,
                    booting = booting,
                    libraryStore = libraryStore,
                    libraryVersion = libraryVersion,
                    onLibraryChanged = {
                        libraryVersion++
                    },
                    onOpenContentManager = {
                        selectedTab =
                            AppTab.SETTINGS
                        settingsPage =
                            SettingsPage
                                .CONTENT_MANAGER
                    },
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                    onPlaybackClick = {
                        entry ->
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            entry
                        selectedMedia =
                            entry.media
                    },
                    onSeeAll = {
                        row ->
                        selectedCatalogRow =
                            row
                    },
                )

                AppTab.SEARCH -> SearchScreen(
                    engine = engine,
                    contentVersion =
                        contentVersion,
                    booting = booting,
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                    },
                    typeFilter =
                        searchTypeFilter,
                    onTypeFilterChange = {
                        searchTypeFilter = it
                    },
                    sortMode =
                        searchSortMode,
                    onSortModeChange = {
                        searchSortMode = it
                    },
                    genre = searchGenre,
                    onGenreChange = {
                        searchGenre = it
                    },
                    listState =
                        searchListState,
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                )

                AppTab.LIBRARY -> LibraryScreen(
                    store =
                        libraryStore,
                    activeProfile =
                        profileStore.activeProfile(),
                    version =
                        libraryVersion,
                    onVersionChanged = {
                        libraryVersion++
                    },
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                    onPlaybackClick = {
                        entry ->

                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            entry
                        selectedMedia =
                            entry.media
                    },
                )

                AppTab.SETTINGS -> when (
                    currentSettingsPage
                ) {
                    SettingsPage.ROOT ->
                        VueoSettingsHub(
                            engine = engine,
                            settingsStore = settingsStore,
                            profileStore = profileStore,
                            profileVersion = profileVersion,
                            onProfiles = {
                                showProfilePicker = true
                                profilePickerOpenedFromApp = true
                            },
                            onContentManager = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                            onEnhancements = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                            onPlayback = {
                                settingsPage = SettingsPage.PLAYBACK
                            },
                            onSubtitles = {
                                settingsPage = SettingsPage.SUBTITLES
                            },
                            onSources = {
                                settingsPage = SettingsPage.SOURCES
                            },
                            onAppearance = {
                                settingsPage = SettingsPage.APPEARANCE
                            },
                            onDataStorage = {
                                settingsPage = SettingsPage.DATA_STORAGE
                            },
                            onAbout = {
                                settingsPage = SettingsPage.ABOUT
                            },
                        )

                    SettingsPage.CONTENT_MANAGER ->
                        ContentManagerScreen(
                            engine = engine,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                            onAddons = {
                                settingsPage = SettingsPage.ADDONS
                            },
                            onPlugins = {
                                settingsPage = SettingsPage.PLUGINS
                            },
                            onCatalogOrder = {
                                settingsPage =
                                    SettingsPage.CATALOG_ORDER
                            },
                        )

                    SettingsPage.ADDONS ->
                        AddonsScreen(
                            engine = engine,
                            store = store,
                            contentVersion = contentVersion,
                            onContentChanged = {
                                contentVersion++
                            },
                            onBack = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                        )

                    SettingsPage.PLUGINS ->
                        PluginsScreen(
                            onBack = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                        )
                    SettingsPage.CATALOG_ORDER ->
                        CatalogOrderScreen(
                            engine = engine,
                            store = store,
                            contentVersion =
                                contentVersion,
                            onContentChanged = {
                                contentVersion++
                            },
                            onBack = {
                                settingsPage =
                                    SettingsPage.CONTENT_MANAGER
                            },
                        )

                    SettingsPage.ENHANCEMENTS ->
                        EnhancementsSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                            onTmdb = {
                                settingsPage = SettingsPage.TMDB
                            },
                            onMdblist = {
                                settingsPage = SettingsPage.MDBLIST
                            },
                        )

                    SettingsPage.TMDB ->
                        TmdbEnhancementSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                        )

                    SettingsPage.MDBLIST ->
                        MdblistEnhancementSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                        )


                    SettingsPage.PLAYBACK ->
                        PlaybackSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.SUBTITLES ->
                        SubtitleSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.SOURCES ->
                        SourceSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.APPEARANCE ->
                        AppearanceSettingsScreen(
                            settingsStore =
                                settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.DATA_STORAGE ->
                        DataStorageSettingsScreen(
                            libraryStore = libraryStore,
                            settingsStore = settingsStore,
                            onLibraryChanged = {
                                libraryVersion++
                            },
                            onCatalogCacheCleared = {
                                contentVersion++
                            },
                            onPersistentDataChanged = {
                                engine
                                    .stremioAddons()
                                    .forEach {
                                        engine.uninstall(
                                            it.descriptor.id
                                        )
                                    }

                                store.seedDevelopmentDefaultsIfNeeded()
                                pluginStore.seedDevelopmentDefaultsIfNeeded()

                                store.manifestUrls()
                                    .forEach { manifestUrl ->
                                        runCatching {
                                            ExtensionInstaller
                                                .installStremioAddon(
                                                    manifestUrl
                                                )
                                        }.onSuccess { extension ->
                                    engine.install(
                                        extension
                                    )
                                    engine.setExtensionEnabled(
                                        id =
                                            extension.descriptor.id,
                                        enabled =
                                            store.isAddonEnabled(
                                                manifestUrl
                                            ),
                                    )
                                }
                                    }

                                providerCodeSync.syncMissing(
                                    pluginStore.repositories()
                                )

                                profileStore.ensureDefaultProfile()
                                VueoPalette.applyAccent(
                                    settingsStore.appAccent()
                                )
                                selectedLibraryEntry = null
                                contentVersion++
                                libraryVersion++
                                profileVersion++
                            },
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.ABOUT ->
                        AboutVueoSettingsScreen(
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )
                }
            }
        }
        }
    }
        }
    }
}

@Composable
private fun RowScope.BottomTab(
    tab: AppTab,
    selected: AppTab,
    icon: ImageVector,
    label: String,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = {
            onSelect(tab)
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription =
                    label,
                modifier =
                    Modifier.size(
                        24.dp
                    ),
            )
        },
        label = {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight =
                    if (
                        selected == tab
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
            )
        },
        colors =
            NavigationBarItemDefaults
                .colors(
                    selectedIconColor =
                        VueoPalette.Accent,
                    selectedTextColor =
                        Color.White,
                    indicatorColor =
                        VueoPalette.Accent
                            .copy(
                                alpha = .16f
                            ),
                    unselectedIconColor =
                        VueoPalette.Muted,
                    unselectedTextColor =
                        VueoPalette.Muted,
                ),
    )
}

@Composable
private fun RowScope.ProfileBottomTab(
    tab: AppTab,
    selected: AppTab,
    profile: VueoProfile,
    onSelect: (AppTab) -> Unit,
) {
    val context =
        LocalContext.current
    val profileTabSelected = selected == tab
    val profileAvatarSize by animateDpAsState(
        targetValue = if (profileTabSelected) 30.dp else 28.dp,
        animationSpec = tween(
            durationMillis = VueoMotion.QUICK_MS,
            easing = VueoMotion.EaseOut,
        ),
        label = "VUEO profile tab avatar size",
    )
    val profileBorderWidth by animateDpAsState(
        targetValue = if (profileTabSelected) 2.dp else 1.dp,
        animationSpec = tween(
            durationMillis = VueoMotion.QUICK_MS,
            easing = VueoMotion.EaseOut,
        ),
        label = "VUEO profile tab border width",
    )
    val profileBorderColor by animateColorAsState(
        targetValue =
            if (profileTabSelected) {
                VueoPalette.Accent
            } else {
                VueoPalette.Stroke
            },
        animationSpec = tween(
            durationMillis = VueoMotion.QUICK_MS,
            easing = VueoMotion.EaseOut,
        ),
        label = "VUEO profile tab border color",
    )

    val avatarDrawable =
        remember(
            profile.avatar,
            context,
        ) {
            if (
                profile.avatar
                    .startsWith(
                        "avatar_"
                    )
            ) {
                context.resources
                    .getIdentifier(
                        profile.avatar,
                        "drawable",
                        context.packageName,
                    )
                    .takeIf {
                        it != 0
                    }
            } else {
                null
            }
        }

    NavigationBarItem(
        selected = selected == tab,
        onClick = {
            onSelect(tab)
        },
        icon = {
            Surface(
                modifier =
                    Modifier.size(
                        profileAvatarSize
                    ),
                shape = CircleShape,
                color =
                    VueoPalette.SurfaceStrong,
                border =
                    androidx.compose
                        .foundation
                        .BorderStroke(
                            width =
                                profileBorderWidth,
                            color =
                                profileBorderColor,
                        ),
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center,
                ) {
                    if (
                        avatarDrawable != null
                    ) {
                        Image(
                            painter =
                                painterResource(
                                    avatarDrawable
                                ),
                            contentDescription =
                                "Profile",
                            contentScale =
                                ContentScale.Crop,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text =
                                profile.name
                                    .trim()
                                    .firstOrNull()
                                    ?.uppercase()
                                    ?: "P",
                            color =
                                if (
                                    selected == tab
                                ) {
                                    VueoPalette.Accent
                                } else {
                                    VueoPalette.Muted
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        },
        label = {
            Text(
                text =
                    profile.name
                        .trim()
                        .ifBlank { "Profile" },
                fontSize = 11.sp,
                fontWeight =
                    if (
                        selected == tab
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors =
            NavigationBarItemDefaults
                .colors(
                    selectedIconColor =
                        VueoPalette.Accent,
                    selectedTextColor =
                        Color.White,
                    indicatorColor =
                        VueoPalette.Accent
                            .copy(
                                alpha = .12f
                            ),
                    unselectedIconColor =
                        VueoPalette.Muted,
                    unselectedTextColor =
                        VueoPalette.Muted,
                ),
    )
}
