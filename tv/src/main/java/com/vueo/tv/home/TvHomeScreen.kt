package com.vueo.tv.home

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.delay

private const val HERO_SETTLE_MS = 145L
private const val ROWS_TOP_FRACTION = .49f
private const val HERO_TEXT_WIDTH_FRACTION = .42f
private val HomeLandscapeWidth = 246.dp
private val HomeLandscapeHeight = 138.dp
private val HomePosterWidth = 136.dp
private val HomePosterHeight = 204.dp
private val HomeCardShape = RoundedCornerShape(10.dp)

private object HomeFocusMemory {
    var lastFocusedKey: String? = null
    val rowIndices = mutableMapOf<String, Int>()
}

private sealed interface HomeEntry {
    val key: String
    val media: MediaItem

    data class Media(
        override val key: String,
        override val media: MediaItem,
    ) : HomeEntry

    data class Resume(
        override val key: String,
        override val media: MediaItem,
        val playback: LibraryPlaybackEntry,
    ) : HomeEntry
}

private data class HomeRow(
    val id: String,
    val title: String,
    val entries: List<HomeEntry>,
    val landscape: Boolean,
)

@Composable
fun TvHomeScreen(
    runtime: TvRuntime,
    refreshToken: Int,
    onNavigate: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onProfile: () -> Unit,
) {
    var catalogRows by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime, refreshToken) {
        loading = catalogRows.isEmpty()
        error = null

        val cached = runCatching { runtime.homeRows(forceRefresh = false) }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) catalogRows = cached

        runCatching { runtime.homeRows(forceRefresh = true) }
            .onSuccess { fresh -> if (fresh.isNotEmpty()) catalogRows = fresh }
            .onFailure { failure ->
                if (catalogRows.isEmpty()) error = failure.message ?: "Unable to load Home"
            }

        loading = false
    }

    val continueWatching = remember(refreshToken) { runtime.libraryStore.continueWatching() }
    val watchlist = remember(refreshToken) { runtime.libraryStore.watchlist() }

    val rows = remember(catalogRows, continueWatching, watchlist) {
        buildList {
            if (continueWatching.isNotEmpty()) {
                add(
                    HomeRow(
                        id = "continue",
                        title = "Continue Watching",
                        entries = continueWatching.map { entry ->
                            HomeEntry.Resume(
                                key = "continue:${entry.mediaKey}",
                                media = entry.media,
                                playback = entry,
                            )
                        },
                        landscape = true,
                    )
                )
            }

            if (watchlist.isNotEmpty()) {
                add(
                    HomeRow(
                        id = "my-list",
                        title = "My List",
                        entries = watchlist.map { media ->
                            HomeEntry.Media(
                                key = "my-list:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                        landscape = false,
                    )
                )
            }

            catalogRows.forEach { row ->
                if (row.items.isNotEmpty()) {
                    add(
                        HomeRow(
                            id = "catalog:${row.id}",
                            title = row.title,
                            entries = row.items.mapIndexed { index, media ->
                                HomeEntry.Media(
                                    key = "${row.id}:$index:${media.type}:${media.id}",
                                    media = media,
                                )
                            },
                            landscape = false,
                        )
                    )
                }
            }
        }
    }

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val rowLastIndices = remember {
        mutableStateMapOf<String, Int>().apply { putAll(HomeFocusMemory.rowIndices) }
    }

    var navExpanded by remember { mutableStateOf(false) }
    var initialFocusResolved by remember { mutableStateOf(false) }
    var lastFocusedKey by remember { mutableStateOf(HomeFocusMemory.lastFocusedKey) }
    var pendingHeroEntry by remember { mutableStateOf<HomeEntry?>(null) }
    var heroEntry by remember { mutableStateOf<HomeEntry?>(null) }

    LaunchedEffect(rows) {
        if (initialFocusResolved) return@LaunchedEffect
        val allEntries = rows.flatMap(HomeRow::entries)
        val restored = lastFocusedKey?.let { saved -> allEntries.firstOrNull { it.key == saved } }
        val target = restored ?: allEntries.firstOrNull()
        if (target != null) {
            if (heroEntry == null) heroEntry = target
            if (pendingHeroEntry == null) pendingHeroEntry = target
            lastFocusedKey = target.key
            HomeFocusMemory.lastFocusedKey = target.key
            initialFocusResolved = true
            delay(100)
            runCatching { requester(target.key).requestFocus() }
        }
    }

    LaunchedEffect(pendingHeroEntry) {
        val next = pendingHeroEntry ?: return@LaunchedEffect
        delay(HERO_SETTLE_MS)
        heroEntry = next
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        HomeBackdrop(heroEntry?.media)
        HomeScrim()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight

            HomeHeroCopy(
                entry = heroEntry,
                modifier = Modifier
                    .fillMaxWidth(HERO_TEXT_WIDTH_FRACTION)
                    .padding(start = 92.dp, top = viewportHeight * .145f),
            )

            if (loading && rows.isEmpty()) {
                Row(
                    modifier = Modifier.padding(start = 92.dp, top = viewportHeight * .43f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        color = TvDesign.White,
                        strokeWidth = 2.dp,
                    )
                    Text("Loading Home", color = TvDesign.Muted, fontSize = 13.sp)
                }
            } else if (error != null && rows.isEmpty()) {
                Text(
                    text = error ?: "Unable to load Home",
                    color = TvDesign.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 92.dp, top = viewportHeight * .43f),
                )
            }

            if (rows.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = viewportHeight * ROWS_TOP_FRACTION),
                    contentPadding = PaddingValues(
                        start = 92.dp,
                        end = 34.dp,
                        bottom = 48.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { rowIndex, row ->
                        HomeMediaRow(
                            row = row,
                            rowIndex = rowIndex,
                            allRows = rows,
                            requester = ::requester,
                            rememberedIndexForRow = { targetRowId -> rowLastIndices[targetRowId] },
                            onRememberIndex = { index ->
                                rowLastIndices[row.id] = index
                                HomeFocusMemory.rowIndices[row.id] = index
                            },
                            onFocused = { entry, index ->
                                lastFocusedKey = entry.key
                                HomeFocusMemory.lastFocusedKey = entry.key
                                rowLastIndices[row.id] = index
                                HomeFocusMemory.rowIndices[row.id] = index
                                pendingHeroEntry = entry
                                navExpanded = false
                            },
                            onOpenSidebar = {
                                navExpanded = true
                                runCatching { navRequesters.getValue("Home").requestFocus() }
                            },
                            onOpen = { entry ->
                                when (entry) {
                                    is HomeEntry.Resume -> onResume(entry.playback)
                                    is HomeEntry.Media -> onOpenMedia(entry.media)
                                }
                            },
                        )
                    }
                }
            }
        }

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
                val target = lastFocusedKey
                if (target != null) runCatching { requester(target).requestFocus() }
                target != null
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun HomeBackdrop(media: MediaItem?) {
    AnimatedContent(
        targetState = media?.background ?: media?.poster,
        transitionSpec = {
            fadeIn(tween(360)) togetherWith fadeOut(tween(220))
        },
        label = "homeBackdrop",
        modifier = Modifier.fillMaxSize(),
    ) { url ->
        TvNetworkImage(
            url = url,
            contentDescription = media?.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )
    }
}

@Composable
private fun HomeScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to TvDesign.Black.copy(alpha = .96f),
                    .30f to TvDesign.Black.copy(alpha = .82f),
                    .52f to TvDesign.Black.copy(alpha = .43f),
                    .74f to TvDesign.Black.copy(alpha = .11f),
                    1f to TvDesign.Black.copy(alpha = .04f),
                )
            )
            .background(
                Brush.verticalGradient(
                    0f to TvDesign.Black.copy(alpha = .20f),
                    .40f to Color.Transparent,
                    .60f to TvDesign.Black.copy(alpha = .18f),
                    .80f to TvDesign.Black.copy(alpha = .72f),
                    1f to TvDesign.Black.copy(alpha = .96f),
                )
            )
    )
}

@Composable
private fun HomeHeroCopy(
    entry: HomeEntry?,
    modifier: Modifier = Modifier,
) {
    val media = entry?.media ?: return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val meta = heroMeta(entry)
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        media.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .72f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeMediaRow(
    row: HomeRow,
    rowIndex: Int,
    allRows: List<HomeRow>,
    requester: (String) -> FocusRequester,
    rememberedIndexForRow: (String) -> Int?,
    onRememberIndex: (Int) -> Unit,
    onFocused: (HomeEntry, Int) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpen: (HomeEntry) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (row.landscape) 14.dp else 13.dp),
            contentPadding = PaddingValues(end = 34.dp),
        ) {
            itemsIndexed(row.entries, key = { _, entry -> entry.key }) { index, entry ->
                HomeMediaCard(
                    entry = entry,
                    landscape = row.landscape,
                    requester = requester(entry.key),
                    onFocused = {
                        onRememberIndex(index)
                        onFocused(entry, index)
                    },
                    onKey = { keyCode ->
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (index == 0) {
                                    onOpenSidebar()
                                    true
                                } else false
                            }

                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (rowIndex <= 0) {
                                    true
                                } else {
                                    val targetRow = allRows[rowIndex - 1]
                                    val targetIndex = (rememberedIndexForRow(targetRow.id) ?: index)
                                        .coerceAtMost(targetRow.entries.lastIndex)
                                    val target = targetRow.entries.getOrNull(targetIndex)
                                    if (target != null) {
                                        runCatching { requester(target.key).requestFocus() }
                                        true
                                    } else false
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (rowIndex >= allRows.lastIndex) {
                                    true
                                } else {
                                    val targetRow = allRows[rowIndex + 1]
                                    val targetIndex = (rememberedIndexForRow(targetRow.id) ?: index)
                                        .coerceAtMost(targetRow.entries.lastIndex)
                                    val target = targetRow.entries.getOrNull(targetIndex)
                                    if (target != null) {
                                        runCatching { requester(target.key).requestFocus() }
                                        true
                                    } else false
                                }
                            }

                            else -> false
                        }
                    },
                    onOpen = { onOpen(entry) },
                )
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    entry: HomeEntry,
    landscape: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onKey: (Int) -> Boolean,
    onOpen: () -> Unit,
) {
    var focused by remember(entry.key) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.022f else 1f,
        animationSpec = tween(if (focused) 120 else 90),
        label = "homeCardScale",
    )
    val width = if (landscape) HomeLandscapeWidth else HomePosterWidth
    val imageHeight = if (landscape) HomeLandscapeHeight else HomePosterHeight
    val imageUrl = if (landscape) entry.media.background ?: entry.media.poster else entry.media.poster ?: entry.media.background
    val progress = (entry as? HomeEntry.Resume)?.playback?.progressFraction

    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(imageHeight)
                .scale(scale)
                .clip(HomeCardShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvDesign.White.copy(alpha = .92f) else TvDesign.White.copy(alpha = .07f),
                    shape = HomeCardShape,
                )
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onKey(event.nativeKeyEvent.keyCode) else false
                }
                .clickable(onClick = onOpen)
                .focusable(),
        ) {
            TvNetworkImage(
                url = imageUrl,
                contentDescription = entry.media.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                TvDesign.Black.copy(alpha = if (landscape) .64f else .18f),
                            )
                        )
                    )
            )

            if (landscape) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val resume = entry as? HomeEntry.Resume
                    if (resume?.playback?.season != null && resume.playback.episode != null) {
                        Text(
                            text = "S${resume.playback.season} E${resume.playback.episode}",
                            color = TvDesign.White.copy(alpha = .78f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = entry.media.name,
                        color = TvDesign.White,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = .55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(TvDesign.White.copy(alpha = .94f)),
                    )
                }
            }
        }

        if (!landscape) {
            Text(
                text = entry.media.name,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .68f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

private fun heroMeta(entry: HomeEntry?): String {
    val media = entry?.media ?: return ""
    val resume = (entry as? HomeEntry.Resume)?.playback
    val parts = buildList {
        if (resume?.season != null && resume.episode != null) {
            add("S${resume.season} E${resume.episode}")
            resume.episodeTitle?.takeIf { it.isNotBlank() }?.let(::add)
        }
        media.displayType.takeIf { it.isNotBlank() }?.let(::add)
        media.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        media.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        media.imdbRating?.let { add("IMDb %.1f".format(it)) }
        if (resume != null && resume.durationMs > resume.positionMs) {
            val minutesLeft = ((resume.durationMs - resume.positionMs) / 60_000L).coerceAtLeast(1L)
            add("${minutesLeft}m left")
        } else {
            media.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}m") }
        }
    }
    return parts.joinToString("  •  ")
}
