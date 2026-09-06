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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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

private const val HERO_SETTLE_MS = 160L
private const val HOME_ROWS_TOP_FRACTION = .49f
private const val HOME_HERO_TEXT_FRACTION = .42f
private val HomeContentStart = 92.dp
private val HomeContentEnd = 48.dp
private val ContinueWatchingWidth = 252.dp
private val PosterWidth = 144.dp
private val HomeCardShape = RoundedCornerShape(12.dp)

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

        val fresh = runCatching { runtime.homeRows(forceRefresh = true) }
        fresh.onSuccess { if (it.isNotEmpty()) catalogRows = it }
            .onFailure { if (catalogRows.isEmpty()) error = it.message ?: "Unable to load Home" }

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
                        entries = continueWatching.map {
                            HomeEntry.Resume(
                                key = "continue:${it.mediaKey}",
                                media = it.media,
                                playback = it,
                            )
                        },
                    )
                )
            }

            if (watchlist.isNotEmpty()) {
                add(
                    HomeRow(
                        id = "my-list",
                        title = "My List",
                        entries = watchlist.map {
                            HomeEntry.Media(
                                key = "my-list:${it.type}:${it.id}",
                                media = it,
                            )
                        },
                    )
                )
            }

            catalogRows.forEach { row ->
                if (row.items.isNotEmpty()) {
                    add(
                        HomeRow(
                            id = row.id,
                            title = row.title,
                            entries = row.items.mapIndexed { index, media ->
                                HomeEntry.Media(
                                    key = "${row.id}:$index:${media.type}:${media.id}",
                                    media = media,
                                )
                            },
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
    val lastIndexByRow = remember { mutableMapOf<String, Int>() }

    var lastFocusedKey by remember { mutableStateOf<String?>(null) }
    var navExpanded by remember { mutableStateOf(false) }
    var pendingHero by remember { mutableStateOf<MediaItem?>(null) }
    var hero by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(rows) {
        val first = rows.firstOrNull()?.entries?.firstOrNull()
        if (hero == null) hero = first?.media
        if (pendingHero == null) pendingHero = first?.media
        if (lastFocusedKey == null && first != null) {
            lastFocusedKey = first.key
            delay(100L)
            runCatching { requester(first.key).requestFocus() }
        }
    }

    LaunchedEffect(pendingHero) {
        val next = pendingHero ?: return@LaunchedEffect
        delay(HERO_SETTLE_MS)
        hero = next
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        AnimatedContent(
            targetState = hero?.background ?: hero?.poster,
            transitionSpec = {
                fadeIn(animationSpec = tween(360)) togetherWith fadeOut(animationSpec = tween(190))
            },
            label = "homeHeroBackdrop",
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            TvNetworkImage(
                url = url,
                contentDescription = hero?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.Black,
            )
        }

        // VUEO 30C: keep the art alive on the right while making the left reading field calm.
        Box(
            Modifier
                .fillMaxSize()
                .background(TvDesign.Black.copy(alpha = .08f))
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to TvDesign.Black.copy(alpha = .96f),
                            0.24f to TvDesign.Black.copy(alpha = .82f),
                            0.48f to TvDesign.Black.copy(alpha = .42f),
                            0.70f to TvDesign.Black.copy(alpha = .10f),
                            1.00f to Color.Transparent,
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to TvDesign.Black.copy(alpha = .16f),
                            0.40f to Color.Transparent,
                            0.58f to Color.Transparent,
                            0.78f to TvDesign.Black.copy(alpha = .58f),
                            1.00f to TvDesign.Black.copy(alpha = .97f),
                        )
                    )
                )
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowsTop = maxHeight * HOME_ROWS_TOP_FRACTION

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowsTop),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = HomeContentStart, end = HomeContentEnd, bottom = 24.dp)
                        .fillMaxWidth(HOME_HERO_TEXT_FRACTION),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = hero?.name.orEmpty(),
                        color = TvDesign.White,
                        fontSize = 38.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val meta = heroMeta(hero)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = TvDesign.White.copy(alpha = .74f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    hero?.description?.takeIf { it.isNotBlank() }?.let { description ->
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

            when {
                loading && rows.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = HomeContentStart, bottom = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(22.dp).height(22.dp),
                            color = TvDesign.White,
                            strokeWidth = 2.dp,
                        )
                        Text("Loading your library", color = TvDesign.Muted, fontSize = 14.sp)
                    }
                }

                error != null && rows.isEmpty() -> {
                    Text(
                        text = error ?: "Unable to load Home",
                        color = TvDesign.Muted,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = HomeContentStart, bottom = 44.dp),
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = maxHeight * HOME_ROWS_TOP_FRACTION),
                    contentPadding = PaddingValues(
                        start = HomeContentStart,
                        end = HomeContentEnd,
                        bottom = 64.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { rowIndex, row ->
                        HomeMediaRow(
                            row = row,
                            requester = ::requester,
                            onFocused = { index, entry ->
                                lastIndexByRow[row.id] = index
                                lastFocusedKey = entry.key
                                pendingHero = entry.media
                                navExpanded = false
                            },
                            onLeftFromRow = {
                                navExpanded = true
                                runCatching { navRequesters.getValue("Home").requestFocus() }
                            },
                            onVerticalMove = { currentIndex, direction ->
                                val targetRowIndex = rowIndex + direction
                                val targetRow = rows.getOrNull(targetRowIndex) ?: return@HomeMediaRow false
                                val rememberedIndex = lastIndexByRow[targetRow.id] ?: currentIndex
                                val targetIndex = rememberedIndex.coerceIn(0, targetRow.entries.lastIndex)
                                val target = targetRow.entries.getOrNull(targetIndex) ?: return@HomeMediaRow false
                                lastIndexByRow[targetRow.id] = targetIndex
                                runCatching { requester(target.key).requestFocus() }.isSuccess
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
private fun HomeMediaRow(
    row: HomeRow,
    requester: (String) -> FocusRequester,
    onFocused: (Int, HomeEntry) -> Unit,
    onLeftFromRow: () -> Unit,
    onVerticalMove: (Int, Int) -> Boolean,
    onOpen: (HomeEntry) -> Unit,
) {
    val isContinueWatching = row.id == "continue"
    val cardWidth = if (isContinueWatching) ContinueWatchingWidth else PosterWidth
    val cardRatio = if (isContinueWatching) 16f / 9f else 2f / 3f
    val cardGap = if (isContinueWatching) 12.dp else 13.dp

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(cardGap),
            contentPadding = PaddingValues(top = 6.dp, bottom = 7.dp),
        ) {
            itemsIndexed(row.entries, key = { _, entry -> entry.key }) { index, entry ->
                var focused by remember(entry.key) { mutableStateOf(false) }
                val focusScale by animateFloatAsState(
                    targetValue = if (focused) 1.02f else 1f,
                    animationSpec = tween(if (focused) 150 else 110),
                    label = "homeCardScale",
                )
                val resume = entry as? HomeEntry.Resume
                val progress = resume?.playback?.progressFraction

                Column(
                    modifier = Modifier.width(cardWidth),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(cardRatio)
                            .scale(focusScale)
                            .shadow(
                                elevation = if (focused) 8.dp else 0.dp,
                                shape = HomeCardShape,
                                clip = false,
                            )
                            .clip(HomeCardShape)
                            .background(TvDesign.SurfaceRaised)
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = if (focused) {
                                    TvDesign.White.copy(alpha = .88f)
                                } else {
                                    TvDesign.White.copy(alpha = .055f)
                                },
                                shape = HomeCardShape,
                            )
                            .focusRequester(requester(entry.key))
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocused(index, entry)
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (index == 0) {
                                            onLeftFromRow()
                                            true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> onVerticalMove(index, -1)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> onVerticalMove(index, 1)
                                    else -> false
                                }
                            }
                            .clickable { onOpen(entry) }
                            .focusable(),
                    ) {
                        TvNetworkImage(
                            url = if (isContinueWatching) {
                                entry.media.background ?: entry.media.poster
                            } else {
                                entry.media.poster ?: entry.media.background
                            },
                            contentDescription = entry.media.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        if (isContinueWatching) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                TvDesign.Black.copy(alpha = .84f),
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 11.dp, end = 11.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.media.name,
                                    color = TvDesign.White,
                                    fontSize = 13.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                resume?.let {
                                    Text(
                                        text = continueMeta(it.playback),
                                        color = TvDesign.White.copy(alpha = .70f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (progress != null && progress > 0f) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.Black.copy(alpha = .50f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .background(TvDesign.White.copy(alpha = .94f))
                                )
                            }
                        }
                    }

                    if (!isContinueWatching) {
                        Text(
                            text = entry.media.name,
                            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .62f),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun continueMeta(playback: LibraryPlaybackEntry): String =
    buildList {
        if (playback.season != null && playback.episode != null) {
            add("S${playback.season.toString().padStart(2, '0')} E${playback.episode.toString().padStart(2, '0')}")
        }
        if (playback.durationMs > playback.positionMs && playback.durationMs > 0L) {
            val minutes = ((playback.durationMs - playback.positionMs) / 60_000L).coerceAtLeast(1L)
            add("${minutes} min left")
        } else {
            add("${(playback.progressFraction * 100).toInt().coerceIn(1, 99)}% watched")
        }
    }.joinToString("  •  ")

private fun heroMeta(media: MediaItem?): String {
    if (media == null) return ""
    return listOfNotNull(
        media.displayType.takeIf(String::isNotBlank),
        media.genres.firstOrNull()?.takeIf(String::isNotBlank),
        media.releaseInfo?.takeIf(String::isNotBlank),
        media.imdbRating?.let { "IMDb %.1f".format(it) },
        media.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
    ).joinToString("  •  ")
}
