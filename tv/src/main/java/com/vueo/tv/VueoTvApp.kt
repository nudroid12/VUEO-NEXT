package com.vueo.tv

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.data.TvCatalogRow
import com.vueo.tv.data.TvHomeData
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.detail.TvDetailRepository
import com.vueo.tv.detail.TvDetailScreen
import com.vueo.tv.library.TvLibraryScreen
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackRequest
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.player.TvPlayerScreen
import com.vueo.tv.player.TvSourceEngine
import com.vueo.tv.player.TvSourcePickerScreen
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SubtitleCandidate
import com.vueo.tv.content.TvContentManagerScreen
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.search.TvSearchRepository
import com.vueo.tv.search.TvSearchScreen
import com.vueo.tv.ui.focus.TvFocusMemory
import com.vueo.tv.ui.focus.TvFocusZone
import com.vueo.tv.ui.focus.tvVerticalFocus
import com.vueo.tv.update.VueoTvUpdateManager
import com.vueo.tv.update.VueoTvUpdateRelease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val VueoBlack = Color(0xFF050706)
private val VueoPanel = Color(0xFF101412)
private val VueoGreen = Color(0xFF84E100)
private val VueoYellow = Color(0xFFD6FF00)
private val VueoMuted = Color(0xFFAAB2AD)
private const val TV_UPDATER_ENABLED = true

private enum class TvRootScreen {
    HOME,
    SEARCH,
    LIBRARY,
    CONTENT_MANAGER,
    DETAIL,
    SOURCE_PICKER,
    PLAYER,
}

@Composable
fun VueoTvApp() {
    val context = LocalContext.current
    var updateRelease by remember { mutableStateOf<VueoTvUpdateRelease?>(null) }
    var updateVisible by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var homeFocusRestoreToken by remember { mutableStateOf(0) }
    var currentScreen by remember { mutableStateOf(TvRootScreen.HOME) }
    var detailMedia by remember { mutableStateOf<TvMediaItem?>(null) }
    var playbackRequest by remember { mutableStateOf<TvPlaybackRequest?>(null) }
    var selectedSource by remember { mutableStateOf<SourceCandidate?>(null) }
    var selectedSubtitles by remember { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var detailReturnScreen by remember { mutableStateOf(TvRootScreen.HOME) }
    var searchFocusRestoreToken by remember { mutableStateOf(0) }
    var libraryFocusRestoreToken by remember { mutableStateOf(0) }
    val libraryStore =
        remember(context) {
            TvLibraryStore(context.applicationContext)
        }
    val contentManagerStore =
        remember(context) {
            TvContentManagerStore(context.applicationContext)
        }
    val sourceEngine =
        remember(context, contentManagerStore) {
            TvSourceEngine(context.applicationContext, contentManagerStore)
        }
    val playbackStore =
        remember(context) {
            TvPlaybackStore(context.applicationContext)
        }
    val searchRepository =
        remember(context) {
            TvSearchRepository(context.applicationContext)
        }
    val detailRepository = remember { TvDetailRepository() }

    val navigate: (String) -> Unit = { label ->
        val nextScreen =
            when (label) {
                "Home" -> TvRootScreen.HOME
                "Search" -> TvRootScreen.SEARCH
                "Library" -> TvRootScreen.LIBRARY
                "Content Manager" -> TvRootScreen.CONTENT_MANAGER
                else -> currentScreen
            }
        if (nextScreen != currentScreen) {
            detailMedia = null
            playbackRequest = null
            selectedSource = null
            selectedSubtitles = emptyList()
            currentScreen = nextScreen
        }
    }

    LaunchedEffect(Unit) {
        if (TV_UPDATER_ENABLED) {
            VueoTvUpdateManager.check(
                context = context.applicationContext,
                force = false,
            ) { result ->
                val release = result.release
                if (release != null && VueoTvUpdateManager.isNewerThanCurrent(context, release)) {
                    updateRelease = release
                    updateVisible = true
                    updateError = null
                }
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VueoBlack,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    TvRootScreen.HOME ->
                        VueoTvHome(
                            focusRestoreToken = homeFocusRestoreToken,
                            onNavigate = navigate,
                            libraryStore = libraryStore,
                            onOpenMedia = { media ->
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.HOME
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )

                    TvRootScreen.SEARCH ->
                        TvSearchScreen(
                            repository = searchRepository,
                            focusRestoreToken = searchFocusRestoreToken,
                            onNavigate = navigate,
                            onOpenMedia = { media ->
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.SEARCH
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )

                    TvRootScreen.LIBRARY ->
                        TvLibraryScreen(
                            store = libraryStore,
                            focusRestoreToken = libraryFocusRestoreToken,
                            onNavigate = navigate,
                            onOpenMedia = { media ->
                                detailMedia = media
                                detailReturnScreen = TvRootScreen.LIBRARY
                                currentScreen = TvRootScreen.DETAIL
                            },
                        )

                    TvRootScreen.CONTENT_MANAGER ->
                        TvContentManagerScreen(
                            store = contentManagerStore,
                            onNavigate = navigate,
                        )

                    TvRootScreen.DETAIL -> {
                        val media = detailMedia
                        if (media != null) {
                            TvDetailScreen(
                                seed = media,
                                repository = detailRepository,
                                onNavigate = navigate,
                                isInMyList = libraryStore.contains(media),
                                onToggleMyList = { libraryStore.toggle(media) },
                                onPlay = { request ->
                                    playbackRequest = request
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.SOURCE_PICKER
                                },
                                onBack = {
                                    val target = detailReturnScreen
                                    detailMedia = null
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = target
                                    when (target) {
                                        TvRootScreen.SEARCH -> searchFocusRestoreToken += 1
                                        TvRootScreen.LIBRARY -> libraryFocusRestoreToken += 1
                                        else -> homeFocusRestoreToken += 1
                                    }
                                },
                            )
                        }
                    }

                    TvRootScreen.SOURCE_PICKER -> {
                        val request = playbackRequest
                        if (request != null) {
                            TvSourcePickerScreen(
                                request = request,
                                sourceEngine = sourceEngine,
                                onPlay = { source, subtitles ->
                                    selectedSource = source
                                    selectedSubtitles = subtitles
                                    currentScreen = TvRootScreen.PLAYER
                                },
                                onBack = {
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.DETAIL
                                },
                            )
                        }
                    }

                    TvRootScreen.PLAYER -> {
                        val request = playbackRequest
                        val source = selectedSource
                        if (request != null && source != null) {
                            TvPlayerScreen(
                                request = request,
                                initialSource = source,
                                externalSubtitles = selectedSubtitles,
                                sourceEngine = sourceEngine,
                                playbackStore = playbackStore,
                                onPlayRequest = { next ->
                                    playbackRequest = next
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.SOURCE_PICKER
                                },
                                onBack = {
                                    playbackRequest = null
                                    selectedSource = null
                                    selectedSubtitles = emptyList()
                                    currentScreen = TvRootScreen.DETAIL
                                },
                            )
                        }
                    }
                }

                val release = updateRelease
                if (updateVisible && release != null) {
                    TvUpdateOverlay(
                        release = release,
                        downloading = updateDownloading,
                        progress = updateProgress,
                        error = updateError,
                        onLater = {
                            if (!updateDownloading) {
                                updateVisible = false
                                homeFocusRestoreToken += 1
                            }
                        },
                        onUpdateNow = {
                            if (VueoTvUpdateManager.needsInstallPermission(context)) {
                                updateError =
                                    "Allow VUEO TV to install unknown apps, then return and choose Update Now again."
                                runCatching {
                                    VueoTvUpdateManager.openInstallPermissionSettings(context)
                                }.onFailure {
                                    updateError = it.message ?: "Unable to open install permission settings."
                                }
                            } else if (!updateDownloading) {
                                updateDownloading = true
                                updateProgress = 0
                                updateError = null

                                VueoTvUpdateManager.downloadAndInstall(
                                    context = context.applicationContext,
                                    release = release,
                                    onProgress = { updateProgress = it },
                                ) { result ->
                                    updateDownloading = false
                                    result.onFailure { failure ->
                                        updateError =
                                            failure.message ?: "Unable to install the TV update."
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoTvHome(
    focusRestoreToken: Int,
    onNavigate: (String) -> Unit,
    libraryStore: TvLibraryStore,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val context = LocalContext.current
    val repository =
        remember(context) {
            TvHomeRepository(context.applicationContext)
        }
    val navRequesters =
        remember {
            listOf("Home", "Search", "Library", "Content Manager", "Luckez")
                .associateWith { FocusRequester() }
        }
    val heroPlayRequester = remember { FocusRequester() }
    val heroListRequester = remember { FocusRequester() }

    var home by remember {
        mutableStateOf(repository.cached())
    }
    var selectedHero by remember {
        mutableStateOf(home?.hero)
    }
    var loading by remember {
        mutableStateOf(home == null)
    }
    var refreshError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        runCatching {
            repository.refresh()
        }
            .onSuccess { fresh ->
                val rememberedMedia = TvFocusMemory.lastMediaKey
                val restoredHero =
                    rememberedMedia?.let { mediaKey ->
                        fresh.rows
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { "${it.type}:${it.id}" == mediaKey }
                    }

                home = fresh
                selectedHero = restoredHero ?: fresh.hero
                refreshError = null
            }
            .onFailure {
                refreshError =
                    if (home == null) {
                        "Unable to load VUEO catalogs"
                    } else {
                        "Showing cached catalog"
                    }
            }

        loading = false
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VueoBlack),
    ) {
        when {
            home != null && selectedHero != null -> {
                HomeContent(
                    home = home!!,
                    hero = selectedHero!!,
                    navRequesters = navRequesters,
                    heroPlayRequester = heroPlayRequester,
                    heroListRequester = heroListRequester,
                    focusRestoreToken = focusRestoreToken,
                    refreshError = refreshError,
                    onCardFocused = { selectedHero = it },
                    isInMyList = { libraryStore.contains(it) },
                    onToggleMyList = { libraryStore.toggle(it) },
                    onOpenMedia = onOpenMedia,
                )
            }

            loading -> LoadingHome()

            else -> ErrorHome(
                message = refreshError ?: "Unable to load VUEO catalogs",
            )
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = heroPlayRequester,
            selectedLabel = "Home",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun HomeContent(
    home: TvHomeData,
    hero: TvMediaItem,
    navRequesters: Map<String, FocusRequester>,
    heroPlayRequester: FocusRequester,
    heroListRequester: FocusRequester,
    focusRestoreToken: Int,
    refreshError: String?,
    onCardFocused: (TvMediaItem) -> Unit,
    isInMyList: (TvMediaItem) -> Boolean,
    onToggleMyList: (TvMediaItem) -> Boolean,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowKey = remember(home.rows) { home.rows.joinToString("|") { it.id } }
    val rowEntryRequesters =
        remember(rowKey) {
            home.rows.associate { it.id to FocusRequester() }
        }
    val firstRowRequester = home.rows.firstOrNull()?.let { rowEntryRequesters[it.id] }
    val homeNavRequester = navRequesters.getValue("Home")
    val railStartIndex = if (refreshError != null) 2 else 1
    var lastVerticalRow by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rowKey, focusRestoreToken) {
        delay(90)
        val target =
            when (TvFocusMemory.lastZone) {
                TvFocusZone.Nav ->
                    navRequesters[TvFocusMemory.lastNavLabel]
                        ?: homeNavRequester

                TvFocusZone.Hero -> {
                    columnState.scrollToItem(0)
                    if (TvFocusMemory.lastHeroAction == 1) {
                        heroListRequester
                    } else {
                        heroPlayRequester
                    }
                }

                TvFocusZone.Rail -> {
                    val rememberedRowId = TvFocusMemory.lastRowId
                    val rememberedRowIndex =
                        home.rows.indexOfFirst { it.id == rememberedRowId }
                    if (rememberedRowIndex >= 0) {
                        columnState.scrollToItem(railStartIndex + rememberedRowIndex)
                        delay(70)
                    }
                    rememberedRowId
                        ?.let(rowEntryRequesters::get)
                        ?: heroPlayRequester
                }
            }

        runCatching { target.requestFocus() }
    }

    LazyColumn(
        state = columnState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 76.dp),
        contentPadding = PaddingValues(bottom = 38.dp),
    ) {
        item {
            Hero(
                item = hero,
                playRequester = heroPlayRequester,
                listRequester = heroListRequester,
                upRequester = homeNavRequester,
                downRequester = firstRowRequester,
                providerName = home.providerName,
                inMyList = isInMyList(hero),
                onToggleMyList = { onToggleMyList(hero) },
                onFocused = {
                    if (lastVerticalRow != null) {
                        lastVerticalRow = null
                        scope.launch {
                            columnState.animateScrollToItem(0)
                        }
                    }
                },
            )
        }

        refreshError?.let { message ->
            item {
                Text(
                    text = message,
                    color = VueoMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 58.dp, vertical = 5.dp),
                )
            }
        }

        home.rows.forEachIndexed { rowIndex, row ->
            val upRequester =
                if (rowIndex == 0) {
                    heroPlayRequester
                } else {
                    rowEntryRequesters[home.rows[rowIndex - 1].id]
                }
            val downRequester =
                home.rows.getOrNull(rowIndex + 1)
                    ?.let { rowEntryRequesters[it.id] }
            val entryRequester = rowEntryRequesters.getValue(row.id)

            item(key = row.id) {
                TvRail(
                    row = row,
                    entryRequester = entryRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onCardFocused = { item, _ ->
                        onCardFocused(item)
                        if (lastVerticalRow != row.id) {
                            lastVerticalRow = row.id
                            scope.launch {
                                columnState.animateScrollToItem(
                                    index = railStartIndex + rowIndex,
                                )
                            }
                        }
                    },
                    onOpenMedia = onOpenMedia,
                )
            }
        }
    }
}

@Composable
internal fun TvTopNav(
    navRequesters: Map<String, FocusRequester>,
    contentDownRequester: FocusRequester,
    selectedLabel: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VueoBlack,
                            VueoBlack.copy(alpha = 0.94f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 42.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(44.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvNavItem(
                    label = "Home",
                    selected = selectedLabel == "Home",
                    requester = navRequesters.getValue("Home"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Home") },
                )
                TvNavItem(
                    label = "Search",
                    selected = selectedLabel == "Search",
                    requester = navRequesters.getValue("Search"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Search") },
                )
                TvNavItem(
                    label = "Library",
                    selected = selectedLabel == "Library",
                    requester = navRequesters.getValue("Library"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Library") },
                )
                TvNavItem(
                    label = "Content Manager",
                    selected = selectedLabel == "Content Manager",
                    requester = navRequesters.getValue("Content Manager"),
                    downRequester = contentDownRequester,
                    onClick = { onSelected("Content Manager") },
                )
            }
        }

        TvNavItem(
            label = "Luckez",
            selected = selectedLabel == "Luckez",
            requester = navRequesters.getValue("Luckez"),
            downRequester = contentDownRequester,
            onClick = { onSelected("Luckez") },
        )
    }
}

@Composable
private fun TvNavItem(
    label: String,
    requester: FocusRequester,
    downRequester: FocusRequester,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (focused || selected) Color.White else VueoMuted,
        label = "navColor",
    )
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        label = "navScale",
    )

    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        TvFocusMemory.rememberNav(label)
                    }
                }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    color = if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (focused) VueoYellow.copy(alpha = 0.62f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                )
                .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun Hero(
    item: TvMediaItem,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    providerName: String,
    inMyList: Boolean,
    onToggleMyList: () -> Boolean,
    onFocused: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(VueoBlack),
    ) {
        TvNetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    VueoBlack,
                                    VueoBlack.copy(alpha = 0.96f),
                                    VueoBlack.copy(alpha = 0.56f),
                                    Color.Transparent,
                                ),
                        )
                    ),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                VueoBlack,
                            )
                        )
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp, end = 40.dp)
                    .fillMaxWidth(0.50f),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = heroMeta(item),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text =
                    item.description
                        ?: item.genres.take(3).joinToString(" • ")
                        .ifBlank { "Available from $providerName" },
                color = VueoMuted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvHeroButton(
                    text = "▶  Play",
                    primary = true,
                    requester = playRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 0,
                    onFocused = onFocused,
                    onClick = { },
                )
                var saved by remember(item.type, item.id, inMyList) { mutableStateOf(inMyList) }
                TvHeroButton(
                    text = if (saved) "✓  My List" else "+  My List",
                    requester = listRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 1,
                    onFocused = onFocused,
                    onClick = { saved = onToggleMyList() },
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = "Source • $providerName",
                color = VueoMuted.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
        }
    }
}

private fun heroMeta(item: TvMediaItem): String =
    buildList {
        add(item.displayType)
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        item.imdbRating?.let {
            add("IMDb ★ ${String.format("%.1f", it)}")
        }
    }.joinToString("  •  ")

@Composable
private fun TvHeroButton(
    text: String,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    actionIndex: Int,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "heroButtonScale")

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(
                    up = upRequester,
                    down = downRequester,
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        TvFocusMemory.rememberHero(actionIndex)
                        onFocused()
                    }
                }
                .scale(scale)
                .border(
                    width = 1.dp,
                    color = if (focused) VueoYellow.copy(alpha = 0.70f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ),
        shape = RoundedCornerShape(9.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (primary) Color.White
                    else Color.White.copy(alpha = if (focused) 0.20f else 0.12f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 23.dp, vertical = 12.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvRail(
    row: TvCatalogRow,
    entryRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onCardFocused: (TvMediaItem, Int) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val rememberedIndex = TvFocusMemory.railIndex(row.id, row.items.size)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (rememberedIndex - 1).coerceAtLeast(0),
        )
    val scope = rememberCoroutineScope()
    var entryIndex by remember(row.id, row.items.size) {
        mutableStateOf(rememberedIndex)
    }

    Column(
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 58.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.providerName,
                color = VueoMuted.copy(alpha = 0.68f),
                fontSize = 11.sp,
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            itemsIndexed(
                items = row.items,
                key = { _, item -> "${row.id}:${item.type}:${item.id}" },
            ) { index, item ->
                val entryModifier =
                    if (index == entryIndex) {
                        Modifier.focusRequester(entryRequester)
                    } else {
                        Modifier
                    }

                TvPosterCard(
                    item = item,
                    modifier = entryModifier,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        entryIndex = index
                        TvFocusMemory.rememberRail(
                            rowId = row.id,
                            itemIndex = index,
                            mediaKey = "${item.type}:${item.id}",
                        )
                        onCardFocused(item, index)

                        scope.launch {
                            listState.animateScrollToItem(
                                index = (index - 1).coerceAtLeast(0),
                            )
                        }
                    },
                    onClick = { onOpenMedia(item) },
                )
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    item: TvMediaItem,
    modifier: Modifier = Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cardScale")
    val borderColor by animateColorAsState(
        if (focused) VueoYellow else Color.Transparent,
        label = "cardBorder",
    )
    val glowColor by animateColorAsState(
        if (focused) VueoGreen.copy(alpha = 0.18f) else Color.Transparent,
        label = "cardGlow",
    )

    Column(
        modifier =
            modifier
                .width(154.dp)
                .zIndex(if (focused) 1f else 0f)
                .scale(scale)
                .tvVerticalFocus(
                    up = upRequester,
                    down = downRequester,
                )
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        onFocused()
                    }
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(222.dp)
                    .background(
                        color = glowColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(3.dp),
        ) {
            TvNetworkImage(
                url = item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cardMeta(item),
            color = if (focused) Color.White.copy(alpha = 0.74f) else VueoMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun cardMeta(item: TvMediaItem): String =
    listOfNotNull(
        item.displayType,
        item.releaseInfo?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

@Composable
private fun TvUpdateOverlay(
    release: VueoTvUpdateRelease,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onLater: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    val updateButtonFocus = remember { FocusRequester() }

    LaunchedEffect(release.versionCode) {
        runCatching { updateButtonFocus.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(610.dp)
                    .background(
                        color = VueoPanel,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 34.dp, vertical = 28.dp),
        ) {
            Text(
                text = "VUEO TV update available",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = release.versionName,
                color = VueoYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (release.changelog.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                release.changelog.take(3).forEach { item ->
                    Text(
                        text = "•  $item",
                        color = VueoMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            if (downloading) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Downloading update… ${progress.coerceIn(0, 100)}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(99.dp),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    (progress.coerceIn(0, 100) / 100f)
                                        .coerceAtLeast(0.01f)
                                )
                                .fillMaxHeight()
                                .background(
                                    VueoYellow,
                                    RoundedCornerShape(99.dp),
                                ),
                    )
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = message,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !downloading,
                    modifier = Modifier.focusRequester(updateButtonFocus),
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Update Now",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onLater,
                    enabled = !downloading,
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingHome() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = VueoYellow,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Loading VUEO",
                color = VueoMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ErrorHome(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = VueoMuted,
                fontSize = 15.sp,
            )
        }
    }
}
