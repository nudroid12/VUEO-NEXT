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


@Composable
internal fun ContentManagerScreen(
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
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 116.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "content-manager-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(modifier = Modifier.weight(1f)) {
                    VueoSettingsTitle(
                        title = "Content Manager",
                        subtitle = "Manage addons, providers and catalogs.",
                    )
                }
            }
        }

        item(key = "content-manager-health") {
            ContentHealthSummary(
                installed = addons.size,
                online = onlineCount,
                slow = slowCount,
                failed = failedCount,
            )
        }

        item(key = "content-manager-group") {
            VueoSettingsHubGroup(label = "CONTENT") {
                VueoSettingsHubRow(
                    title = "Addons",
                    subtitle = "Catalogs, metadata, streams and subtitles.",
                    status = "${addons.size} installed",
                    icon = Icons.Default.Extension,
                    onClick = onAddons,
                )
                VueoSettingsHubDivider()
                VueoSettingsHubRow(
                    title = "Plugins & Providers",
                    subtitle = "Repositories, runtime providers, health and diagnostics.",
                    status = "$repositoryCount repos • $providerCount providers",
                    icon = Icons.Default.SettingsInputComponent,
                    onClick = onPlugins,
                )
                VueoSettingsHubDivider()
                VueoSettingsHubRow(
                    title = "Catalog Order",
                    subtitle = "Choose the order catalogs appear on Home.",
                    status = "$catalogCount catalogs",
                    icon = Icons.Default.VideoLibrary,
                    onClick = onCatalogOrder,
                )
            }
        }

        item(key = "content-manager-note") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VueoPalette.Accent.copy(alpha = .75f))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Provider health feeds Smart Source ranking, so slower or unavailable providers do not need to block faster sources.",
                    color = VueoPalette.Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
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
    val catalogEnabled: Boolean,
)

@Composable
internal fun CatalogOrderScreen(
    engine: UnifiedMediaEngine,
    store: AddonStore,
    contentVersion: Int,
    onContentChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val entries =
        remember(contentVersion) {
            engine.stremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter { it.canLoadWithoutExtras }
                        .map { catalog ->
                            val key =
                                "${extension.descriptor.id}:${catalog.type}:${catalog.id}"
                            CatalogOrderEntry(
                                key = key,
                                title = catalog.name ?: catalog.id,
                                providerName = extension.descriptor.name,
                                type = catalog.type.replaceFirstChar { it.uppercase() },
                                addonEnabled = engine.isExtensionEnabled(extension.descriptor.id),
                                catalogEnabled = store.isCatalogEnabled(key),
                            )
                        }
                }
        }

    val entryByKey = remember(entries) { entries.associateBy { it.key } }

    var order by remember(contentVersion, entries) {
        mutableStateOf(store.reconcileCatalogOrder(entries.map { it.key }))
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in order.indices || target !in order.indices) return
        val next = order.toMutableList()
        val moved = next.removeAt(index)
        next.add(target, moved)
        order = next
        store.setCatalogOrder(next)
        onContentChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
    ) {
        ScreenHeader(
            title = "Catalog Order",
            subtitle = "Arrange how catalogs appear on Home",
            onBack = onBack,
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No catalogs available", color = VueoPalette.Muted)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 116.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "catalog-order-note") {
                Text(
                    text = "Top catalogs appear first. Hide any catalog without changing its saved position.",
                    color = VueoPalette.Muted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }

            order.forEachIndexed { index, key ->
                val entry = entryByKey[key] ?: return@forEachIndexed
                item(key = "catalog-order:$key") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VueoPalette.SurfaceElevated,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = VueoPalette.SurfaceStrong,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }

                            Spacer(Modifier.width(11.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.title,
                                    color = if (entry.addonEnabled) Color.White else VueoPalette.Muted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${entry.providerName} • ${entry.type}",
                                    color = VueoPalette.Muted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                when {
                                    !entry.addonEnabled ->
                                        Text(
                                            text = "Addon disabled",
                                            color = VueoPalette.Muted.copy(alpha = .72f),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    !entry.catalogEnabled ->
                                        Text(
                                            text = "Hidden",
                                            color = VueoPalette.Muted.copy(alpha = .72f),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                }
                            }

                            Switch(
                                checked = entry.catalogEnabled,
                                onCheckedChange = { enabled ->
                                    store.setCatalogEnabled(entry.key, enabled)
                                    onContentChanged()
                                },
                            )

                            IconButton(
                                enabled = index > 0,
                                onClick = { move(index, -1) },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Text("↑", fontSize = 19.sp)
                            }
                            IconButton(
                                enabled = index < order.lastIndex,
                                onClick = { move(index, 1) },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Text("↓", fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentHealthSummary(
    installed: Int,
    online: Int,
    slow: Int,
    failed: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VueoPalette.SurfaceElevated,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ContentMetric(Modifier.weight(1f), installed.toString(), "Installed")
            ContentMetric(Modifier.weight(1f), online.toString(), "Online")
            ContentMetric(Modifier.weight(1f), slow.toString(), "Slow")
            ContentMetric(Modifier.weight(1f), failed.toString(), "Failed")
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
            color = VueoPalette.Accent,
            fontSize = 18.sp,
            fontWeight =
                FontWeight.Black,
        )

        Text(
            label,
            color = VueoPalette.Muted,
            fontSize = 9.sp,
        )
    }
}

