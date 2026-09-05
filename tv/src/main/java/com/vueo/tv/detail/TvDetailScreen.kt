package com.vueo.tv.detail

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.TV_TOP_NAV_LABELS
import com.vueo.tv.TvTopNav
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.player.TvEpisodeRef
import com.vueo.tv.player.TvPlaybackRequest
import com.vueo.tv.player.TvPlaybackStore
import com.vueo.tv.ui.components.TvNetworkImage
import com.vueo.tv.ui.focus.tvVerticalFocus
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvFocusColorSpec

private val DetailBlack = Color(0xFF050706)
private val DetailPanel = Color(0xFF101412)
private val DetailMuted = Color(0xFFAAB2AD)

@Composable
fun TvDetailScreen(
    seed: TvMediaItem,
    repository: TvDetailRepository,
    onNavigate: (String) -> Unit,
    isInMyList: Boolean,
    onToggleMyList: () -> Boolean,
    onPlay: (TvPlaybackRequest) -> Unit,
    onOpenMedia: (TvMediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS
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
    var geminiInsight by remember(seed.type, seed.id) { mutableStateOf<String?>(null) }
    var geminiInsightLoading by remember(seed.type, seed.id) { mutableStateOf(false) }
    var geminiInsightError by remember(seed.type, seed.id) { mutableStateOf<String?>(null) }

    val media = details?.media ?: seed
    val context = LocalContext.current
    val dnaMatch =
        remember(media, context) {
            val appContext = context.applicationContext
            val profileStore = ProfileStore(appContext)
            val profileId = profileStore.activeProfileId()
            val preferences =
                UserDnaPreferences(
                    context = appContext,
                    prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
                )
            if (!preferences.shouldShowDnaMatch(profileId)) {
                null
            } else {
                UserDnaEngine(
                    LibraryStore(
                        context = appContext,
                        prefsName = TvLibraryStore.PREFS_NAME,
                        watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
                    ),
                ).matchPercent(media)
            }
        }
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
        geminiInsight = null
        geminiInsightLoading = false
        geminiInsightError = null
        runCatching { repository.load(seed) }
            .onSuccess { loaded ->
                details = loaded
                selectedSeason = loaded.seasons.firstOrNull()
            }
            .onFailure { failure ->
                loadError = failure.message ?: "Unable to refresh metadata"
            }
    }

    LaunchedEffect(seed.type, seed.id) {
        runCatching { listState.scrollToItem(0) }
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
            modifier = Modifier.fillMaxWidth().height(640.dp),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(640.dp)
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
                    .height(680.dp)
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
            modifier = Modifier.fillMaxSize().padding(top = 82.dp),
            contentPadding = PaddingValues(bottom = 56.dp),
        ) {
            item {
                DetailHero(
                    media = media,
                    runtime = details?.runtime,
                    director = details?.director.orEmpty(),
                    dnaMatch = dnaMatch,
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

            if (details?.ratings?.isNotEmpty() == true) {
                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        SectionTitle("Ratings")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            items(details?.ratings.orEmpty()) { rating ->
                                InfoChip(
                                    "${rating.compactLabel} ${rating.displayValue()}"
                                )
                            }
                        }
                    }
                }
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
                    CastSection(details?.cast.orEmpty())
                }
            }

            val featuredCompanies =
                if (media.type.equals("series", ignoreCase = true) || media.type.equals("tv", ignoreCase = true)) {
                    details?.networks.orEmpty()
                } else {
                    details?.productionCompanies.orEmpty()
                }
            if (featuredCompanies.isNotEmpty()) {
                item {
                    CompanySection(
                        title = if (media.type.equals("series", ignoreCase = true) || media.type.equals("tv", ignoreCase = true)) "Networks" else "Production",
                        companies = featuredCompanies,
                    )
                }
            }

            if (!details?.related.isNullOrEmpty()) {
                item {
                    MoreLikeThisSection(
                        relatedItems = details?.related.orEmpty(),
                        usesTmdb = details?.relatedUsesTmdb == true,
                        onOpenMedia = onOpenMedia,
                    )
                }
            }

            if (details?.geminiAvailable == true) {
                item {
                    GeminiInsightSection(
                        insight = geminiInsight,
                        loading = geminiInsightLoading,
                        error = geminiInsightError,
                        onGenerate = {
                            if (!geminiInsightLoading) {
                                geminiInsightLoading = true
                                geminiInsightError = null
                                scope.launch {
                                    runCatching { repository.generateGeminiInsight(media) }
                                        .onSuccess { geminiInsight = it }
                                        .onFailure { failure ->
                                            geminiInsightError =
                                                failure.message
                                                    ?.take(180)
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: "Gemini could not generate an insight."
                                        }
                                    geminiInsightLoading = false
                                }
                            }
                        },
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
    dnaMatch: Int?,
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
                .width(820.dp)
                .padding(start = 62.dp, top = 88.dp, bottom = 42.dp),
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = media.genres.take(4).joinToString("  •  "),
                color = DetailMuted,
                fontSize = 14.sp,
            )
        }
        if (director.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Director • ${director.take(2).joinToString(", ")}",
                color = DetailMuted.copy(alpha = 0.84f),
                fontSize = 12.sp,
            )
        }
        dnaMatch?.let { score ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VUEO DNA Match • $score%",
                color = TvAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
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
        media.certification?.takeIf { it.isNotBlank() }?.let(::add)
        runtime?.takeIf { it.isNotBlank() }?.let(::add)
        media.imdbRating?.let { add("IMDb ★ ${String.format("%.1f", it)}") }
        if (media.imdbRating == null) {
            media.tmdbRating?.let { add("TMDB ★ ${String.format("%.1f", it)}") }
        }
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
    val scale by animateFloatAsState(if (focused) 1.035f else 1f,
        animationSpec = tvFocusSpec(), label = "detailActionScale")

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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
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
                .width(980.dp)
                .padding(horizontal = 62.dp, vertical = 16.dp),
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
            fontSize = 14.sp,
            lineHeight = 21.sp,
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
        modifier = Modifier.padding(horizontal = 62.dp, vertical = 7.dp),
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
    val scale by animateFloatAsState(if (focused) 1.03f else 1f,
        animationSpec = tvFocusSpec(), label = "seasonScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.Transparent,
        animationSpec = tvFocusColorSpec(),
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
            fontSize = 13.sp,
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
    val scale by animateFloatAsState(if (focused) 1.018f else 1f,
        animationSpec = tvFocusSpec(), label = "episodeScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.White.copy(alpha = 0.08f),
        animationSpec = tvFocusColorSpec(),
        label = "episodeBorder",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 62.dp, vertical = 9.dp)
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
        Column(modifier = Modifier.width(690.dp).padding(start = 18.dp)) {
            Text(
                text = if (episode.episode > 0) "${episode.episode}. ${episode.title}" else episode.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.overview?.let { overview ->
                Spacer(Modifier.height(5.dp))
                Text(
                    text = overview,
                    color = DetailMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CastSection(cast: List<MediaPerson>) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        SectionTitle("Cast")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(
                cast.take(20),
                key = { "${it.name}:${it.character.orEmpty()}" },
            ) { person ->
                Column(
                    modifier = Modifier.width(116.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(92.dp)
                                .height(92.dp)
                                .clip(CircleShape)
                                .background(DetailPanel),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!person.profile.isNullOrBlank()) {
                            TvNetworkImage(
                                url = person.profile,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = person.name.take(1).uppercase(),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = person.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    person.character
                        ?.takeIf { it.isNotBlank() }
                        ?.let { character ->
                            Text(
                                text = character,
                                color = DetailMuted,
                                fontSize = 11.sp,
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
private fun CompanySection(
    title: String,
    companies: List<MediaCompany>,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(companies.take(12), key = { it.name }) { company ->
                Row(
                    modifier =
                        Modifier
                            .height(66.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp))
                            .padding(horizontal = 15.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!company.logo.isNullOrBlank()) {
                        TvNetworkImage(
                            url = company.logo,
                            contentDescription = company.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.width(82.dp).height(42.dp),
                        )
                    }
                    Text(
                        text = company.name,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLikeThisSection(
    relatedItems: List<TvMediaItem>,
    usesTmdb: Boolean,
    onOpenMedia: (TvMediaItem) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        SectionTitle("More Like This")
        Text(
            text = if (usesTmdb) "Recommended for you • TMDB + VUEO" else "Recommended from your VUEO catalogs",
            color = DetailMuted.copy(alpha = 0.82f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 62.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(
                relatedItems.take(18),
                key = { "${it.type}:${it.id}" },
            ) { related ->
                RelatedPoster(
                    media = related,
                    onClick = { onOpenMedia(related) },
                )
            }
        }
    }
}

@Composable
private fun RelatedPoster(
    media: TvMediaItem,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(), label = "relatedPosterScale")
    val borderColor by animateColorAsState(
        if (focused) Color.White else Color.White.copy(alpha = 0.08f),
        animationSpec = tvFocusColorSpec(),
        label = "relatedPosterBorder",
    )

    Column(modifier = Modifier.width(154.dp)) {
        Box(
            modifier =
                Modifier
                    .width(150.dp)
                    .height(222.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(11.dp))
                    .background(DetailPanel)
                    .border(2.dp, borderColor, RoundedCornerShape(11.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onClick)
                    .focusable(),
        ) {
            TvNetworkImage(
                url = media.poster,
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = media.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        media.releaseInfo?.takeIf { it.isNotBlank() }?.let { release ->
            Text(
                text = release,
                color = DetailMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GeminiInsightSection(
    insight: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 62.dp, vertical = 20.dp)
                .background(DetailPanel.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
                .border(1.dp, TvAccent.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gemini Insight",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Optional AI • generated only when you request it",
                    color = DetailMuted,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "GEMINI",
                color = TvAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }

        insight?.takeIf { it.isNotBlank() }?.let { value ->
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }

        error?.takeIf { it.isNotBlank() }?.let { value ->
            Text(
                text = value,
                color = DetailMuted,
                fontSize = 12.sp,
            )
        }

        var focused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (focused) 1.025f else 1f,
        animationSpec = tvFocusSpec(), label = "geminiButtonScale")
        Button(
            onClick = onGenerate,
            enabled = !loading,
            modifier =
                Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .scale(scale)
                    .border(
                        1.dp,
                        if (focused) Color.White else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    ),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(9.dp),
        ) {
            Text(
                text = when {
                    loading -> "Generating..."
                    insight.isNullOrBlank() -> "Generate Insight"
                    else -> "Regenerate"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
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
