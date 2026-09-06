package com.vueo.tv.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

private const val DetailHeroTextWidthFraction = .60f
private val DetailPrimaryActionHeight = 46.dp

@Composable
internal fun TvDetailBackdrop(
    item: MediaItem,
    imageAlpha: Float,
    gradientAlpha: Float,
) {
    val backdrop = item.background ?: item.poster

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = backdrop,
            animationSpec = tween(300),
            label = "detail36Backdrop",
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
                .graphicsLayer { alpha = gradientAlpha }
                .background(
                    Brush.horizontalGradient(
                        0f to TvDesign.Black,
                        .18f to TvDesign.Black.copy(alpha = .96f),
                        .44f to TvDesign.Black.copy(alpha = .72f),
                        .70f to TvDesign.Black.copy(alpha = .22f),
                        1f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .58f to Color.Transparent,
                        .82f to TvDesign.Black.copy(alpha = .62f),
                        1f to TvDesign.Black,
                    )
                ),
        )
    }
}

@Composable
internal fun TvDetailHero(
    state: TvDetailPresentationState,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
) {
    val facts = remember(state.item) { detailHeroFacts(state.item) }
    val creditLine = remember(state.item) { detailHeroCreditLine(state.item) }
    val canPlay = !state.loading && (!state.item.isDetailSeries() || state.selectedEpisode != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(DetailHeroHeight)
            .padding(
                start = DetailHorizontalPadding,
                end = DetailHorizontalPadding,
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
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidthFraction),
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
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidthFraction),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailPlayAction(
                label = state.primaryActionLabel,
                enabled = canPlay,
                requester = playRequester,
                rightRequester = listRequester,
                downRequester = downRequester,
                onClick = onPlay,
            )
            DetailListAction(
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

        if (!creditLine.isNullOrBlank()) {
            Text(
                text = creditLine,
                color = TvDesign.White.copy(alpha = .64f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidthFraction),
            )
            Spacer(Modifier.height(10.dp))
        }

        if (state.ratings.isNotEmpty()) {
            DetailHeroRatings(state.ratings)
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
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidthFraction),
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
private fun DetailPlayAction(
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
            .height(DetailPrimaryActionHeight)
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
                    !enabled -> TvDesign.White.copy(alpha = .10f)
                    focused -> TvDesign.White
                    else -> TvDesign.White.copy(alpha = .92f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) Color.Black else TvDesign.White.copy(alpha = .35f),
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            color = if (enabled) Color.Black else TvDesign.White.copy(alpha = .35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailListAction(
    watchlisted: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(watchlisted) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(DetailPrimaryActionHeight)
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                up = FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (focused) TvDesign.White
                else TvDesign.Black.copy(alpha = .58f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (watchlisted) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (watchlisted) "Remove from My List" else "Add to My List",
            tint = if (focused) Color.Black else TvDesign.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DetailHeroRatings(ratings: List<MediaRating>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.take(4).forEach { rating ->
            Row(
                modifier = Modifier
                    .background(
                        TvDesign.Black.copy(alpha = .44f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rating.compactLabel,
                    color = TvDesign.White.copy(alpha = .66f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rating.displayValue(),
                    color = TvDesign.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun detailHeroFacts(item: MediaItem): List<String> {
    val seasonCount = if (item.isDetailSeries()) {
        item.episodes.map(EpisodeItem::season).filter { it > 0 }.distinct().size.takeIf { it > 0 }
    } else {
        null
    }
    return listOfNotNull(
        item.releaseInfo
            ?.trim()
            ?.trimEnd { it.isWhitespace() || it == '-' || it == '–' || it == '—' }
            ?.trim()
            ?.takeIf(String::isNotBlank),
        seasonCount?.let { if (it == 1) "1 Season" else "$it Seasons" },
        item.runtimeMinutes?.takeIf { it > 0 }?.let(::detailRuntimeLabel),
        item.certification?.takeIf(String::isNotBlank),
    )
}

private fun detailHeroCreditLine(item: MediaItem): String? {
    val creators = item.creators.take(3).joinToString(", ")
    val directors = item.directors.take(3).joinToString(", ")
    val writers = item.writers.take(3).joinToString(", ")
    return when {
        item.isDetailSeries() && creators.isNotBlank() -> "Creator: $creators"
        directors.isNotBlank() -> "Director: $directors"
        writers.isNotBlank() -> "Writer: $writers"
        else -> null
    }
}

private fun detailRuntimeLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
