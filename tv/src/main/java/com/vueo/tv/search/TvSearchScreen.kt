package com.vueo.tv.search

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvTopBar
import kotlinx.coroutines.delay

@Composable
fun TvSearchScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }

    val fieldRequester = remember { FocusRequester() }
    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fieldRequester.requestFocus() }
    }

    LaunchedEffect(query) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(260)
        searching = true
        results = runCatching { runtime.search(normalized) }.getOrDefault(emptyList())
        searching = false
    }

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 92.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 52.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Search",
                    color = TvDesign.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(.58f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TvDesign.SurfaceRaised)
                        .border(
                            width = if (fieldFocused) 2.dp else 1.dp,
                            color = if (fieldFocused) TvDesign.White else TvDesign.White.copy(alpha = .12f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TvDesign.White,
                            fontSize = 16.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldRequester)
                            .onFocusChanged { fieldFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                ) {
                                    navRequesters.getValue("Search").requestFocus()
                                    true
                                } else false
                            },
                        decorationBox = { inner ->
                            if (query.isBlank()) {
                                Text(
                                    text = "Title, actor or keyword",
                                    color = TvDesign.Dim,
                                    fontSize = 16.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            if (searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TvDesign.White, strokeWidth = 2.dp)
                }
            } else if (query.trim().length >= 2 && results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results", color = TvDesign.Muted, fontSize = 15.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 52.dp,
                        end = 52.dp,
                        top = 24.dp,
                        bottom = 48.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = results,
                        key = { "${it.type}:${it.id}:${it.name}" },
                    ) { media ->
                        SearchCard(media = media, onClick = { onOpenMedia(media) })
                    }
                }
            }
        }

        TvTopBar(
            selected = "Search",
            expanded = true,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {},
            onNavigate = onNavigate,
            onProfile = onProfile,
            onDownFromNav = { runCatching { fieldRequester.requestFocus() }; true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SearchCard(
    media: MediaItem,
    onClick: () -> Unit,
) {
    var focused by remember(media.id, media.type) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}
