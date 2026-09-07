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

private const val Detail39TextWidthFraction = .60f
private val Detail39ActionHeight = 48.dp
private val Detail39PlayShape = RoundedCornerShape(28.dp)

/** Nuvio-style sticky backdrop: long left fade + bottom fade beginning around 38%. */
@Composable
internal fun TvDetail39Backdrop(
    item: MediaItem,
    imageAlpha: Float,
    scrimAlpha: Float,
) {
    val backdrop = item.background ?: item.poster

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = backdrop,
            animationSpec = tween(400),
            label = "detail39Backdrop",
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
                        // Nuvio's shader reaches transparent at ~78% of screen width.
                        0.000f to TvDesign.Black,
                        0.078f to TvDesign.Black.copy(alpha = .95f),
                        0.172f to TvDesign.Black.copy(alpha = .84f),
                        0.281f to TvDesign.Black.copy(alpha = .70f),
                        0.406f to TvDesign.Black.copy(alpha = .52f),
                        0.515f to TvDesign.Black.copy(alpha = .34f),
                        0.608f to TvDesign.Black.copy(alpha = .18f),
                        0.702f to TvDesign.Black.copy(alpha = .07f),
                        0.780f to Color.Transparent,
                        1.000f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        // Nuvio bottom shader starts at 38% and ramps across the remaining height.
                        0.000f to Color.Transparent,
                        0.380f to Color.Transparent,
                        0.442f to TvDesign.Black.copy(alpha = .05f),
                        0.516f to TvDesign.Black.copy(alpha = .18f),
                        0.603f to TvDesign.Black.copy(alpha = .38f),
                        0.702f to TvDesign.Black.copy(alpha = .60f),
                        0.789f to TvDesign.Black.copy(alpha = .78f),
                        0.864f to TvDesign.Black.copy(alpha = .91f),
                        0.938f to TvDesign.Black.copy(alpha = .97f),
                        1.000f to TvDesign.Black,
                    )
                ),
        )
    }
}

/**
 * Mirrors Nuvio HeroContentSection order instead of the old VUEO Details order:
 * title -> actions -> credit -> ratings -> synopsis -> meta row.
 */
@Composable
internal fun TvDetail39Hero(
    state: TvDetailUiState,
    playRequester: FocusRequester,
    libraryRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
) {
    val creditLine = remember(state.item) { detail39CreditLine(state.item) }
    val metaFacts = remember(state.item, state.dnaMatch) { detail39MetaFacts(state.item, state.dnaMatch) }
    val canPlay = !state.loading && (!state.item.isTvSeries() || state.selectedEpisode != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(Detail39HeroHeight),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Detail39HorizontalPadding,
                    end = Detail39HorizontalPadding,
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = state.item.name,
                color = TvDesign.White,
                fontSize = 46.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.58f),
            )

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Detail39PlayButton(
                    label = state.primaryActionLabel,
                    enabled = canPlay,
                    requester = playRequester,
                    rightRequester = libraryRequester,
                    downRequester = downRequester,
                    onClick = onPlay,
                )
                Detail39LibraryButton(
                    watchlisted = state.watchlisted,
                    requester = libraryRequester,
                    leftRequester = playRequester,
                    downRequester = downRequester,
                    onClick = onToggleList,
                )
            }

            state.playbackEntry?.takeIf(::detailCanResume)?.let { entry ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(.46f),
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
                        trackColor = TvDesign.White.copy(alpha = .18f),
                    )
                    Text(
                        text = detailRemainingLabel(entry),
                        color = TvDesign.White.copy(alpha = .60f),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!creditLine.isNullOrBlank()) {
                Text(
                    text = creditLine,
                    color = TvDesign.White.copy(alpha = .66f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(Detail39TextWidthFraction),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (state.ratings.isNotEmpty()) {
                Detail39Ratings(state.ratings)
                Spacer(Modifier.height(14.dp))
            }

            state.item.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    color = TvDesign.White.copy(alpha = .84f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(Detail39TextWidthFraction),
                )
                Spacer(Modifier.height(14.dp))
            }

            if (metaFacts.isNotEmpty()) {
                Text(
                    text = metaFacts.joinToString("  •  "),
                    color = TvDesign.White.copy(alpha = .60f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(Detail39TextWidthFraction),
                )
            }
        }
    }
}

@Composable
private fun Detail39PlayButton(
    label: String,
    enabled: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(Detail39ActionHeight)
            .focusRequester(requester)
            .focusProperties {
                right = rightRequester
                up = FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(Detail39PlayShape)
            .background(
                if (enabled) TvDesign.White else TvDesign.White.copy(alpha = .20f)
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.Focus else Color.Transparent,
                shape = Detail39PlayShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) Color.Black else TvDesign.White.copy(alpha = .48f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = if (enabled) Color.Black else TvDesign.White.copy(alpha = .48f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun Detail39LibraryButton(
    watchlisted: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(watchlisted) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(Detail39ActionHeight)
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (focused) TvDesign.White else TvDesign.Surface.copy(alpha = .88f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .14f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (watchlisted) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (watchlisted) "Remove from My List" else "Add to My List",
            tint = if (focused) Color.Black else TvDesign.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun Detail39Ratings(ratings: List<MediaRating>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.take(4).forEach { rating ->
            Text(
                text = "${rating.compactLabel} ${rating.displayValue()}",
                color = TvDesign.White.copy(alpha = .78f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun detail39MetaFacts(item: MediaItem, dnaMatch: Int?): List<String> =
    buildList {
        item.releaseInfo?.takeIf(String::isNotBlank)?.let(::add)
        add(item.displayType)
        item.runtimeMinutes?.takeIf { it > 0 }?.let { add(detail39RuntimeLabel(it)) }
        item.certification?.takeIf(String::isNotBlank)?.let(::add)
        item.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let(::add)
        dnaMatch?.let { add("VUEO DNA $it%") }
    }

private fun detail39CreditLine(item: MediaItem): String? =
    when {
        item.isTvSeries() && item.creators.isNotEmpty() -> "Creator: ${item.creators.take(3).joinToString(", ")}"
        item.directors.isNotEmpty() -> "Director: ${item.directors.take(3).joinToString(", ")}"
        item.writers.isNotEmpty() -> "Writer: ${item.writers.take(3).joinToString(", ")}"
        else -> null
    }

private fun detail39RuntimeLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
