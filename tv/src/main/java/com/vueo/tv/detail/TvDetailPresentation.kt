package com.vueo.tv.detail

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay

private val DetailHorizontalPadding = 52.dp
private val DetailHeroHeight = 540.dp
private val DetailHeroTextWidth = .60f
private val DetailActionHeight = 46.dp
private val DetailEpisodeWidth = 278.dp
private val DetailEpisodeImageHeight = 156.dp
private val DetailRelatedWidth = 260.dp
private val DetailRelatedHeight = 146.dp
private val DetailShape = RoundedCornerShape(12.dp)
private const val DetailCardFocusScale = 1.02f

private object TvDetailFocusMemory {
    var mediaKey: String? = null
    var episodeId: String? = null
    var relatedIndex: Int = 0
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvDetailPresentation(
    state: TvDetailPresentationState,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeFocused: (EpisodeItem) -> Unit,
    onEpisodeSelected: (EpisodeItem) -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
    onGenerateInsight: () -> Unit,
) {
    val mediaKey = "${state.item.type}:${state.item.id}"
    val playRequester = remember(mediaKey) { FocusRequester() }
    val listRequester = remember(mediaKey) { FocusRequester() }
    val selectedSeasonRequester = remember(mediaKey) { FocusRequester() }
    val episodeRowRequester = remember(mediaKey) { FocusRequester() }
    val relatedRowRequester = remember(mediaKey) { FocusRequester() }

    LaunchedEffect(mediaKey) {
        if (TvDetailFocusMemory.mediaKey != mediaKey) {
            TvDetailFocusMemory.mediaKey = mediaKey
            TvDetailFocusMemory.episodeId = null
            TvDetailFocusMemory.relatedIndex = 0
        }
    }

    LaunchedEffect(mediaKey, state.loading) {
        if (!state.loading) {
            delay(120)
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        DetailStickyBackdrop(state.item)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "detail-hero") {
                DetailModernHero(
                    state = state,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = when {
                        state.seasons.isNotEmpty() -> selectedSeasonRequester
                        state.episodes.isNotEmpty() -> episodeRowRequester
                        state.related.isNotEmpty() -> relatedRowRequester
                        else -> null
                    },
                    onPlay = onPlay,
                    onToggleList = onToggleList,
                )
            }

            if (state.item.isDetailSeries() && state.seasons.isNotEmpty()) {
                item(key = "detail-season-tabs") {
                    DetailSeasonTabs(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        selectedRequester = selectedSeasonRequester,
                        upRequester = playRequester,
                        downRequester = if (state.episodes.isNotEmpty()) episodeRowRequester else null,
                        onSelect = onSeasonSelected,
                    )
                }
            }

            if (state.item.isDetailSeries() && state.episodes.isNotEmpty()) {
                item(key = "detail-episodes:${state.selectedSeason ?: 0}") {
                    DetailEpisodesRow(
                        media = state.item,
                        episodes = state.episodes,
                        history = state.history,
                        selectedEpisode = state.selectedEpisode,
                        rowRequester = episodeRowRequester,
                        upRequester = if (state.seasons.isNotEmpty()) selectedSeasonRequester else playRequester,
                        downRequester = if (state.related.isNotEmpty()) relatedRowRequester else null,
                        onFocused = { episode ->
                            TvDetailFocusMemory.episodeId = episode.id
                            onEpisodeFocused(episode)
                        },
                        onOpen = onEpisodeSelected,
                    )
                }
            } else if (state.item.isDetailSeries() && !state.loading && state.item.episodes.isEmpty()) {
                item(key = "detail-no-episodes") {
                    DetailMessage("Episodes are not available for this title yet.")
                }
            }

            if (state.item.cast.isNotEmpty()) {
                item(key = "detail-cast") {
                    DetailCastSection(state.item.cast)
                }
            }

            val companies = if (state.item.isDetailSeries()) state.item.networks else state.item.productionCompanies
            if (companies.isNotEmpty()) {
                item(key = "detail-companies") {
                    DetailCompanySection(
                        title = if (state.item.isDetailSeries()) {
                            if (companies.size == 1) "Network" else "Networks"
                        } else {
                            "Production"
                        },
                        companies = companies,
                    )
                }
            }

            if (state.related.isNotEmpty()) {
                item(key = "detail-related") {
                    DetailRelatedSection(
                        items = state.related,
                        rowRequester = relatedRowRequester,
                        upRequester = when {
                            state.episodes.isNotEmpty() -> episodeRowRequester
                            state.seasons.isNotEmpty() -> selectedSeasonRequester
                            else -> playRequester
                        },
                        onOpen = onOpenRelated,
                    )
                }
            }

            if (state.insightAvailable) {
                item(key = "detail-insight") {
                    DetailInsightCard(
                        insight = state.insight,
                        loading = state.insightLoading,
                        error = state.insightError,
                        onGenerate = onGenerateInsight,
                    )
                }
            }
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 30.dp, end = 38.dp)
                    .size(22.dp),
                color = TvDesign.White,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun DetailStickyBackdrop(item: MediaItem) {
    val backdrop = item.background ?: item.poster
    Crossfade(
        targetState = backdrop,
        animationSpec = tween(260),
        label = "detailBackdropFade",
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
            .background(
                Brush.horizontalGradient(
                    0f to TvDesign.Black.copy(alpha = .98f),
                    .30f to TvDesign.Black.copy(alpha = .90f),
                    .60f to TvDesign.Black.copy(alpha = .38f),
                    1f to Color.Transparent,
                )
            )
            .background(
                Brush.verticalGradient(
                    0f to TvDesign.Black.copy(alpha = .05f),
                    .48f to Color.Transparent,
                    .78f to TvDesign.Black.copy(alpha = .72f),
                    1f to TvDesign.Black,
                )
            ),
    )
}

@Composable
private fun DetailModernHero(
    state: TvDetailPresentationState,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
) {
    val facts = remember(state.item) { detailFacts(state.item) }
    val creditLine = remember(state.item) { detailCreditLine(state.item) }
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
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.58f),
        )

        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = facts.joinToString("  •  "),
                color = TvDesign.White.copy(alpha = .84f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidth),
            )
        }

        if (state.item.genres.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = state.item.genres.take(4).joinToString("  •  "),
                color = TvDesign.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidth),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailPrimaryAction(
                label = state.primaryActionLabel,
                enabled = canPlay,
                requester = playRequester,
                rightRequester = listRequester,
                downRequester = downRequester,
                onClick = onPlay,
            )
            DetailSquareAction(
                label = if (state.watchlisted) "✓" else "+",
                contentLabel = if (state.watchlisted) "In My List" else "Add to My List",
                requester = listRequester,
                leftRequester = playRequester,
                downRequester = downRequester,
                onClick = onToggleList,
            )
            Text(
                text = if (state.watchlisted) "In My List" else "My List",
                color = TvDesign.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
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
                    trackColor = TvDesign.White.copy(alpha = .15f),
                )
                Text(
                    text = detailRemainingLabel(entry),
                    color = TvDesign.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }

        if (!creditLine.isNullOrBlank()) {
            Spacer(Modifier.height(17.dp))
            Text(
                text = creditLine,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidth),
            )
        }

        if (state.ratings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DetailRatingsRow(state.ratings)
        }

        state.item.description?.takeIf(String::isNotBlank)?.let { description ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .72f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(DetailHeroTextWidth),
            )
        }

        state.dnaMatch?.let { score ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VUEO DNA Match  •  $score%",
                color = TvDesign.Dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DetailPrimaryAction(
    label: String,
    enabled: Boolean,
    requester: FocusRequester,
    rightRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(if (focused) 130 else 95),
        label = "detailPrimaryScale",
    )

    Box(
        modifier = Modifier
            .height(DetailActionHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties {
                right = rightRequester
                downRequester?.let { down = it }
                up = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    !enabled -> TvDesign.White.copy(alpha = .10f)
                    focused -> TvDesign.White
                    else -> TvDesign.White.copy(alpha = .93f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.Black else TvDesign.Dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailSquareAction(
    label: String,
    contentLabel: String,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(contentLabel) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(if (focused) 130 else 95),
        label = "detailSquareScale",
    )
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .size(DetailActionHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties {
                left = leftRequester
                downRequester?.let { down = it }
                up = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .22f)
                else Color.Black.copy(alpha = .46f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .14f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TvDesign.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailRatingsRow(ratings: List<MediaRating>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.distinctBy(MediaRating::source).take(4).forEach { rating ->
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = .42f), RoundedCornerShape(6.dp))
                    .border(1.dp, TvDesign.White.copy(alpha = .14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${rating.compactLabel} ${rating.displayValue()}",
                    color = TvDesign.White.copy(alpha = .84f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DetailSeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    selectedRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailSectionTitle("Episodes")

        val initialIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer { selectedRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(seasons, key = { it }) { season ->
                val selected = season == selectedSeason
                DetailSeasonChip(
                    season = season,
                    selected = selected,
                    requester = if (selected) selectedRequester else null,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun DetailSeasonChip(
    season: Int,
    selected: Boolean,
    requester: FocusRequester?,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    var focused by remember(season) { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (focused && !selected) {
            delay(150)
            if (focused) onSelect(season)
        }
    }

    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> TvDesign.White.copy(alpha = .20f)
                    selected -> TvDesign.White.copy(alpha = .11f)
                    else -> Color.Black.copy(alpha = .36f)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .10f),
                shape = shape,
            )
            .clickable { onSelect(season) }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (season == 0) "Specials" else "Season $season",
            color = if (focused || selected) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DetailEpisodesRow(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    history: List<LibraryPlaybackEntry>,
    selectedEpisode: EpisodeItem?,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: (EpisodeItem) -> Unit,
    onOpen: (EpisodeItem) -> Unit,
) {
    val savedId = TvDetailFocusMemory.episodeId
        ?.takeIf { id -> episodes.any { it.id == id } }
        ?: selectedEpisode?.id
        ?: episodes.firstOrNull()?.id
    val savedIndex = episodes.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedIndex)
    val requesters = remember(media.id, media.type, episodes.map(EpisodeItem::id)) {
        mutableMapOf<String, FocusRequester>()
    }
    val restoreRequester = requesters.getOrPut(savedId.orEmpty()) { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowRequester)
                .focusRestorer { restoreRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = episodes,
                key = { _, episode -> episode.id },
            ) { _, episode ->
                val requester = requesters.getOrPut(episode.id) { FocusRequester() }
                DetailEpisodeCard(
                    episode = episode,
                    entry = detailPlaybackEntry(media, episode, history),
                    selected = selectedEpisode?.id == episode.id,
                    requester = requester,
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onFocused = { onFocused(episode) },
                    onOpen = { onOpen(episode) },
                )
            }
        }
    }
}

@Composable
private fun DetailEpisodeCard(
    episode: EpisodeItem,
    entry: LibraryPlaybackEntry?,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) DetailCardFocusScale else 1f,
        animationSpec = tween(if (focused) 125 else 95),
        label = "detailEpisodeScale",
    )

    Column(
        modifier = Modifier
            .width(DetailEpisodeWidth)
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
            .onFocusChanged {
                val becameFocused = it.isFocused
                if (becameFocused && !focused) onFocused()
                focused = becameFocused
            }
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailEpisodeImageHeight)
                .clip(DetailShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                    color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .22f),
                    shape = DetailShape,
                ),
        ) {
            TvNetworkImage(
                url = episode.thumbnail,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.SurfaceRaised,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            .65f to Color.Transparent,
                            1f to TvDesign.Black.copy(alpha = .72f),
                        )
                    )
            )

            Text(
                text = "S${episode.season} E${episode.episode}",
                color = TvDesign.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 9.dp),
            )

            entry?.takeIf { it.positionMs > 5_000L }?.let {
                LinearProgressIndicator(
                    progress = { it.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TvDesign.White,
                    trackColor = TvDesign.White.copy(alpha = .16f),
                )
            }
        }

        Text(
            text = if (episode.episode > 0) "${episode.episode}. ${episode.title}" else episode.title,
            color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .86f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        episode.overview?.takeIf(String::isNotBlank)?.let { overview ->
            Text(
                text = overview,
                color = TvDesign.Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        entry?.takeIf(::detailCanResume)?.let {
            Text(
                text = detailRemainingLabel(it),
                color = TvDesign.Dim,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailCastSection(cast: List<MediaPerson>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailSectionTitle("Cast")
        LazyRow(
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast.take(24), key = { "${it.name}:${it.character.orEmpty()}:${it.role.orEmpty()}" }) { person ->
                Column(
                    modifier = Modifier.width(108.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
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
                        color = TvDesign.White.copy(alpha = .82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (person.character ?: person.role)?.takeIf(String::isNotBlank)?.let { role ->
                        Text(
                            text = role,
                            color = TvDesign.Dim,
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
private fun DetailCompanySection(
    title: String,
    companies: List<MediaCompany>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailSectionTitle(title)
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
                            color = Color.Black.copy(alpha = .78f),
                            fontSize = 10.sp,
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
private fun DetailRelatedSection(
    items: List<MediaItem>,
    rowRequester: FocusRequester,
    upRequester: FocusRequester,
    onOpen: (MediaItem) -> Unit,
) {
    val visibleItems = remember(items) { items.take(18) }
    val safeIndex = TvDetailFocusMemory.relatedIndex.coerceIn(0, visibleItems.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val requesters = remember(items.map { "${it.type}:${it.id}" }) {
        mutableMapOf<Int, FocusRequester>()
    }
    val restoreRequester = requesters.getOrPut(safeIndex) { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailSectionTitle("More Like This")
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowRequester)
                .focusRestorer { restoreRequester }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = DetailHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = visibleItems,
                key = { _, item -> "${item.type}:${item.id}" },
            ) { index, item ->
                DetailRelatedCard(
                    item = item,
                    requester = requesters.getOrPut(index) { FocusRequester() },
                    upRequester = upRequester,
                    onFocused = { TvDetailFocusMemory.relatedIndex = index },
                    onOpen = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun DetailRelatedCard(
    item: MediaItem,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) DetailCardFocusScale else 1f,
        animationSpec = tween(if (focused) 125 else 95),
        label = "detailRelatedScale",
    )

    Column(
        modifier = Modifier
            .width(DetailRelatedWidth)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties { up = upRequester }
            .onFocusChanged {
                val becameFocused = it.isFocused
                if (becameFocused && !focused) onFocused()
                focused = becameFocused
            }
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .width(DetailRelatedWidth)
                .height(DetailRelatedHeight)
                .clip(DetailShape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White else Color.Transparent,
                    shape = DetailShape,
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
                color = TvDesign.Dim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailInsightCard(
    insight: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailHorizontalPadding)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) TvDesign.White.copy(alpha = .13f)
                else TvDesign.Surface.copy(alpha = .78f)
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
            loading -> Text("Generating…", color = TvDesign.Muted, fontSize = 11.sp)
            !insight.isNullOrBlank() -> Text(
                text = insight,
                color = TvDesign.White.copy(alpha = .76f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            !error.isNullOrBlank() -> Text(error, color = Color(0xFFFFB0B0), fontSize = 11.sp)
            else -> Text(
                text = "Press OK to generate a title insight.",
                color = TvDesign.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DetailSectionTitle(title: String) {
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
private fun DetailMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailHorizontalPadding)
            .background(TvDesign.Surface.copy(alpha = .78f), DetailShape)
            .padding(16.dp),
    ) {
        Text(message, color = TvDesign.Muted, fontSize = 12.sp)
    }
}

private fun detailFacts(item: MediaItem): List<String> {
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

private fun detailCreditLine(item: MediaItem): String? {
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
