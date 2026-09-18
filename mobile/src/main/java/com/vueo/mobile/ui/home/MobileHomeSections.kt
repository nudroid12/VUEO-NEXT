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
internal fun HomeContinueWatchingSection(
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
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            0.dp
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
                                lineHeight =
                                    11.sp,
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
                        lineHeight = 14.sp,
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
                                lineHeight =
                                    11.sp,
                                maxLines = 1,
                                overflow =
                                    TextOverflow
                                        .Ellipsis,
                            )
                        }
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
                            .padding(
                                top = 4.dp
                            )
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

internal fun homeRemainingTimeLabel(
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
internal fun HomeSectionHeader(
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
internal fun HomeLoadingState() {
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
internal fun EmptyHomeCard(
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
                            "Install an addon in Content Manager. Available catalogs will appear here automatically."
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
internal fun HomePersonalizedSection(
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
internal fun CatalogSection(
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

