package com.vueo.tv.detail

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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

private enum class NuvioPeopleTab { CAST, RELATED, TRAILER }

private val NuvioCastCircle = 100.dp
private val NuvioCastItemWidth = 150.dp
private val NuvioRelatedWidth = 260.dp
private val NuvioRelatedHeight = 146.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun NuvioDetailPeopleSwitcher(
    media: MediaItem,
    cast: List<MediaPerson>,
    related: List<MediaItem>,
    trailerAvailable: Boolean,
    tabsRequester: FocusRequester,
    castContentRequester: FocusRequester,
    relatedContentRequester: FocusRequester,
    trailerContentRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onOpenRelated: (MediaItem) -> Unit,
    onTrailer: () -> Unit,
) {
    val available = remember(cast, related, trailerAvailable) {
        buildList {
            if (cast.isNotEmpty()) add(NuvioPeopleTab.CAST)
            if (related.isNotEmpty()) add(NuvioPeopleTab.RELATED)
            if (trailerAvailable) add(NuvioPeopleTab.TRAILER)
        }
    }
    if (available.isEmpty()) return

    var active by remember(available) { mutableStateOf(available.first()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (available.size > 1) {
            NuvioPeopleTabs(
                active = active,
                available = available,
                sectionRequester = tabsRequester,
                castContentRequester = castContentRequester,
                relatedContentRequester = relatedContentRequester,
                trailerContentRequester = trailerContentRequester,
                upRequester = upRequester,
                onTabFocused = { active = it },
            )
        } else {
            NuvioDetailSectionTitle(
                when (available.first()) {
                    NuvioPeopleTab.CAST -> "Creator and Cast"
                    NuvioPeopleTab.RELATED -> "More like this"
                    NuvioPeopleTab.TRAILER -> "Trailer"
                }
            )
        }

        Crossfade(
            targetState = active,
            animationSpec = tween(160),
            label = "detail39PeopleTab",
        ) { tab ->
            when (tab) {
                NuvioPeopleTab.CAST -> NuvioCastRow(
                    cast = cast,
                    sectionRequester = castContentRequester,
                    upRequester = if (available.size > 1) tabsRequester else upRequester,
                    downRequester = downRequester,
                )

                NuvioPeopleTab.RELATED -> NuvioRelatedRow(
                    items = related,
                    sectionRequester = relatedContentRequester,
                    upRequester = if (available.size > 1) tabsRequester else upRequester,
                    downRequester = downRequester,
                    onOpen = onOpenRelated,
                )

                NuvioPeopleTab.TRAILER -> NuvioTrailerRow(
                    media = media,
                    sectionRequester = trailerContentRequester,
                    upRequester = if (available.size > 1) tabsRequester else upRequester,
                    downRequester = downRequester,
                    onTrailer = onTrailer,
                )
            }
        }
    }
}

@Composable
private fun NuvioPeopleTabs(
    active: NuvioPeopleTab,
    available: List<NuvioPeopleTab>,
    sectionRequester: FocusRequester,
    castContentRequester: FocusRequester,
    relatedContentRequester: FocusRequester,
    trailerContentRequester: FocusRequester,
    upRequester: FocusRequester,
    onTabFocused: (NuvioPeopleTab) -> Unit,
) {
    val extraRequesters = remember(available) {
        available.drop(1).associateWith { FocusRequester() }
    }

    Row(
        modifier = Modifier.padding(horizontal = NuvioDetailHorizontalPadding, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        available.forEachIndexed { index, tab ->
            val requester = if (index == 0) sectionRequester else extraRequesters.getValue(tab)
            var focused by remember(tab) { mutableStateOf(false) }
            val label = when (tab) {
                NuvioPeopleTab.CAST -> "Creator and Cast"
                NuvioPeopleTab.RELATED -> "More like this"
                NuvioPeopleTab.TRAILER -> "Trailer"
            }
            Text(
                text = label,
                color = when {
                    focused -> TvDesign.White
                    active == tab -> TvDesign.White.copy(alpha = .92f)
                    else -> TvDesign.White.copy(alpha = .58f)
                },
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = if (active == tab) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .focusRequester(requester)
                    .focusProperties {
                        up = upRequester
                        down = when (tab) {
                            NuvioPeopleTab.CAST -> castContentRequester
                            NuvioPeopleTab.RELATED -> relatedContentRequester
                            NuvioPeopleTab.TRAILER -> trailerContentRequester
                        }
                    }
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (state.isFocused) onTabFocused(tab)
                    }
                    .clickable { onTabFocused(tab) }
                    .focusable()
                    .padding(vertical = 4.dp),
            )
            if (index < available.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(27.dp)
                        .background(TvDesign.White.copy(alpha = .48f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun NuvioCastRow(
    cast: List<MediaPerson>,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
) {
    val visible = remember(cast) { cast.take(20) }
    val requesters = remember(visible.map { it.name + "|" + it.character.orEmpty() }) {
        visible.indices.associateWith { index -> if (index == 0) sectionRequester else FocusRequester() }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer { sectionRequester }
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = NuvioDetailHorizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(visible, key = { index, person -> "$index:${person.name}:${person.character.orEmpty()}" }) { index, person ->
            NuvioCastMember(
                person = person,
                requester = requesters.getValue(index),
                upRequester = upRequester,
                downRequester = downRequester,
            )
        }
    }
}

@Composable
private fun NuvioCastMember(
    person: MediaPerson,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
) {
    var focused by remember(person.name, person.character) { mutableStateOf(false) }
    val role = person.character?.takeIf(String::isNotBlank)
        ?: person.role?.takeIf(String::isNotBlank)

    Column(
        modifier = Modifier
            .width(NuvioCastItemWidth)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(NuvioCastCircle)
                .clip(CircleShape)
                .background(
                    if (focused) TvDesign.White.copy(alpha = .13f)
                    else TvDesign.SurfaceRaised,
                )
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.Focus else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!person.profile.isNullOrBlank()) {
                TvNetworkImage(
                    url = person.profile,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallback = TvDesign.SurfaceRaised,
                )
            } else {
                Text(
                    text = person.name.firstOrNull()?.uppercase() ?: "?",
                    color = TvDesign.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            text = person.name,
            color = TvDesign.White.copy(alpha = .82f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!role.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = role,
                color = TvDesign.White.copy(alpha = .48f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun NuvioRelatedRow(
    items: List<MediaItem>,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onOpen: (MediaItem) -> Unit,
) {
    val visible = remember(items) { items.take(18) }
    val requesters = remember(visible.map { "${it.type}:${it.id}" }) {
        visible.indices.associateWith { index -> if (index == 0) sectionRequester else FocusRequester() }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer { sectionRequester }
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = NuvioDetailHorizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(visible, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
            NuvioRelatedCard(
                item = item,
                requester = requesters.getValue(index),
                upRequester = upRequester,
                downRequester = downRequester,
                onOpen = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun NuvioRelatedCard(
    item: MediaItem,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onOpen: () -> Unit,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(if (focused) 120 else 90),
        label = "detail39RelatedScale",
    )

    Column(
        modifier = Modifier
            .width(NuvioRelatedWidth)
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
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(NuvioRelatedWidth)
                .height(NuvioRelatedHeight)
                .clip(NuvioDetailCardShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.Focus else Color.Transparent,
                    shape = NuvioDetailCardShape,
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
            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .82f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.releaseInfo?.takeIf(String::isNotBlank)?.let { release ->
            Text(
                text = release,
                color = TvDesign.White.copy(alpha = .42f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NuvioTrailerRow(
    media: MediaItem,
    sectionRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onTrailer: () -> Unit,
) {
    var focused by remember(media.id, media.type) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioDetailHorizontalPadding, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(168.dp)
                .focusRequester(sectionRequester)
                .focusProperties {
                    up = upRequester
                    downRequester?.let { down = it }
                }
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.Focus else Color.Transparent,
                    shape = shape,
                )
                .clickable(onClick = onTrailer),
        ) {
            TvNetworkImage(
                url = media.background ?: media.poster,
                contentDescription = "Trailer",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.SurfaceRaised,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(TvDesign.Black.copy(alpha = .72f))
                    .border(1.dp, TvDesign.White.copy(alpha = .30f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶",
                    color = TvDesign.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Official Trailer",
            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .82f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun NuvioDetailCompanies(
    title: String,
    companies: List<MediaCompany>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 15.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NuvioDetailSectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = NuvioDetailHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                companies.take(14),
                key = { index, company -> "$title:$index:${company.name}:${company.logo.orEmpty()}" },
            ) { _, company ->
                NuvioCompanyCard(company)
            }
        }
    }
}

@Composable
private fun NuvioCompanyCard(company: MediaCompany) {
    var focused by remember(company.name) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(110),
        label = "detail39CompanyScale",
    )

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(70.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvDesign.Focus else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!company.logo.isNullOrBlank()) {
            TvNetworkImage(
                url = company.logo,
                contentDescription = company.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                fallback = Color.White,
            )
        } else {
            Text(
                text = company.name,
                color = Color.Black.copy(alpha = .72f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NuvioDetailInsight(
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
            .padding(horizontal = NuvioDetailHorizontalPadding, vertical = 12.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .12f)
                else TvDesign.Surface.copy(alpha = .86f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.Focus else TvDesign.White.copy(alpha = .08f),
                shape = shape,
            )
            .clickable(enabled = !loading, onClick = onGenerate)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "VUEO Insight",
            color = TvDesign.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        when {
            loading -> Text("Generating…", color = TvDesign.White.copy(alpha = .56f), fontSize = 11.sp)
            !insight.isNullOrBlank() -> Text(
                text = insight,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            !error.isNullOrBlank() -> Text(error, color = Color(0xFFFFB0B0), fontSize = 11.sp)
            else -> Text(
                text = "Press OK to generate a title insight.",
                color = TvDesign.White.copy(alpha = .54f),
                fontSize = 11.sp,
            )
        }
    }
}
