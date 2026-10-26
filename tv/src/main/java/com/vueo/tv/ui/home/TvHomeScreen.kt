package com.vueo.tv.home

import com.vueo.shared.core.storage.RecommendationEngine
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics.Tab
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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

    // Lives above the route composition: Sources may dispose the Home UI.
    internal var routeFocusAnchor by mutableStateOf<HomeRouteFocusAnchor?>(null)

    internal var loadedRefreshToken = Int.MIN_VALUE
    internal var libraryHydrationRefreshToken = Int.MIN_VALUE
    internal var libraryHydrationRevision = Int.MIN_VALUE
    internal var libraryHydrationProfileId: String? = null
    internal var continueWatching by mutableStateOf(startupContinueWatching)
    internal var watchHistory by mutableStateOf<List<LibraryPlaybackEntry>>(emptyList())
    internal var recommendationEngine by mutableStateOf<RecommendationEngine?>(null)
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
    onOpenCollection: (String, String) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val traktContext = LocalContext.current
    val trakt = remember { com.vueo.shared.core.trakt.TraktService.get(traktContext) }
    val traktProfile = runtime.profileStore.activeProfileId()
    val traktRevision by trakt.catalogRevision.collectAsState()
    val traktLibraryRevision by trakt.libraryRevision.collectAsState()
    var traktRows by remember(traktProfile) { mutableStateOf(trakt.cachedHomeRows(traktProfile)) }
    LaunchedEffect(traktProfile, active, refreshToken) {
        if (active) trakt.scheduleSync(traktProfile)
    }
    LaunchedEffect(traktProfile, active, refreshToken, traktRevision) {
        if (active) traktRows = trakt.homeRows(traktProfile)
    }
    LaunchedEffect(traktProfile, traktLibraryRevision) {
        if (traktLibraryRevision > 0L) retainedState.libraryRevision += 1
    }
    val collectionStore = remember(traktContext, traktProfile) {
        com.vueo.shared.core.collections.CollectionStore.get(traktContext, traktProfile)
    }
    val userCollections by collectionStore.collections.collectAsState()
    LaunchedEffect(collectionStore) {
        try { collectionStore.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Management screen surfaces file errors. */ }
    }
    val collectionRows = remember(userCollections) {
        userCollections.filter { it.enabled && it.folders.isNotEmpty() }.map { collection ->
            TvHomeRow("collection:${collection.id}", collection.title, TvHomeRowKind.COLLECTIONS,
                collection.folders.map { TvHomeEntry.Folder(collection.id, it) })
        }
    }
    val catalogRows = retainedState.catalogRows
    val loading = retainedState.presentationRows.isEmpty() && traktRows.isEmpty() && retainedState.error == null &&
        (retainedState.loading || retainedState.presentationCatalogRows !== catalogRows)
    val error = retainedState.error.takeIf { traktRows.isEmpty() }
    var returnSnapshotReady by remember { mutableStateOf(retainedState.routeFocusAnchor == null) }
    var actionEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var retryAttempt by remember(runtime) { mutableIntStateOf(0) }
    var handledRetryAttempt by remember(runtime) { mutableIntStateOf(0) }
    // Initial Home focus is applied once per composition. TvModernHomeRows
    // starts at row 1 only if there is no saved session focus; on a rebuild it
    // restores the saved row/card instead. Retained Home uses its focusRestorer.
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
        retainedState.catalogRows = runtime.visibleHomeRows(
            retainedState.catalogRows.ifEmpty { runtime.cachedHomeRows() },
        )
        if (explicitRetry || retainedState.loadedRefreshToken != refreshToken || runtime.needsHomeRefresh()) {
            retainedState.loading = retainedState.catalogRows.isEmpty()
            retainedState.error = null

            runCatching {
                coroutineScope {
                    val updates = Channel<List<CatalogRow>>(Channel.CONFLATED)
                    val publisher = launch {
                        for (partialRows in updates) {
                            retainedState.catalogRows = runtime.visibleHomeRows(partialRows)
                            retainedState.loading = false
                        }
                    }
                    try {
                        PerformanceDiagnostics.measuredContext(Dispatchers.Default, "tv_home_load", Tab.HOME) {
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
                    retainedState.catalogRows = runtime.visibleHomeRows(rows)
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
    LaunchedEffect(runtime, activeProfileId, refreshToken, libraryRevision, active, actionEntry, retainedState.routeFocusAnchor) {
        if (!active || actionEntry != null) return@LaunchedEffect
        val hydrationIsCurrent =
            retainedState.libraryHydrationProfileId == activeProfileId &&
                retainedState.libraryHydrationRefreshToken == refreshToken &&
                retainedState.libraryHydrationRevision == libraryRevision
        if (hydrationIsCurrent && returnSnapshotReady) return@LaunchedEffect

        if (retainedState.libraryHydrationProfileId != activeProfileId) {
            retainedState.continueWatching = PerformanceDiagnostics.measuredContext(Dispatchers.IO, "tv_home_hydrate_2", Tab.HOME) {
                runtime.libraryStore.homeStartupContinueWatching()
            }
            retainedState.watchHistory = emptyList()
            com.vueo.tv.channels.TvHomeChannels.updateForYou(traktContext.applicationContext, activeProfileId, emptyList())
            retainedState.homeRecommendations = HomeRecommendationSections()
            retainedState.libraryHydrationProfileId = activeProfileId
        }

        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_STARTUP_PUBLISHED count=${retainedState.continueWatching.size}"
        )
        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_FULL_BEGIN revision=$libraryRevision refresh=$refreshToken"
        )
        val librarySnapshot = PerformanceDiagnostics.measuredContext(Dispatchers.IO, "tv_home_hydrate_3", Tab.HOME) {
            runtime.libraryStore.homeSnapshot()
        }
        retainedState.continueWatching = librarySnapshot.continueWatching
        retainedState.watchHistory = librarySnapshot.history
        retainedState.libraryHydrationRefreshToken = refreshToken
        retainedState.libraryHydrationRevision = libraryRevision
        retainedState.libraryHydrationProfileId = activeProfileId
        returnSnapshotReady = true
        PerformanceDiagnostics.captureRuntimeEvent(
            "HOME_CW_FULL_PUBLISHED count=${librarySnapshot.continueWatching.size} " +
                "history=${librarySnapshot.history.size}"
        )
    }

    val continueWatching = retainedState.continueWatching
    val currentRecommendationEngine = runtime.settingsStore.recommendationEngine()
    val currentTraktClientId = runtime.settingsStore.traktClientId()
    val recommendationEngineIsCurrent = retainedState.recommendationEngine == currentRecommendationEngine
    val forYouEnabled = runtime.dnaPreferences.shouldShowForYou(activeProfileId)
    val becauseEnabled = runtime.dnaPreferences.shouldShowBecauseYouWatched(activeProfileId)
    val homeRecommendations = (if (recommendationEngineIsCurrent) retainedState.homeRecommendations
        else HomeRecommendationSections()).let { sections -> sections.copy(
            forYou = if (forYouEnabled) sections.forYou else emptyList(),
            becauseYouWatchedSeed = if (becauseEnabled) sections.becauseYouWatchedSeed else null,
            becauseYouWatched = if (becauseEnabled) sections.becauseYouWatched else emptyList()) }

    // Presentation is now a cheap pure build. Catalog partials may still arrive
    // rapidly, but they no longer restart disk/library hydration.
    LaunchedEffect(catalogRows, continueWatching, homeRecommendations) {
        retainedState.presentationRows = PerformanceDiagnostics.measuredContext(Dispatchers.Default, "tv_home_presentation_4", Tab.HOME) {
            buildTvHomeRows(
                catalogRows = catalogRows,
                continueWatching = continueWatching,
                homeRecommendations = homeRecommendations,
            )
        }
        retainedState.presentationCatalogRows = catalogRows
    }

    // Observe the app-owned feed after library hydration. Catalog updates only rebuild presentation.
    val currentTmdbKey = runtime.pluginStore.tmdbApiKey()
    val currentTmdbRecommendations = runtime.settingsStore.tmdbRecommendationsEnabled()
    val currentTmdbSimilar = runtime.settingsStore.tmdbSimilarTitlesEnabled()
    val personalized = runtime.dnaPreferences.shouldPersonalizeRecommendations(activeProfileId)
    val ratingRevision by com.vueo.shared.core.storage.RatingChanges.revision.collectAsState()
    LaunchedEffect(ratingRevision, active, runtime, activeProfileId, refreshToken, libraryRevision,
        retainedState.libraryHydrationRefreshToken, retainedState.libraryHydrationRevision,
        currentRecommendationEngine, currentTraktClientId, currentTmdbKey,
        currentTmdbRecommendations, currentTmdbSimilar, personalized, forYouEnabled, becauseEnabled) {
        val libraryIsCurrent = retainedState.libraryHydrationProfileId == activeProfileId &&
            retainedState.libraryHydrationRefreshToken == refreshToken &&
            retainedState.libraryHydrationRevision == libraryRevision
        if (!active || !libraryIsCurrent) return@LaunchedEffect
        if (!personalized) {
            com.vueo.shared.core.home.HomeRecommendationFeeds.cancelProfile(activeProfileId)
            retainedState.homeRecommendations = HomeRecommendationSections()
            retainedState.recommendationEngine = currentRecommendationEngine
            return@LaunchedEffect
        }
        val favorites = runtime.readDetailLibrary { runtime.libraryStore.watchlist() }
        val request = com.vueo.shared.core.home.HomeRecommendationFeeds.Request(activeProfileId,
            retainedState.watchHistory, favorites, currentRecommendationEngine,
            currentTmdbKey, currentTraktClientId, currentTmdbRecommendations, currentTmdbSimilar, forYouEnabled, becauseEnabled)
        val feed = com.vueo.shared.core.home.HomeRecommendationFeeds.observe(traktContext, request)
        retainedState.recommendationEngine = currentRecommendationEngine
        feed.collect { sections ->
            retainedState.homeRecommendations = sections
            com.vueo.tv.channels.TvHomeChannels.updateForYou(traktContext.applicationContext, activeProfileId, sections.forYou)
        }
    }

    val baseRows = if (recommendationEngineIsCurrent) retainedState.presentationRows
        else retainedState.presentationRows.filterNot { it.key == "because-you-watched" || it.key == "for-you" }
    val kidsLimit = runtime.profileStore.kidsAgeLimit()
    val rows = remember(baseRows, traktRows, kidsLimit, collectionRows) {
        val combined = baseRows + buildTvHomeRows(traktRows, emptyList()) + collectionRows
        if (kidsLimit == null) combined else combined.mapNotNull { row ->
            if (row.kind == TvHomeRowKind.COLLECTIONS) row else row.copy(entries = row.entries.filter {
                com.vueo.shared.core.profile.KidsContentPolicy.allows(it.media.certification, kidsLimit)
            }).takeIf { it.entries.isNotEmpty() }
        }
    }
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
    // Opening a hidden navigation panel is asynchronous. Keep the current
    // poster focusable until a real navigation item has accepted focus;
    // revoking it earlier makes Compose relocate focus inside the LazyRow.
    var navigationOwnsFocus by remember { mutableStateOf(false) }
    var contentReturnToken by remember { mutableIntStateOf(0) }
    val routeReturnPending = retainedState.routeFocusAnchor != null

    fun focusSidebar() {
        if (!navExpanded) navigationOwnsFocus = false
        navExpanded = true
        // TvSidebar requests focus only after the expanding panel has composed.
        // An eager request here can target a hidden/unfocusable Topbar/rail.
    }

    BackHandler(enabled = active) {
        if (navExpanded) onBack() else focusSidebar()
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
            active = active,
            routeReturnPending = routeReturnPending,
            routeFocusAnchor = retainedState.routeFocusAnchor?.copy(
                restoreReady = returnSnapshotReady && actionEntry == null,
            ),
            onCaptureRouteFocusAnchor = {
                returnSnapshotReady = false
                retainedState.routeFocusAnchor = it
            },
            onRouteFocusRestored = { retainedState.routeFocusAnchor = null },
            navigationVisible = navExpanded,
            navigationOwnsFocus = navigationOwnsFocus,
            contentFocusRequester = contentFocusRequester,
            focusResetToken = homeFocusResetToken,
            contentReturnToken = contentReturnToken,
            onContentFocused = {
                navigationOwnsFocus = false
                navExpanded = false
            },
            onOpenNavigation = ::focusSidebar,
            // Rows captures the retained anchor before this changes routes.
            onOpen = { entry -> entry.open(onOpenMedia, onResume, onOpenCollection) },
            onLongClick = { entry -> if (entry !is TvHomeEntry.Folder) actionEntry = entry },
            modifier = Modifier.fillMaxSize(),
        )

        TvSidebar(
            selected = "Home",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {
                if (active && !routeReturnPending && actionEntry == null) {
                    navigationOwnsFocus = true
                    navExpanded = true
                }
            },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                if (rows.isNotEmpty()) {
                    // Keep menu focus until the saved row/card is placed and receives focus.
                    contentReturnToken += 1
                    true
                } else {
                    navigationOwnsFocus = false
                    navExpanded = false
                    runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                }
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
            onChanged = {
                returnSnapshotReady = false
                retainedState.libraryRevision += 1
            },
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
                            key = "continue:${playback.media.type}:${playback.media.id}",
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
        if (becauseSeed != null && homeRecommendations.becauseYouWatched.isNotEmpty()) {
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
                        title = when (row.id.split(':', limit = 3).getOrNull(1)?.lowercase(java.util.Locale.ROOT)) {
                            "movie" -> "${row.title} - Movie"
                            "series", "tv" -> "${row.title} - Series"
                            else -> row.title
                        },
                        providerName = row.providerName.takeIf { it.isNotBlank() },
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
