package com.vueo.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.data.TvCatalogRow
import com.vueo.tv.data.TvBrowseKind
import com.vueo.tv.data.TvHomeData
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.detail.TvDetailRepository
import com.vueo.tv.detail.TvDetailScreen
import com.vueo.tv.library.TvLibraryScreen
import com.vueo.tv.home.TvHomeScreenV2
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackRequest
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.player.TvPlayerScreen
import com.vueo.tv.player.TvSourceEngine
import com.vueo.tv.player.TvSourcePickerScreen
import com.vueo.tv.profile.TvProfileDnaPanel
import com.vueo.tv.profile.TvProfilePickerScreen
import com.vueo.tv.profile.TvUserHubScreen
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.content.TvContentManagerScreen
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.search.TvSearchRepository
import com.vueo.tv.search.TvSearchScreen
import com.vueo.tv.ui.focus.TvFocusMemory
import com.vueo.tv.ui.focus.TvFocusZone
import com.vueo.tv.ui.focus.tvHorizontalEdgeGuard
import com.vueo.tv.ui.focus.tvVerticalFocus
import com.vueo.tv.ui.motion.tvImmediateCut
import com.vueo.tv.ui.motion.tvPlayerFadeThrough
import com.vueo.tv.ui.motion.tvScreenFadeThrough
import com.vueo.tv.ui.theme.LocalTvAccent
import com.vueo.tv.update.VueoTvUpdateManager
import com.vueo.tv.update.VueoTvUpdateRelease
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvFocusColorSpec

private val VueoBlack = Color(0xFF050706)
private val VueoPanel = Color(0xFF101412)
private val VueoYellow = Color(0xFFD6FF00)
private val VueoMuted = Color(0xFFAAB2AD)
private const val TV_UPDATER_ENABLED = true
private const val HOME_HERO_IDLE_ROTATE_MS = 18_000L

internal val TV_TOP_NAV_LABELS =
    listOf("Search", "Library", "Home", "Movie", "Series", "Anime")

private enum class TvRootScreen {
    STARTUP,
    HOME,
    SEARCH,
    LIBRARY,
    MOVIE,
    SERIES,
    ANIME,
    CONTENT_MANAGER,
    USER_HUB,
    PROFILE_PICKER,
    DETAIL,
    SOURCE_PICKER,
    PLAYER,
}

private fun TvRootScreen.isTopLevelBrowse(): Boolean =
    this == TvRootScreen.HOME ||
        this == TvRootScreen.SEARCH ||
        this == TvRootScreen.LIBRARY ||
        this == TvRootScreen.MOVIE ||
        this == TvRootScreen.SERIES ||
        this == TvRootScreen.ANIME

@Composable
fun VueoTvApp() {
    val context = LocalContext.current
    var updateRelease by remember { mutableStateOf<VueoTvUpdateRelease?>(null) }
    var updateVisible by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var homeFocusRestoreToken by remember { mutableStateOf(0) }
    var currentScreen by remember { mutableStateOf(TvRootScreen.STARTUP) }
    var detailMedia by remember { mutableStateOf<TvMediaItem?>(null) }
    var detailHistory by remember { mutableStateOf<List<TvMediaItem>>(emptyList()) }
    var playbackRequest by remember { mutableStateOf<TvPlaybackRequest?>(null) }
    var selectedSource by remember { mutableStateOf<SourceCandidate?>(null) }
    var selectedSubtitles by remember { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var detailReturnScreen by remember { mutableStateOf(TvRootScreen.HOME) }
    var playbackReturnScreen by remember { mutableStateOf(TvRootScreen.DETAIL) }
    var searchFocusRestoreToken by remember { mutableStateOf(0) }
    var libraryFocusRestoreToken by remember { mutableStateOf(0) }
    var browseFocusRestoreToken by remember { mutableStateOf(0) }
    var profileDnaVisible by remember { mutableStateOf(false) }
    var settingsInitialCategory by remember { mutableStateOf<String?>(null) }
    var settingsReturnScreen by remember { mutableStateOf(TvRootScreen.HOME) }
    var profileFocusReturnToken by remember { mutableStateOf(0) }
    val libraryStore =
        remember(context) {
            TvLibraryStore(context.applicationContext)
        }
    val profileStore =
        remember(context) {
            ProfileStore(context.applicationContext)
        }
    val settingsStore =
        remember(context) {
            SettingsStore(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }
    var appAccent by remember { mutableStateOf(settingsStore.appAccent()) }
    val contentManagerStore =
        remember(context) {
            TvContentManagerStore(context.applicationContext)
        }
    val sourceEngine =
        remember(context, contentManagerStore) {
            TvSourceEngine(context.applicationContext, contentManagerStore)
        }
    val playbackStore =
        remember(context) {
            TvPlaybackStore(context.applicationContext)
        }
    val searchRepository =
        remember(context) {
            TvSearchRepository(context.applicationContext)
        }
    val detailRepository =
        remember(context) {
            TvDetailRepository(context.applicationContext)
        }

    val navigate: (String) -> Unit = { label ->
        when (label) {
            "Profile" -> {
                if (profileDnaVisible) {
                    profileDnaVisible = false
                    profileFocusReturnToken += 1
                } else {
                    profileDnaVisible = true
                }
            }

            "Settings" -> {
                currentScreen = settingsReturnScreen
                profileDnaVisible = true
            }

            else -> {
                val nextScreen =
                    when (label) {
                        "Home" -> TvRootScreen.HOME
                        "Search" -> TvRootScreen.SEARCH
                        "Library" -> TvRootScreen.LIBRARY
                        "Movie" -> TvRootScreen.MOVIE
                        "Series" -> TvRootScreen.SERIES
                        "Anime" -> TvRootScreen.ANIME
                        "Content Manager" -> TvRootScreen.CONTENT_MANAGER
                        else -> currentScreen
                    }
                if (nextScreen != currentScreen) {
                    profileDnaVisible = false
                    settingsInitialCategory = null
                    detailMedia = null
                    detailHistory = emptyList()
                    playbackRequest = null
                    selectedSource = null
                    selectedSubtitles = emptyList()
                    currentScreen = nextScreen
                }
            }
        }
    }

    LaunchedEffect(profileFocusReturnToken) {
        if (profileFocusReturnToken > 0) {
            delay(220)
            runCatching { TvTopNavProfileFocus.requester?.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        if (TV_UPDATER_ENABLED && settingsStore.automaticUpdateChecksEnabled()) {
            VueoTvUpdateManager.check(
                context = context.applicationContext,
                force = false,
            ) { result ->
                val release = result.release
                if (release != null && VueoTvUpdateManager.isNewerThanCurrent(context, release)) {
                    updateRelease = release
                    updateVisible = true
                    updateError = null
                }
            }
        }
    }

    LaunchedEffect(profileStore) {
        currentScreen =
            if (profileStore.shouldShowPickerOnStartup()) {
                TvRootScreen.PROFILE_PICKER
            } else {
                TvRootScreen.HOME
            }
    }

    val tvAccent = Color(appAccent.argb)
    CompositionLocalProvider(LocalTvAccent provides tvAccent) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = tvAccent),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = VueoBlack,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        when {
                            initialState.isTopLevelBrowse() && targetState.isTopLevelBrowse() ->
                                tvImmediateCut()

                            initialState == TvRootScreen.PLAYER || targetState == TvRootScreen.PLAYER ->
                                tvPlayerFadeThrough()

                            else -> tvScreenFadeThrough()
                        }
                    },
                    label = "tvRootScreen",
                ) { screen ->
                when (screen) {
                    TvRootScreen.STARTUP ->
                        Box(
                            modifier = Modifier.fillMaxSize().background(VueoBlack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "VUEO",
                                color = VueoYellow,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }

                    TvRootScreen.HOME ->
                        TvHomeScreenV2(
                            focusRestoreToken = homeFocusRestoreToken,
                            onNavigate = navigate,
                            libraryStore = libraryStore,
                            profileStore = profileStore,
                            onOpenMedia = { media ->
                                detailHistory = emptyList()
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.HOME
                                currentScreen = TvRootScreen.DETAIL
                            },
                            onPlayMedia = { media ->
                                if (media.type.equals("movie", ignoreCase = true)) {
                                    playbackReturnScreen = TvRootScreen.HOME
                                    playbackRequest =
                                        TvPlaybackRequest(
                                            media = media,
                                            videoId = media.id,
                                        )
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.SOURCE_PICKER
                                } else {
                                    detailHistory = emptyList()
                                    detailMedia = media
                                    detailReturnScreen = TvRootScreen.HOME
                                    currentScreen = TvRootScreen.DETAIL
                                }
                            },
                            onResumeEntry = { entry ->
                                playbackReturnScreen = TvRootScreen.HOME
                                playbackRequest = entry.toTvPlaybackRequest()
                                selectedSource = null
                                selectedSubtitles = emptyList()
                                currentScreen = TvRootScreen.SOURCE_PICKER
                            },
                            onExitApp = { context.findActivity()?.finish() },
                        )

                    TvRootScreen.SEARCH ->
                        TvSearchScreen(
                            repository = searchRepository,
                            focusRestoreToken = searchFocusRestoreToken,
                            onNavigate = navigate,
                            onOpenMedia = { media ->
                                detailHistory = emptyList()
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.SEARCH
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )

                    TvRootScreen.LIBRARY ->
                        TvLibraryScreen(
                            store = libraryStore,
                            focusRestoreToken = libraryFocusRestoreToken,
                            onNavigate = navigate,
                            onOpenMedia = { media ->
                                detailHistory = emptyList()
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.LIBRARY
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )

                    TvRootScreen.MOVIE,
                    TvRootScreen.SERIES,
                    TvRootScreen.ANIME -> {
                        val kind =
                            when (screen) {
                                TvRootScreen.MOVIE -> TvBrowseKind.MOVIE
                                TvRootScreen.SERIES -> TvBrowseKind.SERIES
                                else -> TvBrowseKind.ANIME
                            }
                        TvBrowseScreen(
                            kind = kind,
                            focusRestoreToken = browseFocusRestoreToken,
                            onNavigate = navigate,
                            onOpenMedia = { media ->
                                detailHistory = emptyList()
                                detailMedia = media
                                detailReturnScreen = screen
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )
                    }

                    TvRootScreen.CONTENT_MANAGER ->
                        TvContentManagerScreen(
                            store = contentManagerStore,
                            onNavigate = navigate,
                            onBack = {
                                currentScreen = settingsReturnScreen
                                profileDnaVisible = true
                            },
                        )

                    TvRootScreen.PROFILE_PICKER ->
                        TvProfilePickerScreen(
                            profileStore = profileStore,
                            onProfileSelected = {
                                TvFocusMemory.resetToHero()
                                homeFocusRestoreToken += 1
                                searchFocusRestoreToken += 1
                                libraryFocusRestoreToken += 1
                                currentScreen = TvRootScreen.HOME
                            },
                            onProfilesChanged = {
                                TvFocusMemory.resetToHero()
                                homeFocusRestoreToken += 1
                                searchFocusRestoreToken += 1
                                libraryFocusRestoreToken += 1
                            },
                        )

                    TvRootScreen.USER_HUB ->
                        TvUserHubScreen(
                            profileStore = profileStore,
                            settingsStore = settingsStore,
                            libraryStore = libraryStore,
                            initialCategoryKey = settingsInitialCategory,
                            onExitToPanel = {
                                settingsInitialCategory = null
                                currentScreen = settingsReturnScreen
                                profileDnaVisible = true
                            },
                            onNavigate = navigate,
                            onProfileChanged = {
                                appAccent = settingsStore.appAccent()
                                TvFocusMemory.resetToHero()
                                homeFocusRestoreToken += 1
                                searchFocusRestoreToken += 1
                                libraryFocusRestoreToken += 1
                            },
                            onAccentChanged = {
                                appAccent = settingsStore.appAccent()
                            },
                            onResume = { entry ->
                                playbackReturnScreen = TvRootScreen.USER_HUB
                                playbackRequest = entry.toTvPlaybackRequest()
                                selectedSource = null
                                selectedSubtitles = emptyList()
                                currentScreen = TvRootScreen.SOURCE_PICKER
                            },
                            onCheckForUpdates = { report ->
                                VueoTvUpdateManager.check(
                                    context = context.applicationContext,
                                    force = true,
                                ) { result ->
                                    val release = result.release
                                    when {
                                        !result.error.isNullOrBlank() -> {
                                            report(result.error)
                                        }
                                        release != null &&
                                            VueoTvUpdateManager.isNewerThanCurrent(context, release) -> {
                                            updateRelease = release
                                            updateVisible = true
                                            updateError = null
                                            report("Update ${release.versionName} available")
                                        }
                                        else -> {
                                            report("VUEO TV is up to date.")
                                        }
                                    }
                                }
                            },
                        )

                    TvRootScreen.DETAIL -> {
                        val media = detailMedia
                        if (media != null) {
                            TvDetailScreen(
                                seed = media,
                                repository = detailRepository,
                                onNavigate = navigate,
                                isInMyList = libraryStore.contains(media),
                                onToggleMyList = { libraryStore.toggle(media) },
                                onPlay = { request ->
                                    playbackReturnScreen = TvRootScreen.DETAIL
                                    playbackRequest = request
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.SOURCE_PICKER
                                },
                                onOpenMedia = { related ->
                                    detailMedia?.let { current ->
                                        detailHistory = detailHistory + current
                                    }
                                    detailMedia = related
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.DETAIL
                                },
                                onBack = {
                                    if (detailHistory.isNotEmpty()) {
                                        detailMedia = detailHistory.last()
                                        detailHistory = detailHistory.dropLast(1)
                                        playbackRequest = null
                                        selectedSource = null
                                        selectedSubtitles = emptyList()
                                        currentScreen = TvRootScreen.DETAIL
                                    } else {
                                        val target = detailReturnScreen
                                        detailMedia = null
                                        playbackRequest = null
                                        selectedSource = null
                                        selectedSubtitles = emptyList()
                                        currentScreen = target
                                        when (target) {
                                            TvRootScreen.SEARCH -> searchFocusRestoreToken += 1
                                            TvRootScreen.LIBRARY -> libraryFocusRestoreToken += 1
                                            TvRootScreen.MOVIE,
                                            TvRootScreen.SERIES,
                                            TvRootScreen.ANIME -> browseFocusRestoreToken += 1
                                            else -> homeFocusRestoreToken += 1
                                        }
                                    }
                                },
                            )
                        }
                    }

                    TvRootScreen.SOURCE_PICKER -> {
                        val request = playbackRequest
                        if (request != null) {
                            TvSourcePickerScreen(
                                request = request,
                                sourceEngine = sourceEngine,
                                onPlay = { source, subtitles ->
                                    selectedSource = source
                                    selectedSubtitles = subtitles
                                    currentScreen = TvRootScreen.PLAYER
                                },
                                onBack = {
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = playbackReturnScreen
                                },
                            )
                        }
                    }

                    TvRootScreen.PLAYER -> {
                        val request = playbackRequest
                        val source = selectedSource
                        if (request != null && source != null) {
                            TvPlayerScreen(
                                request = request,
                                initialSource = source,
                                externalSubtitles = selectedSubtitles,
                                sourceEngine = sourceEngine,
                                playbackStore = playbackStore,
                                onPlayRequest = { next ->
                                    playbackRequest = next
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.SOURCE_PICKER
                                },
                                onBack = {
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = playbackReturnScreen
                                },
                            )
                        }
                    }
                }
                }

                TvProfileDnaPanel(
                        visible = profileDnaVisible,
                        profileStore = profileStore,
                        settingsStore = settingsStore,
                        onDismiss = {
                            profileDnaVisible = false
                            profileFocusReturnToken += 1
                        },
                        onSwitchProfile = {
                            profileDnaVisible = false
                            currentScreen = TvRootScreen.PROFILE_PICKER
                        },
                        onOpenSettings = { categoryKey ->
                            settingsReturnScreen = currentScreen
                            settingsInitialCategory = categoryKey
                            profileDnaVisible = false
                            currentScreen = TvRootScreen.USER_HUB
                        },
                        onOpenContentManager = {
                            settingsReturnScreen = currentScreen
                            profileDnaVisible = false
                            currentScreen = TvRootScreen.CONTENT_MANAGER
                        },
                    )

                val release = updateRelease
                if (updateVisible && release != null) {
                    TvUpdateOverlay(
                        release = release,
                        downloading = updateDownloading,
                        progress = updateProgress,
                        error = updateError,
                        onLater = {
                            if (!updateDownloading) {
                                updateVisible = false
                                homeFocusRestoreToken += 1
                            }
                        },
                        onUpdateNow = {
                            if (VueoTvUpdateManager.needsInstallPermission(context)) {
                                updateError =
                                    "Allow VUEO TV to install unknown apps, then return and choose Update Now again."
                                runCatching {
                                    VueoTvUpdateManager.openInstallPermissionSettings(context)
                                }.onFailure {
                                    updateError = it.message ?: "Unable to open install permission settings."
                                }
                            } else if (!updateDownloading) {
                                updateDownloading = true
                                updateProgress = 0
                                updateError = null

                                VueoTvUpdateManager.downloadAndInstall(
                                    context = context.applicationContext,
                                    release = release,
                                    onProgress = { updateProgress = it },
                                ) { result ->
                                    updateDownloading = false
                                    result.onFailure { failure ->
                                        updateError =
                                            failure.message ?: "Unable to install the TV update."
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun VueoTvHome(
    focusRestoreToken: Int,
    onNavigate: (String) -> Unit,
    libraryStore: TvLibraryStore,
    onOpenMedia: (TvMediaItem) -> Unit,
    onPlayMedia: (TvMediaItem) -> Unit,
    onResumeEntry: (LibraryPlaybackEntry) -> Unit,
) {
    val context = LocalContext.current
    val repository =
        remember(context) {
            TvHomeRepository(context.applicationContext)
        }
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS.associateWith { FocusRequester() }
        }
    val heroPlayRequester = remember { FocusRequester() }
    val heroMoreInfoRequester = remember { FocusRequester() }

    var home by remember { mutableStateOf(repository.cached()) }
    var selectedHero by remember { mutableStateOf(home?.hero) }
    var loading by remember { mutableStateOf(home == null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    var exitConfirmVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = !exitConfirmVisible) {
        exitConfirmVisible = true
        interactionNonce += 1
    }
    BackHandler(enabled = exitConfirmVisible) {
        exitConfirmVisible = false
        interactionNonce += 1
    }

    LaunchedEffect(refreshNonce) {
        val current = home
        if (refreshNonce == 0 && !repository.shouldRefresh(current)) {
            loading = false
            return@LaunchedEffect
        }

        loading = current == null
        runCatching { repository.refresh() }
            .onSuccess { fresh ->
                val restoredHero =
                    if (TvFocusMemory.lastZone == TvFocusZone.Rail) {
                        val rememberedMedia = TvFocusMemory.lastMediaKey
                        rememberedMedia?.let { mediaKey ->
                            libraryStore.continueWatching()
                                .firstOrNull {
                                    "${it.media.type}:${it.media.id}" == mediaKey
                                }
                                ?.media
                                ?: fresh.rows
                                    .asSequence()
                                    .flatMap { it.items.asSequence() }
                                    .firstOrNull { "${it.type}:${it.id}" == mediaKey }
                        }
                    } else {
                        null
                    }

                home = fresh
                selectedHero = restoredHero ?: fresh.hero
                refreshError = null
            }
            .onFailure {
                refreshError =
                    if (home == null) {
                        "Unable to load VUEO catalogs"
                    } else {
                        "Offline. Showing saved Home."
                    }
            }

        loading = false
    }

    LaunchedEffect(home, interactionNonce) {
        val currentHome = home ?: return@LaunchedEffect
        while (true) {
            delay(HOME_HERO_IDLE_ROTATE_MS)
            if (exitConfirmVisible || TvFocusMemory.lastZone == TvFocusZone.Rail) {
                continue
            }

            val candidates =
                currentHome.rows
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .filter { !it.background.isNullOrBlank() }
                    .distinctBy { "${it.type}:${it.id}" }
                    .take(8)
                    .toList()

            if (candidates.size > 1) {
                val currentKey = selectedHero?.let { "${it.type}:${it.id}" }
                val currentIndex = candidates.indexOfFirst { "${it.type}:${it.id}" == currentKey }
                selectedHero = candidates[(currentIndex + 1) % candidates.size]
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VueoBlack)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && !exitConfirmVisible) {
                        interactionNonce += 1
                    }
                    false
                },
    ) {
        when {
            home != null && selectedHero != null -> {
                HomeContent(
                    home = home!!,
                    hero = selectedHero!!,
                    continueWatching = libraryStore.continueWatching(),
                    navRequesters = navRequesters,
                    heroPlayRequester = heroPlayRequester,
                    heroMoreInfoRequester = heroMoreInfoRequester,
                    focusRestoreToken = focusRestoreToken,
                    refreshError = refreshError,
                    onUserInteraction = { interactionNonce += 1 },
                    onCardFocused = { selectedHero = it },
                    onOpenMedia = onOpenMedia,
                    onPlayMedia = onPlayMedia,
                    onResumeEntry = onResumeEntry,
                )
            }

            loading -> LoadingHome()

            else -> ErrorHome(
                message = refreshError ?: "Unable to load VUEO catalogs",
                onRetry = { refreshNonce += 1 },
            )
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = heroPlayRequester,
            selectedLabel = "Home",
            onSelected = onNavigate,
        )

        if (exitConfirmVisible) {
            TvHomeExitOverlay(
                onStay = {
                    exitConfirmVisible = false
                    interactionNonce += 1
                },
                onExit = {
                    context.findActivity()?.finish()
                },
            )
        }
    }
}

@Composable
private fun HomeContent(
    home: TvHomeData,
    hero: TvMediaItem,
    continueWatching: List<LibraryPlaybackEntry>,
    navRequesters: Map<String, FocusRequester>,
    heroPlayRequester: FocusRequester,
    heroMoreInfoRequester: FocusRequester,
    focusRestoreToken: Int,
    refreshError: String?,
    onUserInteraction: () -> Unit,
    onCardFocused: (TvMediaItem) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
    onPlayMedia: (TvMediaItem) -> Unit,
    onResumeEntry: (LibraryPlaybackEntry) -> Unit,
) {
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val continueRowId = "continue-watching"
    val contentRowIds =
        remember(home.rows, continueWatching) {
            buildList {
                if (continueWatching.isNotEmpty()) add(continueRowId)
                addAll(home.rows.map { it.id })
            }
        }
    val rowKey = remember(contentRowIds) { contentRowIds.joinToString("|") }
    val rowEntryRequesters =
        remember(rowKey) {
            contentRowIds.associateWith { FocusRequester() }
        }
    val firstRowRequester = contentRowIds.firstOrNull()?.let(rowEntryRequesters::get)
    val homeNavRequester = navRequesters.getValue("Home")
    val railStartIndex = if (refreshError != null) 2 else 1
    val catalogRowOffset = if (continueWatching.isNotEmpty()) 1 else 0
    var lastVerticalRow by remember { mutableStateOf<String?>(null) }
    var preferredRailColumn by remember { mutableIntStateOf(TvFocusMemory.lastRailColumn) }

    val heroResumeEntry =
        remember(hero.type, hero.id, continueWatching) {
            continueWatching.firstOrNull {
                it.media.type.equals(hero.type, ignoreCase = true) && it.media.id == hero.id
            }
        }

    LaunchedEffect(rowKey, focusRestoreToken) {
        delay(90)
        val target =
            when (TvFocusMemory.lastZone) {
                TvFocusZone.Nav ->
                    navRequesters[TvFocusMemory.lastNavLabel] ?: homeNavRequester

                TvFocusZone.Hero -> {
                    columnState.scrollToItem(0)
                    if (TvFocusMemory.lastHeroAction == 1) {
                        heroMoreInfoRequester
                    } else {
                        heroPlayRequester
                    }
                }

                TvFocusZone.Rail -> {
                    val rememberedRowId = TvFocusMemory.lastRowId
                    val rememberedRowIndex = contentRowIds.indexOf(rememberedRowId)
                    if (rememberedRowIndex >= 0) {
                        columnState.scrollToItem(railStartIndex + rememberedRowIndex)
                        delay(70)
                    }
                    rememberedRowId?.let(rowEntryRequesters::get) ?: heroPlayRequester
                }
            }

        runCatching { target.requestFocus() }
    }

    LazyColumn(
        state = columnState,
        modifier =
            Modifier
                .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp),
    ) {
        item {
            Hero(
                item = hero,
                playRequester = heroPlayRequester,
                moreInfoRequester = heroMoreInfoRequester,
                upRequester = homeNavRequester,
                downRequester = firstRowRequester,
                providerName = home.providerName,
                resumeEntry = heroResumeEntry,
                onPlay = {
                    heroResumeEntry?.let(onResumeEntry) ?: onPlayMedia(hero)
                },
                onMoreInfo = { onOpenMedia(hero) },
                onFocused = {
                    onUserInteraction()
                    if (lastVerticalRow != null) {
                        lastVerticalRow = null
                        scope.launch { columnState.animateScrollToItem(0) }
                    }
                },
            )
        }

        refreshError?.let { message ->
            item {
                Text(
                    text = message,
                    color = VueoMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 58.dp, vertical = 5.dp),
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = continueRowId) {
                TvContinueWatchingRail(
                    entries = continueWatching,
                    entryRequester = rowEntryRequesters.getValue(continueRowId),
                    upRequester = heroPlayRequester,
                    downRequester = home.rows.firstOrNull()?.let { rowEntryRequesters[it.id] },
                    preferredIndex = preferredRailColumn,
                    onFocused = { entry, index ->
                        preferredRailColumn = index
                        onUserInteraction()
                        onCardFocused(entry.media)
                        TvFocusMemory.rememberRail(
                            rowId = continueRowId,
                            itemIndex = index,
                            mediaKey = "${entry.media.type}:${entry.media.id}",
                        )
                        if (lastVerticalRow != continueRowId) {
                            lastVerticalRow = continueRowId
                            scope.launch { columnState.animateScrollToItem(railStartIndex) }
                        }
                    },
                    onResume = onResumeEntry,
                )
            }
        }

        home.rows.forEachIndexed { rowIndex, row ->
            val contentIndex = rowIndex + catalogRowOffset
            val upRequester =
                if (contentIndex == 0) {
                    heroPlayRequester
                } else {
                    contentRowIds.getOrNull(contentIndex - 1)?.let(rowEntryRequesters::get)
                }
            val downRequester =
                contentRowIds.getOrNull(contentIndex + 1)?.let(rowEntryRequesters::get)
            val entryRequester = rowEntryRequesters.getValue(row.id)

            item(key = row.id) {
                TvRail(
                    row = row,
                    entryRequester = entryRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    preferredIndex = preferredRailColumn,
                    onCardFocused = { item, index ->
                        preferredRailColumn = index
                        onUserInteraction()
                        onCardFocused(item)
                        if (lastVerticalRow != row.id) {
                            lastVerticalRow = row.id
                            scope.launch {
                                columnState.animateScrollToItem(
                                    index = railStartIndex + contentIndex,
                                )
                            }
                        }
                    },
                    onOpenMedia = onOpenMedia,
                )
            }
        }
    }
}

@Composable
internal fun TvTopNav(
    navRequesters: Map<String, FocusRequester>,
    contentDownRequester: FocusRequester,
    selectedLabel: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VueoBlack,
                            VueoBlack.copy(alpha = 0.94f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(34.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSearchNavItem(
                    selected = selectedLabel == "Search",
                    requester = navRequesters.getValue("Search"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Search") },
                )
                TvLibraryNavItem(
                    selected = selectedLabel == "Library",
                    requester = navRequesters.getValue("Library"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Library") },
                )
                TvNavItem(
                    label = "Home",
                    selected = selectedLabel == "Home",
                    requester = navRequesters.getValue("Home"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Home") },
                )
                TvNavItem(
                    label = "Movie",
                    selected = selectedLabel == "Movie",
                    requester = navRequesters.getValue("Movie"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Movie") },
                )
                TvNavItem(
                    label = "Series",
                    selected = selectedLabel == "Series",
                    requester = navRequesters.getValue("Series"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Series") },
                )
                TvNavItem(
                    label = "Anime",
                    selected = selectedLabel == "Anime",
                    requester = navRequesters.getValue("Anime"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Anime") },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvClock()
            TvProfileNavItem(
                downRequester = contentDownRequester,
                onClick = { onSelected("Profile") },
            )
        }
    }
}

private object TvTopNavProfileFocus {
    var requester: FocusRequester? = null
}

@Composable
private fun TvClock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    val pattern =
        remember(context) {
            if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        }
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000L)
        }
    }

    Text(
        text = formatter.format(now),
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TvSearchNavItem(
    selected: Boolean,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "searchNavScale",
    )

    Box(
        modifier =
            Modifier
                .width(42.dp)
                .height(42.dp)
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) TvFocusMemory.rememberNav("Search")
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    color =
                        when {
                            selected -> Color.White.copy(alpha = 0.16f)
                            focused -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                    shape = CircleShape,
                )
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(20.dp).height(20.dp)) {
            val stroke = 2.2.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.31f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.43f),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.64f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.88f),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun TvLibraryNavItem(
    selected: Boolean,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "libraryNavScale",
    )

    Box(
        modifier =
            Modifier
                .width(42.dp)
                .height(42.dp)
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) TvFocusMemory.rememberNav("Library")
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    color =
                        when {
                            selected -> Color.White.copy(alpha = 0.16f)
                            focused -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                    shape = CircleShape,
                )
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(21.dp).height(21.dp)) {
            val stroke = 1.9.dp.toPx()
            val spineWidth = size.width * 0.20f
            val gap = size.width * 0.08f
            val top = size.height * 0.16f
            val bookHeight = size.height * 0.68f
            val startX = size.width * 0.12f

            repeat(3) { index ->
                val left = startX + index * (spineWidth + gap)
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(spineWidth, bookHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8.dp.toPx()),
                    style = Stroke(width = stroke),
                )
            }
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.88f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.88f),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun TvProfileNavItem(
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "profileNavScale",
    )

    LaunchedEffect(requester) {
        TvTopNavProfileFocus.requester = requester
    }

    Box(
        modifier =
            Modifier
                .width(44.dp)
                .height(44.dp)
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        TvFocusMemory.rememberNav("Profile")
                    }
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    color = if (focused) Color.White.copy(alpha = 0.18f) else VueoPanel,
                    shape = CircleShape,
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(24.dp).height(24.dp)) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.18f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.34f),
            )
            drawArc(
                color = Color.White,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.47f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.48f),
            )
        }
    }
}

@Composable
private fun TvNavItem(
    label: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (focused || selected) Color.White else VueoMuted,
        animationSpec = tvFocusColorSpec(),
        label = "navColor",
    )
    val scale by animateFloatAsState(
        if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "navScale",
    )

    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        TvFocusMemory.rememberNav(label)
                    }
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    color = if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun Hero(
    item: TvMediaItem,
    playRequester: FocusRequester,
    moreInfoRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    providerName: String,
    resumeEntry: LibraryPlaybackEntry?,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    onFocused: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(500.dp)
                .background(VueoBlack),
    ) {
        AnimatedContent(
            targetState = item.background ?: item.poster,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                tvPlayerFadeThrough(
                    enterDurationMillis = 320,
                    exitDurationMillis = 150,
                    enterDelayMillis = 18,
                )
            },
            label = "homeHeroBackdrop",
        ) { imageUrl ->
            TvNetworkImage(
                url = imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    VueoBlack,
                                    VueoBlack.copy(alpha = 0.94f),
                                    VueoBlack.copy(alpha = 0.58f),
                                    Color.Transparent,
                                    Color.Transparent,
                                ),
                        )
                    ),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                VueoBlack.copy(alpha = 0.18f),
                                Color.Transparent,
                                Color.Transparent,
                                VueoBlack.copy(alpha = 0.72f),
                                VueoBlack,
                            )
                        )
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 64.dp, end = 44.dp, top = 42.dp)
                    .fillMaxWidth(0.47f),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = heroMeta(item),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text =
                    item.description
                        ?: item.genres.take(3).joinToString(" • ")
                            .ifBlank { "Available from $providerName" },
                color = VueoMuted,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvHeroButton(
                    text = if (resumeEntry != null) "▶  Resume" else "▶  Play",
                    primary = true,
                    requester = playRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 0,
                    onFocused = onFocused,
                    onClick = onPlay,
                )
                TvHeroButton(
                    text = "More Info",
                    requester = moreInfoRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 1,
                    onFocused = onFocused,
                    onClick = onMoreInfo,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text =
                    if (resumeEntry != null) {
                        val percent = (resumeEntry.progressFraction * 100).toInt().coerceIn(1, 99)
                        "$percent% watched  •  $providerName"
                    } else {
                        providerName
                    },
                color = VueoMuted.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
        }
    }
}

private fun heroMeta(item: TvMediaItem): String =
    buildList {
        add(item.displayType)
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        item.imdbRating?.let {
            add("IMDb ★ ${String.format("%.1f", it)}")
        }
    }.joinToString("  •  ")

@Composable
private fun TvHeroButton(
    text: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    actionIndex: Int,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(), label = "heroButtonScale")

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(
                    up = upRequester,
                    down = downRequester,
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        TvFocusMemory.rememberHero(actionIndex)
                        onFocused()
                    }
                }
                .scale(scale)
                .border(
                    width = 1.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (primary) TvAccent
                    else Color.White.copy(alpha = if (focused) 0.22f else 0.12f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 13.dp),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvContinueWatchingRail(
    entries: List<LibraryPlaybackEntry>,
    entryRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    preferredIndex: Int,
    onFocused: (LibraryPlaybackEntry, Int) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
) {
    val rememberedIndex = TvFocusMemory.railIndex("continue-watching", entries.size)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (rememberedIndex - 1).coerceAtLeast(0),
        )
    val scope = rememberCoroutineScope()
    var entryIndex by remember(entries.size) { mutableIntStateOf(rememberedIndex) }

    LaunchedEffect(preferredIndex, entries.size) {
        if (entries.isNotEmpty()) {
            val targetIndex = preferredIndex.coerceIn(0, entries.lastIndex)
            if (entryIndex != targetIndex) {
                entryIndex = targetIndex
                listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
            }
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Continue Watching",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your progress",
                color = VueoMuted.copy(alpha = 0.68f),
                fontSize = 12.sp,
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.mediaKey },
            ) { index, entry ->
                val entryModifier =
                    (if (index == entryIndex) {
                        Modifier.focusRequester(entryRequester)
                    } else {
                        Modifier
                    }).tvHorizontalEdgeGuard(
                        blockLeft = index == 0,
                        blockRight = index == entries.lastIndex,
                    )

                TvContinueWatchingCard(
                    entry = entry,
                    modifier = entryModifier,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        entryIndex = index
                        onFocused(entry, index)
                        scope.launch {
                            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                        }
                    },
                    onClick = { onResume(entry) },
                )
            }
        }
    }
}

@Composable
private fun TvContinueWatchingCard(
    entry: LibraryPlaybackEntry,
    modifier: Modifier = Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember {
        mutableStateOf(false)
    }
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(),
        label = "continueCardScale",
    )
    val borderColor by animateColorAsState(
        if (focused) Color.White
        else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "continueCardBorder",
    )

    Column(
        modifier =
            modifier
                .width(286.dp)
                .zIndex(if (focused) 1f else 0f)
                .scale(scale)
                .tvVerticalFocus(
                    up = upRequester,
                    down = downRequester,
                )
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        onFocused()
                    }
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(161.dp)
                    .background(
                        VueoPanel,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        width =
                            if (focused) 2.dp
                            else 1.dp,
                        color = borderColor,
                        shape =
                            RoundedCornerShape(12.dp),
                    ),
        ) {
            TvNetworkImage(
                url =
                    entry.media.background
                        ?: entry.media.poster,
                contentDescription =
                    entry.media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            Color.White.copy(
                                alpha = 0.24f
                            )
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(
                                entry.progressFraction
                                    .coerceIn(
                                        0.02f,
                                        1f,
                                    )
                            )
                            .fillMaxHeight()
                            .background(
                                TvAccent
                            ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.media.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight =
                if (focused) FontWeight.Bold
                else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                buildList {
                    if (
                        entry.season != null &&
                        entry.episode != null
                    ) {
                        add(
                            "S${entry.season}E${entry.episode}"
                        )
                    }
                    entry.episodeTitle
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let(::add)
                    if (isEmpty()) {
                        add(
                            "${(entry.progressFraction * 100).toInt()}% watched"
                        )
                    }
                }.joinToString(" • "),
            color =
                if (focused) {
                    Color.White.copy(alpha = 0.74f)
                } else {
                    VueoMuted
                },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvRail(
    row: TvCatalogRow,
    entryRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    preferredIndex: Int,
    onCardFocused: (TvMediaItem, Int) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val rememberedIndex = TvFocusMemory.railIndex(row.id, row.items.size)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (rememberedIndex - 1).coerceAtLeast(0),
        )
    val scope = rememberCoroutineScope()
    var entryIndex by remember(row.id, row.items.size) { mutableIntStateOf(rememberedIndex) }

    LaunchedEffect(preferredIndex, row.id, row.items.size) {
        if (row.items.isNotEmpty()) {
            val targetIndex = preferredIndex.coerceIn(0, row.items.lastIndex)
            if (entryIndex != targetIndex) {
                entryIndex = targetIndex
                listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
            }
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = row.providerName,
                color = VueoMuted.copy(alpha = 0.68f),
                fontSize = 12.sp,
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = row.items,
                key = { _, item -> "${row.id}:${item.type}:${item.id}" },
            ) { index, item ->
                val entryModifier =
                    (if (index == entryIndex) {
                        Modifier.focusRequester(entryRequester)
                    } else {
                        Modifier
                    }).tvHorizontalEdgeGuard(
                        blockLeft = index == 0,
                        blockRight = index == row.items.lastIndex,
                    )

                TvPosterCard(
                    item = item,
                    modifier = entryModifier,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        entryIndex = index
                        TvFocusMemory.rememberRail(
                            rowId = row.id,
                            itemIndex = index,
                            mediaKey = "${item.type}:${item.id}",
                        )
                        onCardFocused(item, index)
                        scope.launch {
                            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                        }
                    },
                    onClick = { onOpenMedia(item) },
                )
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    item: TvMediaItem,
    modifier: Modifier = Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(), label = "cardScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "cardBorder",
    )
    val glowColor by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "cardGlow",
    )

    Column(
        modifier =
            modifier
                .width(184.dp)
                .zIndex(if (focused) 1f else 0f)
                .scale(scale)
                .tvVerticalFocus(
                    up = upRequester,
                    down = downRequester,
                )
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        onFocused()
                    }
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(268.dp)
                    .background(
                        color = glowColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(3.dp),
        ) {
            TvNetworkImage(
                url = item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cardMeta(item),
            color = if (focused) Color.White.copy(alpha = 0.74f) else VueoMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun cardMeta(item: TvMediaItem): String =
    listOfNotNull(
        item.displayType,
        item.releaseInfo?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

private fun LibraryPlaybackEntry.toTvPlaybackRequest():
    TvPlaybackRequest =
    TvPlaybackRequest(
        media = media,
        videoId = videoId,
        episodeTitle = episodeTitle,
        season = season,
        episode = episode,
    )

@Composable
private fun TvUpdateOverlay(
    release: VueoTvUpdateRelease,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onLater: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    val updateButtonFocus = remember { FocusRequester() }

    LaunchedEffect(release.versionCode) {
        runCatching { updateButtonFocus.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(610.dp)
                    .background(
                        color = VueoPanel,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 34.dp, vertical = 28.dp),
        ) {
            Text(
                text = "VUEO TV update available",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = release.versionName,
                color = VueoYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (release.changelog.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                release.changelog.take(3).forEach { item ->
                    Text(
                        text = "•  $item",
                        color = VueoMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            if (downloading) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Downloading update… ${progress.coerceIn(0, 100)}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(99.dp),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    (progress.coerceIn(0, 100) / 100f)
                                        .coerceAtLeast(0.01f)
                                )
                                .fillMaxHeight()
                                .background(
                                    VueoYellow,
                                    RoundedCornerShape(99.dp),
                                ),
                    )
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = message,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !downloading,
                    modifier = Modifier.focusRequester(updateButtonFocus),
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Update Now",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onLater,
                    enabled = !downloading,
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun LoadingHome() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 104.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.42f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.78f)
                            .height(42.dp)
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(
                    modifier = Modifier.width(28.dp).height(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            )
        }

        Spacer(Modifier.height(38.dp))
        Text(
            text = "Loading Home",
            color = VueoMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 58.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(6) {
                Box(
                    modifier =
                        Modifier
                            .width(158.dp)
                            .height(226.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@Composable
private fun ErrorHome(
    message: String,
    onRetry: () -> Unit,
) {
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { retryRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = VueoMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryRequester),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TvHomeExitOverlay(
    onStay: () -> Unit,
    onExit: () -> Unit,
) {
    val stayRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { stayRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(40f)
                .background(Color.Black.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(430.dp)
                    .background(VueoPanel, RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 30.dp, vertical = 26.dp),
        ) {
            Text(
                text = "Exit Vueo?",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your Home position and playback progress are already saved.",
                color = VueoMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStay,
                    modifier = Modifier.focusRequester(stayRequester),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                ) {
                    Text("Stay", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onExit,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Exit", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

