package com.vueo.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.vueo.shared.core.home.HomeRecommendationPolicy
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvPosterActionDialog
import com.vueo.tv.ui.TvSidebar

class TvHomeRetainedState internal constructor(runtime: TvRuntime) {
    var catalogRows by mutableStateOf(runtime.cachedHomeRows())
        internal set
    var loading by mutableStateOf(catalogRows.isEmpty())
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var libraryRevision by mutableIntStateOf(0)
        internal set

    internal var loadedRefreshToken = Int.MIN_VALUE
    internal var presentationRefreshToken = Int.MIN_VALUE
    internal var presentationLibraryRevision = Int.MIN_VALUE
    internal var presentationCatalogRows: List<CatalogRow>? = null
    internal var presentationRows: List<TvHomeRow> = emptyList()
}

@Composable
fun rememberTvHomeRetainedState(runtime: TvRuntime): TvHomeRetainedState =
    remember(runtime) { TvHomeRetainedState(runtime) }

/**
 * Home's VUEO boundary.
 *
 * Runtime/data/routing stay VUEO. Everything below this boundary is the new
 * Home presentation and focus system. The shared sidebar is intentionally
 * left untouched in 32B because sidebar redesign is being handled separately.
 */
@Composable
fun TvHomeScreen(
    runtime: TvRuntime,
    retainedState: TvHomeRetainedState,
    refreshToken: Int,
    onNavigate: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val catalogRows = retainedState.catalogRows
    val loading = retainedState.loading
    val error = retainedState.error
    var actionEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    val libraryRevision = retainedState.libraryRevision

    LaunchedEffect(runtime, refreshToken) {
        if (retainedState.loadedRefreshToken != refreshToken) {
            retainedState.loading = retainedState.catalogRows.isEmpty()
            retainedState.error = null

            runCatching {
                runtime.homeRows(
                    forceRefresh = false,
                    onPartial = { partialRows ->
                        if (partialRows.isNotEmpty()) {
                            retainedState.catalogRows = partialRows
                            retainedState.loading = false
                        }
                    },
                )
            }
                .onSuccess { rows ->
                    if (rows.isNotEmpty()) retainedState.catalogRows = rows
                }
                .onFailure { failure ->
                    if (retainedState.catalogRows.isEmpty()) {
                        retainedState.error = failure.message ?: "Unable to load Home"
                    }
                }

            retainedState.loading =
                retainedState.catalogRows.isEmpty() && !runtime.isHomeCatalogRuntimeReady()
            retainedState.loadedRefreshToken = refreshToken
        }
    }

    val presentationIsCurrent =
        retainedState.presentationRefreshToken == refreshToken &&
            retainedState.presentationLibraryRevision == libraryRevision &&
            retainedState.presentationCatalogRows === catalogRows

    val rows = if (presentationIsCurrent) {
        retainedState.presentationRows
    } else {
        val continueWatching = runtime.libraryStore.continueWatching().take(12)
        val watchHistory = runtime.libraryStore.history()
        val activeProfileId = runtime.profileStore.activeProfileId()
        val personalizedHomeEnabled =
            runtime.dnaPreferences.shouldPersonalizeRecommendations(activeProfileId)
        val homeRecommendations = HomeRecommendationPolicy.build(
            catalogRows = catalogRows,
            watchHistory = watchHistory,
            dnaEngine = runtime.dnaEngine,
            personalizationEnabled = personalizedHomeEnabled,
            limit = 12,
        )
        val rebuiltRows = buildList {
            if (continueWatching.isNotEmpty()) {
                add(
                    TvHomeRow(
                        key = "continue-watching",
                        title = "Continue Watching",
                        kind = TvHomeRowKind.CONTINUE_WATCHING,
                        entries = continueWatching.map { playback ->
                            TvHomeEntry.Resume(
                                key = "continue:${playback.mediaKey}",
                                media = playback.media,
                                playback = playback,
                            )
                        },
                    )
                )
            }

            if (homeRecommendations.forYou.size >= 4) {
                add(
                    TvHomeRow(
                        key = "for-you",
                        title = "For You",
                        kind = TvHomeRowKind.POSTERS,
                        entries = homeRecommendations.forYou.map { media ->
                            TvHomeEntry.Media(
                                key = "for-you:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            val becauseSeed = homeRecommendations.becauseYouWatchedSeed
            if (becauseSeed != null && homeRecommendations.becauseYouWatched.size >= 4) {
                add(
                    TvHomeRow(
                        key = "because-you-watched",
                        title = "Because You Watched ${becauseSeed.name}",
                        kind = TvHomeRowKind.POSTERS,
                        entries = homeRecommendations.becauseYouWatched.map { media ->
                            TvHomeEntry.Media(
                                key = "because:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            catalogRows.forEach { row ->
                if (row.items.isNotEmpty()) {
                    add(
                        TvHomeRow(
                            key = "catalog:${row.id}",
                            title = row.title,
                            kind = TvHomeRowKind.POSTERS,
                            entries = row.items.mapIndexed { index, media ->
                                TvHomeEntry.Media(
                                    key = "${row.id}:$index:${media.type}:${media.id}",
                                    media = media,
                                )
                            },
                        )
                    )
                }
            }
        }
        retainedState.presentationRows = rebuiltRows
        retainedState.presentationRefreshToken = refreshToken
        retainedState.presentationLibraryRevision = libraryRevision
        retainedState.presentationCatalogRows = catalogRows
        rebuiltRows
    }

    val contentFocusRequester = remember { FocusRequester() }

    // Sidebar is held as-is for now. New Home content only interacts with it
    // through this one focus boundary.
    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    var navExpanded by remember { mutableStateOf(false) }

    fun focusSidebar() {
        navExpanded = true
        runCatching { navRequesters.getValue("Home").requestFocus() }
    }

    BackHandler {
        if (navExpanded) onBack() else focusSidebar()
    }

    LaunchedEffect(rows.isEmpty(), loading, error) {
        if (rows.isEmpty() && !loading) {
            navExpanded = true
            runCatching { navRequesters.getValue("Home").requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvHomePresentation(
            rows = rows,
            loading = loading,
            error = error,
            navigationVisible = navExpanded,
            contentFocusRequester = contentFocusRequester,
            onContentFocused = { navExpanded = false },
            onOpenNavigation = ::focusSidebar,
            onOpen = { entry -> entry.open(onOpenMedia, onResume) },
            onLongClick = { entry -> actionEntry = entry },
            modifier = Modifier.fillMaxSize(),
        )

        TvSidebar(
            selected = "Home",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                navExpanded = false
                runCatching { contentFocusRequester.requestFocus() }.isSuccess
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }

    actionEntry?.let { entry ->
        TvPosterActionDialog(
            media = entry.media,
            libraryStore = runtime.libraryStore,
            continueEntry = (entry as? TvHomeEntry.Resume)?.playback,
            onOpenDetails = { onOpenMedia(entry.media) },
            onChanged = { retainedState.libraryRevision += 1 },
            onDismiss = { actionEntry = null },
        )
    }
}
