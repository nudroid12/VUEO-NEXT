package com.vueo.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.vueo.tv.data.TvHomeData
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.TvFocusMemory
import com.vueo.tv.ui.focus.TvFocusZone
import com.vueo.tv.ui.focus.tvHorizontalEdgeGuard
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.motion.tvFocusColorSpec
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvPlayerFadeThrough
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val ExperienceBlack = Color(0xFF050608)
private val ExperiencePanel = Color(0xFF111418)
private val ExperienceMuted = Color(0xFFB1B5BC)
private val ExperienceDanger = Color(0xFFFF827B)
private val ExperienceFocus = Color.White

private val ExperienceHorizontalSafe = 52.dp
private val ExperienceTopBarHeight = 68.dp
private val ExperienceCardWidth = 246.dp
private val ExperienceCardHeight = 138.dp

private const val HERO_SETTLE_MS = 180L
private const val CONTINUE_RAIL_ID = "experience:continue"
private const val MY_LIST_RAIL_ID = "experience:my-list"

private data class ExperienceEntry(
    val key: String,
    val media: TvMediaItem,
    val progress: Float? = null,
    val progressLabel: String? = null,
)

private data class ExperienceRail(
    val id: String,
    val title: String,
    val entries: List<ExperienceEntry>,
)

/**
 * VUEO TV 29A experience foundation.
 *
 * Locked principles:
 * - Premium, cinematic, fluid.
 * - Hero + peeking first content rail.
 * - Hero is presentation only; it never owns focus.
 * - Initial focus is Continue Watching when available, otherwise the first rail.
 * - Focus is immediate while hero artwork settles after a short debounce.
 * - UP from the first rail reveals contextual top navigation.
 * - DOWN from navigation restores the exact previous content card.
 * - OK on a card opens Details directly.
 */
@Composable
fun TvHomeExperienceFoundation(
    focusRestoreToken: Int,
    onNavigate: (String) -> Unit,
    libraryStore: TvLibraryStore,
    profileStore: ProfileStore,
    onOpenMedia: (TvMediaItem) -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { TvHomeRepository(context.applicationContext) }

    var home by remember { mutableStateOf(repository.cached()) }
    var loading by remember { mutableStateOf(home == null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var exitVisible by remember { mutableStateOf(false) }

    val continueWatching = libraryStore.continueWatching()
    val myList = libraryStore.items()

    BackHandler(enabled = !exitVisible) { exitVisible = true }
    BackHandler(enabled = exitVisible) { exitVisible = false }

    LaunchedEffect(refreshNonce, focusRestoreToken) {
        val cached = home
        if (refreshNonce == 0 && !repository.shouldRefresh(cached)) {
            loading = false
            return@LaunchedEffect
        }

        loading = cached == null
        runCatching { repository.refresh() }
            .onSuccess { fresh ->
                home = fresh
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
                .background(ExperienceBlack),
    ) {
        val currentHome = home
        when {
            currentHome != null ->
                ExperienceViewport(
                    home = currentHome,
                    continueWatching = continueWatching,
                    myList = myList,
                    profileStore = profileStore,
                    focusRestoreToken = focusRestoreToken,
                    refreshError = refreshError,
                    onNavigate = onNavigate,
                    onOpenMedia = onOpenMedia,
                )

            loading -> ExperienceLoading()
            else ->
                ExperienceError(
                    message = refreshError ?: "Unable to load VUEO catalogs",
                    onRetry = { refreshNonce += 1 },
                )
        }

        if (exitVisible) {
            ExperienceExitOverlay(
                onStay = { exitVisible = false },
                onExit = onExitApp,
            )
        }
    }
}

@Composable
private fun ExperienceViewport(
    home: TvHomeData,
    continueWatching: List<LibraryPlaybackEntry>,
    myList: List<TvMediaItem>,
    profileStore: ProfileStore,
    focusRestoreToken: Int,
    refreshError: String?,
    onNavigate: (String) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp.dp * 0.52f).coerceIn(330.dp, 420.dp)
    val outerState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val rails =
        remember(home.rows, continueWatching, myList) {
            buildList {
                if (continueWatching.isNotEmpty()) {
                    add(
                        ExperienceRail(
                            id = CONTINUE_RAIL_ID,
                            title = "Continue Watching",
                            entries =
                                continueWatching.mapIndexed { index, entry ->
                                    ExperienceEntry(
                                        key = "$CONTINUE_RAIL_ID:$index:${entry.mediaKey}",
                                        media = entry.media,
                                        progress = entry.progressFraction.coerceIn(0f, 1f),
                                        progressLabel = continueLabel(entry),
                                    )
                                },
                        )
                    )
                }

                if (myList.isNotEmpty()) {
                    add(
                        ExperienceRail(
                            id = MY_LIST_RAIL_ID,
                            title = "My List",
                            entries =
                                myList.mapIndexed { index, media ->
                                    ExperienceEntry(
                                        key = "$MY_LIST_RAIL_ID:$index:${mediaKey(media)}",
                                        media = media,
                                    )
                                },
                        )
                    )
                }

                home.rows
                    .filter { it.items.isNotEmpty() }
                    .forEach { row ->
                        add(
                            ExperienceRail(
                                id = "experience:catalog:${row.id}",
                                title = row.title,
                                entries =
                                    row.items.mapIndexed { index, media ->
                                        ExperienceEntry(
                                            key = "experience:catalog:${row.id}:$index:${mediaKey(media)}",
                                            media = media,
                                        )
                                    },
                            )
                        )
                    }
            }
        }

    val railSignature =
        remember(rails) {
            rails.joinToString("|") { rail ->
                "${rail.id}:${rail.entries.joinToString(",") { it.key }}"
            }
        }
    val allEntries = remember(rails) { rails.flatMap { it.entries } }
    val requesters =
        remember(railSignature) {
            allEntries.associate { it.key to FocusRequester() }
        }

    val navLabels = remember { listOf("Home", "Search", "Library", "Settings") }
    val navRequesters = remember { navLabels.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    val restoredKey =
        remember(railSignature, focusRestoreToken) {
            restoreEntryKey(rails) ?: rails.firstOrNull()?.entries?.firstOrNull()?.key
        }
    var lastContentKey by remember(railSignature, focusRestoreToken) { mutableStateOf(restoredKey) }
    val initialHero =
        remember(railSignature, home.hero, restoredKey) {
            allEntries.firstOrNull { it.key == restoredKey }?.media ?: rails.firstOrNull()?.entries?.firstOrNull()?.media ?: home.hero
        }
    var pendingHero by remember(railSignature) { mutableStateOf(initialHero) }
    var hero by remember(railSignature) { mutableStateOf(initialHero) }
    var navExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(mediaKey(pendingHero)) {
        delay(HERO_SETTLE_MS)
        hero = pendingHero
    }

    LaunchedEffect(railSignature, focusRestoreToken) {
        if (rails.isEmpty()) return@LaunchedEffect
        delay(120)

        val targetKey = restoreEntryKey(rails) ?: rails.first().entries.first().key
        val (railIndex, _) = locateEntry(rails, targetKey) ?: (0 to 0)
        lastContentKey = targetKey
        if (railIndex == 0) {
            outerState.scrollToItem(0)
        } else {
            outerState.scrollToItem(railIndex)
        }
        delay(60)
        runCatching { requesters[targetKey]?.requestFocus() }
    }

    fun requestLastContentFocus(): Boolean {
        val fallback = rails.firstOrNull()?.entries?.firstOrNull()?.key
        val target = lastContentKey?.takeIf(requesters::containsKey) ?: fallback ?: return false
        return runCatching {
            requesters.getValue(target).requestFocus()
            true
        }.getOrDefault(false)
    }

    fun onEntryFocused(
        railIndex: Int,
        itemIndex: Int,
        entry: ExperienceEntry,
    ) {
        lastContentKey = entry.key
        pendingHero = entry.media
        navExpanded = false
        TvFocusMemory.rememberRail(
            rowId = rails[railIndex].id,
            itemIndex = itemIndex,
            mediaKey = mediaKey(entry.media),
        )
        scope.launch {
            if (railIndex == 0) {
                if (outerState.firstVisibleItemIndex > 0) outerState.animateScrollToItem(0)
            } else {
                outerState.animateScrollToItem(railIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ExperienceBackdrop(hero)

        LazyColumn(
            state = outerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 54.dp),
        ) {
            item(key = "experience-hero") {
                ExperienceHero(
                    item = hero,
                    height = heroHeight,
                    topInset = ExperienceTopBarHeight,
                )
            }

            rails.forEachIndexed { railIndex, rail ->
                item(key = rail.id) {
                    ExperienceRailRow(
                        rail = rail,
                        railIndex = railIndex,
                        rails = rails,
                        requesters = requesters,
                        navHomeRequester = navRequesters.getValue("Home"),
                        onFocused = { itemIndex, entry -> onEntryFocused(railIndex, itemIndex, entry) },
                        onOpen = { entry -> onOpenMedia(entry.media) },
                    )
                }
            }
        }

        ExperienceTopNav(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f),
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            profileStore = profileStore,
            onNavFocus = { label ->
                navExpanded = true
                TvFocusMemory.rememberNav(label)
            },
            onProfileFocus = {
                navExpanded = true
                TvFocusMemory.rememberNav("Profile")
            },
            onDownToContent = { requestLastContentFocus() },
            onNavigate = onNavigate,
        )

        refreshError?.let { message ->
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = ExperienceTopBarHeight + 8.dp, end = ExperienceHorizontalSafe),
            )
        }
    }
}

private fun restoreEntryKey(rails: List<ExperienceRail>): String? {
    if (TvFocusMemory.lastZone != TvFocusZone.Rail) return null
    val rowId = TvFocusMemory.lastRowId ?: return null
    val media = TvFocusMemory.lastMediaKey ?: return null
    val row = rails.firstOrNull { it.id == rowId } ?: return null
    return row.entries.firstOrNull { mediaKey(it.media) == media }?.key
}

private fun locateEntry(
    rails: List<ExperienceRail>,
    key: String,
): Pair<Int, Int>? {
    rails.forEachIndexed { railIndex, rail ->
        val itemIndex = rail.entries.indexOfFirst { it.key == key }
        if (itemIndex >= 0) return railIndex to itemIndex
    }
    return null
}

private fun mediaKey(item: TvMediaItem): String = "${item.type}:${item.id}"

@Composable
private fun ExperienceBackdrop(item: TvMediaItem) {
    AnimatedContent(
        targetState = item.background ?: item.poster,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            tvPlayerFadeThrough(
                enterDurationMillis = 360,
                exitDurationMillis = 180,
                enterDelayMillis = 18,
            )
        },
        label = "experienceBackdrop",
    ) { url ->
        TvNetworkImage(
            url = url,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ExperienceBlack,
                            ExperienceBlack.copy(alpha = 0.97f),
                            ExperienceBlack.copy(alpha = 0.80f),
                            ExperienceBlack.copy(alpha = 0.34f),
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
                            ExperienceBlack.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Transparent,
                            ExperienceBlack.copy(alpha = 0.64f),
                            ExperienceBlack.copy(alpha = 0.96f),
                        )
                    )
                ),
    )
}

@Composable
private fun ExperienceHero(
    item: TvMediaItem,
    height: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(
                    start = ExperienceHorizontalSafe,
                    end = ExperienceHorizontalSafe,
                    top = topInset,
                    bottom = 26.dp,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.43f),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = heroMeta(item),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = item.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = description,
                    color = ExperienceMuted.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
        item.genres.take(2).forEach(::add)
        item.imdbRating?.let { add("★ ${String.format("%.1f", it)}") }
        if (isEmpty()) add(item.displayType)
    }.joinToString("  •  ")

@Composable
private fun ExperienceTopNav(
    modifier: Modifier,
    expanded: Boolean,
    navRequesters: Map<String, FocusRequester>,
    profileRequester: FocusRequester,
    profileStore: ProfileStore,
    onNavFocus: (String) -> Unit,
    onProfileFocus: () -> Unit,
    onDownToContent: () -> Boolean,
    onNavigate: (String) -> Unit,
) {
    val navAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = TvMotion.EaseOut),
        label = "experienceNavAlpha",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.82f else 0.18f,
        animationSpec = tween(durationMillis = 180, easing = TvMotion.EaseOut),
        label = "experienceNavScrim",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ExperienceTopBarHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ExperienceBlack.copy(alpha = scrimAlpha),
                            ExperienceBlack.copy(alpha = scrimAlpha * 0.55f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = ExperienceHorizontalSafe),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExperienceBrandAnchor()
        Spacer(Modifier.width(34.dp))

        Row(
            modifier = Modifier.graphicsLayer { alpha = navAlpha },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Home", "Search", "Library", "Settings").forEach { label ->
                ExperienceNavItem(
                    label = label,
                    selected = label == "Home",
                    requester = navRequesters.getValue(label),
                    onFocused = { onNavFocus(label) },
                    onDown = onDownToContent,
                    onClick = { onNavigate(label) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        ExperienceProfileAnchor(
            requester = profileRequester,
            profileStore = profileStore,
            onFocused = onProfileFocus,
            onDown = onDownToContent,
            onClick = { onNavigate("Profile") },
        )
    }
}

@Composable
private fun ExperienceBrandAnchor() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(SharedR.drawable.vueo_logo_mark),
            contentDescription = "VUEO",
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "VUEO",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.1.sp,
        )
    }
}

@Composable
private fun ExperienceNavItem(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onDown: () -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "experienceNavScale:$label",
    )
    val textColor by animateColorAsState(
        targetValue = if (focused || selected) Color.White else ExperienceMuted.copy(alpha = 0.72f),
        animationSpec = tvFocusColorSpec(),
        label = "experienceNavColor:$label",
    )

    Column(
        modifier =
            Modifier
                .focusRequester(requester)
                .scale(scale)
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onDown()
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .width(if (focused || selected) 20.dp else 0.dp)
                    .height(2.dp)
                    .background(
                        if (focused || selected) Color.White.copy(alpha = if (focused) 0.94f else 0.54f)
                        else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
        )
    }
}

@Composable
private fun ExperienceProfileAnchor(
    requester: FocusRequester,
    profileStore: ProfileStore,
    onFocused: () -> Unit,
    onDown: () -> Boolean,
    onClick: () -> Unit,
) {
    val profile = profileStore.activeProfile()
    val drawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tvFocusSpec(),
        label = "experienceProfileScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.22f),
        animationSpec = tvFocusColorSpec(),
        label = "experienceProfileBorder",
    )

    Box(
        modifier =
            Modifier
                .size(36.dp)
                .focusRequester(requester)
                .scale(scale)
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onDown()
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clip(CircleShape)
                .border(if (focused) 2.dp else 1.dp, borderColor, CircleShape)
                .clickable(onClick = onClick)
                .focusable()
                .background(ExperiencePanel),
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ExperienceRailRow(
    rail: ExperienceRail,
    railIndex: Int,
    rails: List<ExperienceRail>,
    requesters: Map<String, FocusRequester>,
    navHomeRequester: FocusRequester,
    onFocused: (Int, ExperienceEntry) -> Unit,
    onOpen: (ExperienceEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        Text(
            text = rail.title,
            color = Color.White.copy(alpha = 0.96f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = ExperienceHorizontalSafe, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = ExperienceHorizontalSafe, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = rail.entries,
                key = { _, entry -> entry.key },
            ) { itemIndex, entry ->
                val upRequester =
                    if (railIndex == 0) {
                        navHomeRequester
                    } else {
                        neighborRequester(
                            rail = rails[railIndex - 1],
                            preferredIndex = itemIndex,
                            requesters = requesters,
                        )
                    }
                val downRequester =
                    if (railIndex >= rails.lastIndex) {
                        null
                    } else {
                        neighborRequester(
                            rail = rails[railIndex + 1],
                            preferredIndex = itemIndex,
                            requesters = requesters,
                        )
                    }

                ExperienceCard(
                    entry = entry,
                    modifier =
                        Modifier
                            .focusRequester(requesters.getValue(entry.key))
                            .tvHorizontalEdgeGuard(
                                blockLeft = itemIndex == 0,
                                blockRight = itemIndex == rail.entries.lastIndex,
                            )
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> requestFocus(upRequester)
                                    Key.DirectionDown -> requestFocus(downRequester)
                                    else -> false
                                }
                            },
                    onFocused = {
                        onFocused(itemIndex, entry)
                        scope.launch { listState.animateScrollToItem(max(0, itemIndex - 1)) }
                    },
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

private fun neighborRequester(
    rail: ExperienceRail,
    preferredIndex: Int,
    requesters: Map<String, FocusRequester>,
): FocusRequester? {
    if (rail.entries.isEmpty()) return null
    val targetIndex = min(preferredIndex, rail.entries.lastIndex)
    return requesters[rail.entries[targetIndex].key]
}

private fun requestFocus(requester: FocusRequester?): Boolean {
    if (requester == null) return false
    return runCatching {
        requester.requestFocus()
        true
    }.getOrDefault(false)
}

@Composable
private fun ExperienceCard(
    entry: ExperienceEntry,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "experienceCardScale:${entry.key}",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) ExperienceFocus.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tvFocusColorSpec(),
        label = "experienceCardBorder:${entry.key}",
    )

    Column(
        modifier =
            modifier
                .width(ExperienceCardWidth)
                .zIndex(if (focused) 4f else 0f)
                .scale(scale)
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
                    .height(ExperienceCardHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ExperiencePanel)
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
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (focused) 0.38f else 0.58f),
                                )
                            )
                        ),
            )

            entry.progress?.let { progress ->
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
                                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.92f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = entry.media.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.progressLabel?.let { label ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = ExperienceMuted.copy(alpha = 0.72f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun continueLabel(entry: LibraryPlaybackEntry): String =
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
private fun ExperienceLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(ExperienceBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(SharedR.drawable.vueo_logo_mark),
                contentDescription = "VUEO",
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.84f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExperienceError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(ExperienceBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Home unavailable", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = ExperienceMuted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExperienceExitOverlay(
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
            color = Color(0xFF111318),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(450.dp),
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Text("Exit VUEO?", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Stay here or close the TV app.", color = ExperienceMuted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExperienceExitButton("Stay", stayRequester, primary = true, onClick = onStay)
                    ExperienceExitButton("Exit", exitRequester, primary = false, onClick = onExit)
                }
            }
        }
    }
}

@Composable
private fun ExperienceExitButton(
    label: String,
    requester: FocusRequester,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(),
        label = "experienceExitScale:$label",
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
                    color = if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else ExperienceDanger.copy(alpha = 0.14f),
                contentColor = if (primary) Color.Black else ExperienceDanger,
            ),
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
