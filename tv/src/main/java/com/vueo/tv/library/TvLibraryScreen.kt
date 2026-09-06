package com.vueo.tv.library

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.delay

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
    val contentRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun contentRequester(key: String): FocusRequester =
        contentRequesters.getOrPut(key) { FocusRequester() }

    var navExpanded by remember { mutableStateOf(false) }
    var lastFocusedKey by remember { mutableStateOf<String?>(null) }

    val firstContentKey =
        continueWatching.firstOrNull()?.let { "continue:${it.mediaKey}" }
            ?: watchlist.firstOrNull()?.let { "list:${it.type}:${it.id}" }
            ?: history.firstOrNull()?.media?.let { "history:${it.type}:${it.id}" }

    LaunchedEffect(firstContentKey) {
        val key = firstContentKey ?: return@LaunchedEffect
        if (lastFocusedKey == null) lastFocusedKey = key
        delay(90)
        runCatching { contentRequester(lastFocusedKey ?: key).requestFocus() }
    }

    fun focusSidebar() {
        navExpanded = true
        runCatching { navRequesters.getValue("Library").requestFocus() }
    }

    fun restoreContentFocus(): Boolean {
        navExpanded = false
        val key = lastFocusedKey ?: firstContentKey ?: return false
        return runCatching {
            contentRequester(key).requestFocus()
            true
        }.getOrDefault(false)
    }

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 52.dp,
                end = 52.dp,
                top = 54.dp,
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
                        requester = ::contentRequester,
                        onFocused = { key ->
                            navExpanded = false
                            lastFocusedKey = key
                        },
                        onLeftFromFirst = ::focusSidebar,
                        onClick = onResume,
                    )
                }
            }

            if (watchlist.isNotEmpty()) {
                item {
                    MediaSection(
                        title = "My List",
                        sectionKey = "list",
                        items = watchlist,
                        requester = ::contentRequester,
                        onFocused = { key ->
                            navExpanded = false
                            lastFocusedKey = key
                        },
                        onLeftFromFirst = ::focusSidebar,
                        onClick = onOpenMedia,
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    MediaSection(
                        title = "Recently Watched",
                        sectionKey = "history",
                        items = history.map { it.media }.distinctBy { "${it.type}:${it.id}" },
                        requester = ::contentRequester,
                        onFocused = { key ->
                            navExpanded = false
                            lastFocusedKey = key
                        },
                        onLeftFromFirst = ::focusSidebar,
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

        TvSidebar(
            selected = "Library",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = ::restoreContentFocus,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun LibrarySection(
    title: String,
    entries: List<LibraryPlaybackEntry>,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onLeftFromFirst: () -> Unit,
    onClick: (LibraryPlaybackEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(entries, key = { _, item -> item.mediaKey }) { index, entry ->
                val key = "continue:${entry.mediaKey}"
                LibraryCard(
                    media = entry.media,
                    progress = entry.progressFraction,
                    requester = requester(key),
                    onFocused = { onFocused(key) },
                    onLeft = if (index == 0) onLeftFromFirst else null,
                    onClick = { onClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    sectionKey: String,
    items: List<MediaItem>,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onLeftFromFirst: () -> Unit,
    onClick: (MediaItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(items, key = { _, item -> "${item.type}:${item.id}:${item.name}" }) { index, media ->
                val key = "$sectionKey:${media.type}:${media.id}"
                LibraryCard(
                    media = media,
                    progress = null,
                    requester = requester(key),
                    onFocused = { onFocused(key) },
                    onLeft = if (index == 0) onLeftFromFirst else null,
                    onClick = { onClick(media) },
                )
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
    requester: FocusRequester,
    onFocused: () -> Unit,
    onLeft: (() -> Unit)?,
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
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    onLeft != null
                ) {
                    onLeft()
                    true
                } else false
            }
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
