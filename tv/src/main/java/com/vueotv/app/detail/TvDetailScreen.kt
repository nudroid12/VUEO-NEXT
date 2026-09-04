package com.vueotv.app.detail

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueotv.app.TvTopNav
import com.vueotv.app.data.TvMediaItem
import com.vueotv.app.player.TvEpisodeRef
import com.vueotv.app.player.TvPlaybackRequest
import com.vueotv.app.ui.components.TvNetworkImage
import com.vueotv.app.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DetailBlack = Color(0xFF050706)
private val DetailPanel = Color(0xFF101412)
private val DetailGreen = Color(0xFF84E100)
private val DetailMuted = Color(0xFFAAB2AD)

@Composable
fun TvDetailScreen(
    seed: TvMediaItem,
    repository: TvDetailRepository,
    onNavigate: (String) -> Unit,
    isInMyList: Boolean,
    onToggleMyList: () -> Boolean,
    onPlay: (TvPlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val navRequesters =
        remember {
            listOf("Home", "Search", "Library", "Content Manager")
                .associateWith { FocusRequester() }
        }
    val playRequester = remember { FocusRequester() }
    val listRequester = remember { FocusRequester() }
    val seasonRequester = remember { FocusRequester() }
    val firstEpisodeRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var details by remember(seed.type, seed.id) { mutableStateOf<TvDetailData?>(null) }
    var loadError by remember(seed.type, seed.id) { mutableStateOf<String?>(null) }
    var selectedSeason by remember(seed.type, seed.id) { mutableStateOf<Int?>(null) }

    val media = details?.media ?: seed
    val seasons = details?.seasons.orEmpty()
    val episodes =
        selectedSeason
            ?.let { details?.episodesForSeason(it) }
            .orEmpty()
    val episodeQueue =
        details?.episodes
            .orEmpty()
            .sortedWith(compareBy<TvEpisode> { it.season }.thenBy { it.episode })
            .map { episode ->
                TvEpisodeRef(
                    videoId = episode.id,
                    title = episode.title,
                    season = episode.season.takeIf { it > 0 },
                    episode = episode.episode.takeIf { it > 0 },
                )
            }

    fun playbackRequest(episode: TvEpisode? = null): TvPlaybackRequest =
        TvPlaybackRequest(
            media = media,
            videoId = episode?.id ?: media.id,
            episodeTitle = episode?.title,
            season = episode?.season?.takeIf { it > 0 },
            episode = episode?.episode?.takeIf { it > 0 },
            episodeQueue = episodeQueue,
        )

    BackHandler(onBack = onBack)

    LaunchedEffect(seed.type, seed.id) {
        loadError = null
        runCatching { repository.load(seed) }
            .onSuccess { loaded ->
                details = loaded
                selectedSeason = loaded.seasons.firstOrNull()
            }
            .onFailure { failure ->
                loadError = failure.message ?: "Unable to refresh metadata"
            }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { playRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DetailBlack),
    ) {
        TvNetworkImage(
            url = media.background ?: media.poster,
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(570.dp),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(570.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                DetailBlack,
                                DetailBlack.copy(alpha = 0.86f),
                                Color.Transparent,
                            )
                        )
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(610.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                DetailBlack,
                            )
                        )
                    ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = 76.dp),
            contentPadding = PaddingValues(bottom = 56.dp),
        ) {
            item {
                DetailHero(
                    media = media,
                    runtime = details?.runtime,
                    director = details?.director.orEmpty(),
                    playRequester = playRequester,
                    listRequester = listRequester,
                    upRequester = navRequesters.getValue("Home"),
                    inMyList = isInMyList,
                    onToggleMyList = onToggleMyList,
                    downRequester = if (seasons.isNotEmpty()) seasonRequester else if (episodes.isNotEmpty()) firstEpisodeRequester else null,
                    onPlay = {
                        val firstPlayableEpisode =
                            if (media.type.equals("series", ignoreCase = true)) {
                                episodes.firstOrNull() ?: details?.episodes?.firstOrNull()
                            } else {
                                null
                            }
                        onPlay(playbackRequest(firstPlayableEpisode))
                    },
                )
            }

            item {
                DetailSection(
                    title = "Overview",
                    body = media.description ?: "No overview available.",
                )
            }

            loadError?.let { message ->
                item {
                    Text(
                        text = "Metadata refresh unavailable • $message",
                        color = DetailMuted.copy(alpha = 0.76f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 58.dp, vertical = 8.dp),
                    )
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        SectionTitle("Season")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(seasons) { index, season ->
                                SeasonChip(
                                    season = season,
                                    selected = season == selectedSeason,
                                    modifier = if (index == 0) Modifier.focusRequester(seasonRequester) else Modifier,
                                    upRequester = playRequester,
                                    downRequester = firstEpisodeRequester,
                                    onClick = {
                                        selectedSeason = season
                                        scope.launch {
                                            listState.animateScrollToItem(3)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                item { SectionTitle("Episodes") }
                itemsIndexed(
                    items = episodes,
                    key = { _, episode -> episode.id },
                ) { index, episode ->
                    EpisodeCard(
                        episode = episode,
                        modifier = if (index == 0) Modifier.focusRequester(firstEpisodeRequester) else Modifier,
                        upRequester = if (index == 0 && seasons.isNotEmpty()) seasonRequester else null,
                        onClick = { onPlay(playbackRequest(episode)) },
                    )
                }
            }

            if (!details?.cast.isNullOrEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 18.dp)) {
                        SectionTitle("Cast")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            items(details?.cast.orEmpty().take(18)) { name ->
                                InfoChip(name)
                            }
                        }
                    }
                }
            }

            details?.network?.let { network ->
                item {
                    DetailSection(
                        title = "Network",
                        body = network,
                    )
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester = playRequester,
            selectedLabel = "",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun DetailHero(
    media: TvMediaItem,
    runtime: String?,
    director: List<String>,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    upRequester: FocusRequester,
    inMyList: Boolean,
    onToggleMyList: () -> Boolean,
    downRequester: FocusRequester?,
    onPlay: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(760.dp)
                .padding(start = 58.dp, top = 78.dp, bottom = 38.dp),
    ) {
        Text(
            text = media.name,
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = detailMeta(media, runtime),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = media.genres.take(4).joinToString("  •  "),
                color = DetailMuted,
                fontSize = 15.sp,
            )
        }
        if (director.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Director • ${director.take(2).joinToString(", ")}",
                color = DetailMuted.copy(alpha = 0.84f),
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailActionButton(
                text = "▶  Play",
                primary = true,
                requester = playRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                onClick = onPlay,
            )
            var saved by remember(media.type, media.id, inMyList) { mutableStateOf(inMyList) }
            DetailActionButton(
                text = if (saved) "✓  My List" else "+  My List",
                requester = listRequester,
                upRequester = upRequester,
                downRequester = downRequester,
                onClick = { saved = onToggleMyList() },
            )
        }
    }
}

private fun detailMeta(media: TvMediaItem, runtime: String?): String =
    buildList {
        add(media.displayType)
        media.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        runtime?.takeIf { it.isNotBlank() }?.let(::add)
        media.imdbRating?.let { add("IMDb ★ ${String.format("%.1f", it)}") }
    }.joinToString("  •  ")

@Composable
private fun DetailActionButton(
    text: String,
    requester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "detailActionScale")

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .onFocusChanged { focused = it.isFocused }
                .scale(scale)
                .border(
                    width = 1.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ),
        shape = RoundedCornerShape(9.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.14f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 13.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun DetailSection(
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .width(900.dp)
                .padding(horizontal = 58.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = DetailMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 58.dp, vertical = 6.dp),
    )
}

@Composable
private fun SeasonChip(
    season: Int,
    selected: Boolean,
    modifier: Modifier,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "seasonScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.Transparent,
        label = "seasonBorder",
    )

    Box(
        modifier =
            modifier
                .scale(scale)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .onFocusChanged { focused = it.isFocused }
                .background(
                    color = if (selected) Color.White else DetailPanel,
                    shape = RoundedCornerShape(10.dp),
                )
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Season $season",
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: TvEpisode,
    modifier: Modifier,
    upRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "episodeScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.White.copy(alpha = 0.08f),
        label = "episodeBorder",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 58.dp, vertical = 7.dp)
                .scale(scale)
                .tvVerticalFocus(up = upRequester)
                .onFocusChanged { focused = it.isFocused }
                .background(DetailPanel.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .focusable()
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvNetworkImage(
            url = episode.thumbnail,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(200.dp).height(112.dp),
        )
        Column(modifier = Modifier.width(720.dp).padding(start = 18.dp)) {
            Text(
                text = if (episode.episode > 0) "${episode.episode}. ${episode.title}" else episode.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.overview?.let { overview ->
                Spacer(Modifier.height(5.dp))
                Text(
                    text = overview,
                    color = DetailMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier =
            Modifier
                .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(9.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp,
        )
    }
}
