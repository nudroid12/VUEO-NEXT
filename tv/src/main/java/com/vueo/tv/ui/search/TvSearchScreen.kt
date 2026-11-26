package com.vueo.tv.search

import androidx.compose.foundation.lazy.rememberLazyListState



import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.window.Dialog

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import com.vueo.shared.core.storage.RecentSearchStore

import com.vueo.shared.core.storage.RecentSearchPolicy

import com.vueo.shared.core.storage.RecentSearchMode

import com.vueo.shared.core.storage.RecentSearch
import com.vueo.tv.ui.TvPosterGridReveal
import com.vueo.tv.ui.rememberTvOkToEdit

import com.vueo.shared.core.search.SearchResultOrderPolicy
import com.vueo.shared.core.search.SearchMediaFilter
import com.vueo.shared.core.search.SearchOrchestrator
import com.vueo.shared.core.search.SearchPolicy
import com.vueo.shared.core.search.DiscoverCatalogPolicy
import com.vueo.shared.core.search.DiscoverSortMode

import com.vueo.tv.ui.motion.*
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.core.browseAnimeProgressive
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPosterActionDialog
import com.vueo.tv.ui.tvPosterActivation
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.tvSidebarIsPillMode
import com.vueo.tv.ui.tvSidebarIsHiddenClassic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val TV_SEARCH_POSTER_COLUMNS = 7
private val SEARCH_FOCUS_PAINT_INSET = 8.dp

internal enum class TvSearchTypeFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    ANIME("Anime"),
}

internal enum class TvSearchSortMode(val label: String) {
    POPULAR("Popular"),
    TRENDING("Trending"),
    NEWEST("Newest"),
}

internal enum class TvSearchMode(val label: String) {
    TITLE("Title"),
    ACTOR("Actor"),
}

/**
 * Search state is owned by VueoTvApp so returning from Detail restores the
 * Mobile-style search surface instead of starting a fresh session.
 */
internal data class TvSearchPreparedInput(
    val rows: List<CatalogRow>,
    val animeItems: List<MediaItem>,
    val results: List<MediaItem>,
    val query: String,
    val type: TvSearchTypeFilter,
    val genre: String?,
    val sort: TvSearchSortMode,
    val mode: TvSearchMode,
    val contentVersion: Int,
)

internal data class TvSearchPreparedResult(
    val items: List<MediaItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val keys: List<String> = emptyList(),
    val sourceEmpty: Boolean = true,
)

private fun prepareSearch(input: TvSearchPreparedInput): TvSearchPreparedResult {
    val animeKeys = if (input.type == TvSearchTypeFilter.ALL) emptySet() else input.rows.asSequence()
        .filter { row -> listOf(row.id, row.title, row.providerName).any { it.contains("anime", ignoreCase = true) } }
        .flatMap { it.items.asSequence() }.map(::mediaKey).toSet() + input.animeItems.map(::mediaKey)
    val searching = input.query.isNotBlank()
    val source = if (searching) input.results else {
        val base = DiscoverCatalogPolicy.baseItems(input.rows, input.sort.toDiscoverSortMode())
        if (input.type == TvSearchTypeFilter.ANIME) (input.animeItems + base).distinctBy(::mediaKey) else base
    }
    val typed = source.filter {
        SearchResultOrderPolicy.matchesType(it, input.type.toSearchMediaFilter(), animeKeys)
    }
    val genres = typed.flatMap { it.genres }.map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("anime", ignoreCase = true) }
        .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val filtered = typed.filter { SearchResultOrderPolicy.matchesGenre(it, input.genre) }
    val items = when {
        searching && input.mode == TvSearchMode.ACTOR -> searchSortActorItems(filtered, input.sort)
        searching -> searchSortItems(filtered, input.sort, input.query)
        input.sort == TvSearchSortMode.NEWEST -> filtered.map { it to SearchPolicy.releaseYear(it) }
            .sortedByDescending { it.second }.map { it.first }
        else -> filtered
    }
    return TvSearchPreparedResult(items, genres, items.map(::mediaKey), source.isEmpty())
}

internal class TvSearchSession {
    // Bounded cache of filter views for the current dataset; shares MediaItem references.
    val sources = LinkedHashMap<Pair<TvSearchTypeFilter, TvSearchSortMode>, TvSearchPreparedResult>()
    var sourceDataset: TvSearchPreparedInput? = null
    var preparedInput by mutableStateOf<TvSearchPreparedInput?>(null)
    var preparedResult by mutableStateOf(TvSearchPreparedResult())
    var query by mutableStateOf("")
    var typeFilter by mutableStateOf(TvSearchTypeFilter.ALL)
    var sortMode by mutableStateOf(TvSearchSortMode.POPULAR)
    var genre by mutableStateOf<String?>(null)
    var mode by mutableStateOf(TvSearchMode.TITLE)

    var searchResults by mutableStateOf<List<MediaItem>>(emptyList())
    var discoverRows by mutableStateOf<List<CatalogRow>>(emptyList())
    var animeBrowseItems by mutableStateOf<List<MediaItem>>(emptyList())
    var animeBrowseContentVersion by mutableStateOf<Int?>(null)
    var actorSourceAvailable by mutableStateOf(true)
    var completedSearchKey by mutableStateOf<String?>(null)
    var discoverContentVersion by mutableStateOf<Int?>(null)

    var focusedMediaKey by mutableStateOf<String?>(null)
    var restoreResultsFocus by mutableStateOf(false)
    var firstVisibleItemIndex by mutableStateOf(0)
    var firstVisibleItemScrollOffset by mutableStateOf(0)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSearchScreen(
    runtime: TvRuntime,
    contentVersion: Int,
    session: TvSearchSession,
    active: Boolean = true,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(session.discoverRows.isEmpty()) }
    var animeDiscovering by remember { mutableStateOf(false) }
    var requestId by remember { mutableStateOf(0L) }
    var navExpanded by remember { mutableStateOf(false) }
    var initialContentFocusHandled by remember { mutableStateOf(false) }
    var lastContentTarget by remember { mutableStateOf("field") }
    var choiceDialog by remember { mutableStateOf<SearchChoice?>(null) }
    var actionMedia by remember { mutableStateOf<MediaItem?>(null) }
    var dialogReturnFocus by remember { mutableStateOf<(() -> Unit)?>(null) }

    val recentProfile = runtime.profileStore.activeProfileId()
    val recentContext = LocalContext.current
    val recentStore = remember(recentProfile, recentContext) { RecentSearchStore(recentContext, recentProfile) }
    var recentSearches by remember(recentStore) { mutableStateOf(recentStore.entries()) }
    var pendingRecent by remember(recentStore) { mutableStateOf<RecentSearch?>(null) }
    var recentExpanded by remember(recentStore) { mutableStateOf(false) }
    val recentFirstRequester = remember { FocusRequester() }
    fun queueRecent(query: String, mode: TvSearchMode) {
        pendingRecent = RecentSearchPolicy.entry(query, RecentSearchMode.valueOf(mode.name))
    }
    fun savePendingRecent() {
        val entry = pendingRecent ?: return
        if (runtime.profileStore.activeProfileId() != recentProfile || !active ||
            RecentSearchPolicy.entry(session.query, RecentSearchMode.valueOf(session.mode.name))?.key != entry.key) return
        recentSearches = recentStore.record(entry)
        pendingRecent = null
    }
    val fieldRequester = remember { FocusRequester() }
    val allYearsRequester = remember { FocusRequester() }
    val modeRequester = remember { FocusRequester() }
    val typeRequester = remember { FocusRequester() }
    val sortRequester = remember { FocusRequester() }
    val genreRequester = remember { FocusRequester() }
    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active, navExpanded) {
        if (!active || navExpanded || initialContentFocusHandled) return@LaunchedEffect
        delay(120)
        if (!session.restoreResultsFocus) {
            initialContentFocusHandled = runCatching { fieldRequester.requestFocus() }.getOrDefault(false)
        }
    }

    LaunchedEffect(contentVersion) {
        val versionChanged = session.discoverContentVersion != contentVersion
        val cached = withContext(Dispatchers.Default) { runtime.cachedHomeRows() }
        if (cached.isNotEmpty()) {
            session.discoverRows = cached
            session.discoverContentVersion = contentVersion
            discovering = false
            if (!runtime.needsHomeRefresh()) return@LaunchedEffect
        }
        if (!versionChanged && session.discoverRows.isNotEmpty() && !runtime.needsHomeRefresh()) {
            discovering = false
            return@LaunchedEffect
        }
        if (versionChanged && cached.isEmpty()) session.discoverRows = emptyList()
        discovering = session.discoverRows.isEmpty()
        val rows = try {
            runtime.homeRows(forceRefresh = false, onPartial = { partial ->
                if (partial.isNotEmpty()) {
                    session.discoverRows = partial
                    discovering = false
                }
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runtime.cachedHomeRows()
        }
        if (rows.isNotEmpty()) session.discoverRows = rows
        session.discoverContentVersion = contentVersion
        discovering = false
    }

    LaunchedEffect(session.query, contentVersion, session.mode) {
        val normalized = session.query.trim()
        requestId += 1L
        val thisRequest = requestId
        val requestedMode = session.mode
        val searchKey = "$contentVersion|${requestedMode.name}|$normalized"

        if (session.completedSearchKey == searchKey) {
            searching = false
            return@LaunchedEffect
        }

        if (normalized.length < 2) {
            searching = false
            session.actorSourceAvailable = true
            session.searchResults = emptyList()
            session.completedSearchKey = searchKey
            return@LaunchedEffect
        }

        searching = true
        delay(250)
        if (
            thisRequest != requestId ||
            session.query.trim() != normalized ||
            session.mode != requestedMode
        ) return@LaunchedEffect

        if (requestedMode == TvSearchMode.TITLE) {
            session.actorSourceAvailable = true
            val local = runtime.engine.filterVisible(SearchOrchestrator.localTitleResults(normalized))
            if (local.isNotEmpty() || session.searchResults.isEmpty()) {
                session.searchResults = local
            }

            val remote = SearchOrchestrator.remoteTitleResults(
                engine = runtime.engine,
                query = normalized,
                localResults = local,
                onPartial = { partial ->
                    if (thisRequest == requestId && session.query.trim() == normalized && session.mode == requestedMode) {
                        session.searchResults = partial
                    }
                },
            )

            if (thisRequest == requestId && session.query.trim() == normalized && session.mode == requestedMode) {
                session.searchResults = remote
                session.completedSearchKey = searchKey
                searching = false
            }
            return@LaunchedEffect
        }

        val tmdbApiKey = runtime.pluginStore.tmdbApiKey()
        val actorAvailability = SearchOrchestrator.actorAvailability(
            engine = runtime.engine,
            tmdbApiKey = tmdbApiKey,
        )
        session.actorSourceAvailable = actorAvailability.available
        session.searchResults = emptyList()

        if (!session.actorSourceAvailable) {
            searching = false
            return@LaunchedEffect
        }

        val actorResults = SearchOrchestrator.actorResults(
            engine = runtime.engine,
            query = normalized,
            tmdbApiKey = tmdbApiKey,
            onPartial = { partial ->
                if (thisRequest == requestId && session.query.trim() == normalized && session.mode == requestedMode) {
                    session.searchResults = partial
                }
            },
        )

        if (thisRequest == requestId && session.query.trim() == normalized && session.mode == requestedMode) {
            session.searchResults = actorResults
            session.completedSearchKey = searchKey
            searching = false
        }
    }

    LaunchedEffect(session.typeFilter, contentVersion) {
        if (session.typeFilter != TvSearchTypeFilter.ANIME) {
            animeDiscovering = false
            return@LaunchedEffect
        }
        if (
            session.animeBrowseContentVersion == contentVersion
        ) {
            animeDiscovering = false
            return@LaunchedEffect
        }

        if (session.animeBrowseContentVersion != contentVersion) session.animeBrowseItems = emptyList()
        animeDiscovering = true
        try {
            val items = runtime.browseAnimeProgressive { partial ->
                session.animeBrowseItems = partial
            }
            session.animeBrowseItems = items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retain successful partial catalogs if another request fails.
        }
        session.animeBrowseContentVersion = contentVersion
        animeDiscovering = false
    }

    val normalizedQuery = session.query.trim()
    val titleSearch = SearchPolicy.parseTitleSearch(normalizedQuery)
        .takeIf { session.mode == TvSearchMode.TITLE && it.year != null }
    val searchingMode = normalizedQuery.isNotBlank()

    val matchingRecents = remember(recentSearches, normalizedQuery, session.mode) {
        recentSearches.filter { entry ->
            entry.mode.name == session.mode.name && entry.query.startsWith(normalizedQuery, ignoreCase = true)
        }
    }
    val showSearchPanel = active && recentExpanded && choiceDialog == null && actionMedia == null &&
        matchingRecents.isNotEmpty()


    val prepareInput = TvSearchPreparedInput(
        session.discoverRows, session.animeBrowseItems, session.searchResults,
        normalizedQuery, session.typeFilter, session.genre, session.sortMode, session.mode, contentVersion,
    )
    // Keep the current grid visible while another catalog batch is prepared.
    // A query/filter/version change still clears results from the previous view.
    val sameView = session.preparedInput?.copy(
        rows = prepareInput.rows,
        animeItems = prepareInput.animeItems,
        results = prepareInput.results,
    ) == prepareInput
    val prepared = if (sameView) session.preparedResult else TvSearchPreparedResult()
    val preparing = session.preparedInput != prepareInput
    val availableGenres = prepared.genres
    val filteredItems = runtime.engine.filterVisible(prepared.items)
    val contentDiscovering = discovering || animeDiscovering || preparing

    LaunchedEffect(active, prepareInput) {
        if (!active || session.preparedInput == prepareInput) return@LaunchedEffect
        val started = System.nanoTime()
        // Genre changes reuse the typed, ordered source and its genre list.
        val sourceInput = prepareInput.copy(genre = null)
        val dataset = sourceInput.copy(type = TvSearchTypeFilter.ALL, sort = TvSearchSortMode.POPULAR)
        if (session.sourceDataset != dataset) {
            session.sources.clear()
            session.sourceDataset = dataset
        }
        val sourceKey = sourceInput.type to sourceInput.sort
        val cachedSource = session.sources[sourceKey]
        val (source, result) = withContext(Dispatchers.Default) {
            val source = cachedSource ?: prepareSearch(sourceInput)
            val items = if (prepareInput.genre == null) source.items else source.items.filter {
                SearchResultOrderPolicy.matchesGenre(it, prepareInput.genre)
            }
            source to if (items === source.items) source else source.copy(
                items = items,
                keys = items.map(::mediaKey),
            )
        }
        // LaunchedEffect cancellation prevents obsolete filter/query work from publishing.
        session.sources.remove(sourceKey)
        session.sources[sourceKey] = source
        while (session.sources.size > 6) session.sources.remove(session.sources.keys.first())
        session.preparedInput = prepareInput
        session.preparedResult = result
        PerformanceDiagnostics.captureRuntimeEvent(
            "SEARCH_PREPARE_DONE items=${result.items.size} sourceReused=${cachedSource != null} elapsedMs=${(System.nanoTime() - started) / 1_000_000}",
        )
    }

    LaunchedEffect(preparing, availableGenres, session.genre) {
        if (!preparing && session.genre != null && availableGenres.none { it.equals(session.genre, ignoreCase = true) }) {
            session.genre = null
        }
    }

    LaunchedEffect(active) {
        if (active) {
            val started = System.nanoTime()
            androidx.compose.runtime.withFrameNanos { }
            PerformanceDiagnostics.captureRuntimeEvent(
                "SEARCH_FIRST_FRAME cached=${!preparing} elapsedMs=${(System.nanoTime() - started) / 1_000_000}",
            )
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = session.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = session.firstVisibleItemScrollOffset,
    )
    val resultKeys = filteredItems.map(::mediaKey)
    val resultRequesterCache = remember(resultKeys, session.mode) { mutableMapOf<String, FocusRequester>() }
    val resultFocusJob = remember { arrayOfNulls<Job>(1) }

    fun focusSidebar() {
        // Cancel any pending poster focus before handing focus to Topbar/Sidebar.
        resultFocusJob[0]?.cancel()
        initialContentFocusHandled = true
        navExpanded = true
        // TvSidebar waits for its expanded focus node; never target the hidden proxy.
    }

    fun resetGridForFilterChange() {
        resultFocusJob[0]?.cancel()
        session.focusedMediaKey = null
        session.restoreResultsFocus = false
        session.firstVisibleItemIndex = 0
        session.firstVisibleItemScrollOffset = 0
        scope.launch {
            try {
                gridState.scrollToItem(0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the current focus if the grid is being disposed.
            }
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current

    fun useRecent(entry: RecentSearch) {
        if (runtime.profileStore.activeProfileId() != recentProfile) return
        recentExpanded = false
        keyboard?.hide()
        pendingRecent = null
        recentSearches = recentStore.record(entry)
        session.mode = TvSearchMode.valueOf(entry.mode.name)
        session.query = entry.query
        session.searchResults = emptyList()
        session.completedSearchKey = null
        session.genre = null
        resetGridForFilterChange()
        lastContentTarget = "field"
        scope.launch {
            withFrameNanos { }
            if (active) runCatching { fieldRequester.requestFocus() }
        }
    }

    fun closeRecent() {
        recentExpanded = false
        scope.launch {
            withFrameNanos { }
            if (active) runCatching { fieldRequester.requestFocus() }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                session.firstVisibleItemIndex = index
                session.firstVisibleItemScrollOffset = offset
            }
    }

    LaunchedEffect(active, session.restoreResultsFocus, filteredItems, navExpanded) {
        if (!active || navExpanded || !session.restoreResultsFocus) return@LaunchedEffect
        val key = session.focusedMediaKey ?: return@LaunchedEffect
        val index = filteredItems.indexOfFirst { mediaKey(it) == key }
        if (index < 0) return@LaunchedEffect

        runCatching { gridState.scrollToItem(index) }
        delay(90)
        val restored = runCatching {
            resultRequesterCache.getOrPut(key) { FocusRequester() }.requestFocus()
        }.getOrDefault(false)
        if (restored) session.restoreResultsFocus = false
    }

    val showYearFallback = titleSearch != null && filteredItems.isEmpty() && !searching && !preparing

    fun focusFirstResult(): Boolean {
        val firstKey = resultKeys.firstOrNull()
        if (firstKey == null) {
            return showYearFallback && runCatching { allYearsRequester.requestFocus() }.getOrDefault(false)
        }
        resultFocusJob[0]?.cancel()
        resultFocusJob[0] = scope.launch {
            // Filter changes rebuild the result FocusRequesters. The old code
            // consumed Down even when the new first poster was not composed yet,
            // leaving focus apparently stuck on the filter row. Ensure item 0 is
            // placed first, then retry the exact poster for a few frames.
            runCatching { gridState.scrollToItem(0) }
            repeat(4) {
                withFrameNanos { }
                val focused = resultRequesterCache[firstKey]?.let { requester ->
                    runCatching { requester.requestFocus() }.getOrDefault(false)
                } == true
                if (focused) return@launch
            }
        }
        return true
    }

    fun dismissChoiceDialog() {
        val restoreFocus = dialogReturnFocus
        choiceDialog = null
        dialogReturnFocus = null
        scope.launch {
            delay(40)
            if (active && !navExpanded) restoreFocus?.invoke()
        }
    }

    BackHandler(enabled = active && choiceDialog != null && actionMedia == null) { dismissChoiceDialog() }
    BackHandler(enabled = active && choiceDialog == null && actionMedia == null) {
        PerformanceDiagnostics.captureRuntimeEvent(
            "TV_TAB_BACK tab=Search action=${if (navExpanded) "home" else "open_navigation"}"
        )
        if (navExpanded) onBack() else focusSidebar()
    }

    BackHandler(enabled = showSearchPanel, onBack = ::closeRecent)

    val contentStartPadding = tvSidebarContentStartPadding(96.dp)
    val floatingPillMode = tvSidebarIsPillMode()
    val fullWidthContent = floatingPillMode || tvSidebarIsHiddenClassic()
    val searchColumns = TV_SEARCH_POSTER_COLUMNS
    val gridEndPadding = if (fullWidthContent) 28.dp else 52.dp

    val density = LocalDensity.current
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    var inputBounds by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier.fillMaxSize().background(TvDesign.Black)
            .onGloballyPositioned { pageOrigin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 46.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = contentStartPadding, end = 52.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    TvMobileSearchField(
                        value = session.query,
                        mode = session.mode,
                        requester = fieldRequester,
                        onOpenRecent = { recentExpanded = true },
                        onSubmit = {
                            savePendingRecent()
                            recentExpanded = false
                            keyboard?.hide()
                        },
                        modeRequester = modeRequester,
                        onFocused = {
                            if (!navExpanded) lastContentTarget = "field"
                        },
                        onModeFocused = {
                            if (!navExpanded) lastContentTarget = "mode"
                        },
                        onModeChange = { next ->
                            if (next != session.mode) {
                                session.mode = next
                                queueRecent(session.query, next)
                                session.searchResults = emptyList()
                                session.completedSearchKey = null
                                session.genre = null
                                resetGridForFilterChange()
                            }
                        },
                        onValueChange = { nextQuery ->
                            if (nextQuery != session.query) {
                                session.query = nextQuery
                                recentExpanded = true
                                queueRecent(nextQuery, session.mode)
                                session.completedSearchKey = null
                                resetGridForFilterChange()
                            }
                        },
                        onLeftWhenEmpty = {
                            focusSidebar()
                        },
                        onRight = { runCatching { modeRequester.requestFocus() } },
                        onUp = {},
                        onDown = {
                            val target = if (showSearchPanel) recentFirstRequester else typeRequester
                            if (!runCatching { target.requestFocus() }.getOrDefault(false)) {
                                runCatching { typeRequester.requestFocus() }
                            }
                        },
                        onInputBounds = { inputBounds = it },
                    )
                }

                if (searching || contentDiscovering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp),
                        color = TvDesign.Accent.copy(alpha = .90f),
                        trackColor = TvDesign.White.copy(alpha = .08f),
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(TvDesign.SurfaceRaised)
                            .border(
                                width = 1.dp,
                                color = TvDesign.White.copy(alpha = .12f),
                                shape = RoundedCornerShape(50.dp),
                            )
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvSearchFilterButton(
                            label = session.typeFilter.label,
                            requester = typeRequester,
                            onFocused = {
                                if (!navExpanded) lastContentTarget = "type"
                            },
                            onClick = {
                                dialogReturnFocus = { runCatching { typeRequester.requestFocus() } }
                                choiceDialog = SearchChoice(
                                    title = "Type",
                                    options = TvSearchTypeFilter.entries.map { it.label },
                                    selected = session.typeFilter.label,
                                    onSelected = { label ->
                                        TvSearchTypeFilter.entries
                                            .firstOrNull { it.label == label }
                                            ?.let { next ->
                                                if (next != session.typeFilter) {
                                                    session.typeFilter = next
                                                    session.genre = null
                                                    resetGridForFilterChange()
                                                }
                                            }
                                        dismissChoiceDialog()
                                    },
                                )
                            },
                            onLeft = {
                                focusSidebar()
                            },
                            onRight = { runCatching { sortRequester.requestFocus() } },
                            onUp = { runCatching { fieldRequester.requestFocus() } },
                            onDown = ::focusFirstResult,
                        )
                        TvSearchFilterButton(
                            label = session.sortMode.label,
                            requester = sortRequester,
                            onFocused = {
                                if (!navExpanded) lastContentTarget = "sort"
                            },
                            onClick = {
                                dialogReturnFocus = { runCatching { sortRequester.requestFocus() } }
                                choiceDialog = SearchChoice(
                                    title = "Sort",
                                    options = TvSearchSortMode.entries.map { it.label },
                                    selected = session.sortMode.label,
                                    onSelected = { label ->
                                        TvSearchSortMode.entries
                                            .firstOrNull { it.label == label }
                                            ?.let { next ->
                                                if (next != session.sortMode) {
                                                    session.sortMode = next
                                                    resetGridForFilterChange()
                                                }
                                            }
                                        dismissChoiceDialog()
                                    },
                                )
                            },
                            onLeft = { runCatching { typeRequester.requestFocus() } },
                            onRight = { runCatching { genreRequester.requestFocus() } },
                            onUp = { runCatching { fieldRequester.requestFocus() } },
                            onDown = ::focusFirstResult,
                        )
                        TvSearchFilterButton(
                            label = session.genre ?: "All Genres",
                            requester = genreRequester,
                            onFocused = {
                                if (!navExpanded) lastContentTarget = "genre"
                            },
                            onClick = {
                                dialogReturnFocus = { runCatching { genreRequester.requestFocus() } }
                                choiceDialog = SearchChoice(
                                    title = "Genre",
                                    options = listOf("All Genres") + availableGenres,
                                    selected = session.genre ?: "All Genres",
                                    onSelected = { label ->
                                        val next = label.takeUnless { it == "All Genres" }
                                        if (next != session.genre) {
                                            session.genre = next
                                            resetGridForFilterChange()
                                        }
                                        dismissChoiceDialog()
                                    },
                                )
                            },
                            onLeft = { runCatching { sortRequester.requestFocus() } },
                            onRight = { true },
                            onUp = { runCatching { fieldRequester.requestFocus() } },
                            onDown = ::focusFirstResult,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (searchingMode && normalizedQuery.length < 2) {
                        Text("Type at least 2 characters", color = TvDesign.Muted, fontSize = 10.sp)
                    } else if (searchingMode && !searching) {
                        Text(
                            text = when {
                                session.mode == TvSearchMode.ACTOR && !session.actorSourceAvailable ->
                                    "Actor search unavailable"
                                filteredItems.isEmpty() -> "No results"
                                else -> "${filteredItems.size} results"
                            },
                            color = TvDesign.Muted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (searchingMode) 6.dp else 14.dp))

            when {
                !searchingMode && prepared.sourceEmpty && !contentDiscovering -> {
                    SearchEmptyState(
                        title = "Nothing to discover yet",
                        body = "Enable a catalog in Content Manager to populate Discover.",
                    )
                }

                !searchingMode && filteredItems.isEmpty() && !contentDiscovering -> {
                    SearchEmptyState(
                        title = "No ${session.typeFilter.label} titles",
                        body = if (session.typeFilter == TvSearchTypeFilter.ANIME) {
                            "No enabled catalog currently exposes Anime browsing."
                        } else {
                            "Try another type, sort or genre filter."
                        },
                    )
                }

                searchingMode && normalizedQuery.length >= 2 && filteredItems.isEmpty() && !searching && !preparing -> {
                    SearchEmptyState(
                        title = if (
                            session.mode == TvSearchMode.ACTOR && !session.actorSourceAvailable
                        ) "Actor search unavailable"
                        else if (titleSearch != null) "No matches for ${titleSearch.year}"
                        else "No matches",
                        body = when {
                            session.mode == TvSearchMode.ACTOR && !session.actorSourceAvailable ->
                                "Enable an actor-capable metadata source or add a TMDB API key."
                            session.mode == TvSearchMode.ACTOR ->
                                "Try another actor name or change the filters."
                            else -> "Try another title or change the filters."
                        },
                        action = {
                            if (titleSearch != null) {
                                TvSearchFilterButton(
                                    label = "Search all years",
                                    requester = allYearsRequester,
                                    onFocused = { if (!navExpanded) lastContentTarget = "all-years" },
                                    onClick = {
                                        fieldRequester.requestFocus()
                                        session.query = titleSearch.title
                                        queueRecent(titleSearch.title, session.mode)
                                        session.completedSearchKey = null
                                        resetGridForFilterChange()
                                    },
                                    onLeft = ::focusSidebar,
                                    onRight = null,
                                    onUp = { sortRequester.requestFocus() },
                                    onDown = { true },
                                )
                            }
                        },
                    )
                }

                filteredItems.isNotEmpty() -> {
                    TvPosterGridReveal {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(searchColumns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = contentStartPadding,
                                end = gridEndPadding,
                                top = SEARCH_FOCUS_PAINT_INSET,
                                bottom = 36.dp + SEARCH_FOCUS_PAINT_INSET,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            gridItemsIndexed(
                                items = filteredItems,
                                key = { _, item -> mediaKey(item) },
                            ) { index, item ->
                                val key = mediaKey(item)
                                TvSearchPosterTile(
                                    item = item,
                                    catalogLabel = searchCatalogLabel(runtime, item),
                                    requester = resultRequesterCache.getOrPut(key) { FocusRequester() },
                                    onFocused = {
                                        if (!navExpanded) {
                                            lastContentTarget = "result:$key"
                                            session.focusedMediaKey = key
                                        }
                                    },
                                    onClick = {
                                        session.focusedMediaKey = key
                                        session.restoreResultsFocus = true
                                        session.firstVisibleItemIndex = gridState.firstVisibleItemIndex
                                        session.firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
                                        savePendingRecent()
                                        onOpenMedia(item)
                                    },
                                    onLongClick = { actionMedia = item },
                                    onUpFromFirstRow = if (index < searchColumns) {
                                        { runCatching { typeRequester.requestFocus() } }
                                    } else null,
                                    onLeftFromFirstColumn = if (index % searchColumns == 0) {
                                        {
                                            focusSidebar()
                                        }
                                    } else null,
                                    blockRight =
                                        index % searchColumns == searchColumns - 1 || index == filteredItems.lastIndex,
                                )
                            }
                        }
                    }
                }

                searching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = TvDesign.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        val bounds = inputBounds
        if (showSearchPanel && bounds != null && bounds.width > 0f) {
            val left = with(density) { (bounds.left - pageOrigin.x).toDp() } - 20.dp
            // The text box is vertically centered in the 50 dp bar.
            val top = with(density) { (bounds.center.y - pageOrigin.y).toDp() } + 23.dp
            val width = with(density) { bounds.width.toDp() } +
                (if (session.query.isNotEmpty()) 70.dp else 30.dp)
            Box(Modifier.offset(x = left, y = top).width(width).zIndex(2f)) {
                TvRecentSearchList(
                    entries = matchingRecents,
                    firstRequester = recentFirstRequester,
                    onUp = { fieldRequester.requestFocus() },
                    onSelect = ::useRecent,
                    onRemove = { entry ->
                        recentSearches = recentStore.remove(entry)
                        if (matchingRecents.size == 1) closeRecent()
                    },
                    onClear = { recentSearches = recentStore.clear(); closeRecent() },
                )
            }
        }

        TvSidebar(
            selected = "Search",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {
                if (active && navExpanded) {
                    PerformanceDiagnostics.captureRuntimeEvent("TV_TAB_NAV_FOCUS tab=Search")
                }
            },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                navExpanded = false
                runCatching {
                    when {
                        lastContentTarget == "type" -> typeRequester.requestFocus()
                        lastContentTarget == "sort" -> sortRequester.requestFocus()
                        lastContentTarget == "genre" -> genreRequester.requestFocus()
                        lastContentTarget == "mode" -> modeRequester.requestFocus()
                        lastContentTarget == "all-years" && showYearFallback -> allYearsRequester.requestFocus()
                        lastContentTarget.startsWith("result:") -> {
                            val key = lastContentTarget.removePrefix("result:")
                            resultRequesterCache[key]?.requestFocus() ?: fieldRequester.requestFocus()
                        }
                        else -> fieldRequester.requestFocus()
                    }
                }.isSuccess
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )

        choiceDialog?.let { dialog ->
            TvSearchChoiceDialog(dialog = dialog)
        }
    }

    actionMedia?.let { media ->
        TvPosterActionDialog(
            media = media,
            libraryStore = runtime.libraryStore,
            onOpenDetails = { savePendingRecent(); onOpenMedia(media) },
            onChanged = {},
            onDismiss = { actionMedia = null },
        )
    }
}

@Composable
private fun TvMobileSearchField(
    value: String,
    mode: TvSearchMode,
    requester: FocusRequester,
    onOpenRecent: () -> Unit,
    onSubmit: () -> Unit,
    modeRequester: FocusRequester,
    onFocused: () -> Unit,
    onModeFocused: () -> Unit,
    onModeChange: (TvSearchMode) -> Unit,
    onValueChange: (String) -> Unit,
    onLeftWhenEmpty: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onInputBounds: (Rect) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val clearRequester = remember { FocusRequester() }
    val editor = rememberTvOkToEdit(onActivate = {
        onOpenRecent()
        false
    })
    val shape = RoundedCornerShape(50.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) TvDesign.Accent.copy(alpha = .86f)
                else TvDesign.White.copy(alpha = .13f),
                shape = shape,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TvDesign.Muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(11.dp))
        Box(
            Modifier.weight(1f).onGloballyPositioned { onInputBounds(it.boundsInRoot()) },
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = editor.readOnly,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = TextStyle(
                    color = TvDesign.White,
                    fontSize = 15.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocused()
                    }
                    .then(editor.modifier)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (value.isBlank()) {
                                    onLeftWhenEmpty()
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (value.isNotEmpty()) clearRequester.requestFocus() else onRight()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                onUp()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                onDown()
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            text = if (mode == TvSearchMode.ACTOR) {
                                "Search actor name..."
                            } else {
                                "Search title or title + year…"
                            },
                            color = TvDesign.Muted,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
        }

        if (value.isNotEmpty()) {
            TvSearchClearButton("Clear search text", clearRequester, onUp, onDown) {
                requester.requestFocus()
                onValueChange("")
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(1.dp)
                .height(24.dp)
                .background(TvDesign.White.copy(alpha = .14f)),
        )

        TvSearchModeToggle(
            mode = mode,
            requester = modeRequester,
            onFocused = onModeFocused,
            onModeChange = onModeChange,
            onLeft = { runCatching { requester.requestFocus() } },
            onUp = { runCatching { requester.requestFocus() } },
            onDown = onDown,
        )
    }
}

@Composable
private fun TvSearchModeToggle(
    mode: TvSearchMode,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onModeChange: (TvSearchMode) -> Unit,
    onLeft: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50.dp)

    Row(
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeft()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        onUp()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown()
                        true
                    }
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) {
                            onModeChange(
                                if (mode == TvSearchMode.TITLE) TvSearchMode.ACTOR else TvSearchMode.TITLE
                            )
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable {
                onModeChange(
                    if (mode == TvSearchMode.TITLE) TvSearchMode.ACTOR else TvSearchMode.TITLE
                )
            }
            .background(
                if (focused) TvDesign.Accent.copy(alpha = .10f) else Color.Transparent,
                shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.Accent.copy(alpha = .70f) else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Title",
            color = if (mode == TvSearchMode.TITLE) TvDesign.Accent else TvDesign.Muted,
            fontSize = 11.sp,
            fontWeight = if (mode == TvSearchMode.TITLE) FontWeight.Bold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(TvDesign.Accent.copy(alpha = .92f))
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(if (mode == TvSearchMode.ACTOR) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(TvDesign.Black),
            )
        }
        Text(
            text = "Actor",
            color = if (mode == TvSearchMode.ACTOR) TvDesign.Accent else TvDesign.Muted,
            fontSize = 11.sp,
            fontWeight = if (mode == TvSearchMode.ACTOR) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TvSearchFilterButton(
    label: String,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: (() -> Unit)?,
    onRight: (() -> Unit)?,
    onUp: () -> Unit,
    onDown: () -> Boolean,
    showChevron: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50.dp)

    Row(
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeft?.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onRight?.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        onUp()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN ->
                        onDown()
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .background(
                if (focused) TvDesign.Accent else Color.Transparent,
                shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.Accent else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (focused) TvDesign.OnAccent.copy(alpha = .82f) else TvDesign.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) Text(
            text = "⌄",
            color = if (focused) TvDesign.OnAccent.copy(alpha = .58f) else TvDesign.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TvSearchPosterTile(
    item: MediaItem,
    catalogLabel: String?,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpFromFirstRow: (() -> Unit)?,
    onLeftFromFirstColumn: (() -> Unit)?,
    blockRight: Boolean,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .graphicsLayer {
                shadowElevation = if (focused) 8.dp.toPx() else 0f
                shape = RoundedCornerShape(10.dp)
                clip = false
            }
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_UP && onUpFromFirstRow != null -> {
                        onUpFromFirstRow()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        code == KeyEvent.KEYCODE_DPAD_LEFT &&
                        onLeftFromFirstColumn != null -> {
                        onLeftFromFirstColumn()
                        true
                    }
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT && blockRight -> true
                    else -> false
                }
            }
            .tvPosterActivation(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .clickable(onClick = onClick),
    ) {
        TvNetworkImage(
            url = item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (focused) 1.dp else 0.dp,
                    color = if (focused) TvDesign.Accent.copy(alpha = .88f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.name,
            color = TvDesign.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOfNotNull(
                SearchPolicy.releaseYear(item).takeIf { it > 0 }?.toString()
                    ?: item.releaseInfo.takeIf { catalogLabel.isNullOrBlank() },
                searchTypeLabel(item),
                catalogLabel?.takeIf { it.isNotBlank() },
            ).joinToString(" • "),
            color = TvDesign.Muted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class SearchChoice(
    val title: String,
    val options: List<String>,
    val selected: String,
    val onSelected: (String) -> Unit,
)

@Composable
private fun TvSearchChoiceDialog(
    dialog: SearchChoice,
) {
    val selectedIndex = dialog.options.indexOf(dialog.selected).coerceAtLeast(0)
    val requesters = remember(dialog.title, dialog.options) {
        List(dialog.options.size) { FocusRequester() }
    }

    LaunchedEffect(dialog.title, dialog.selected) {
        delay(80)
        requesters.getOrNull(selectedIndex)?.let { runCatching { it.requestFocus() } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(430.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TvDesign.SurfaceRaised)
                .border(
                    1.dp,
                    TvDesign.White.copy(alpha = .12f),
                    RoundedCornerShape(18.dp),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = dialog.title,
                color = TvDesign.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lazyItems(
                    items = dialog.options,
                    key = { it },
                ) { option ->
                    val index = dialog.options.indexOf(option)
                    TvSearchChoiceRow(
                        label = option,
                        selected = option == dialog.selected,
                        requester = requesters[index],
                        onClick = { dialog.onSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSearchChoiceRow(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else false
            }
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> TvDesign.Accent.copy(alpha = .14f)
                    selected -> TvDesign.Accent.copy(alpha = .07f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.Accent.copy(alpha = .78f) else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (selected) TvDesign.Accent else TvDesign.Muted,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TvDesign.Accent),
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            color = TvDesign.White,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TvSearchClearButton(
    label: String,
    requester: FocusRequester,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(40.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else if (event.type == KeyEventType.KeyDown) {
                    val move = when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> onUp
                        KeyEvent.KEYCODE_DPAD_DOWN -> onDown
                        else -> null
                    }
                    move?.invoke()
                    move != null
                } else false
            }
            .background(if (focused) TvDesign.Accent.copy(alpha = .22f) else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Close, contentDescription = label, tint = TvDesign.White)
    }
}

@Composable
private fun TvRecentSearchList(
    entries: List<RecentSearch>,
    firstRequester: FocusRequester,
    onUp: () -> Unit,
    onSelect: (RecentSearch) -> Unit,
    onRemove: (RecentSearch) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(TvDesign.SurfaceRaised, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 172.dp)) {
            if (entries.isNotEmpty()) {
                item(key = "recent-heading") { TvSearchPanelHeading("Recent Search") }
            }
            lazyItems(entries, key = { "recent:${it.key}" }) { entry ->
                val first = entry.key == entries.first().key
                val requester = if (first) firstRequester else remember(entry.key) { FocusRequester() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TvRecentSearchAction(entry.label, requester, onUp.takeIf { first }) { onSelect(entry) }
                    }
                    TvSearchClearButton("Remove ${entry.label}", remember(entry.key) { FocusRequester() },
                        onUp = onUp.takeIf { first }) { onRemove(entry) }
                }
            }

        }
        if (entries.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TvDesign.White.copy(alpha = .14f)))
            TvRecentSearchAction("Clear all", remember { FocusRequester() }, onClick = onClear)
        }
    }
}

@Composable
private fun TvSearchPanelHeading(label: String) {
    Text(label, color = TvDesign.Muted, fontSize = 11.sp,
        modifier = Modifier.padding(start = 12.dp, top = 5.dp, bottom = 2.dp))
}

@Composable
private fun TvRecentSearchAction(
    label: String,
    requester: FocusRequester,
    onUp: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                when {
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    onUp != null && event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        onUp()
                        true
                    }
                    else -> false
                }
            }
            .background(if (focused) TvDesign.Accent.copy(alpha = .22f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = TvDesign.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchEmptyState(
    title: String,
    body: String,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 52.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(
            text = body,
            color = TvDesign.Muted,
            fontSize = 12.sp,
        )
        action()
    }
}

private fun TvSearchSortMode.toDiscoverSortMode(): DiscoverSortMode =
    when (this) {
        TvSearchSortMode.POPULAR -> DiscoverSortMode.POPULAR
        TvSearchSortMode.TRENDING -> DiscoverSortMode.TRENDING
        TvSearchSortMode.NEWEST -> DiscoverSortMode.NEWEST
    }

private fun TvSearchTypeFilter.toSearchMediaFilter(): SearchMediaFilter =
    when (this) {
        TvSearchTypeFilter.ALL -> SearchMediaFilter.ALL
        TvSearchTypeFilter.MOVIES -> SearchMediaFilter.MOVIES
        TvSearchTypeFilter.SERIES -> SearchMediaFilter.SERIES
        TvSearchTypeFilter.ANIME -> SearchMediaFilter.ANIME
    }

private fun searchSortActorItems(
    items: List<MediaItem>,
    mode: TvSearchSortMode,
): List<MediaItem> =
    SearchResultOrderPolicy.sortActorItems(items, mode.toDiscoverSortMode())

private fun searchSortItems(
    items: List<MediaItem>,
    mode: TvSearchSortMode,
    query: String? = null,
): List<MediaItem> =
    SearchResultOrderPolicy.sortTitleItems(items, mode.toDiscoverSortMode(), query)

private fun searchCatalogLabel(runtime: TvRuntime, item: MediaItem): String? {
    val direct = item.catalogSources.firstOrNull { it.isNotBlank() }
    val resolved = direct
        ?: runtime.engine.extension(item.sourceExtensionId)?.descriptor?.name
        ?: item.sourceExtensionId?.substringAfterLast('.')?.substringAfterLast(':')
    return resolved?.let(::searchPrettyCatalogName)
}

private fun searchPrettyCatalogName(value: String): String? {
    val cleaned = value
        .trim()
        .replace(Regex("""(?i)\s+(stremio\s+)?addon$"""), "")
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }
        ?: return null

    val lower = cleaned.lowercase()
    return when {
        "cinemeta" in lower -> "Cinemeta"
        "mediafusion" in lower || "media fusion" in lower -> "MediaFusion"
        Regex("""\btmdb\b""").containsMatchIn(lower) || "the movie database" in lower -> "TMDB"
        Regex("""\bimdb\b""").containsMatchIn(lower) -> "IMDb"
        Regex("""\btrakt\b""").containsMatchIn(lower) -> "Trakt"
        else -> cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun searchTypeLabel(item: MediaItem): String = when (item.type.lowercase()) {
    "movie" -> "Movie"
    "series", "tv" -> "Series"
    "anime" -> "Anime"
    else -> item.type.replaceFirstChar { it.uppercase() }
}

private fun mediaKey(item: MediaItem): String = "${item.type}:${item.id}"

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
