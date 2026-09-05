package com.vueo.tv.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

@Composable
fun TvDetailScreen(
    runtime: TvRuntime,
    initial: MediaItem,
    onBack: () -> Unit,
    onWatch: (MediaItem, EpisodeItem?) -> Unit,
    onLibraryChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var item by remember(initial.id, initial.type) { mutableStateOf(initial) }
    var loading by remember(initial.id, initial.type) { mutableStateOf(true) }
    var watchlisted by remember(initial.id, initial.type) {
        mutableStateOf(runtime.libraryStore.isWatchlisted(initial))
    }

    LaunchedEffect(initial.id, initial.type) {
        loading = true
        item = runCatching { runtime.loadMeta(initial) }.getOrDefault(initial)
        watchlisted = runtime.libraryStore.isWatchlisted(item)
        loading = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        TvNetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvDesign.Black,
                            TvDesign.Black.copy(alpha = .92f),
                            TvDesign.Black.copy(alpha = .38f),
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TvDesign.Black.copy(alpha = .22f),
                            Color.Transparent,
                            TvDesign.Black.copy(alpha = .92f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(.62f)
                .padding(start = 58.dp, top = 92.dp, bottom = 38.dp),
        ) {
            Text(
                text = item.name,
                color = TvDesign.White,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = detailMeta(item),
                color = TvDesign.White.copy(alpha = .72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(15.dp))
            item.description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = TvDesign.Muted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailButton(
                    text = "Watch",
                    primary = true,
                    onClick = {
                        val firstEpisode =
                            if (item.type.lowercase() in setOf("series", "tv")) {
                                item.episodes.firstOrNull()
                            } else null
                        onWatch(item, firstEpisode)
                    },
                )
                DetailButton(
                    text = if (watchlisted) "✓ My List" else "+ My List",
                    primary = false,
                    onClick = {
                        watchlisted = runtime.libraryStore.toggleWatchlist(item)
                        onLibraryChanged()
                    },
                )
            }

            if (loading) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(
                    modifier = Modifier.width(22.dp).height(22.dp),
                    color = TvDesign.White,
                    strokeWidth = 2.dp,
                )
            }

            if (item.episodes.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Episodes",
                    color = TvDesign.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(item.episodes, key = { it.id }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onWatch(item, episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (primary) TvDesign.White.copy(alpha = if (focused) 1f else .94f)
                else TvDesign.White.copy(alpha = if (focused) .20f else .10f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (primary) Color.Transparent
                else if (focused) TvDesign.White else TvDesign.White.copy(alpha = .12f),
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = if (primary) Color.Black else TvDesign.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    onClick: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            TvNetworkImage(
                url = episode.thumbnail,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = "S${episode.season}  E${episode.episode}",
                color = TvDesign.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(topEnd = 7.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = episode.title,
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun detailMeta(item: MediaItem): String =
    listOfNotNull(
        item.releaseInfo?.takeIf(String::isNotBlank),
        item.displayType,
        item.certification?.takeIf(String::isNotBlank),
        item.imdbRating?.let { "IMDb %.1f".format(it) },
        item.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
        item.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" / "),
    ).joinToString("  •  ")
