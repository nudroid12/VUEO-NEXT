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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.vueo.shared.core.source.SourceDiscoveryActivity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcePickerScreen(
    mediaTitle: String,
    streams: List<StreamSource>,
    rawCount: Int,
    notice: String?,
    searching: Boolean,
    progressText: String,
    activityLog: List<SourceDiscoveryActivity>,
    firstResultMs: Long?,
    providerOrder: List<String>,
    originalLanguage: String?,
    showTechnicalDetails: Boolean,
    onRefresh: () -> Unit,
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
    var clearedActivityCount by remember(mediaTitle) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(activityLog.size) {
        if (activityLog.size < clearedActivityCount) {
            clearedActivityCount = 0
        }
    }

    if (showEngineDetails) {
        SourceActivityLogDialog(
            mediaTitle = mediaTitle,
            searching = searching,
            progressText = progressText,
            rawCount = rawCount,
            uniqueCount = streams.size,
            firstResultMs = firstResultMs,
            notice = notice,
            entries = activityLog.drop(clearedActivityCount),
            onClear = { clearedActivityCount = activityLog.size },
            onDismiss = { showEngineDetails = false },
        )
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

    PullToRefreshBox(
        isRefreshing = searching,
        onRefresh = {
            if (!searching) onRefresh()
        },
        // Source Engine already communicates discovery progress. Keep the
        // pull gesture, but suppress Material's second loading indicator.
        indicator = {},
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
                            "Details",
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

                        sourceTitleDisplayName(best)?.let { sourceTitle ->
                            Text(
                                sourceTitle,
                                color = Color.White.copy(alpha = .55f),
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
                            "No sources were returned for this title. Pull down to refresh."
                        } else {
                            "Sources were found, but none are directly playable by the current VUEO player. Pull down to refresh."
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
}

private enum class SourceLogFilter(val label: String) {
    ALL("All"), REQUESTS("Requests"), SOURCES("Sources"),
    SUBTITLES("Subtitles"), ERRORS("Errors"),
}

@Composable
private fun SourceActivityLogDialog(
    mediaTitle: String,
    searching: Boolean,
    progressText: String,
    rawCount: Int,
    uniqueCount: Int,
    firstResultMs: Long?,
    notice: String?,
    entries: List<SourceDiscoveryActivity>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(SourceLogFilter.ALL) }
    val listState = rememberLazyListState()
    val filteredEntries = remember(entries, selectedFilter) {
        entries.filter { entry ->
            when (selectedFilter) {
                SourceLogFilter.ALL -> true
                SourceLogFilter.REQUESTS -> entry.category == "requests"
                SourceLogFilter.SOURCES -> entry.category == "sources"
                SourceLogFilter.SUBTITLES -> entry.category == "subtitles"
                SourceLogFilter.ERRORS ->
                    entry.level == "error" || entry.level == "warning"
            }
        }
    }

    LaunchedEffect(filteredEntries.size) {
        if (filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = VueoPalette.SurfaceElevated
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                VueoPalette.Stroke.copy(alpha = .55f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Live Activity Log",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            mediaTitle,
                            color = VueoPalette.Muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (searching) {
                            VueoPalette.Accent.copy(alpha = .14f)
                        } else {
                            VueoPalette.SurfaceStrong
                        },
                    ) {
                        Text(
                            if (searching) "LIVE" else "COMPLETE",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp,
                            ),
                            color = if (searching) {
                                VueoPalette.Accent
                            } else {
                                Color.White.copy(alpha = .72f)
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close activity log",
                            tint = Color.White,
                        )
                    }
                }

                Text(
                    progressText,
                    color = Color.White.copy(alpha = .8f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )

                Text(
                    buildString {
                        append("$rawCount raw • $uniqueCount unique")
                        firstResultMs?.let { append(" • first source ${it} ms") }
                        notice?.takeIf { it.isNotBlank() }?.let {
                            append("\n")
                            append(it)
                        }
                    },
                    color = VueoPalette.Muted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(SourceLogFilter.values().toList()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 10.sp) },
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 430.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = VueoPalette.Background.copy(alpha = .72f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        VueoPalette.Stroke.copy(alpha = .35f),
                    ),
                ) {
                    if (filteredEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (entries.isEmpty()) {
                                    "Waiting for discovery activity…"
                                } else {
                                    "No events in this filter."
                                },
                                color = VueoPalette.Muted,
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredEntries) { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        formatSourceLogTime(entry.elapsedMs),
                                        modifier = Modifier.width(72.dp),
                                        color = VueoPalette.Muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                    )
                                    Text(
                                        entry.message,
                                        modifier = Modifier.weight(1f),
                                        color = when (entry.level) {
                                            "error" -> Color(0xFFFF7D7D)
                                            "warning" -> Color(0xFFFFC46B)
                                            else -> Color.White.copy(alpha = .86f)
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onClear,
                        enabled = entries.isNotEmpty(),
                    ) { Text("Clear") }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "VUEO source activity log",
                                    buildSourceActivityExport(
                                        mediaTitle,
                                        progressText,
                                        entries,
                                    ),
                                )
                            )
                            Toast.makeText(
                                context,
                                "Source activity log copied",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        enabled = entries.isNotEmpty(),
                    ) { Text("Copy log") }
                }
            }
        }
    }
}

private fun formatSourceLogTime(elapsedMs: Long): String {
    val minutes = elapsedMs / 60_000L
    val seconds = (elapsedMs / 1_000L) % 60L
    val millis = elapsedMs % 1_000L
    return "[%02d:%02d.%03d]".format(minutes, seconds, millis)
}

private fun buildSourceActivityExport(
    mediaTitle: String,
    progressText: String,
    entries: List<SourceDiscoveryActivity>,
): String = buildString {
    appendLine("VUEO Source Activity Log")
    appendLine("Title: $mediaTitle")
    appendLine("Status: $progressText")
    appendLine()
    entries.forEach { entry ->
        append(formatSourceLogTime(entry.elapsedMs))
        append(' ')
        append(entry.category.uppercase())
        append(' ')
        appendLine(entry.message)
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

private fun sourceServerDisplayName(
    source: StreamSource,
): String? =
    source.serverName
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun sourceTitleDisplayName(
    source: StreamSource,
): String? {
    val title = source.name.trim()
    if (title.isBlank() ||
        title.startsWith("http://", ignoreCase = true) ||
        title.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }

    val provider = sourceProviderTabDisplayName(
        sourceProviderTabKey(source)
    )
    val server = sourceServerDisplayName(source)
    return title.takeUnless {
        it.equals(provider, ignoreCase = true) ||
            server?.let { serverName ->
                it.equals(serverName, ignoreCase = true)
            } == true
    }
}

private fun sourceMetadataLine(
    source: StreamSource,
    assessment: PlayerSourceAssessment,
    compactAudioLabel: Boolean = false,
): String =
    listOfNotNull(
        sourceServerDisplayName(source)
            ?: sourceRepositoryDisplayName(source),
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
        assessment.summary
            .split(" • ")
            .filterNot { it.equals("Unknown", ignoreCase = true) }
            .joinToString(" • ")
            .takeIf { it.isNotBlank() },
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

            val sourceTitle = sourceTitleDisplayName(source)
            if (
                sourceTitle != null &&
                (showTechnicalDetails || sourceServerDisplayName(source) != null)
            ) {
                Text(
                    sourceTitle,
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
