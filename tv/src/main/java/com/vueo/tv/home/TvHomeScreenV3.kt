package com.vueo.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.R as SharedR
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.data.TvCatalogRow
import com.vueo.tv.data.TvHomeData
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.TvFocusMemory
import com.vueo.tv.ui.focus.TvFocusZone
import com.vueo.tv.ui.focus.tvHorizontalEdgeGuard
import com.vueo.tv.ui.focus.tvVerticalFocus
import com.vueo.tv.ui.motion.tvFocusColorSpec
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvPlayerFadeThrough
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private val V3Black = Color(0xFF050706)
private val V3Panel = Color(0xFF111612)
private val V3Muted = Color(0xFFA8B0AA)
private val V3Lime = Color(0xFFB6FF00)
private val V3Danger = Color(0xFFFF7B72)

private val NavHeight = 76.dp
private val HorizontalSafe = 48.dp

private const val CONTINUE_ROW_ID = "continue-watching-v3"
private const val MY_LIST_ROW_ID = "my-list-v3"

/**
 * TV Home V3.
 *
 * The screen is intentionally rebuilt around four rules:
 * Predictable placement, minimal clutter, fluid performance and D-pad first.
 * The fixed top navigation owns its own viewport; Home content never renders
 * behind it. Continue Watching and My List always keep the first two row slots.
 */
@Composable
fun TvHomeScreenV3(
    focusRestoreToken: Int,
    onNavigate: (String) -> Unit,
    libraryStore: TvLibraryStore,
    profileStore: ProfileStore,
    onOpenMedia: (TvMediaItem) -> Unit,
    onPlayMedia: (TvMediaItem) -> Unit,
    onResumeEntry: (LibraryPlaybackEntry) -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { TvHomeRepository(context.applicationContext) }

    var home by remember { mutableStateOf(repository.cached()) }
    var selectedHero by remember { mutableStateOf(home?.hero) }
    var loading by remember { mutableStateOf(home == null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var exitVisible by remember { mutableStateOf(false) }

    val continueWatching = libraryStore.continueWatching()
    val myList = libraryStore.items()

    BackHandler(enabled = !exitVisible) { exitVisible = true }
    BackHandler(enabled = exitVisible) { exitVisible = false }

    LaunchedEffect(refreshNonce, focusRestoreToken) {
        val current = home
        if (refreshNonce == 0 && !repository.shouldRefresh(current)) {
            selectedHero = restoreHeroV3(current, continueWatching, myList) ?: current?.hero
            loading = false
            return@LaunchedEffect
        }

        loading = current == null
        runCatching { repository.refresh() }
            .onSuccess { fresh ->
                home = fresh
                selectedHero = restoreHeroV3(fresh, continueWatching, myList) ?: fresh.hero
                refreshError = null
            }
            .onFailure {
                refreshError =
                    if (home == null) "Unable to load VUEO catalogs"
                    else "Offline. Showing saved Home."
            }
        loading = false
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(V3Black),
    ) {
        val currentHome = home
        val currentHero = selectedHero

        when {
            currentHome != null && currentHero != null ->
                HomeViewportV3(
                    home = currentHome,
                    hero = currentHero,
                    continueWatching = continueWatching,
                    myList = myList,
                    profileStore = profileStore,
                    focusRestoreToken = focusRestoreToken,
                    refreshError = refreshError,
                    onNavigate = onNavigate,
                    onHeroChanged = { selectedHero = it },
                    onOpenMedia = onOpenMedia,
                    onPlayMedia = onPlayMedia,
                    onResumeEntry = onResumeEntry,
                )

            loading -> HomeLoadingV3()

            else ->
                HomeErrorV3(
                    message = refreshError ?: "Unable to load VUEO catalogs",
                    onRetry = { refreshNonce += 1 },
                )
        }

        if (exitVisible) {
            ExitOverlayV3(
                onStay = { exitVisible = false },
                onExit = onExitApp,
            )
        }
    }
}

private fun restoreHeroV3(
    home: TvHomeData?,
    continueWatching: List<LibraryPlaybackEntry>,
    myList: List<TvMediaItem>,
): TvMediaItem? {
    if (home == null || TvFocusMemory.lastZone != TvFocusZone.Rail) return null
    val key = TvFocusMemory.lastMediaKey ?: return null

    continueWatching.firstOrNull { mediaKey(it.media) == key }?.let { return it.media }
    myList.firstOrNull { mediaKey(it) == key }?.let { return it }

    return home.rows
        .asSequence()
        .flatMap { it.items.asSequence() }
        .firstOrNull { mediaKey(it) == key }
}

private fun mediaKey(item: TvMediaItem): String = "${item.type}:${item.id}"

private fun catalogRowId(row: TvCatalogRow): String = "catalog:${row.id}"

@Composable
private fun HomeViewportV3(
    home: TvHomeData,
    hero: TvMediaItem,
    continueWatching: List<LibraryPlaybackEntry>,
    myList: List<TvMediaItem>,
    profileStore: ProfileStore,
    focusRestoreToken: Int,
    refreshError: String?,
    onNavigate: (String) -> Unit,
    onHeroChanged: (TvMediaItem) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
    onPlayMedia: (TvMediaItem) -> Unit,
    onResumeEntry: (LibraryPlaybackEntry) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val requestedHeroHeight = configuration.screenHeightDp.dp * 0.42f
    val heroHeight = requestedHeroHeight.coerceIn(286.dp, 390.dp)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val navLabels = remember { listOf("Home", "Movie", "Series", "Anime", "Search", "Library") }
    val navRequesters = remember { navLabels.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val infoRequester = remember { FocusRequester() }

    val focusableRowIds =
        remember(home.rows, continueWatching, myList) {
            buildList {
                if (continueWatching.isNotEmpty()) add(CONTINUE_ROW_ID)
                if (myList.isNotEmpty()) add(MY_LIST_ROW_ID)
                home.rows.filter { it.items.isNotEmpty() }.forEach { add(catalogRowId(it)) }
            }
        }
    val focusableRowKey = remember(focusableRowIds) { focusableRowIds.joinToString("|") }
    val rowRequesters = remember(focusableRowKey) { focusableRowIds.associateWith { FocusRequester() } }
    val firstRowRequester = focusableRowIds.firstOrNull()?.let(rowRequesters::get)

    val visualIndexByRowId =
        remember(home.rows) {
            buildMap {
                put(CONTINUE_ROW_ID, 1)
                put(MY_LIST_ROW_ID, 2)
                home.rows.forEachIndexed { index, row -> put(catalogRowId(row), 3 + index) }
            }
        }

    val heroResume =
        remember(hero.type, hero.id, continueWatching) {
            continueWatching.firstOrNull { mediaKey(it.media) == mediaKey(hero) }
        }

    LaunchedEffect(focusableRowKey, focusRestoreToken) {
        delay(110)
        when (TvFocusMemory.lastZone) {
            TvFocusZone.Nav -> {
                val target =
                    if (TvFocusMemory.lastNavLabel == "Profile") profileRequester
                    else navRequesters[TvFocusMemory.lastNavLabel] ?: navRequesters.getValue("Home")
                runCatching { target.requestFocus() }
            }

            TvFocusZone.Hero -> {
                listState.scrollToItem(0)
                runCatching {
                    if (TvFocusMemory.lastHeroAction == 1) infoRequester.requestFocus()
                    else playRequester.requestFocus()
                }
            }

            TvFocusZone.Rail -> {
                val rowId = TvFocusMemory.lastRowId
                val target = rowId?.let(rowRequesters::get)
                val visualIndex = rowId?.let(visualIndexByRowId::get)
                if (target != null && visualIndex != null) {
                    listState.scrollToItem(max(0, visualIndex - 1))
                    delay(70)
                    runCatching { target.requestFocus() }
                } else {
                    listState.scrollToItem(0)
                    runCatching { playRequester.requestFocus() }
                }
            }
        }
    }

    fun focusNeighbors(rowId: String): Pair<FocusRequester?, FocusRequester?> {
        val index = focusableRowIds.indexOf(rowId)
        val up =
            if (index <= 0) playRequester
            else rowRequesters[focusableRowIds[index - 1]]
        val down =
            if (index < 0 || index >= focusableRowIds.lastIndex) null
            else rowRequesters[focusableRowIds[index + 1]]
        return up to down
    }

    fun revealRow(rowId: String) {
        val visualIndex = visualIndexByRowId[rowId] ?: return
        scope.launch {
            listState.animateScrollToItem(max(0, visualIndex - 1))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CinematicBackdropV3(hero)

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = NavHeight),
            contentPadding = PaddingValues(bottom = 52.dp),
        ) {
            item(key = "hero-v3") {
                HeroV3(
                    item = hero,
                    providerName = home.providerName,
                    resumeEntry = heroResume,
                    height = heroHeight,
                    playRequester = playRequester,
                    infoRequester = infoRequester,
                    upRequester = navRequesters.getValue("Home"),
                    downRequester = firstRowRequester,
                    onPlay = { heroResume?.let(onResumeEntry) ?: onPlayMedia(hero) },
                    onMoreInfo = { onOpenMedia(hero) },
                )
            }

            item(key = CONTINUE_ROW_ID) {
                val (up, down) = focusNeighbors(CONTINUE_ROW_ID)
                if (continueWatching.isEmpty()) {
                    EmptyPinnedRowV3(
                        title = "Continue Watching",
                        message = "Your unfinished movies and episodes will stay here.",
                    )
                } else {
                    ContinueWatchingRowV3(
                        entries = continueWatching,
                        entryRequester = rowRequesters.getValue(CONTINUE_ROW_ID),
                        preferredIndex = TvFocusMemory.railIndex(CONTINUE_ROW_ID, continueWatching.size),
                        upRequester = up,
                        downRequester = down,
                        onFocused = { entry, index ->
                            onHeroChanged(entry.media)
                            TvFocusMemory.rememberRail(
                                rowId = CONTINUE_ROW_ID,
                                itemIndex = index,
                                mediaKey = mediaKey(entry.media),
                            )
                            revealRow(CONTINUE_ROW_ID)
                        },
                        onResume = onResumeEntry,
                    )
                }
            }

            item(key = MY_LIST_ROW_ID) {
                val (up, down) = focusNeighbors(MY_LIST_ROW_ID)
                if (myList.isEmpty()) {
                    EmptyPinnedRowV3(
                        title = "My List",
                        message = "Save titles from Details and they will always appear here.",
                    )
                } else {
                    MediaPosterRowV3(
                        title = "My List",
                        items = myList,
                        rowId = MY_LIST_ROW_ID,
                        entryRequester = rowRequesters.getValue(MY_LIST_ROW_ID),
                        preferredIndex = TvFocusMemory.railIndex(MY_LIST_ROW_ID, myList.size),
                        upRequester = up,
                        downRequester = down,
                        onFocused = { item, index ->
                            onHeroChanged(item)
                            TvFocusMemory.rememberRail(
                                rowId = MY_LIST_ROW_ID,
                                itemIndex = index,
                                mediaKey = mediaKey(item),
                            )
                            revealRow(MY_LIST_ROW_ID)
                        },
                        onOpen = onOpenMedia,
                    )
                }
            }

            home.rows.forEach { row ->
                val rowId = catalogRowId(row)
                item(key = rowId) {
                    if (row.items.isNotEmpty()) {
                        val (up, down) = focusNeighbors(rowId)
                        MediaPosterRowV3(
                            title = row.title,
                            items = row.items,
                            rowId = rowId,
                            entryRequester = rowRequesters.getValue(rowId),
                            preferredIndex = TvFocusMemory.railIndex(rowId, row.items.size),
                            upRequester = up,
                            downRequester = down,
                            onFocused = { item, index ->
                                onHeroChanged(item)
                                TvFocusMemory.rememberRail(
                                    rowId = rowId,
                                    itemIndex = index,
                                    mediaKey = mediaKey(item),
                                )
                                revealRow(rowId)
                            },
                            onOpen = onOpenMedia,
                        )
                    }
                }
            }
        }

        HomeTopNavV3(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f),
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            contentDownRequester = playRequester,
            profileStore = profileStore,
            onNavigate = onNavigate,
        )

        refreshError?.let { message ->
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = NavHeight + 8.dp, end = HorizontalSafe),
            )
        }
    }
}

@Composable
private fun CinematicBackdropV3(item: TvMediaItem) {
    AnimatedContent(
        targetState = item.background ?: item.poster,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            tvPlayerFadeThrough(
                enterDurationMillis = 340,
                exitDurationMillis = 170,
                enterDelayMillis = 20,
            )
        },
        label = "homeV3Backdrop",
    ) { url ->
        TvNetworkImage(
            url = url,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            V3Black,
                            V3Black.copy(alpha = 0.97f),
                            V3Black.copy(alpha = 0.72f),
                            V3Black.copy(alpha = 0.24f),
                            Color.Transparent,
                        )
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
                            V3Black.copy(alpha = 0.24f),
                            Color.Transparent,
                            V3Black.copy(alpha = 0.38f),
                            V3Black.copy(alpha = 0.88f),
                            V3Black,
                        )
                    )
                ),
    )
}

@Composable
private fun HomeTopNavV3(
    modifier: Modifier,
    navRequesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    contentDownRequester: FocusRequester,
    profileStore: ProfileStore,
    onNavigate: (String) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(NavHeight)
                .background(V3Black.copy(alpha = 0.90f))
                .padding(horizontal = HorizontalSafe),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockupV3()
        Spacer(Modifier.width(38.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Home", "Movie", "Series", "Anime", "Search", "Library").forEach { label ->
                NavItemV3(
                    label = label,
                    displayLabel = if (label == "Movie") "Movies" else label,
                    selected = label == "Home",
                    requester = navRequesters.getValue(label),
                    downRequester = contentDownRequester,
                    onClick = { onNavigate(label) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        ProfileAvatarV3(
            requester = profileRequester,
            downRequester = contentDownRequester,
            profileStore = profileStore,
            onClick = { onNavigate("Profile") },
        )
    }
}

@Composable
private fun BrandLockupV3() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(SharedR.drawable.vueo_logo_mark),
            contentDescription = "VUEO",
            modifier = Modifier.size(29.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "VUEO",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 2.2.sp,
        )
    }
}

@Composable
private fun NavItemV3(
    label: String,
    displayLabel: String,
    selected: Boolean,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(
        targetValue = if (focused || selected) Color.White else V3Muted,
        animationSpec = tvFocusColorSpec(),
        label = "homeV3NavColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3NavScale",
    )

    Column(
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) TvFocusMemory.rememberNav(label)
                }
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = displayLabel,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .width(if (selected || focused) 24.dp else 0.dp)
                    .height(2.dp)
                    .background(
                        if (selected || focused) V3Lime else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
        )
    }
}

@Composable
private fun ProfileAvatarV3(
    requester: FocusRequester,
    downRequester: FocusRequester,
    profileStore: ProfileStore,
    onClick: () -> Unit,
) {
    val profile = profileStore.activeProfile()
    val drawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.045f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3ProfileScale",
    )
    val borderColor by animateColorAsState(
        if (focused) V3Lime else Color.White.copy(alpha = 0.24f),
        animationSpec = tvFocusColorSpec(),
        label = "homeV3ProfileBorder",
    )

    Box(
        modifier =
            Modifier
                .size(40.dp)
                .focusRequester(requester)
                .tvVerticalFocus(down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) TvFocusMemory.rememberNav("Profile")
                }
                .clip(CircleShape)
                .border(if (focused) 2.dp else 1.dp, borderColor, CircleShape)
                .clickable(onClick = onClick)
                .focusable()
                .background(V3Panel),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(drawable),
                contentDescription = profile.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = profile.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroV3(
    item: TvMediaItem,
    providerName: String,
    resumeEntry: LibraryPlaybackEntry?,
    height: Dp,
    playRequester: FocusRequester,
    infoRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = HorizontalSafe),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.46f),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = heroMetaV3(item),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text =
                    item.description
                        ?: item.genres.take(3).joinToString(" • ").ifBlank { "Available from $providerName" },
                color = V3Muted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButtonV3(
                    text = if (resumeEntry != null) "▶  Resume" else "▶  Play",
                    primary = true,
                    requester = playRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 0,
                    onClick = onPlay,
                )
                HeroButtonV3(
                    text = "More Info",
                    primary = false,
                    requester = infoRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 1,
                    onClick = onMoreInfo,
                )
            }
        }
    }
}

private fun heroMetaV3(item: TvMediaItem): String =
    buildList {
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        item.certification?.takeIf { it.isNotBlank() }?.let(::add)
        item.runtimeMinutes?.takeIf { it > 0 }?.let { runtime ->
            val hours = runtime / 60
            val minutes = runtime % 60
            add(if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m")
        }
        add(item.displayType)
        item.imdbRating?.let { add("IMDb ★ ${String.format("%.1f", it)}") }
    }.joinToString("  •  ")

@Composable
private fun HeroButtonV3(
    text: String,
    primary: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    actionIndex: Int,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.025f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3HeroButtonScale",
    )

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) TvFocusMemory.rememberHero(actionIndex)
                }
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) V3Lime else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ),
        shape = RoundedCornerShape(9.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (primary) Color.White
                    else Color(0xFF242C2A).copy(alpha = if (focused) 0.94f else 0.78f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyPinnedRowV3(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        RowHeaderV3(title)
        Box(
            modifier =
                Modifier
                    .padding(horizontal = HorizontalSafe, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(V3Panel.copy(alpha = 0.70f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = message,
                color = V3Muted.copy(alpha = 0.76f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContinueWatchingRowV3(
    entries: List<LibraryPlaybackEntry>,
    entryRequester: FocusRequester,
    preferredIndex: Int,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: (LibraryPlaybackEntry, Int) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusIndex = preferredIndex.coerceIn(0, entries.lastIndex)

    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        RowHeaderV3("Continue Watching")
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = HorizontalSafe, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { index, item -> "$CONTINUE_ROW_ID:$index:${item.mediaKey}" }) { index, entry ->
                ContinueCardV3(
                    entry = entry,
                    modifier =
                        (if (index == focusIndex) Modifier.focusRequester(entryRequester) else Modifier)
                            .tvHorizontalEdgeGuard(
                                blockLeft = index == 0,
                                blockRight = index == entries.lastIndex,
                            ),
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        onFocused(entry, index)
                        scope.launch { listState.animateScrollToItem(max(0, index - 1)) }
                    },
                    onClick = { onResume(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCardV3(
    entry: LibraryPlaybackEntry,
    modifier: Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3ContinueScale",
    )
    val borderColor by animateColorAsState(
        if (focused) V3Lime else Color.White.copy(alpha = 0.10f),
        animationSpec = tvFocusColorSpec(),
        label = "homeV3ContinueBorder",
    )

    Box(
        modifier =
            modifier
                .width(252.dp)
                .height(142.dp)
                .zIndex(if (focused) 2f else 0f)
                .scale(scale)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clip(RoundedCornerShape(9.dp))
                .background(V3Panel)
                .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(9.dp))
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        TvNetworkImage(
            url = entry.media.background ?: entry.media.poster,
            contentDescription = entry.media.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.84f))
                        )
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 11.dp, end = 11.dp, bottom = 11.dp),
        ) {
            Text(
                text = entry.media.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = continueMetaV3(entry),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.20f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(entry.progressFraction.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(V3Lime),
            )
        }
    }
}

private fun continueMetaV3(entry: LibraryPlaybackEntry): String =
    buildList {
        if (entry.season != null && entry.episode != null) {
            add("S${entry.season.toString().padStart(2, '0')} E${entry.episode.toString().padStart(2, '0')}")
        }
        if (entry.durationMs > entry.positionMs && entry.durationMs > 0L) {
            val minutes = ((entry.durationMs - entry.positionMs) / 60_000L).coerceAtLeast(1L)
            add("${minutes} min left")
        } else {
            add("${(entry.progressFraction * 100).toInt().coerceIn(1, 99)}% watched")
        }
    }.joinToString("  •  ")

@Composable
private fun MediaPosterRowV3(
    title: String,
    items: List<TvMediaItem>,
    rowId: String,
    entryRequester: FocusRequester,
    preferredIndex: Int,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: (TvMediaItem, Int) -> Unit,
    onOpen: (TvMediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusIndex = preferredIndex.coerceIn(0, items.lastIndex)

    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        RowHeaderV3(title)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = HorizontalSafe, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "$rowId:$index:${mediaKey(item)}" }) { index, item ->
                PosterCardV3(
                    item = item,
                    modifier =
                        (if (index == focusIndex) Modifier.focusRequester(entryRequester) else Modifier)
                            .tvHorizontalEdgeGuard(
                                blockLeft = index == 0,
                                blockRight = index == items.lastIndex,
                            ),
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = {
                        onFocused(item, index)
                        scope.launch { listState.animateScrollToItem(max(0, index - 1)) }
                    },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun RowHeaderV3(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = HorizontalSafe, vertical = 2.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PosterCardV3(
    item: TvMediaItem,
    modifier: Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3PosterScale",
    )
    val borderColor by animateColorAsState(
        if (focused) V3Lime else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "homeV3PosterBorder",
    )

    Column(
        modifier =
            modifier
                .width(132.dp)
                .zIndex(if (focused) 2f else 0f)
                .scale(scale)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(198.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(V3Panel)
                    .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
        ) {
            TvNetworkImage(
                url = item.poster ?: item.background,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeLoadingV3() {
    Box(
        modifier = Modifier.fillMaxSize().background(V3Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(SharedR.drawable.vueo_logo_mark),
                contentDescription = "VUEO",
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = V3Lime, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun HomeErrorV3(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(V3Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Home unavailable", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = V3Muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ExitOverlayV3(
    onStay: () -> Unit,
    onExit: () -> Unit,
) {
    val stayRequester = remember { FocusRequester() }
    val exitRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { stayRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xFF111613),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(460.dp),
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Text("Exit VUEO?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("You can stay here or close the TV app.", color = V3Muted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExitButtonV3("Stay", stayRequester, primary = true, onClick = onStay)
                    ExitButtonV3("Exit", exitRequester, primary = false, onClick = onExit)
                }
            }
        }
    }
}

@Composable
private fun ExitButtonV3(
    label: String,
    requester: FocusRequester,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV3ExitScale",
    )

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) V3Lime else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else V3Danger.copy(alpha = 0.14f),
                contentColor = if (primary) Color.Black else V3Danger,
            ),
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
