package com.vueo.tv.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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

private val Detail38RelatedWidth = 260.dp
private val Detail38RelatedHeight = 146.dp
private val Detail38RelatedShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail38CastRail(
    cast: List<MediaPerson>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
) {
    val visible = remember(cast) { cast.take(18) }
    val requesters = remember(visible.map { it.name + "|" + it.character.orEmpty() }) {
        visible.indices.associateWith { index -> if (index == 0) rowRequester else FocusRequester() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetail38SectionTitle("Cast")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { rowRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Detail38HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(visible, key = { index, person -> "$index:${person.name}:${person.character.orEmpty()}" }) { index, person ->
                var focused by remember(index, person.name) { mutableStateOf(false) }
                val role = person.character?.takeIf(String::isNotBlank)
                    ?: person.role?.takeIf(String::isNotBlank)

                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .focusRequester(requesters.getValue(index))
                        .focusProperties {
                            up = upRequester
                            downRequester?.let { down = it }
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .focusable(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(TvDesign.SurfaceRaised)
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) TvDesign.Focus else Color.Transparent,
                                shape = CircleShape,
                            ),
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
                        color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .84f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    role?.let {
                        Text(
                            text = it,
                            color = TvDesign.White.copy(alpha = .44f),
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetail38CompanyRail(
    title: String,
    companies: List<MediaCompany>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
) {
    val visible = remember(companies) { companies.take(12) }
    val requesters = remember(visible.map(MediaCompany::name)) {
        visible.indices.associateWith { index -> if (index == 0) rowRequester else FocusRequester() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetail38SectionTitle(title)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { rowRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Detail38HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(visible, key = { index, company -> "$index:${company.name}" }) { index, company ->
                var focused by remember(index, company.name) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(70.dp)
                        .focusRequester(requesters.getValue(index))
                        .focusProperties {
                            up = upRequester
                            downRequester?.let { down = it }
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TvDesign.White.copy(alpha = .92f))
                        .border(
                            width = if (focused) 2.dp else 0.dp,
                            color = if (focused) TvDesign.Focus else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
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
internal fun TvDetail38RelatedRail(
    items: List<MediaItem>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onOpen: (MediaItem) -> Unit,
) {
    val visible = remember(items) { items.take(18) }
    val rememberedIndex = TvDetailSessionMemory.relatedIndex.coerceIn(0, visible.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)
    val requesters = remember(visible.map { "${it.type}:${it.id}" }, rememberedIndex) {
        visible.indices.associateWith { index -> if (index == rememberedIndex) rowRequester else FocusRequester() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDetail38SectionTitle("More Like This")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { requesters[TvDetailSessionMemory.relatedIndex.coerceIn(0, visible.lastIndex)] ?: rowRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Detail38HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = visible,
                key = { _, item -> "${item.type}:${item.id}" },
            ) { index, item ->
                TvDetail38RelatedCard(
                    item = item,
                    requester = requesters.getValue(index),
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = { TvDetailSessionMemory.relatedIndex = index },
                    onOpen = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun TvDetail38RelatedCard(
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
        label = "detail38RelatedScale",
    )

    Column(
        modifier = Modifier
            .width(Detail38RelatedWidth)
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
                .width(Detail38RelatedWidth)
                .height(Detail38RelatedHeight)
                .clip(Detail38RelatedShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.Focus else Color.Transparent,
                    shape = Detail38RelatedShape,
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
internal fun TvDetail38Insight(
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
            .padding(horizontal = Detail38HorizontalPadding, vertical = 8.dp)
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
                color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .09f),
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
internal fun TvDetail38SectionTitle(title: String) {
    Text(
        text = title,
        color = TvDesign.White.copy(alpha = .94f),
        fontSize = 19.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = Detail38HorizontalPadding),
    )
}

@Composable
internal fun TvDetail38Message(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Detail38HorizontalPadding)
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
