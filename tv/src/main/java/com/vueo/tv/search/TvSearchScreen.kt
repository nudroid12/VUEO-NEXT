package com.vueo.tv.search

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SEARCH_COLUMNS = 8

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
internal class TvSearchSession {
    var query by mutableStateOf("")
    var typeFilter by mutableStateOf(TvSearchTypeFilter.ALL)
    var sortMode by mutableStateOf(TvSearchSortMode.POPULAR)
    var genre by mutableStateOf<String?>(null)
    var mode by mutableStateOf(TvSearchMode.TITLE)

    var searchResults by mutableStateOf<List<MediaItem>>(emptyList())
    var discoverRows by mutableStateOf<List<CatalogRow>>(emptyList())
    var actorSourceAvailable by mutableStateOf(true)

    var focusedMediaKey by mutableStateOf<String?>(null)
    var restoreResultsFocus by mutableStateOf(false)
    var firstVisibleItemIndex by mutableStateOf(0)
    var firstVisibleItemScrollOffset by mutableStateOf(0)
}

@Composable
internal fun TvSearchScreen(
    runtime: TvRuntime,
    contentVersion: Int,
    session: TvSearchSession,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(session.discoverRows.isEmpty()) }
    var requestId by remember { mutableStateOf(0L) }
    var navExpanded by remember { mutableStateOf(false) }
    var lastContentTarget by remember { mutableStateOf("field") }
    var choiceDialog by remember { mutableStateOf<SearchChoice?>(null) }
    var dialogReturnFocus by remember { mutableStateOf<(() -> Unit)?>(null) }

    val fieldRequester = remember { FocusRequester() }
    val modeRequester = remember { FocusRequester() }
    val typeRequester = remember { FocusRequester() }
    val sortRequester = remember { FocusRequester() }
    val genreRequester = remember { FocusRequester() }
    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        if (!session.restoreResultsFocus) {
            runCatching { fieldRequester.requestFocus() }
        }
    }

    LaunchedEffect(contentVersion) {
        discovering = session.discoverRows.isEmpty()
        val current = runCatching { runtime.homeRows(forceRefresh = false) }
            .getOrDefault(emptyList())

        session.discoverRows = current.ifEmpty {
            CatalogDiscoveryCache.home(allowStale = true).orEmpty()
        }
        discovering = false
    }

    LaunchedEffect(session.query, contentVersion, session.mode) {
        val normalized = session.query.trim()
        requestId += 1L
        val thisRequest = requestId
        val requestedMode = session.mode

        if (normalized.length < 2) {
            searching = false
            session.actorSourceAvailable = true
            session.searchResults =
                if (requestedMode == TvSearchMode.TITLE && normalized.length >= 2) {
                    searchRankAndDedupe(
                        items = CatalogDiscoveryCache.searchLocal(normalized),
                        query = normalized,
                    )
                } else {
                    emptyList()
                }
            return@LaunchedEffect
        }

        if (requestedMode == TvSearchMode.TITLE) {
            session.actorSourceAvailable = true
            val local = searchRankAndDedupe(
                items = CatalogDiscoveryCache.searchLocal(normalized),
                query = normalized,
            )
            if (local.isNotEmpty() || session.searchResults.isEmpty()) {
                session.searchResults = local
            }

            searching = true
            delay(250)
            if (
                thisRequest != requestId ||
                session.query.trim() != normalized ||
                session.mode != requestedMode
            ) return@LaunchedEffect

            val remote = try {
                runtime.engine.search(
                    query = normalized,
                    onPartial = { partial ->
                        if (
                            thisRequest == requestId &&
                            session.query.trim() == normalized &&
                            session.mode == requestedMode
                        ) {
                            session.searchResults = searchRankAndDedupe(
                                items = partial + local,
                                query = normalized,
                            )
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }

            if (
                thisRequest == requestId &&
                session.query.trim() == normalized &&
                session.mode == requestedMode
            ) {
                session.searchResults = searchRankAndDedupe(
                    items = remote + local,
                    query = normalized,
                )
                searching = false
            }
            return@LaunchedEffect
        }

        val tmdbApiKey = runtime.pluginStore.tmdbApiKey()
        val addonActorSearch = runtime.engine.hasActorSearchAddons()
        session.actorSourceAvailable = addonActorSearch || tmdbApiKey.isNotBlank()

        if (!session.actorSourceAvailable) {
            session.searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }

        if (session.searchResults.isEmpty()) searching = true
        delay(250)
        if (
            thisRequest != requestId ||
            session.query.trim() != normalized ||
            session.mode != requestedMode
        ) return@LaunchedEffect

        coroutineScope {
            var providerItems = emptyList<MediaItem>()
            var tmdbItems = emptyList<MediaItem>()

            fun publish() {
                if (
                    thisRequest == requestId &&
                    session.query.trim() == normalized &&
                    session.mode == requestedMode
                ) {
                    session.searchResults = runtime.engine.mergeActorResults(
                        items = providerItems + tmdbItems,
                    )
                }
            }

            launch {
                providerItems =
                    if (!addonActorSearch) {
                        emptyList()
                    } else {
                        try {
                            runtime.engine.searchActor(
                                query = normalized,
                                onPartial = { partial ->
                                    providerItems = partial
                                    publish()
                                },
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                publish()
            }

            launch {
                tmdbItems =
                    if (tmdbApiKey.isBlank()) {
                        emptyList()
                    } else {
                        try {
                            TmdbEnhancementClient.actorFilmography(
                                query = normalized,
                                apiKey = tmdbApiKey,
                            ).orEmpty()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                publish()
            }
        }

        if (
            thisRequest == requestId &&
            session.query.trim() == normalized &&
            session.mode == requestedMode
        ) searching = false
    }

    val normalizedQuery = session.query.trim()
    val searchingMode = normalizedQuery.isNotBlank()

    val animeCatalogKeys = remember(session.discoverRows) {
        session.discoverRows
            .filter { row ->
                listOf(row.id, row.title, row.providerName)
                    .any { it.contains("anime", ignoreCase = true) }
            }
            .flatMap { it.items }
            .map { mediaKey(it) }
            .toSet()
    }

    val discoverBaseItems = remember(session.discoverRows, session.sortMode) {
        session.discoverRows
            .sortedByDescending { searchCatalogPriority(it, session.sortMode) }
            .flatMap { it.items }
            .distinctBy(::mediaKey)
    }

    val sourceItems = if (searchingMode) session.searchResults else discoverBaseItems

    val availableGenres = remember(sourceItems, session.typeFilter, animeCatalogKeys) {
        sourceItems
            .filter { searchMatchesType(it, session.typeFilter, animeCatalogKeys) }
            .flatMap { it.genres }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("anime", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    LaunchedEffect(availableGenres, session.genre) {
        if (
            session.genre != null &&
            availableGenres.none { it.equals(session.genre, ignoreCase = true) }
        ) {
            session.genre = null
        }
    }

    val filteredItems = remember(
        sourceItems,
        session.typeFilter,
        session.genre,
        session.sortMode,
        searchingMode,
        session.mode,
        animeCatalogKeys,
    ) {
        val filtered = sourceItems.filter { item ->
            searchMatchesType(item, session.typeFilter, animeCatalogKeys) &&
                searchMatchesGenre(item, session.genre)
        }

        when {
            searchingMode && session.mode == TvSearchMode.ACTOR ->
                searchSortActorItems(filtered, session.sortMode)

            searchingMode ->
                searchSortItems(filtered, session.sortMode, normalizedQuery)

            session.sortMode == TvSearchSortMode.NEWEST ->
                searchSortItems(filtered, session.sortMode)

            else -> filtered
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = session.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = session.firstVisibleItemScrollOffset,
    )
    val resultKeys = remember(filteredItems) { filteredItems.map(::mediaKey) }
    val resultRequesters = remember(resultKeys) { resultKeys.associateWith { FocusRequester() } }

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

    LaunchedEffect(session.restoreResultsFocus, filteredItems) {
        if (!session.restoreResultsFocus) return@LaunchedEffect
        val key = session.focusedMediaKey ?: return@LaunchedEffect
        val index = filteredItems.indexOfFirst { mediaKey(it) == key }
        if (index < 0) return@LaunchedEffect

        runCatching { gridState.scrollToItem(index) }
        delay(90)
        val restored = runCatching {
            resultRequesters.getValue(key).requestFocus()
            true
        }.getOrDefault(false)
        if (restored) session.restoreResultsFocus = false
    }

    fun focusFirstResult(): Boolean {
        val firstKey = resultKeys.firstOrNull() ?: return false
        return runCatching {
            resultRequesters.getValue(firstKey).requestFocus()
            true
        }.getOrDefault(false)
    }

    BackHandler(enabled = choiceDialog != null) {
        dialogReturnFocus?.invoke()
        choiceDialog = null
        dialogReturnFocus = null
    }
    BackHandler(enabled = choiceDialog == null, onBack = onBack)

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 46.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = 92.dp, end = 52.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Search",
                    color = TvDesign.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )

                Box(modifier = Modifier.fillMaxWidth(.76f)) {
                    TvMobileSearchField(
                        value = session.query,
                        mode = session.mode,
                        requester = fieldRequester,
                        onFocused = {
                            navExpanded = false
                            lastContentTarget = "field"
                        },
                        onValueChange = {
                            session.query = it
                            session.restoreResultsFocus = false
                            session.focusedMediaKey = null
                            session.firstVisibleItemIndex = 0
                            session.firstVisibleItemScrollOffset = 0
                        },
                        onLeftWhenEmpty = {
                            navExpanded = true
                            runCatching { navRequesters.getValue("Search").requestFocus() }
                        },
                        onUp = {},
                        onDown = { runCatching { typeRequester.requestFocus() } },
                    )
                }

                if (searching || discovering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(.76f)
                            .height(1.dp),
                        color = TvDesign.White.copy(alpha = .90f),
                        trackColor = TvDesign.White.copy(alpha = .08f),
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvSearchFilterButton(
                        label = session.typeFilter.label,
                        requester = typeRequester,
                        onFocused = {
                            navExpanded = false
                            lastContentTarget = "type"
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
                                        ?.let { session.typeFilter = it }
                                    dialogReturnFocus?.invoke()
                                    choiceDialog = null
                                    dialogReturnFocus = null
                                },
                            )
                        },
                        onLeft = {
                            navExpanded = true
                            runCatching { navRequesters.getValue("Search").requestFocus() }
                        },
                        onRight = { runCatching { sortRequester.requestFocus() } },
                        onUp = { runCatching { fieldRequester.requestFocus() } },
                        onDown = ::focusFirstResult,
                    )
                    TvSearchFilterButton(
                        label = session.sortMode.label,
                        requester = sortRequester,
                        onFocused = {
                            navExpanded = false
                            lastContentTarget = "sort"
                        },
                        onClick = {
                            dialogReturnFocus = { runCatching { sortRequester.requestFocus() } }
                            choiceDialog = SearchChoice(
                                title = "Discover",
                                options = TvSearchSortMode.entries.map { it.label },
                                selected = session.sortMode.label,
                                onSelected = { label ->
                                    TvSearchSortMode.entries
                                        .firstOrNull { it.label == label }
                                        ?.let { session.sortMode = it }
                                    dialogReturnFocus?.invoke()
                                    choiceDialog = null
                                    dialogReturnFocus = null
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
                            navExpanded = false
                            lastContentTarget = "genre"
                        },
                        onClick = {
                            dialogReturnFocus = { runCatching { genreRequester.requestFocus() } }
                            choiceDialog = SearchChoice(
                                title = "Genre",
                                options = listOf("All Genres") + availableGenres,
                                selected = session.genre ?: "All Genres",
                                onSelected = { label ->
                                    session.genre = label.takeUnless { it == "All Genres" }
                                    dialogReturnFocus?.invoke()
                                    choiceDialog = null
                                    dialogReturnFocus = null
                                },
                            )
                        },
                        onLeft = { runCatching { sortRequester.requestFocus() } },
                        onRight = { runCatching { modeRequester.requestFocus() } },
                        onUp = { runCatching { fieldRequester.requestFocus() } },
                        onDown = ::focusFirstResult,
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(TvDesign.White.copy(alpha = .16f)),
                    )

                    TvSearchModeToggle(
                        mode = session.mode,
                        requester = modeRequester,
                        onFocused = {
                            navExpanded = false
                            lastContentTarget = "mode"
                        },
                        onModeChange = { next ->
                            if (next != session.mode) {
                                session.mode = next
                                session.searchResults = emptyList()
                                session.genre = null
                                session.focusedMediaKey = null
                                session.restoreResultsFocus = false
                                session.firstVisibleItemIndex = 0
                                session.firstVisibleItemScrollOffset = 0
                            }
                        },
                        onLeft = { runCatching { genreRequester.requestFocus() } },
                        onUp = { runCatching { fieldRequester.requestFocus() } },
                        onDown = { focusFirstResult() },
                    )

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

                Text(
                    text = if (searchingMode) "Search Results" else "Discover",
                    color = TvDesign.White.copy(alpha = .94f),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            when {
                !searchingMode && session.discoverRows.isEmpty() && !discovering -> {
                    SearchEmptyState(
                        title = "Nothing to discover yet",
                        body = "Enable a catalog in Content Manager to populate Discover.",
                    )
                }

                searchingMode && normalizedQuery.length >= 2 && filteredItems.isEmpty() && !searching -> {
                    SearchEmptyState(
                        title = if (
                            session.mode == TvSearchMode.ACTOR && !session.actorSourceAvailable
                        ) "Actor search unavailable" else "No matches",
                        body = when {
                            session.mode == TvSearchMode.ACTOR && !session.actorSourceAvailable ->
                                "Enable an actor-capable metadata source or add a TMDB API key."
                            session.mode == TvSearchMode.ACTOR ->
                                "Try another actor name or change the filters."
                            else -> "Try another title or change the filters."
                        },
                    )
                }

                filteredItems.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(SEARCH_COLUMNS),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 92.dp,
                            end = 52.dp,
                            top = 2.dp,
                            bottom = 36.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItemsIndexed(
                            items = filteredItems,
                            key = { _, item -> mediaKey(item) },
                        ) { index, item ->
                            val key = mediaKey(item)
                            TvSearchPosterTile(
                                item = item,
                                catalogLabel = searchCatalogLabel(runtime, item),
                                requester = resultRequesters.getValue(key),
                                onFocused = {
                                    navExpanded = false
                                    lastContentTarget = "result:$key"
                                    session.focusedMediaKey = key
                                },
                                onClick = {
                                    session.focusedMediaKey = key
                                    session.restoreResultsFocus = true
                                    session.firstVisibleItemIndex = gridState.firstVisibleItemIndex
                                    session.firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
                                    onOpenMedia(item)
                                },
                                onUpFromFirstRow = if (index < SEARCH_COLUMNS) {
                                    { runCatching { typeRequester.requestFocus() } }
                                } else null,
                                onLeftFromFirstColumn = if (index % SEARCH_COLUMNS == 0) {
                                    {
                                        navExpanded = true
                                        runCatching { navRequesters.getValue("Search").requestFocus() }
                                    }
                                } else null,
                                blockRight =
                                    index % SEARCH_COLUMNS == SEARCH_COLUMNS - 1 || index == filteredItems.lastIndex,
                            )
                        }
                    }
                }

                searching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = TvDesign.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        TvSidebar(
            selected = "Search",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
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
                        lastContentTarget.startsWith("result:") -> {
                            val key = lastContentTarget.removePrefix("result:")
                            resultRequesters[key]?.requestFocus() ?: fieldRequester.requestFocus()
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
}

@Composable
private fun TvMobileSearchField(
    value: String,
    mode: TvSearchMode,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onValueChange: (String) -> Unit,
    onLeftWhenEmpty: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .86f)
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
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (value.isBlank()) {
                                    onLeftWhenEmpty()
                                    true
                                } else false
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
                                "Search movies, shows..."
                            },
                            color = TvDesign.Muted,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
        }
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
    val shape = RoundedCornerShape(12.dp)

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
                if (focused) TvDesign.White.copy(alpha = .10f) else Color.Transparent,
                shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .70f) else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Title",
            color = if (mode == TvSearchMode.TITLE) TvDesign.White else TvDesign.Muted,
            fontSize = 11.sp,
            fontWeight = if (mode == TvSearchMode.TITLE) FontWeight.Bold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(TvDesign.White.copy(alpha = .92f))
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
            color = if (mode == TvSearchMode.ACTOR) TvDesign.White else TvDesign.Muted,
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
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

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
                    event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown()
                        true
                    }
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .background(
                if (focused) TvDesign.White.copy(alpha = .12f) else TvDesign.SurfaceRaised,
                shape,
            )
            .border(
                width = 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .82f)
                else TvDesign.White.copy(alpha = .11f),
                shape = shape,
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TvDesign.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⌄",
            color = TvDesign.Muted,
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
    onUpFromFirstRow: (() -> Unit)?,
    onLeftFromFirstColumn: (() -> Unit)?,
    blockRight: Boolean,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(if (focused) 145 else 110),
        label = "searchPosterScale",
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
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
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onClick()
                        true
                    }
                    else -> false
                }
            }
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
                    color = if (focused) TvDesign.White.copy(alpha = .88f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.name,
            color = TvDesign.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (catalogLabel.isNullOrBlank()) {
                listOfNotNull(item.releaseInfo, searchTypeLabel(item)).joinToString(" • ")
            } else {
                listOf(searchTypeLabel(item), catalogLabel).joinToString(" • ")
            },
            color = TvDesign.Muted,
            fontSize = 8.sp,
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
                    focused -> TvDesign.White.copy(alpha = .14f)
                    selected -> TvDesign.White.copy(alpha = .07f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .78f) else Color.Transparent,
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
                    if (selected) TvDesign.White else TvDesign.Muted,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TvDesign.White),
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
private fun SearchEmptyState(
    title: String,
    body: String,
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
    }
}

private fun searchCatalogPriority(
    row: CatalogRow,
    mode: TvSearchSortMode,
): Int {
    val value = "${row.id} ${row.title}".lowercase()
    return when (mode) {
        TvSearchSortMode.POPULAR -> when {
            "popular" in value -> 100
            "top" in value -> 80
            else -> 0
        }
        TvSearchSortMode.TRENDING -> when {
            "trending" in value || "trend" in value -> 100
            "popular" in value -> 60
            else -> 0
        }
        TvSearchSortMode.NEWEST -> when {
            "new" in value || "latest" in value -> 100
            "recent" in value -> 90
            "release" in value -> 80
            else -> 0
        }
    }
}

private fun searchMatchesType(
    item: MediaItem,
    filter: TvSearchTypeFilter,
    animeCatalogKeys: Set<String>,
): Boolean = when (filter) {
    TvSearchTypeFilter.ALL -> true
    TvSearchTypeFilter.MOVIES ->
        item.type.equals("movie", ignoreCase = true) && !searchIsAnime(item, animeCatalogKeys)
    TvSearchTypeFilter.SERIES ->
        item.type.equals("series", ignoreCase = true) && !searchIsAnime(item, animeCatalogKeys)
    TvSearchTypeFilter.ANIME -> searchIsAnime(item, animeCatalogKeys)
}

private fun searchIsAnime(item: MediaItem, animeCatalogKeys: Set<String>): Boolean {
    if (
        item.type.equals("anime", ignoreCase = true) ||
        item.genres.any { it.equals("anime", ignoreCase = true) }
    ) return true

    if (mediaKey(item) in animeCatalogKeys) return true

    return listOfNotNull(item.sourceExtensionId, item.id)
        .any { it.contains("anime", ignoreCase = true) }
}

private fun searchMatchesGenre(item: MediaItem, genre: String?): Boolean =
    genre == null || item.genres.any { it.equals(genre, ignoreCase = true) }

private fun searchSortActorItems(
    items: List<MediaItem>,
    mode: TvSearchSortMode,
): List<MediaItem> = when (mode) {
    TvSearchSortMode.POPULAR,
    TvSearchSortMode.TRENDING -> items
    TvSearchSortMode.NEWEST -> items.sortedByDescending(::searchReleaseYear)
}

private fun searchSortItems(
    items: List<MediaItem>,
    mode: TvSearchSortMode,
    query: String? = null,
): List<MediaItem> {
    val normalizedQuery = query?.let(::searchNormalizeText).orEmpty()

    if (normalizedQuery.isBlank()) {
        return when (mode) {
            TvSearchSortMode.POPULAR -> items.sortedByDescending {
                it.imdbRating ?: it.tmdbRating ?: 0.0
            }
            TvSearchSortMode.TRENDING -> items
            TvSearchSortMode.NEWEST -> items.sortedByDescending(::searchReleaseYear)
        }
    }

    val relevance = compareByDescending<MediaItem> {
        searchRelevanceScore(it, normalizedQuery)
    }

    return when (mode) {
        TvSearchSortMode.POPULAR -> items.sortedWith(
            relevance
                .thenByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
                .thenByDescending(::searchReleaseYear)
        )
        TvSearchSortMode.TRENDING -> items.sortedWith(
            relevance.thenByDescending(::searchMetadataScore)
        )
        TvSearchSortMode.NEWEST -> items.sortedWith(
            relevance
                .thenByDescending(::searchReleaseYear)
                .thenByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
        )
    }
}

private fun searchRankAndDedupe(
    items: List<MediaItem>,
    query: String,
): List<MediaItem> {
    val normalizedQuery = searchNormalizeText(query)
    if (normalizedQuery.isBlank()) return items.distinctBy(::mediaKey)

    val groups = mutableListOf<MutableList<MediaItem>>()

    items
        .filter { searchIsRelevantEnough(it, normalizedQuery) }
        .forEach { candidate ->
            val candidateTitle = searchCanonicalTitle(candidate)
            val candidateType = searchCanonicalType(candidate.type)
            val candidateYear = searchReleaseYear(candidate)

            val target = groups.firstOrNull { group ->
                val sample = group.first()
                val sampleYear = searchReleaseYear(sample)
                candidateTitle == searchCanonicalTitle(sample) &&
                    candidateType == searchCanonicalType(sample.type) &&
                    (
                        candidateYear == 0 ||
                            sampleYear == 0 ||
                            kotlin.math.abs(candidateYear - sampleYear) <= 1
                        )
            }

            if (target == null) groups += mutableListOf(candidate) else target += candidate
        }

    return groups
        .mapNotNull { duplicates ->
            val best = duplicates.maxByOrNull { item ->
                searchRelevanceScore(item, normalizedQuery) * 100 + searchMetadataScore(item)
            } ?: return@mapNotNull null

            best.copy(
                genres = duplicates.flatMap { it.genres }.distinctBy { it.lowercase() },
                catalogSources = (best.catalogSources + duplicates.flatMap { it.catalogSources })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            )
        }
        .sortedWith(
            compareByDescending<MediaItem> { searchRelevanceScore(it, normalizedQuery) }
                .thenByDescending(::searchMetadataScore)
                .thenByDescending { it.imdbRating ?: it.tmdbRating ?: 0.0 }
        )
}

private fun searchIsRelevantEnough(item: MediaItem, query: String): Boolean {
    val normalizedQuery = searchNormalizeText(query)
    val titleQuery = searchTitleQuery(normalizedQuery)
    val title = searchNormalizeText(item.name)
    if (normalizedQuery.isBlank() || titleQuery.isBlank() || title.isBlank()) return false

    val score = searchRelevanceScore(item, normalizedQuery)
    if (score >= 44_000) return true

    val queryTokens = titleQuery.split(' ').filter { it.length >= 2 }
    val titleTokens = title.split(' ').filter { it.isNotBlank() }
    if (queryTokens.isEmpty()) return score > 0

    val matched = queryTokens.count { token ->
        titleTokens.any { titleToken ->
            titleToken == token || titleToken.startsWith(token) || token.startsWith(titleToken)
        }
    }

    return if (queryTokens.size == 1) matched == 1 else matched == queryTokens.size
}

private fun searchCanonicalTitle(item: MediaItem): String {
    var title = searchNormalizeText(item.name)
        .replace(Regex("""\s+(19|20)\d{2}$"""), "")

    if (searchCanonicalType(item.type) == "series") {
        title = title
            .replace(Regex("""\s+season\s+\d+.*$"""), "")
            .replace(Regex("""\s+(tv\s+)?series\s*$"""), "")
    }
    return title.trim()
}

private fun searchCanonicalType(value: String): String = when (value.trim().lowercase()) {
    "tv", "show", "shows", "series" -> "series"
    "film", "films", "movies", "movie" -> "movie"
    else -> value.trim().lowercase()
}

private fun searchRelevanceScore(item: MediaItem, query: String): Int {
    val normalizedQuery = searchNormalizeText(query)
    val titleQuery = searchTitleQuery(normalizedQuery)
    val title = searchNormalizeText(item.name)
    if (normalizedQuery.isBlank() || titleQuery.isBlank() || title.isBlank()) return 0

    val queryTokens = titleQuery.split(' ').filter { it.isNotBlank() }
    val titleTokens = title.split(' ').filter { it.isNotBlank() }
    val exactTokenMatches = queryTokens.count { it in titleTokens }
    val prefixTokenMatches = queryTokens.count { token ->
        titleTokens.any { it.startsWith(token) }
    }

    var score = when {
        title == titleQuery -> 120_000
        title.startsWith("$titleQuery ") -> 96_000
        title.contains(" $titleQuery ") || title.endsWith(" $titleQuery") -> 86_000
        title.contains(titleQuery) -> 76_000
        queryTokens.isNotEmpty() && exactTokenMatches == queryTokens.size -> 62_000
        queryTokens.isNotEmpty() && prefixTokenMatches == queryTokens.size -> 52_000
        else -> exactTokenMatches * 6_000 + prefixTokenMatches * 3_000
    }

    if (queryTokens.size > 1 && exactTokenMatches < queryTokens.size) {
        score -= (queryTokens.size - exactTokenMatches) * 4_000
    }

    val queryYear = Regex("""\b(19|20)\d{2}\b""")
        .find(normalizedQuery)
        ?.value
        ?.toIntOrNull()

    if (queryYear != null) {
        score += if (searchReleaseYear(item) == queryYear) 12_000 else -4_000
    }
    return score
}

private fun searchMetadataScore(item: MediaItem): Int {
    var score = 0
    if (!item.poster.isNullOrBlank()) score += 80
    if (!item.background.isNullOrBlank()) score += 35
    if (!item.description.isNullOrBlank()) score += 30
    if (!item.releaseInfo.isNullOrBlank()) score += 20
    if (item.genres.isNotEmpty()) score += 15
    if (item.catalogSources.isNotEmpty()) score += 12
    score += (((item.imdbRating ?: item.tmdbRating ?: 0.0) * 10.0).toInt())
    return score
}

private fun searchTitleQuery(normalizedQuery: String): String = normalizedQuery
    .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
    .trim()
    .replace(Regex("""\s+"""), " ")

private fun searchNormalizeText(value: String): String = value
    .lowercase()
    .replace(Regex("""[^a-z0-9]+"""), " ")
    .trim()
    .replace(Regex("""\s+"""), " ")

private fun searchReleaseYear(item: MediaItem): Int = item.releaseInfo
    ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value?.toIntOrNull() }
    ?: 0

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
