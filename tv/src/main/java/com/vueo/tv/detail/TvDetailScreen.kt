package com.vueo.tv.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import kotlinx.coroutines.launch

/**
 * TV 39A Detail boundary.
 *
 * VUEO owns data, library state and routing. Presentation is delegated to the
 * Nuvio-first 39A screen tree. The small Nuvio extras payload only fills visual
 * metadata that VUEO's base MediaItem does not currently carry (logo/status/
 * country/crew/trailer target); playback/source contracts remain untouched.
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
    val context = LocalContext.current

    var item by remember(initial.id, initial.type) { mutableStateOf(initial) }
    var loading by remember(initial.id, initial.type) { mutableStateOf(true) }
    var watchlisted by remember(initial.id, initial.type) {
        mutableStateOf(runtime.libraryStore.isWatchlisted(initial))
    }
    var movieWatched by remember(initial.id, initial.type) {
        mutableStateOf(runtime.libraryStore.isMarkedWatched(initial))
    }
    var nuvioExtras by remember(initial.id, initial.type) {
        mutableStateOf(TvDetailNuvioExtras())
    }
    var ratings by remember(initial.id, initial.type) {
        mutableStateOf<List<MediaRating>>(emptyList())
    }
    var related by remember(initial.id, initial.type) {
        mutableStateOf<List<MediaItem>>(emptyList())
    }
    var selectedSeason by remember(initial.id, initial.type) { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember(initial.id, initial.type) { mutableStateOf<EpisodeItem?>(null) }
    var insight by remember(initial.id, initial.type) { mutableStateOf<String?>(null) }
    var insightLoading by remember(initial.id, initial.type) { mutableStateOf(false) }
    var insightError by remember(initial.id, initial.type) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initial.id, initial.type) {
        val mediaKey = "${initial.type}:${initial.id}"
        val restoringSameTitle = NuvioDetailFocusMemory.mediaKey == mediaKey
        if (!restoringSameTitle) NuvioDetailFocusMemory.resetFor(mediaKey)

        loading = true
        val enriched = runCatching { runtime.loadMeta(initial) }.getOrDefault(initial)
        item = enriched
        watchlisted = runtime.libraryStore.isWatchlisted(enriched)
        movieWatched = runtime.libraryStore.isMarkedWatched(enriched)
        nuvioExtras = TvDetailNuvioExtras()
        ratings = (detailBaseRatings(enriched) + runCatching { runtime.ratings(enriched) }.getOrDefault(emptyList()))
            .associateBy(MediaRating::source)
            .values
            .toList()
        related = runCatching { runtime.relatedTitles(enriched) }.getOrDefault(emptyList())

        if (enriched.isDetailSeries()) {
            val history = runtime.libraryStore.history()
            val resumeEntry = history.firstOrNull { entry ->
                entry.media.id == enriched.id &&
                    entry.media.type == enriched.type &&
                    entry.season != null &&
                    entry.episode != null &&
                    detailCanResume(entry)
            }
            val resumeEpisode = resumeEntry?.let { entry ->
                enriched.episodes.firstOrNull { episode ->
                    episode.season == entry.season && episode.episode == entry.episode
                }
            }
            val seasonNumbers = enriched.episodes.map(EpisodeItem::season).distinct()
            val regularSeasons = seasonNumbers.filter { it > 0 }.sorted()
            val specials = seasonNumbers.filter { it == 0 }
            val orderedSeasons = regularSeasons + specials
            val rememberedSeason = NuvioDetailFocusMemory.selectedSeason
                ?.takeIf { restoringSameTitle && it in orderedSeasons }
            val firstSeason = rememberedSeason
                ?: resumeEpisode?.season
                ?: orderedSeasons.firstOrNull()

            val rememberedEpisode = NuvioDetailFocusMemory.episodeId
                ?.takeIf { restoringSameTitle }
                ?.let { id ->
                    enriched.episodes.firstOrNull { episode ->
                        episode.id == id && episode.season == firstSeason
                    }
                }

            selectedSeason = firstSeason
            selectedEpisode = rememberedEpisode
                ?: resumeEpisode?.takeIf { it.season == firstSeason }
                ?: enriched.episodes.firstOrNull { it.season == firstSeason }
            NuvioDetailFocusMemory.selectedSeason = firstSeason
        } else {
            selectedSeason = null
            selectedEpisode = null
            NuvioDetailFocusMemory.selectedSeason = null
            NuvioDetailFocusMemory.episodeId = null
        }
        loading = false
    }

    LaunchedEffect(item.id, item.type, loading) {
        if (!loading) {
            nuvioExtras = runCatching {
                loadTvDetailNuvioExtras(
                    media = item,
                    tmdbApiKey = runtime.pluginStore.tmdbApiKey(),
                )
            }.getOrDefault(TvDetailNuvioExtras())
        }
    }

    val history = remember(item.id, item.type, selectedEpisode, loading) {
        runtime.libraryStore.history()
    }
    val playbackEntry = remember(item.id, item.type, selectedEpisode?.id, history) {
        detailPlaybackEntry(item, selectedEpisode, history)
    }
    val seasons = remember(item.episodes) {
        val regular = item.episodes.map(EpisodeItem::season).distinct().filter { it > 0 }.sorted()
        val specials = item.episodes.map(EpisodeItem::season).distinct().filter { it == 0 }
        regular + specials
    }
    val episodesForSeason = remember(item.episodes, selectedSeason) {
        item.episodes.filter { it.season == selectedSeason }
    }
    val dnaMatch = remember(item, loading) {
        if (loading) null else runtime.dnaMatch(item)
    }
    val primaryActionLabel = remember(item, selectedEpisode?.id, playbackEntry, loading) {
        detailPrimaryActionLabel(
            item = item,
            episode = selectedEpisode,
            playbackEntry = playbackEntry,
            loading = loading,
        )
    }
    val insightAvailable = remember(loading) {
        !loading &&
            runtime.settingsStore.geminiInsightsEnabled() &&
            runtime.settingsStore.geminiApiKey().isNotBlank()
    }

    TvDetailPresentation(
        state = TvDetailPresentationState(
            item = item,
            loading = loading,
            watchlisted = watchlisted,
            movieWatched = movieWatched,
            nuvioExtras = nuvioExtras,
            ratings = ratings,
            dnaMatch = dnaMatch,
            seasons = seasons,
            selectedSeason = selectedSeason,
            episodes = episodesForSeason,
            selectedEpisode = selectedEpisode,
            history = history,
            playbackEntry = playbackEntry,
            related = related,
            primaryActionLabel = primaryActionLabel,
            insightAvailable = insightAvailable,
            insight = insight,
            insightLoading = insightLoading,
            insightError = insightError,
        ),
        onPlay = {
            if (!loading && (!item.isDetailSeries() || selectedEpisode != null)) {
                onWatch(item, selectedEpisode)
            }
        },
        onToggleList = {
            watchlisted = runtime.libraryStore.toggleWatchlist(item)
            onLibraryChanged()
        },
        onToggleWatched = {
            if (!item.isDetailSeries()) {
                movieWatched = !movieWatched
                runtime.libraryStore.setMarkedWatched(item, movieWatched)
                onLibraryChanged()
            }
        },
        onTrailer = {
            nuvioExtras.trailerUrl?.let { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        },
        onSeasonSelected = { season ->
            selectedSeason = season
            selectedEpisode = item.episodes.firstOrNull { it.season == season }
            NuvioDetailFocusMemory.selectedSeason = season
            NuvioDetailFocusMemory.episodeId = null
        },
        onEpisodeFocused = { episode ->
            selectedSeason = episode.season
            selectedEpisode = episode
            NuvioDetailFocusMemory.selectedSeason = episode.season
            NuvioDetailFocusMemory.episodeId = episode.id
        },
        onEpisodeSelected = { episode ->
            selectedSeason = episode.season
            selectedEpisode = episode
            NuvioDetailFocusMemory.selectedSeason = episode.season
            NuvioDetailFocusMemory.episodeId = episode.id
            onWatch(item, episode)
        },
        onOpenRelated = onOpenRelated,
        onGenerateInsight = {
            if (!insightLoading) {
                insightLoading = true
                insightError = null
                scope.launch {
                    runCatching { runtime.geminiInsight(item) }
                        .onSuccess { result ->
                            insight = result
                            if (result.isNullOrBlank()) insightError = "No insight returned."
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

internal data class TvDetailPresentationState(
    val item: MediaItem,
    val loading: Boolean,
    val watchlisted: Boolean,
    val movieWatched: Boolean,
    val nuvioExtras: TvDetailNuvioExtras,
    val ratings: List<MediaRating>,
    val dnaMatch: Int?,
    val seasons: List<Int>,
    val selectedSeason: Int?,
    val episodes: List<EpisodeItem>,
    val selectedEpisode: EpisodeItem?,
    val history: List<LibraryPlaybackEntry>,
    val playbackEntry: LibraryPlaybackEntry?,
    val related: List<MediaItem>,
    val primaryActionLabel: String,
    val insightAvailable: Boolean,
    val insight: String?,
    val insightLoading: Boolean,
    val insightError: String?,
)

internal fun detailPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    entries: List<LibraryPlaybackEntry>,
): LibraryPlaybackEntry? =
    entries.firstOrNull { entry ->
        entry.media.id == media.id &&
            entry.media.type == media.type &&
            if (media.isDetailSeries() && episode != null) {
                entry.season == episode.season && entry.episode == episode.episode
            } else {
                !media.isDetailSeries()
            }
    }

internal fun detailCanResume(entry: LibraryPlaybackEntry): Boolean =
    entry.positionMs > 15_000L &&
        (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * .95f).toLong())

internal fun detailRemainingLabel(entry: LibraryPlaybackEntry): String {
    if (entry.durationMs <= 0L) return "Resume"
    val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0L)
    val minutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    return if (minutes > 0L) "$minutes min left" else "Almost done"
}

internal fun MediaItem.isDetailSeries(): Boolean =
    type.lowercase() in setOf("series", "tv")

private fun detailPrimaryActionLabel(
    item: MediaItem,
    episode: EpisodeItem?,
    playbackEntry: LibraryPlaybackEntry?,
    loading: Boolean,
): String {
    val canResume = playbackEntry?.let(::detailCanResume) == true
    return when {
        loading -> "Loading…"
        item.isDetailSeries() && episode != null && canResume ->
            "Resume S${episode.season} E${episode.episode}"
        item.isDetailSeries() && episode != null ->
            "Play S${episode.season} E${episode.episode}"
        item.isDetailSeries() -> "Select an Episode"
        canResume -> "Resume"
        else -> "Play"
    }
}

private fun detailBaseRatings(media: MediaItem): List<MediaRating> =
    buildList {
        media.imdbRating?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "imdb", value = it)) }
        media.tmdbRating?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { add(MediaRating(source = "tmdb", value = it)) }
    }
