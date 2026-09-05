package com.vueo.tv.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.vueo.tv.TV_TOP_NAV_LABELS
import com.vueo.tv.TvTopNav
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvFocusColorSpec

private val SearchBlack = Color(0xFF050706)
private val SearchPanel = Color(0xFF101412)
private val SearchMuted = Color(0xFFAAB2AD)

private object TvSearchFocusMemory {
    var query: String = ""
    var mode: TvSearchMode = TvSearchMode.TITLE
    var type: TvSearchType = TvSearchType.ALL
    var sort: TvSearchSort = TvSearchSort.POPULAR
    var genre: String? = null
    var resultIndex: Int = 0
}

@Composable
fun TvSearchScreen(
    repository: TvSearchRepository,
    focusRestoreToken: Int = 0,
    onNavigate: (String) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS
                .associateWith { FocusRequester() }
        }
    val inputRequester = remember { FocusRequester() }
    val titleModeRequester = remember { FocusRequester() }
    val actorModeRequester = remember { FocusRequester() }
    val allTypeRequester = remember { FocusRequester() }
    val movieTypeRequester = remember { FocusRequester() }
    val seriesTypeRequester = remember { FocusRequester() }
    val animeTypeRequester = remember { FocusRequester() }
    val popularSortRequester = remember { FocusRequester() }
    val trendingSortRequester = remember { FocusRequester() }
    val newestSortRequester = remember { FocusRequester() }
    val genreRequester = remember { FocusRequester() }
    val resultEntryRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(TvSearchFocusMemory.query) }
    var mode by remember { mutableStateOf(TvSearchFocusMemory.mode) }
    var type by remember { mutableStateOf(TvSearchFocusMemory.type) }
    var sort by remember { mutableStateOf(TvSearchFocusMemory.sort) }
    var genre by remember { mutableStateOf(TvSearchFocusMemory.genre) }
    var results by remember { mutableStateOf<List<TvSearchResult>>(emptyList()) }
    var actorAvailable by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var requestGeneration by remember { mutableStateOf(0L) }
    var focusedIndex by remember { mutableStateOf(TvSearchFocusMemory.resultIndex) }

    val availableGenres = repository.availableGenres(results)
    val displayResults = repository.filterAndSort(
        items = results,
        type = type,
        genre = genre,
        sort = sort,
        query = query,
        actorMode = mode == TvSearchMode.ACTOR,
    )

    BackHandler {
        onNavigate("Home")
    }

    LaunchedEffect(Unit) {
        actorAvailable = runCatching { repository.actorSearchAvailable() }.getOrDefault(false)
    }

    LaunchedEffect(focusRestoreToken) {
        if (focusRestoreToken == 0) {
            delay(120)
            runCatching { inputRequester.requestFocus() }
        }
    }

    LaunchedEffect(focusRestoreToken, displayResults.size) {
        if (focusRestoreToken > 0 && displayResults.isNotEmpty()) {
            delay(100)
            focusedIndex = TvSearchFocusMemory.resultIndex.coerceIn(0, displayResults.lastIndex)
            gridState.scrollToItem((focusedIndex - 5).coerceAtLeast(0))
            runCatching { resultEntryRequester.requestFocus() }
        }
    }

    LaunchedEffect(query, mode, type) {
        TvSearchFocusMemory.query = query
        TvSearchFocusMemory.mode = mode
        TvSearchFocusMemory.type = type
        TvSearchFocusMemory.sort = sort
        TvSearchFocusMemory.genre = genre
        requestGeneration += 1L
        val generation = requestGeneration
        searchError = null

        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(250)
        if (generation != requestGeneration) return@LaunchedEffect

        searching = true
        if (mode == TvSearchMode.ACTOR) {
            results = emptyList()
            runCatching { repository.searchActorRemote(query, type) }
                .onSuccess { actorResults ->
                    if (generation == requestGeneration) {
                        results = actorResults
                        searchError = null
                    }
                }
                .onFailure { failure ->
                    if (generation == requestGeneration) {
                        searchError = failure.message ?: "Unable to search actor filmography"
                    }
                }
        } else {
            val local = repository.searchLocal(query, type)
            results = local
            runCatching { repository.searchRemote(query, type) }
                .onSuccess { remote ->
                    if (generation == requestGeneration) {
                        results = repository.merge(query, type, local, remote)
                        searchError = null
                    }
                }
                .onFailure { failure ->
                    if (generation == requestGeneration) {
                        searchError =
                            if (local.isEmpty()) {
                                failure.message ?: "Unable to search VUEO catalogs"
                            } else {
                                "Showing cached results"
                            }
                    }
                }
        }
        if (generation == requestGeneration) searching = false
    }

    LaunchedEffect(sort, genre) {
        TvSearchFocusMemory.sort = sort
        TvSearchFocusMemory.genre = genre
    }

    LaunchedEffect(results, availableGenres) {
        if (genre != null && availableGenres.none { it.equals(genre, ignoreCase = true) }) {
            genre = null
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF090D0A),
                            SearchBlack,
                            SearchBlack,
                        )
                    )
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp, end = 58.dp, top = 94.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Search",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Find titles or actor filmographies across Shared Core discovery sources.",
                        color = SearchMuted,
                        fontSize = 15.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvSearchChip(
                        label = "Title",
                        selected = mode == TvSearchMode.TITLE,
                        requester = titleModeRequester,
                        upRequester = inputRequester,
                        downRequester = allTypeRequester,
                        onClick = { mode = TvSearchMode.TITLE },
                    )
                    TvSearchChip(
                        label = if (actorAvailable) "Actor" else "Actor*",
                        selected = mode == TvSearchMode.ACTOR,
                        requester = actorModeRequester,
                        upRequester = inputRequester,
                        downRequester = allTypeRequester,
                        onClick = { mode = TvSearchMode.ACTOR },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .width(820.dp)
                        .focusRequester(inputRequester)
                        .tvVerticalFocus(
                            up = navRequesters.getValue("Search"),
                            down = titleModeRequester,
                        ),
                singleLine = true,
                placeholder = {
                    Text(
                        if (mode == TvSearchMode.ACTOR) {
                            "Search actor name..."
                        } else {
                            "Search movies, shows..."
                        }
                    )
                },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(22.dp).height(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                        focusedContainerColor = SearchPanel.copy(alpha = 0.98f),
                        unfocusedContainerColor = SearchPanel.copy(alpha = 0.84f),
                        focusedPlaceholderColor = SearchMuted,
                        unfocusedPlaceholderColor = SearchMuted.copy(alpha = 0.72f),
                    ),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSearchChip("All", type == TvSearchType.ALL, allTypeRequester, titleModeRequester, popularSortRequester) { type = TvSearchType.ALL }
                TvSearchChip("Movies", type == TvSearchType.MOVIE, movieTypeRequester, titleModeRequester, popularSortRequester) { type = TvSearchType.MOVIE }
                TvSearchChip("Series", type == TvSearchType.SERIES, seriesTypeRequester, titleModeRequester, popularSortRequester) { type = TvSearchType.SERIES }
                TvSearchChip("Anime", type == TvSearchType.ANIME, animeTypeRequester, titleModeRequester, popularSortRequester) { type = TvSearchType.ANIME }
                if (searchError != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(searchError.orEmpty(), color = SearchMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSearchChip("Popular", sort == TvSearchSort.POPULAR, popularSortRequester, allTypeRequester, if (displayResults.isNotEmpty()) resultEntryRequester else null) { sort = TvSearchSort.POPULAR }
                TvSearchChip("Trending", sort == TvSearchSort.TRENDING, trendingSortRequester, allTypeRequester, if (displayResults.isNotEmpty()) resultEntryRequester else null) { sort = TvSearchSort.TRENDING }
                TvSearchChip("Newest", sort == TvSearchSort.NEWEST, newestSortRequester, allTypeRequester, if (displayResults.isNotEmpty()) resultEntryRequester else null) { sort = TvSearchSort.NEWEST }
                TvSearchChip(
                    label = "Genre: ${genre ?: "All"}",
                    selected = genre != null,
                    requester = genreRequester,
                    upRequester = allTypeRequester,
                    downRequester = if (displayResults.isNotEmpty()) resultEntryRequester else null,
                    onClick = {
                        val options = listOf<String?>(null) + availableGenres
                        val current = options.indexOfFirst { it?.equals(genre, ignoreCase = true) ?: (genre == null) }.coerceAtLeast(0)
                        genre = options[(current + 1) % options.size]
                    },
                )
            }
        }

        when {
            mode == TvSearchMode.ACTOR && !actorAvailable && query.isNotBlank() -> {
                SearchMessage(
                    title = "Actor source unavailable",
                    body = "Enable an actor-capable addon or configure a TMDB API key in Settings.",
                )
            }
            query.isBlank() -> {
                SearchMessage(
                    title = "Search VUEO",
                    body = if (mode == TvSearchMode.ACTOR) "Type an actor name using your TV keyboard." else "Type a movie, series or anime title using your TV keyboard.",
                )
            }
            displayResults.isEmpty() && searching -> {
                SearchMessage(
                    title = "Searching…",
                    body = "Checking enabled Content Manager catalogs.",
                )
            }
            displayResults.isEmpty() -> {
                SearchMessage(
                    title = "No results",
                    body = "Try another query, type, genre or sort filter.",
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 346.dp),
                    contentPadding =
                        PaddingValues(
                            start = 58.dp,
                            end = 58.dp,
                            top = 14.dp,
                            bottom = 50.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    itemsIndexed(
                        items = displayResults,
                        key = { _, result ->
                            "${result.media.type}:${result.media.id}:${result.providerName}"
                        },
                    ) { index, result ->
                        val entryModifier =
                            if (index == focusedIndex.coerceIn(0, displayResults.lastIndex)) {
                                Modifier.focusRequester(resultEntryRequester)
                            } else {
                                Modifier
                            }
                        TvSearchPosterCard(
                            result = result,
                            modifier = entryModifier,
                            upRequester = if (index < 5) popularSortRequester else null,
                            onFocused = {
                                focusedIndex = index
                                TvSearchFocusMemory.resultIndex = index
                                scope.launch {
                                    gridState.animateScrollToItem(
                                        index = (index - 5).coerceAtLeast(0),
                                    )
                                }
                            },
                            onClick = { onOpenMedia(result.media) },
                        )
                    }
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = inputRequester,
            selectedLabel = "Search",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun SearchMessage(
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 58.dp, top = 340.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = body,
            color = SearchMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun TvSearchChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(), label = "searchChipScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "searchChipBorder",
    )
    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .onFocusChanged { focused = it.isFocused }
                .scale(scale)
                .background(
                    color =
                        when {
                            selected -> Color.White
                            focused -> Color.White.copy(alpha = 0.17f)
                            else -> Color.White.copy(alpha = 0.07f)
                        },
                    shape = RoundedCornerShape(11.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(11.dp),
                )
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TvSearchPosterCard(
    result: TvSearchResult,
    modifier: Modifier,
    upRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f,
        animationSpec = tvFocusSpec(), label = "searchPosterScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.White.copy(alpha = 0.10f),
        animationSpec = tvFocusColorSpec(),
        label = "searchPosterBorder",
    )

    Column(
        modifier =
            modifier
                .width(176.dp)
                .scale(scale)
                .then(if (upRequester != null) Modifier.tvVerticalFocus(up = upRequester) else Modifier)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) onFocused()
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(254.dp)
                    .background(SearchPanel, RoundedCornerShape(12.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    ),
        ) {
            TvNetworkImage(
                url = result.media.poster,
                contentDescription = result.media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (focused) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.34f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = result.media.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${result.media.displayType}  •  ${result.providerName}",
            color = if (focused) Color.White.copy(alpha = 0.74f) else SearchMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
