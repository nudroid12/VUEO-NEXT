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

    var showRuntimeDiagnostics by remember {
        mutableStateOf(false)
    }
    var showProviderHealth by remember {
        mutableStateOf(false)
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

    if (showProviderHealth) {
        ProviderHealthOverviewScreen(
            repositories = repositories,
            store = store,
            healthStore = healthStore,
            healthRevision = healthRevision,
            onBack = { showProviderHealth = false },
        )
        return
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProviderHealth = true },
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
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Open Provider Health",
                            tint = Color.White.copy(alpha = .72f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            item(key = "plugins-runtime-diagnostics") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRuntimeDiagnostics = true },
                    shape = RoundedCornerShape(17.dp),
                    color = VueoPalette.SurfaceElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Performance & Crash Diagnostics",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Source scan timing, UI stalls, memory and crash evidence.",
                                color = VueoPalette.Muted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .82f),
                            modifier = Modifier.size(19.dp),
                        )
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

    if (showRuntimeDiagnostics) {
        RuntimeDiagnosticsDialog(
            onDismiss = { showRuntimeDiagnostics = false },
        )
    }
}

