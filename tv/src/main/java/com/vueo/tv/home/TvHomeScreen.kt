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
import com.vueo.tv.ui.TvTopBar
import kotlinx.coroutines.delay

private const val HERO_SETTLE_MS = 180L
private val HomeCardShape = RoundedCornerShape(11.dp)

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

    val navRequesters = remember {
        TvPrimaryDestinations.associateWith { FocusRequester() }
    }
    val profileRequester = remember { FocusRequester() }

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
            delay(110)
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
                fadeIn(animationSpec = tween(durationMillis = 420)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 220))
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

        // 29B: layered scrims keep artwork cinematic while giving copy and rows a calm reading field.
        Box(
            Modifier
                .fillMaxSize()
                .background(TvDesign.Black.copy(alpha = .08f))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .98f),
                            TvDesign.Black.copy(alpha = .84f),
                            TvDesign.Black.copy(alpha = .43f),
                            TvDesign.Black.copy(alpha = .08f),
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .48f),
                            Color.Transparent,
                            Color.Transparent,
                            TvDesign.Black.copy(alpha = .70f),
                            TvDesign.Black.copy(alpha = .98f),
                        )
                    )
                )
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 54.dp),
            ) {
                Spacer(Modifier.height(viewportHeight * .205f))

                Column(
                    modifier = Modifier.fillMaxWidth(.46f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = hero?.name.orEmpty(),
                        color = TvDesign.White,
                        fontSize = 39.sp,
                        lineHeight = 43.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val meta = heroMeta(hero)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = TvDesign.White.copy(alpha = .72f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    hero?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            color = TvDesign.White.copy(alpha = .68f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(viewportHeight * .11f))

                if (loading && rows.isEmpty()) {
                    Row(
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
                } else if (error != null && rows.isEmpty()) {
                    Text(
                        text = error ?: "Unable to load Home",
                        color = TvDesign.Muted,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = maxHeight * .62f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 54.dp,
                        end = 54.dp,
                        bottom = 58.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { rowIndex, row ->
                        HomeMediaRow(
                            row = row,
                            rowIndex = rowIndex,
                            requester = ::requester,
                            onFocused = { entry ->
                                lastFocusedKey = entry.key
                                pendingHero = entry.media
                                navExpanded = false
                            },
                            onUpFromFirstRow = {
                                navExpanded = true
                                navRequesters.getValue("Home").requestFocus()
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

        TvTopBar(
            selected = "Home",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onDownFromNav = {
                navExpanded = false
                val target = lastFocusedKey
                if (target != null) runCatching { requester(target).requestFocus() }
                target != null
            },
            cinematicCollapsed = true,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HomeMediaRow(
    row: HomeRow,
    rowIndex: Int,
    requester: (String) -> FocusRequester,
    onFocused: (HomeEntry) -> Unit,
    onUpFromFirstRow: () -> Unit,
    onOpen: (HomeEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 9.dp,
                bottom = 9.dp,
            ),
        ) {
            itemsIndexed(row.entries, key = { _, entry -> entry.key }) { _, entry ->
                var focused by remember(entry.key) { mutableStateOf(false) }
                val focusScale by animateFloatAsState(
                    targetValue = if (focused) 1.045f else 1f,
                    animationSpec = tween(if (focused) 145 else 110),
                    label = "homeCardScale",
                )
                val progress = (entry as? HomeEntry.Resume)?.playback?.progressFraction

                Column(
                    modifier = Modifier.width(244.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .scale(focusScale)
                            .shadow(
                                elevation = if (focused) 14.dp else 0.dp,
                                shape = HomeCardShape,
                                clip = false,
                            )
                            .clip(HomeCardShape)
                            .background(TvDesign.SurfaceRaised)
                            .border(
                                width = if (focused) 1.dp else 0.dp,
                                color = if (focused) TvDesign.White.copy(alpha = .82f) else Color.Transparent,
                                shape = HomeCardShape,
                            )
                            .focusRequester(requester(entry.key))
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocused(entry)
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    rowIndex == 0 &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                ) {
                                    onUpFromFirstRow()
                                    true
                                } else false
                            }
                            .clickable { onOpen(entry) }
                            .focusable(),
                    ) {
                        TvNetworkImage(
                            url = entry.media.background ?: entry.media.poster,
                            contentDescription = entry.media.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        if (focused) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                TvDesign.White.copy(alpha = .035f),
                                            )
                                        )
                                    )
                            )
                        }

                        if (progress != null && progress > 0f) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.Black.copy(alpha = .52f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .background(TvDesign.White.copy(alpha = .96f))
                                )
                            }
                        }
                    }

                    Text(
                        text = entry.media.name,
                        color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .68f),
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

private fun heroMeta(media: MediaItem?): String {
    if (media == null) return ""
    return listOfNotNull(
        media.releaseInfo?.takeIf(String::isNotBlank),
        media.displayType.takeIf(String::isNotBlank),
        media.imdbRating?.let { "IMDb %.1f".format(it) },
        media.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
    ).joinToString("  •  ")
}
