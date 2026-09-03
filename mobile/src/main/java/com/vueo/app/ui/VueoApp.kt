package com.vueo.app.ui

import android.app.Activity
import android.net.Uri
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.graphics.Typeface
import android.util.TypedValue
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
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
import com.vueo.app.core.extensions.AddonCategory
import com.vueo.app.core.extensions.ExtensionInstaller
import com.vueo.app.core.extensions.primaryAddonCategory
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.extensions.UnifiedMediaEngine
import com.vueo.app.core.extensions.SourceRanker
import com.vueo.app.core.extensions.SourceCleaner
import com.vueo.app.core.extensions.SourceDiscoveryCache
import com.vueo.app.core.extensions.CatalogDiscoveryCache
import com.vueo.app.core.enrichment.GeminiClient
import com.vueo.app.core.enrichment.MdblistClient
import com.vueo.app.core.enrichment.MediaRating
import com.vueo.app.core.enrichment.RichDetailsClient
import com.vueo.app.core.enrichment.TmdbEnhancementClient
import com.vueo.app.core.dna.UserDnaEngine
import com.vueo.app.core.dna.UserDnaPreferences
import com.vueo.app.core.model.CatalogRow
import com.vueo.app.BuildConfig
import com.vueo.app.R
import com.vueo.app.core.storage.PlaybackStore
import com.vueo.app.core.storage.LibraryStore
import com.vueo.app.core.storage.ProfileStore
import com.vueo.app.core.storage.VueoProfile
import com.vueo.app.core.storage.LibraryPlaybackEntry
import com.vueo.app.core.storage.PreferredQuality
import com.vueo.app.core.storage.PlayerOrientation
import com.vueo.app.core.storage.PlayerVideoFit
import com.vueo.app.core.storage.SettingsStore
import com.vueo.app.core.player.PlayerSkipKind
import com.vueo.app.core.player.PlayerSkipRepository
import com.vueo.app.core.player.PlayerSkipSegment
import com.vueo.app.core.player.PlayerPlaybackPhase
import com.vueo.app.core.player.PlayerSourceAssessment
import com.vueo.app.core.player.PlayerSourceAudioMatch
import com.vueo.app.core.player.PlayerSourcePolicy
import com.vueo.app.core.player.PlayerSourceRecoverySession
import com.vueo.app.core.player.PLAYER_REBUFFER_TIMEOUT_MS
import com.vueo.app.core.player.PLAYER_RECOVERY_SOURCE_TIMEOUT_MS
import com.vueo.app.core.player.PLAYER_STARTUP_TIMEOUT_MS
import com.vueo.app.core.storage.VueoDataMigration
import com.vueo.app.core.stremio.SimpleHttp
import com.vueo.app.core.update.VueoUpdateManager
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.plugin.PluginStore
import com.vueo.app.core.plugin.ProviderCodeSyncManager
import com.vueo.app.core.plugin.ProviderCodeStore
import com.vueo.app.core.plugin.ProviderHealthStatus
import com.vueo.app.core.plugin.ProviderHealthRecord
import com.vueo.app.core.plugin.PluginHealthStore
import com.vueo.app.core.plugin.TmdbResolver
import com.vueo.app.core.plugin.PluginSourceEngine
import com.vueo.app.core.plugin.PluginRepositoryDescriptor
import com.vueo.app.core.plugin.PluginRepositoryClient
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaCompany
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.MediaPerson
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.storage.AddonStore
import com.vueo.app.ui.components.NetworkImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

private enum class AppTab {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
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
    GEMINI,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    UPDATES,
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
        SettingsPage.MDBLIST,
        SettingsPage.GEMINI ->
            SettingsPage.ENHANCEMENTS

        SettingsPage.ROOT ->
            SettingsPage.ROOT

        else ->
            SettingsPage.ROOT
    }

private enum class HomeMediaFilter(
    val label: String,
) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
}


private enum class SearchTypeFilter(
    val label: String,
) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    ANIME("Anime"),
}

private enum class SearchSortMode(
    val label: String,
) {
    POPULAR("Popular"),
    TRENDING("Trending"),
    NEWEST("Newest"),
}

private enum class SearchMode(
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
    var selectedMedia by remember {
        mutableStateOf<MediaItem?>(null)
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
        mutableStateOf(
            profileStore
                .shouldShowPickerOnStartup()
        )
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

    if (
        !booting &&
        showProfilePicker
    ) {
        WhosWatchingScreen(
            profileStore =
                profileStore,
            profileVersion =
                profileVersion,
            onProfileSelected = {
                selectedMedia = null
                selectedCatalogRow = null
                selectedLibraryEntry =
                    null
                mediaBackStack =
                    emptyList()
                selectedTab =
                    if (
                        profilePickerOpenedFromApp
                    ) {
                        AppTab.SETTINGS
                    } else {
                        AppTab.HOME
                    }
                settingsPage =
                    SettingsPage.ROOT
                libraryVersion++
                profileVersion++
                showProfilePicker =
                    false
                profilePickerOpenedFromApp =
                    false
            },
            onProfilesChanged = {
                profileVersion++
                libraryVersion++
            },
        )
        return
    }

    if (
        selectedCatalogRow != null &&
        selectedMedia == null
    ) {
        CatalogSeeAllScreen(
            row =
                selectedCatalogRow!!,
            libraryStore =
                libraryStore,
            libraryVersion =
                libraryVersion,
            onLibraryChanged = {
                libraryVersion++
            },
            onBack = {
                selectedCatalogRow =
                    null
            },
            onMediaClick = {
                item ->
                selectedLibraryEntry =
                    null
                mediaBackStack =
                    emptyList()
                selectedMedia = item
            },
        )
        return
    }

    if (selectedMedia != null) {
        MediaDetailsScreen(
            engine = engine,
            settingsStore =
                settingsStore,
            initialItem = selectedMedia!!,
            initialLibraryEntry =
                selectedLibraryEntry,
            onLibraryChanged = {
                libraryVersion++
            },
            onBack = {
                val previous =
                    mediaBackStack
                        .lastOrNull()

                if (previous == null) {
                    selectedMedia = null
                    selectedLibraryEntry =
                        null
                } else {
                    selectedMedia =
                        previous
                    mediaBackStack =
                        mediaBackStack
                            .dropLast(1)
                }
            },
            onMediaClick = { next ->
                selectedMedia?.let {
                    current ->

                    mediaBackStack =
                        mediaBackStack +
                            current
                }

                selectedLibraryEntry =
                    null
                selectedMedia = next
            },
        )
        return
    }

    Scaffold(
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
            when (selectedTab) {
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
                    settingsPage
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
                            onUpdates = {
                                settingsPage = SettingsPage.UPDATES
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
                            onGemini = {
                                settingsPage = SettingsPage.GEMINI
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

                    SettingsPage.GEMINI ->
                        GeminiEnhancementSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage =
                                    SettingsPage.ENHANCEMENTS
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

                    SettingsPage.UPDATES ->
                        UpdatesSettingsScreen(
                            settingsStore = settingsStore,
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
                        if (
                            selected == tab
                        ) {
                            30.dp
                        } else {
                            28.dp
                        }
                    ),
                shape = CircleShape,
                color =
                    VueoPalette.SurfaceStrong,
                border =
                    androidx.compose
                        .foundation
                        .BorderStroke(
                            width =
                                if (
                                    selected == tab
                                ) {
                                    2.dp
                                } else {
                                    1.dp
                                },
                            color =
                                if (
                                    selected == tab
                                ) {
                                    VueoPalette.Accent
                                } else {
                                    VueoPalette.Stroke
                                },
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

@Composable
private fun HomeScreen(
    engine: UnifiedMediaEngine,
    contentVersion: Int,
    booting: Boolean,
    libraryStore: LibraryStore,
    libraryVersion: Int,
    onLibraryChanged: () -> Unit,
    onOpenContentManager: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlaybackClick:
        (LibraryPlaybackEntry) -> Unit,
    onSeeAll: (CatalogRow) -> Unit,
) {
    val context =
        LocalContext.current
    val homeAddonStore =
        remember {
            AddonStore(
                context.applicationContext
            )
        }

    val catalogOrder =
        remember(
            contentVersion
        ) {
            homeAddonStore
                .catalogOrder()
        }


    val profileStore =
        remember {
            ProfileStore(
                context.applicationContext
            )
        }

    val dnaPreferences =
        remember {
            UserDnaPreferences(
                context.applicationContext
            )
        }

    val dnaEngine =
        remember(
            libraryStore
        ) {
            UserDnaEngine(
                libraryStore
            )
        }

    val activeProfileId =
        remember(
            libraryVersion
        ) {
            profileStore
                .activeProfileId()
        }

    val personalizedHomeEnabled =
        dnaPreferences
            .shouldPersonalizeRecommendations(
                activeProfileId
            )

        val listState =
        rememberLazyListState()

    var rows by remember(
        contentVersion,
        catalogOrder,
    ) {
        mutableStateOf(
            orderHomeCatalogRows(
                rows =
                    CatalogDiscoveryCache
                        .home(
                            allowStale = true
                        )
                        .orEmpty(),
                catalogOrder =
                    catalogOrder,
            )
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var continueWatchingAction by remember {
        mutableStateOf<
            LibraryPlaybackEntry?
        >(null)
    }

    var catalogAction by remember {
        mutableStateOf<MediaItem?>(
            null
        )
    }

    var continueWatchingActionAnchor by remember {
        mutableStateOf(
            IntOffset.Zero
        )
    }

    var catalogActionAnchor by remember {
        mutableStateOf(
            IntOffset.Zero
        )
    }

    LaunchedEffect(
        contentVersion
    ) {
        CatalogDiscoveryCache
            .home(
                allowStale = true
            )
            ?.takeIf {
                it.isNotEmpty()
            }
            ?.let {
                rows =
                    orderHomeCatalogRows(
                        rows = it,
                        catalogOrder =
                            catalogOrder,
                    )
            }

        if (booting) {
            loading = false
            return@LaunchedEffect
        }

        loading =
            rows.isEmpty()
        error = null

        runCatching {
            engine.loadCatalogRows(
                forceRefresh =
                    rows.isNotEmpty(),
                catalogOrder =
                    catalogOrder,
            )
        }.onSuccess {
            fresh ->
            if (
                fresh.isNotEmpty()
            ) {
                rows =
                    orderHomeCatalogRows(
                        rows = fresh,
                        catalogOrder =
                            catalogOrder,
                    )

                CatalogDiscoveryCache
                    .persistHome(
                        context =
                            context
                                .applicationContext,
                        rows = fresh,
                    )
            }
        }.onFailure {
            failure ->
            error =
                failure.message

            if (
                rows.isEmpty()
            ) {
                rows =
                    orderHomeCatalogRows(
                        rows =
                            CatalogDiscoveryCache
                                .home(
                                    allowStale =
                                        true
                                )
                                .orEmpty(),
                        catalogOrder =
                            catalogOrder,
                    )
            }
        }

        loading = false
    }

    val featuredItems =
        remember(rows) {
            val allItems =
                rows
                    .asSequence()
                    .flatMap {
                        it.items
                            .asSequence()
                    }
                    .distinctBy {
                        "${it.type}:${it.id}"
                    }

            val withBackdrop =
                allItems
                    .filter {
                        !it.background
                            .isNullOrBlank()
                    }
                    .take(7)
                    .toList()

            if (
                withBackdrop
                    .isNotEmpty()
            ) {
                withBackdrop
            } else {
                rows
                    .asSequence()
                    .flatMap {
                        it.items
                            .asSequence()
                    }
                    .distinctBy {
                        "${it.type}:${it.id}"
                    }
                    .take(7)
                    .toList()
            }
        }

    val continueWatching =
        remember(
            libraryVersion
        ) {
            libraryStore
                .continueWatching()
                .take(12)
        }

    val watchHistory =
        remember(
            libraryVersion
        ) {
            libraryStore.history()
        }

    val dnaSnapshot =
        remember(
            activeProfileId,
            libraryVersion,
            personalizedHomeEnabled,
        ) {
            if (
                personalizedHomeEnabled
            ) {
                dnaEngine.build()
            } else {
                null
            }
        }

    val catalogCandidates =
        remember(rows) {
            rows
                .asSequence()
                .flatMap {
                    it.items.asSequence()
                }
                .distinctBy {
                    "${it.type}:${it.id}"
                }
                .toList()
        }

    val watchedTitleKeys =
        remember(
            watchHistory
        ) {
            watchHistory
                .asSequence()
                .map {
                    "${it.media.type}:${it.media.id}"
                }
                .toSet()
        }

    val forYouItems =
        remember(
            catalogCandidates,
            dnaSnapshot,
            watchedTitleKeys,
            personalizedHomeEnabled,
        ) {
            val snapshot =
                dnaSnapshot

            if (
                !personalizedHomeEnabled ||
                snapshot == null ||
                !snapshot.hasUsefulData
            ) {
                emptyList()
            } else {
                catalogCandidates
                    .asSequence()
                    .filterNot {
                        "${it.type}:${it.id}" in
                            watchedTitleKeys
                    }
                    .mapNotNull {
                        candidate ->
                        dnaEngine
                            .matchPercent(
                                media =
                                    candidate,
                                dna =
                                    snapshot,
                            )
                            ?.takeIf {
                                it >= 55
                            }
                            ?.let {
                                score ->
                                candidate to score
                            }
                    }
                    .sortedByDescending {
                        it.second
                    }
                    .take(12)
                    .map {
                        it.first
                    }
                    .toList()
            }
        }

    val becauseYouWatchedSeed =
        remember(
            watchHistory,
            personalizedHomeEnabled,
        ) {
            if (
                !personalizedHomeEnabled
            ) {
                null
            } else {
                watchHistory
                    .asSequence()
                    .filter {
                        entry ->
                        entry.isCompleted ||
                            entry.positionMs >=
                                120_000L ||
                            entry.progressFraction >=
                                .20f
                    }
                    .distinctBy {
                        entry ->
                        "${entry.media.type}:${entry.media.id}"
                    }
                    .firstOrNull()
                    ?.media
            }
        }

    val becauseYouWatchedItems =
        remember(
            becauseYouWatchedSeed,
            catalogCandidates,
            watchedTitleKeys,
            forYouItems,
            personalizedHomeEnabled,
        ) {
            val seed =
                becauseYouWatchedSeed

            if (
                !personalizedHomeEnabled ||
                seed == null
            ) {
                emptyList()
            } else {
                val seedGenres =
                    seed.genres
                        .map {
                            it.trim()
                                .lowercase()
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .toSet()

                val related =
                    CatalogDiscoveryCache
                        .related(
                            seed,
                            limit = 30,
                        )

                val fallback =
                    catalogCandidates
                        .filter {
                            candidate ->
                            candidate.type ==
                                seed.type &&
                                candidate.genres
                                    .any {
                                        genre ->
                                        genre.trim()
                                            .lowercase() in
                                            seedGenres
                                    }
                        }

                val forYouKeys =
                    forYouItems
                        .asSequence()
                        .map {
                            "${it.type}:${it.id}"
                        }
                        .toSet()

                (
                    related +
                        fallback
                )
                    .asSequence()
                    .distinctBy {
                        "${it.type}:${it.id}"
                    }
                    .filterNot {
                        candidate ->
                        val key =
                            "${candidate.type}:${candidate.id}"

                        key ==
                            "${seed.type}:${seed.id}" ||
                            key in
                                watchedTitleKeys ||
                            key in
                                forYouKeys
                    }
                    .take(12)
                    .toList()
            }
        }

        LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                bottom = 24.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                16.dp
            ),
    ) {
        if (
            featuredItems
                .isNotEmpty()
        ) {
            item(
                key =
                    "home_featured"
            ) {
                HomeFeaturedCarousel(
                    items =
                        featuredItems,
                    onViewDetails =
                        onMediaClick,
                )
            }
        }

        if (
            (booting || loading) &&
            rows.isEmpty()
        ) {
            item(
                key =
                    "home_loading"
            ) {
                HomeLoadingState()
            }
        }

        if (
            !booting &&
            !loading &&
            rows.isEmpty()
        ) {
            item(
                key =
                    "home_empty"
            ) {
                EmptyHomeCard(
                    hasAddons =
                        engine
                            .stremioAddons()
                            .isNotEmpty(),
                    error = error,
                    onOpenContentManager =
                        onOpenContentManager,
                )
            }
        }

        if (
            continueWatching
                .isNotEmpty()
        ) {
            item(
                key =
                    "continue_watching"
            ) {
                HomeContinueWatchingSection(
                    entries =
                        continueWatching,
                    onPlaybackClick =
                        onPlaybackClick,
                    onHold = {
                        entry,
                        anchor ->
                        continueWatchingAction =
                            entry
                        continueWatchingActionAnchor =
                            anchor
                    },
                )
            }
        }

        if (
        forYouItems.size >= 4
    ) {
        item(
            key =
                "home_for_you"
        ) {
            HomePersonalizedSection(
                title =
                    "For You",
                contextLabel =
                    "Your DNA",
                items =
                    forYouItems,
                onMediaClick =
                    onMediaClick,
                onMediaHold = {
                    item,
                    anchor ->
                    catalogAction = item
                    catalogActionAnchor = anchor
                },
            )
        }
    }

    if (
        becauseYouWatchedSeed !=
            null &&
        becauseYouWatchedItems
            .size >= 4
    ) {
        item(
            key =
                "home_because_you_watched"
        ) {
            HomePersonalizedSection(
                title =
                    "Because You Watched " +
                        becauseYouWatchedSeed
                            .name,
                contextLabel =
                    "Recent viewing",
                items =
                    becauseYouWatchedItems,
                onMediaClick =
                    onMediaClick,
                onMediaHold = {
                    item,
                    anchor ->
                    catalogAction = item
                    catalogActionAnchor = anchor
                },
            )
        }
    }

            items(
            items = rows,
            key = {
                "catalog:${it.id}"
            },
        ) {
            row ->
            CatalogSection(
                row = row,
                onMediaClick =
                    onMediaClick,
                onMediaHold = {
                    item,
                    anchor ->
                    catalogAction = item
                    catalogActionAnchor = anchor
                },
                onSeeAll = {
                    onSeeAll(row)
                },
            )
        }
    }

    continueWatchingAction?.let {
        entry ->
        ContinueWatchingActionsDialog(
            entry = entry,
            anchor =
                continueWatchingActionAnchor,
            onDismiss = {
                continueWatchingAction =
                    null
            },
            onResume = {
                continueWatchingAction =
                    null
                onPlaybackClick(
                    entry
                )
            },
            onViewDetails = {
                continueWatchingAction =
                    null
                onMediaClick(
                    entry.media
                )
            },
            onRemove = {
                libraryStore
                    .removeFromContinueWatching(
                        entry
                    )
                continueWatchingAction =
                    null
                onLibraryChanged()
            },
        )
    }

    catalogAction?.let {
        item ->
        CatalogActionsDialog(
            item = item,
            anchor =
                catalogActionAnchor,
            isWatchlisted =
                libraryStore
                    .isWatchlisted(
                        item
                    ),
            isMarkedWatched =
                libraryStore
                    .isMarkedWatched(
                        item
                    ),
            onDismiss = {
                catalogAction = null
            },
            onViewDetails = {
                catalogAction = null
                onMediaClick(item)
            },
            onToggleWatchlist = {
                libraryStore
                    .toggleWatchlist(
                        item
                    )
                catalogAction = null
                onLibraryChanged()
            },
            onToggleWatched = {
                libraryStore
                    .setMarkedWatched(
                        media = item,
                        watched =
                            !libraryStore
                                .isMarkedWatched(
                                    item
                                ),
                    )
                catalogAction = null
                onLibraryChanged()
            },
        )
    }
}

@Composable
private fun HomeFeaturedCarousel(
    items: List<MediaItem>,
    onViewDetails:
        (MediaItem) -> Unit,
) {
    var selectedIndex by remember(
        items
    ) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        items.size
    ) {
        selectedIndex =
            selectedIndex
                .coerceIn(
                    0,
                    (
                        items.size - 1
                    )
                        .coerceAtLeast(
                            0
                        ),
                )

        if (
            items.size <= 1
        ) {
            return@LaunchedEffect
        }

        while (true) {
            delay(6500L)

            selectedIndex =
                (
                    selectedIndex +
                        1
                ) % items.size
        }
    }

    val item =
        items[
            selectedIndex
                .coerceIn(
                    0,
                    items.lastIndex
                )
        ]

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        16f / 10f
                    )
                    .clickable {
                        onViewDetails(
                            item
                        )
                    },
        ) {
            NetworkImage(
                url =
                    item.background
                        ?: item.poster,
                contentDescription =
                    item.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    item.name,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0.00f to
                                            Color.Black
                                                .copy(
                                                    alpha = .08f
                                                ),
                                        0.48f to
                                            Color.Black
                                                .copy(
                                                    alpha = .08f
                                                ),
                                        0.78f to
                                            Color.Black
                                                .copy(
                                                    alpha = .58f
                                                ),
                                        1.00f to
                                            VueoPalette
                                                .Background,
                                    )
                            )
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomCenter
                        )
                        .fillMaxWidth()
                        .padding(
                            start = 22.dp,
                            end = 22.dp,
                            bottom = 20.dp,
                        ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        9.dp
                    ),
            ) {
                Text(
                    text = item.name,
                    color =
                        Color.White,
                    fontSize = 28.sp,
                    lineHeight =
                        30.sp,
                    fontWeight =
                        FontWeight.Black,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                val metadata =
                    buildList {
                        item.type
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.replaceFirstChar {
                                it.uppercase()
                            }
                            ?.let(::add)

                        item.genres
                            .firstOrNull()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let(::add)

                        item.releaseInfo
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let(::add)
                    }
                        .joinToString(
                            "  •  "
                        )

                if (
                    metadata.isNotBlank()
                ) {
                    Text(
                        text =
                            metadata,
                        color =
                            Color.White
                                .copy(
                                    alpha = .88f
                                ),
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Medium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }

                Button(
                    onClick = {
                        onViewDetails(
                            item
                        )
                    },
                    modifier =
                        Modifier
                            .width(176.dp)
                            .height(44.dp),
                    shape =
                        RoundedCornerShape(
                            22.dp
                        ),
                ) {
                    Text(
                        text =
                            "View Details",
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.Bold,
                    )
                }
            }
        }

        if (
            items.size > 1
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.Center,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                items.indices
                    .forEach {
                        index ->
                        val selected =
                            index ==
                                selectedIndex

                        Box(
                            modifier =
                                Modifier
                                    .padding(
                                        horizontal =
                                            3.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            50
                                        )
                                    )
                                    .background(
                                        if (
                                            selected
                                        ) {
                                            VueoPalette
                                                .Accent
                                        } else {
                                            Color.White
                                                .copy(
                                                    alpha =
                                                        .42f
                                                )
                                        }
                                    )
                                    .size(
                                        width =
                                            if (
                                                selected
                                            ) {
                                                26.dp
                                            } else {
                                                7.dp
                                            },
                                        height =
                                            7.dp,
                                    )
                                    .clickable {
                                        selectedIndex =
                                            index
                                    },
                        )
                    }
            }
        }
    }
}

@Composable
private fun Modifier.clickOrHoldForMenu(
    onClick: () -> Unit,
    onHold: (IntOffset) -> Unit,
): Modifier =
    composed {
        val hapticFeedback =
            LocalHapticFeedback.current

        var windowOrigin by remember {
            mutableStateOf(
                IntOffset.Zero
            )
        }

        this
            .onGloballyPositioned {
                coordinates ->
                val position =
                    coordinates.positionInWindow()

                windowOrigin =
                    IntOffset(
                        x =
                            position.x
                                .roundToInt(),
                        y =
                            position.y
                                .roundToInt(),
                    )
            }
            .pointerInput(
                onClick,
                onHold,
            ) {
                detectTapGestures(
                    onPress = {
                        localPosition ->
                        var holdTriggered =
                            false

                        coroutineScope {
                            val holdJob =
                                launch {
                                    delay(
                                        MENU_HOLD_MS
                                    )

                                    holdTriggered =
                                        true

                                    hapticFeedback
                                        .performHapticFeedback(
                                            HapticFeedbackType
                                                .LongPress
                                        )

                                    onHold(
                                        IntOffset(
                                            x =
                                                windowOrigin.x +
                                                    localPosition.x
                                                        .roundToInt(),
                                            y =
                                                windowOrigin.y +
                                                    localPosition.y
                                                        .roundToInt(),
                                        )
                                    )
                                }

                            val released =
                                tryAwaitRelease()

                            holdJob.cancel()

                            if (
                                released &&
                                !holdTriggered
                            ) {
                                onClick()
                            }
                        }
                    },
                )
            }
    }

private data class MediaMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun ContinueWatchingActionsDialog(
    entry: LibraryPlaybackEntry,
    anchor: IntOffset,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onViewDetails: () -> Unit,
    onRemove: () -> Unit,
) {
    MediaActionsSheet(
        title = entry.media.name,
        subtitle =
            playbackEntrySubtitle(
                entry
            ),
        anchor = anchor,
        actions =
            listOf(
                MediaMenuAction(
                    label = "Resume",
                    icon =
                        Icons.Default.PlayArrow,
                    onClick = onResume,
                ),
                MediaMenuAction(
                    label = "View Details",
                    icon = Icons.Default.Info,
                    onClick = onViewDetails,
                ),
                MediaMenuAction(
                    label = "Remove from Continue Watching",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    onClick = onRemove,
                ),
            ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun CatalogActionsDialog(
    item: MediaItem,
    isWatchlisted: Boolean,
    isMarkedWatched: Boolean,
    anchor: IntOffset,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    MediaActionsSheet(
        anchor = anchor,
        title = item.name,
        subtitle = "Catalog options",
        actions =
            listOf(
                MediaMenuAction(
                    label =
                        if (isWatchlisted) {
                            "Remove from My List"
                        } else {
                            "Add to My List"
                        },
                    icon =
                        if (isWatchlisted) {
                            Icons.Default.Delete
                        } else {
                            Icons.Default.Add
                        },
                    onClick =
                        onToggleWatchlist,
                ),
                MediaMenuAction(
                    label = "View Details",
                    icon = Icons.Default.Info,
                    onClick = onViewDetails,
                ),
                MediaMenuAction(
                    label =
                        if (isMarkedWatched) {
                            "Mark as Unwatched"
                        } else {
                            "Mark as Watched"
                        },
                    icon =
                        Icons.Default.VideoLibrary,
                    onClick =
                        onToggleWatched,
                ),
            ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun MediaActionsSheet(
    title: String,
    subtitle: String?,
    anchor: IntOffset,
    actions: List<MediaMenuAction>,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment =
            Alignment.TopStart,
        offset =
            IntOffset(
                x =
                    (anchor.x - 28)
                        .coerceAtLeast(8),
                y =
                    (anchor.y - 28)
                        .coerceAtLeast(8),
            ),
        onDismissRequest =
            onDismiss,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = true,
            ),
    ) {
        Surface(
            modifier =
                Modifier.widthIn(
                    min = 218.dp,
                    max = 246.dp,
                ),
            shape =
                RoundedCornerShape(
                    18.dp
                ),
            color =
                Color(
                    0xF5141619
                ),
            border =
                androidx.compose
                    .foundation
                    .BorderStroke(
                        width = 1.dp,
                        color =
                            Color.White
                                .copy(
                                    alpha = .10f
                                ),
                    ),
            shadowElevation =
                20.dp,
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 9.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        1.dp
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(
                                1f
                            ),
                    ) {
                        Text(
                            text = title,
                            color =
                                Color.White,
                            fontSize =
                                14.sp,
                            fontWeight =
                                FontWeight.Black,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                        )

                        subtitle
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                Text(
                                    text = it,
                                    color =
                                        VueoPalette.Muted,
                                    fontSize =
                                        9.sp,
                                    maxLines = 1,
                                    overflow =
                                        TextOverflow.Ellipsis,
                                )
                            }
                    }

                    Box(
                        modifier =
                            Modifier
                                .width(
                                    15.dp
                                )
                                .height(
                                    2.dp
                                )
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    VueoPalette.Accent
                                        .copy(
                                            alpha = .82f
                                        )
                                ),
                    )
                }

                Spacer(
                    Modifier.height(
                        3.dp
                    )
                )

                actions.forEach {
                    action ->

                    val tint =
                        if (
                            action.destructive
                        ) {
                            MaterialTheme
                                .colorScheme
                                .error
                        } else {
                            VueoPalette.Accent
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        12.dp
                                    )
                                )
                                .clickable(
                                    onClick =
                                        action.onClick
                                )
                                .padding(
                                    horizontal =
                                        6.dp,
                                    vertical =
                                        6.dp,
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                9.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier =
                                Modifier.size(
                                    31.dp
                                ),
                            shape =
                                CircleShape,
                            color =
                                tint.copy(
                                    alpha = .12f
                                ),
                        ) {
                            Icon(
                                imageVector =
                                    action.icon,
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.padding(
                                        7.dp
                                    ),
                                tint = tint,
                            )
                        }

                        Text(
                            text =
                                action.label,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            color =
                                if (
                                    action.destructive
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .error
                                } else {
                                    Color.White
                                        .copy(
                                            alpha = .94f
                                        )
                                },
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private const val MENU_HOLD_MS =
    800L

@Composable
private fun HomeContinueWatchingSection(
    entries:
        List<LibraryPlaybackEntry>,
    onPlaybackClick:
        (LibraryPlaybackEntry) -> Unit,
    onHold:
        (LibraryPlaybackEntry, IntOffset) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        HomeSectionHeader(
            title =
                "Continue Watching",
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            items(
                items = entries,
                key = {
                    it.mediaKey
                },
            ) {
                entry ->
                HomeContinueWatchingCard(
                    entry = entry,
                    onClick = {
                        onPlaybackClick(
                            entry
                        )
                    },
                    onHold = {
                        anchor ->
                        onHold(
                            entry,
                            anchor,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeContinueWatchingCard(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
    onHold: (IntOffset) -> Unit,
) {
    val remainingLabel =
        homeRemainingTimeLabel(
            entry
        )

    Surface(
        modifier =
            Modifier
                .width(232.dp)
                .aspectRatio(
                    16f / 9f
                )
                .clickOrHoldForMenu(
                    onClick = onClick,
                    onHold = onHold,
                ),
        shape =
            RoundedCornerShape(
                15.dp
            ),
        color =
            VueoPalette.Surface,
    ) {
        Box {
            NetworkImage(
                url =
                    entry.media.background
                        ?: entry.media.poster,
                contentDescription =
                    entry.media.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    entry.media.name,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black
                                            .copy(
                                                alpha = .04f
                                            ),
                                        Color.Black
                                            .copy(
                                                alpha = .18f
                                            ),
                                        Color.Black
                                            .copy(
                                                alpha = .84f
                                            ),
                                    )
                            )
                        ),
            )

            remainingLabel
                ?.let {
                    remaining ->
                    Surface(
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopEnd
                                )
                                .padding(
                                    8.dp
                                ),
                        shape =
                            RoundedCornerShape(
                                10.dp
                            ),
                        color =
                            Color.Black
                                .copy(
                                    alpha = .72f
                                ),
                    ) {
                        Text(
                            text =
                                remaining,
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        5.dp,
                                ),
                            color =
                                Color.White,
                            fontSize =
                                9.sp,
                            fontWeight =
                                FontWeight
                                    .SemiBold,
                        )
                    }
                }

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomStart
                        )
                        .fillMaxWidth()
                        .padding(
                            start = 10.dp,
                            end = 10.dp,
                            bottom = 8.dp,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    ),
            ) {
                homeEpisodeLabel(
                    entry
                )
                    ?.let {
                        episode ->
                        Text(
                            text =
                                episode,
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            .88f
                                    ),
                            fontSize =
                                9.sp,
                            fontWeight =
                                FontWeight
                                    .Medium,
                            maxLines = 1,
                        )
                    }

                Text(
                    text =
                        entry.media.name,
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                )

                entry.episodeTitle
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        episodeTitle ->
                        Text(
                            text =
                                episodeTitle,
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            .68f
                                    ),
                            fontSize =
                                9.sp,
                            maxLines = 1,
                            overflow =
                                TextOverflow
                                    .Ellipsis,
                        )
                    }

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
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(
                                RoundedCornerShape(
                                    50
                                )
                            ),
                    color =
                        VueoPalette.Accent,
                    trackColor =
                        Color.White
                            .copy(
                                alpha = .24f
                            ),
                )
            }
        }
    }
}

private fun homeEpisodeLabel(
    entry: LibraryPlaybackEntry,
): String? =
    if (
        entry.season != null &&
        entry.episode != null
    ) {
        "S${entry.season} E${entry.episode}"
    } else {
        null
    }

private fun homeRemainingTimeLabel(
    entry: LibraryPlaybackEntry,
): String? {
    if (
        entry.durationMs <= 0L
    ) {
        return null
    }

    val remainingMs =
        (
            entry.durationMs -
                entry.positionMs
        )
            .coerceAtLeast(0L)

    val totalMinutes =
        remainingMs /
            (
                60L *
                    1000L
            )

    return if (
        totalMinutes >= 60L
    ) {
        val hours =
            totalMinutes / 60L

        val minutes =
            totalMinutes % 60L

        "${hours}h ${minutes}m left"
    } else {
        "${totalMinutes.coerceAtLeast(1L)}m left"
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    contextLabel: String? = null,
    subtitle: String? = null,
    onTrailingClick:
        (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier.weight(1f),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            contextLabel
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    label ->
                    Text(
                        text =
                            " · $label",
                        color =
                            VueoPalette.Muted,
                        fontWeight =
                            FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
        }

        subtitle
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                trailing ->
                Text(
                    text =
                        trailing,
                    color =
                        if (
                            trailing ==
                                "See All"
                        ) {
                            VueoPalette
                                .Accent
                        } else {
                            VueoPalette
                                .Muted
                        },
                    fontSize =
                        if (
                            trailing ==
                                "See All"
                        ) {
                            12.sp
                        } else {
                            10.sp
                        },
                    fontWeight =
                        if (
                            trailing ==
                                "See All"
                        ) {
                            FontWeight
                                .SemiBold
                        } else {
                            FontWeight
                                .Normal
                        },
                    maxLines = 1,
                    modifier =
                        if (
                            onTrailingClick !=
                                null
                        ) {
                            Modifier
                                .clip(
                                    RoundedCornerShape(
                                        50
                                    )
                                )
                                .clickable(
                                    onClick =
                                        onTrailingClick
                                )
                                .padding(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        6.dp,
                                )
                        } else {
                            Modifier
                        },
                )
            }
    }
}

@Composable
private fun HomeLoadingState() {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
    ) {
        HomeSectionHeader(
            title =
                "Loading VUEO",
            subtitle =
                "Refreshing catalogs",
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            items(
                listOf(
                    1,
                    2,
                    3,
                )
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(122.dp)
                            .aspectRatio(
                                2f / 3f
                            ),
                    shape =
                        RoundedCornerShape(
                            15.dp
                        ),
                    color =
                        VueoPalette
                            .SurfaceElevated,
                ) {}
            }
        }

        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            16.dp
                    ),
            color =
                VueoPalette.Accent,
            trackColor =
                VueoPalette
                    .SurfaceStrong,
        )
    }
}

@Composable
private fun EmptyHomeCard(
    hasAddons: Boolean,
    error: String?,
    onOpenContentManager:
        () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp
                ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        VueoPalette
                            .SurfaceElevated
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    20.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            Text(
                text =
                    "CONTENT",
                color =
                    VueoPalette.Accent,
                fontWeight =
                    FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing =
                    1.4.sp,
            )

            Text(
                text =
                    if (
                        hasAddons
                    ) {
                        "No catalog loaded"
                    } else {
                        "Connect your first content source"
                    },
                color =
                    Color.White,
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text =
                    when {
                        error != null ->
                            "VUEO could not load a catalog right now. Open Content Manager to review your addon."

                        hasAddons ->
                            "The installed addon does not expose a catalog that can load without extra filters."

                        else ->
                            "Install a Stremio addon in Content Manager. Available catalogs will appear here automatically."
                    },
                color =
                    VueoPalette.Muted,
            )

            Spacer(
                Modifier.height(
                    4.dp
                )
            )

            Button(
                onClick =
                    onOpenContentManager,
            ) {
                Text(
                    text =
                        "Open Content Manager"
                )
            }
        }
    }
}

private fun orderHomeCatalogRows(
    rows: List<CatalogRow>,
    catalogOrder: List<String>,
): List<CatalogRow> {
    if (catalogOrder.isEmpty()) {
        return rows
    }

    val index =
        catalogOrder
            .withIndex()
            .associate {
                it.value to it.index
            }

    return rows.sortedBy {
        index[it.id] ?: Int.MAX_VALUE
    }
}

private fun homeCatalogTypeLabel(
    row: CatalogRow,
): String? {
    val type =
        row.items
            .asSequence()
            .map {
                it.type
                    .trim()
                    .lowercase()
            }
            .firstOrNull {
                it.isNotBlank()
            }
            ?: return null

    return when (type) {
        "movie",
        "movies" ->
            "Movies"

        "series",
        "tv",
        "show",
        "shows" ->
            "Series"

        else ->
            type.replaceFirstChar {
                it.uppercase()
            }
    }
}

@Composable
private fun HomePersonalizedSection(
    title: String,
    contextLabel: String,
    items: List<MediaItem>,
    onMediaClick:
        (MediaItem) -> Unit,
    onMediaHold:
        (MediaItem, IntOffset) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        HomeSectionHeader(
            title = title,
            contextLabel =
                contextLabel,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal =
                        16.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            items(
                items = items,
                key = {
                    "personalized:${it.type}:${it.id}"
                },
            ) {
                item ->
                MediaPoster(
                    item = item,
                    onClick = {
                        onMediaClick(
                            item
                        )
                    },
                    onHold = {
                        anchor ->
                        onMediaHold(
                            item,
                            anchor,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogSection(
    row: CatalogRow,
    onMediaClick:
        (MediaItem) -> Unit,
    onMediaHold:
        (MediaItem, IntOffset) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        HomeSectionHeader(
            title = row.title,
            contextLabel =
                homeCatalogTypeLabel(
                    row
                ),
            subtitle = "See All",
            onTrailingClick =
                onSeeAll,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            items(
                items = row.items,
                key = {
                    "${row.id}:${it.id}"
                },
            ) {
                item ->
                MediaPoster(
                    item = item,
                    onClick = {
                        onMediaClick(
                            item
                        )
                    },
                    onHold = {
                        anchor ->
                        onMediaHold(
                            item,
                            anchor,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogSeeAllScreen(
    row: CatalogRow,
    libraryStore: LibraryStore,
    libraryVersion: Int,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
    onMediaClick:
        (MediaItem) -> Unit,
) {
    var catalogAction by remember(
        row.id,
        libraryVersion,
    ) {
        mutableStateOf<MediaItem?>(
            null
        )
    }

    var catalogActionAnchor by remember(
        row.id,
        libraryVersion,
    ) {
        mutableStateOf(
            IntOffset.Zero
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                )
                .statusBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 8.dp,
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    imageVector =
                        Icons.Default
                            .ArrowBack,
                    contentDescription =
                        "Back",
                    tint =
                        Color.White,
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f),
            ) {
                Text(
                    text =
                        row.title,
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                )

                Text(
                    text =
                        "${row.items.size} titles",
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }
        }

        LazyVerticalGrid(
            columns =
                GridCells.Adaptive(
                    minSize = 112.dp
                ),
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp
                ),
        ) {
            gridItems(
                items = row.items,
                key = {
                    "${row.id}:${it.id}"
                },
            ) {
                item ->
                CatalogGridPoster(
                    item = item,
                    onClick = {
                        onMediaClick(
                            item
                        )
                    },
                    onHold = {
                        anchor ->
                        catalogAction = item
                        catalogActionAnchor = anchor
                    },
                )
            }
        }
    }

    catalogAction?.let {
        item ->
        CatalogActionsDialog(
            item = item,
            anchor =
                catalogActionAnchor,
            isWatchlisted =
                libraryStore
                    .isWatchlisted(
                        item
                    ),
            isMarkedWatched =
                libraryStore
                    .isMarkedWatched(
                        item
                    ),
            onDismiss = {
                catalogAction = null
            },
            onViewDetails = {
                catalogAction = null
                onMediaClick(item)
            },
            onToggleWatchlist = {
                libraryStore
                    .toggleWatchlist(
                        item
                    )
                catalogAction = null
                onLibraryChanged()
            },
            onToggleWatched = {
                libraryStore
                    .setMarkedWatched(
                        media = item,
                        watched =
                            !libraryStore
                                .isMarkedWatched(
                                    item
                                ),
                    )
                catalogAction = null
                onLibraryChanged()
            },
        )
    }

}

@Composable
private fun CatalogGridPoster(
    item: MediaItem,
    onClick: () -> Unit,
    onHold: (IntOffset) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickOrHoldForMenu(
                    onClick = onClick,
                    onHold = onHold,
                ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    item.name,
            )
        }

        Spacer(
            Modifier.height(
                7.dp
            )
        )

        Text(
            text = item.name,
            color =
                Color.White,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            fontWeight =
                FontWeight.SemiBold,
            fontSize = 12.sp,
        )

        Spacer(
            Modifier.height(
                3.dp
            )
        )

        Text(
            text =
                listOfNotNull(
                    item.releaseInfo,
                    item.type
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.replaceFirstChar {
                            it.uppercase()
                        },
                )
                    .joinToString(
                        " • "
                    ),
            color =
                VueoPalette.Muted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaPoster(
    item: MediaItem,
    onClick: () -> Unit,
    onHold: ((IntOffset) -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .width(122.dp)
                .let {
                    modifier ->
                    if (onHold != null) {
                        modifier
                            .clickOrHoldForMenu(
                                onClick = onClick,
                                onHold = onHold,
                            )
                    } else {
                        modifier.clickable(
                            onClick = onClick
                        )
                    }
                },
    ) {
        Surface(
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
                        .fillMaxWidth()
                        .height(180.dp),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    item.name,
            )
        }

        Spacer(
            Modifier.height(
                7.dp
            )
        )

        Text(
            text = item.name,
            color =
                Color.White,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            fontWeight =
                FontWeight.SemiBold,
            fontSize = 12.sp,
        )

        Spacer(
            Modifier.height(
                3.dp
            )
        )

        Text(
            text =
                listOfNotNull(
                    item.releaseInfo,
                    item.type
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.replaceFirstChar {
                            it.uppercase()
                        },
                )
                    .joinToString(
                        " • "
                    ),
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            color =
                VueoPalette.Muted,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun SearchScreen(
    engine: UnifiedMediaEngine,
    contentVersion: Int,
    booting: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: SearchTypeFilter,
    onTypeFilterChange:
        (SearchTypeFilter) -> Unit,
    sortMode: SearchSortMode,
    onSortModeChange:
        (SearchSortMode) -> Unit,
    genre: String?,
    onGenreChange: (String?) -> Unit,
    listState:
        androidx.compose.foundation.lazy.LazyListState,
    onMediaClick: (MediaItem) -> Unit,
) {
    val context =
        LocalContext.current

    val searchPluginStore =
        remember(context) {
            PluginStore(
                context.applicationContext
            )
        }

    var searchMode by remember {
        mutableStateOf(
            SearchMode.TITLE
        )
    }

    var actorSourceAvailable by remember {
        mutableStateOf(true)
    }

    var searchResults by remember {
        mutableStateOf<List<MediaItem>>(
            emptyList()
        )
    }

    var discoverRows by remember {
        mutableStateOf(
            CatalogDiscoveryCache
                .home(
                    allowStale = true
                )
                .orEmpty()
        )
    }

    var searching by remember {
        mutableStateOf(false)
    }

    var searchRequestId by remember {
        mutableStateOf(0L)
    }

    var discovering by remember {
        mutableStateOf(
            discoverRows.isEmpty()
        )
    }

    var typeDialog by remember {
        mutableStateOf(false)
    }
    var sortDialog by remember {
        mutableStateOf(false)
    }
    var genreDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        contentVersion,
        booting,
    ) {
        CatalogDiscoveryCache
            .home(
                allowStale = true
            )
            ?.takeIf {
                it.isNotEmpty()
            }
            ?.let {
                discoverRows = it
            }

        if (
            booting ||
            discoverRows.isNotEmpty()
        ) {
            discovering = false
            return@LaunchedEffect
        }

        discovering = true

        runCatching {
            engine.loadCatalogRows(
                forceRefresh = false,
            )
        }.onSuccess {
            fresh ->
            if (fresh.isNotEmpty()) {
                discoverRows = fresh

                CatalogDiscoveryCache
                    .persistHome(
                        context =
                            context
                                .applicationContext,
                        rows = fresh,
                    )
            }
        }.onFailure {
            discoverRows =
                CatalogDiscoveryCache
                    .home(
                        allowStale = true
                    )
                    .orEmpty()
        }

        discovering = false
    }

    LaunchedEffect(
        query,
        contentVersion,
        booting,
        searchMode,
    ) {
        val normalized =
            query.trim()

        searchRequestId += 1L
        val requestId =
            searchRequestId
        val requestedMode =
            searchMode

        if (
            booting ||
            normalized.length < 2
        ) {
            searching = false
            actorSourceAvailable = true
            searchResults =
                if (
                    requestedMode ==
                        SearchMode.TITLE &&
                    normalized.length >= 2
                ) {
                    searchRankAndDedupe(
                        items =
                            CatalogDiscoveryCache
                                .searchLocal(
                                    normalized
                                ),
                        query = normalized,
                    )
                } else {
                    emptyList()
                }
            return@LaunchedEffect
        }

        if (
            requestedMode ==
            SearchMode.TITLE
        ) {
            actorSourceAvailable = true

            val local =
                searchRankAndDedupe(
                    items =
                        CatalogDiscoveryCache
                            .searchLocal(
                                normalized
                            ),
                    query = normalized,
                )

            searchResults = local
            searching = true
            delay(250)

            if (
                requestId != searchRequestId ||
                query.trim() != normalized ||
                searchMode != requestedMode
            ) {
                return@LaunchedEffect
            }

            val remote =
                try {
                    engine.search(
                        query = normalized,
                        onPartial = { partial ->
                            if (
                                requestId ==
                                    searchRequestId &&
                                query.trim() ==
                                    normalized &&
                                searchMode ==
                                    requestedMode
                            ) {
                                searchResults =
                                    searchRankAndDedupe(
                                        items =
                                            partial +
                                                local,
                                        query =
                                            normalized,
                                    )
                            }
                        },
                    )
                } catch (
                    cancelled:
                        CancellationException
                ) {
                    throw cancelled
                } catch (
                    _: Throwable
                ) {
                    emptyList()
                }

            if (
                requestId == searchRequestId &&
                query.trim() == normalized &&
                searchMode == requestedMode
            ) {
                searchResults =
                    searchRankAndDedupe(
                        items =
                            remote + local,
                        query = normalized,
                    )

                searching = false
            }

            return@LaunchedEffect
        }

        val tmdbApiKey =
            searchPluginStore
                .tmdbApiKey()
        val addonActorSearch =
            engine.hasActorSearchAddons()

        actorSourceAvailable =
            addonActorSearch ||
                tmdbApiKey.isNotBlank()

        searchResults = emptyList()

        if (!actorSourceAvailable) {
            searching = false
            return@LaunchedEffect
        }

        searching = true
        delay(250)

        if (
            requestId != searchRequestId ||
            query.trim() != normalized ||
            searchMode != requestedMode
        ) {
            return@LaunchedEffect
        }

        coroutineScope {
            var providerItems =
                emptyList<MediaItem>()
            var tmdbItems =
                emptyList<MediaItem>()

            fun publishActorResults() {
                if (
                    requestId == searchRequestId &&
                    query.trim() == normalized &&
                    searchMode == requestedMode
                ) {
                    searchResults =
                        engine.mergeActorResults(
                            items =
                                providerItems +
                                    tmdbItems,
                        )
                }
            }

            launch {
                providerItems =
                    if (!addonActorSearch) {
                        emptyList()
                    } else {
                        try {
                            engine.searchActor(
                                query = normalized,
                                onPartial = { partial ->
                                    providerItems = partial
                                    publishActorResults()
                                },
                            )
                        } catch (
                            cancelled:
                                CancellationException
                        ) {
                            throw cancelled
                        } catch (
                            _: Throwable
                        ) {
                            emptyList()
                        }
                    }

                publishActorResults()
            }

            launch {
                tmdbItems =
                    if (tmdbApiKey.isBlank()) {
                        emptyList()
                    } else {
                        try {
                            TmdbEnhancementClient
                                .actorFilmography(
                                    query = normalized,
                                    apiKey = tmdbApiKey,
                                )
                                .orEmpty()
                        } catch (
                            cancelled:
                                CancellationException
                        ) {
                            throw cancelled
                        } catch (
                            _: Throwable
                        ) {
                            emptyList()
                        }
                    }

                publishActorResults()
            }
        }

        if (
            requestId == searchRequestId &&
            query.trim() == normalized &&
            searchMode == requestedMode
        ) {
            searching = false
        }
    }

    val normalizedQuery =
        query.trim()

    val searchingMode =
        normalizedQuery.isNotBlank()

    val animeCatalogKeys =
        remember(discoverRows) {
            discoverRows
                .filter {
                    row ->
                    listOf(
                        row.id,
                        row.title,
                        row.providerName,
                    ).any {
                        value ->
                        value.contains(
                            "anime",
                            ignoreCase = true,
                        )
                    }
                }
                .flatMap {
                    it.items
                }
                .map {
                    "${it.type}:${it.id}"
                }
                .toSet()
        }

    val discoverBaseItems =
        remember(
            discoverRows,
            sortMode,
        ) {
            discoverRows
                .sortedByDescending {
                    row ->
                    searchCatalogPriority(
                        row = row,
                        mode = sortMode,
                    )
                }
                .flatMap {
                    it.items
                }
                .distinctBy {
                    "${it.type}:${it.id}"
                }
        }

    val sourceItems =
        if (searchingMode) {
            searchResults
        } else {
            discoverBaseItems
        }

    val availableGenres =
        remember(
            sourceItems,
            typeFilter,
            animeCatalogKeys,
        ) {
            sourceItems
                .filter {
                    item ->
                    searchMatchesType(
                        item = item,
                        filter =
                            typeFilter,
                        animeCatalogKeys =
                            animeCatalogKeys,
                    )
                }
                .flatMap {
                    it.genres
                }
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank() &&
                        !it.equals(
                            "anime",
                            ignoreCase = true,
                        )
                }
                .distinctBy {
                    it.lowercase()
                }
                .sortedBy {
                    it.lowercase()
                }
        }

    LaunchedEffect(
        availableGenres,
        genre,
    ) {
        if (
            genre != null &&
            availableGenres.none {
                it.equals(
                    genre,
                    ignoreCase = true,
                )
            }
        ) {
            onGenreChange(null)
        }
    }

    val filteredItems =
        remember(
            sourceItems,
            typeFilter,
            genre,
            sortMode,
            searchingMode,
            searchMode,
            animeCatalogKeys,
        ) {
            val filtered =
                sourceItems
                    .filter {
                        item ->
                        searchMatchesType(
                            item = item,
                            filter =
                                typeFilter,
                            animeCatalogKeys =
                                animeCatalogKeys,
                        ) &&
                            searchMatchesGenre(
                                item = item,
                                genre = genre,
                            )
                    }

            if (
                searchingMode &&
                searchMode == SearchMode.ACTOR
            ) {
                searchSortActorItems(
                    items = filtered,
                    mode = sortMode,
                )
            } else if (searchingMode) {
                searchSortItems(
                    items = filtered,
                    mode = sortMode,
                    query = normalizedQuery,
                )
            } else {
                if (
                    sortMode ==
                    SearchSortMode.NEWEST
                ) {
                    searchSortItems(
                        items = filtered,
                        mode = sortMode,
                    )
                } else {
                    filtered
                }
            }
        }

    if (typeDialog) {
        SearchChoiceDialog(
            title = "Type",
            options =
                SearchTypeFilter
                    .values()
                    .map {
                        it.label
                    },
            selected =
                typeFilter.label,
            onDismiss = {
                typeDialog = false
            },
            onSelected = {
                label ->
                SearchTypeFilter
                    .values()
                    .firstOrNull {
                        it.label == label
                    }
                    ?.let(
                        onTypeFilterChange
                    )
                typeDialog = false
            },
        )
    }

    if (sortDialog) {
        SearchChoiceDialog(
            title = "Discover",
            options =
                SearchSortMode
                    .values()
                    .map {
                        it.label
                    },
            selected =
                sortMode.label,
            onDismiss = {
                sortDialog = false
            },
            onSelected = {
                label ->
                SearchSortMode
                    .values()
                    .firstOrNull {
                        it.label == label
                    }
                    ?.let(
                        onSortModeChange
                    )
                sortDialog = false
            },
        )
    }

    if (genreDialog) {
        SearchChoiceDialog(
            title = "Genre",
            options =
                listOf(
                    "All Genres"
                ) + availableGenres,
            selected =
                genre ?: "All Genres",
            onDismiss = {
                genreDialog = false
            },
            onSelected = {
                label ->
                onGenreChange(
                    label.takeUnless {
                        it == "All Genres"
                    }
                )
                genreDialog = false
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
    ) {
        item(
            key = "search_header"
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 24.dp,
                        bottom = 2.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        20.dp
                    ),
            ) {
                Text(
                    text = "Search",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight =
                        FontWeight.Black,
                )

                OutlinedTextField(
                    value = query,
                    onValueChange =
                        onQueryChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 64.dp
                            ),
                    placeholder = {
                        Text(
                            text =
                                if (
                                    searchMode ==
                                    SearchMode.ACTOR
                                ) {
                                    "Search actor name..."
                                } else {
                                    "Search movies, shows..."
                                },
                            color =
                                VueoPalette.Muted,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                Icons.Default.Search,
                            contentDescription =
                                null,
                            tint =
                                VueoPalette.Muted,
                        )
                    },
                    trailingIcon = {
                        if (
                            query.isNotEmpty()
                        ) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Close,
                                    contentDescription =
                                        "Clear search",
                                    tint =
                                        VueoPalette.Muted,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                )

                if (
                    searching ||
                    discovering
                ) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                    )
                }
            }
        }

        item(
            key = "search_discover_header"
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (searchingMode) {
                                "Search Results"
                            } else {
                                "Discover"
                            },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                    )

                    Spacer(
                        Modifier.weight(1f)
                    )

                    SearchModeToggle(
                        mode = searchMode,
                        onModeChange = { next ->
                            if (next != searchMode) {
                                searchMode = next
                                searchResults =
                                    emptyList()
                                searching = false
                                onGenreChange(null)
                            }
                        },
                    )
                }

                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    item {
                        SearchFilterButton(
                            label =
                                typeFilter.label,
                            onClick = {
                                typeDialog = true
                            },
                        )
                    }

                    item {
                        SearchFilterButton(
                            label =
                                sortMode.label,
                            onClick = {
                                sortDialog = true
                            },
                        )
                    }

                    item {
                        SearchFilterButton(
                            label =
                                genre
                                    ?: "All Genres",
                            onClick = {
                                genreDialog = true
                            },
                        )
                    }
                }

                if (
                    searchingMode &&
                    normalizedQuery.length < 2
                ) {
                    Text(
                        text =
                            "Type at least 2 characters.",
                        color =
                            VueoPalette.Muted,
                        fontSize = 12.sp,
                    )
                } else if (
                    searchingMode &&
                    !searching
                ) {
                    Text(
                        text =
                            when {
                                searchMode ==
                                    SearchMode.ACTOR &&
                                    !actorSourceAvailable ->
                                    "No enabled metadata source supports actor search."

                                filteredItems
                                    .isEmpty() ->
                                    "No results for \"$normalizedQuery\"."

                                else ->
                                    "${filteredItems.size} results"
                            },
                        color =
                            VueoPalette.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (
            !searchingMode &&
            discoverRows.isEmpty() &&
            !discovering
        ) {
            item(
                key = "search_empty_discover"
            ) {
                SearchEmptyState(
                    title =
                        "Nothing to discover yet",
                    body =
                        "Enable a catalog in Content Manager to populate Discover.",
                )
            }
        } else if (
            searchingMode &&
            normalizedQuery.length >= 2 &&
            filteredItems.isEmpty() &&
            !searching
        ) {
            item(
                key = "search_empty_results"
            ) {
                SearchEmptyState(
                    title =
                        if (
                            searchMode ==
                            SearchMode.ACTOR &&
                            !actorSourceAvailable
                        ) {
                            "Actor search unavailable"
                        } else {
                            "No matches"
                        },
                    body =
                        if (
                            searchMode ==
                            SearchMode.ACTOR &&
                            !actorSourceAvailable
                        ) {
                            "Enable a metadata source that supports actor or cast lookup, or add a TMDB API key."
                        } else if (
                            searchMode ==
                            SearchMode.ACTOR
                        ) {
                            "Try another actor name or change the filters."
                        } else {
                            "Try another title or change the filters."
                        },
                )
            }
        } else if (
            filteredItems.isNotEmpty()
        ) {
            items(
                items =
                    filteredItems
                        .chunked(3),
                key = {
                    row ->
                    row.joinToString(
                        "|"
                    ) {
                        "${it.type}:${it.id}"
                    }
                },
            ) {
                row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    20.dp
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                ) {
                    row.forEach {
                        item ->
                        SearchPosterTile(
                            item = item,
                            catalogLabel =
                                searchCatalogLabel(
                                    engine = engine,
                                    item = item,
                                ),
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick = {
                                onMediaClick(
                                    item
                                )
                            },
                        )
                    }

                    repeat(
                        3 - row.size
                    ) {
                        Spacer(
                            Modifier.weight(
                                1f
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun searchCatalogPriority(
    row: CatalogRow,
    mode: SearchSortMode,
): Int {
    val value =
        "${row.id} ${row.title}"
            .lowercase()

    return when (mode) {
        SearchSortMode.POPULAR ->
            when {
                "popular" in value -> 100
                "top" in value -> 80
                else -> 0
            }

        SearchSortMode.TRENDING ->
            when {
                "trending" in value -> 100
                "trend" in value -> 100
                "popular" in value -> 60
                else -> 0
            }

        SearchSortMode.NEWEST ->
            when {
                "new" in value -> 100
                "latest" in value -> 100
                "recent" in value -> 90
                "release" in value -> 80
                else -> 0
            }
    }
}

private fun searchMatchesType(
    item: MediaItem,
    filter: SearchTypeFilter,
    animeCatalogKeys: Set<String>,
): Boolean =
    when (filter) {
        SearchTypeFilter.ALL ->
            true

        SearchTypeFilter.MOVIES ->
            item.type.equals(
                "movie",
                ignoreCase = true,
            ) &&
                !searchIsAnime(
                    item = item,
                    animeCatalogKeys =
                        animeCatalogKeys,
                )

        SearchTypeFilter.SERIES ->
            item.type.equals(
                "series",
                ignoreCase = true,
            ) &&
                !searchIsAnime(
                    item = item,
                    animeCatalogKeys =
                        animeCatalogKeys,
                )

        SearchTypeFilter.ANIME ->
            searchIsAnime(
                item = item,
                animeCatalogKeys =
                    animeCatalogKeys,
            )
    }

private fun searchIsAnime(
    item: MediaItem,
    animeCatalogKeys: Set<String>,
): Boolean {
    if (
        item.type.equals(
            "anime",
            ignoreCase = true,
        ) ||
        item.genres.any {
            it.equals(
                "anime",
                ignoreCase = true,
            )
        }
    ) {
        return true
    }

    val key =
        "${item.type}:${item.id}"

    if (key in animeCatalogKeys) {
        return true
    }

    return listOfNotNull(
        item.sourceExtensionId,
        item.id,
    ).any {
        it.contains(
            "anime",
            ignoreCase = true,
        )
    }
}

private fun searchMatchesGenre(
    item: MediaItem,
    genre: String?,
): Boolean {
    if (genre == null) {
        return true
    }

    return item.genres.any {
        it.equals(
            genre,
            ignoreCase = true,
        )
    }
}

private fun searchSortActorItems(
    items: List<MediaItem>,
    mode: SearchSortMode,
): List<MediaItem> =
    when (mode) {
        SearchSortMode.POPULAR,
        SearchSortMode.TRENDING ->
            items

        SearchSortMode.NEWEST ->
            items.sortedByDescending {
                searchReleaseYear(it)
            }
    }

private fun searchSortItems(
    items: List<MediaItem>,
    mode: SearchSortMode,
    query: String? = null,
): List<MediaItem> {
    val normalizedQuery =
        query
            ?.let(::searchNormalizeText)
            .orEmpty()

    if (normalizedQuery.isBlank()) {
        return when (mode) {
            SearchSortMode.POPULAR ->
                items.sortedByDescending {
                    it.imdbRating
                        ?: it.tmdbRating
                        ?: 0.0
                }

            SearchSortMode.TRENDING ->
                items

            SearchSortMode.NEWEST ->
                items.sortedByDescending {
                    searchReleaseYear(it)
                }
        }
    }

    val relevance =
        compareByDescending<MediaItem> {
            searchRelevanceScore(
                item = it,
                query = normalizedQuery,
            )
        }

    return when (mode) {
        SearchSortMode.POPULAR ->
            items.sortedWith(
                relevance
                    .thenByDescending {
                        it.imdbRating
                            ?: it.tmdbRating
                            ?: 0.0
                    }
                    .thenByDescending {
                        searchReleaseYear(it)
                    }
            )

        SearchSortMode.TRENDING ->
            items.sortedWith(
                relevance
                    .thenByDescending {
                        searchMetadataScore(it)
                    }
            )

        SearchSortMode.NEWEST ->
            items.sortedWith(
                relevance
                    .thenByDescending {
                        searchReleaseYear(it)
                    }
                    .thenByDescending {
                        it.imdbRating
                            ?: it.tmdbRating
                            ?: 0.0
                    }
            )
    }
}

private fun searchRankAndDedupe(
    items: List<MediaItem>,
    query: String,
): List<MediaItem> {
    val normalizedQuery =
        searchNormalizeText(query)

    if (normalizedQuery.isBlank()) {
        return items
            .distinctBy {
                "${it.type}:${it.id}"
            }
    }

    val groups =
        mutableListOf<
            MutableList<MediaItem>
        >()

    items
        .filter {
            searchIsRelevantEnough(
                item = it,
                query = normalizedQuery,
            )
        }
        .forEach {
            candidate ->

            val candidateTitle =
                searchCanonicalTitle(
                    candidate
                )
            val candidateType =
                searchCanonicalType(
                    candidate.type
                )
            val candidateYear =
                searchReleaseYear(
                    candidate
                )

            val target =
                groups.firstOrNull {
                    group ->
                    val sample =
                        group.first()

                    val sampleYear =
                        searchReleaseYear(
                            sample
                        )

                    candidateTitle ==
                        searchCanonicalTitle(
                            sample
                        ) &&
                        candidateType ==
                            searchCanonicalType(
                                sample.type
                            ) &&
                        (
                            candidateYear == 0 ||
                                sampleYear == 0 ||
                                kotlin.math.abs(
                                    candidateYear -
                                        sampleYear
                                ) <= 1
                        )
                }

            if (target == null) {
                groups +=
                    mutableListOf(
                        candidate
                    )
            } else {
                target += candidate
            }
        }

    return groups
        .mapNotNull {
            duplicates ->

            val best =
                duplicates.maxByOrNull {
                    item ->
                    (
                        searchRelevanceScore(
                            item = item,
                            query =
                                normalizedQuery,
                        ) * 100
                    ) +
                        searchMetadataScore(
                            item
                        )
                }
                    ?: return@mapNotNull null

            best.copy(
                genres =
                    duplicates
                        .flatMap {
                            it.genres
                        }
                        .distinctBy {
                            it.lowercase()
                        },
                catalogSources =
                    (
                        best.catalogSources +
                            duplicates
                                .flatMap {
                                    it.catalogSources
                                }
                    )
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .distinctBy {
                            it.lowercase()
                        },
            )
        }
        .sortedWith(
            compareByDescending<MediaItem> {
                searchRelevanceScore(
                    item = it,
                    query = normalizedQuery,
                )
            }.thenByDescending {
                searchMetadataScore(it)
            }.thenByDescending {
                it.imdbRating
                    ?: it.tmdbRating
                    ?: 0.0
            }
        )
}

private fun searchIsRelevantEnough(
    item: MediaItem,
    query: String,
): Boolean {
    val normalizedQuery =
        searchNormalizeText(query)
    val titleQuery =
        searchTitleQuery(
            normalizedQuery
        )
    val title =
        searchNormalizeText(
            item.name
        )

    if (
        normalizedQuery.isBlank() ||
        titleQuery.isBlank() ||
        title.isBlank()
    ) {
        return false
    }

    val score =
        searchRelevanceScore(
            item = item,
            query = normalizedQuery,
        )

    if (score >= 44_000) {
        return true
    }

    val queryTokens =
        titleQuery
            .split(' ')
            .filter {
                it.length >= 2
            }
    val titleTokens =
        title
            .split(' ')
            .filter {
                it.isNotBlank()
            }

    if (queryTokens.isEmpty()) {
        return score > 0
    }

    val matched =
        queryTokens.count {
            token ->
            titleTokens.any {
                titleToken ->
                titleToken == token ||
                    titleToken
                        .startsWith(
                            token
                        ) ||
                    token.startsWith(
                        titleToken
                    )
            }
        }

    return if (
        queryTokens.size == 1
    ) {
        matched == 1
    } else {
        matched ==
            queryTokens.size
    }
}

private fun searchCanonicalTitle(
    item: MediaItem,
): String {
    var title =
        searchNormalizeText(
            item.name
        )

    title =
        title.replace(
            Regex(
                """\s+(19|20)\d{2}$"""
            ),
            "",
        )

    if (
        searchCanonicalType(
            item.type
        ) == "series"
    ) {
        title =
            title
                .replace(
                    Regex(
                        """\s+season\s+\d+.*$"""
                    ),
                    "",
                )
                .replace(
                    Regex(
                        """\s+(tv\s+)?series\s*$"""
                    ),
                    "",
                )
    }

    return title.trim()
}

private fun searchCanonicalType(
    value: String,
): String =
    when (
        value
            .trim()
            .lowercase()
    ) {
        "tv",
        "show",
        "shows",
        "series" ->
            "series"

        "film",
        "films",
        "movies",
        "movie" ->
            "movie"

        else ->
            value
                .trim()
                .lowercase()
    }

private fun searchRelevanceScore(
    item: MediaItem,
    query: String,
): Int {
    val normalizedQuery =
        searchNormalizeText(query)
    val titleQuery =
        searchTitleQuery(
            normalizedQuery
        )
    val title =
        searchNormalizeText(
            item.name
        )

    if (
        normalizedQuery.isBlank() ||
        titleQuery.isBlank() ||
        title.isBlank()
    ) {
        return 0
    }

    val queryTokens =
        titleQuery
            .split(' ')
            .filter {
                it.isNotBlank()
            }
    val titleTokens =
        title
            .split(' ')
            .filter {
                it.isNotBlank()
            }

    val exactTokenMatches =
        queryTokens.count {
            it in titleTokens
        }
    val prefixTokenMatches =
        queryTokens.count {
            token ->
            titleTokens.any {
                titleToken ->
                titleToken.startsWith(
                    token
                )
            }
        }

    var score =
        when {
            title == titleQuery ->
                120_000

            title.startsWith(
                "$titleQuery "
            ) ->
                96_000

            title.contains(
                " $titleQuery "
            ) ||
                title.endsWith(
                    " $titleQuery"
                ) ->
                86_000

            title.contains(
                titleQuery
            ) ->
                76_000

            queryTokens.isNotEmpty() &&
                exactTokenMatches ==
                    queryTokens.size ->
                62_000

            queryTokens.isNotEmpty() &&
                prefixTokenMatches ==
                    queryTokens.size ->
                52_000

            else ->
                (
                    exactTokenMatches *
                        6_000
                ) +
                    (
                        prefixTokenMatches *
                            3_000
                    )
        }

    if (
        queryTokens.size > 1 &&
        exactTokenMatches <
            queryTokens.size
    ) {
        score -=
            (
                queryTokens.size -
                    exactTokenMatches
            ) * 4_000
    }

    val queryYear =
        Regex(
            """\b(19|20)\d{2}\b"""
        )
            .find(normalizedQuery)
            ?.value
            ?.toIntOrNull()

    if (queryYear != null) {
        score +=
            if (
                searchReleaseYear(item) ==
                queryYear
            ) {
                12_000
            } else {
                -4_000
            }
    }

    return score
}

private fun searchMetadataScore(
    item: MediaItem,
): Int {
    var score = 0

    if (!item.poster.isNullOrBlank()) {
        score += 80
    }
    if (!item.background.isNullOrBlank()) {
        score += 35
    }
    if (!item.description.isNullOrBlank()) {
        score += 30
    }
    if (!item.releaseInfo.isNullOrBlank()) {
        score += 20
    }
    if (item.genres.isNotEmpty()) {
        score += 15
    }
    if (item.catalogSources.isNotEmpty()) {
        score += 12
    }

    score +=
        ((
            item.imdbRating
                ?: item.tmdbRating
                ?: 0.0
        ) * 10.0)
            .toInt()

    return score
}

private fun searchTitleQuery(
    normalizedQuery: String,
): String =
    normalizedQuery
        .replace(
            Regex(
                """\b(19|20)\d{2}\b"""
            ),
            " ",
        )
        .trim()
        .replace(
            Regex(
                """\s+"""
            ),
            " ",
        )

private fun searchNormalizeText(
    value: String,
): String =
    value
        .lowercase()
        .replace(
            Regex(
                """[^a-z0-9]+"""
            ),
            " ",
        )
        .trim()
        .replace(
            Regex(
                """\s+"""
            ),
            " ",
        )

private fun searchReleaseYear(
    item: MediaItem,
): Int =
    item.releaseInfo
        ?.let {
            Regex(
                """\b(19|20)\d{2}\b"""
            )
                .find(it)
                ?.value
                ?.toIntOrNull()
        }
        ?: 0

@Composable
private fun SearchModeToggle(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Title",
            color =
                if (
                    mode == SearchMode.TITLE
                ) {
                    Color.White
                } else {
                    VueoPalette.Muted
                },
            fontSize = 11.sp,
            fontWeight =
                if (
                    mode == SearchMode.TITLE
                ) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
            modifier =
                Modifier.clickable {
                    onModeChange(
                        SearchMode.TITLE
                    )
                },
        )

        Box(
            modifier =
                Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .clip(
                        RoundedCornerShape(
                            50.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha = .92f
                        )
                    )
                    .clickable {
                        onModeChange(
                            if (
                                mode ==
                                SearchMode.TITLE
                            ) {
                                SearchMode.ACTOR
                            } else {
                                SearchMode.TITLE
                            }
                        )
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                horizontalArrangement =
                    if (
                        mode == SearchMode.ACTOR
                    ) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                VueoPalette.Background
                            )
                )
            }
        }

        Text(
            text = "Actor",
            color =
                if (
                    mode == SearchMode.ACTOR
                ) {
                    Color.White
                } else {
                    VueoPalette.Muted
                },
            fontSize = 11.sp,
            fontWeight =
                if (
                    mode == SearchMode.ACTOR
                ) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
            modifier =
                Modifier.clickable {
                    onModeChange(
                        SearchMode.ACTOR
                    )
                },
        )
    }
}

@Composable
private fun SearchFilterButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color =
            VueoPalette.SurfaceElevated,
        border =
            androidx.compose.foundation
                .BorderStroke(
                    width = 1.dp,
                    color =
                        VueoPalette.Stroke
                            .copy(
                                alpha = .45f
                            ),
                ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 11.dp,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Text(
                text = "⌄",
                color =
                    VueoPalette.Muted,
                fontSize = 16.sp,
                fontWeight =
                    FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SearchChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight =
                    FontWeight.Black,
            )
        },
        text = {
            LazyColumn(
                modifier =
                    Modifier.heightIn(
                        max = 420.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                items(
                    options,
                    key = { it },
                ) {
                    option ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(
                                        option
                                    )
                                },
                        shape =
                            RoundedCornerShape(
                                12.dp
                            ),
                        color =
                            if (
                                option ==
                                selected
                            ) {
                                VueoPalette.Accent
                                    .copy(
                                        alpha = .13f
                                    )
                            } else {
                                Color.Transparent
                            },
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        12.dp,
                                    vertical =
                                        11.dp,
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected =
                                    option ==
                                        selected,
                                onClick = {
                                    onSelected(
                                        option
                                    )
                                },
                            )

                            Spacer(
                                Modifier.width(
                                    8.dp
                                )
                            )

                            Text(
                                text = option,
                                color =
                                    Color.White,
                                fontWeight =
                                    if (
                                        option ==
                                        selected
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Done"
                )
            }
        },
        containerColor =
            VueoPalette.SurfaceElevated,
    )
}

@Composable
private fun SearchEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 28.dp,
                    vertical = 36.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            ),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(
            text = body,
            color =
                VueoPalette.Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SearchPosterTile(
    item: MediaItem,
    modifier: Modifier = Modifier,
    catalogLabel: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier.clickable(
                onClick = onClick
            ),
    ) {
        NetworkImage(
            url = item.poster,
            contentDescription =
                item.name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        2f / 3f
                    )
                    .clip(
                        RoundedCornerShape(
                            14.dp
                        )
                    ),
            contentScale =
                ContentScale.Crop,
            fallbackText =
                item.name,
        )

        Spacer(
            Modifier.height(
                7.dp
            )
        )

        Text(
            text = item.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight =
                FontWeight.SemiBold,
            maxLines = 2,
            overflow =
                TextOverflow.Ellipsis,
        )

        Spacer(
            Modifier.height(
                3.dp
            )
        )

        Text(
            text =
                if (
                    catalogLabel
                        .isNullOrBlank()
                ) {
                    listOfNotNull(
                        item.releaseInfo,
                        searchTypeLabel(
                            item
                        ),
                    ).joinToString(
                        " • "
                    )
                } else {
                    listOf(
                        searchTypeLabel(
                            item
                        ),
                        catalogLabel,
                    ).joinToString(
                        " • "
                    )
                },
            color =
                VueoPalette.Muted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )
    }
}

private fun searchCatalogLabel(
    engine: UnifiedMediaEngine,
    item: MediaItem,
): String? {
    val direct =
        item.catalogSources
            .firstOrNull {
                it.isNotBlank()
            }

    val resolved =
        direct
            ?: engine
                .extension(
                    item.sourceExtensionId
                )
                ?.descriptor
                ?.name
            ?: item.sourceExtensionId
                ?.substringAfterLast(
                    '.'
                )
                ?.substringAfterLast(
                    ':'
                )

    return resolved
        ?.let(
            ::searchPrettyCatalogName
        )
}

private fun searchPrettyCatalogName(
    value: String,
): String? {
    val cleaned =
        value
            .trim()
            .replace(
                Regex(
                    """(?i)\s+(stremio\s+)?addon$"""
                ),
                "",
            )
            .replace(
                Regex(
                    """\s+"""
                ),
                " ",
            )
            .takeIf {
                it.isNotBlank()
            }
            ?: return null

    val lower =
        cleaned.lowercase()

    return when {
        "cinemeta" in lower ->
            "Cinemeta"

        "mediafusion" in lower ||
            "media fusion" in lower ->
            "MediaFusion"

        Regex(
            """\btmdb\b"""
        ).containsMatchIn(
            lower
        ) ||
            "the movie database" in
                lower ->
            "TMDB"

        Regex(
            """\bimdb\b"""
        ).containsMatchIn(
            lower
        ) ->
            "IMDb"

        Regex(
            """\btrakt\b"""
        ).containsMatchIn(
            lower
        ) ->
            "Trakt"

        else ->
            cleaned.replaceFirstChar {
                ch ->
                if (
                    ch.isLowerCase()
                ) {
                    ch.titlecase()
                } else {
                    ch.toString()
                }
            }
    }
}

private fun searchTypeLabel(
    item: MediaItem,
): String =
    when (
        item.type.lowercase()
    ) {
        "movie" -> "Movie"
        "series", "tv" ->
            "Series"
        "anime" -> "Anime"
        else ->
            item.type
                .replaceFirstChar {
                    it.uppercase()
                }
    }

@Composable
private fun LibraryScreen(
    store: LibraryStore,
    activeProfile: VueoProfile,
    version: Int,
    onVersionChanged: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlaybackClick:
        (LibraryPlaybackEntry) -> Unit,
) {
    val watchlist = remember(
        version
    ) {
        store.watchlist()
    }

    val context = LocalContext.current

    var cloudSelected by remember {
        mutableStateOf(false)
    }

    val libraryUiPreferences =
        remember {
            context.getSharedPreferences(
                "vueo_library_ui",
                Context.MODE_PRIVATE,
            )
        }

    var gridView by remember {
        mutableStateOf(
            libraryUiPreferences.getBoolean(
                "grid_view",
                true,
            )
        )
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 34.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                18.dp
            ),
    ) {
        item(
            key = "library-header"
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        20.dp
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Library",
                        modifier =
                            Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight =
                            FontWeight.Black,
                    )

                    LibraryViewModeButton(
                        gridView = gridView,
                        onToggle = {
                            val nextGridView =
                                !gridView

                            gridView =
                                nextGridView

                            libraryUiPreferences
                                .edit()
                                .putBoolean(
                                    "grid_view",
                                    nextGridView,
                                )
                                .apply()
                        },
                    )
                }

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    LibraryTab(
                        label = "My List",
                        selected =
                            !cloudSelected,
                        onClick = {
                            cloudSelected =
                                false
                        },
                    )

                    LibraryTab(
                        label = "Cloud",
                        selected =
                            cloudSelected,
                        onClick = {
                            cloudSelected =
                                true
                        },
                    )
                }
            }
        }

        if (cloudSelected) {
            item(
                key = "library-cloud-empty"
            ) {
                LibraryEmptyCard(
                    title =
                        "Cloud library",
                    body =
                        "Cloud sync is not connected yet. Your locally saved titles stay available in My List.",
                )
            }
        } else if (
            watchlist.isEmpty()
        ) {
            item(
                key = "library-saved-empty"
            ) {
                LibraryEmptyCard(
                    title =
                        "Your library is empty",
                    body =
                        "Titles added to My List will appear here.",
                )
            }
        } else if (gridView) {
            watchlist
                .chunked(3)
                .forEachIndexed {
                    rowIndex,
                    rowItems ->

                    item(
                        key =
                            "library-grid-row-$rowIndex"
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                            verticalAlignment =
                                Alignment.Top,
                        ) {
                            rowItems.forEach {
                                media ->

                                SearchPosterTile(
                                    item = media,
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        ),
                                    onClick = {
                                        onMediaClick(
                                            media
                                        )
                                    },
                                )
                            }

                            repeat(
                                3 -
                                    rowItems.size
                            ) {
                                Spacer(
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }
                        }
                    }
                }
        } else {
            items(
                items = watchlist,
                key = {
                    media ->
                    "library-list:" +
                        media.type +
                        ":" +
                        media.id
                },
            ) {
                media ->

                LibrarySavedListRow(
                    media = media,
                    onClick = {
                        onMediaClick(
                            media
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                50
            ),
        color =
            if (selected) {
                Color.White.copy(
                    alpha = .14f
                )
            } else {
                VueoPalette
                    .SurfaceElevated
                    .copy(
                        alpha = .58f
                    )
            },
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 10.dp,
                ),
            color =
                if (selected) {
                    Color.White
                } else {
                    VueoPalette.Muted
                },
            fontSize = 14.sp,
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
        )
    }
}

@Composable
private fun LibraryViewModeButton(
    gridView: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .size(42.dp)
                .clickable(
                    onClick = onToggle
                ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color = Color.Transparent,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center,
        ) {
            if (gridView) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            4.dp
                        ),
                ) {
                    repeat(2) {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    4.dp
                                ),
                        ) {
                            repeat(2) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(
                                                8.dp
                                            )
                                            .clip(
                                                RoundedCornerShape(
                                                    2.dp
                                                )
                                            )
                                            .background(
                                                VueoPalette
                                                    .Muted
                                            )
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            5.dp
                        ),
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .width(
                                        22.dp
                                    )
                                    .height(
                                        4.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            2.dp
                                        )
                                    )
                                    .background(
                                        VueoPalette
                                            .Muted
                                    )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyCard(
    title: String,
    body: String,
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                26.dp
            ),
        color =
            VueoPalette
                .SurfaceElevated
                .copy(
                    alpha = .78f
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 22.dp,
                    vertical = 24.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text = body,
                color =
                    VueoPalette.Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun LibrarySavedListRow(
    media: MediaItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                )
                .padding(
                    vertical = 2.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        NetworkImage(
            url = media.poster,
            contentDescription =
                media.name,
            modifier =
                Modifier
                    .width(
                        62.dp
                    )
                    .height(
                        88.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            10.dp
                        )
                    ),
            contentScale =
                ContentScale.Crop,
            fallbackText =
                media.name,
        )

        Spacer(
            Modifier.width(
                14.dp
            )
        )

        Column(
            modifier =
                Modifier.weight(
                    1f
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    5.dp
                ),
        ) {
            Text(
                text = media.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Text(
                text =
                    listOfNotNull(
                        media.releaseInfo,
                        searchTypeLabel(
                            media
                        ),
                    ).joinToString(
                        " • "
                    ),
                color =
                    VueoPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                "$count items",
                color =
                    VueoPalette.Muted,
                fontSize = 10.sp,
            )
        }

        if (
            actionLabel != null &&
            onAction != null
        ) {
            TextButton(
                onClick = onAction,
            ) {
                Text(
                    actionLabel,
                    color =
                        VueoPalette.Accent,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LibraryMetricPill(
    label: String,
    count: Int,
) {
    Surface(
        shape =
            RoundedCornerShape(
                50
            ),
        color =
            VueoPalette
                .SurfaceElevated,
    ) {
        Text(
            "$count $label",
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 6.dp,
                ),
            color =
                if (
                    count > 0
                ) {
                    Color.White
                } else {
                    VueoPalette.Muted
                },
            fontSize = 10.sp,
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(250.dp)
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp),
            ) {
                NetworkImage(
                    url =
                        entry.media
                            .background
                            ?: entry.media
                                .poster,
                    contentDescription =
                        entry.media.name,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop,
                    fallbackText =
                        entry.media.name,
                )

                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                Color.Black
                                    .copy(
                                        alpha = .24f
                                    )
                            ),
                )

                FilledIconButton(
                    onClick = onClick,
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                ) {
                    Icon(
                        Icons.Default
                            .PlayArrow,
                        contentDescription =
                            "Continue",
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier =
                        Modifier.align(
                            Alignment.TopEnd
                        ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Remove",
                        tint = Color.White,
                    )
                }
            }

            LinearProgressIndicator(
                progress = {
                    entry.progressFraction
                },
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Column(
                modifier =
                    Modifier.padding(
                        12.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                Text(
                    entry.media.name,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    playbackEntrySubtitle(
                        entry
                    ),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .58f),
                    fontSize = 11.sp,
                )

                Text(
                    "${formatPlaybackTime(entry.positionMs)} of " +
                        if (
                            entry.durationMs >
                                0L
                        ) {
                            formatPlaybackTime(
                                entry.durationMs
                            )
                        } else {
                            "unknown"
                        },
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    media: MediaItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier.width(
                132.dp
            ),
    ) {
        MediaPoster(
            item = media,
            onClick = onClick,
        )

        FilledIconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .padding(4.dp)
                    .size(30.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription =
                    "Remove from My List",
                modifier =
                    Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            )
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            NetworkImage(
                url =
                    entry.media.poster
                        ?: entry.media
                            .background,
                contentDescription =
                    entry.media.name,
                modifier = Modifier
                    .width(66.dp)
                    .height(92.dp)
                    .clip(
                        RoundedCornerShape(
                            9.dp
                        )
                    ),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    entry.media.name,
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                Text(
                    entry.media.name,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    playbackEntrySubtitle(
                        entry
                    ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .58f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    when {
                        entry.isCompleted ->
                            "Completed"

                        entry.positionMs >
                            5_000L ->
                            "Stopped at " +
                                formatPlaybackTime(
                                    entry.positionMs
                                )

                        else ->
                            "Opened"
                    },
                    color =
                        if (
                            entry.isCompleted
                        ) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(
                                    alpha = .5f
                                )
                        },
                    fontSize = 11.sp,
                )
            }

            IconButton(
                onClick = onRemove,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription =
                        "Remove from History",
                )
            }
        }
    }
}

private fun playbackEntrySubtitle(
    entry: LibraryPlaybackEntry,
): String =
    when {
        entry.season != null &&
            entry.episode != null ->
            "S${entry.season} E${entry.episode}" +
                entry.episodeTitle
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        " • $it"
                    }
                    .orEmpty()

        else ->
            entry.media.type
                .replaceFirstChar {
                    it.uppercase()
                }
    }


@Composable
private fun ContentManagerScreen(
    engine: UnifiedMediaEngine,
    onBack: () -> Unit,
    onAddons: () -> Unit,
    onPlugins: () -> Unit,
    onCatalogOrder: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(
            context.applicationContext
        )
    }
    val healthStore = remember {
        PluginHealthStore(
            context.applicationContext
        )
    }
    val addons =
        engine.stremioAddons()
    val repositoryCount =
        pluginStore.repositories().size
    val providerCount =
        pluginStore.totalProviderCount()
    val catalogCount =
        addons.sumOf {
            it.descriptor.catalogs.count {
                catalog ->
                catalog.canLoadWithoutExtras
            }
        }
    val health =
        healthStore.records()
    val onlineCount =
        health.count {
            it.status == ProviderHealthStatus.ONLINE ||
                it.status == ProviderHealthStatus.SLOW
        }
    val slowCount =
        health.count {
            it.status == ProviderHealthStatus.SLOW ||
                it.status == ProviderHealthStatus.TIMEOUT
        }
    val failedCount =
        health.count {
            it.status == ProviderHealthStatus.FAILED ||
                it.status == ProviderHealthStatus.BLOCKED ||
                it.status == ProviderHealthStatus.UNAVAILABLE
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 14.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
    ) {
        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription =
                            "Back to Settings",
                        tint = Color.White,
                    )
                }

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            3.dp
                        ),
                ) {
                    Text(
                        "Content Manager",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight =
                            FontWeight.Black,
                    )
                    Text(
                        "Manage addons, plugins, providers and catalogs.",
                        color =
                            VueoPalette.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            Card(
                shape =
                    RoundedCornerShape(
                        20.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            VueoPalette
                                .SurfaceElevated
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                16.dp
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),
                ) {
                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            addons.size.toString(),
                        label = "Installed",
                    )
                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            onlineCount.toString(),
                        label = "Online",
                    )
                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            slowCount.toString(),
                        label = "Slow",
                    )
                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            failedCount.toString(),
                        label = "Failed",
                    )
                }
            }
        }

        item {
            ContentManagerCard(
                title = "Addons",
                subtitle =
                    "Stremio compatible catalogs, metadata, streams and subtitles.",
                status =
                    "${addons.size} installed",
                icon =
                    Icons.Default.Extension,
                onClick = onAddons,
            )
        }

        item {
            ContentManagerCard(
                title =
                    "Plugins & Providers",
                subtitle =
                    "QuickJS repositories, runtime providers, health and diagnostics.",
                status =
                    "$repositoryCount repos • $providerCount providers",
                icon =
                    Icons.Default.SettingsInputComponent,
                onClick = onPlugins,
            )
        }

        item {
            ContentManagerCard(
                title = "Catalog Order",
                subtitle =
                    "Choose the order catalogs appear on Home.",
                status =
                    "$catalogCount catalogs",
                icon =
                    Icons.Default.VideoLibrary,
                onClick =
                    onCatalogOrder,
            )
        }

        item {
            Surface(
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                color =
                    VueoPalette.Surface,
            ) {
                Text(
                    "Provider Health feeds Smart Source ranking. Slow or failed providers never need to block faster sources.",
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private data class CatalogOrderEntry(
    val key: String,
    val title: String,
    val providerName: String,
    val type: String,
    val addonEnabled: Boolean,
)

@Composable
private fun CatalogOrderScreen(
    engine: UnifiedMediaEngine,
    store: AddonStore,
    contentVersion: Int,
    onContentChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val entries =
        remember(
            contentVersion
        ) {
            engine.stremioAddons()
                .flatMap {
                    extension ->
                    extension.descriptor
                        .catalogs
                        .filter {
                            it.canLoadWithoutExtras
                        }
                        .map {
                            catalog ->
                            CatalogOrderEntry(
                                key =
                                    "${extension.descriptor.id}:${catalog.type}:${catalog.id}",
                                title =
                                    catalog.name
                                        ?: catalog.id,
                                providerName =
                                    extension.descriptor.name,
                                type =
                                    catalog.type
                                        .replaceFirstChar {
                                            it.uppercase()
                                        },
                                addonEnabled =
                                    engine.isExtensionEnabled(
                                        extension.descriptor.id
                                    ),
                            )
                        }
                }
        }

    val entryByKey =
        remember(entries) {
            entries.associateBy {
                it.key
            }
        }

    var order by remember(
        contentVersion,
        entries,
    ) {
        mutableStateOf(
            store.reconcileCatalogOrder(
                entries.map {
                    it.key
                }
            )
        )
    }

    fun move(
        index: Int,
        delta: Int,
    ) {
        val target =
            index + delta

        if (
            index !in order.indices ||
            target !in order.indices
        ) {
            return
        }

        val next =
            order.toMutableList()
        val moved =
            next.removeAt(index)

        next.add(
            target,
            moved,
        )

        order = next
        store.setCatalogOrder(next)
        onContentChanged()
    }

    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title =
                "Catalog Order",
            subtitle =
                "Arrange how catalogs appear on Home",
            onBack =
                onBack,
        )

        if (entries.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    "No catalogs available",
                    color =
                        VueoPalette.Muted,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier =
                Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            item {
                Text(
                    "Top catalogs appear first on Home. Catalogs from disabled addons keep their position but are not loaded.",
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            order.forEachIndexed {
                index,
                key ->
                val entry =
                    entryByKey[key]
                        ?: return@forEachIndexed

                item(
                    key =
                        "catalog-order:$key"
                ) {
                    ElevatedCard(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 12.dp,
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier =
                                    Modifier.size(
                                        34.dp
                                    ),
                                shape =
                                    CircleShape,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(
                                            alpha = .12f
                                        ),
                            ) {
                                Box(
                                    contentAlignment =
                                        Alignment.Center,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        fontWeight =
                                            FontWeight.Black,
                                    )
                                }
                            }

                            Spacer(
                                Modifier.width(
                                    12.dp
                                )
                            )

                            Column(
                                modifier =
                                    Modifier.weight(1f),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        2.dp
                                    ),
                            ) {
                                Text(
                                    entry.title,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        if (
                                            entry.addonEnabled
                                        ) {
                                            Color.White
                                        } else {
                                            VueoPalette.Muted
                                        },
                                )
                                Text(
                                    "${entry.providerName} • ${entry.type}",
                                    color =
                                        VueoPalette.Muted,
                                    fontSize = 11.sp,
                                )
                                if (
                                    !entry.addonEnabled
                                ) {
                                    Text(
                                        "Addon disabled",
                                        color =
                                            VueoPalette.Muted,
                                        fontSize = 10.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                    )
                                }
                            }

                            TextButton(
                                enabled =
                                    index > 0,
                                onClick = {
                                    move(
                                        index,
                                        -1,
                                    )
                                },
                            ) {
                                Text(
                                    "↑",
                                    fontSize = 20.sp,
                                )
                            }
                            TextButton(
                                enabled =
                                    index <
                                        order.lastIndex,
                                onClick = {
                                    move(
                                        index,
                                        1,
                                    )
                                },
                            ) {
                                Text(
                                    "↓",
                                    fontSize = 20.sp,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(
                    Modifier.height(
                        20.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun ContentMetric(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                2.dp
            ),
    ) {
        Text(
            value,
            color =
                VueoPalette.Accent,
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Black,
        )

        Text(
            label,
            color =
                VueoPalette.Muted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ContentManagerCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    17.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .background(
                        VueoPalette
                            .SurfaceStrong
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription =
                        null,
                    tint =
                        VueoPalette.Accent,
                )
            }

            Spacer(
                Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    ),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )

                Text(
                    status,
                    color =
                        VueoPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            Text(
                "›",
                color =
                    VueoPalette.Muted,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun AddonsScreen(
    engine: UnifiedMediaEngine,
    store: AddonStore,
    contentVersion: Int,
    onContentChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var installed by remember(contentVersion) {
        mutableStateOf(engine.stremioAddons())
    }
    var showInstallDialog by remember { mutableStateOf(false) }
    var manifestUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshingId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title = "Addons",
            subtitle = "Stremio compatible content sources",
            onBack = onBack,
            action = {
                FilledIconButton(onClick = { showInstallDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add addon")
                }
            },
        )

        if (installed.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(28.dp),
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "No addons installed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    Text(
                        "Install an HTTPS Stremio manifest URL. Catalogs exposed by the addon can then populate Home.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showInstallDialog = true }) {
                        Text("Add Stremio Addon")
                    }
                }
            }
        } else {
            val groupedAddons = AddonCategory.values().toList()
                .mapNotNull { category ->
                    val addons = installed.filter {
                        it.descriptor.primaryAddonCategory() == category
                    }
                    if (addons.isEmpty()) null else category to addons
                }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                groupedAddons.forEach { (category, addons) ->
                    item(key = "header:${category.name}") {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                category.label.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                                fontSize = 12.sp,
                                letterSpacing = 1.3.sp,
                            )

                            Text(
                                when (category) {
                                    AddonCategory.CATALOG_METADATA ->
                                        "Discovery, catalogs and title information"
                                    AddonCategory.STREAMS ->
                                        "Playback source providers"
                                    AddonCategory.SUBTITLES ->
                                        "Subtitle providers"
                                    AddonCategory.MULTI_PURPOSE ->
                                        "Addons with more than one content capability"
                                    AddonCategory.OTHER ->
                                        "Other Stremio addon resources"
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    items(
                        addons,
                        key = { it.descriptor.id },
                    ) { addon ->
                        AddonCard(
                            addon = addon,
                            enabled =
                                store.isAddonEnabled(
                                    addon.descriptor.baseUrl
                                ),
                            onEnabledChanged = {
                                enabled ->
                                store.setAddonEnabled(
                                    addon.descriptor.baseUrl,
                                    enabled,
                                )
                                engine.setExtensionEnabled(
                                    addon.descriptor.id,
                                    enabled,
                                )
                                installed =
                                    engine.stremioAddons()
                                onContentChanged()
                            },
                            isDevelopmentDefault = store.isDevelopmentDefault(
                                addon.descriptor.baseUrl
                            ),
                            refreshing = refreshingId == addon.descriptor.id,
                            onRefresh = {
                                scope.launch {
                                    refreshingId = addon.descriptor.id

                                    runCatching {
                                        ExtensionInstaller.installStremioAddon(
                                            addon.descriptor.baseUrl
                                        )
                                    }.onSuccess {
                                        refreshed ->
                                        engine.install(
                                            refreshed
                                        )
                                        engine.setExtensionEnabled(
                                            id =
                                                refreshed.descriptor.id,
                                            enabled =
                                                store.isAddonEnabled(
                                                    addon.descriptor.baseUrl
                                                ),
                                        )
                                        installed =
                                            engine.stremioAddons()
                                        onContentChanged()
                                    }

                                    refreshingId = null
                                }
                            },
                            onDelete = {
                                engine.uninstall(addon.descriptor.id)
                                store.remove(addon.descriptor.baseUrl)
                                installed = engine.stremioAddons()
                                onContentChanged()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showInstallDialog = false
                    status = null
                }
            },
            title = { Text("Install Stremio Addon") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Paste the addon's HTTPS manifest URL. VUEO will inspect its resources and catalogs before installing it."
                    )

                    OutlinedTextField(
                        value = manifestUrl,
                        onValueChange = {
                            manifestUrl = it
                            status = null
                        },
                        label = { Text("Manifest URL") },
                        placeholder = { Text("https://.../manifest.json") },
                        enabled = !busy,
                        singleLine = true,
                    )

                    status?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }

                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = manifestUrl.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null

                            runCatching {
                                ExtensionInstaller.installStremioAddon(
                                    manifestUrl.trim()
                                )
                            }.onSuccess { addon ->
                                store.add(
                                    addon.descriptor.baseUrl
                                )
                                engine.install(addon)
                                engine.setExtensionEnabled(
                                    addon.descriptor.id,
                                    true,
                                )
                                installed = engine.stremioAddons()
                                manifestUrl = ""
                                showInstallDialog = false
                                onContentChanged()
                            }.onFailure {
                                status = it.message ?: "Unable to install addon."
                            }

                            busy = false
                        }
                    },
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showInstallDialog = false
                        status = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AddonCard(
    addon: MediaExtension,
    enabled: Boolean,
    onEnabledChanged:
        (Boolean) -> Unit,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "S",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        addon.descriptor.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "v${addon.descriptor.version}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            fontSize = 12.sp,
                        )

                        if (isDevelopmentDefault) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                            ) {
                                Text(
                                    "DEV DEFAULT",
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 3.dp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange =
                        onEnabledChanged,
                )
                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            addon.descriptor.description?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider()

            Text(
                "${addon.descriptor.catalogs.size} catalogs  •  " +
                    "${addon.descriptor.resources.size} resources",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )

            if (addon.descriptor.resources.isNotEmpty()) {
                Text(
                    addon.descriptor.resources.sorted().joinToString("  •  "),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PluginsScreen(
    onBack: () -> Unit,
) {
    val context =
        LocalContext.current
    val store =
        remember {
            PluginStore(
                context.applicationContext
            )
        }
    val scope =
        rememberCoroutineScope()
    val healthStore =
        remember {
            PluginHealthStore(
                context.applicationContext
            )
        }
    val codeStore =
        remember {
            ProviderCodeStore(
                context.applicationContext
            )
        }
    val codeSync =
        remember {
            ProviderCodeSyncManager(
                context.applicationContext
            )
        }

    var healthRevision by remember {
        mutableIntStateOf(0)
    }
    var codeRevision by remember {
        mutableIntStateOf(0)
    }
    var repositories by remember {
        mutableStateOf(
            store.repositories()
        )
    }
    var selectedRepositoryUrl by remember {
        mutableStateOf<String?>(
            repositories.firstOrNull()
                ?.manifestUrl
        )
    }
    var pluginsEnabled by remember {
        mutableStateOf(
            store.pluginsEnabled()
        )
    }
    var showAddDialog by remember {
        mutableStateOf(false)
    }
    var repositoryUrl by remember {
        mutableStateOf("")
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var message by remember {
        mutableStateOf<String?>(
            null
        )
    }
    var refreshingUrl by remember {
        mutableStateOf<String?>(
            null
        )
    }

    fun refreshRepositories() {
        repositories =
            store.repositories()

        if (
            selectedRepositoryUrl == null ||
            repositories.none {
                it.manifestUrl ==
                    selectedRepositoryUrl
            }
        ) {
            selectedRepositoryUrl =
                repositories.firstOrNull()
                    ?.manifestUrl
        }
    }

    LaunchedEffect(Unit) {
        store.seedDevelopmentDefaultsIfNeeded()
        refreshRepositories()
        codeSync.syncMissing(
            repositories
        )
        codeRevision++
    }

    val selectedRepository =
        repositories.firstOrNull {
            it.manifestUrl ==
                selectedRepositoryUrl
        }

    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title = "Plugins",
            subtitle =
                "JavaScript provider repositories",
            onBack = onBack,
            action = {
                FilledIconButton(
                    onClick = {
                        showAddDialog = true
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription =
                            "Add repository",
                    )
                }
            },
        )

        LazyColumn(
            modifier =
                Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                ),
        ) {
            item {
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                18.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f),
                        ) {
                            Text(
                                "Plugin providers",
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                            Text(
                                "${repositories.size} repos • " +
                                    "${store.enabledProviderCount()} enabled providers",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = .6f),
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked =
                                pluginsEnabled,
                            onCheckedChange = {
                                pluginsEnabled = it
                                store.setPluginsEnabled(
                                    it
                                )
                            },
                        )
                    }
                }
            }

            item {
                val summary =
                    healthStore.summary(
                        repositories =
                            repositories,
                        pluginStore =
                            store,
                    )
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                18.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                9.dp
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    "Provider Health",
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                                Text(
                                    "Updated whenever VUEO runs source discovery.",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                            .copy(alpha = .58f),
                                    fontSize = 11.sp,
                                )
                            }
                            IconButton(
                                onClick = {
                                    healthRevision++
                                },
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription =
                                        "Refresh health",
                                )
                            }
                        }

                        Text(
                            "${summary.online} online • ${summary.slow} slow • " +
                                "${summary.noResults} no results",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 12.sp,
                        )

                        if (
                            summary.needsSetup > 0 ||
                            summary.unavailable > 0 ||
                            summary.blocked > 0 ||
                            summary.timeout > 0 ||
                            summary.failed > 0
                        ) {
                            Text(
                                "${summary.needsSetup} setup • " +
                                    "${summary.unavailable} unavailable • " +
                                    "${summary.blocked} blocked • " +
                                    "${summary.timeout} timeout • " +
                                    "${summary.failed} failed",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = .5f),
                                fontSize = 11.sp,
                            )
                        }

                        if (
                            summary.unknown > 0 ||
                            summary.disabled > 0
                        ) {
                            Text(
                                "${summary.unknown} unknown • ${summary.disabled} disabled",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = .5f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            if (repositories.isEmpty()) {
                item {
                    ElevatedCard(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    20.dp
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                        ) {
                            Text(
                                "No plugin repositories",
                                fontSize = 21.sp,
                                fontWeight =
                                    FontWeight.Black,
                            )
                            Text(
                                "Add a Nuvio-style provider repository URL.",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = .68f),
                            )
                            Button(
                                onClick = {
                                    showAddDialog = true
                                },
                            ) {
                                Text(
                                    "Add Repository"
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "REPOSITORIES",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .52f),
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    LazyRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        items(
                            repositories,
                            key = {
                                it.manifestUrl
                            },
                        ) {
                            repository ->
                            FilterChip(
                                selected =
                                    selectedRepositoryUrl ==
                                        repository.manifestUrl,
                                onClick = {
                                    selectedRepositoryUrl =
                                        repository.manifestUrl
                                },
                                label = {
                                    Text(
                                        repository.name,
                                        maxLines = 1,
                                        overflow =
                                            TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                selectedRepository
                    ?.let {
                        repository ->
                        item(
                            key =
                                "repo-card:${repository.manifestUrl}"
                        ) {
                            PluginRepositoryCard(
                                repository =
                                    repository,
                                store = store,
                                healthStore =
                                    healthStore,
                                healthRevision =
                                    healthRevision,
                                codeStore =
                                    codeStore,
                                codeRevision =
                                    codeRevision,
                                repositoryEnabled =
                                    store.isRepositoryEnabled(
                                        repository
                                    ),
                                onRepositoryEnabledChanged = {
                                    enabled ->
                                    store.setRepositoryEnabled(
                                        repository,
                                        enabled,
                                    )
                                    refreshRepositories()
                                    healthRevision++
                                },
                                isDevelopmentDefault =
                                    store.isDevelopmentDefault(
                                        repository.manifestUrl
                                    ),
                                refreshing =
                                    refreshingUrl ==
                                        repository.manifestUrl,
                                onRefresh = {
                                    scope.launch {
                                        refreshingUrl =
                                            repository.manifestUrl
                                        runCatching {
                                            PluginRepositoryClient
                                                .fetch(
                                                    repository.manifestUrl
                                                )
                                        }.onSuccess {
                                            refreshed ->
                                            store.upsert(
                                                refreshed
                                            )
                                            val syncResult =
                                                codeSync.syncRepository(
                                                    repository =
                                                        refreshed,
                                                    force = true,
                                                )
                                            refreshRepositories()
                                            selectedRepositoryUrl =
                                                refreshed.manifestUrl
                                            codeRevision++
                                            message =
                                                "Provider code ready " +
                                                    "${syncResult.readyProviders}/" +
                                                    "${refreshed.providers.size}"
                                        }.onFailure {
                                            message =
                                                it.message
                                        }
                                        refreshingUrl = null
                                    }
                                },
                                onDelete = {
                                    healthStore.removeRepository(
                                        repository.manifestUrl
                                    )
                                    store.remove(
                                        repository.manifestUrl
                                    )
                                    refreshRepositories()
                                    healthRevision++
                                },
                                onProviderChanged = {
                                    refreshRepositories()
                                    healthRevision++
                                },
                            )
                        }
                    }
            }

            item {
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                18.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            ),
                    ) {
                        Text(
                            "Runtime status",
                            fontWeight =
                                FontWeight.Bold,
                        )
                        Text(
                            "Provider runtime ACTIVE. Disabled repositories are skipped completely during source discovery.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showAddDialog = false
                    message = null
                }
            },
            title = {
                Text(
                    "Add Plugin Repository"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                ) {
                    Text(
                        "Paste a repository base URL or direct manifest.json URL."
                    )
                    OutlinedTextField(
                        value =
                            repositoryUrl,
                        onValueChange = {
                            repositoryUrl = it
                            message = null
                        },
                        label = {
                            Text(
                                "Repository URL"
                            )
                        },
                        placeholder = {
                            Text(
                                "https://.../manifest.json"
                            )
                        },
                        enabled = !busy,
                        singleLine = true,
                    )
                    message?.let {
                        Text(
                            it,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                            fontSize = 12.sp,
                        )
                    }
                    if (busy) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        repositoryUrl.isNotBlank() &&
                            !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            runCatching {
                                PluginRepositoryClient
                                    .fetch(
                                        repositoryUrl
                                    )
                            }.onSuccess {
                                repository ->
                                store.upsert(
                                    repository
                                )
                                store.setRepositoryEnabled(
                                    repository,
                                    true,
                                )
                                val syncResult =
                                    codeSync.syncRepository(
                                        repository =
                                            repository,
                                        force = true,
                                    )
                                refreshRepositories()
                                selectedRepositoryUrl =
                                    repository.manifestUrl
                                codeRevision++
                                repositoryUrl = ""
                                showAddDialog = false
                                message =
                                    "Installed ${repository.name}. " +
                                        "Provider code ready " +
                                        "${syncResult.readyProviders}/" +
                                        "${repository.providers.size}"
                            }.onFailure {
                                message =
                                    it.message
                                        ?: "Unable to install repository."
                            }
                            busy = false
                        }
                    },
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showAddDialog = false
                        message = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PluginRepositoryCard(
    repository: PluginRepositoryDescriptor,
    store: PluginStore,
    healthStore: PluginHealthStore,
    healthRevision: Int,
    codeStore: ProviderCodeStore,
    codeRevision: Int,
    repositoryEnabled: Boolean,
    onRepositoryEnabledChanged:
        (Boolean) -> Unit,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onProviderChanged: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(
                            RoundedCornerShape(
                                12.dp
                            )
                        )
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                        ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Text(
                        "JS",
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        fontWeight =
                            FontWeight.Black,
                    )
                }

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            repository.name,
                            fontWeight =
                                FontWeight.Black,
                            fontSize = 18.sp,
                        )

                        if (
                            isDevelopmentDefault
                        ) {
                            Spacer(
                                Modifier.width(8.dp)
                            )
                            Surface(
                                shape =
                                    RoundedCornerShape(
                                        50
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(
                                            alpha =
                                                .14f
                                        ),
                            ) {
                                Text(
                                    "DEV DEFAULT",
                                    modifier =
                                        Modifier.padding(
                                            horizontal =
                                                7.dp,
                                            vertical =
                                                3.dp,
                                        ),
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    fontSize =
                                        9.sp,
                                    fontWeight =
                                        FontWeight.Black,
                                )
                            }
                        }
                    }

                    Text(
                        "v${repository.version} • " +
                            "${repository.providers.size} providers",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .55f),
                        fontSize = 12.sp,
                    )

                    val readyProviderCode =
                        remember(
                            repository.manifestUrl,
                            repository.version,
                            codeRevision,
                        ) {
                            codeStore.readyCount(
                                repository
                            )
                        }

                    Text(
                        "Provider code $readyProviderCode/" +
                            "${repository.providers.size} ready locally",
                        color =
                            if (
                                readyProviderCode ==
                                repository.providers.size
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(alpha = .55f)
                            },
                        fontSize = 11.sp,
                    )
                }

                Switch(
                    checked =
                        repositoryEnabled,
                    onCheckedChange =
                        onRepositoryEnabledChanged,
                )
                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )
                IconButton(
                    enabled = !refreshing,
                    onClick = onRefresh,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription =
                            "Refresh",
                    )
                }

                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Delete",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .error,
                    )
                }
            }

            if (refreshing) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth()
                )
            }
            if (!repositoryEnabled) {
                Text(
                    "Repository disabled • provider preferences are preserved",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .55f),
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            repository.description?.let {
                Text(
                    it,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .65f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider()

            repository.providers.forEach { provider ->
                val enabled = store.isProviderEnabled(
                    repository,
                    provider,
                )

                val health = if (healthRevision >= 0) {
                    healthStore.record(
                        repositoryManifestUrl = repository.manifestUrl,
                        providerId = provider.id,
                    )
                } else {
                    null
                }

                ProviderHealthRow(
                    repository = repository,
                    provider = provider,
                    health = health,
                    enabled = enabled,
                    onEnabledChanged = { next ->
                        store.setProviderEnabled(
                            repository,
                            provider,
                            next,
                        )
                        onProviderChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderHealthRow(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord?,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val effectiveStatus = if (!enabled) {
        "Disabled"
    } else {
        health?.status?.label ?: ProviderHealthStatus.UNKNOWN.label
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    provider.name,
                    fontWeight = FontWeight.Bold,
                )

                val details = buildList {
                    if (provider.supportedTypes.isNotEmpty()) {
                        add(provider.supportedTypes.sorted().joinToString("/"))
                    }
                    if (provider.formats.isNotEmpty()) {
                        add(provider.formats.take(3).joinToString(", "))
                    }
                    if (provider.limited) add("limited")
                }.joinToString(" • ")

                if (details.isNotBlank()) {
                    Text(
                        details,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                        fontSize = 11.sp,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    effectiveStatus,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)
                        health?.status in setOf(
                            ProviderHealthStatus.FAILED,
                            ProviderHealthStatus.BLOCKED,
                            ProviderHealthStatus.TIMEOUT,
                            ProviderHealthStatus.UNAVAILABLE,
                        ) -> MaterialTheme.colorScheme.error

                        health?.status == ProviderHealthStatus.NEEDS_SETUP ->
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)

                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )

                health?.responseMs?.let { responseMs ->
                    Text(
                        "${responseMs} ms",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        if (
            enabled &&
            health?.status in setOf(
                ProviderHealthStatus.FAILED,
                ProviderHealthStatus.NEEDS_SETUP,
                ProviderHealthStatus.UNAVAILABLE,
                ProviderHealthStatus.BLOCKED,
                ProviderHealthStatus.TIMEOUT,
            )
        ) {
            health?.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error.copy(alpha = .85f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (enabled && health?.status == ProviderHealthStatus.NO_RESULTS) {
            Text(
                "Runtime completed but this title returned no sources.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                fontSize = 10.sp,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
        )
    }
}

@Composable
private fun MediaDetailsScreen(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    initialItem: MediaItem,
    initialLibraryEntry:
        LibraryPlaybackEntry?,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
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
    var geminiInsight by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<String?>(
            null
        )
    }

    var geminiInsightLoading by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf(false)
    }

    var geminiInsightError by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf<String?>(
            null
        )
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

        if (preserveCurrent) {
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

        val resumeCandidate =
            initialLibraryEntry
                ?: libraryStore
                    .continueWatching()
                    .firstOrNull { entry ->
                        entry.media.id ==
                            media.id &&
                            entry.media.type ==
                                media.type &&
                            entry.season != null &&
                            entry.episode != null
                    }

        val requestedSeason =
            resumeCandidate
                ?.season

        val requestedEpisode =
            resumeCandidate
                ?.episode

        val matched =
            if (
                requestedSeason != null &&
                requestedEpisode != null
            ) {
                media.episodes
                    .firstOrNull {
                        it.season ==
                            requestedSeason &&
                            it.episode ==
                            requestedEpisode
                    }
            } else {
                null
            }

        val firstSeason =
            matched?.season
                ?: media.episodes
                    .map {
                        it.season
                    }
                    .distinct()
                    .sorted()
                    .firstOrNull {
                        it > 0
                    }
                ?: media.episodes
                    .map {
                        it.season
                    }
                    .distinct()
                    .sorted()
                    .firstOrNull()

        selectedSeason =
            firstSeason

        selectedEpisode =
            matched
                ?: media.episodes
                    .firstOrNull {
                        it.season ==
                            firstSeason
                    }
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
            normalizeSeriesEpisodes(
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
                    TmdbEnhancementClient
                        .prepareForCore(
                            item =
                                initialItem,
                            apiKey =
                                tmdbKey,
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
            normalizeSeriesEpisodes(
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
            CatalogDiscoveryCache
                .related(
                    resolvedItem,
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
                        TmdbEnhancementClient
                            .enrich(
                                item =
                                    enrichedItem,
                                apiKey =
                                    tmdbKey,
                                metadataEnabled =
                                    settingsStore
                                        .tmdbMetadataEnrichmentEnabled(),
                                artworkEnabled =
                                    settingsStore
                                        .tmdbArtworkEnrichmentEnabled(),
                            )
                    }.getOrDefault(
                        enrichedItem
                    )

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
                        RichDetailsClient
                            .enrich(
                                media =
                                    enrichedItem,
                                apiKey =
                                    tmdbKey,
                            )
                    }.getOrDefault(
                        enrichedItem
                    )

                item =
                    enrichedItem
                publishDetailsRatings(
                    enrichedItem
                )
            }
        }

        launch {
            if (
                tmdbKey.isBlank() ||
                (
                    !settingsStore
                        .tmdbRecommendationsEnabled() &&
                    !settingsStore
                        .tmdbSimilarTitlesEnabled()
                )
            ) {
                return@launch
            }

            val tmdbRelated =
                runCatching {
                    TmdbEnhancementClient
                        .moreLikeThis(
                            item =
                                resolvedItem,
                            apiKey =
                                tmdbKey,
                            recommendationsEnabled =
                                settingsStore
                                    .tmdbRecommendationsEnabled(),
                            similarEnabled =
                                settingsStore
                                    .tmdbSimilarTitlesEnabled(),
                            limit = 18,
                        )
                }.getOrDefault(
                    emptyList()
                )

            if (tmdbRelated.isNotEmpty()) {
                relatedItems =
                    (
                        tmdbRelated +
                            localRelated
                    )
                        .distinctBy {
                            "${it.type}:${it.id}"
                        }
                        .take(18)
            }
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
    selectedPlaybackStartPositionMs =
        startPositionMs
            .coerceAtLeast(0L)

    val targetVideoId =
        selectedVideoId(
            item,
            targetEpisode,
        ) ?: return

    sourceDiscoveryJob
        ?.cancel()

    val cacheKey =
        SourceDiscoveryCache.key(
            mediaType =
                item.type,
            mediaId =
                item.id,
            videoId =
                targetVideoId,
        )

    val cached =
        SourceDiscoveryCache
            .get(cacheKey)

    val rankedCachedStreams =
        SourceCleaner.clean(
            sources = cached?.streams.orEmpty(),
            preferredQuality = preferredSourceQuality,
        )

    var autoPlayCommitted = false
    var subtitlesResolved = false
    var sourceDiscoveryCompleted = false
    var latestAutoPlayCandidates =
        rankedCachedStreams

    fun commitAutoPlayIfReady(
        candidates: List<StreamSource>,
        allowLowQualityFallback: Boolean = false,
    ) {
        latestAutoPlayCandidates = candidates

        if (
            !autoPlayFirst ||
            autoPlayCommitted ||
            !subtitlesResolved
        ) {
            return
        }

        val directCandidates =
            candidates
                .filter { it.isDirectPlayable }
                .sortedWith(
                    PlayerSourcePolicy.comparator(
                        preferredQuality = preferredSourceQuality,
                        originalLanguage = item.originalLanguage,
                    )
                )
        val candidate =
            directCandidates.firstOrNull {
                PlayerSourcePolicy
                    .assess(
                        source = it,
                        preferredQuality =
                            preferredSourceQuality,
                    )
                    .let { assessment ->
                        assessment.quality
                            .automaticRecoveryEligible &&
                            assessment.audioMatch
                                .recommendationEligible
                    }
            } ?: directCandidates
                .firstOrNull()
                ?.takeIf { allowLowQualityFallback }
            ?: return

        autoPlayCommitted = true
        selectedSeason = targetEpisode?.season
            ?: selectedSeason
        selectedEpisode = targetEpisode
        selectedPlaybackVideoId = targetVideoId
        selectedPlaybackSource = candidate
    }

    sourcePickerStreams =
        rankedCachedStreams

    sourcePickerProviderOrder =
        rankedCachedStreams
            .asSequence()
            .filter { it.isDirectPlayable }
            .map(::sourceProviderTabKey)
            .distinct()
            .toList()

    sourcePickerSubtitles =
        emptyList()

    sourcePickerRawCount =
        cached?.rawCount
            ?: 0

    sourcePickerNotice =
        cached?.notice

    sourcePickerSearching =
        true

    sourcePickerFirstResultMs =
        null

    sourcePickerProgress =
        if (cached != null) {
            "Recent sources loaded instantly • refreshing in background"
        } else {
            "Starting source discovery…"
        }

    loadingStreams = true
    sourceStatus = null

    sourceDiscoveryJob =
        scope.launch {
            val startedAt =
                System.nanoTime()

            val cachedStreams =
                rankedCachedStreams

            var freshAddonStreams =
                emptyList<StreamSource>()

            var freshPluginStreams =
                emptyList<StreamSource>()

            var addonRawCount = 0
            var pluginRawCount = 0

            var addonCompleted = 0
            var addonTotal = 0

            var pluginCompleted = 0
            var pluginTotal = 0

            fun elapsedMs(): Long =
                (
                    System.nanoTime() -
                        startedAt
                ) / 1_000_000L

            fun recordPlayableProviders(
                candidates: List<StreamSource>,
            ) {
                val discovered =
                    candidates
                        .asSequence()
                        .filter { it.isDirectPlayable }
                        .map(::sourceProviderTabKey)
                        .distinct()
                        .toList()

                if (discovered.isEmpty()) return

                val next =
                    sourcePickerProviderOrder
                        .toMutableList()

                discovered.forEach { provider ->
                    if (provider !in next) {
                        next += provider
                    }
                }

                if (next != sourcePickerProviderOrder) {
                    sourcePickerProviderOrder = next
                }
            }

            fun publish(
                progress: String,
            ) {
                recordPlayableProviders(
                    freshAddonStreams +
                        freshPluginStreams
                )

                val fresh =
                    SourceCleaner.clean(
                        sources =
                            freshAddonStreams +
                                freshPluginStreams,
                        preferredQuality =
                            preferredSourceQuality,
                    )

                val display =
                    if (
                        sourcePickerSearching
                    ) {
                        SourceCleaner.clean(
                            sources =
                                cachedStreams +
                                    fresh,
                            preferredQuality =
                                preferredSourceQuality,
                        )
                    } else {
                        fresh
                    }

                if (
                    sourcePickerFirstResultMs ==
                    null &&
                    display.isNotEmpty() &&
                    cachedStreams.isEmpty()
                ) {
                    sourcePickerFirstResultMs =
                        elapsedMs()
                }

                sourcePickerStreams =
                    display

                commitAutoPlayIfReady(display)

                sourcePickerRawCount =
                    maxOf(
                        cached?.rawCount
                            ?: 0,
                        addonRawCount +
                            pluginRawCount,
                    )

                sourcePickerProgress =
                    progress
            }

            val subtitlesDeferred =
                async {
                    runCatching {
                        engine.resolveSubtitles(
                            type =
                                item.type,
                            videoId =
                                targetVideoId,
                        )
                    }.getOrDefault(
                        emptyList()
                    )
                }

            launch {
                sourcePickerSubtitles =
                    subtitlesDeferred.await()
                subtitlesResolved = true
                commitAutoPlayIfReady(
                    candidates = latestAutoPlayCandidates,
                    allowLowQualityFallback =
                        sourceDiscoveryCompleted,
                )
            }

            val addonDeferred =
                async {
                    runCatching {
                        engine
                            .resolveStreamsProgressive(
                                type =
                                    item.type,
                                videoId =
                                    targetVideoId,
                            ) { progress ->
                                freshAddonStreams =
                                    progress.streams

                                addonRawCount =
                                    progress.rawCount

                                addonCompleted =
                                    progress.completedAddons

                                addonTotal =
                                    progress.totalAddons

                                publish(
                                    "Searching • Addons " +
                                        "$addonCompleted/$addonTotal • " +
                                        "Plugins $pluginCompleted/$pluginTotal"
                                )
                            }
                    }.getOrElse {
                        emptyList()
                    }
                }

            val pluginDeferred =
                async {
                    if (
                        !pluginStore
                            .pluginsEnabled() ||
                        pluginStore
                            .repositories()
                            .isEmpty()
                    ) {
                        return@async null
                    }

                    val tmdbId =
                        runCatching {
                            TmdbResolver.resolve(
                                media =
                                    item,
                                apiKey =
                                    pluginStore
                                        .tmdbApiKey(),
                            )
                        }.getOrNull()

                    if (tmdbId == null) {
                        sourcePickerNotice =
                            "Plugin providers skipped: VUEO could not resolve a TMDB ID. Add your TMDB API key in Settings > Enhancements > TMDB."

                        return@async null
                    }

                    val mediaType =
                        if (
                            item.type ==
                            "series"
                        ) {
                            "tv"
                        } else {
                            "movie"
                        }

                    runCatching {
                        pluginEngine
                            .discoverProgressive(
                                tmdbId =
                                    tmdbId,
                                mediaType =
                                    mediaType,
                                season =
                                    targetEpisode
                                        ?.season,
                                episode =
                                    targetEpisode
                                        ?.episode,
                            ) { progress ->
                                freshPluginStreams =
                                    progress
                                        .result
                                        .streams

                                pluginRawCount =
                                    freshPluginStreams
                                        .size

                                pluginCompleted =
                                    progress
                                        .completedProviders

                                pluginTotal =
                                    progress
                                        .totalProviders

                                publish(
                                    "Searching • Addons " +
                                        "$addonCompleted/$addonTotal • " +
                                        "Plugins $pluginCompleted/$pluginTotal • " +
                                        "${
                                            SourceCleaner.clean(
                                                sources =
                                                    freshAddonStreams +
                                                        freshPluginStreams,
                                                preferredQuality =
                                                    preferredSourceQuality,
                                            ).size
                                        } fresh sources"
                                )
                            }
                    }.getOrNull()
                }

            val finalAddonStreams =
                addonDeferred.await()

            val pluginResult =
                pluginDeferred.await()

            freshAddonStreams =
                finalAddonStreams

            if (pluginResult != null) {
                freshPluginStreams =
                    pluginResult.streams

                pluginRawCount =
                    pluginResult.streams.size

                sourcePickerNotice =
                    "Plugins: ${pluginResult.attemptedProviders} checked • " +
                        "${pluginResult.successfulProviders} online • " +
                        "${pluginResult.slowProviders} slow • " +
                        "${pluginResult.noResultProviders} no results • " +
                        "${pluginResult.needsSetupProviders} setup • " +
                        "${pluginResult.unavailableProviders} unavailable • " +
                        "${pluginResult.blockedProviders} blocked • " +
                        "${pluginResult.timeoutProviders} timeout • " +
                        "${pluginResult.failedProviders} failed."
            }

            val freshFinal =
                SourceCleaner.clean(
                    sources =
                        freshAddonStreams +
                            freshPluginStreams,
                    preferredQuality =
                        preferredSourceQuality,
                )

            val finalStreams =
                if (
                    freshFinal.isNotEmpty()
                ) {
                    freshFinal
                } else {
                    cachedStreams
                }

            recordPlayableProviders(
                finalStreams
            )

            sourcePickerStreams =
                finalStreams

            sourceDiscoveryCompleted = true
            commitAutoPlayIfReady(
                candidates = finalStreams,
                allowLowQualityFallback = true,
            )

            sourcePickerRawCount =
                maxOf(
                    cached?.rawCount
                        ?: 0,
                    addonRawCount +
                        pluginRawCount,
                )

            sourcePickerSearching =
                false

            loadingStreams = false

            sourcePickerProgress =
                if (
                    finalStreams.isEmpty()
                ) {
                    "Search complete • no sources found"
                } else {
                    "Search complete • ${finalStreams.size} unique sources"
                }

            if (
                finalStreams.isNotEmpty()
            ) {
                SourceDiscoveryCache.put(
                    key =
                        cacheKey,
                    streams =
                        finalStreams,
                    rawCount =
                        sourcePickerRawCount,
                    notice =
                        sourcePickerNotice,
                )
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

    if (playbackSource != null && playbackVideoId != null) {
        val nextEpisode =
            if (
                item.type == "series" &&
                selectedEpisode != null
            ) {
                val currentEpisode =
                    selectedEpisode!!

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
            settingsStore =
                settingsStore,
            title = playbackTitle(
                media = item,
                episode = selectedEpisode,
            ),
            mediaKey =
                "${item.type}:${item.id}:$playbackVideoId",
            media = item,
            videoId =
                playbackVideoId,
            episode =
                selectedEpisode,
            episodes =
                item.episodes,
            nextEpisode =
                nextEpisode,
            source = playbackSource,
            availableSources =
                sourcePickerStreams
                    .orEmpty(),
            sourceProviderOrder =
                sourcePickerProviderOrder,
            subtitles =
                sourcePickerSubtitles,
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
                selectedPlaybackSource =
                    nextSource
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
            onBack = {
                sourceDiscoveryJob?.cancel()
                sourceDiscoveryJob = null
                sourcePickerSearching = false
                sourcePickerStreams = null
                loadingStreams = false
                pendingPlaybackEpisode = null
                pendingPlaybackFailed = false
                selectedPlaybackSource = null
                selectedPlaybackVideoId = null
                selectedPlaybackStartPositionMs =
                    0L
            },
        )
        return
    }

    sourcePickerStreams?.let { streams ->
        SourcePickerScreen(
            mediaTitle = playbackTitle(
                media = item,
                episode = selectedEpisode,
            ),
            streams = streams,
            rawCount = sourcePickerRawCount,
            notice = sourcePickerNotice,
            searching = sourcePickerSearching,
            progressText = sourcePickerProgress,
            firstResultMs = sourcePickerFirstResultMs,
            providerOrder =
                sourcePickerProviderOrder,
            originalLanguage =
                item.originalLanguage,
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

                val videoId = selectedVideoId(item, selectedEpisode)

                if (videoId != null && source.isDirectPlayable) {
                    selectedPlaybackStartPositionMs =
                        selectedPlaybackStartPositionMs
                            .coerceAtLeast(0L)
                    selectedPlaybackVideoId = videoId
                    selectedPlaybackSource = source
                }
            },
        )
        return
    }

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

    val geminiApiKey =
        settingsStore
            .geminiApiKey()

    val geminiAvailable =
        geminiApiKey
            .isNotBlank() &&
            settingsStore
                .geminiInsightsEnabled()

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

        if (
            item.directors.isNotEmpty() ||
            item.creators.isNotEmpty() ||
            item.writers.isNotEmpty()
        ) {
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

        if (item.cast.isNotEmpty()) {
            item {
                MediaCastSection(
                    cast = item.cast
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
                        selectedSeason = season
                        selectedEpisode =
                            item.episodes
                                .firstOrNull {
                                    it.season ==
                                        season
                                }
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

        if (
            geminiAvailable &&
            !loadingMeta
        ) {
            item(
                key =
                    "details_gemini_insight"
            ) {
                GeminiInsightCard(
                    insight =
                        geminiInsight,
                    loading =
                        geminiInsightLoading,
                    error =
                        geminiInsightError,
                    onGenerate = {
                        if (
                            !geminiInsightLoading
                        ) {
                            geminiInsightLoading =
                                true
                            geminiInsightError =
                                null

                            scope.launch {
                                val dnaForInsight =
                                    if (
                                        detailsDnaPreferences
                                            .userDnaEnabled(
                                                detailsProfileId
                                            )
                                    ) {
                                        detailsDnaEngine
                                            .build()
                                    } else {
                                        null
                                    }

                                val visibleMatch =
                                    if (
                                        showDnaMatch &&
                                        dnaForInsight
                                            ?.hasUsefulData ==
                                            true
                                    ) {
                                        detailsDnaEngine
                                            .matchPercent(
                                                media =
                                                    item,
                                                dna =
                                                    dnaForInsight,
                                            )
                                    } else {
                                        null
                                    }

                                runCatching {
                                    GeminiClient
                                        .titleInsight(
                                            media =
                                                item,
                                            dna =
                                                dnaForInsight,
                                            dnaMatchPercent =
                                                visibleMatch,
                                            apiKey =
                                                geminiApiKey,
                                        )
                                }
                                    .onSuccess {
                                        result ->
                                        geminiInsight =
                                            result
                                    }
                                    .onFailure {
                                        error ->
                                        geminiInsightError =
                                            error.message
                                                ?.take(
                                                    180
                                                )
                                                ?.takeIf {
                                                    it.isNotBlank()
                                                }
                                                ?: "Gemini could not generate an insight. Try again."
                                    }

                                geminiInsightLoading =
                                    false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailsLoadingSkeleton() {
    Column(
        modifier =
            Modifier.padding(
                horizontal = 18.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(.42f)
                    .height(12.dp),
            shape = CircleShape,
            color =
                VueoPalette.SurfaceStrong,
        ) {}

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            shape = CircleShape,
            color =
                VueoPalette.Surface,
        ) {}

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(.78f)
                    .height(10.dp),
            shape = CircleShape,
            color =
                VueoPalette.Surface,
        ) {}
    }
}

private fun normalizeSeriesEpisodes(
    media: MediaItem,
): MediaItem {
    if (
        media.type != "series" ||
        media.episodes.isEmpty()
    ) {
        return media
    }

    val normalized =
        media.episodes.map { episode ->
            val idParts =
                episode.id.split(":")

            val idSeason =
                idParts
                    .getOrNull(
                        idParts.lastIndex - 1
                    )
                    ?.toIntOrNull()

            val idEpisode =
                idParts
                    .lastOrNull()
                    ?.toIntOrNull()

            episode.copy(
                season =
                    when {
                        episode.season > 0 ->
                            episode.season
                        idSeason != null &&
                            idSeason > 0 ->
                            idSeason
                        else ->
                            episode.season
                    },
                episode =
                    when {
                        episode.episode > 0 ->
                            episode.episode
                        idEpisode != null &&
                            idEpisode > 0 ->
                            idEpisode
                        else ->
                            episode.episode
                    },
            )
        }

    val finalEpisodes =
        if (
            normalized.isNotEmpty() &&
            normalized.none {
                it.season > 0
            } &&
            normalized.all {
                it.season == 0
            }
        ) {
            normalized.map {
                it.copy(
                    season = 1
                )
            }
        } else {
            normalized
        }

    return media.copy(
        episodes =
            finalEpisodes.sortedWith(
                compareBy<EpisodeItem> {
                    it.season
                }.thenBy {
                    it.episode
                }
            )
    )
}

private fun baseDetailsRatings(
    media: MediaItem,
): List<MediaRating> =
    buildList {
        media.imdbRating
            ?.takeIf {
                it.isFinite() &&
                    it > 0.0
            }
            ?.let {
                add(
                    MediaRating(
                        source = "imdb",
                        value = it,
                    )
                )
            }

        media.tmdbRating
            ?.takeIf {
                it.isFinite() &&
                    it > 0.0
            }
            ?.let {
                add(
                    MediaRating(
                        source = "tmdb",
                        value = it,
                    )
                )
            }
    }

private fun cleanDetailsReleaseInfo(
    releaseInfo: String?,
): String? =
    releaseInfo
        ?.trim()
        ?.trimEnd { char ->
            char.isWhitespace() ||
                char == '-' ||
                char == '–' ||
                char == '—'
        }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun formatDetailsRuntime(
    minutes: Int,
): String {
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        remaining <= 0 -> "${hours}h"
        else -> "${hours}h ${remaining}m"
    }
}

@Composable
private fun GeminiInsightCard(
    insight: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
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
                .SurfaceElevated,
        border =
            androidx.compose
                .foundation
                .BorderStroke(
                    width = 1.dp,
                    color =
                        VueoPalette.Accent
                            .copy(
                                alpha =
                                    .16f
                            ),
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    9.dp
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            2.dp
                        ),
                ) {
                    Text(
                        text =
                            "Gemini Insight",
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Black,
                    )

                    Text(
                        text =
                            "Optional AI • generated on demand",
                        color =
                            VueoPalette.Muted,
                        fontSize =
                            10.sp,
                    )
                }

                Surface(
                    shape =
                        RoundedCornerShape(
                            50.dp
                        ),
                    color =
                        VueoPalette.Accent
                            .copy(
                                alpha =
                                    .10f
                            ),
                ) {
                    Text(
                        text =
                            "GEMINI",
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    8.dp,
                                vertical =
                                    4.dp,
                            ),
                        color =
                            VueoPalette.Accent,
                        fontSize =
                            9.sp,
                        fontWeight =
                            FontWeight.Black,
                    )
                }
            }

            insight
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Text(
                        text = it,
                        color =
                            Color.White
                                .copy(
                                    alpha =
                                        .76f
                                ),
                        fontSize =
                            12.sp,
                        lineHeight =
                            18.sp,
                    )
                }

            error
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Text(
                        text = it,
                        color =
                            VueoPalette.Muted,
                        fontSize =
                            11.sp,
                    )
                }

            OutlinedButton(
                enabled =
                    !loading,
                onClick =
                    onGenerate,
            ) {
                Text(
                    if (loading) {
                        "Generating..."
                    } else if (
                        insight
                            .isNullOrBlank()
                    ) {
                        "Generate Insight"
                    } else {
                        "Refresh Insight"
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailsFactsRow(
    facts: List<String>,
    imdbRating: MediaRating?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        if (facts.isNotEmpty()) {
            Text(
                text =
                    facts.joinToString(
                        " • "
                    ),
                modifier =
                    Modifier.weight(
                        1f,
                        fill = false,
                    ),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        } else {
            Spacer(
                Modifier.weight(1f)
            )
        }

        imdbRating?.let { rating ->
            ImdbRatingMark(
                rating = rating
            )
        }
    }
}

@Composable
private fun ImdbRatingMark(
    rating: MediaRating,
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                5.dp
            ),
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable
                        .rating_imdb
                ),
            contentDescription =
                "IMDb",
            modifier =
                Modifier.size(
                    width = 30.dp,
                    height = 17.dp,
                ),
            contentScale =
                ContentScale.Fit,
        )

        Text(
            text =
                rating.displayValue(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight =
                FontWeight.Black,
        )
    }
}

@Composable
private fun MediaRatingsStrip(
    ratings: List<MediaRating>,
    vueoMatchPercent: Int?,
) {
    val ratingPriority =
        remember {
            mapOf(
                "imdb" to 0,
                "tomatoes" to 1,
                "metacritic" to 2,
                "tmdb" to 3,
                "trakt" to 4,
            )
        }

    val orderedRatings =
        remember(ratings) {
            ratings
                .distinctBy {
                    it.source
                }
                .sortedBy {
                    ratingPriority[
                        it.source
                    ] ?: Int.MAX_VALUE
                }
        }

    LazyRow(
        contentPadding =
            PaddingValues(
                horizontal = 18.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        items(
            orderedRatings,
            key = {
                it.source
            },
        ) { rating ->
            MediaRatingMark(
                rating = rating
            )
        }

        vueoMatchPercent?.let { percent ->
            item(
                key = "dna_match"
            ) {
                DnaMatchMark(
                    percent = percent
                )
            }
        }
    }
}

@Composable
private fun MediaRatingMark(
    rating: MediaRating,
) {
    val logo =
        when (rating.source) {
            "imdb" ->
                R.drawable.rating_imdb

            "tomatoes" ->
                R.drawable.rating_rotten_tomatoes

            "metacritic" ->
                R.drawable.rating_metacritic

            "tmdb" ->
                R.drawable.rating_tmdb

            "trakt" ->
                R.drawable.rating_trakt

            else -> null
        }

    val valueColor =
        when (rating.source) {
            "imdb" -> Color(0xFFF5C518)
            "tomatoes" -> Color(0xFFFA320A)
            "metacritic" -> Color(0xFFFFCC33)
            "tmdb" -> Color(0xFF01B4E4)
            "trakt" -> Color(0xFFED1C24)
            else -> Color.White
        }

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                5.dp
            ),
    ) {
        if (logo != null) {
            Image(
                painter =
                    painterResource(
                        logo
                    ),
                contentDescription =
                    rating.label,
                modifier =
                    Modifier.size(
                        width =
                            if (
                                rating.source ==
                                    "imdb"
                            ) {
                                30.dp
                            } else {
                                17.dp
                            },
                        height = 17.dp,
                    ),
                contentScale =
                    ContentScale.Fit,
            )
        } else {
            Text(
                text = rating.compactLabel,
                color = VueoPalette.Muted,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold,
            )
        }

        Text(
            text = rating.displayValue(),
            color = valueColor,
            fontSize = 13.sp,
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DnaMatchMark(
    percent: Int,
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp
            ),
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable
                        .ic_dna_match
                ),
            contentDescription =
                "DNA Match",
            modifier =
                Modifier.size(
                    18.dp
                ),
            contentScale =
                ContentScale.Fit,
        )

        Text(
            text = "DNA",
            color = VueoPalette.Muted,
            fontSize = 10.sp,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
        )

        Text(
            text = "$percent%",
            color = VueoPalette.Accent,
            fontSize = 13.sp,
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun MediaCreditsSummary(
    media: MediaItem,
) {
    Column(
        modifier =
            Modifier.padding(
                horizontal = 18.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp
            ),
    ) {
        if (
            media.type == "series" &&
            media.creators.isNotEmpty()
        ) {
            DetailsCreditLine(
                label = "Creator",
                names = media.creators,
            )
        } else if (
            media.directors.isNotEmpty()
        ) {
            DetailsCreditLine(
                label = "Director",
                names = media.directors,
            )
        }

        if (media.writers.isNotEmpty()) {
            DetailsCreditLine(
                label = "Writer",
                names = media.writers,
            )
        }
    }
}

@Composable
private fun DetailsCreditLine(
    label: String,
    names: List<String>,
) {
    Text(
        text =
            "$label: ${names.take(3).joinToString(", ")}",
        color =
            Color.White.copy(
                alpha = .78f
            ),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
private fun MediaCompanySection(
    title: String,
    companies: List<MediaCompany>,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        Text(
            text = title,
            modifier =
                Modifier.padding(
                    horizontal = 18.dp
                ),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight =
                FontWeight.Black,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 18.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            items(
                companies.take(12),
                key = {
                    it.name
                },
            ) { company ->
                Surface(
                    modifier =
                        Modifier
                            .width(116.dp)
                            .height(66.dp),
                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),
                    color =
                        Color.White.copy(
                            alpha = .94f
                        ),
                ) {
                    if (
                        !company.logo.isNullOrBlank()
                    ) {
                        NetworkImage(
                            url = company.logo,
                            contentDescription =
                                company.name,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                            contentScale =
                                ContentScale.Fit,
                            fallbackText =
                                company.name,
                            transparentBackground =
                                true,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Text(
                                text =
                                    company.name,
                                color =
                                    Color.Black.copy(
                                        alpha = .78f
                                    ),
                                fontSize = 10.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                maxLines = 2,
                                overflow =
                                    TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCastSection(
    cast: List<MediaPerson>,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        Text(
            text = "Cast",
            modifier =
                Modifier.padding(
                    horizontal = 18.dp
                ),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight =
                FontWeight.Black,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 18.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            items(
                cast.take(20),
                key = {
                    "${it.name}:${it.character.orEmpty()}"
                },
            ) { person ->
                Column(
                    modifier =
                        Modifier.width(
                            78.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(
                            4.dp
                        ),
                ) {
                    Surface(
                        modifier =
                            Modifier.size(
                                68.dp
                            ),
                        shape = CircleShape,
                        color =
                            VueoPalette
                                .SurfaceStrong,
                    ) {
                        NetworkImage(
                            url = person.profile,
                            contentDescription =
                                person.name,
                            modifier =
                                Modifier.fillMaxSize(),
                            contentScale =
                                ContentScale.Crop,
                            fallbackText =
                                person.name
                                    .take(1)
                                    .uppercase(),
                        )
                    }

                    Text(
                        text = person.name,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    person.character
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let { role ->
                            Text(
                                text = role,
                                color =
                                    VueoPalette.Muted,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis,
                            )
                        }
                }
            }
        }
    }
}

private fun detailsPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    entries: List<LibraryPlaybackEntry>,
): LibraryPlaybackEntry? =
    entries.firstOrNull { entry ->
        entry.media.id == media.id &&
            entry.media.type ==
                media.type &&
            if (
                media.type == "series"
            ) {
                episode != null &&
                    entry.season ==
                        episode.season &&
                    entry.episode ==
                        episode.episode
            } else {
                true
            }
    }

private fun moreLikeThisAttribution(
    usesTmdb: Boolean,
): String =
    if (usesTmdb) {
        "Powered by TMDB"
    } else {
        "Powered by VUEO"
    }

@Composable
private fun SeasonSelector(
    episodes: List<EpisodeItem>,
    selectedSeason: Int?,
    onSelectSeason: (Int) -> Unit,
) {
    val seasons =
        episodes
            .map {
                it.season
            }
            .distinct()
            .sortedWith(
                compareBy {
                    if (it == 0) {
                        Int.MAX_VALUE
                    } else {
                        it
                    }
                }
            )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        Text(
            text = "Season",
            modifier =
                Modifier.padding(
                    horizontal = 18.dp
                ),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight =
                FontWeight.Black,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 18.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
        ) {
            items(seasons) { season ->
                val selected =
                    season ==
                        selectedSeason

                Surface(
                    modifier =
                        Modifier
                            .widthIn(
                                min = 88.dp
                            )
                            .height(40.dp)
                            .clickable {
                                onSelectSeason(
                                    season
                                )
                            },
                    shape =
                        RoundedCornerShape(
                            12.dp
                        ),
                    color =
                        if (selected) {
                            MaterialTheme
                                .colorScheme
                                .primary
                                .copy(
                                    alpha = .24f
                                )
                        } else {
                            Color.Transparent
                        },
                    border =
                        androidx.compose
                            .foundation
                            .BorderStroke(
                                width = 1.dp,
                                color =
                                    if (selected) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(
                                                alpha = .52f
                                            )
                                    } else {
                                        Color.White.copy(
                                            alpha = .24f
                                        )
                                    },
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal =
                                        14.dp
                                ),
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Text(
                            text =
                                if (season == 0) {
                                    "Specials"
                                } else {
                                    "Season $season"
                                },
                            color =
                                if (selected) {
                                    Color.White
                                } else {
                                    Color.White.copy(
                                        alpha = .76f
                                    )
                                },
                            fontSize = 11.sp,
                            fontWeight =
                                if (selected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelector(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    selectedEpisode: EpisodeItem?,
    playbackEntries:
        List<LibraryPlaybackEntry>,
    onEpisodeClick:
        (EpisodeItem) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            ),
    ) {
        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 18.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    11.dp
                ),
        ) {
            items(
                episodes,
                key = {
                    it.id
                },
            ) { episode ->
                val selected =
                    episode.id ==
                        selectedEpisode?.id

                val playbackEntry =
                    detailsPlaybackEntry(
                        media = media,
                        episode = episode,
                        entries =
                            playbackEntries,
                    )

                Card(
                    modifier =
                        Modifier
                            .width(250.dp)
                            .height(164.dp)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color =
                                            VueoPalette.Accent,
                                        shape =
                                            RoundedCornerShape(
                                                16.dp
                                            ),
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                onEpisodeClick(
                                    episode
                                )
                            },
                    shape =
                        RoundedCornerShape(
                            16.dp
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (selected) {
                                    VueoPalette.Accent
                                        .copy(
                                            alpha = .10f
                                        )
                                } else {
                                    VueoPalette.Surface
                                }
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize(),
                    ) {
                            NetworkImage(
                                url =
                                    episode.thumbnail,
                                contentDescription =
                                    episode.title,
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                                contentScale =
                                    ContentScale.Crop,
                                fallbackText =
                                    episode.title,
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Black.copy(
                                                        alpha = .04f
                                                    ),
                                                    Color.Transparent,
                                                    Color.Black.copy(
                                                        alpha = .92f
                                                    ),
                                                )
                                            )
                                        ),
                            )

                            Surface(
                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.TopStart
                                        )
                                        .padding(
                                            9.dp
                                        ),
                                shape =
                                    RoundedCornerShape(
                                        8.dp
                                    ),
                                color =
                                    Color.Black.copy(
                                        alpha = .62f
                                    ),
                            ) {
                                Text(
                                    text =
                                        "S${episode.season} E${episode.episode}",
                                    modifier =
                                        Modifier.padding(
                                            horizontal =
                                                7.dp,
                                            vertical =
                                                4.dp,
                                        ),
                                    color =
                                        Color.White,
                                    fontSize = 9.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                )
                            }

                        Column(
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.BottomStart
                                    )
                                    .fillMaxWidth()
                                    .padding(
                                        start = 11.dp,
                                        end = 11.dp,
                                        bottom = 10.dp,
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    3.dp
                                ),
                        ) {
                            Text(
                                text =
                                    episode.title,
                                color =
                                    if (selected) {
                                        VueoPalette.Accent
                                    } else {
                                        Color.White
                                    },
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis,
                            )

                            episode.overview
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { overview ->
                                    Text(
                                        text = overview,
                                        color =
                                            Color.White.copy(
                                                alpha = .72f
                                            ),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 2,
                                        overflow =
                                            TextOverflow.Ellipsis,
                                    )
                                }

                            if (
                                playbackEntry != null &&
                                playbackEntry.positionMs >
                                    15_000L &&
                                (
                                    playbackEntry.durationMs <=
                                        0L ||
                                        playbackEntry.positionMs <
                                            (
                                                playbackEntry.durationMs *
                                                    .95f
                                            ).toLong()
                                )
                            ) {
                                LinearProgressIndicator(
                                    progress = {
                                        playbackEntry
                                            .progressFraction
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(
                                                CircleShape
                                            ),
                                    color =
                                        VueoPalette.Accent,
                                    trackColor =
                                        Color.White.copy(
                                            alpha = .18f
                                        ),
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
private fun SourcePickerScreen(
    mediaTitle: String,
    streams: List<StreamSource>,
    rawCount: Int,
    notice: String?,
    searching: Boolean,
    progressText: String,
    firstResultMs: Long?,
    providerOrder: List<String>,
    originalLanguage: String?,
    showTechnicalDetails: Boolean,
    onBack: () -> Unit,
    onPlay: (StreamSource) -> Unit,
) {
    BackHandler { onBack() }

    val playable = streams.filter { it.isDirectPlayable }
    val rankedAllSources = remember(
        playable,
        originalLanguage,
    ) {
        playable.sortedWith(
            PlayerSourcePolicy.comparator(
                originalLanguage = originalLanguage,
            )
        )
    }
    val best = rankedAllSources
        .firstOrNull {
            PlayerSourcePolicy.assess(
                source = it,
                originalLanguage = originalLanguage,
            ).let { assessment ->
                assessment.quality.automaticRecoveryEligible &&
                    assessment.audioMatch.recommendationEligible
            }
        } ?: rankedAllSources.firstOrNull()

    val currentProviders = playable
        .asSequence()
        .map(::sourceProviderTabKey)
        .distinct()
        .toList()

    val visibleProviders = (
        providerOrder.filter { it in currentProviders } +
            currentProviders.filter { it !in providerOrder }
        ).distinct()

    var selectedProvider by remember(mediaTitle) {
        mutableStateOf<String?>(SOURCE_PROVIDER_ALL)
    }
    var showEngineDetails by remember(mediaTitle) {
        mutableStateOf(false)
    }

    LaunchedEffect(visibleProviders) {
        val selected = selectedProvider

        when {
            visibleProviders.isEmpty() -> selectedProvider = null
            selected == null -> {
                selectedProvider = SOURCE_PROVIDER_ALL
            }
            selected != SOURCE_PROVIDER_ALL &&
                selected !in visibleProviders -> {
                selectedProvider = SOURCE_PROVIDER_ALL
            }
        }
    }

    val filteredSources = when (val selected = selectedProvider) {
        null,
        SOURCE_PROVIDER_ALL -> rankedAllSources

        else -> playable.filter {
            sourceProviderTabKey(it) == selected
        }
    }

    val selectedProviderLabel = when (val selected = selectedProvider) {
        null,
        SOURCE_PROVIDER_ALL -> "All"

        else -> sourceProviderTabDisplayName(selected)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "source-picker-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = 14.dp,
                        end = 18.dp,
                        top = 8.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = VueoPalette.SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        VueoPalette.Stroke.copy(alpha = .42f),
                    ),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Sources",
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        mediaTitle,
                        color = VueoPalette.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item(key = "smart-source-engine") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.SurfaceElevated
                ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 15.dp,
                        vertical = 9.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "SMART SOURCE ENGINE",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = .7.sp,
                        )

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = VueoPalette.Accent.copy(alpha = .13f),
                        ) {
                            Text(
                                if (searching) "LIVE" else "READY",
                                modifier = Modifier.padding(
                                    horizontal = 9.dp,
                                    vertical = 5.dp,
                                ),
                                color = VueoPalette.Accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = .8.sp,
                            )
                        }
                    }

                    if (searching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = VueoPalette.Accent,
                            trackColor = VueoPalette.SurfaceStrong,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                searching && playable.isEmpty() ->
                                    "Searching for sources"
                                searching ->
                                    "Top-ranked source found • checking remaining"
                                playable.isNotEmpty() ->
                                    "${streams.size} unique sources analysed"
                                else ->
                                    "Check engine details"
                            },
                            modifier = Modifier.weight(1f),
                            color = VueoPalette.Muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            if (showEngineDetails) {
                                "Hide details"
                            } else {
                                "Details"
                            },
                            color = VueoPalette.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    showEngineDetails = !showEngineDetails
                                }
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 5.dp,
                                ),
                        )
                    }

                    if (showEngineDetails) {
                        HorizontalDivider(
                            color = VueoPalette.Stroke.copy(alpha = .35f)
                        )

                        Text(
                            progressText,
                            color = Color.White.copy(alpha = .72f),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )

                        firstResultMs?.let {
                            Text(
                                "First source in $it ms",
                                color = VueoPalette.Muted,
                                fontSize = 10.sp,
                            )
                        }

                        if (rawCount > streams.size) {
                            Text(
                                "$rawCount raw results analysed • " +
                                    "${rawCount - streams.size} duplicates removed",
                                color = VueoPalette.Muted,
                                fontSize = 10.sp,
                            )
                        }

                        notice
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                Text(
                                    it,
                                    color = VueoPalette.Muted,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                    }
                }
            }
        }

        if (best != null) {
            item(key = "recommended-source") {
                val assessment = PlayerSourcePolicy.assess(
                    source = best,
                    originalLanguage = originalLanguage,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(
                            1.dp,
                            VueoPalette.Accent.copy(alpha = .42f),
                            RoundedCornerShape(20.dp),
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = VueoPalette.SurfaceElevated
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 15.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "VUEO RECOMMENDS",
                                modifier = Modifier.weight(1f),
                                color = VueoPalette.Accent,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(VueoPalette.Success)
                            )
                        }

                        Text(
                            sourceProviderTabDisplayName(
                                sourceProviderTabKey(best)
                            ),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val recommendedTags =
                            sourceMetadataLine(
                                source = best,
                                assessment = assessment,
                                compactAudioLabel = true,
                            )

                        if (recommendedTags.isNotBlank()) {
                            Text(
                                recommendedTags,
                                color = VueoPalette.Muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable { onPlay(best) },
                            shape = RoundedCornerShape(50),
                            color = VueoPalette.Accent,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "Play Recommended",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        } else if (!searching) {
            item(key = "source-empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = VueoPalette.SurfaceElevated,
                ) {
                    Text(
                        if (streams.isEmpty()) {
                            "No sources were returned for this title."
                        } else {
                            "Sources were found, but none are directly playable by the current VUEO player."
                        },
                        modifier = Modifier.padding(16.dp),
                        color = VueoPalette.Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        if (playable.isNotEmpty()) {
            item(key = "all-sources-header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 2.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "All Sources",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${playable.size} sources from " +
                            "${visibleProviders.size} providers",
                        color = VueoPalette.Muted,
                        fontSize = 10.sp,
                    )
                }
            }

            item(key = "provider-tabs") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = SOURCE_PROVIDER_ALL) {
                        SourceProviderTab(
                            label = "All",
                            selected = selectedProvider == SOURCE_PROVIDER_ALL,
                            onClick = {
                                selectedProvider = SOURCE_PROVIDER_ALL
                            },
                        )
                    }

                    items(
                        items = visibleProviders,
                        key = { "provider:$it" },
                    ) { provider ->
                        SourceProviderTab(
                            label = sourceProviderTabDisplayName(provider),
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = provider
                            },
                        )
                    }
                }
            }

            if (selectedProvider != SOURCE_PROVIDER_ALL) {
                item(key = "provider-result-summary") {
                    Text(
                        "$selectedProviderLabel • " +
                            "${filteredSources.size} sources",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = VueoPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            items(
                items = filteredSources,
                key = {
                    listOf(
                        sourceProviderTabKey(it),
                        it.url,
                        it.infoHash,
                        it.fileIndex,
                        it.providerId,
                        it.name,
                    ).joinToString(":")
                },
            ) { source ->
                StreamSourceCard(
                    source = source,
                    originalLanguage = originalLanguage,
                    showTechnicalDetails = showTechnicalDetails,
                    onClick = { onPlay(source) },
                )
            }
        } else if (searching) {
            item(key = "source-search-waiting") {
                Text(
                    "Sources will appear as soon as a provider responds.",
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    ),
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private const val SOURCE_PROVIDER_ALL =
    "__vueo_all_sources__"

private fun sourceProviderTabKey(
    source: StreamSource,
): String =
    source.providerName
        .trim()
        .ifBlank { "Other" }

private fun sourceProviderTabDisplayName(
    provider: String,
): String =
    provider
        .substringAfterLast(" / ", provider)
        .trim()
        .ifBlank { "Other" }

private fun sourceRepositoryDisplayName(
    source: StreamSource,
): String? =
    source.providerName
        .takeIf { " / " in it }
        ?.substringBefore(" / ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun sourceMetadataLine(
    source: StreamSource,
    assessment: PlayerSourceAssessment,
    compactAudioLabel: Boolean = false,
): String =
    listOfNotNull(
        sourceRepositoryDisplayName(source),
        when (assessment.audioMatch) {
            PlayerSourceAudioMatch.ORIGINAL ->
                if (compactAudioLabel) "Original" else "Original audio"

            PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL ->
                if (compactAudioLabel) {
                    "Original + multi"
                } else {
                    "Original in multi audio"
                }

            PlayerSourceAudioMatch.FOREIGN_DUB ->
                "Dub"

            PlayerSourceAudioMatch.UNKNOWN ->
                null
        },
        assessment.summary,
        source.hdr,
        source.audio,
    )
        .flatMap { value ->
            value.split(" • ")
        }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .joinToString(" • ")

@Composable
private fun SourceProviderTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) {
            VueoPalette.Accent.copy(alpha = .16f)
        } else {
            VueoPalette.SurfaceElevated
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) {
                VueoPalette.Accent.copy(alpha = .55f)
            } else {
                VueoPalette.Stroke.copy(alpha = .35f)
            },
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp,
            ),
            color = if (selected) {
                VueoPalette.Accent
            } else {
                Color.White.copy(alpha = .78f)
            },
            fontSize = 11.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamSourceCard(
    source: StreamSource,
    originalLanguage: String?,
    showTechnicalDetails: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val assessment = PlayerSourcePolicy.assess(
        source = source,
        originalLanguage = originalLanguage,
    )
    val provider = sourceProviderTabDisplayName(
        sourceProviderTabKey(source)
    )
    val metadata = sourceMetadataLine(
        source = source,
        assessment = assessment,
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 13.dp,
                vertical = 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    provider,
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = .9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (
                                assessment.quality
                                    .automaticRecoveryEligible
                            ) {
                                VueoPalette.Success
                            } else {
                                VueoPalette.Muted
                            }
                        )
                )
            }

            if (metadata.isNotBlank()) {
                Text(
                    metadata,
                    color = VueoPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (
                showTechnicalDetails &&
                source.name.isNotBlank()
            ) {
                Text(
                    source.name,
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


private data class PlayerContentWarning(
    val label: String,
    val severity: String,
    val severityRank: Int,
)

private object PlayerContentWarningRepository {
    private const val BASE_URL = "https://api.tiffara.com"
    private val cache = mutableMapOf<String, List<PlayerContentWarning>>()

    suspend fun get(imdbId: String): List<PlayerContentWarning> {
        synchronized(cache) {
            cache[imdbId]?.let { return it }
        }

        val warnings = runCatching {
            val root = JSONObject(
                SimpleHttp.get(
                    "$BASE_URL/titles/$imdbId/parentsGuide"
                )
            )
            val categories = root.optJSONArray("parentsGuide")
                ?: return@runCatching emptyList()
            val result = mutableListOf<PlayerContentWarning>()

            for (categoryIndex in 0 until categories.length()) {
                val category = categories.optJSONObject(categoryIndex)
                    ?: continue
                val categoryName = category.optString("category")
                    .uppercase()
                val label = when (categoryName) {
                    "SEXUAL_CONTENT" -> "Nudity"
                    "VIOLENCE" -> "Violence"
                    "PROFANITY" -> "Profanity"
                    "ALCOHOL_DRUGS" -> "Alcohol/Drugs"
                    "FRIGHTENING_INTENSE_SCENES" -> "Frightening"
                    else -> null
                } ?: continue
                val breakdowns = category
                    .optJSONArray("severityBreakdowns")
                    ?: continue
                var noneVotes = 0
                var dominantLevel: String? = null
                var dominantVotes = -1

                for (severityIndex in 0 until breakdowns.length()) {
                    val breakdown = breakdowns
                        .optJSONObject(severityIndex)
                        ?: continue
                    val level = breakdown
                        .optString("severityLevel")
                        .lowercase()
                    val votes = breakdown.optInt("voteCount", 0)

                    if (level == "none") {
                        noneVotes = votes
                    } else if (
                        level in setOf("mild", "moderate", "severe") &&
                        votes > dominantVotes
                    ) {
                        dominantLevel = level
                        dominantVotes = votes
                    }
                }

                if (dominantLevel == null || dominantVotes <= noneVotes) {
                    continue
                }

                val severity = when (dominantLevel) {
                    "severe" -> "Severe"
                    "moderate" -> "Moderate"
                    else -> "Mild"
                }
                val rank = when (dominantLevel) {
                    "severe" -> 0
                    "moderate" -> 1
                    else -> 2
                }
                result += PlayerContentWarning(
                    label = label,
                    severity = severity,
                    severityRank = rank,
                )
            }

            result.sortedBy { it.severityRank }.take(5)
        }.getOrDefault(emptyList())

        synchronized(cache) {
            cache[imdbId] = warnings
        }
        return warnings
    }
}

private fun extractPlayerImdbId(value: String?): String? =
    value?.let {
        Regex("tt\\d+", RegexOption.IGNORE_CASE)
            .find(it)
            ?.value
            ?.lowercase()
    }

private fun Context.playerContentWarningsEnabled(): Boolean =
    getSharedPreferences(
        "vueo_player_gestures",
        Context.MODE_PRIVATE,
    ).getBoolean("content_warnings", true)

private fun Context.setPlayerContentWarningsEnabled(
    enabled: Boolean,
) {
    getSharedPreferences(
        "vueo_player_gestures",
        Context.MODE_PRIVATE,
    ).edit()
        .putBoolean("content_warnings", enabled)
        .apply()
}

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

@Composable
private fun PlayerContentWarningsOverlay(
    warnings: List<PlayerContentWarning>,
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

private val VueoPlayerAccent =
    Color(0xFFB9FF3A)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerScreen(
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
    var contentWarningsEnabled by remember {
        mutableStateOf(
            context.playerContentWarningsEnabled()
        )
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
        source.url,
        initialPositionMs,
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
    var sleepTimerOption by remember {
        mutableStateOf(PlayerSleepTimerOption.OFF)
    }
    var sleepTimerDeadlineMs by remember {
        mutableStateOf<Long?>(null)
    }
    var sleepTimerRemainingSeconds by remember {
        mutableStateOf<Long?>(null)
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
        mutableStateOf<List<PlayerContentWarning>>(emptyList())
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

            else -> onBack()
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
                .setDataSourceFactory(httpFactory)

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
                            !settingsStore
                                .subtitlesOnByDefault(),
                        )
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
            subtitles
                .map { it.url }
                .distinct()
        )
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

    LaunchedEffect(
        player,
        subtitles,
    ) {
        val latestSubtitleUrls =
            subtitles
                .map { it.url }
                .distinct()

        if (latestSubtitleUrls != appliedSubtitleUrls) {
            val positionMs =
                player.currentPosition
                    .coerceAtLeast(0L)
            val continuePlaying =
                player.playWhenReady

            audioPreferenceRestored = false
            subtitlePreferenceRestored = false

            player.setMediaItem(
                buildPlayerMediaItem(
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
                ),
                positionMs,
            )
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(
                        C.TRACK_TYPE_TEXT,
                        subtitlesDisabled,
                    )
                    .build()
            player.prepare()
            player.playWhenReady = continuePlaying
            appliedSubtitleUrls = latestSubtitleUrls
        }
    }

    fun recordLibraryProgress() {
        libraryStore.recordPlayback(
            media = media,
            videoId = videoId,
            episodeTitle = episode?.title,
            season = episode?.season,
            episode = episode?.episode,
            positionMs = player.currentPosition,
            durationMs = player.duration,
        )
    }

    fun savePosition() {
        playbackStore.savePositionMs(
            mediaKey = mediaKey,
            positionMs = player.currentPosition,
            durationMs = player.duration,
        )
        recordLibraryProgress()
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
    ) {
        val externalSubtitles =
            subtitles.associateBy { it.id }
        audioTracks = playerTrackChoices(
            tracks = tracks,
            trackType = C.TRACK_TYPE_AUDIO,
        )
        textTracks = playerTrackChoices(
            tracks = tracks,
            trackType = C.TRACK_TYPE_TEXT,
            externalSubtitles = externalSubtitles,
        )
        selectedSubtitleIsExternal =
            !subtitlesDisabled &&
            textTracks
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

        if (!subtitlePreferenceRestored && textTracks.isNotEmpty()) {
            val globalSelection =
                settingsStore.lastSubtitleSelection()
            val contentSelection =
                settingsStore.subtitleSelection(mediaKey)
            val savedSelection =
                contentSelection ?: globalSelection
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
                }

                savedTrack != null -> {
                    if (globalSelection == null) {
                        settingsStore.setLastSubtitleSelection(
                            PLAYER_SUBTITLE_LANGUAGE_PREFIX +
                                canonicalSubtitleLanguage(
                                    savedTrack.language
                                )
                        )
                    }
                    subtitlePreferenceRestored = true
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
                PlayerContentWarningRepository.get(imdbId)
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
                        sourceRecoverySession.markReady()
                        playbackError = null
                        playbackPhase = PlayerPlaybackPhase.READY
                        recoveryInProgress = false
                    }

                    if (
                        playbackState ==
                            Player.STATE_ENDED
                    ) {
                        val sleepAfterEpisode =
                            sleepTimerOption ==
                                PlayerSleepTimerOption.END_OF_EPISODE
                        if (sleepAfterEpisode) {
                            sleepTimerOption =
                                PlayerSleepTimerOption.OFF
                            sleepTimerDeadlineMs = null
                            sleepTimerRemainingSeconds = null
                            nextEpisodeCountdown = null
                            gestureMessage = "Sleep timer ended"
                        }
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
                                if (
                                    autoPlayNextEpisode &&
                                    !sleepAfterEpisode
                                ) {
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
        sleepTimerDeadlineMs,
    ) {
        val deadline = sleepTimerDeadlineMs
            ?: return@LaunchedEffect

        while (true) {
            val remainingMs =
                (deadline - SystemClock.elapsedRealtime())
                    .coerceAtLeast(0L)
            sleepTimerRemainingSeconds =
                (remainingMs + 999L) / 1_000L

            if (remainingMs <= 0L) {
                player.pause()
                sleepTimerOption = PlayerSleepTimerOption.OFF
                sleepTimerDeadlineMs = null
                sleepTimerRemainingSeconds = null
                gestureMessage = "Sleep timer ended"
                controlsVisible = true
                break
            }
            delay(minOf(1_000L, remainingMs))
        }
    }

    LaunchedEffect(
        player,
        mediaKey,
    ) {
        var librarySaveTicks = 0
        while (true) {
            delay(500L)
            currentPositionMs =
                player.currentPosition
                    .coerceAtLeast(0L)
            durationMs =
                player.duration
                    .coerceAtLeast(0L)

            librarySaveTicks++
            if (librarySaveTicks >= 20) {
                playbackStore.savePositionMs(
                    mediaKey = mediaKey,
                    positionMs =
                        player.currentPosition,
                    durationMs = player.duration,
                )
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
                player.seekTo(0L)
                player.playWhenReady = true
                resumePromptVisible = false
            },
            onDismiss = onBack,
        )
    }

    if (showAudioDialog) {
        PlayerAudioWorkspace(
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
    }

    if (showSubtitleDialog) {
        PlayerSubtitleWorkspace(
            tracks = textTracks,
            subtitlesDisabled = subtitlesDisabled,
            preferredLanguageCode =
                playerPreferredSubtitleLanguageCode(
                    settingsStore
                ),
            secondaryLanguageCode = settingsStore
                .secondarySubtitleLanguage()
                .languageCode,
            subtitleDelayMs = subtitleDelayMs,
            style = subtitleStyle,
            onDisable = {
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
                applyTrackChoice(
                    player = player,
                    trackType = C.TRACK_TYPE_TEXT,
                    choice = choice,
                )
                subtitlesDisabled = false
                selectedSubtitleIsExternal =
                    choice.selectionId
                        .startsWith("external:")
                settingsStore.setSubtitleSelection(
                    contentId = mediaKey,
                    selectionId = choice.selectionId,
                )
                settingsStore.setLastSubtitleSelection(
                    PLAYER_SUBTITLE_LANGUAGE_PREFIX +
                        canonicalSubtitleLanguage(
                            choice.language
                        )
                )
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
    }

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

    if (showSourceDialog) {
        PlayerSourcesWorkspace(
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
    }

    if (showEpisodeDialog) {
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
    }

    if (showMoreDialog) {
        PlayerMoreWorkspace(
            playbackSpeed = playbackSpeed,
            videoFit = videoFit,
            sleepTimer = sleepTimerOption,
            sleepTimerRemainingSeconds =
                sleepTimerRemainingSeconds,
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
            onSleepTimerChange = { option ->
                sleepTimerOption = option
                sleepTimerDeadlineMs = option.minutes?.let { minutes ->
                    SystemClock.elapsedRealtime() +
                        minutes * 60_000L
                }
                sleepTimerRemainingSeconds = option.minutes?.let {
                    it * 60L
                }
                gestureMessage = when (option) {
                    PlayerSleepTimerOption.OFF ->
                        "Sleep timer off"
                    PlayerSleepTimerOption.END_OF_EPISODE ->
                        "Sleep after this episode"
                    else -> "Sleep timer ${option.label}"
                }
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
                context.setPlayerContentWarningsEnabled(enabled)
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
                sleepTimerOption = PlayerSleepTimerOption.OFF
                sleepTimerDeadlineMs = null
                sleepTimerRemainingSeconds = null
                autoPlayNextEpisode = true
                settingsStore.setAutoPlayNextEpisodeEnabled(true)
                skipSegmentsEnabled = true
                settingsStore.setSkipSegmentsEnabled(true)
                contentWarningsEnabled = true
                context.setPlayerContentWarningsEnabled(true)
                gestureMessage = "Player controls reset"
            },
            onDismiss = { showMoreDialog = false },
        )
    }

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

        if (controlsVisible && !controlsLocked) {
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
                        onBack()
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
            if (
                nextEpisodeCardEpisode != null &&
                nextEpisodeCardVisible
            ) {
                PlayerNextEpisodeCard(
                    episode = nextEpisodeCardEpisode,
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
                                nextEpisodeCardEpisode
                            )
                        } else {
                            startNextEpisode()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 24.dp,
                            bottom = 126.dp,
                        ),
                )
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

        if (controlsLocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clickable {
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

        gestureMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(
                    alpha = .74f
                ),
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

@Composable
private fun PlayerResumePrompt(
    positionLabel: String,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.46f)
                .widthIn(min = 320.dp, max = 440.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF2131416),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = .14f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = VueoPlayerAccent.copy(alpha = .14f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            VueoPlayerAccent.copy(alpha = .42f),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = VueoPlayerAccent,
                            )
                        }
                    }

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Resume watching?",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Continue from $positionLabel",
                            color = Color.White.copy(alpha = .58f),
                            fontSize = 11.sp,
                        )
                    }
                }

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 10.dp,
                    ),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Resume $positionLabel",
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = onStartOver,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = .18f),
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 9.dp,
                    ),
                ) {
                    Text(
                        "Start Over",
                        color = Color.White.copy(alpha = .86f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerPlaybackErrorCard(
    error: String,
    canChooseSource: Boolean,
    onRetry: () -> Unit,
    onChooseSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(.38f)
            .widthIn(min = 300.dp, max = 440.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xED181A1C),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = .14f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(VueoPlayerAccent),
                )
                Text(
                    "Playback problem",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                error,
                color = Color.White.copy(alpha = .58f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Text(
                        "Retry",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    enabled = canChooseSource,
                    onClick = onChooseSource,
                    modifier = Modifier.weight(1.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(
                            alpha = if (canChooseSource) .20f else .08f
                        ),
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Text(
                        "Choose Source",
                        color = Color.White.copy(
                            alpha = if (canChooseSource) .86f else .28f
                        ),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerNextEpisodeCard(
    episode: EpisodeItem,
    countdownSeconds: Int?,
    switching: Boolean,
    failed: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canPlay = !switching
    Surface(
        modifier = modifier
            .fillMaxWidth(.38f)
            .widthIn(min = 300.dp, max = 440.dp)
            .clickable(
                enabled = canPlay,
                onClick = onPlay,
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xED181A1C),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = .14f),
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NetworkImage(
                    url = episode.thumbnail,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .width(92.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    fallbackText = "S${episode.season} E${episode.episode}",
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = when {
                            failed -> "No playable source"
                            switching -> "Finding source..."
                            else -> "Next Episode"
                        },
                        color = when {
                            failed -> Color(0xFFFF8A80)
                            switching -> VueoPlayerAccent
                            else -> Color.White.copy(alpha = .62f)
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = "S${episode.season} E${episode.episode} • " +
                            episode.title.ifBlank {
                                "Episode ${episode.episode}"
                            },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    enabled = canPlay,
                    onClick = onPlay,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (canPlay) {
                            VueoPlayerAccent.copy(alpha = .72f)
                        } else {
                            Color.White.copy(alpha = .14f)
                        },
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 11.dp,
                        vertical = 5.dp,
                    ),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (canPlay) {
                            VueoPlayerAccent
                        } else {
                            Color.White.copy(alpha = .30f)
                        },
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = when {
                            failed -> "Retry"
                            switching -> "Loading"
                            countdownSeconds != null ->
                                "Play in ${countdownSeconds}s"
                            else -> "Play"
                        },
                        color = if (canPlay) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = .30f)
                        },
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }

            countdownSeconds?.let { seconds ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = .12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (seconds / 8f).coerceIn(0f, 1f)
                            )
                            .fillMaxHeight()
                            .background(VueoPlayerAccent),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRoundAction(
    icon: ImageVector,
    contentDescription: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .size(
                if (primary) 78.dp else 62.dp
            ),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(
                if (primary) 52.dp else 42.dp
            ),
            tint = Color.White,
        )
    }
}

@Composable
private fun PlayerTopAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = .42f),
    ) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = Color.White.copy(
                    alpha = if (enabled) .94f else .38f
                ),
            )
        }
    }
}

@Composable
private fun PlayerPanelAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = 9.dp,
                vertical = 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = Color.White.copy(
                alpha = if (enabled) .94f else .38f
            ),
        )
        Text(
            label,
            maxLines = 1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(
                alpha = if (enabled) .88f else .38f
            ),
        )
    }
}

@Composable
private fun PlayerPanelWindow(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.86f)
                .widthIn(max = 520.dp)
                .heightIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF2131416),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = .14f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        subtitle?.let {
                            Text(
                                it,
                                color = Color.White.copy(alpha = .52f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(19.dp),
                            tint = Color.White.copy(alpha = .82f),
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = .09f),
                )

                content()
            }
        }
    }
}

@Composable
private fun PlayerChoiceCard(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            VueoPlayerAccent.copy(alpha = .12f)
        } else {
            Color.White.copy(alpha = .035f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) {
                VueoPlayerAccent.copy(alpha = .42f)
            } else {
                Color.White.copy(alpha = .07f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 13.dp,
                vertical = 11.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    color = Color.White.copy(alpha = .94f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = Color.White.copy(alpha = .50f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Text(
                    "Playing",
                    color = VueoPlayerAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun PlayerView.applyVueoSubtitleStyle(
    style: PlayerSubtitleStyleState,
    fontScale: Float = 1f,
) {
    subtitleView?.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            style.fontSizeSp.toFloat() *
                fontScale.coerceIn(.35f, 1f),
        )
        setBottomPaddingFraction(
            style.bottomPaddingPercent / 100f
        )
        setStyle(
            CaptionStyleCompat(
                style.textColor,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                if (style.outlineEnabled) {
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    CaptionStyleCompat.EDGE_TYPE_NONE
                },
                style.outlineColor,
                if (style.bold) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                },
            )
        )
    }
}

@androidx.annotation.OptIn(
    androidx.media3.common.util.UnstableApi::class
)
private class VueoSubtitleOffsetRenderersFactory(
    context: Context,
    private val subtitleDelayUsProvider: () -> Long,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val normalizingOutput =
            VueoCueNormalizingTextOutput(
                delegate = output,
                shouldNormalizeCuePositionProvider =
                    shouldNormalizeCuePositionProvider,
            )
        val firstTextRenderer = out.size
        super.buildTextRenderers(
            context,
            normalizingOutput,
            outputLooper,
            extensionRendererMode,
            out,
        )
        for (index in firstTextRenderer until out.size) {
            out[index] = VueoSubtitleOffsetRenderer(
                baseRenderer = out[index],
                subtitleDelayUsProvider = subtitleDelayUsProvider,
            )
        }
    }
}

private class VueoCueNormalizingTextOutput(
    private val delegate: TextOutput,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        delegate.onCues(
            CueGroup(
                cueGroup.cues.map(::normalizeCuePosition),
                cueGroup.presentationTimeUs,
            )
        )
    }

    @Deprecated(
        "Uses the deprecated Media3 callback for text outputs."
    )
    override fun onCues(cues: List<Cue>) {
        delegate.onCues(
            cues.map(::normalizeCuePosition)
        )
    }

    private fun normalizeCuePosition(cue: Cue): Cue {
        if (
            !shouldNormalizeCuePositionProvider() ||
            cue.bitmap != null ||
            cue.verticalType != Cue.TYPE_UNSET ||
            cue.line == Cue.DIMEN_UNSET
        ) {
            return cue
        }

        return cue.buildUpon()
            .setLine(
                Cue.DIMEN_UNSET,
                Cue.TYPE_UNSET,
            )
            .setLineAnchor(Cue.TYPE_UNSET)
            .build()
    }
}

private class VueoSubtitleOffsetRenderer(
    baseRenderer: Renderer,
    private val subtitleDelayUsProvider: () -> Long,
) : ForwardingRenderer(baseRenderer) {
    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        val subtitlePositionUs =
            (positionUs - subtitleDelayUsProvider())
                .coerceAtLeast(0L)
        super.render(
            subtitlePositionUs,
            elapsedRealtimeUs,
        )
    }
}

@Composable
private fun PlayerFullscreenEffect(
    context: android.content.Context,
    orientation: PlayerOrientation,
) {
    DisposableEffect(
        context,
        orientation,
    ) {
        val activity = context as? Activity
        val window = activity?.window
        val decor = window?.decorView
        val previousFlags =
            decor?.systemUiVisibility ?: 0
        val previousOrientation =
            activity?.requestedOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        activity?.requestedOrientation =
            when (orientation) {
                PlayerOrientation.AUTO,
                PlayerOrientation.LANDSCAPE ->
                    ActivityInfo
                        .SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                PlayerOrientation.PORTRAIT ->
                    ActivityInfo
                        .SCREEN_ORIENTATION_SENSOR_PORTRAIT

                PlayerOrientation.FOLLOW_DEVICE ->
                    ActivityInfo
                        .SCREEN_ORIENTATION_UNSPECIFIED
            }

        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        onDispose {
            activity?.requestedOrientation =
                previousOrientation

            if (Build.VERSION.SDK_INT >= 30) {
                window?.insetsController?.show(
                    WindowInsets.Type.systemBars()
                )
            } else {
                decor?.systemUiVisibility =
                    previousFlags
            }
        }
    }
}

internal data class PlayerTrackChoice(
    val key: String,
    val label: String,
    val override:
        TrackSelectionOverride,
    val selected: Boolean,
    val language: String?,
    val sourceLabel: String,
    val metadata: String?,
    val selectionId: String,
)

private const val PLAYER_SUBTITLE_LABEL_PREFIX =
    "vueo-subtitle:"

private const val PLAYER_SUBTITLE_OFF =
    "subtitle:off"

private const val PLAYER_SUBTITLE_LANGUAGE_PREFIX =
    "subtitle-language:"

private const val PLAYER_AUDIO_AUTO =
    "audio:auto"

private fun playerPreferredSubtitleLanguageCode(
    settingsStore: SettingsStore,
): String? {
    val lastSelection = settingsStore.lastSubtitleSelection()
    return lastSelection
        ?.takeIf {
            it.startsWith(
                PLAYER_SUBTITLE_LANGUAGE_PREFIX
            )
        }
        ?.removePrefix(
            PLAYER_SUBTITLE_LANGUAGE_PREFIX
        )
        ?: settingsStore
            .preferredSubtitleLanguage()
            .languageCode
}

private fun playerSubtitlesEnabledAtStart(
    settingsStore: SettingsStore,
): Boolean =
    when (val lastSelection = settingsStore.lastSubtitleSelection()) {
        PLAYER_SUBTITLE_OFF -> false
        null -> settingsStore.subtitlesOnByDefault()
        else ->
            lastSelection.startsWith(
                PLAYER_SUBTITLE_LANGUAGE_PREFIX
            ) || settingsStore.subtitlesOnByDefault()
    }

private fun playerTrackChoices(
    tracks: Tracks,
    trackType: Int,
    externalSubtitles: Map<String, SubtitleTrack> = emptyMap(),
): List<PlayerTrackChoice> {
    val result =
        mutableListOf<
            PlayerTrackChoice
        >()

    tracks.groups.forEachIndexed {
        groupIndex,
        group ->

        if (
            group.type !=
            trackType
        ) {
            return@forEachIndexed
        }

        for (
            trackIndex in
            0 until group.length
        ) {
            if (
                !group.isTrackSupported(
                    trackIndex
                )
            ) {
                continue
            }

            val format =
                group.getTrackFormat(
                    trackIndex
                )

            val externalSubtitle =
                format.id?.let(externalSubtitles::get)
                    ?: format.label
                        ?.removePrefix(
                            PLAYER_SUBTITLE_LABEL_PREFIX
                        )
                        ?.takeIf {
                            format.label?.startsWith(
                                PLAYER_SUBTITLE_LABEL_PREFIX
                            ) == true
                        }
                        ?.let(externalSubtitles::get)
            val trackLanguage =
                externalSubtitle?.language
                    ?: format.language
                    ?: format.label
                        ?.trim()
                        ?.takeIf { it.length in 2..3 }
            val label = if (
                trackType == C.TRACK_TYPE_TEXT
            ) {
                externalSubtitle?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: friendlySubtitleLanguageName(
                        trackLanguage
                    )
            } else {
                buildAudioTrackLabel(
                    formatLabel = format.label,
                    language = format.language,
                    fallbackIndex = result.size + 1,
                )
            }
            val selectionId = if (
                externalSubtitle != null
            ) {
                "external:${externalSubtitle.providerId}:" +
                    "${externalSubtitle.id}:" +
                    externalSubtitle.url.hashCode()
            } else if (trackType == C.TRACK_TYPE_AUDIO) {
                buildAudioSelectionId(
                    language = trackLanguage,
                    formatLabel = format.label,
                    channelCount = format.channelCount,
                    sampleMimeType = format.sampleMimeType,
                    trackId = format.id,
                )
            } else {
                "builtin:${canonicalSubtitleLanguage(trackLanguage)}:" +
                    "${format.label.orEmpty()}:$trackIndex"
            }

            result +=
                PlayerTrackChoice(
                    key =
                        "$groupIndex:" +
                            trackIndex,
                    label = label,
                    override =
                        TrackSelectionOverride(
                            group.mediaTrackGroup,
                            trackIndex,
                        ),
                    selected =
                        group
                            .isTrackSelected(
                                trackIndex
                            ),
                    language = trackLanguage,
                    sourceLabel =
                        externalSubtitle?.providerName
                            ?: "Built-in",
                    metadata = if (trackType == C.TRACK_TYPE_AUDIO) {
                        buildAudioTrackMetadata(
                            formatLabel = format.label,
                            channelCount = format.channelCount,
                            sampleMimeType = format.sampleMimeType,
                        )
                    } else {
                        friendlySubtitleLanguageName(trackLanguage)
                    },
                    selectionId = selectionId,
                )
        }
    }

    return result
}

private fun buildAudioTrackLabel(
    formatLabel: String?,
    language: String?,
    fallbackIndex: Int,
): String {
    val languageName = friendlySubtitleLanguageName(language)
    if (languageName != "Unknown") return languageName

    val label = formatLabel?.trim().orEmpty()
    val labelLanguage = friendlySubtitleLanguageName(label)
    return when {
        labelLanguage != "Unknown" && label.length in 2..3 -> labelLanguage
        label.isNotBlank() -> label
        else -> "Audio track $fallbackIndex"
    }
}

private fun buildAudioTrackMetadata(
    formatLabel: String?,
    channelCount: Int,
    sampleMimeType: String?,
): String = buildList {
    when (channelCount) {
        1 -> add("Mono")
        2 -> add("Stereo")
        6 -> add("5.1")
        8 -> add("7.1")
        in 3..Int.MAX_VALUE -> add("$channelCount channels")
    }
    friendlyAudioCodec(sampleMimeType)?.let(::add)
    friendlyAudioVariant(formatLabel)?.let(::add)
}.distinct().joinToString(" • ")

private fun friendlyAudioCodec(value: String?): String? =
    when (value?.lowercase()) {
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3" -> "Dolby Digital Plus"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/opus" -> "Opus"
        "audio/flac" -> "FLAC"
        "audio/mpeg" -> "MP3"
        else -> value
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
    }

private fun friendlyAudioVariant(value: String?): String? {
    val label = value?.lowercase().orEmpty()
    return when {
        "commentary" in label -> "Commentary"
        "original" in label -> "Original"
        "dub" in label -> "Dub"
        "descriptive" in label || "description" in label -> "Audio description"
        else -> null
    }
}

private fun buildAudioSelectionId(
    language: String?,
    formatLabel: String?,
    channelCount: Int,
    sampleMimeType: String?,
    trackId: String?,
): String = listOf(
    "audio",
    canonicalSubtitleLanguage(language),
    formatLabel.orEmpty().trim().lowercase(),
    channelCount.toString(),
    sampleMimeType.orEmpty().lowercase(),
    trackId.orEmpty().lowercase(),
).joinToString(":")

private fun findSavedAudioTrack(
    tracks: List<PlayerTrackChoice>,
    savedSelection: String?,
): PlayerTrackChoice? {
    if (savedSelection.isNullOrBlank()) return null
    tracks.firstOrNull {
        it.selectionId == savedSelection
    }?.let { return it }

    val savedLanguage = savedSelection
        .split(':')
        .getOrNull(1)
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?: return null
    return tracks.firstOrNull {
        canonicalSubtitleLanguage(it.language) == savedLanguage
    }
}

@Composable
private fun PlayerTrackDialog(
    title: String,
    tracks: List<PlayerTrackChoice>,
    automaticLabel: String,
    offLabel: String?,
    offSelected: Boolean = false,
    emptyMessage: String,
    onAutomatic: () -> Unit,
    onOff: (() -> Unit)?,
    onSelect:
        (PlayerTrackChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerPanelWindow(
        title = title,
        subtitle = if (tracks.isEmpty()) {
            "No selectable tracks"
        } else {
            "${tracks.size} available"
        },
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item {
                PlayerTrackDialogRow(
                    label = automaticLabel,
                    selected =
                        tracks.none { it.selected } &&
                            !offSelected,
                    onClick = onAutomatic,
                )
            }

            if (offLabel != null && onOff != null) {
                item {
                    PlayerTrackDialogRow(
                        label = offLabel,
                        selected = offSelected,
                        onClick = onOff,
                    )
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Text(
                        emptyMessage,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 18.dp,
                        ),
                        color = Color.White.copy(alpha = .54f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            } else {
                items(
                    tracks,
                    key = { it.key },
                ) { track ->
                    PlayerTrackDialogRow(
                        label = track.label,
                        selected = track.selected,
                        onClick = { onSelect(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTrackDialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            VueoPlayerAccent.copy(alpha = .12f)
        } else {
            Color.White.copy(alpha = .035f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) {
                VueoPlayerAccent.copy(alpha = .42f)
            } else {
                Color.White.copy(alpha = .07f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 13.dp,
                vertical = 11.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        if (selected) {
                            VueoPlayerAccent
                        } else {
                            Color.White.copy(alpha = .36f)
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(VueoPlayerAccent),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Text(
                label,
                color = Color.White.copy(
                    alpha = if (selected) .96f else .76f
                ),
                fontSize = 12.sp,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun applyTrackChoice(
    player: ExoPlayer,
    trackType: Int,
    choice: PlayerTrackChoice,
) {
    player.trackSelectionParameters =
        player
            .trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(
                trackType,
                false,
            )
            .clearOverridesOfType(
                trackType
            )
            .setOverrideForType(
                choice.override
            )
            .build()
}

private fun clearTrackOverride(
    player: ExoPlayer,
    trackType: Int,
    disable: Boolean,
) {
    player.trackSelectionParameters =
        player
            .trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(
                trackType
            )
            .setTrackTypeDisabled(
                trackType,
                disable,
            )
            .build()
}

private fun PlayerVideoFit.toMedia3ResizeMode(): Int =
    when (this) {
        PlayerVideoFit.FIT ->
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerVideoFit.FILL ->
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        PlayerVideoFit.ZOOM ->
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

private fun friendlyPlaybackError(
    error: PlaybackException,
): String {
    val message =
        error.message
            ?.takeIf {
                it.isNotBlank()
            }
    val diagnostic = generateSequence<Throwable>(error) {
        it.cause
    }.take(6)
        .joinToString(" ") { cause ->
            "${cause.javaClass.simpleName} ${cause.message.orEmpty()}"
        }

    return when {
        diagnostic
            .contains(
                "403",
                ignoreCase = true,
            ) ->
            "The stream server rejected this request. Try another source."

        diagnostic
            .contains(
                "404",
                ignoreCase = true,
            ) ->
            "This stream is no longer available. Try another source."

        diagnostic
            .contains(
                "timeout",
                ignoreCase = true,
            ) ->
            "The stream took too long to respond. Retry or choose another source."

        diagnostic.contains(
            "UnknownHost",
            ignoreCase = true,
        ) || diagnostic.contains(
            "Network is unreachable",
            ignoreCase = true,
        ) ->
            "VUEO could not reach the stream server. Check your connection or choose another source."

        diagnostic
            .contains(
                "decoder",
                ignoreCase = true,
            ) ||
            diagnostic
                .contains(
                    "codec",
                    ignoreCase = true,
                ) ->
            "This device may not support the stream codec. Try another source."

        else ->
            message
                ?: "Playback failed. Retry or choose another source."
    }
}

private fun formatPlaybackTime(
    positionMs: Long,
): String {
    val totalSeconds =
        (
            positionMs /
                1_000L
        ).coerceAtLeast(0L)

    val hours =
        totalSeconds / 3_600L

    val minutes =
        (
            totalSeconds %
                3_600L
        ) / 60L

    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    } else {
        "%d:%02d".format(
            minutes,
            seconds,
        )
    }
}

private fun buildPlayerMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleTrack>,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    subtitlesOnByDefault: Boolean,
    autoSelectPreferred: Boolean,
    embeddedPriority: Boolean,
): Media3MediaItem {
    val normalizedPreferred =
        preferredLanguageCode
            ?.lowercase()

    val normalizedSecondary =
        secondaryLanguageCode
            ?.lowercase()

    val orderedSubtitles =
        subtitles
            .filter {
                it.url.startsWith(
                    "https://"
                )
            }
            .distinctBy {
                it.url
            }
            .sortedBy {
                subtitle ->

                subtitleLanguagePriority(
                    language =
                        subtitle.language,
                    preferred =
                        normalizedPreferred,
                    secondary =
                        normalizedSecondary,
                )
            }

    val subtitleConfigurations =
        orderedSubtitles
            .mapIndexed {
                index,
                subtitle ->

                val builder =
                    Media3MediaItem
                        .SubtitleConfiguration
                        .Builder(
                            Uri.parse(
                                subtitle.url
                            )
                        )
                        .setId(
                            subtitle.id
                        )
                        .setLabel(
                            PLAYER_SUBTITLE_LABEL_PREFIX +
                                subtitle.id
                        )
                        .setLanguage(
                            subtitle.language
                        )
                        .setMimeType(
                            subtitleMimeType(
                                subtitle.url
                            )
                        )

                val shouldMarkDefault =
                    subtitlesOnByDefault &&
                        autoSelectPreferred &&
                        !embeddedPriority &&
                        (
                            subtitleLanguagePriority(
                                language =
                                    subtitle.language,
                                preferred =
                                    normalizedPreferred,
                                secondary =
                                    normalizedSecondary,
                            ) == 0 ||
                                (
                                    normalizedPreferred == null &&
                                        index == 0
                                )
                        )

                if (shouldMarkDefault) {
                    builder.setSelectionFlags(
                        C.SELECTION_FLAG_DEFAULT
                    )
                }

                builder.build()
            }

    return Media3MediaItem
        .Builder()
        .setUri(
            Uri.parse(sourceUrl)
        )
        .setSubtitleConfigurations(
            subtitleConfigurations
        )
        .build()
}

private fun subtitleLanguagePriority(
    language: String,
    preferred: String?,
    secondary: String?,
): Int {
    val normalized =
        language
            .trim()
            .lowercase()

    return when {
        preferred != null &&
            (
                normalized == preferred ||
                    normalized.startsWith(
                        "$preferred-"
                    )
            ) -> 0

        secondary != null &&
            (
                normalized == secondary ||
                    normalized.startsWith(
                        "$secondary-"
                    )
            ) -> 1

        else -> 2
    }
}

private fun subtitleMimeType(
    url: String,
): String =
    when (
        url.substringBefore("?")
            .substringAfterLast(
                ".",
                "",
            )
            .lowercase()
    ) {
        "vtt" ->
            MimeTypes.TEXT_VTT

        "ssa",
        "ass" ->
            MimeTypes.TEXT_SSA

        "ttml",
        "xml" ->
            MimeTypes.APPLICATION_TTML

        else ->
            MimeTypes.APPLICATION_SUBRIP
    }

private fun selectedVideoId(
    media: MediaItem,
    episode: EpisodeItem?,
): String? =
    if (media.type == "series") {
        episode?.id
    } else {
        media.id
    }

private fun playbackTitle(
    media: MediaItem,
    episode: EpisodeItem?,
): String =
    if (episode == null) {
        media.name
    } else {
        "${media.name} • S${episode.season}E${episode.episode} • ${episode.title}"
    }

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Surface(
            shape =
                RoundedCornerShape(
                    50
                ),
            color =
                VueoPalette
                    .SurfaceElevated,
        ) {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription =
                        "Back",
                    tint = Color.White,
                )
            }
        }

        Spacer(
            Modifier.width(12.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight =
                    FontWeight.Black,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Text(
                subtitle,
                color =
                    VueoPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }

        action?.invoke()
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            )
        }
    }
}
