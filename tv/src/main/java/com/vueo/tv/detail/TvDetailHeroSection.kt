package com.vueo.tv.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

private const val Detail38TextWidthFraction = .60f
private val Detail38ActionHeight = 46.dp

@Composable
internal fun TvDetail38Backdrop(
    item: MediaItem,
    imageAlpha: Float,
    scrimAlpha: Float,
) {
    val backdrop = item.background ?: item.poster

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = backdrop,
            animationSpec = tween(320),
            label = "detail38Backdrop",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha },
        ) { url ->
            TvNetworkImage(
                url = url,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.Black,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(
                    Brush.horizontalGradient(
                        0f to TvDesign.Black,
                        .16f to TvDesign.Black.copy(alpha = .97f),
                        .42f to TvDesign.Black.copy(alpha = .76f),
                        .70f to TvDesign.Black.copy(alpha = .20f),
                        1f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .58f to Color.Transparent,
                        .82f to TvDesign.Black.copy(alpha = .66f),
                        1f to TvDesign.Black,
                    )
                ),
        )
    }
}

@Composable
internal fun TvDetail38Hero(
    state: TvDetailUiState,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
) {
    val facts = remember(state.item) { detail38Facts(state.item) }
    val credits = remember(state.item) { detail38Credits(state.item) }
    val canPlay = !state.loading && (!state.item.isTvSeries() || state.selectedEpisode != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(Detail38HeroHeight)
            .padding(
                start = Detail38HorizontalPadding,
                end = Detail38HorizontalPadding,
                bottom = 28.dp,
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = state.item.name,
            color = TvDesign.White,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.58f),
        )

        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = facts.joinToString("  •  "),
                color = TvDesign.White.copy(alpha = .90f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(Detail38TextWidthFraction),
            )
        }

        if (state.item.genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.item.genres.take(4).joinToString("  •  "),
                color = TvDesign.White.copy(alpha = .62f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(Detail38TextWidthFraction),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Detail38PlayAction(
                label = state.primaryActionLabel,
                enabled = canPlay,
                requester = playRequester,
                rightRequester = listRequester,
                downRequester = downRequester,
                onClick = onPlay,
            )
            Detail38ListAction(
                watchlisted = state.watchlisted,
                requester = listRequester,
                leftRequester = playRequester,
                downRequester = downRequester,
                onClick = onToggleList,
            )
        }

        state.playbackEntry?.takeIf(::detailCanResume)?.let { entry ->
            Spacer(Modifier.height(13.dp))
            Row(
                modifier = Modifier.fillMaxWidth(.48f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LinearProgressIndicator(
                    progress = { entry.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = TvDesign.White,
                    trackColor = TvDesign.White.copy(alpha = .17f),
                )
                Text(
                    text = detailRemainingLabel(entry),
                    color = TvDesign.White.copy(alpha = .58f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (!credits.isNullOrBlank()) {
            Text(
                text = credits,
                color = TvDesign.White.copy(alpha = .64f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(Detail38TextWidthFraction),
            )
            Spacer(Modifier.height(10.dp))
        }

        if (state.ratings.isNotEmpty()) {
            Detail38Ratings(state.ratings)
            Spacer(Modifier.height(12.dp))
        }

        state.item.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .78f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(Detail38TextWidthFraction),
            )
        }

        state.dnaMatch?.let { score ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VUEO DNA Match  •  $score%",
                color = TvDesign.White.copy(alpha = .48f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Detail38PlayAction(
    label: String,
    enabled: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .height(Detail38ActionHeight)
            .focusRequester(requester)
            .focusProperties {
                right = rightRequester
                up = FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    !enabled -> TvDesign.White.copy(alpha = .18f)
                    focused -> TvDesign.White
                    else -> TvDesign.White.copy(alpha = .90f)
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.Focus else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) Color.Black else TvDesign.White.copy(alpha = .45f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = if (enabled) Color.Black else TvDesign.White.copy(alpha = .45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun Detail38ListAction(
    watchlisted: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(watchlisted) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .size(Detail38ActionHeight)
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .18f)
                else TvDesign.Black.copy(alpha = .58f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .18f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (watchlisted) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (watchlisted) "Remove from My List" else "Add to My List",
            tint = TvDesign.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Detail38Ratings(ratings: List<MediaRating>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.take(4).forEach { rating ->
            Text(
                text = "${rating.compactLabel} ${rating.displayValue()}",
                color = TvDesign.White.copy(alpha = .72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun detail38Facts(item: MediaItem): List<String> =
    buildList {
        item.releaseInfo?.takeIf(String::isNotBlank)?.let(::add)
        add(item.displayType)
        item.runtimeMinutes?.takeIf { it > 0 }?.let { add(detail38RuntimeLabel(it)) }
        item.certification?.takeIf(String::isNotBlank)?.let(::add)
    }

private fun detail38Credits(item: MediaItem): String? {
    val names = when {
        item.isTvSeries() && item.creators.isNotEmpty() -> "Creator: ${item.creators.take(3).joinToString(", ")}"
        item.directors.isNotEmpty() -> "Director: ${item.directors.take(3).joinToString(", ")}"
        item.writers.isNotEmpty() -> "Writer: ${item.writers.take(3).joinToString(", ")}"
        else -> null
    }
    return names
}

private fun detail38RuntimeLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
