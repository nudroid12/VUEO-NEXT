package com.vueo.mobile.ui

import android.app.Activity
import android.net.Uri
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontFamily
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
import com.vueo.mobile.core.extensions.AddonCategory
import com.vueo.mobile.core.extensions.ExtensionInstaller
import com.vueo.mobile.core.extensions.primaryAddonCategory
import com.vueo.mobile.core.extensions.ExtensionKind
import com.vueo.mobile.core.extensions.MediaExtension
import com.vueo.mobile.core.extensions.UnifiedMediaEngine
import com.vueo.mobile.core.extensions.SourceRanker
import com.vueo.mobile.core.extensions.SourceCleaner
import com.vueo.mobile.core.extensions.SourceDiscoveryCache
import com.vueo.mobile.core.extensions.CatalogDiscoveryCache
import com.vueo.mobile.core.enrichment.MdblistClient
import com.vueo.mobile.core.enrichment.MediaRating
import com.vueo.mobile.core.enrichment.RichDetailsClient
import com.vueo.mobile.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.plugin.providerHealthSortKey
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.enrichment.ContentWarningRepository
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
import com.vueo.mobile.core.plugin.TmdbResolver
import com.vueo.mobile.core.plugin.PluginSourceEngine
import com.vueo.mobile.core.plugin.PluginRepositoryDescriptor
import com.vueo.mobile.core.plugin.PluginRepositoryClient
import com.vueo.mobile.core.model.EpisodeItem
import com.vueo.mobile.core.model.MediaCompany
import com.vueo.mobile.core.model.MediaItem
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


internal data class RankedProviderHealthEntry(
    val repository: PluginRepositoryDescriptor,
    val provider: com.vueo.mobile.core.plugin.PluginProviderDescriptor,
    val health: ProviderHealthRecord?,
)

@Composable
internal fun ProviderHealthOverviewScreen(
    repositories: List<PluginRepositoryDescriptor>,
    store: PluginStore,
    healthStore: PluginHealthStore,
    healthRevision: Int,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val knownHealth =
        remember(repositories, healthRevision) {
            healthStore.records()
                .associateBy {
                    it.repositoryManifestUrl to it.providerId
                }
        }
    val rankedProviders =
        remember(repositories, knownHealth, healthRevision) {
            repositories
                .filter(store::isRepositoryEnabled)
                .flatMap { repository ->
                    repository.providers
                        .filter { provider ->
                            store.isProviderEnabled(repository, provider)
                        }
                        .map { provider ->
                            RankedProviderHealthEntry(
                                repository = repository,
                                provider = provider,
                                health = knownHealth[repository.manifestUrl to provider.id],
                            )
                        }
                }
                .sortedWith(
                    compareBy<RankedProviderHealthEntry> { entry ->
                        providerHealthSortKey(entry.health).availabilityTier
                    }.thenByDescending { entry ->
                        providerHealthSortKey(entry.health).performanceScore
                    }.thenBy { entry ->
                        providerHealthSortKey(entry.health).statusTier
                    }.thenBy { entry ->
                        providerHealthSortKey(entry.health).responseMs
                    }.thenBy { entry ->
                        entry.provider.name.lowercase()
                    }
                )
        }
    val measuredProviders =
        rankedProviders.count { entry ->
            healthStore.performance(entry.health).historyRuns > 0
        }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Provider Health",
            subtitle = "Historical provider performance",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 116.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item(key = "provider-health-explainer") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = VueoPalette.SurfaceElevated,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = "Smart provider order",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$measuredProviders/${rankedProviders.size} enabled providers have scan history. " +
                                "Ranking updates automatically after source scans.",
                            color = VueoPalette.Muted,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        )
                        Text(
                            text = "Score affects scan order only. Providers are not removed, and No Results is not treated as a hard failure.",
                            color = VueoPalette.Muted.copy(alpha = .78f),
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp,
                        )
                    }
                }
            }

            if (rankedProviders.isEmpty()) {
                item(key = "provider-health-empty") {
                    Text(
                        text = "No enabled providers.",
                        color = VueoPalette.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
            } else {
                item(key = "provider-health-heading") {
                    Text(
                        text = "PROVIDER RANKING",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 1.dp),
                    )
                }

                itemsIndexed(
                    items = rankedProviders,
                    key = { _, entry ->
                        entry.repository.manifestUrl + ":" + entry.provider.id
                    },
                ) { index, entry ->
                    ProviderHealthRankingRow(
                        rank = index + 1,
                        entry = entry,
                        healthStore = healthStore,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProviderHealthRankingRow(
    rank: Int,
    entry: RankedProviderHealthEntry,
    healthStore: PluginHealthStore,
) {
    val performance = healthStore.performance(entry.health)
    val status = entry.health?.status ?: ProviderHealthStatus.UNKNOWN
    val timing =
        performance.averageResponseMs?.let(::formatProviderAverageResponse)
            ?: "No timing"
    val history =
        if (performance.historyRuns > 0) {
            val hit = performance.hitRatePercent?.let { "$it% hit" } ?: "No hit rate"
            "$hit • $timing • ${performance.historyRuns} runs"
        } else {
            "No scan history yet"
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = VueoPalette.SurfaceElevated,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#$rank",
                color = Color.White.copy(alpha = .52f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp),
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.provider.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.repository.name,
                    color = VueoPalette.Muted.copy(alpha = .78f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = history,
                    color = VueoPalette.Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Score ${performance.score}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status.label,
                    color = providerStatusColor(true, entry.health),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

internal fun formatProviderAverageResponse(
    responseMs: Long,
): String =
    if (responseMs < 1_000L) {
        "${responseMs} ms avg"
    } else {
        val tenths = ((responseMs + 50L) / 100L) / 10.0
        "${tenths}s avg"
    }

@Composable
internal fun RuntimeDiagnosticsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var diagnosticText by remember {
        mutableStateOf(RuntimeDiagnostics.export(context.applicationContext))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Performance & Crash Diagnostics") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Run source discovery until the lag/crash happens, then copy this log. It records provider timing, concurrency, main-thread stalls and memory without provider secrets.",
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = diagnosticText.takeLast(24_000),
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    diagnosticText = RuntimeDiagnostics.export(context.applicationContext)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("VUEO performance diagnostic", diagnosticText)
                    )
                    Toast.makeText(context, "Diagnostic log copied", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Copy Log")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        RuntimeDiagnostics.clear(context.applicationContext)
                        diagnosticText = RuntimeDiagnostics.export(context.applicationContext)
                    },
                ) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
internal fun PluginRepositoryCard(
    repository: PluginRepositoryDescriptor,
    store: PluginStore,
    healthStore: PluginHealthStore,
    healthRevision: Int,
    codeStore: ProviderCodeStore,
    codeRevision: Int,
    repositoryEnabled: Boolean,
    onRepositoryEnabledChanged: (Boolean) -> Unit,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onProviderChanged: () -> Unit,
) {
    val readyProviderCode =
        remember(repository.manifestUrl, repository.version, codeRevision) {
            codeStore.readyCount(repository)
        }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = VueoPalette.SurfaceElevated,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(VueoPalette.SurfaceStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "P",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Spacer(Modifier.width(11.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = repository.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "v${repository.version}  •  ${repository.providers.size} providers  •  " +
                                "$readyProviderCode ready",
                            color = VueoPalette.Muted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Switch(
                        checked = repositoryEnabled,
                        onCheckedChange = onRepositoryEnabledChanged,
                    )
                }

                if (refreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                repository.description
                    ?.let(::neutralizePlatformCopy)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { description ->
                        Text(
                            text = description,
                            color = VueoPalette.Muted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (repositoryEnabled) {
                            "Provider preferences active"
                        } else {
                            "Repository disabled • preferences preserved"
                        },
                        color = VueoPalette.Muted.copy(alpha = .72f),
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        enabled = !refreshing,
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = .82f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = .82f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }

        if (repository.providers.isNotEmpty()) {
            val knownHealth =
                remember(repository.manifestUrl, healthRevision) {
                    healthStore.records()
                        .asSequence()
                        .filter { it.repositoryManifestUrl == repository.manifestUrl }
                        .associateBy { it.providerId }
                }
            val rankedProviders =
                remember(repository, knownHealth, healthRevision) {
                    repository.providers
                        .map { provider ->
                            RankedProviderHealthEntry(
                                repository = repository,
                                provider = provider,
                                health = knownHealth[provider.id],
                            )
                        }
                        .sortedWith(
                            compareBy<RankedProviderHealthEntry> { entry ->
                                providerHealthSortKey(entry.health).availabilityTier
                            }.thenByDescending { entry ->
                                providerHealthSortKey(entry.health).performanceScore
                            }.thenBy { entry ->
                                providerHealthSortKey(entry.health).statusTier
                            }.thenBy { entry ->
                                providerHealthSortKey(entry.health).responseMs
                            }.thenBy { entry ->
                                entry.provider.name.lowercase()
                            }
                        )
                }

            Text(
                text = "PROVIDERS",
                color = VueoPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 3.dp, top = 2.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                color = VueoPalette.SurfaceElevated,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    rankedProviders.forEach { entry ->
                        val provider = entry.provider
                        val health = entry.health
                        val enabled = store.isProviderEnabled(repository, provider)

                        ProviderHealthRow(
                            repository = repository,
                            provider = provider,
                            health = health,
                            enabled = enabled,
                            providerCodeReady = codeStore.isReady(repository, provider),
                            onEnabledChanged = { next ->
                                store.setProviderEnabled(repository, provider, next)
                                onProviderChanged()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProviderHealthRow(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.mobile.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord?,
    enabled: Boolean,
    providerCodeReady: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember(repository.manifestUrl, provider.id) {
        mutableStateOf(false)
    }
    var rawExpanded by remember(repository.manifestUrl, provider.id) {
        mutableStateOf(false)
    }

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    effectiveStatus,
                    color = providerStatusColor(enabled, health),
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

            if (health != null) {
                IconButton(
                    onClick = {
                        expanded = !expanded
                        if (!expanded) rawExpanded = false
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse diagnostics" else "Open diagnostics",
                        tint = Color.White.copy(alpha = .76f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(34.dp))
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        AnimatedVisibility(visible = expanded && health != null) {
            health?.let { record ->
                ProviderDiagnosticPanel(
                    repository = repository,
                    provider = provider,
                    health = record,
                    currentlyEnabled = enabled,
                    providerCodeReady = providerCodeReady,
                    rawExpanded = rawExpanded,
                    onRawExpandedChanged = { rawExpanded = it },
                    onCopyFullLog = {
                        copyProviderDiagnostic(
                            context = context,
                            label = "VUEO provider diagnostic log",
                            text = providerDiagnosticFullLog(
                                repository = repository,
                                provider = provider,
                                health = record,
                                currentlyEnabled = enabled,
                                providerCodeReady = providerCodeReady,
                            ),
                        )
                    },
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
        )
    }
}

@Composable
internal fun ProviderDiagnosticPanel(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.mobile.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
    rawExpanded: Boolean,
    onRawExpandedChanged: (Boolean) -> Unit,
    onCopyFullLog: () -> Unit,
) {
    val request = providerRequestLabelOrNull(health)
    val failureStage = providerFailureStage(health, providerCodeReady)
    val failureCategory = providerFailureCategory(health)
    val httpStatus = providerHttpStatus(health)
    val errorType = health.errorType?.takeIf { it.isNotBlank() }?.let(::sanitizeDiagnosticText)
    val errorMessage = health.error?.takeIf { it.isNotBlank() }?.let(::sanitizeDiagnosticText)
    val likelyCause = providerLikelyCause(health, providerCodeReady)
    val timing = providerRelevantTimingLabel(health)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = VueoPalette.SurfaceStrong.copy(alpha = .72f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "DIAGNOSTIC",
                        color = VueoPalette.Muted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.05.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${provider.name} • v${provider.version}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (currentlyEnabled) health.status.label else "Disabled • ${health.status.label}",
                    color = providerStatusColor(currentlyEnabled, health),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            request?.let { DiagnosticFact("Request", it) }
            DiagnosticFact("Failure", "$failureStage • $failureCategory")
            errorType?.let { DiagnosticFact("Error type", it) }
            httpStatus?.let { DiagnosticFact("HTTP", it) }
            timing?.let { DiagnosticFact("Timing", it) }

            if (health.status == ProviderHealthStatus.NO_RESULTS || health.streamCount == 0) {
                DiagnosticFact(
                    "Result",
                    "${health.streamCount} playable source${if (health.streamCount == 1) "" else "s"}",
                )
            }

            if (!providerCodeReady) {
                DiagnosticFact("Provider code", "Missing or not ready")
            }

            errorMessage?.let { error ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Error",
                        color = Color.White.copy(alpha = .88f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error.copy(alpha = .88f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = VueoPalette.SurfaceElevated.copy(alpha = .72f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Likely cause",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = likelyCause,
                        color = VueoPalette.Muted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCopyFullLog) {
                    Text("Copy Debug Log", fontSize = 10.5.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onRawExpandedChanged(!rawExpanded) },
                ) {
                    Text(
                        text = "Raw technical log",
                        fontSize = 10.5.sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = if (rawExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (rawExpanded) "Collapse raw log" else "Open raw log",
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            AnimatedVisibility(visible = rawExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = .28f),
                ) {
                    Text(
                        text = providerRawDiagnosticLog(health),
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticFact(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = VueoPalette.Muted.copy(alpha = .78f),
            fontSize = 9.5.sp,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = .88f),
            fontSize = 9.8.sp,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun providerStatusColor(
    enabled: Boolean,
    health: ProviderHealthRecord?,
): Color =
    when {
        !enabled -> VueoPalette.Muted.copy(alpha = .55f)
        health?.status in setOf(
            ProviderHealthStatus.FAILED,
            ProviderHealthStatus.BLOCKED,
            ProviderHealthStatus.TIMEOUT,
            ProviderHealthStatus.UNAVAILABLE,
        ) -> MaterialTheme.colorScheme.error
        health?.status == ProviderHealthStatus.NEEDS_SETUP -> VueoPalette.Muted
        else -> Color.White.copy(alpha = .88f)
    }

internal fun providerRequestLabelOrNull(health: ProviderHealthRecord): String? {
    val parts = buildList {
        health.requestMediaType?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
        health.requestTmdbId?.takeIf { it.isNotBlank() }?.let { add("TMDB $it") }
        if (health.requestSeason != null && health.requestEpisode != null) {
            add("S${health.requestSeason.toString().padStart(2, '0')} E${health.requestEpisode.toString().padStart(2, '0')}")
        }
    }
    return parts.joinToString(" • ").takeIf { it.isNotBlank() }
}

internal fun providerRelevantTimingLabel(health: ProviderHealthRecord): String? {
    val elapsed = health.responseMs?.let { "${it} ms" }
    val timeout = health.timeoutMs?.let { "timeout ${it} ms" }
    return when {
        health.status == ProviderHealthStatus.TIMEOUT && elapsed != null && timeout != null -> "$elapsed • $timeout"
        health.status == ProviderHealthStatus.TIMEOUT && timeout != null -> timeout
        health.status == ProviderHealthStatus.SLOW && elapsed != null -> elapsed
        health.status == ProviderHealthStatus.FAILED && elapsed != null -> elapsed
        health.status == ProviderHealthStatus.UNAVAILABLE && elapsed != null -> elapsed
        health.status == ProviderHealthStatus.BLOCKED && elapsed != null -> elapsed
        else -> null
    }
}

internal fun providerFailureStage(
    health: ProviderHealthRecord,
    providerCodeReady: Boolean,
): String {
    val error = health.error.orEmpty().lowercase()
    return when {
        !providerCodeReady || "code is not installed" in error -> "Provider preparation"
        health.status == ProviderHealthStatus.NEEDS_SETUP -> "Provider configuration"
        health.status == ProviderHealthStatus.UNAVAILABLE || health.status == ProviderHealthStatus.BLOCKED -> "Network / upstream access"
        health.status == ProviderHealthStatus.TIMEOUT -> "Provider execution"
        health.status == ProviderHealthStatus.NO_RESULTS -> "Result extraction"
        else -> "Source discovery"
    }
}

internal fun providerFailureCategory(health: ProviderHealthRecord): String {
    val error = health.error.orEmpty().lowercase()
    return when {
        health.status == ProviderHealthStatus.ONLINE -> "Healthy"
        health.status == ProviderHealthStatus.SLOW -> "Slow response"
        health.status == ProviderHealthStatus.NO_RESULTS -> "No playable sources"
        health.status == ProviderHealthStatus.NEEDS_SETUP -> "Configuration required"
        health.status == ProviderHealthStatus.TIMEOUT -> "Execution timeout"
        health.status == ProviderHealthStatus.UNAVAILABLE -> "Host unavailable / DNS"
        health.status == ProviderHealthStatus.BLOCKED -> "Upstream blocked request"
        "not found" in error && health.requestSeason != null -> "Episode or source not found"
        "status 404" in error || "http 404" in error -> "HTTP not found"
        "status 429" in error || "http 429" in error -> "Rate limited"
        "status 5" in error || "http 5" in error -> "Upstream server error"
        health.status == ProviderHealthStatus.FAILED -> "Provider execution failed"
        else -> "Unknown"
    }
}

internal fun providerLikelyCause(
    health: ProviderHealthRecord,
    providerCodeReady: Boolean,
): String {
    val error = health.error.orEmpty().lowercase()
    val http = providerHttpStatus(health)
    return when {
        !providerCodeReady || "code is not installed" in error ->
            "Provider code is missing or not ready locally. Refresh the repository and run source discovery again."
        health.status == ProviderHealthStatus.NEEDS_SETUP ->
            "Provider configuration is incomplete. Required setup must be completed before source discovery can succeed."
        health.status == ProviderHealthStatus.TIMEOUT ->
            "Provider execution exceeded the captured runtime timeout. Inspect the raw log for the last request or parser step reached before timeout."
        health.status == ProviderHealthStatus.UNAVAILABLE ->
            "The captured run could not reach the upstream host. Inspect the raw log for DNS, connection, or host-resolution evidence."
        health.status == ProviderHealthStatus.BLOCKED ->
            "The captured run indicates upstream access was blocked. Inspect the HTTP/error evidence for the exact rejection."
        http == "404" ->
            "The captured upstream request returned HTTP 404. Check the generated route, title mapping, season/episode mapping, or changed upstream path."
        http == "429" ->
            "The captured upstream request returned HTTP 429 rate limiting. Provider request pacing or caching may need adjustment."
        http?.startsWith("5") == true ->
            "The captured upstream request returned a server-side HTTP error. Verify whether the upstream service or endpoint is currently failing."
        "not found" in error && health.requestSeason != null ->
            "The provider could not resolve the requested episode/source. Check season/episode mapping, URL construction, and extraction selectors."
        health.status == ProviderHealthStatus.NO_RESULTS ->
            "Provider execution completed but returned zero playable sources for the captured request. Check title mapping and extraction selectors against the current upstream response."
        health.status == ProviderHealthStatus.FAILED && health.error?.isNotBlank() == true ->
            "Provider execution failed with the captured error shown above. Use that error and the raw log to identify the failing request or parser step."
        health.status == ProviderHealthStatus.SLOW ->
            "Provider returned a slow response for the captured request. Timing evidence is shown above."
        health.status == ProviderHealthStatus.ONLINE ->
            "No provider failure was captured in the latest run."
        else ->
            "Cause not determined from captured evidence. Run source discovery again and inspect the raw technical log."
    }
}

internal fun providerHttpStatus(health: ProviderHealthRecord): String? {
    val combined = buildString {
        health.error?.let { append(it).append('\n') }
        health.logs.forEach { append(it).append('\n') }
    }
    val regexes = listOf(
        Regex("(?i)(?:http|status|status code|request failed with status)\\s*[:=]?\\s*(\\d{3})"),
        Regex("(?i)\\b(4\\d{2}|5\\d{2})\\b"),
    )
    return regexes.asSequence()
        .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1) }
        .firstOrNull()
}

internal fun providerDiagnosticSummary(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.mobile.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
): String = buildString {
    appendLine("VUEO Provider Debug Log")
    appendLine("Provider: ${provider.name} v${provider.version}")
    appendLine("Status: ${if (currentlyEnabled) health.status.label else "Disabled (last ${health.status.label})"}")
    providerRequestLabelOrNull(health)?.let { appendLine("Request: $it") }
    appendLine("Failure: ${providerFailureStage(health, providerCodeReady)} • ${providerFailureCategory(health)}")
    health.errorType?.takeIf { it.isNotBlank() }?.let { appendLine("Error type: ${sanitizeDiagnosticText(it)}") }
    providerHttpStatus(health)?.let { appendLine("HTTP: $it") }
    providerRelevantTimingLabel(health)?.let { appendLine("Timing: $it") }
    if (health.status == ProviderHealthStatus.NO_RESULTS || health.streamCount == 0) {
        appendLine("Result: ${health.streamCount} playable source${if (health.streamCount == 1) "" else "s"}")
    }
    if (!providerCodeReady) appendLine("Provider code: Missing or not ready")
    health.error?.takeIf { it.isNotBlank() }?.let { appendLine("Error: ${sanitizeDiagnosticText(it)}") }
    appendLine("Likely cause: ${providerLikelyCause(health, providerCodeReady)}")
}

internal fun providerDiagnosticFullLog(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.mobile.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
): String = buildString {
    append(providerDiagnosticSummary(repository, provider, health, currentlyEnabled, providerCodeReady))
    appendLine()
    appendLine("Raw technical log (sanitized)")
    append(providerRawDiagnosticLog(health))
}

internal fun providerRawDiagnosticLog(health: ProviderHealthRecord): String {
    val lines = buildList {
        health.error?.takeIf { it.isNotBlank() }?.let { add("ERROR: $it") }
        addAll(health.logs)
    }
    return if (lines.isEmpty()) {
        "No raw provider log was captured for this run."
    } else {
        lines.joinToString("\n") { sanitizeDiagnosticText(it) }
    }
}

internal fun sanitizeDiagnosticText(raw: String): String {
    var text = raw

    text = text.replace(
        Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\\s*[:=]\\s*([^\\s,;]+)"),
    ) { match -> "${match.groupValues[1]}=<redacted>" }

    text = text.replace(
        Regex("https?://[^\\s\\]\\[<>\\\"']+"),
    ) { match -> sanitizeDiagnosticUrl(match.value) }

    return text.take(12_000)
}

internal fun sanitizeDiagnosticUrl(url: String): String {
    val queryIndex = url.indexOf('?')
    if (queryIndex < 0) return url

    val base = url.substring(0, queryIndex)
    val rawQuery = url.substring(queryIndex + 1)
    if (rawQuery.isBlank()) return base

    val safeQuery = rawQuery
        .split('&')
        .take(12)
        .mapNotNull { part ->
            val key = part.substringBefore('=').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "$key=<redacted>"
        }
        .joinToString("&")

    return if (safeQuery.isBlank()) base else "$base?$safeQuery"
}

internal fun copyProviderDiagnostic(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Diagnostic copied", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = VueoPalette.SurfaceElevated,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "VUEO",
                color = VueoPalette.Muted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.7.sp,
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = VueoPalette.Muted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        action?.invoke()
    }
}
