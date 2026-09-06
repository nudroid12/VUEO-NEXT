package com.vueo.tv.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

private val RelatedWidth = 260.dp
private val RelatedImageHeight = 146.dp
private val RelatedShape = RoundedCornerShape(12.dp)

@Composable
internal fun TvDetailCastRow(cast: List<MediaPerson>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvDetailSectionTitle("Cast")
        LazyRow(
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cast.take(14), key = { "${it.name}:${it.character.orEmpty()}" }) { person ->
                Column(
                    modifier = Modifier.width(122.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(TvDesign.SurfaceRaised),
                    ) {
                        TvNetworkImage(
                            url = person.profile,
                            contentDescription = person.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            fallback = TvDesign.SurfaceRaised,
                        )
                    }
                    Text(
                        text = person.name,
                        color = TvDesign.White.copy(alpha = .90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val role = person.character?.takeIf(String::isNotBlank)
                        ?: person.role?.takeIf(String::isNotBlank)
                    role?.let {
                        Text(
                            text = it,
                            color = TvDesign.White.copy(alpha = .46f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvDetailCompanyRow(
    title: String,
    companies: List<MediaCompany>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvDetailSectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(companies.take(12), key = MediaCompany::name) { company ->
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TvDesign.White.copy(alpha = .92f))
                        .padding(13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!company.logo.isNullOrBlank()) {
                        TvNetworkImage(
                            url = company.logo,
                            contentDescription = company.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            fallback = Color.Transparent,
                        )
                    } else {
                        Text(
                            text = company.name,
                            color = Color.Black.copy(alpha = .76f),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailRelatedRow(
    items: List<MediaItem>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onOpen: (MediaItem) -> Unit,
) {
    val visibleItems = remember(items) { items.take(18) }
    val safeIndex = TvDetailFocusMemory.relatedIndex.coerceIn(0, visibleItems.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val requesters = remember(visibleItems.map { "${it.type}:${it.id}" }) {
        mutableMapOf<Int, FocusRequester>()
    }
    val fallbackRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetailSectionTitle("More Like This")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowRequester)
                .focusRestorer {
                    requesters[TvDetailFocusMemory.relatedIndex.coerceIn(0, visibleItems.lastIndex)]
                        ?: fallbackRequester
                }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = visibleItems,
                key = { _, item -> "${item.type}:${item.id}" },
            ) { index, item ->
                TvDetailRelatedCard(
                    item = item,
                    requester = requesters.getOrPut(index) { FocusRequester() },
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = { TvDetailFocusMemory.relatedIndex = index },
                    onOpen = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun TvDetailRelatedCard(
    item: MediaItem,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(if (focused) 120 else 90),
        label = "detailRelatedScale",
    )

    Column(
        modifier = Modifier
            .width(RelatedWidth)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { state ->
                val becameFocused = state.isFocused
                if (becameFocused && !focused) onFocused()
                focused = becameFocused
            }
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .width(RelatedWidth)
                .height(RelatedImageHeight)
                .clip(RelatedShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White else Color.Transparent,
                    shape = RelatedShape,
                ),
        ) {
            TvNetworkImage(
                url = item.background ?: item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.SurfaceRaised,
            )
        }
        Text(
            text = item.name,
            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .84f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.releaseInfo?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                color = TvDesign.White.copy(alpha = .42f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TvDetailInsight(
    insight: String?,
    loading: Boolean,
    error: String?,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onGenerate: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailHorizontalPadding, vertical = 8.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .13f)
                else TvDesign.Surface.copy(alpha = .84f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .09f),
                shape = shape,
            )
            .clickable(enabled = !loading, onClick = onGenerate)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "VUEO Insight",
            color = TvDesign.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            loading -> Text("Generating…", color = TvDesign.White.copy(alpha = .55f), fontSize = 11.sp)
            !insight.isNullOrBlank() -> Text(
                text = insight,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            !error.isNullOrBlank() -> Text(error, color = Color(0xFFFFB0B0), fontSize = 11.sp)
            else -> Text(
                text = "Press OK to generate a title insight.",
                color = TvDesign.White.copy(alpha = .55f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun TvDetailSectionTitle(title: String) {
    Text(
        text = title,
        color = TvDesign.White.copy(alpha = .94f),
        fontSize = 19.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = DetailHorizontalPadding),
    )
}

@Composable
internal fun TvDetailMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailHorizontalPadding)
            .background(TvDesign.Surface.copy(alpha = .82f), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = message,
            color = TvDesign.White.copy(alpha = .58f),
            fontSize = 12.sp,
        )
    }
}
