package com.vueo.tv.detail

import android.content.Context
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.enrichment.GeminiClient
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.enrichment.MetadataEnhancementEngine
import com.vueo.shared.core.enrichment.MetadataEnhancementOptions
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.data.TvUnifiedDiscovery
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV Details adapter over canonical Shared Core metadata/discovery clients.
 *
 * No provider endpoint is hardcoded here. Core metadata comes from the same
 * UnifiedMediaEngine used by Mobile, while TMDB, MDBList, local discovery and
 * Gemini all remain shared:core features.
 */
class TvDetailRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val pluginStore = PluginStore(appContext)
    private val settingsStore =
        SettingsStore(
            context = appContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    private val discovery = TvUnifiedDiscovery(appContext)
    private val profileStore = ProfileStore(appContext)
    private val dnaPreferences =
        UserDnaPreferences(
            context = appContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    private val dnaEngine =
        UserDnaEngine(
            LibraryStore(
                context = appContext,
                prefsName = TvLibraryStore.PREFS_NAME,
                watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
            )
        )

    suspend fun load(seed: TvMediaItem): TvDetailData =
        withContext(Dispatchers.IO) {
            val tmdbKey = pluginStore.tmdbApiKey().trim()
            val preparedSeed =
                if (tmdbKey.isNotBlank() && seed.id.startsWith("tmdb:")) {
                    runCatching {
                        TmdbEnhancementClient.prepareForCore(
                            item = seed,
                            apiKey = tmdbKey,
                        )
                    }.getOrDefault(seed)
                } else {
                    seed
                }

            val coreMedia =
                runCatching {
                    discovery.loadMeta(preparedSeed)
                }.getOrDefault(preparedSeed)

            val enhanced =
                MetadataEnhancementEngine.enrich(
                    media = coreMedia,
                    options =
                        MetadataEnhancementOptions(
                            tmdbApiKey = tmdbKey,
                            mdblistApiKey = settingsStore.mdblistApiKey(),
                            tmdbMetadataEnabled = settingsStore.tmdbMetadataEnrichmentEnabled(),
                            tmdbArtworkEnabled = settingsStore.tmdbArtworkEnrichmentEnabled(),
                            richDetailsEnabled = true,
                            ratingsEnabled = settingsStore.mdblistRatingsEnabled(),
                        ),
                )

            val media = enhanced.media
            val ratings =
                enhanced.ratings.filter { rating ->
                    when (rating.source) {
                        "imdb" -> settingsStore.mdblistImdbEnabled()
                        "tomatoes" -> settingsStore.mdblistRottenTomatoesEnabled()
                        "metacritic" -> settingsStore.mdblistMetacriticEnabled()
                        "tmdb" -> settingsStore.mdblistTmdbRatingEnabled()
                        "trakt" -> settingsStore.mdblistTraktEnabled()
                        else -> true
                    }
                }

            val localRelated =
                CatalogDiscoveryCache.related(
                    item = media,
                    limit = RELATED_LIMIT,
                )

            val tmdbRelatedEnabled =
                tmdbKey.isNotBlank() &&
                    (
                        settingsStore.tmdbRecommendationsEnabled() ||
                            settingsStore.tmdbSimilarTitlesEnabled()
                    )

            val tmdbRelated =
                if (tmdbRelatedEnabled) {
                    runCatching {
                        TmdbEnhancementClient.moreLikeThis(
                            item = media,
                            apiKey = tmdbKey,
                            recommendationsEnabled = settingsStore.tmdbRecommendationsEnabled(),
                            similarEnabled = settingsStore.tmdbSimilarTitlesEnabled(),
                            limit = RELATED_LIMIT,
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

            val related =
                (tmdbRelated + localRelated)
                    .asSequence()
                    .filterNot { candidate ->
                        candidate.id == media.id && candidate.type == media.type
                    }
                    .distinctBy { candidate ->
                        "${candidate.type}:${candidate.id}"
                    }
                    .take(RELATED_LIMIT)
                    .toList()

            TvDetailData(
                media = media,
                runtime = media.runtimeMinutes?.let(::formatRuntime),
                director = media.directors,
                cast = media.cast,
                productionCompanies = media.productionCompanies,
                networks = media.networks,
                episodes = media.episodes,
                ratings = ratings,
                related = related,
                relatedUsesTmdb = tmdbRelated.isNotEmpty(),
                geminiAvailable =
                    settingsStore.geminiInsightsEnabled() &&
                        settingsStore.geminiApiKey().isNotBlank(),
            )
        }

    suspend fun generateGeminiInsight(media: TvMediaItem): String =
        withContext(Dispatchers.IO) {
            check(settingsStore.geminiInsightsEnabled()) {
                "Gemini Insights are disabled in Settings."
            }

            val apiKey = settingsStore.geminiApiKey().trim()
            check(apiKey.isNotBlank()) {
                "Gemini API key is not configured."
            }

            val profileId = profileStore.activeProfileId()
            val dna =
                if (dnaPreferences.userDnaEnabled(profileId)) {
                    dnaEngine.build()
                } else {
                    null
                }
            val visibleMatch =
                if (
                    dna != null &&
                    dna.hasUsefulData &&
                    dnaPreferences.shouldShowDnaMatch(profileId)
                ) {
                    dnaEngine.matchPercent(media = media, dna = dna)
                } else {
                    null
                }

            GeminiClient.titleInsight(
                media = media,
                dna = dna,
                dnaMatchPercent = visibleMatch,
                apiKey = apiKey,
            )
        }

    companion object {
        private const val RELATED_LIMIT = 18
    }
}

data class TvDetailData(
    val media: TvMediaItem,
    val runtime: String?,
    val director: List<String>,
    val cast: List<MediaPerson>,
    val productionCompanies: List<MediaCompany>,
    val networks: List<MediaCompany>,
    val episodes: List<TvEpisode>,
    val ratings: List<MediaRating> = emptyList(),
    val related: List<TvMediaItem> = emptyList(),
    val relatedUsesTmdb: Boolean = false,
    val geminiAvailable: Boolean = false,
) {
    val seasons: List<Int>
        get() = episodes.map { it.season }.filter { it > 0 }.distinct().sorted()

    fun episodesForSeason(season: Int): List<TvEpisode> =
        episodes.filter { it.season == season }.sortedBy { it.episode }
}

typealias TvEpisode = com.vueo.shared.core.media.EpisodeItem

private fun formatRuntime(minutes: Int): String =
    when {
        minutes <= 0 -> ""
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
