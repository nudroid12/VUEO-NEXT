package com.vueo.tv.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.detail.DetailUpstreamPolicy
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.enrichDetailRichDetails
import com.vueo.tv.core.enrichDetailTmdb
import com.vueo.tv.core.loadCoreDetail
import com.vueo.tv.core.prepareDetailForCore
import kotlinx.coroutines.launch

/**
 * TV Details follows Mobile's orchestration upstream:
 * instant shell -> identity preparation -> core metadata -> progressive extras.
 * TV keeps only its 10-foot presentation/focus behavior here.
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
    val initialShell = remember(initial) {
        DetailUpstreamPolicy.normalizeSeriesEpisodes(initial)
    }

    var item by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf(initialShell) }
    var loading by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf(true) }
    var watchlisted by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(runtime.libraryStore.isWatchlisted(initialShell))
    }
    var movieWatched by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(runtime.libraryStore.isMarkedWatched(initialShell))
    }
    var nuvioExtras by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(TvDetailNuvioExtras())
    }
    var supplementalRatings by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf<List<MediaRating>>(emptyList())
    }
    var ratings by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(detailBaseRatings(initialShell))
    }
    var related by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf<List<MediaItem>>(emptyList())
    }
    var selectedSeason by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf<EpisodeItem?>(null) }

    fun publishRatings(media: MediaItem) {
        ratings = (detailBaseRatings(media) + supplementalRatings)
            .associateBy(MediaRating::source)
            .values
            .toList()
    }

    fun syncEpisodeSelection(
        media: MediaItem,
        preserveCurrent: Boolean,
        restoringSameTitle: Boolean,
    ) {
        if (!media.isDetailSeries() || media.episodes.isEmpty()) {
            selectedSeason = null
            selectedEpisode = null
            NuvioDetailFocusMemory.selectedSeason = null
            NuvioDetailFocusMemory.episodeId = null
            return
        }

        if (preserveCurrent) {
            val current = selectedEpisode?.let { selected ->
                media.episodes.firstOrNull { candidate ->
                    candidate.season == selected.season && candidate.episode == selected.episode
                }
            }
            if (current != null) {
                selectedSeason = current.season
                selectedEpisode = current
                return
            }
        }

        val history = runtime.libraryStore.history()
        val resumeEntry = history.firstOrNull { entry ->
            entry.media.id == media.id &&
                entry.media.type == media.type &&
                entry.season != null &&
                entry.episode != null &&
                detailCanResume(entry)
        }
        val resumeEpisode = resumeEntry?.let { entry ->
            media.episodes.firstOrNull { episode ->
                episode.season == entry.season && episode.episode == entry.episode
            }
        }

        val seasonNumbers = media.episodes.map(EpisodeItem::season).distinct()
        val orderedSeasons = seasonNumbers.filter { it > 0 }.sorted() + seasonNumbers.filter { it == 0 }
        val rememberedSeason = NuvioDetailFocusMemory.selectedSeason
            ?.takeIf { restoringSameTitle && it in orderedSeasons }
        val firstSeason = rememberedSeason ?: resumeEpisode?.season ?: orderedSeasons.firstOrNull()
        val rememberedEpisode = NuvioDetailFocusMemory.episodeId
            ?.takeIf { restoringSameTitle }
            ?.let { id ->
                media.episodes.firstOrNull { episode ->
                    episode.id == id && episode.season == firstSeason
                }
            }

        selectedSeason = firstSeason
        selectedEpisode = rememberedEpisode
            ?: resumeEpisode?.takeIf { it.season == firstSeason }
            ?: media.episodes.firstOrNull { it.season == firstSeason }
        NuvioDetailFocusMemory.selectedSeason = firstSeason
    }

    LaunchedEffect(initial.id, initial.type, initial.sourceExtensionId) {
        val mediaKey = "${initial.type}:${initial.id}"
        val restoringSameTitle = NuvioDetailFocusMemory.mediaKey == mediaKey
        if (!restoringSameTitle) NuvioDetailFocusMemory.resetFor(mediaKey)

        // Publish the catalog/search item immediately. Network work must not own the page shell.
        val shell = DetailUpstreamPolicy.normalizeSeriesEpisodes(initial)
        item = shell
        loading = true
        related = emptyList()
        nuvioExtras = TvDetailNuvioExtras()
        supplementalRatings = emptyList()
        publishRatings(shell)
        watchlisted = runtime.libraryStore.isWatchlisted(shell)
        movieWatched = runtime.libraryStore.isMarkedWatched(shell)
        syncEpisodeSelection(shell, preserveCurrent = false, restoringSameTitle = restoringSameTitle)

        // Actor Search / More Like This can produce tmdb:<id>. Resolve that identity before Stremio core.
        val prepared = runCatching { runtime.prepareDetailForCore(initial) }.getOrDefault(initial)
        val core = runCatching { runtime.loadCoreDetail(prepared) }
            .getOrElse { DetailUpstreamPolicy.normalizeSeriesEpisodes(prepared) }

        item = core
        publishRatings(core)
        watchlisted = runtime.libraryStore.isWatchlisted(core)
        movieWatched = runtime.libraryStore.isMarkedWatched(core)
        syncEpisodeSelection(core, preserveCurrent = true, restoringSameTitle = restoringSameTitle)

        val localRelated = runtime.localRelatedTitles(core)
        related = localRelated

        // Core metadata + local More Like This are enough to release Detail.
        // Remote enrichment continues progressively, matching Mobile behavior.
        loading = false

        launch {
            var enriched = runCatching { runtime.enrichDetailTmdb(core) }.getOrDefault(core)
            if (enriched != core) {
                item = enriched
                publishRatings(enriched)
                watchlisted = runtime.libraryStore.isWatchlisted(enriched)
                movieWatched = runtime.libraryStore.isMarkedWatched(enriched)
                syncEpisodeSelection(
                    enriched,
                    preserveCurrent = true,
                    restoringSameTitle = restoringSameTitle,
                )
            }

            val rich = runCatching { runtime.enrichDetailRichDetails(enriched) }.getOrDefault(enriched)
            if (rich != enriched) {
                enriched = rich
                item = enriched
                publishRatings(enriched)
                watchlisted = runtime.libraryStore.isWatchlisted(enriched)
                movieWatched = runtime.libraryStore.isMarkedWatched(enriched)
                syncEpisodeSelection(
                    enriched,
                    preserveCurrent = true,
                    restoringSameTitle = restoringSameTitle,
                )
            }
        }

        launch {
            val fetched = runCatching { runtime.ratings(core) }.getOrDefault(emptyList())
            supplementalRatings = fetched
            publishRatings(item)
        }

        launch {
            related = runCatching {
                runtime.relatedTitles(
                    item = core,
                    localItems = localRelated,
                )
            }.getOrDefault(localRelated)
        }

        launch {
            nuvioExtras = runCatching {
                loadTvDetailNuvioExtras(
                    media = core,
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
    val primaryActionLabel = remember(item, selectedEpisode?.id, playbackEntry) {
        detailPrimaryActionLabel(
            item = item,
            episode = selectedEpisode,
            playbackEntry = playbackEntry,
        )
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
): String {
    val canResume = playbackEntry?.let(::detailCanResume) == true
    return when {
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
