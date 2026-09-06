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
import com.vueo.tv.ui.TvSidebar
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
                .background(TvDesign.Black.copy(alpha = .11f))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .95f),
                            TvDesign.Black.copy(alpha = .76f),
                            TvDesign.Black.copy(alpha = .36f),
                            TvDesign.Black.copy(alpha = .06f),
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .20f),
                            Color.Transparent,
                            Color.Transparent,
                            TvDesign.Black.copy(alpha = .64f),
                            TvDesign.Black.copy(alpha = .97f),
                        )
                    )
                )
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 58.dp),
            ) {
                Spacer(Modifier.height(viewportHeight * .125f))

                Column(
                    modifier = Modifier.fillMaxWidth(.49f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        text = hero?.name.orEmpty(),
                        color = TvDesign.White,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val meta = heroMeta(hero)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = TvDesign.White.copy(alpha = .72f),
                            fontSize = 14.sp,
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(viewportHeight * .045f))

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
                        .padding(top = maxHeight * .465f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 58.dp,
                        end = 58.dp,
                        bottom = 58.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { _, row ->
                        HomeMediaRow(
                            row = row,
                            requester = ::requester,
                            onFocused = { entry ->
                                lastFocusedKey = entry.key
                                pendingHero = entry.media
                                navExpanded = false
                            },
                            onLeftFromRow = {
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
    onFocused: (HomeEntry) -> Unit,
    onLeftFromRow: () -> Unit,
    onOpen: (HomeEntry) -> Unit,
) {
    val isContinueWatching = row.id == "continue"
    val cardWidth = if (isContinueWatching) 238.dp else 148.dp
    val cardRatio = if (isContinueWatching) 16f / 9f else 2f / 3f
    val cardGap = if (isContinueWatching) 15.dp else 14.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = row.title,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(cardGap),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = 8.dp,
            ),
        ) {
            itemsIndexed(row.entries, key = { _, entry -> entry.key }) { index, entry ->
                var focused by remember(entry.key) { mutableStateOf(false) }
                val focusScale by animateFloatAsState(
                    targetValue = if (focused) 1.028f else 1f,
                    animationSpec = tween(if (focused) 165 else 125),
                    label = "homeCardScale",
                )
                val resume = entry as? HomeEntry.Resume
                val progress = resume?.playback?.progressFraction

                Column(
                    modifier = Modifier.width(cardWidth),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(cardRatio)
                            .scale(focusScale)
                            .shadow(
                                elevation = if (focused) 10.dp else 0.dp,
                                shape = HomeCardShape,
                                clip = false,
                            )
                            .clip(HomeCardShape)
                            .background(TvDesign.SurfaceRaised)
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = if (focused) {
                                    TvDesign.White.copy(alpha = .82f)
                                } else {
                                    TvDesign.White.copy(alpha = .06f)
                                },
                                shape = HomeCardShape,
                            )
                            .focusRequester(requester(entry.key))
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocused(entry)
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    index == 0 &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                ) {
                                    onLeftFromRow()
                                    true
                                } else false
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
                                                TvDesign.Black.copy(alpha = .82f),
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
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
                                        color = TvDesign.White.copy(alpha = .68f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else if (focused) {
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
                                    .height(3.dp)
                                    .background(Color.Black.copy(alpha = .48f))
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
                            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .66f),
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
        media.releaseInfo?.takeIf(String::isNotBlank),
        media.displayType.takeIf(String::isNotBlank),
        media.imdbRating?.let { "IMDb %.1f".format(it) },
        media.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
    ).joinToString("  •  ")
}
