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
internal fun SearchScreen(
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
                    SearchOrchestrator.localTitleResults(normalized)
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
                SearchOrchestrator.localTitleResults(normalized)

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
                SearchOrchestrator.remoteTitleResults(
                    engine = engine,
                    query = normalized,
                    localResults = local,
                    onPartial = { partial ->
                        if (
                            requestId == searchRequestId &&
                            query.trim() == normalized &&
                            searchMode == requestedMode
                        ) {
                            searchResults = partial
                        }
                    },
                )

            if (
                requestId == searchRequestId &&
                query.trim() == normalized &&
                searchMode == requestedMode
            ) {
                searchResults = remote
                searching = false
            }

            return@LaunchedEffect
        }

        val tmdbApiKey =
            searchPluginStore
                .tmdbApiKey()
        val actorAvailability =
            SearchOrchestrator.actorAvailability(
                engine = engine,
                tmdbApiKey = tmdbApiKey,
            )

        actorSourceAvailable = actorAvailability.available
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

        val actorResults =
            SearchOrchestrator.actorResults(
                engine = engine,
                query = normalized,
                tmdbApiKey = tmdbApiKey,
                onPartial = { partial ->
                    if (
                        requestId == searchRequestId &&
                        query.trim() == normalized &&
                        searchMode == requestedMode
                    ) {
                        searchResults = partial
                    }
                },
            )

        if (
            requestId == searchRequestId &&
            query.trim() == normalized &&
            searchMode == requestedMode
        ) {
            searchResults = actorResults
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
            DiscoverCatalogPolicy.baseItems(
                rows = discoverRows,
                mode = sortMode.toDiscoverSortMode(),
            )
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
                .filter { item ->
                    SearchResultOrderPolicy.matchesType(
                        item = item,
                        filter = typeFilter.toSearchMediaFilter(),
                        animeCatalogKeys = animeCatalogKeys,
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
                        SearchResultOrderPolicy.matchesType(
                            item = item,
                            filter = typeFilter.toSearchMediaFilter(),
                            animeCatalogKeys = animeCatalogKeys,
                        ) &&
                            SearchResultOrderPolicy.matchesGenre(
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
                DiscoverCatalogPolicy.orderFiltered(
                    items = filtered,
                    mode = sortMode.toDiscoverSortMode(),
                )
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

private fun SearchSortMode.toDiscoverSortMode(): DiscoverSortMode =
    when (this) {
        SearchSortMode.POPULAR -> DiscoverSortMode.POPULAR
        SearchSortMode.TRENDING -> DiscoverSortMode.TRENDING
        SearchSortMode.NEWEST -> DiscoverSortMode.NEWEST
    }

private fun SearchTypeFilter.toSearchMediaFilter(): SearchMediaFilter =
    when (this) {
        SearchTypeFilter.ALL -> SearchMediaFilter.ALL
        SearchTypeFilter.MOVIES -> SearchMediaFilter.MOVIES
        SearchTypeFilter.SERIES -> SearchMediaFilter.SERIES
        SearchTypeFilter.ANIME -> SearchMediaFilter.ANIME
    }

private fun searchSortActorItems(
    items: List<MediaItem>,
    mode: SearchSortMode,
): List<MediaItem> =
    SearchResultOrderPolicy.sortActorItems(
        items = items,
        mode = mode.toDiscoverSortMode(),
    )

private fun searchSortItems(
    items: List<MediaItem>,
    mode: SearchSortMode,
    query: String? = null,
): List<MediaItem> =
    SearchResultOrderPolicy.sortTitleItems(
        items = items,
        mode = mode.toDiscoverSortMode(),
        query = query,
    )

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
internal fun SearchPosterTile(
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

internal fun searchTypeLabel(
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

