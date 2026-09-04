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
import com.vueo.tv.TV_TOP_NAV_LABELS
import com.vueo.tv.TvTopNav
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LibraryBlack = Color(0xFF050706)
private val LibraryPanel = Color(0xFF101412)
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
            TV_TOP_NAV_LABELS
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
            gridState.scrollToItem((focusedIndex - 5).coerceAtLeast(0))
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
                            Color(0xFF0A0D0B),
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
                    .padding(top = 98.dp),
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
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "My List  •  ${items.size} title${if (items.size == 1) "" else "s"}",
                        color = LibraryMuted,
                        fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            if (items.isEmpty()) {
                LibraryEmpty(
                    requester = browseRequester,
                    upRequester = navRequesters.getValue("Library"),
                    onBrowse = { onNavigate("Home") },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 58.dp, end = 58.dp, bottom = 50.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
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
                            upRequester = if (index < 5) navRequesters.getValue("Library") else null,
                            onFocused = {
                                focusedIndex = index
                                TvLibraryFocusMemory.resultIndex = index
                                if (index >= 5) {
                                    scope.launch {
                                        gridState.animateScrollToItem((index - 5).coerceAtLeast(0))
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "libraryEmptyButtonScale")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 48.dp),
    ) {
        Text(
            text = "Your Library is empty",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = "Add movies and series to My List from Home or Detail.",
            color = LibraryMuted,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBrowse,
            modifier =
                Modifier
                    .focusRequester(requester)
                    .tvVerticalFocus(up = upRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .scale(scale),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            shape = RoundedCornerShape(11.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Browse Home", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    val scale by animateFloatAsState(if (focused) 1.075f else 1f, label = "libraryPosterScale")

    Column(
        modifier =
            modifier
                .width(176.dp)
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
                    .height(254.dp)
                    .background(LibraryPanel, RoundedCornerShape(12.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
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
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.38f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = media.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = media.displayType,
            color = if (focused) Color.White.copy(alpha = 0.72f) else LibraryMuted,
            fontSize = 12.sp,
        )
    }
}
