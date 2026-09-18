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
internal fun HomeScreen(
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

    val disabledCatalogKeys =
        remember(
            contentVersion
        ) {
            homeAddonStore
                .disabledCatalogKeys()
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
        disabledCatalogKeys,
    ) {
        mutableStateOf(
            HomeCatalogPolicy.orderRows(
                rows =
                    CatalogDiscoveryCache
                        .home(
                            allowStale = true
                        )
                        .orEmpty(),
                catalogOrder =
                    catalogOrder,
                disabledCatalogKeys =
                    disabledCatalogKeys,
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
                    HomeCatalogPolicy.orderRows(
                        rows = it,
                        catalogOrder =
                            catalogOrder,
                        disabledCatalogKeys =
                            disabledCatalogKeys,
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
                disabledCatalogKeys =
                    disabledCatalogKeys,
            )
        }.onSuccess {
            fresh ->
            if (
                fresh.isNotEmpty()
            ) {
                rows =
                    HomeCatalogPolicy.orderRows(
                        rows = fresh,
                        catalogOrder =
                            catalogOrder,
                        disabledCatalogKeys =
                            disabledCatalogKeys,
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
                    HomeCatalogPolicy.orderRows(
                        rows =
                            CatalogDiscoveryCache
                                .home(
                                    allowStale =
                                        true
                                )
                                .orEmpty(),
                        catalogOrder =
                            catalogOrder,
                        disabledCatalogKeys =
                            disabledCatalogKeys,
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

    val homeRecommendations =
        remember(
            rows,
            watchHistory,
            activeProfileId,
            libraryVersion,
            personalizedHomeEnabled,
        ) {
            HomeRecommendationPolicy.build(
                catalogRows = rows,
                watchHistory = watchHistory,
                dnaEngine = dnaEngine,
                personalizationEnabled = personalizedHomeEnabled,
                limit = 12,
            )
        }

    val forYouItems = homeRecommendations.forYou
    val becauseYouWatchedSeed = homeRecommendations.becauseYouWatchedSeed
    val becauseYouWatchedItems = homeRecommendations.becauseYouWatched

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
                    modifier =
                        Modifier.fillMaxWidth(),
                    color =
                        Color.White,
                    fontSize = 28.sp,
                    lineHeight =
                        30.sp,
                    fontWeight =
                        FontWeight.Black,
                    textAlign =
                        TextAlign.Center,
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

