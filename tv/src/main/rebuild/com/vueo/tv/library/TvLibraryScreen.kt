package com.vueo.tv.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvTopBar

@Composable
fun TvLibraryScreen(
    runtime: TvRuntime,
    refreshToken: Int,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val continueWatching = remember(refreshToken) { runtime.libraryStore.continueWatching() }
    val watchlist = remember(refreshToken) { runtime.libraryStore.watchlist() }
    val history = remember(refreshToken) { runtime.libraryStore.history() }

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 52.dp,
                end = 52.dp,
                top = 112.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                Text(
                    text = "Library",
                    color = TvDesign.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (continueWatching.isNotEmpty()) {
                item {
                    LibrarySection(
                        title = "Continue Watching",
                        entries = continueWatching,
                        onClick = onResume,
                    )
                }
            }

            if (watchlist.isNotEmpty()) {
                item {
                    MediaSection(
                        title = "My List",
                        items = watchlist,
                        onClick = onOpenMedia,
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    MediaSection(
                        title = "Recently Watched",
                        items = history.map { it.media }.distinctBy { "${it.type}:${it.id}" },
                        onClick = onOpenMedia,
                    )
                }
            }

            if (continueWatching.isEmpty() && watchlist.isEmpty() && history.isEmpty()) {
                item {
                    Text(
                        text = "Your library is empty.",
                        color = TvDesign.Muted,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        TvTopBar(
            selected = "Library",
            expanded = true,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {},
            onNavigate = onNavigate,
            onProfile = onProfile,
            onDownFromNav = { false },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun LibrarySection(
    title: String,
    entries: List<LibraryPlaybackEntry>,
    onClick: (LibraryPlaybackEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(entries, key = { it.mediaKey }) { entry ->
                LibraryCard(
                    media = entry.media,
                    progress = entry.progressFraction,
                    onClick = { onClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { "${it.type}:${it.id}:${it.name}" }) { media ->
                LibraryCard(media = media, progress = null, onClick = { onClick(media) })
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TvDesign.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LibraryCard(
    media: MediaItem,
    progress: Float?,
    onClick: () -> Unit,
) {
    var focused by remember(media.id, media.type) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(210.dp)
            .height(118.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(TvDesign.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.White else Color.Transparent,
                shape = RoundedCornerShape(11.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        TvNetworkImage(
            url = media.background ?: media.poster,
            contentDescription = media.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .82f))
                )
            )
        )
        Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(TvDesign.White)
            )
        }
    }
}
