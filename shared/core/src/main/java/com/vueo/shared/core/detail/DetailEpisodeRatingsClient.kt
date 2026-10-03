package com.vueo.shared.core.detail

import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.enrichment.MetadataHttp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.plugin.TmdbResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** IMDb episode scores, keyed by the metadata provider's season/episode numbering. */
object DetailEpisodeRatingsClient {
    private data class Entry(val scores: Map<Pair<Int, Int>, Double>, val expiresAt: Long)
    private val cache = object : LinkedHashMap<String, Entry>(40, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 40
    }

    suspend fun load(media: MediaItem, tmdbApiKey: String): Map<Pair<Int, Int>, Double> =
        withContext(Dispatchers.IO) {
            if (!MediaTypePolicy.isSeries(media.type)) return@withContext emptyMap()
            val key = "${media.type}:${media.id}"
            synchronized(cache) { cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() } }
                ?.let { return@withContext it.scores }
            try {
                val rawId = media.id.trim()
                val tmdbId = if (rawId.startsWith("tmdb:")) rawId.substringAfter("tmdb:").toLongOrNull()
                    else rawId.toLongOrNull()
                val resolved = tmdbId?.takeIf { it > 0L }?.toString()
                    ?: if (tmdbApiKey.isNotBlank()) TmdbResolver.resolve(media.id, media.type, tmdbApiKey) else null
                if (resolved == null || resolved.toLongOrNull()?.let { it > 0L } != true) {
                    trace("EPISODE_RATINGS_SKIPPED", "reason=NO_TMDB_ID")
                    return@withContext emptyMap()
                }
                val scores = parse(JSONArray(MetadataHttp.get(
                    "https://seriesgraph.com/api/shows/$resolved/season-ratings"
                )))
                synchronized(cache) {
                    cache[key] = Entry(scores, System.currentTimeMillis() + if (scores.isEmpty()) 60_000L else 30 * 60_000L)
                }
                trace("EPISODE_RATINGS_READY", "tmdb=$resolved scores=${scores.size} source=seriesgraph-imdb")
                scores
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(cache) { cache[key] = Entry(emptyMap(), System.currentTimeMillis() + 60_000L) }
                trace("EPISODE_RATINGS_FAILED", "type=${error.javaClass.simpleName}")
                emptyMap()
            }
        }

    internal fun parse(seasons: JSONArray): Map<Pair<Int, Int>, Double> = buildMap {
        for (seasonIndex in 0 until seasons.length()) {
            val episodes = seasons.optJSONObject(seasonIndex)?.optJSONArray("episodes") ?: continue
            for (index in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(index) ?: continue
                val season = episode.optInt("tmdb_season_number", -1).takeIf { it >= 0 }
                    ?: episode.optInt("season_number", -1)
                val number = episode.optInt("tmdb_episode_number", -1).takeIf { it > 0 }
                    ?: episode.optInt("episode_number", -1)
                // vote_average can describe another rating source; require the explicit IMDb score.
                val score = episode.optDouble("imdb_rating", Double.NaN)
                if (season >= 0 && number > 0 && score.isFinite() && score > 0.0 && score <= 10.0) {
                    put(season to number, score)
                }
            }
        }
    }

    private fun trace(stage: String, details: String) =
        RuntimeDiagnostics.recordDiscoveryTrace(0L, stage, details)
}
