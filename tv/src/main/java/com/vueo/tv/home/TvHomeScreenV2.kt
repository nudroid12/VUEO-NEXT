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

private val HomeBlack = Color(0xFF050706)
private val HomePanel = Color(0xFF121714)
private val HomeMuted = Color(0xFFAAB2AD)
private val HomeLime = Color(0xFFB6FF00)
private val HomeDanger = Color(0xFFFF7B72)

private const val CONTINUE_ROW_ID = "continue-watching-v2"

/**
 * Clean TV Home implementation introduced by the 25B V2 rebuild.
 *
 * This screen intentionally does not share the previous Home layout. It only
 * consumes the existing Home repository, Library store and navigation contracts.
 */
@Composable
fun TvHomeScreenV2(
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

    BackHandler(enabled = !exitVisible) { exitVisible = true }
    BackHandler(enabled = exitVisible) { exitVisible = false }

    LaunchedEffect(refreshNonce, focusRestoreToken) {
        val current = home
        if (refreshNonce == 0 && !repository.shouldRefresh(current)) {
            loading = false
            return@LaunchedEffect
        }

        loading = current == null
        runCatching { repository.refresh() }
            .onSuccess { fresh ->
                home = fresh
                selectedHero = restoreHero(fresh, continueWatching) ?: fresh.hero
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
                .background(HomeBlack),
    ) {
        val currentHome = home
        val currentHero = selectedHero

        when {
            currentHome != null && currentHero != null ->
                HomeWorkspace(
                    home = currentHome,
                    hero = currentHero,
                    continueWatching = continueWatching,
                    profileStore = profileStore,
                    focusRestoreToken = focusRestoreToken,
                    refreshError = refreshError,
                    onNavigate = onNavigate,
                    onHeroChanged = { selectedHero = it },
                    onOpenMedia = onOpenMedia,
                    onPlayMedia = onPlayMedia,
                    onResumeEntry = onResumeEntry,
                )

            loading -> HomeLoading()

            else ->
                HomeError(
                    message = refreshError ?: "Unable to load VUEO catalogs",
                    onRetry = { refreshNonce += 1 },
                )
        }

        if (exitVisible) {
            ExitOverlay(
                onStay = { exitVisible = false },
                onExit = onExitApp,
            )
        }
    }
}

private fun restoreHero(
    home: TvHomeData,
    continueWatching: List<LibraryPlaybackEntry>,
): TvMediaItem? {
    if (TvFocusMemory.lastZone != TvFocusZone.Rail) return null
    val key = TvFocusMemory.lastMediaKey ?: return null

    return continueWatching.firstOrNull {
        "${it.media.type}:${it.media.id}" == key
    }?.media ?: home.rows
        .asSequence()
        .flatMap { it.items.asSequence() }
        .firstOrNull { "${it.type}:${it.id}" == key }
}

@Composable
private fun HomeWorkspace(
    home: TvHomeData,
    hero: TvMediaItem,
    continueWatching: List<LibraryPlaybackEntry>,
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
    val heroHeight = (configuration.screenHeightDp * 0.55f).dp.coerceIn(340.dp, 500.dp)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val navLabels = remember { listOf("Home", "Movie", "Series", "Anime", "Search", "Library") }
    val navRequesters = remember { navLabels.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val infoRequester = remember { FocusRequester() }

    val rowIds =
        remember(home.rows, continueWatching) {
            buildList {
                if (continueWatching.isNotEmpty()) add(CONTINUE_ROW_ID)
                addAll(home.rows.map { it.id })
            }
        }
    val rowKey = remember(rowIds) { rowIds.joinToString("|") }
    val rowRequesters = remember(rowKey) { rowIds.associateWith { FocusRequester() } }
    val firstRowRequester = rowIds.firstOrNull()?.let(rowRequesters::get)
    val contentStartIndex = 1 + if (refreshError != null) 1 else 0
    var preferredColumn by remember { mutableIntStateOf(TvFocusMemory.lastRailColumn) }

    val heroResume =
        remember(hero.type, hero.id, continueWatching) {
            continueWatching.firstOrNull {
                it.media.type.equals(hero.type, ignoreCase = true) && it.media.id == hero.id
            }
        }

    LaunchedEffect(rowKey, focusRestoreToken) {
        delay(120)
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
                val rememberedRow = TvFocusMemory.lastRowId
                val rowIndex = rowIds.indexOf(rememberedRow)
                if (rowIndex >= 0) {
                    listState.scrollToItem(max(0, contentStartIndex + rowIndex - 1))
                    delay(70)
                    runCatching { rowRequesters[rememberedRow]?.requestFocus() }
                } else {
                    runCatching { playRequester.requestFocus() }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CinematicBackdrop(hero)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 42.dp),
        ) {
            item(key = "hero") {
                HeroWorkspace(
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

            refreshError?.let { message ->
                item(key = "refresh-warning") {
                    Text(
                        text = message,
                        color = HomeMuted.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 54.dp, vertical = 2.dp),
                    )
                }
            }

            if (continueWatching.isNotEmpty()) {
                val rowPosition = rowIds.indexOf(CONTINUE_ROW_ID)
                item(key = CONTINUE_ROW_ID) {
                    ContinueWatchingRail(
                        entries = continueWatching,
                        entryRequester = rowRequesters.getValue(CONTINUE_ROW_ID),
                        preferredIndex = preferredColumn,
                        upRequester = playRequester,
                        downRequester = rowIds.getOrNull(rowPosition + 1)?.let(rowRequesters::get),
                        onFocused = { entry, index ->
                            preferredColumn = index
                            onHeroChanged(entry.media)
                            TvFocusMemory.rememberRail(
                                rowId = CONTINUE_ROW_ID,
                                itemIndex = index,
                                mediaKey = "${entry.media.type}:${entry.media.id}",
                            )
                            scope.launch {
                                listState.animateScrollToItem(max(0, contentStartIndex - 1))
                            }
                        },
                        onResume = onResumeEntry,
                    )
                }
            }

            home.rows.forEach { row ->
                val rowPosition = rowIds.indexOf(row.id)
                item(key = row.id) {
                    PosterRail(
                        row = row,
                        entryRequester = rowRequesters.getValue(row.id),
                        preferredIndex = preferredColumn,
                        upRequester =
                            if (rowPosition == 0) playRequester
                            else rowIds.getOrNull(rowPosition - 1)?.let(rowRequesters::get),
                        downRequester = rowIds.getOrNull(rowPosition + 1)?.let(rowRequesters::get),
                        onFocused = { item, index ->
                            preferredColumn = index
                            onHeroChanged(item)
                            TvFocusMemory.rememberRail(
                                rowId = row.id,
                                itemIndex = index,
                                mediaKey = "${item.type}:${item.id}",
                            )
                            scope.launch {
                                val target = max(0, contentStartIndex + rowPosition - 1)
                                listState.animateScrollToItem(target)
                            }
                        },
                        onOpen = onOpenMedia,
                    )
                }
            }
        }

        HomeTopNav(
            modifier = Modifier.align(Alignment.TopCenter),
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            contentDownRequester = playRequester,
            profileStore = profileStore,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun CinematicBackdrop(item: TvMediaItem) {
    AnimatedContent(
        targetState = item.background ?: item.poster,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            tvPlayerFadeThrough(
                enterDurationMillis = 360,
                exitDurationMillis = 170,
                enterDelayMillis = 20,
            )
        },
        label = "homeV2Backdrop",
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
                            HomeBlack,
                            HomeBlack.copy(alpha = 0.95f),
                            HomeBlack.copy(alpha = 0.60f),
                            HomeBlack.copy(alpha = 0.10f),
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
                            HomeBlack.copy(alpha = 0.32f),
                            Color.Transparent,
                            HomeBlack.copy(alpha = 0.42f),
                            HomeBlack.copy(alpha = 0.92f),
                            HomeBlack,
                        )
                    )
                ),
    )
}

@Composable
private fun HomeTopNav(
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
                .height(78.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HomeBlack.copy(alpha = 0.92f),
                            HomeBlack.copy(alpha = 0.55f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockup()
        Spacer(Modifier.width(42.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Home", "Movie", "Series", "Anime", "Search", "Library").forEach { label ->
                HomeNavItem(
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
        ProfileNavAvatar(
            requester = profileRequester,
            downRequester = contentDownRequester,
            profileStore = profileStore,
            onClick = { onNavigate("Profile") },
        )
    }
}

@Composable
private fun BrandLockup() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(SharedR.drawable.vueo_logo_mark),
            contentDescription = "VUEO",
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "VUEO",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.4.sp,
        )
    }
}

@Composable
private fun HomeNavItem(
    label: String,
    displayLabel: String,
    selected: Boolean,
    requester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(
        targetValue = if (focused || selected) Color.White else HomeMuted,
        animationSpec = tvFocusColorSpec(),
        label = "homeV2NavColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2NavScale",
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
                .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = displayLabel,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier =
                Modifier
                    .width(if (selected || focused) 28.dp else 0.dp)
                    .height(2.dp)
                    .background(
                        if (selected || focused) HomeLime else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
        )
    }
}

@Composable
private fun ProfileNavAvatar(
    requester: FocusRequester,
    downRequester: FocusRequester,
    profileStore: ProfileStore,
    onClick: () -> Unit,
) {
    val profile = profileStore.activeProfile()
    val drawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2ProfileScale",
    )
    val borderColor by animateColorAsState(
        if (focused) HomeLime else Color.White.copy(alpha = 0.28f),
        animationSpec = tvFocusColorSpec(),
        label = "homeV2ProfileBorder",
    )

    Box(
        modifier =
            Modifier
                .size(42.dp)
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
                .background(HomePanel),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroWorkspace(
    item: TvMediaItem,
    providerName: String,
    resumeEntry: LibraryPlaybackEntry?,
    height: androidx.compose.ui.unit.Dp,
    playRequester: FocusRequester,
    infoRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(start = 54.dp, top = 104.dp, end = 54.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.46f)) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = heroMeta(item),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(13.dp))
            Text(
                text =
                    item.description
                        ?: item.genres.take(3).joinToString(" • ").ifBlank { "Available from $providerName" },
                color = HomeMuted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    text = if (resumeEntry != null) "▶  Resume" else "▶  Play",
                    primary = true,
                    requester = playRequester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    actionIndex = 0,
                    onClick = onPlay,
                )
                HeroButton(
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

private fun heroMeta(item: TvMediaItem): String =
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
private fun HeroButton(
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
        if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2HeroButtonScale",
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
                    color = if (focused) HomeLime else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        shape = RoundedCornerShape(10.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (primary) Color.White
                    else Color(0xFF273039).copy(alpha = if (focused) 0.90f else 0.72f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContinueWatchingRail(
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

    Column(modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)) {
        RailHeader("Continue Watching")
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 54.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(entries, key = { _, item -> item.mediaKey }) { index, entry ->
                ContinueCard(
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
private fun ContinueCard(
    entry: LibraryPlaybackEntry,
    modifier: Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.045f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2ContinueScale",
    )
    val borderColor by animateColorAsState(
        if (focused) HomeLime else Color.White.copy(alpha = 0.12f),
        animationSpec = tvFocusColorSpec(),
        label = "homeV2ContinueBorder",
    )

    Column(
        modifier =
            modifier
                .width(272.dp)
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
                    .height(153.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp)),
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
                                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.82f))
                            )
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = entry.media.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = continueMeta(entry),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(entry.progressFraction.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(HomeLime),
                )
            }
        }
    }
}

private fun continueMeta(entry: LibraryPlaybackEntry): String =
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
private fun PosterRail(
    row: TvCatalogRow,
    entryRequester: FocusRequester,
    preferredIndex: Int,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: (TvMediaItem, Int) -> Unit,
    onOpen: (TvMediaItem) -> Unit,
) {
    if (row.items.isEmpty()) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusIndex = preferredIndex.coerceIn(0, row.items.lastIndex)

    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        RailHeader(row.title)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 54.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                PosterCard(
                    item = item,
                    modifier =
                        (if (index == focusIndex) Modifier.focusRequester(entryRequester) else Modifier)
                            .tvHorizontalEdgeGuard(
                                blockLeft = index == 0,
                                blockRight = index == row.items.lastIndex,
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
private fun RailHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 54.dp, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PosterCard(
    item: TvMediaItem,
    modifier: Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.045f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2PosterScale",
    )
    val borderColor by animateColorAsState(
        if (focused) HomeLime else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
        label = "homeV2PosterBorder",
    )

    Column(
        modifier =
            modifier
                .width(148.dp)
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
                    .height(222.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(HomePanel)
                    .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(9.dp)),
        ) {
            TvNetworkImage(
                url = item.poster ?: item.background,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(HomeBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = HomeLime, strokeWidth = 3.dp)
            Spacer(Modifier.height(18.dp))
            Text("Loading VUEO", color = HomeMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HomeError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(HomeBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Home unavailable", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = HomeMuted, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ExitOverlay(
    onStay: () -> Unit,
    onExit: () -> Unit,
) {
    val stayRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { stayRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xFF111613),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.width(480.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text("Exit VUEO?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("You can stay here or close the TV app.", color = HomeMuted, fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExitButton("Stay", stayRequester, primary = true, onClick = onStay)
                    ExitButton("Exit", remember { FocusRequester() }, primary = false, onClick = onExit)
                }
            }
        }
    }
}

@Composable
private fun ExitButton(
    label: String,
    requester: FocusRequester,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "homeV2ExitScale",
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
                    color = if (focused) HomeLime else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else HomeDanger.copy(alpha = 0.14f),
                contentColor = if (primary) Color.Black else HomeDanger,
            ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
