package com.vueo.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.shared.core.home.HomeRecommendationPolicy
import com.vueo.shared.core.home.HomeRecommendationSections
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.ContinueWatchingMetadataRefresh
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvTitleArtwork
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvPosterActionDialog
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvHomeRetainedState internal constructor(runtime: TvRuntime) {
    private val startupContinueWatching = runtime.libraryStore.homeStartupContinueWatching()

    var catalogRows by mutableStateOf(runtime.cachedHomeRows())
        internal set
    var loading by mutableStateOf(catalogRows.isEmpty())
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var libraryRevision by mutableIntStateOf(0)
        internal set

    internal var loadedRefreshToken = Int.MIN_VALUE
    internal var libraryHydrationRefreshToken = Int.MIN_VALUE
    internal var libraryHydrationRevision = Int.MIN_VALUE
    internal var libraryHydrationProfileId: String? = null
    internal var continueWatching by mutableStateOf(startupContinueWatching)
    internal var watchHistory by mutableStateOf<List<LibraryPlaybackEntry>>(emptyList())
    internal var homeRecommendations by mutableStateOf(HomeRecommendationSections())
    internal var presentationCatalogRows: List<CatalogRow>? = null
    internal var presentationRows by mutableStateOf(
        buildTvHomeRows(
            catalogRows = catalogRows,
            continueWatching = startupContinueWatching,
        )
    )
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
    active: Boolean = true,
    onNavigate: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val catalogRows = retainedState.catalogRows
    val loading = retainedState.presentationRows.isEmpty() && retainedState.error == null &&
        (retainedState.loading || retainedState.presentationCatalogRows !== catalogRows)
    val error = retainedState.error
    var actionEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var retryAttempt by remember(runtime) { mutableIntStateOf(0) }
    var handledRetryAttempt by remember(runtime) { mutableIntStateOf(0) }
    // Every fresh Home composition starts on row 1 / card 1. A retained
    // Details -> Home reveal restores the exact row/card that opened Details
    // instead of incrementing this reset token.
    var homeFocusResetToken by remember { mutableIntStateOf(1) }
    val libraryRevision = retainedState.libraryRevision

    DisposableEffect(Unit) {
        PerformanceDiagnostics.captureRuntimeEvent("HOME_COMPOSE_ENTER")
        onDispose {
            PerformanceDiagnostics.captureRuntimeEvent("HOME_COMPOSE_DISPOSE")
        }
    }

    LaunchedEffect(active) {
        PerformanceDiagnostics.captureRuntimeEvent("HOME_ACTIVE active=$active")
        if (!active) actionEntry = null
    }

    LaunchedEffect(runtime, refreshToken, retryAttempt) {
        val requestedRetryAttempt = retryAttempt
        val explicitRetry = requestedRetryAttempt > handledRetryAttempt
        // Startup restores disk cache after this retained state is created.
        // Publish it before awaiting any fresh catalog/provider result.
        if (retainedState.catalogRows.isEmpty()) {
            val restored = runtime.cachedHomeRows()
            if (restored.isNotEmpty()) retainedState.catalogRows = restored
        }
        if (explicitRetry || retainedState.loadedRefreshToken != refreshToken || runtime.needsHomeRefresh()) {
            retainedState.loading = retainedState.catalogRows.isEmpty()
            retainedState.error = null

            runCatching {
                coroutineScope {
                    val updates = Channel<List<CatalogRow>>(Channel.CONFLATED)
                    val publisher = launch {
                        for (partialRows in updates) {
                            if (partialRows.isNotEmpty()) {
                                retainedState.catalogRows = partialRows
                                retainedState.loading = false
                            }
                        }
                    }
                    try {
                        withContext(Dispatchers.Default) {
                            runtime.homeRows(
                                forceRefresh = explicitRetry,
                                onPartial = { updates.trySend(it) },
                            )
                        }
                    } finally {
                        updates.close()
                        publisher.join()
                    }
                }
            }
                .onSuccess { rows ->
                    if (rows.isNotEmpty()) retainedState.catalogRows = rows
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    retainedState.error = failure.message?.takeIf(String::isNotBlank)
                        ?: "Unable to load Home. Check your connection and try again."
                }

            retainedState.loading = false
            retainedState.loadedRefreshToken = refreshToken
            handledRetryAttempt = requestedRetryAttempt
        }
    }

    val activeProfileId = runtime.profileStore.activeProfileId()

    // Continue Watching is already present in retainedState from the tiny persisted
    // startup cache, so cached catalogs + CW can participate in the same first Home
    // presentation. Reconcile against full history in the background without gating
    // first paint or catalog streaming.
    LaunchedEffect(runtime, activeProfileId, refreshToken, libraryRevision) {
        val hydrationIsCurrent =
            retainedState.libraryHydrationProfileId == activeProfileId &&
                retainedState.libraryHydrationRefreshToken == refreshToken &&
                retainedState.libraryHydrationRevision == libraryRevision
        if (hydrationIsCurrent) return@LaunchedEffect

        if (retainedState.libraryHydrationProfileId != activeProfileId) {
            retainedState.continueWatching = withContext(Dispatchers.IO) {
                runtime.libraryStore.homeStartupContinueWatching()
            }
            retainedState.watchHistory = emptyList()
            retainedState.homeRecommendations = HomeRecommendationSections()
            retainedState.libraryHydrationProfileId = activeProfileId
        }

        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_STARTUP_PUBLISHED count=${retainedState.continueWatching.size}"
        )
        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_FULL_BEGIN revision=$libraryRevision refresh=$refreshToken"
        )
        val librarySnapshot = withContext(Dispatchers.IO) {
            runtime.libraryStore.homeSnapshot()
        }
        retainedState.continueWatching = librarySnapshot.continueWatching
        retainedState.watchHistory = librarySnapshot.history
        retainedState.libraryHydrationRefreshToken = refreshToken
        retainedState.libraryHydrationRevision = libraryRevision
        retainedState.libraryHydrationProfileId = activeProfileId
        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_FULL_PUBLISHED count=${librarySnapshot.continueWatching.size} " +
                "history=${librarySnapshot.history.size}"
        )
    }

    val continueWatching = retainedState.continueWatching
    val homeRecommendations = retainedState.homeRecommendations

    // Presentation is now a cheap pure build. Catalog partials may still arrive
    // rapidly, but they no longer restart disk/library hydration.
    LaunchedEffect(catalogRows, continueWatching, homeRecommendations) {
        retainedState.presentationRows = withContext(Dispatchers.Default) {
            buildTvHomeRows(
                catalogRows = catalogRows,
                continueWatching = continueWatching,
                homeRecommendations = homeRecommendations,
            )
        }
        retainedState.presentationCatalogRows = catalogRows
    }

    // Personalized rows depend on full history, so keep them behind the exact
    // library snapshot. They can be recomputed/cancelled as catalog partials
    // stream without holding back Continue Watching.
    LaunchedEffect(
        runtime,
        activeProfileId,
        refreshToken,
        libraryRevision,
        catalogRows,
        retainedState.libraryHydrationRefreshToken,
        retainedState.libraryHydrationRevision,
    ) {
        val libraryIsCurrent =
            retainedState.libraryHydrationProfileId == activeProfileId &&
                retainedState.libraryHydrationRefreshToken == refreshToken &&
                retainedState.libraryHydrationRevision == libraryRevision
        if (!libraryIsCurrent) return@LaunchedEffect

        val history = retainedState.watchHistory
        val rebuilt = withContext(Dispatchers.Default) {
            val personalizedHomeEnabled =
                runtime.dnaPreferences.shouldPersonalizeRecommendations(activeProfileId)
            HomeRecommendationPolicy.build(
                catalogRows = catalogRows,
                watchHistory = history,
                dnaEngine = runtime.dnaEngine,
                personalizationEnabled = personalizedHomeEnabled,
                limit = 12,
            )
        }
        retainedState.homeRecommendations = rebuilt
    }

    val rows = retainedState.presentationRows
    val visibleContinueWatchingCount =
        rows.firstOrNull { it.key == "continue-watching" }?.entries?.size ?: 0

    LaunchedEffect(visibleContinueWatchingCount) {
        if (visibleContinueWatchingCount > 0) {
            withFrameNanos { }
            PerformanceDiagnostics.captureRuntimeEvent(
                "HOME_CW_FIRST_FRAME count=$visibleContinueWatchingCount"
            )
        }
    }

    // Continue-Watching metadata repair is best-effort. Let Home paint and give
    // the first frame/hero work a short head start before starting extra network
    // and profile writes.
    LaunchedEffect(
        runtime,
        refreshToken,
        runtime.isHomeCatalogRuntimeReady(),
        rows.isNotEmpty(),
    ) {
        if (!runtime.isHomeCatalogRuntimeReady() || rows.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        delay(700L)
        if (ContinueWatchingMetadataRefresh.refresh(runtime.libraryStore, runtime.engine::loadMeta)) {
            retainedState.libraryRevision += 1
        }
    }

    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty()) {
            withFrameNanos { }
            runtime.markHomePresented()
        }
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

    BackHandler(enabled = active) {
        if (navExpanded) onBack() else focusSidebar()
    }

    var previousActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        val returningFromCoveredRoute = active && !previousActive
        previousActive = active
        if (returningFromCoveredRoute) {
            // The retained Home tree still owns its LazyColumn/LazyRow focus
            // restorers. Re-enter that focus boundary rather than resetting the
            // memory to row 1 / card 1. Retry briefly in case Details detaches
            // one frame before the parent's focus nodes are eligible again.
            var restored = false
            for (attempt in 0 until 3) {
                withFrameNanos { }
                restored = runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                if (restored) break
            }
            PerformanceDiagnostics.captureRuntimeEvent(
                "HOME_FOCUS_READY restoreLast=true restored=$restored",
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvHomePresentation(
            rows = rows,
            artworkApiKey = runtime.pluginStore.tmdbApiKey(),
            loading = loading,
            error = error,
            onRetry = {
                retainedState.error = null
                retainedState.loading = retainedState.catalogRows.isEmpty()
                retryAttempt += 1
            },
            navigationVisible = navExpanded,
            contentFocusRequester = contentFocusRequester,
            focusResetToken = homeFocusResetToken,
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


private fun buildTvHomeRows(
    catalogRows: List<CatalogRow>,
    continueWatching: List<LibraryPlaybackEntry>,
    homeRecommendations: HomeRecommendationSections = HomeRecommendationSections(),
): List<TvHomeRow> =
    buildList {
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
