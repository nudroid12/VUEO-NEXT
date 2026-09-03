package com.vueo.tv.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.TvTopNav
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LibraryBlack = Color(0xFF050706)
private val LibraryPanel = Color(0xFF101412)
private val LibraryYellow = Color(0xFFD6FF00)
private val LibraryGreen = Color(0xFF84E100)
private val LibraryMuted = Color(0xFFAAB2AD)

private object TvLibraryFocusMemory {
    var resultIndex: Int = 0
}

@Composable
fun TvLibraryScreen(
    store: TvLibraryStore,
    focusRestoreToken: Int = 0,
    onNavigate: (String) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val navRequesters =
        remember {
            listOf("Home", "Search", "Library", "Content Manager", "Luckez")
                .associateWith { FocusRequester() }
        }
    val firstCardRequester = remember { FocusRequester() }
    val browseRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(store.items()) }
    var focusedIndex by remember { mutableStateOf(TvLibraryFocusMemory.resultIndex) }

    BackHandler { onNavigate("Home") }

    LaunchedEffect(focusRestoreToken) {
        items = store.items()
        delay(100)
        if (items.isEmpty()) {
            runCatching { browseRequester.requestFocus() }
        } else {
            focusedIndex = TvLibraryFocusMemory.resultIndex.coerceIn(0, items.lastIndex)
            gridState.scrollToItem((focusedIndex - 6).coerceAtLeast(0))
            runCatching { firstCardRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A100C),
                            LibraryBlack,
                            LibraryBlack,
                        )
                    )
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Library",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "My List • ${items.size} title${if (items.size == 1) "" else "s"}",
                        color = LibraryMuted,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (items.isEmpty()) {
                LibraryEmpty(
                    requester = browseRequester,
                    upRequester = navRequesters.getValue("Library"),
                    onBrowse = { onNavigate("Home") },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 58.dp, end = 58.dp, bottom = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, media -> "${media.type}:${media.id}" },
                    ) { index, media ->
                        LibraryPoster(
                            media = media,
                            modifier =
                                if (index == focusedIndex) {
                                    Modifier.focusRequester(firstCardRequester)
                                } else {
                                    Modifier
                                },
                            upRequester = if (index < 6) navRequesters.getValue("Library") else null,
                            onFocused = {
                                focusedIndex = index
                                TvLibraryFocusMemory.resultIndex = index
                                if (index >= 6) {
                                    scope.launch {
                                        gridState.animateScrollToItem((index - 6).coerceAtLeast(0))
                                    }
                                }
                            },
                            onClick = { onOpenMedia(media) },
                        )
                    }
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = if (items.isEmpty()) browseRequester else firstCardRequester,
            selectedLabel = "Library",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun LibraryEmpty(
    requester: FocusRequester,
    upRequester: FocusRequester,
    onBrowse: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 42.dp),
    ) {
        Text(
            text = "Your Library is empty",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add movies and series to My List from Home or Detail.",
            color = LibraryMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBrowse,
            modifier = Modifier.focusRequester(requester).tvVerticalFocus(up = upRequester),
            colors = ButtonDefaults.buttonColors(containerColor = LibraryGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(9.dp),
        ) {
            Text("Browse Home", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryPoster(
    media: TvMediaItem,
    modifier: Modifier,
    upRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "libraryPosterScale")

    Column(
        modifier =
            modifier
                .width(154.dp)
                .scale(scale)
                .then(if (upRequester != null) Modifier.tvVerticalFocus(up = upRequester) else Modifier)
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
                    .height(220.dp)
                    .background(LibraryPanel, RoundedCornerShape(10.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) LibraryYellow else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                    ),
        ) {
            TvNetworkImage(
                url = media.poster,
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (focused) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(LibraryGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = media.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = media.displayType,
            color = LibraryMuted,
            fontSize = 11.sp,
        )
    }
}
