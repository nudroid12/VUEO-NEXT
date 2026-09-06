package com.vueo.tv.detail

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV 29E Detail.
 *
 * Behaviour/data stays on VUEO Mobile + Shared Core semantics while the screen
 * composition and focus grammar are adapted from the supplied Nuvio TV source:
 * sticky cinematic backdrop, hero-owned primary actions, season tabs + episode
 * row, then horizontal supporting sections.
 */
@Composable
fun TvDetailScreen(
    runtime: TvRuntime,
    initial: MediaItem,
    onBack: () -> Unit,
    onWatch: (MediaItem, EpisodeItem?) -> Unit,
    onOpenRelated: (MediaItem) -> Unit = {},
    onLibraryChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var item by remember(initial.id, initial.type) { mutableStateOf(initial) }
    var loadingMeta by remember(initial.id, initial.type) { mutableStateOf(true) }
    var watchlisted by remember(initial.id, initial.type) {
        mutableStateOf(runtime.libraryStore.isWatchlisted(initial))
    }
    var ratings by remember(initial.id, initial.type) { mutableStateOf<List<MediaRating>>(emptyList()) }
    var related by remember(initial.id, initial.type) { mutableStateOf<List<MediaItem>>(emptyList()) }

    var selectedSeason by remember(initial.id, initial.type) { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember(initial.id, initial.type) { mutableStateOf<EpisodeItem?>(null) }

    var insight by remember(initial.id, initial.type) { mutableStateOf<String?>(null) }
    var insightLoading by remember(initial.id, initial.type) { mutableStateOf(false) }
    var insightError by remember(initial.id, initial.type) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val playRequester = remember(initial.id, initial.type) { FocusRequester() }
    val listRequester = remember(initial.id, initial.type) { FocusRequester() }
    val seasonRequesters = remember(initial.id, initial.type) { mutableMapOf<Int, FocusRequester>() }
    val episodeRequesters = remember(initial.id, initial.type) { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(initial.id, initial.type) {
        loadingMeta = true
        val enriched = runCatching { runtime.loadMeta(initial) }.getOrDefault(initial)
        item = enriched
        watchlisted = runtime.libraryStore.isWatchlisted(enriched)
        val supplementalRatings =
            runCatching { runtime.ratings(enriched) }.getOrDefault(emptyList())
        ratings = (baseDetailRatings(enriched) + supplementalRatings)
            .associateBy { it.source }
            .values
            .toList()
        related = runCatching { runtime.relatedTitles(enriched) }.getOrDefault(emptyList())

        if (enriched.isSeries()) {
            val playbackEntries = runtime.libraryStore.history()
            val resumeEntry = playbackEntries.firstOrNull { entry ->
                entry.media.id == enriched.id &&
                    entry.media.type == enriched.type &&
                    entry.season != null &&
                    entry.episode != null &&
                    entry.positionMs > 15_000L &&
                    !entry.isCompleted
            }
            val resumeEpisode = resumeEntry?.let { entry ->
                enriched.episodes.firstOrNull { episode ->
                    episode.season == entry.season && episode.episode == entry.episode
                }
            }
            val firstSeason = resumeEpisode?.season
                ?: enriched.episodes.map { it.season }.distinct().sorted().firstOrNull { it > 0 }
                ?: enriched.episodes.map { it.season }.distinct().sorted().firstOrNull()

            selectedSeason = firstSeason
            selectedEpisode = resumeEpisode
                ?: enriched.episodes.firstOrNull { it.season == firstSeason }
        } else {
            selectedSeason = null
            selectedEpisode = null
        }
        loadingMeta = false
    }

    val dnaMatch = remember(item, loadingMeta) {
        if (loadingMeta) null else runtime.dnaMatch(item)
    }
    val playbackEntries = remember(item.id, item.type, selectedEpisode, loadingMeta) {
        runtime.libraryStore.history()
    }
    val activePlaybackEntry = remember(item.id, item.type, selectedEpisode, playbackEntries) {
        detailPlaybackEntry(item, selectedEpisode, playbackEntries)
    }
    val canResume = activePlaybackEntry?.let(::canResumeEntry) == true
    val primaryActionLabel = remember(item, selectedEpisode, canResume, loadingMeta) {
        when {
            loadingMeta -> "Loading…"
            item.isSeries() && selectedEpisode != null && canResume ->
                "Resume S${selectedEpisode!!.season} E${selectedEpisode!!.episode}"
            item.isSeries() && selectedEpisode != null ->
                "Play S${selectedEpisode!!.season} E${selectedEpisode!!.episode}"
            item.isSeries() -> "Select an Episode"
            canResume -> "Resume"
            else -> "Watch"
        }
    }

    val seasons = remember(item.episodes) {
        item.episodes.map { it.season }.distinct().sorted()
    }
    val episodesForSeason = remember(item.episodes, selectedSeason) {
        item.episodes.filter { it.season == selectedSeason }
    }
    val selectedSeasonRequester = selectedSeason?.let { season ->
        seasonRequesters.getOrPut(season) { FocusRequester() }
    }
    val firstEpisodeRequester = episodesForSeason.firstOrNull()?.let { episode ->
        episodeRequesters.getOrPut(episode.id) { FocusRequester() }
    }

    LaunchedEffect(initial.id, initial.type, loadingMeta) {
        if (!loadingMeta) {
            delay(120)
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        TvNetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )

        // Nuvio-reference backdrop treatment: the backdrop remains cinematic,
        // while strong left/bottom scrims guarantee calm readable content.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            TvDesign.Black.copy(alpha = .98f),
                            TvDesign.Black.copy(alpha = .86f),
                            TvDesign.Black.copy(alpha = .32f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = .08f),
                            Color.Transparent,
                            TvDesign.Black.copy(alpha = .82f),
                            TvDesign.Black,
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 54.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "detail-hero") {
                DetailHero(
                    item = item,
                    ratings = ratings,
                    dnaMatch = dnaMatch,
                    primaryActionLabel = primaryActionLabel,
                    canPlay = !loadingMeta && (!item.isSeries() || selectedEpisode != null),
                    watchlisted = watchlisted,
                    activePlaybackEntry = activePlaybackEntry,
                    playRequester = playRequester,
                    listRequester = listRequester,
                    downRequester = selectedSeasonRequester ?: firstEpisodeRequester,
                    onWatch = { onWatch(item, selectedEpisode) },
                    onToggleList = {
                        watchlisted = runtime.libraryStore.toggleWatchlist(item)
                        onLibraryChanged()
                    },
                )
            }

            item.description
                ?.takeIf(String::isNotBlank)
                ?.let { description ->
                    item(key = "detail-overview") {
                        OverviewCard(description)
                    }
                }

            if (item.isSeries() && seasons.isNotEmpty()) {
                item(key = "detail-seasons") {
                    SeasonTabs(
                        seasons = seasons,
                        selectedSeason = selectedSeason,
                        requesters = seasonRequesters,
                        upRequester = playRequester,
                        downRequester = firstEpisodeRequester,
                        onSelect = { season ->
                            selectedSeason = season
                            selectedEpisode = item.episodes.firstOrNull { it.season == season }
                        },
                    )
                }
            }

            if (item.isSeries() && episodesForSeason.isNotEmpty()) {
                item(key = "detail-episodes:${selectedSeason ?: 0}") {
                    DetailEpisodesRow(
                        media = item,
                        episodes = episodesForSeason,
                        selectedEpisode = selectedEpisode,
                        playbackEntries = playbackEntries,
                        requesters = episodeRequesters,
                        upRequester = selectedSeasonRequester ?: playRequester,
                        onEpisodeFocused = { selectedEpisode = it },
                        onEpisodeClick = { episode ->
                            selectedSeason = episode.season
                            selectedEpisode = episode
                            onWatch(item, episode)
                        },
                    )
                }
            } else if (item.isSeries() && !loadingMeta && item.episodes.isEmpty()) {
                item(key = "detail-no-episodes") {
                    DetailMessageCard("Episodes are not available for this title yet.")
                }
            }

            if (item.cast.isNotEmpty()) {
                item(key = "detail-cast") {
                    CastSection(item.cast)
                }
            }

            val companies = if (item.isSeries()) item.networks else item.productionCompanies
            if (companies.isNotEmpty()) {
                item(key = "detail-companies") {
                    CompanySection(
                        title = if (item.isSeries()) {
                            if (companies.size == 1) "Network" else "Networks"
                        } else {
                            "Production"
                        },
                        companies = companies,
                    )
                }
            }

            if (related.isNotEmpty()) {
                item(key = "detail-related") {
                    MoreLikeThisSection(
                        items = related,
                        onClick = onOpenRelated,
                    )
                }
            }

            val geminiAvailable =
                runtime.settingsStore.geminiInsightsEnabled() &&
                    runtime.settingsStore.geminiApiKey().isNotBlank()
            if (geminiAvailable && !loadingMeta) {
                item(key = "detail-ai-insight") {
                    InsightCard(
                        insight = insight,
                        loading = insightLoading,
                        error = insightError,
                        onGenerate = {
                            if (!insightLoading) {
                                insightLoading = true
                                insightError = null
                                scope.launch {
                                    runCatching { runtime.geminiInsight(item) }
                                        .onSuccess { result ->
                                            insight = result
                                            if (result.isNullOrBlank()) {
                                                insightError = "No insight returned."
                                            }
                                        }
                                        .onFailure { error ->
                                            insightError = error.message ?: "Insight failed."
                                        }
                                    insightLoading = false
                                }
                            }
                        },
                    )
                }
            }
        }

        if (loadingMeta) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 42.dp)
                    .size(22.dp),
                color = TvDesign.White,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun DetailHero(
    item: MediaItem,
    ratings: List<MediaRating>,
    dnaMatch: Int?,
    primaryActionLabel: String,
    canPlay: Boolean,
    watchlisted: Boolean,
    activePlaybackEntry: LibraryPlaybackEntry?,
    playRequester: FocusRequester,
    listRequester: FocusRequester,
    downRequester: FocusRequester?,
    onWatch: () -> Unit,
    onToggleList: () -> Unit,
) {
    val facts = detailFacts(item)
    val creditLines = detailCreditLines(item)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(start = 58.dp, end = 54.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = item.name,
            color = TvDesign.White,
            fontSize = 42.sp,
            lineHeight = 45.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.62f),
        )

        Spacer(Modifier.height(15.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailActionButton(
                label = primaryActionLabel,
                primary = true,
                enabled = canPlay,
                requester = playRequester,
                leftRequester = null,
                rightRequester = listRequester,
                downRequester = downRequester,
                onClick = onWatch,
            )
            DetailActionButton(
                label = if (watchlisted) "✓  In My List" else "+  My List",
                primary = false,
                requester = listRequester,
                leftRequester = playRequester,
                rightRequester = null,
                downRequester = downRequester,
                onClick = onToggleList,
            )
        }

        activePlaybackEntry
            ?.takeIf(::canResumeEntry)
            ?.let { entry ->
                Spacer(Modifier.height(13.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(.55f),
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
                        trackColor = Color.White.copy(alpha = .15f),
                    )
                    Text(
                        text = remainingTimeLabel(entry),
                        color = TvDesign.Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }

        if (creditLines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            creditLines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(3.dp))
                Text(
                    text = line,
                    color = TvDesign.Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.60f),
                )
            }
        }

        if (ratings.isNotEmpty() || dnaMatch != null) {
            Spacer(Modifier.height(11.dp))
            RatingsRow(
                ratings = ratings,
                dnaMatch = dnaMatch,
            )
        }

        item.description?.takeIf(String::isNotBlank)?.let { description ->
            Spacer(Modifier.height(13.dp))
            Text(
                text = description,
                color = TvDesign.White.copy(alpha = .80f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.60f),
            )
        }

        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = facts.joinToString("  •  "),
                color = TvDesign.White.copy(alpha = .62f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.72f),
            )
        }

        if (item.genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.genres.take(4).joinToString("  •  "),
                color = TvDesign.Dim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.72f),
            )
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    requester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(160),
        label = "detailActionScale",
    )
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(46.dp)
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent event.isTvActivationKey()
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                color = when {
                    !enabled -> TvDesign.White.copy(alpha = .08f)
                    primary && focused -> TvDesign.White
                    primary -> TvDesign.White.copy(alpha = .92f)
                    focused -> TvDesign.White.copy(alpha = .20f)
                    else -> Color.Black.copy(alpha = .42f)
                },
                shape = shape,
            )
            .border(
                width = if (focused && !primary) 1.dp else 0.dp,
                color = if (focused && !primary) TvDesign.White.copy(alpha = .58f) else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 21.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TvDesign.Dim
                primary -> Color.Black
                else -> TvDesign.White
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RatingsRow(
    ratings: List<MediaRating>,
    dnaMatch: Int?,
) {
    val visibleRatings = ratings
        .distinctBy { it.source }
        .take(4)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dnaMatch?.let {
            DetailInfoChip("VUEO $it%")
        }
        visibleRatings.forEach { rating ->
            DetailInfoChip("${rating.compactLabel} ${rating.displayValue()}")
        }
    }
}

@Composable
private fun DetailInfoChip(label: String) {
    Box(
        modifier = Modifier
            .background(
                Color.Black.copy(alpha = .44f),
                RoundedCornerShape(6.dp),
            )
            .border(
                1.dp,
                TvDesign.White.copy(alpha = .16f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = TvDesign.White.copy(alpha = .82f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int?,
    requesters: MutableMap<Int, FocusRequester>,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelect: (Int) -> Unit,
) {
    DetailSectionTitle("Episodes")
    Spacer(Modifier.height(10.dp))

    LazyRow(
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(seasons, key = { it }) { season ->
            val requester = requesters.getOrPut(season) { FocusRequester() }
            DetailSeasonPill(
                label = if (season > 0) "Season $season" else "Specials",
                selected = season == selectedSeason,
                requester = requester,
                upRequester = upRequester,
                downRequester = if (season == selectedSeason) downRequester else null,
                onClick = { onSelect(season) },
            )
        }
    }
}

@Composable
private fun DetailSeasonPill(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .height(40.dp)
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else false
            }
            .background(
                color = when {
                    focused -> TvDesign.White.copy(alpha = .20f)
                    selected -> TvDesign.White.copy(alpha = .12f)
                    else -> TvDesign.SurfaceRaised.copy(alpha = .78f)
                },
                shape = shape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) TvDesign.White.copy(alpha = .52f) else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused || selected) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailEpisodesRow(
    media: MediaItem,
    episodes: List<EpisodeItem>,
    selectedEpisode: EpisodeItem?,
    playbackEntries: List<LibraryPlaybackEntry>,
    requesters: MutableMap<String, FocusRequester>,
    upRequester: FocusRequester,
    onEpisodeFocused: (EpisodeItem) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = episodes,
            key = { _, episode -> episode.id },
        ) { _, episode ->
            val requester = requesters.getOrPut(episode.id) { FocusRequester() }
            val entry = detailPlaybackEntry(media, episode, playbackEntries)
            EpisodeCard(
                episode = episode,
                selected = selectedEpisode?.id == episode.id,
                playbackEntry = entry,
                requester = requester,
                upRequester = upRequester,
                onFocused = { onEpisodeFocused(episode) },
                onClick = { onEpisodeClick(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    selected: Boolean,
    playbackEntry: LibraryPlaybackEntry?,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "detailEpisodeScale",
    )
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .width(250.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(requester)
            .focusProperties { up = upRequester }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else false
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused || selected) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White
                    else if (selected) TvDesign.White.copy(alpha = .36f)
                    else Color.Transparent,
                    shape = shape,
                ),
        ) {
            TvNetworkImage(
                url = episode.thumbnail,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .72f))
                        )
                    )
            )
            Text(
                text = "S${episode.season}  E${episode.episode}",
                color = TvDesign.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )

            playbackEntry?.takeIf { it.positionMs > 5_000L }?.let { entry ->
                LinearProgressIndicator(
                    progress = { entry.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TvDesign.White,
                    trackColor = Color.White.copy(alpha = .18f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = episode.title,
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode.overview?.takeIf(String::isNotBlank)?.let { overview ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = overview,
                color = TvDesign.Dim,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CastSection(cast: List<MediaPerson>) {
    DetailSectionTitle("Cast")
    Spacer(Modifier.height(10.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cast.take(24), key = { "${it.name}:${it.character.orEmpty()}:${it.role.orEmpty()}" }) { person ->
            CastCard(person)
        }
    }
}

@Composable
private fun CastCard(person: MediaPerson) {
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(TvDesign.SurfaceRaised),
        ) {
            TvNetworkImage(
                url = person.profile,
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = person.name,
            color = TvDesign.Muted,
            fontSize = 11.sp,
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

@Composable
private fun CompanySection(
    title: String,
    companies: List<MediaCompany>,
) {
    DetailSectionTitle(title)
    Spacer(Modifier.height(10.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(companies.take(12), key = { it.name }) { company ->
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvDesign.White.copy(alpha = .92f))
                    .padding(14.dp),
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLikeThisSection(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
) {
    DetailSectionTitle("More Like This", subtitle = "Recommended for you")
    Spacer(Modifier.height(10.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items.take(18), key = { "${it.type}:${it.id}" }) { media ->
            RelatedCard(media, onClick = { onClick(media) })
        }
    }
}

@Composable
private fun RelatedCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    var focused by remember(item.id, item.type) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "relatedScale",
    )
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .width(250.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else false
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(TvDesign.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvDesign.White else Color.Transparent,
                    shape = shape,
                ),
        ) {
            TvNetworkImage(
                url = item.background ?: item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.name,
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.releaseInfo?.takeIf(String::isNotBlank)?.let { release ->
            Text(
                text = release,
                color = TvDesign.Dim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightCard(
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
            .padding(horizontal = 58.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp && !loading) onGenerate()
                    true
                } else false
            }
            .background(
                if (focused) TvDesign.White.copy(alpha = .12f)
                else TvDesign.Surface.copy(alpha = .78f),
                shape,
            )
            .border(
                if (focused) 1.dp else 0.dp,
                if (focused) TvDesign.White.copy(alpha = .44f) else Color.Transparent,
                shape,
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
private fun OverviewCard(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var focused by remember(description) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.isTvActivationKey()) {
                    if (event.type == KeyEventType.KeyUp) expanded = !expanded
                    true
                } else false
            }
            .background(
                if (focused) TvDesign.White.copy(alpha = .10f) else TvDesign.Surface.copy(alpha = .66f),
                shape,
            )
            .border(
                if (focused) 1.dp else 0.dp,
                if (focused) TvDesign.White.copy(alpha = .38f) else Color.Transparent,
                shape,
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Overview",
            color = TvDesign.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = TvDesign.White.copy(alpha = .72f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Text(
            text = if (expanded) "Press OK to collapse" else "Press OK to read more",
            color = if (focused) TvDesign.White.copy(alpha = .78f) else TvDesign.Dim,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun DetailSectionTitle(
    title: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.padding(horizontal = 58.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        subtitle?.let {
            Text(
                text = it,
                color = TvDesign.Dim,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DetailMessageCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp)
            .background(TvDesign.Surface.copy(alpha = .82f), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = message,
            color = TvDesign.Muted,
            fontSize = 12.sp,
        )
    }
}

private fun baseDetailRatings(media: MediaItem): List<MediaRating> =
    buildList {
        media.imdbRating
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "imdb", value = it)) }
        media.tmdbRating
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "tmdb", value = it)) }
    }

private fun cleanReleaseInfo(value: String?): String? =
    value
        ?.trim()
        ?.trimEnd { it.isWhitespace() || it == '-' || it == '–' || it == '—' }
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun detailFacts(item: MediaItem): List<String> {
    val seasonCount = if (item.isSeries()) {
        item.episodes.map { it.season }.filter { it > 0 }.distinct().size.takeIf { it > 0 }
    } else null

    return listOfNotNull(
        cleanReleaseInfo(item.releaseInfo),
        seasonCount?.let { if (it == 1) "1 Season" else "$it Seasons" },
        item.runtimeMinutes?.takeIf { it > 0 }?.let(::formatRuntime),
        item.certification?.takeIf(String::isNotBlank),
    )
}

private fun detailCreditLines(item: MediaItem): List<String> =
    buildList {
        val creators = item.creators.take(3).joinToString(", ")
        val directors = item.directors.take(3).joinToString(", ")
        val writers = item.writers.take(3).joinToString(", ")

        when {
            item.isSeries() && creators.isNotBlank() -> add("Creator: $creators")
            directors.isNotBlank() -> add("Director: $directors")
        }
        if (writers.isNotBlank()) add("Writer: $writers")
    }

private fun detailPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    entries: List<LibraryPlaybackEntry>,
): LibraryPlaybackEntry? =
    entries.firstOrNull { entry ->
        entry.media.id == media.id &&
            entry.media.type == media.type &&
            if (media.isSeries() && episode != null) {
                entry.season == episode.season && entry.episode == episode.episode
            } else {
                !media.isSeries()
            }
    }

private fun canResumeEntry(entry: LibraryPlaybackEntry): Boolean =
    entry.positionMs > 15_000L &&
        (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * .95f).toLong())

private fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

private fun remainingTimeLabel(entry: LibraryPlaybackEntry): String {
    if (entry.durationMs <= 0L) return "Resume"
    val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0L)
    val minutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    return if (minutes > 0L) "$minutes min left" else "Almost done"
}

private fun MediaItem.isSeries(): Boolean =
    type.lowercase() in setOf("series", "tv")

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean {
    val code = nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER
}
