package com.vueo.tv.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.plugin.PluginHttp
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private val NuvioHeroContentWidth = .61f
private val NuvioHeroActionSize = 52.dp

@Composable
internal fun NuvioDetailHero(
    state: TvDetailPresentationState,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    onTrailer: () -> Unit,
) {
    val item = state.item
    val creditLine = remember(item) { nuvioDetailCreditLine(item) }
    val canPlay = !state.loading && (!item.isDetailSeries() || state.selectedEpisode != null)
    val primaryMeta = remember(item, state.ratings, state.nuvioExtras.fullReleaseDate) {
        buildList {
            if (item.genres.isNotEmpty()) add(item.genres.take(4).joinToString(" • "))
            (state.nuvioExtras.fullReleaseDate ?: item.releaseInfo)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            if (state.ratings.isEmpty()) {
                item.imdbRating?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { add("IMDb ${String.format("%.1f", it)}") }
            }
        }
    }
    val secondaryMeta = remember(item, state.nuvioExtras.country, state.nuvioExtras.language) {
        buildList {
            item.runtimeMinutes?.takeIf { it > 0 }?.let { add(nuvioDetailRuntime(it)) }
            state.nuvioExtras.country?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            (state.nuvioExtras.language ?: item.originalLanguage)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.uppercase()
                ?.let(::add)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(NuvioDetailHeroHeight)
            .padding(
                start = NuvioDetailHorizontalPadding,
                end = NuvioDetailHorizontalPadding,
                bottom = 34.dp,
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        NuvioHeroTitle(
            title = item.name,
            logoUrl = state.nuvioExtras.logo,
        )

        Spacer(Modifier.height(18.dp))

        val watchedRequester = remember(item.id, item.type) { FocusRequester() }
        val trailerRequester = remember(item.id, item.type) { FocusRequester() }
        val showWatched = !item.isDetailSeries()
        val showTrailer = !state.nuvioExtras.trailerUrl.isNullOrBlank()

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NuvioPlayButton(
                label = state.primaryActionLabel,
                enabled = canPlay,
                requester = playRequester,
                rightRequester = listRequester,
                downRequester = downRequester,
                onClick = onPlay,
            )
            NuvioCircleAction(
                icon = if (state.watchlisted) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (state.watchlisted) "Remove from My List" else "Add to My List",
                selected = state.watchlisted,
                requester = listRequester,
                leftRequester = playRequester,
                rightRequester = when {
                    showWatched -> watchedRequester
                    showTrailer -> trailerRequester
                    else -> null
                },
                downRequester = downRequester,
                onClick = onToggleList,
            )
            if (showWatched) {
                NuvioCircleAction(
                    icon = if (state.movieWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (state.movieWatched) "Mark unwatched" else "Mark watched",
                    selected = state.movieWatched,
                    requester = watchedRequester,
                    leftRequester = listRequester,
                    rightRequester = if (showTrailer) trailerRequester else null,
                    downRequester = downRequester,
                    onClick = onToggleWatched,
                )
            }
            if (showTrailer) {
                NuvioCircleAction(
                    icon = Icons.Default.SmartDisplay,
                    contentDescription = "Play trailer",
                    selected = false,
                    requester = trailerRequester,
                    leftRequester = if (showWatched) watchedRequester else listRequester,
                    rightRequester = null,
                    downRequester = downRequester,
                    onClick = onTrailer,
                )
            }
        }

        Spacer(Modifier.height(17.dp))

        if (!creditLine.isNullOrBlank()) {
            Text(
                text = creditLine,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(NuvioHeroContentWidth),
            )
            Spacer(Modifier.height(11.dp))
        }

        if (state.ratings.isNotEmpty()) {
            NuvioRatingsRow(state.ratings)
            Spacer(Modifier.height(12.dp))
        }

        item.description?.trim()?.takeIf(String::isNotBlank)?.let { synopsis ->
            Text(
                text = synopsis,
                color = TvDesign.White.copy(alpha = .92f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(NuvioHeroContentWidth),
            )
            Spacer(Modifier.height(11.dp))
        }

        if (primaryMeta.isNotEmpty()) {
            NuvioMetaTextRow(primaryMeta, fontSize = 13)
            Spacer(Modifier.height(9.dp))
        }

        val certification = item.certification?.trim()?.takeIf(String::isNotBlank)
        val status = state.nuvioExtras.status?.trim()?.takeIf(String::isNotBlank)
        if (certification != null || status != null || secondaryMeta.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    certification != null && status != null -> NuvioCombinedMetaBadge(certification, status)
                    certification != null -> NuvioMetaBadge(certification)
                    status != null -> NuvioMetaBadge(status)
                }
                if ((certification != null || status != null) && secondaryMeta.isNotEmpty()) {
                    NuvioMetaDot()
                }
                secondaryMeta.forEachIndexed { index, value ->
                    Text(
                        text = value,
                        color = TvDesign.White.copy(alpha = .88f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (index < secondaryMeta.lastIndex) NuvioMetaDot()
                }
            }
        }
    }
}

@Composable
private fun NuvioHeroTitle(
    title: String,
    logoUrl: String?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = logoUrl) {
        value = if (logoUrl.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(logoUrl)
                        .header("User-Agent", "VUEO/0.9.6")
                        .build()
                    PluginHttp.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body.byteStream().use(BitmapFactory::decodeStream)
                    }
                }.getOrNull()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = title,
            modifier = Modifier
                .height(100.dp)
                .fillMaxWidth(.40f),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
        )
    } else {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 62.sp,
            lineHeight = 66.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.53f),
        )
    }
}

@Composable
private fun NuvioPlayButton(
    label: String,
    enabled: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(30.dp)

    Row(
        modifier = Modifier
            .height(NuvioHeroActionSize)
            .focusRequester(requester)
            .focusProperties {
                up = FocusRequester.Cancel
                right = rightRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    !enabled -> TvDesign.White.copy(alpha = .22f)
                    else -> TvDesign.White
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.Focus else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) Color.Black else TvDesign.White.copy(alpha = .48f),
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            color = if (enabled) Color.Black else TvDesign.White.copy(alpha = .48f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun NuvioCircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(contentDescription, selected) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(NuvioHeroActionSize)
            .focusRequester(requester)
            .focusProperties {
                up = FocusRequester.Cancel
                left = leftRequester
                right = rightRequester ?: FocusRequester.Cancel
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                when {
                    selected && focused -> TvDesign.White
                    selected -> TvDesign.White.copy(alpha = .92f)
                    focused -> TvDesign.White.copy(alpha = .18f)
                    else -> TvDesign.SurfaceRaised.copy(alpha = .82f)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .13f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Color.Black else TvDesign.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun NuvioRatingsRow(ratings: List<MediaRating>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings
            .distinctBy(MediaRating::source)
            .sortedBy { rating ->
                when (rating.source.lowercase()) {
                    "trakt" -> 0
                    "imdb" -> 1
                    "tmdb" -> 2
                    "tomatoes" -> 3
                    "metacritic" -> 4
                    else -> 9
                }
            }
            .take(6)
            .forEach { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvioRatingMark(rating.source)
                    Text(
                        text = nuvioRatingDisplayValue(rating),
                        color = TvDesign.White.copy(alpha = .78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
    }
}

private fun nuvioRatingDisplayValue(rating: MediaRating): String {
    if (rating.source.equals("tmdb", ignoreCase = true)) {
        rating.score?.takeIf { it.isFinite() }?.let { score ->
            return String.format("%.1f", score)
        }
    }
    return rating.displayValue().removeSuffix("%")
}

@Composable
private fun NuvioRatingMark(source: String) {
    val normalized = source.lowercase()
    val (label, background, foreground) = when (normalized) {
        "imdb" -> Triple("IMDb", Color(0xFFF5C518), Color.Black)
        "tmdb" -> Triple("TMDB", Color(0xFF0D253F), Color(0xFF90CEA1))
        "tomatoes" -> Triple("RT", Color(0xFFFA320A), Color.White)
        "metacritic" -> Triple("M", Color(0xFFFFCC34), Color.Black)
        "trakt" -> Triple("T", Color(0xFFED1C24), Color.White)
        else -> Triple(source.take(4).uppercase(), TvDesign.White.copy(alpha = .16f), TvDesign.White)
    }

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = if (label.length > 2) 5.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = if (label.length > 3) 8.sp else 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun NuvioMetaTextRow(values: List<String>, fontSize: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            Text(
                text = value,
                color = TvDesign.White.copy(alpha = .70f),
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index < values.lastIndex) NuvioMetaDot()
        }
    }
}

@Composable
private fun NuvioMetaBadge(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = TvDesign.White.copy(alpha = .55f),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TvDesign.White.copy(alpha = .92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NuvioCombinedMetaBadge(
    left: String,
    right: String,
) {
    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = TvDesign.White.copy(alpha = .55f),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, color = TvDesign.White.copy(alpha = .72f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.width(1.dp).height(13.dp).background(TvDesign.White.copy(alpha = .42f)))
        Text(right, color = TvDesign.White.copy(alpha = .94f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NuvioMetaDot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(TvDesign.White.copy(alpha = .58f)),
    )
}

private fun nuvioDetailCreditLine(item: MediaItem): String? {
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
