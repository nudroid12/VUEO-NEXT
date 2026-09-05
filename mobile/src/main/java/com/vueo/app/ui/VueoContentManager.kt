package com.vueo.app.ui

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
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.enrichment.ContentWarningRepository
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
                        .background(VueoPalette.BrandLime.copy(alpha = .75f))
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
            color = VueoPalette.BrandLime,
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

@Composable
internal fun AddonsScreen(
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
            subtitle = "Catalogs, metadata, streams & subtitles",
            onBack = onBack,
            action = {
                FilledIconButton(
                    onClick = { showInstallDialog = true },
                    modifier = Modifier.size(44.dp),
                ) {
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
                        "Install an HTTPS addon manifest URL. Catalogs exposed by the addon can then populate Home.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showInstallDialog = true }) {
                        Text("Add Addon")
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
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
                                        "Other addon resources"
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
            title = { Text("Install Addon") },
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

private fun neutralizePlatformCopy(value: String): String =
    value
        .replace(Regex("(?i)\\bfor\\s+stremio\\b"), "")
        .replace(Regex("(?i)\\bstremio[- ]compatible\\b"), "compatible")
        .replace(Regex("(?i)\\bstremio\\b"), "the app")
        .replace(Regex("(?i)\\bnuvio[- ]style\\b"), "provider")
        .replace(Regex("(?i)\\bnuvio\\b"), "the app")
        .replace(Regex("\\s+([.,])"), "$1")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

@Composable
private fun AddonCard(
    addon: MediaExtension,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = VueoPalette.SurfaceElevated,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(VueoPalette.SurfaceStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .92f),
                        modifier = Modifier.size(21.dp),
                    )
                }

                Spacer(Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = addon.descriptor.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${addon.descriptor.version}  •  " +
                            "${addon.descriptor.catalogs.size} catalogs  •  " +
                            "${addon.descriptor.resources.size} resources",
                        color = VueoPalette.Muted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            addon.descriptor.description
                ?.let(::neutralizePlatformCopy)
                ?.takeIf { it.isNotBlank() }
                ?.let { description ->
                    Text(
                        text = description,
                        color = VueoPalette.Muted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = addon.descriptor.resources
                        .sorted()
                        .joinToString("  •  ")
                        .ifBlank { "Addon" },
                    color = VueoPalette.Muted.copy(alpha = .72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
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
}

@Composable
internal fun PluginsScreen(
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
                "Provider repositories",
            onBack = onBack,
            action = {
                FilledIconButton(
                    onClick = {
                        showAddDialog = true
                    },
                    modifier = Modifier.size(44.dp),
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
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 116.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            item(key = "plugins-master") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = VueoPalette.SurfaceElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Providers",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${repositories.size} repositories • ${store.enabledProviderCount()} enabled",
                                color = VueoPalette.Muted,
                                fontSize = 10.5.sp,
                            )
                        }
                        Switch(
                            checked = pluginsEnabled,
                            onCheckedChange = {
                                pluginsEnabled = it
                                store.setPluginsEnabled(it)
                            },
                        )
                    }
                }
            }

            item(key = "plugins-health") {
                val summary =
                    healthStore.summary(
                        repositories = repositories,
                        pluginStore = store,
                    )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = VueoPalette.SurfaceElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Provider Health",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${summary.online} online • ${summary.slow} slow • " +
                                    "${summary.noResults} no results • ${summary.failed} failed",
                                color = VueoPalette.Muted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { healthRevision++ },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh health",
                                tint = Color.White.copy(alpha = .82f),
                                modifier = Modifier.size(19.dp),
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
                                "Add a provider repository URL.",
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
                        contentPadding = PaddingValues(end = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                        fontSize = 12.sp,
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

            item(key = "plugins-runtime-note") {
                Text(
                    text = "Disabled repositories are skipped during source discovery.",
                    color = VueoPalette.Muted.copy(alpha = .72f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                )
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
                    repository.providers.forEach { provider ->
                        val enabled = store.isProviderEnabled(repository, provider)
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
private fun ProviderHealthRow(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
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
                        fontSize = 11.sp,
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
private fun ProviderDiagnosticPanel(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
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
private fun DiagnosticFact(
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
private fun providerStatusColor(
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

private fun providerRequestLabelOrNull(health: ProviderHealthRecord): String? {
    val parts = buildList {
        health.requestMediaType?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
        health.requestTmdbId?.takeIf { it.isNotBlank() }?.let { add("TMDB $it") }
        if (health.requestSeason != null && health.requestEpisode != null) {
            add("S${health.requestSeason.toString().padStart(2, '0')} E${health.requestEpisode.toString().padStart(2, '0')}")
        }
    }
    return parts.joinToString(" • ").takeIf { it.isNotBlank() }
}

private fun providerRelevantTimingLabel(health: ProviderHealthRecord): String? {
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

private fun providerFailureStage(
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

private fun providerFailureCategory(health: ProviderHealthRecord): String {
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

private fun providerLikelyCause(
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

private fun providerHttpStatus(health: ProviderHealthRecord): String? {
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

private fun providerDiagnosticSummary(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
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

private fun providerDiagnosticFullLog(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord,
    currentlyEnabled: Boolean,
    providerCodeReady: Boolean,
): String = buildString {
    append(providerDiagnosticSummary(repository, provider, health, currentlyEnabled, providerCodeReady))
    appendLine()
    appendLine("Raw technical log (sanitized)")
    append(providerRawDiagnosticLog(health))
}

private fun providerRawDiagnosticLog(health: ProviderHealthRecord): String {
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

private fun sanitizeDiagnosticText(raw: String): String {
    var text = raw

    text = text.replace(
        Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\\s*[:=]\\s*([^\\s,;]+)"),
    ) { match -> "${match.groupValues[1]}=<redacted>" }

    text = text.replace(
        Regex("https?://[^\\s\\]\\[<>\\\"']+"),
    ) { match -> sanitizeDiagnosticUrl(match.value) }

    return text.take(12_000)
}

private fun sanitizeDiagnosticUrl(url: String): String {
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

private fun copyProviderDiagnostic(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Diagnostic copied", Toast.LENGTH_SHORT).show()
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
