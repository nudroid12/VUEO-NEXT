package com.vueo.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.detail.TvDetailScreen
import com.vueo.tv.home.TvHomeScreen
import com.vueo.tv.library.TvLibraryScreen
import com.vueo.tv.player.TvPlayerScreen
import com.vueo.tv.profile.TvProfilePickerScreen
import com.vueo.tv.search.TvSearchScreen
import com.vueo.tv.settings.TvSettingsScreen
import com.vueo.tv.source.TvSourceScreen
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.launch

private enum class TvRoute {
    STARTUP,
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
    PROFILE,
    DETAIL,
    SOURCE,
    PLAYER,
}

@Composable
fun VueoTvApp() {
    val context = LocalContext.current
    val runtime = remember { TvRuntime(context.applicationContext) }

    var route by remember { mutableStateOf(TvRoute.STARTUP) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
    var sourceBundle by remember { mutableStateOf<TvSourceBundle?>(null) }
    var selectedSource by remember { mutableStateOf<StreamSource?>(null) }
    var initialPositionMs by remember { mutableLongStateOf(0L) }

    var profileReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var detailReturnRoute by remember { mutableStateOf(TvRoute.HOME) }
    var sourceReturnRoute by remember { mutableStateOf(TvRoute.DETAIL) }

    LaunchedEffect(runtime) {
        runtime.boot()
        route =
            if (runtime.profileStore.shouldShowPickerOnStartup()) {
                TvRoute.PROFILE
            } else {
                TvRoute.HOME
            }
        profileReturnRoute = TvRoute.HOME

        launch { runtime.prepareProvidersInBackground() }
    }

    fun navigate(label: String) {
        route = when (label) {
            "Home" -> TvRoute.HOME
            "Search" -> TvRoute.SEARCH
            "Library" -> TvRoute.LIBRARY
            "Settings" -> TvRoute.SETTINGS
            else -> route
        }
    }

    fun openProfile(from: TvRoute) {
        profileReturnRoute = from
        route = TvRoute.PROFILE
    }

    fun openDetail(media: MediaItem, from: TvRoute) {
        selectedMedia = media
        selectedEpisode = null
        initialPositionMs = 0L
        detailReturnRoute = from
        route = TvRoute.DETAIL
    }

    fun resume(entry: LibraryPlaybackEntry, from: TvRoute) {
        selectedMedia = entry.media
        selectedEpisode =
            if (
                entry.media.type.lowercase() in setOf("series", "tv") &&
                entry.season != null &&
                entry.episode != null
            ) {
                EpisodeItem(
                    id = entry.videoId,
                    title = entry.episodeTitle ?: "Episode ${entry.episode}",
                    season = entry.season,
                    episode = entry.episode,
                )
            } else null
        initialPositionMs = entry.positionMs
        sourceReturnRoute = from
        route = TvRoute.SOURCE
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvDesign.White,
            background = TvDesign.Black,
            surface = TvDesign.Surface,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(TvDesign.Black),
        ) {
            when (route) {
                TvRoute.STARTUP -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "VUEO",
                            color = TvDesign.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                    }
                }

                TvRoute.HOME -> {
                    TvHomeScreen(
                        runtime = runtime,
                        refreshToken = refreshToken,
                        onNavigate = ::navigate,
                        onOpenMedia = { openDetail(it, TvRoute.HOME) },
                        onResume = { resume(it, TvRoute.HOME) },
                        onProfile = { openProfile(TvRoute.HOME) },
                    )
                }

                TvRoute.SEARCH -> {
                    TvSearchScreen(
                        runtime = runtime,
                        onNavigate = ::navigate,
                        onProfile = { openProfile(TvRoute.SEARCH) },
                        onOpenMedia = { openDetail(it, TvRoute.SEARCH) },
                        onBack = { route = TvRoute.HOME },
                    )
                }

                TvRoute.LIBRARY -> {
                    TvLibraryScreen(
                        runtime = runtime,
                        refreshToken = refreshToken,
                        onNavigate = ::navigate,
                        onProfile = { openProfile(TvRoute.LIBRARY) },
                        onOpenMedia = { openDetail(it, TvRoute.LIBRARY) },
                        onResume = { resume(it, TvRoute.LIBRARY) },
                        onBack = { route = TvRoute.HOME },
                    )
                }

                TvRoute.SETTINGS -> {
                    TvSettingsScreen(
                        runtime = runtime,
                        onNavigate = ::navigate,
                        onProfile = { openProfile(TvRoute.SETTINGS) },
                        onBack = { route = TvRoute.HOME },
                    )
                }

                TvRoute.PROFILE -> {
                    // Explicit preservation exception for the clean TV rebuild:
                    // Who’s Watching, Manage Profiles and Add/Edit Profile keep
                    // the approved TV experience while the app runtime around
                    // them is rebuilt from Mobile/Shared Core behavior.
                    TvProfilePickerScreen(
                        profileStore = runtime.profileStore,
                        onProfileSelected = {
                            refreshToken++
                                                route = profileReturnRoute
                        },
                        onProfilesChanged = {
                            refreshToken++
                        },
                    )
                }

                TvRoute.DETAIL -> {
                    val media = selectedMedia
                    if (media == null) {
                        route = detailReturnRoute
                    } else {
                        TvDetailScreen(
                            runtime = runtime,
                            initial = media,
                            onBack = { route = detailReturnRoute },
                            onWatch = { enriched, episode ->
                                selectedMedia = enriched
                                selectedEpisode = episode
                                initialPositionMs = 0L
                                sourceReturnRoute = TvRoute.DETAIL
                                route = TvRoute.SOURCE
                            },
                            onLibraryChanged = { refreshToken++ },
                        )
                    }
                }

                TvRoute.SOURCE -> {
                    val media = selectedMedia
                    if (media == null) {
                        route = sourceReturnRoute
                    } else {
                        TvSourceScreen(
                            runtime = runtime,
                            media = media,
                            episode = selectedEpisode,
                            onBack = { route = sourceReturnRoute },
                            onPlay = { bundle, source ->
                                sourceBundle = bundle
                                selectedSource = source
                                route = TvRoute.PLAYER
                            },
                        )
                    }
                }

                TvRoute.PLAYER -> {
                    val media = selectedMedia
                    val bundle = sourceBundle
                    val source = selectedSource
                    if (media == null || bundle == null || source == null) {
                        route = TvRoute.SOURCE
                    } else {
                        TvPlayerScreen(
                            runtime = runtime,
                            media = media,
                            episode = selectedEpisode,
                            bundle = bundle,
                            source = source,
                            initialPositionMs = initialPositionMs,
                            onBack = { route = TvRoute.SOURCE },
                            onLibraryChanged = { refreshToken++ },
                        )
                    }
                }
            }
        }
    }
}
