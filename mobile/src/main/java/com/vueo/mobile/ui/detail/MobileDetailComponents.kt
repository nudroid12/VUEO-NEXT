package com.vueo.mobile.ui

import com.vueo.shared.core.storage.ContinueWatchingPolicy

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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

internal fun baseDetailsRatings(
    media: MediaItem,
): List<MediaRating> =
    buildList {
        media.imdbRating
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "imdb", value = it)) }

        media.tmdbRating
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "tmdb", value = it)) }
    }

internal fun cleanDetailsReleaseInfo(
    releaseInfo: String?,
): String? =
    releaseInfo
        ?.trim()
        ?.trimEnd { char ->
            char.isWhitespace() || char == '-' || char == '–' || char == '—'
        }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal fun formatDetailsRuntime(
    minutes: Int,
): String {
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        remaining <= 0 -> "${hours}h"
        else -> "${hours}h ${remaining}m"
    }
}

@Composable
internal fun DetailsFactsRow(
    facts: List<String>,
    imdbRating: MediaRating?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        if (facts.isNotEmpty()) {
            Text(
                text =
                    facts.joinToString(
                        " • "
                    ),
                modifier =
                    Modifier.weight(
                        1f,
                        fill = false,
                    ),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        } else {
            Spacer(
                Modifier.weight(1f)
            )
        }

        imdbRating?.let { rating ->
            ImdbRatingMark(
                rating = rating
            )
        }
    }
}

@Composable
internal fun ImdbRatingMark(
    rating: MediaRating,
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                5.dp
            ),
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable
                        .rating_imdb
                ),
            contentDescription =
                "IMDb",
            modifier =
                Modifier.size(
                    width = 30.dp,
                    height = 17.dp,
                ),
            contentScale =
                ContentScale.Fit,
        )

        Text(
            text =
                rating.displayValue(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight =
                FontWeight.Black,
        )
    }
}

@Composable
internal fun StableDetailsMetadata(
    facts: List<String>,
    ratings: List<MediaRating>,
    vueoMatchPercent: Int?,
    media: MediaItem,
) {
    val certification = ContentWarningRepository.tvCertification(media)
    val imdbId = ContentWarningRepository.titleImdbId(media)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (facts.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 18.dp)) { DetailsFactsText(facts) }
        }
        key(imdbId) {
            val warnings by produceState(emptyList<String>()) {
                value = imdbId?.let { ContentWarningRepository.getForTvTitle(it) }.orEmpty()
            }
            val advisory = listOfNotNull(certification) + warnings
            if (advisory.isNotEmpty()) {
                Text(
                    text = advisory.joinToString(" · "),
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = Color.White.copy(alpha = .78f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (ratings.isNotEmpty() || vueoMatchPercent != null) {
            MediaRatingsStrip(ratings, vueoMatchPercent)
        }
    }
}

@Composable
private fun DetailsFactsText(facts: List<String>) {
    Text(
        text = facts.joinToString(" • "),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailsMetadataArrival(content: @Composable () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(160, easing = VueoMotion.EaseOut),
        label = "Details rating fade",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

@Composable
internal fun MediaRatingsStrip(
    ratings: List<MediaRating>,
    vueoMatchPercent: Int?,
    horizontalPadding: Dp = 18.dp,
) {
    val ratingPriority =
        remember {
            mapOf(
                "imdb" to 0,
                "tomatoes" to 1,
                "metacritic" to 2,
                "tmdb" to 3,
                "trakt" to 4,
            )
        }

    val orderedRatings =
        remember(ratings) {
            ratings
                .distinctBy {
                    it.source
                }
                .sortedBy {
                    ratingPriority[
                        it.source
                    ] ?: Int.MAX_VALUE
                }
        }

    LazyRow(
        contentPadding =
            PaddingValues(
                horizontal = horizontalPadding
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        items(
            orderedRatings,
            key = {
                it.source
            },
        ) { rating ->
            DetailsMetadataArrival {
                MediaRatingMark(rating = rating)
            }
        }

        vueoMatchPercent?.let { percent ->
            item(
                key = "dna_match"
            ) {
                DetailsMetadataArrival {
                    DnaMatchMark(percent = percent)
                }
            }
        }
    }
}

@Composable
internal fun MediaRatingMark(
    rating: MediaRating,
) {
    val logo =
        when (rating.source) {
            "imdb" ->
                R.drawable.rating_imdb

            "tomatoes" ->
                R.drawable.rating_rotten_tomatoes

            "metacritic" ->
                R.drawable.rating_metacritic

            "tmdb" ->
                R.drawable.rating_tmdb

            "trakt" ->
                R.drawable.rating_trakt

            else -> null
        }

    val valueColor =
        when (rating.source) {
            "imdb" -> Color(0xFFF5C518)
            "tomatoes" -> Color(0xFFFA320A)
            "metacritic" -> Color(0xFFFFCC33)
            "tmdb" -> Color(0xFF01B4E4)
            "trakt" -> Color(0xFFED1C24)
            else -> Color.White
        }

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                5.dp
            ),
    ) {
        if (logo != null) {
            Image(
                painter =
                    painterResource(
                        logo
                    ),
                contentDescription =
                    rating.label,
                modifier =
                    Modifier.size(
                        width =
                            if (
                                rating.source ==
                                    "imdb"
                            ) {
                                30.dp
                            } else {
                                17.dp
                            },
                        height = 17.dp,
                    ),
                contentScale =
                    ContentScale.Fit,
            )
        } else {
            Text(
                text = rating.compactLabel,
                color = VueoPalette.Muted,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold,
            )
        }

        Text(
            text = rating.displayValue(),
            color = valueColor,
            fontSize = 13.sp,
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
internal fun DnaMatchMark(
    percent: Int,
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp
            ),
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable
                        .ic_dna_match
                ),
            contentDescription =
                "DNA Match",
            modifier =
                Modifier.size(
                    18.dp
                ),
            contentScale =
                ContentScale.Fit,
        )

        Text(
            text = "DNA",
            color = VueoPalette.Muted,
            fontSize = 10.sp,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
        )

        Text(
            text = "$percent%",
            color = VueoPalette.Accent,
            fontSize = 13.sp,
            fontWeight =
                FontWeight.Black,
            maxLines = 1,
        )
    }
}


@Composable
internal fun MediaCompanySection(
    title: String,
    companies: List<MediaCompany>,
    onCompanyClick: (MediaCompany) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
    ) {
        Text(
            text = title,
            modifier =
                Modifier.padding(
                    horizontal = 18.dp
                ),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight =
                FontWeight.Black,
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 18.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            items(
                companies.take(12),
                key = {
                    it.name
                },
            ) { company ->
                Surface(
                    modifier =
                        Modifier
                            .width(116.dp)
                            .height(66.dp)
                            .clickable { onCompanyClick(company) },
                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),
                    color =
                        Color.White.copy(
                            alpha = .94f
                        ),
                ) {
                    if (
                        !company.logo.isNullOrBlank()
                    ) {
                        NetworkImage(
                            url = company.logo,
                            contentDescription =
                                company.name,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                            contentScale =
                                ContentScale.Fit,
                            fallbackText =
                                company.name,
                            transparentBackground =
                                true,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Text(
                                text =
                                    company.name,
                                color =
                                    Color.Black.copy(
                                        alpha = .78f
                                    ),
                                fontSize = 10.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                maxLines = 2,
                                overflow =
                                    TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun mobileCrewCredits(media: MediaItem): List<MediaPerson> {
    val rolesByName = linkedMapOf<String, MutableSet<String>>()
    val displayNames = mutableMapOf<String, String>()
    val profiles = media.cast.filter { !it.profile.isNullOrBlank() }
        .associateBy { it.name.trim().lowercase() }

    for ((role, names) in listOf(
        "Creator" to media.creators,
        "Director" to media.directors,
        "Writer" to media.writers,
    )) {
        for (name in names) {
            val displayName = name.trim()
            if (displayName.isEmpty()) continue
            val key = displayName.lowercase()
            displayNames.putIfAbsent(key, displayName)
            rolesByName.getOrPut(key) { linkedSetOf() }.add(role)
        }
    }

    return rolesByName.map { (key, roles) ->
        MediaPerson(
            name = displayNames.getValue(key),
            role = roles.joinToString(" & "),
            profile = profiles[key]?.profile,
        )
    }
}

@Composable
internal fun MediaCastSection(
    cast: List<MediaPerson>,
    credits: List<MediaPerson>,
    onPersonClick: (MediaPerson) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (cast.isEmpty()) "Credits" else "Cast",
            modifier = Modifier.padding(horizontal = 18.dp),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                cast.distinctBy { it.name to it.character.orEmpty() },
                key = { "cast:${it.name}:${it.character.orEmpty()}" },
            ) { person ->
                MediaCastCard(person, person.character) { onPersonClick(person) }
            }
            if (cast.isNotEmpty() && credits.isNotEmpty()) {
                item(key = "credits-divider") {
                    Box(Modifier.padding(horizontal = 4.dp)
                        .width(1.dp).height(90.dp)
                        .background(Color.White.copy(alpha = .22f)))
                }
            }
            items(credits, key = { "credit:${it.name.lowercase()}" }) { person ->
                MediaCastCard(person, person.role)
            }
        }
    }
}

@Composable
private fun MediaCastCard(person: MediaPerson, roleLabel: String?, onClick: (() -> Unit)? = null) {
    Column(
        modifier =
            Modifier
                .width(78.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                4.dp
            ),
    ) {
        Surface(
            modifier =
                Modifier.size(
                    68.dp
                ),
            shape = CircleShape,
            color =
                VueoPalette
                    .SurfaceStrong,
        ) {
            NetworkImage(
                url = person.profile,
                contentDescription =
                    person.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    person.name
                        .take(1)
                        .uppercase(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = person.name,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            roleLabel?.takeIf { it.isNotBlank() }?.let { role ->
                Text(
                    text = role,
                    color = VueoPalette.Muted,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun detailsPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    entries: List<LibraryPlaybackEntry>,
): LibraryPlaybackEntry? =
    entries.firstOrNull { entry ->
        entry.media.id == media.id &&
            entry.media.type ==
                media.type &&
            if (
                media.type == "series"
            ) {
                episode != null &&
                    entry.season ==
                        episode.season &&
                    entry.episode ==
                        episode.episode
            } else {
                true
            }
    }

internal fun orderedDetailsEpisodes(
    episodes: List<EpisodeItem>,
): List<EpisodeItem> {
    val regularEpisodes =
        episodes.filter {
            it.season > 0
        }

    return (
        if (regularEpisodes.isNotEmpty()) {
            regularEpisodes
        } else {
            episodes
        }
    )
        .sortedWith(
            compareBy<EpisodeItem> {
                it.season
            }.thenBy {
                it.episode
            }
        )
        .distinctBy {
            it.season to
                it.episode
        }
}

internal fun detailsPlaybackTargetEpisode(
    media: MediaItem,
    entries: List<LibraryPlaybackEntry>,
    initialEntry: LibraryPlaybackEntry?,
    watchedEpisodes: Set<String> = emptySet(),
): EpisodeItem? = ContinueWatchingPolicy.targetEpisode(media, entries, initialEntry, watchedEpisodes)

internal fun moreLikeThisAttribution(
    usesTmdb: Boolean,
): String =
    if (usesTmdb) {
        "Powered by TMDB"
    } else {
        "Powered by Trakt"
    }

