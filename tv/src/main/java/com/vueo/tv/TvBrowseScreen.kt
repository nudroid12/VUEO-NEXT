package com.vueo.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.data.TvBrowseKind
import com.vueo.tv.data.TvBrowseRepository
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BrowseBlack = Color(0xFF050706)
private val BrowsePanel = Color(0xFF101412)
private val BrowseMuted = Color(0xFFAAB2AD)

private object TvBrowseFocusMemory {
    private val indexes = mutableMapOf<TvBrowseKind, Int>()

    fun index(kind: TvBrowseKind): Int = indexes[kind] ?: 0

    fun remember(kind: TvBrowseKind, index: Int) {
        indexes[kind] = index.coerceAtLeast(0)
    }
}

@Composable
fun TvBrowseScreen(
    kind: TvBrowseKind,
    focusRestoreToken: Int,
    onNavigate: (String) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { TvBrowseRepository(context.applicationContext) }
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS.associateWith { FocusRequester() }
        }
    val firstCardRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    var items by remember(kind) { mutableStateOf(repository.cached(kind)) }
    var loading by remember(kind) { mutableStateOf(items.isEmpty()) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var focusedIndex by remember(kind) { mutableIntStateOf(TvBrowseFocusMemory.index(kind)) }

    BackHandler { onNavigate("Home") }

    LaunchedEffect(kind, focusRestoreToken) {
        runCatching { repository.refresh(kind) }
            .onSuccess { fresh ->
                items = fresh
                error = null
            }
            .onFailure { failure ->
                if (items.isEmpty()) {
                    error = failure.message ?: "Unable to load ${kind.title.lowercase()}"
                } else {
                    error = "Showing cached catalog"
                }
            }
        loading = false
    }

    LaunchedEffect(kind, focusRestoreToken, items.size) {
        if (items.isNotEmpty()) {
            delay(110)
            focusedIndex = TvBrowseFocusMemory.index(kind).coerceIn(0, items.lastIndex)
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
                            BrowseBlack,
                            BrowseBlack,
                        )
                    )
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp, end = 58.dp, top = 102.dp),
        ) {
            Text(
                text = kind.title,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = error ?: kind.subtitle,
                color = BrowseMuted,
                fontSize = 15.sp,
            )
        }

        when {
            loading && items.isEmpty() -> {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(start = 58.dp, top = 208.dp)
                            .width(30.dp)
                            .height(30.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }

            items.isEmpty() -> {
                Column(
                    modifier = Modifier.padding(start = 58.dp, top = 210.dp),
                ) {
                    Text(
                        text = "Nothing to show yet",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error ?: "Try again later.",
                        color = BrowseMuted,
                        fontSize = 14.sp,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 178.dp),
                    contentPadding =
                        PaddingValues(
                            start = 58.dp,
                            end = 58.dp,
                            top = 18.dp,
                            bottom = 50.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(27.dp),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> "${item.type}:${item.id}" },
                    ) { index, item ->
                        val focusModifier =
                            if (index == focusedIndex.coerceIn(0, items.lastIndex)) {
                                Modifier.focusRequester(firstCardRequester)
                            } else {
                                Modifier
                            }

                        BrowsePosterCard(
                            item = item,
                            modifier = focusModifier,
                            upRequester = if (index < 5) navRequesters.getValue(kind.navLabel) else null,
                            onFocused = {
                                focusedIndex = index
                                TvBrowseFocusMemory.remember(kind, index)
                                scope.launch {
                                    gridState.animateScrollToItem((index - 5).coerceAtLeast(0))
                                }
                            },
                            onClick = { onOpenMedia(item) },
                        )
                    }
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = firstCardRequester,
            selectedLabel = kind.navLabel,
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun BrowsePosterCard(
    item: TvMediaItem,
    modifier: Modifier,
    upRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.075f else 1f,
        label = "browsePosterScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
        label = "browsePosterBorder",
    )

    Column(
        modifier =
            modifier
                .width(176.dp)
                .scale(scale)
                .then(if (upRequester != null) Modifier.tvVerticalFocus(up = upRequester) else Modifier)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) onFocused()
                }
                .clickable(onClick = onClick)
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(254.dp)
                    .background(BrowsePanel, RoundedCornerShape(12.dp))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    ),
        ) {
            TvNetworkImage(
                url = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.releaseInfo ?: item.displayType,
            color = BrowseMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
