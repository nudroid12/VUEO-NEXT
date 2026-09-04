package com.vueo.tv.detail

import android.content.Context
import com.vueo.shared.core.enrichment.MediaRating
import com.vueo.shared.core.enrichment.MetadataEnhancementEngine
import com.vueo.shared.core.enrichment.MetadataEnhancementOptions
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.storage.SettingsStore
import com.vueo.tv.data.TvMediaItem
import com.vueo.tv.player.TvPlaybackStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

    suspend fun load(seed: TvMediaItem): TvDetailData =
        withContext(Dispatchers.IO) {
            val url = "$CINEMETA_BASE/meta/${seed.type}/${seed.id}.json"
            val root = JSONObject(httpGet(url))
            val meta = root.optJSONObject("meta") ?: root

            val cinemetaMedia =
                TvMediaItem(
                    id = meta.optString("id").trim().ifBlank { seed.id },
                    type = meta.optString("type").trim().ifBlank { seed.type },
                    name = meta.optString("name").trim().ifBlank { seed.name },
                    poster = httpsOrNull(meta.optString("poster")) ?: seed.poster,
                    background = httpsOrNull(meta.optString("background")) ?: seed.background,
                    description = meta.optString("description").trim().takeIf { it.isNotBlank() } ?: seed.description,
                    releaseInfo = meta.optString("releaseInfo").trim().takeIf { it.isNotBlank() } ?: seed.releaseInfo,
                    genres = meta.stringList("genres").ifEmpty { seed.genres },
                    imdbRating = meta.flexibleDouble("imdbRating", "imdb_rating") ?: seed.imdbRating,
                )

            val enhanced =
                MetadataEnhancementEngine.enrich(
                    media = cinemetaMedia,
                    options =
                        MetadataEnhancementOptions(
                            tmdbApiKey = pluginStore.tmdbApiKey(),
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

            val runtime =
                meta.optString("runtime").trim().takeIf { it.isNotBlank() }
                    ?: media.runtimeMinutes?.let { "$it min" }

            val directors =
                media.directors.ifEmpty {
                    meta.stringList("director")
                }

            val cast =
                media.cast
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .ifEmpty { meta.stringList("cast") }

            val network =
                media.networks.firstOrNull()?.name
                    ?: meta.optString("network").trim().takeIf { it.isNotBlank() }
                    ?: meta.optString("country").trim().takeIf { it.isNotBlank() }

            TvDetailData(
                media = media,
                runtime = runtime,
                director = directors,
                cast = cast,
                network = network,
                episodes = meta.optJSONArray("videos").toEpisodes(),
                ratings = ratings,
                providerName =
                    if (media != cinemetaMedia || ratings.isNotEmpty()) {
                        "Cinemeta + VUEO Enhancements"
                    } else {
                        "Cinemeta"
                    },
            )
        }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) {
            "VUEO TV detail requests require HTTPS."
        }

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VUEO-TV/0.5")
            }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code from Cinemeta")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000
    }
}

data class TvDetailData(
    val media: TvMediaItem,
    val runtime: String?,
    val director: List<String>,
    val cast: List<String>,
    val network: String?,
    val episodes: List<TvEpisode>,
    val ratings: List<MediaRating> = emptyList(),
    val providerName: String,
) {
    val seasons: List<Int>
        get() = episodes.map { it.season }.filter { it > 0 }.distinct().sorted()

    fun episodesForSeason(season: Int): List<TvEpisode> =
        episodes.filter { it.season == season }.sortedBy { it.episode }
}

typealias TvEpisode = com.vueo.shared.core.media.EpisodeItem

private fun JSONArray?.toEpisodes(): List<TvEpisode> {
    if (this == null) return emptyList()

    return (0 until length())
        .mapNotNull { index ->
            val video = optJSONObject(index) ?: return@mapNotNull null
            val id = video.optString("id").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val season = video.optInt("season", 0)
            val episode = video.optInt("episode", 0)
            val title =
                video.optString("title").trim().takeIf { it.isNotBlank() }
                    ?: video.optString("name").trim().takeIf { it.isNotBlank() }
                    ?: if (episode > 0) "Episode $episode" else "Episode"

            TvEpisode(
                id = id,
                title = title,
                season = season,
                episode = episode,
                overview =
                    video.optString("overview").trim().takeIf { it.isNotBlank() }
                        ?: video.optString("description").trim().takeIf { it.isNotBlank() },
                thumbnail = httpsOrNull(video.optString("thumbnail")),
                released = video.optString("released").trim().takeIf { it.isNotBlank() },
            )
        }
        .distinctBy { it.id }
}

private fun JSONObject.stringList(key: String): List<String> {
    val value = opt(key)
    return when (value) {
        is JSONArray ->
            (0 until value.length())
                .mapNotNull { index ->
                    value.optString(index).trim().takeIf { it.isNotBlank() }
                }
        is String ->
            value.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        else -> emptyList()
    }
}

private fun JSONObject.flexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val raw = opt(key)
        val parsed =
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }
        if (parsed != null) return parsed
    }
    return null
}

private fun httpsOrNull(value: String?): String? =
    value?.trim()?.takeIf { it.startsWith("https://") }
