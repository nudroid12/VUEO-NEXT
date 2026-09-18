package com.vueo.tv.detail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vueo.shared.core.detail.DetailUpstreamPolicy
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.search.MediaEntityTarget
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.enrichDetailRichDetails
import com.vueo.tv.core.enrichDetailTmdb
import com.vueo.tv.core.loadCoreDetail
import com.vueo.tv.core.prepareDetailForCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV Details follows Mobile's orchestration upstream:
 * instant shell -> identity preparation -> core metadata -> progressive extras.
 * TV keeps only its 10-foot presentation/focus behavior here.
 */
@Composable
fun TvDetailScreen(
    runtime: TvRuntime,
    initial: MediaItem,
    initialLibraryEntry: LibraryPlaybackEntry? = null,
    onBack: () -> Unit,
    onWatch: (MediaItem, EpisodeItem?, Long) -> Unit,
    onOpenRelated: (MediaItem) -> Unit = {},
    onOpenEntity: (MediaEntityTarget) -> Unit = {},
    onLibraryChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)
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
    var vueoExtras by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(TvDetailVueoExtras())
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
    var tmdbMoreLikeThisEnabled by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(false)
    }
    var dnaMatch by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf<Int?>(null)
    }
    var selectedSeason by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember(initial.id, initial.type, initial.sourceExtensionId) { mutableStateOf<EpisodeItem?>(null) }
    var episodeSelectionTouchedByUser by remember(initial.id, initial.type, initial.sourceExtensionId) {
        mutableStateOf(false)
    }

    fun publishRatings(media: MediaItem) {
        ratings = (detailBaseRatings(media) + supplementalRatings)
            .associateBy(MediaRating::source)
            .values
            .toList()
    }

    fun syncEpisodeSelection(
        media: MediaItem,
        preserveCurrent: Boolean,
    ) {
        if (!media.isDetailSeries() || media.episodes.isEmpty()) {
            selectedSeason = null
            selectedEpisode = null
            VueoDetailFocusMemory.selectedSeason = null
            VueoDetailFocusMemory.episodeId = null
            return
        }

        if (preserveCurrent && episodeSelectionTouchedByUser) {
            val current = selectedEpisode?.let { selected ->
                media.episodes.firstOrNull { candidate ->
                    candidate.season == selected.season &&
                        candidate.episode == selected.episode
                }
            }
            if (current != null) {
                selectedSeason = current.season
                selectedEpisode = current
                return
            }
        }

        val playbackTarget = detailPlaybackTargetEpisode(
            media = media,
            entries = runtime.libraryStore.history(),
            initialEntry = initialLibraryEntry,
        )
        val target = playbackTarget
            ?: orderedDetailEpisodes(media.episodes).firstOrNull()

        selectedSeason = target?.season
        selectedEpisode = target
        VueoDetailFocusMemory.selectedSeason = target?.season
        VueoDetailFocusMemory.episodeId = null
    }

    LaunchedEffect(initial.id, initial.type, initial.sourceExtensionId) {
        val mediaKey = "${initial.type}:${initial.id}"
        val restoringSameTitle = VueoDetailFocusMemory.mediaKey == mediaKey
        if (!restoringSameTitle) VueoDetailFocusMemory.resetFor(mediaKey)

        // Publish the catalog/search item immediately. Network work must not own the page shell.
        val shell = DetailUpstreamPolicy.normalizeSeriesEpisodes(initial)
        item = shell
        loading = true
        related = emptyList()
        tmdbMoreLikeThisEnabled = runtime.pluginStore.tmdbApiKey().isNotBlank() &&
            (
                runtime.settingsStore.tmdbRecommendationsEnabled() ||
                    runtime.settingsStore.tmdbSimilarTitlesEnabled()
            )
        dnaMatch = null
        vueoExtras = TvDetailVueoExtras()
        supplementalRatings = emptyList()
        publishRatings(shell)
        watchlisted = runtime.libraryStore.isWatchlisted(shell)
        movieWatched = runtime.libraryStore.isMarkedWatched(shell)
        syncEpisodeSelection(shell, preserveCurrent = false)

        // Actor Search / More Like This can produce tmdb:<id>. Resolve that identity before Stremio core.
        val prepared = runCatching { runtime.prepareDetailForCore(initial) }.getOrDefault(initial)
        val core = runCatching { runtime.loadCoreDetail(prepared) }
            .getOrElse { DetailUpstreamPolicy.normalizeSeriesEpisodes(prepared) }

        item = core
        publishRatings(core)
        watchlisted = runtime.libraryStore.isWatchlisted(core)
        movieWatched = runtime.libraryStore.isMarkedWatched(core)
        syncEpisodeSelection(core, preserveCurrent = true)

        // Match Mobile's perceived-load flow: core metadata is enough to
        // release Detail. Local/remote recommendations continue after the
        // page is already visible.
        loading = false

        launch {
            val localRelated = runCatching {
                withContext(Dispatchers.Default) {
                    runtime.localRelatedTitles(core)
                }
            }.getOrDefault(emptyList())

            related = localRelated
            related = runCatching {
                withContext(Dispatchers.Default) {
                    runtime.relatedTitles(
                        item = core,
                        localItems = localRelated,
                    )
                }
            }.getOrDefault(localRelated)
        }

        launch {
            var enriched = runCatching { runtime.enrichDetailTmdb(core) }.getOrDefault(core)
            if (enriched != core) {
                item = enriched
                publishRatings(enriched)
                watchlisted = runtime.libraryStore.isWatchlisted(enriched)
                movieWatched = runtime.libraryStore.isMarkedWatched(enriched)
                syncEpisodeSelection(enriched, preserveCurrent = true)
            }
            val rich = runCatching { runtime.enrichDetailRichDetails(enriched) }.getOrDefault(enriched)
            if (rich != enriched) {
                enriched = rich
                item = enriched
                publishRatings(enriched)
                watchlisted = runtime.libraryStore.isWatchlisted(enriched)
                movieWatched = runtime.libraryStore.isMarkedWatched(enriched)
                syncEpisodeSelection(enriched, preserveCurrent = true)
            }
        }

        launch {
            val fetched = runCatching { runtime.ratings(core) }.getOrDefault(emptyList())
            supplementalRatings = fetched
            publishRatings(item)
        }

        launch {
            vueoExtras = runCatching {
                loadTvDetailVueoExtras(
                    media = core,
                    tmdbApiKey = runtime.pluginStore.tmdbApiKey(),
                )
            }.getOrDefault(TvDetailVueoExtras())
        }
    }

    val history = remember(item.id, item.type, selectedEpisode, loading) {
        runtime.libraryStore.history()
    }
    val playbackEntry = remember(item.id, item.type, selectedEpisode?.id, history, initialLibraryEntry) {
        detailPlaybackEntry(item, selectedEpisode, history)
            ?: detailInitialPlaybackEntry(item, selectedEpisode, initialLibraryEntry)
    }
    val seasons = remember(item.episodes) {
        val regular = item.episodes.map(EpisodeItem::season).distinct().filter { it > 0 }.sorted()
        val specials = item.episodes.map(EpisodeItem::season).distinct().filter { it == 0 }
        regular + specials
    }
    val episodesForSeason = remember(item.episodes, selectedSeason) {
        item.episodes.filter { it.season == selectedSeason }
    }
    LaunchedEffect(item, loading) {
        if (loading) {
            dnaMatch = null
        } else {
            dnaMatch = runCatching {
                withContext(Dispatchers.Default) {
                    runtime.dnaMatch(item)
                }
            }.getOrNull()
        }
    }
    val primaryActionLabel = remember(item, selectedEpisode?.id, playbackEntry) {
        detailPrimaryActionLabel(item, selectedEpisode, playbackEntry)
    }

    TvDetailPresentation(
        state = TvDetailPresentationState(
            item = item,
            loading = loading,
            watchlisted = watchlisted,
            movieWatched = movieWatched,
            vueoExtras = vueoExtras,
            ratings = ratings,
            dnaMatch = dnaMatch,
            seasons = seasons,
            selectedSeason = selectedSeason,
            episodes = episodesForSeason,
            selectedEpisode = selectedEpisode,
            history = history,
            playbackEntry = playbackEntry,
            related = related,
            tmdbMoreLikeThisEnabled = tmdbMoreLikeThisEnabled,
            primaryActionLabel = primaryActionLabel,
        ),
        onPlay = {
            val seriesNeedsEpisode = item.isDetailSeries() && item.episodes.isNotEmpty()
            if (!seriesNeedsEpisode || selectedEpisode != null) {
                val startPositionMs = playbackEntry?.takeIf(::detailCanResume)?.positionMs ?: 0L
                onWatch(item, selectedEpisode, startPositionMs)
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
        onSeasonSelected = { season ->
            episodeSelectionTouchedByUser = true
            selectedSeason = season
            selectedEpisode = item.episodes
                .asSequence()
                .filter { it.season == season }
                .sortedBy(EpisodeItem::episode)
                .firstOrNull()
            VueoDetailFocusMemory.selectedSeason = season
            VueoDetailFocusMemory.episodeId = null
        },
        onEpisodeFocused = { episode ->
            episodeSelectionTouchedByUser = true
            selectedSeason = episode.season
            selectedEpisode = episode
            VueoDetailFocusMemory.selectedSeason = episode.season
            VueoDetailFocusMemory.episodeId = episode.id
        },
        onEpisodeSelected = { episode ->
            episodeSelectionTouchedByUser = true
            selectedSeason = episode.season
            selectedEpisode = episode
            VueoDetailFocusMemory.selectedSeason = episode.season
            VueoDetailFocusMemory.episodeId = episode.id
            val episodeEntry = detailPlaybackEntry(item, episode, history)
                ?: detailInitialPlaybackEntry(item, episode, initialLibraryEntry)
            val startPositionMs = episodeEntry?.takeIf(::detailCanResume)?.positionMs ?: 0L
            onWatch(item, episode, startPositionMs)
        },
        onOpenRelated = onOpenRelated,
        onOpenEntity = onOpenEntity,
    )
}

internal data class TvDetailPresentationState(
    val item: MediaItem,
    val loading: Boolean,
    val watchlisted: Boolean,
    val movieWatched: Boolean,
    val vueoExtras: TvDetailVueoExtras,
    val ratings: List<MediaRating>,
    val dnaMatch: Int?,
    val seasons: List<Int>,
    val selectedSeason: Int?,
    val episodes: List<EpisodeItem>,
    val selectedEpisode: EpisodeItem?,
    val history: List<LibraryPlaybackEntry>,
    val playbackEntry: LibraryPlaybackEntry?,
    val related: List<MediaItem>,
    val tmdbMoreLikeThisEnabled: Boolean,
    val primaryActionLabel: String,
)

private fun orderedDetailEpisodes(
    episodes: List<EpisodeItem>,
): List<EpisodeItem> {
    val regularEpisodes = episodes.filter { it.season > 0 }
    return (if (regularEpisodes.isNotEmpty()) regularEpisodes else episodes)
        .sortedWith(
            compareBy<EpisodeItem>(EpisodeItem::season)
                .thenBy(EpisodeItem::episode)
        )
        .distinctBy { it.season to it.episode }
}

private fun detailPlaybackTargetEpisode(
    media: MediaItem,
    entries: List<LibraryPlaybackEntry>,
    initialEntry: LibraryPlaybackEntry?,
): EpisodeItem? {
    val orderedEpisodes = orderedDetailEpisodes(media.episodes)
    if (orderedEpisodes.isEmpty()) return null

    val latestPlayback = (entries + listOfNotNull(initialEntry))
        .asSequence()
        .filter { entry ->
            entry.media.id == media.id &&
                entry.media.type == media.type &&
                entry.season != null &&
                entry.episode != null &&
                (entry.isCompleted || entry.positionMs > 15_000L)
        }
        .maxByOrNull(LibraryPlaybackEntry::lastWatchedEpochMs)
        ?: return null

    val currentIndex = orderedEpisodes.indexOfFirst { episode ->
        episode.season == latestPlayback.season &&
            episode.episode == latestPlayback.episode
    }
    if (currentIndex < 0) return null

    if (!latestPlayback.isCompleted) {
        return orderedEpisodes[currentIndex]
    }

    return orderedEpisodes.getOrNull(currentIndex + 1)
        ?: orderedEpisodes.firstOrNull()
}

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

private fun detailInitialPlaybackEntry(
    media: MediaItem,
    episode: EpisodeItem?,
    initial: LibraryPlaybackEntry?,
): LibraryPlaybackEntry? =
    initial?.takeIf { entry ->
        if (media.isDetailSeries()) {
            episode != null && entry.season == episode.season && entry.episode == episode.episode
        } else true
    }

internal fun detailCanResume(entry: LibraryPlaybackEntry): Boolean =
    entry.positionMs > 15_000L &&
        (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * .95f).toLong())

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
