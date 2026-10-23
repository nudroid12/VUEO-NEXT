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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.vueo.shared.core.storage.LibraryHistoryPresentation
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
internal fun LibraryScreen(
    store: LibraryStore,
    activeProfile: VueoProfile,
    version: Int,
    onVersionChanged: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlaybackClick:
        (LibraryPlaybackEntry) -> Unit,
) {
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStore(context.applicationContext) }
    var historySelected by rememberSaveable(activeProfile.id) { mutableStateOf(false) }
    var history by remember(store, activeProfile.id, activeProfile.isKids, activeProfile.kidsMaxAge) {
        mutableStateOf<List<LibraryPlaybackEntry>>(emptyList())
    }
    var favorites by remember(store, activeProfile.id, activeProfile.isKids, activeProfile.kidsMaxAge) {
        mutableStateOf<List<MediaItem>>(emptyList())
    }
    var loading by remember(store, activeProfile.id, activeProfile.isKids, activeProfile.kidsMaxAge) {
        mutableStateOf(true)
    }
    var loadFailed by remember(store, activeProfile.id, activeProfile.isKids, activeProfile.kidsMaxAge) {
        mutableStateOf(false)
    }
    LaunchedEffect(store, version, activeProfile.id, activeProfile.isKids, activeProfile.kidsMaxAge) {
        loading = true
        loadFailed = false
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // The provided store follows the active profile. Reject a read
                // spanning a profile switch instead of displaying another user's list.
                if (profileStore.activeProfileId() != activeProfile.id) return@runCatching null
                (store.watchlist() to LibraryHistoryPresentation.latestTitles(store.history()))
                    .takeIf { profileStore.activeProfileId() == activeProfile.id }
            }
        }
        result.onSuccess { fresh -> if (fresh != null) {
            favorites = fresh.first
            history = fresh.second
        } }
            .onFailure { loadFailed = true }
        loading = false
    }

    val watchlist = if (historySelected) history.map { it.media } else favorites
    val historyCaptions = remember(history) {
        history.associate { "${it.media.type}:${it.media.id}" to LibraryHistoryPresentation.caption(it) }
    }
    var historyAction by remember(activeProfile.id) { mutableStateOf<MediaItem?>(null) }
    var confirmHistoryDelete by remember(activeProfile.id) { mutableStateOf(false) }
    var deletingHistory by remember(activeProfile.id) { mutableStateOf(false) }
    var deleteHistoryError by remember(activeProfile.id) { mutableStateOf(false) }
    val historyScope = rememberCoroutineScope()
    historyAction?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deletingHistory) historyAction = null },
            title = { Text(if (confirmHistoryDelete) "Delete history?" else target.name) },
            text = if (deleteHistoryError) ({ Text("Couldn't delete history. Try again.") }) else null,
            dismissButton = { TextButton(enabled = !deletingHistory, onClick = { historyAction = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(enabled = !deletingHistory, onClick = {
                    if (!confirmHistoryDelete) confirmHistoryDelete = true
                    else {
                        deletingHistory = true
                        historyScope.launch {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { LibraryStore(context, profileIdOverride = activeProfile.id).resetTitleHistory(target) }
                            }
                            deletingHistory = false
                            if (result.isSuccess) { historyAction = null; onVersionChanged() }
                            else deleteHistoryError = true
                        }
                    }
                }) { Text(if (confirmHistoryDelete) "Delete" else "Delete history") }
            },
        )
    }
    fun showHistoryActions(media: MediaItem) {
        confirmHistoryDelete = false
        deleteHistoryError = false
        historyAction = media
    }
    val listState = rememberLazyListState()
    LaunchedEffect(historySelected) { listState.scrollToItem(0) }
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
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        // Match Search exactly: the list owns only the bottom inset and the
        // header owns its own 20/20/24 positioning.
        contentPadding =
            PaddingValues(
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
                modifier =
                    Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 24.dp,
                        bottom = 2.dp,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.Top,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Favorites", "History").forEachIndexed { index, label ->
                        val selected = historySelected == (index == 1)
                        TextButton(onClick = { historySelected = index == 1 }) {
                            Text(label, color = if (selected) Color.White else VueoPalette.Muted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        if (watchlist.isEmpty() && loading) {
            item(key = "library-loading") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
        } else if (watchlist.isEmpty() && loadFailed) {
            item(key = "library-load-failed") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    LibraryEmptyCard(
                        title = if (historySelected) "Couldn't load History" else "Couldn't load Favorites",
                        body = "Try loading your library again.",
                    )
                    TextButton(onClick = onVersionChanged) { Text("Retry") }
                }
            }
        } else if (watchlist.isEmpty()) {
            item(
                key = "library-saved-empty"
            ) {
                Box(
                    modifier =
                        Modifier.padding(
                            horizontal = 20.dp
                        )
                ) {
                    LibraryEmptyCard(
                        title =
                            if (historySelected) "No watch history yet" else "Your library is empty",
                        body =
                            if (historySelected) "Titles you watch will appear here." else "Titles added to Favorites will appear here.",
                    )
                }
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
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp
                                    ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                            verticalAlignment =
                                Alignment.Top,
                        ) {
                            rowItems.forEach {
                                media ->

                                Column(modifier = Modifier.weight(1f)) {
                                    if (historySelected) {
                                        LibraryHistoryPoster(
                                            media = media,
                                            caption = historyCaptions["${media.type}:${media.id}"].orEmpty(),
                                            onClick = { onMediaClick(media) },
                                            onLongClick = { showHistoryActions(media) },
                                        )
                                    } else {
                                        SearchPosterTile(item = media, modifier = Modifier.fillMaxWidth(),
                                            onClick = { onMediaClick(media) })
                                    }
                                }
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

                Box(
                    modifier =
                        Modifier.padding(
                            horizontal = 20.dp
                        )
                ) {
                    LibrarySavedListRow(
                        media = media,
                        historyCaption = if (historySelected) historyCaptions["${media.type}:${media.id}"] else null,
                        onLongClick = if (historySelected) ({ showHistoryActions(media) }) else null,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySavedListRow(
    media: MediaItem,
    historyCaption: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    historyCaption ?: listOfNotNull(
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
                    "Remove from Favorites",
                modifier =
                    Modifier.size(16.dp),
            )
        }
    }
}

internal fun playbackEntrySubtitle(
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



@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryHistoryPoster(media: MediaItem, caption: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        NetworkImage(url = media.poster, contentDescription = media.name, fallbackText = media.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.height(7.dp))
        Text(media.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(caption, color = VueoPalette.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
