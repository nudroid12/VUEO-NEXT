package com.vueo.shared.core.plugin

import com.vueo.shared.core.media.EpisodeItem
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Typed anime identity bridge. Local anime episode numbers are never sent directly to TMDB providers. */
object AnimeIdResolver {
    data class Result(val tmdbId: String? = null, val season: Int? = null, val episode: Int? = null, val reason: String)
    private data class Entry(val value: JSONObject, val at: Long)
    private val cache = object : LinkedHashMap<String, Entry>(65, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 64
    }
    private fun identity(rawId: String): Pair<String, String>? {
        val match = Regex("^(mal|myanimelist|anilist|kitsu):([1-9]\\d*)$", RegexOption.IGNORE_CASE)
            .matchEntire(rawId.trim()) ?: return null
        val source = match.groupValues[1].lowercase().let { if (it == "mal") "myanimelist" else it }
        return source to match.groupValues[2]
    }
    fun supports(rawId: String): Boolean = identity(rawId) != null
    private fun positive(raw: String): String? = raw.takeIf { it.matches(Regex("[1-9]\\d*")) }
    private fun date(raw: String?): Long? = try {
        val text = raw?.take(10)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        if (text == null) null else SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(text)?.time?.div(86_400_000L)
    } catch (_: Exception) { null }
    private fun encode(raw: String): String = URLEncoder.encode(raw, StandardCharsets.UTF_8.name())
    private suspend fun document(cacheKey: String, url: String): JSONObject {
        synchronized(cache) { cache[cacheKey] }?.takeIf { System.currentTimeMillis() - it.at < 3_600_000L }?.let { return it.value }
        val value = JSONObject(PluginHttp.getText(url))
        synchronized(cache) { cache[cacheKey] = Entry(value, System.currentTimeMillis()) }
        return value
    }

    suspend fun resolve(rawId: String, mediaType: String, episode: EpisodeItem?, apiKey: String,
                        knownTmdbId: String? = null): Result {
        val (source, id) = identity(rawId) ?: return Result(reason = "unsupported anime ID")
        val type = when (mediaType.lowercase()) { "tv", "series" -> "tv"; "movie" -> "movie"; else -> return Result(reason = "unsupported media type") }
        try {
            val row = document("arm:$source:$id", "https://arm.haglund.dev/api/v2/ids?source=$source&id=$id")
            if (row.optString(source) != id) return Result(reason = "anime mapping returned a different source ID")
            val movie = row.optString("media") == "MOVIE"
            if (row.optString("media") !in setOf("TV", "TV_SHORT", "OVA", "ONA", "SPECIAL", "MUSIC", "MOVIE") || movie != (type == "movie")) return Result(reason = "anime mapping media type disagrees")
            val tmdb = positive(row.optString("themoviedb")) ?: return Result(reason = "no TMDB mapping for the exact anime ID")
            if (knownTmdbId != null && knownTmdbId != tmdb) return Result(reason = "metadata and anime mapping TMDB IDs disagree")
            if (movie) return Result(tmdbId = tmdb, reason = "ANIME_ID")
            if (episode == null || episode.episode <= 0) return Result(reason = "no selected anime episode")
            val season = row.optString("themoviedb-season").toIntOrNull()?.takeIf { it >= 0 }
                ?: return Result(reason = "no explicit TMDB season mapping")
            if (apiKey.isBlank()) return Result(reason = "TMDB API key required to verify anime episode coordinates")
            val field = when (source) { "myanimelist" -> "mal_id"; "anilist" -> "anilist_id"; else -> "kitsu_id" }
            val dataset = document("episodes:$source:$id", "https://api.ani.zip/mappings?$field=$id")
            val mapping = dataset.optJSONObject("mappings")
            if (mapping?.optString(field) != id || mapping.optString("themoviedb_id") != tmdb)
                return Result(reason = "episode dataset identity disagrees with anime mapping")
            val episodes = dataset.optJSONObject("episodes") ?: return Result(reason = "anime episode dataset missing")
            val candidates = episodes.keys().asSequence().mapNotNull { episodes.optJSONObject(it) }
                .filter { it.optString("episode").toIntOrNull() == episode.episode }.toList()
            if (candidates.size != 1) return Result(reason = "selected anime episode is missing or ambiguous")
            val aired = date(candidates.single().optString("airDate")) ?: date(candidates.single().optString("airdate"))
            val day = aired ?: return Result(reason = "no episode air date to verify TMDB coordinates")
            val data = document("tmdb:$tmdb:$season", "https://api.themoviedb.org/3/tv/$tmdb/season/$season?api_key=${encode(apiKey.trim())}")
            if (data.optInt("season_number", -1) != season) return Result(reason = "TMDB returned a different season")
            val entries = data.optJSONArray("episodes") ?: return Result(reason = "TMDB season episodes missing")
            val matches = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }.filter {
                val targetDay = date(it.optString("air_date"))
                it.optInt("season_number", -1) == season && it.optInt("episode_number", 0) > 0 &&
                    targetDay != null && kotlin.math.abs(targetDay - day) <= 1L
            }.map { it.optInt("episode_number") }.distinct()
            if (matches.size != 1) return Result(reason = "TMDB episode air-date match is missing or ambiguous")
            return Result(tmdb, season, matches.single(), "ANIME_EPISODE")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return Result(reason = "exact anime ID or episode lookup failed") }
    }
}
