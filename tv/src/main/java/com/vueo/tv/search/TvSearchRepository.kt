package com.vueo.tv.search

import android.content.Context
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.data.TvDiscoverySearchType
import com.vueo.tv.data.TvHomeRepository
import com.vueo.tv.data.TvUnifiedDiscovery
import com.vueo.tv.data.TvMediaItem
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class TvSearchMode {
    TITLE,
    ACTOR,
}

enum class TvSearchType {
    ALL,
    MOVIE,
    SERIES,
    ANIME,
}

enum class TvSearchSort {
    POPULAR,
    TRENDING,
    NEWEST,
}

data class TvSearchResult(
    val media: TvMediaItem,
    val providerName: String,
)

class TvSearchRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val homeRepository = TvHomeRepository(appContext)
    private val contentStore = TvContentManagerStore(appContext)
    private val discovery = TvUnifiedDiscovery(appContext)
    private val pluginStore = PluginStore(appContext)

    suspend fun actorSearchAvailable(): Boolean =
        pluginStore.tmdbApiKey().isNotBlank() || discovery.hasActorSearchSource()

    fun searchLocal(
        query: String,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val homeItems =
            homeRepository.cached()
                ?.rows
                .orEmpty()
                .flatMap { row ->
                    row.items.map { item ->
                        TvSearchResult(
                            media = item,
                            providerName = row.providerName.ifBlank { "Content Manager" },
                        )
                    }
                }

        return rankAndDedupe(
            query = cleanQuery,
            items = homeItems + cachedRemoteItems(),
            type = type,
        )
    }

    suspend fun searchRemote(
        query: String,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val remote = discovery.search(
            query = cleanQuery,
            type = type.toDiscoveryType(),
        ).map { result ->
            TvSearchResult(
                media = result.media,
                providerName = result.providerName,
            )
        }

        val ranked = rankAndDedupe(
            query = cleanQuery,
            items = remote,
            type = type,
        )

        if (ranked.isNotEmpty()) {
            persistRemoteItems(ranked)
        }

        return ranked
    }

    suspend fun searchActorRemote(
        query: String,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val addonResults =
            discovery.searchActor(cleanQuery)
                .map { result ->
                    TvSearchResult(
                        media = result.media,
                        providerName = result.providerName,
                    )
                }

        val tmdbResults =
            pluginStore.tmdbApiKey()
                .takeIf(String::isNotBlank)
                ?.let { key ->
                    TmdbEnhancementClient.actorFilmography(
                        query = cleanQuery,
                        apiKey = key,
                        limit = MAX_RESULTS,
                    )
                }
                .orEmpty()
                .map { media ->
                    TvSearchResult(
                        media = media,
                        providerName = "TMDB",
                    )
                }

        return (addonResults + tmdbResults)
            .filter { type.accepts(it.media) }
            .distinctBy { canonicalKey(it.media) }
            .take(MAX_RESULTS)
    }

    fun availableGenres(items: List<TvSearchResult>): List<String> =
        items
            .flatMap { it.media.genres }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sorted()
            .take(MAX_GENRES)

    fun filterAndSort(
        items: List<TvSearchResult>,
        type: TvSearchType,
        genre: String?,
        sort: TvSearchSort,
        query: String,
        actorMode: Boolean,
    ): List<TvSearchResult> {
        val filtered = items
            .asSequence()
            .filter { type.accepts(it.media) }
            .filter { result ->
                genre == null || result.media.genres.any { it.equals(genre, ignoreCase = true) }
            }
            .distinctBy { canonicalKey(it.media) }
            .toList()

        if (actorMode && sort != TvSearchSort.NEWEST) return filtered

        val normalizedQuery = normalize(query)
        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        fun relevance(result: TvSearchResult): Int =
            relevanceScore(normalizedQuery, queryTokens, result.media)

        return when (sort) {
            TvSearchSort.POPULAR ->
                filtered.sortedWith(
                    compareByDescending<TvSearchResult> { relevance(it) }
                        .thenByDescending { it.media.imdbRating ?: it.media.tmdbRating ?: 0.0 }
                        .thenByDescending { releaseYear(it.media) }
                )
            TvSearchSort.TRENDING ->
                filtered.sortedByDescending { relevance(it) }
            TvSearchSort.NEWEST ->
                filtered.sortedWith(
                    compareByDescending<TvSearchResult> { relevance(it) }
                        .thenByDescending { releaseYear(it.media) }
                        .thenByDescending { it.media.imdbRating ?: it.media.tmdbRating ?: 0.0 }
                )
        }
    }

    fun merge(
        query: String,
        type: TvSearchType,
        local: List<TvSearchResult>,
        remote: List<TvSearchResult>,
    ): List<TvSearchResult> =
        rankAndDedupe(
            query = query,
            items = local + remote,
            type = type,
        )

    private fun rankAndDedupe(
        query: String,
        items: List<TvSearchResult>,
        type: TvSearchType,
    ): List<TvSearchResult> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

        return items
            .asSequence()
            .filter { result -> type.accepts(result.media) }
            .map { result ->
                result to relevanceScore(normalizedQuery, queryTokens, result.media)
            }
            .filter { (_, score) -> score >= MIN_RELEVANCE_SCORE }
            .sortedWith(
                compareByDescending<Pair<TvSearchResult, Int>> { it.second }
                    .thenByDescending { (_, score) -> score }
                    .thenByDescending { (result, _) -> result.media.imdbRating ?: 0.0 }
                    .thenBy { (result, _) -> result.media.name.lowercase(Locale.ROOT) }
            )
            .distinctBy { (result, _) -> canonicalKey(result.media) }
            .take(MAX_RESULTS)
            .map { it.first }
            .toList()
    }

    private fun relevanceScore(
        normalizedQuery: String,
        queryTokens: List<String>,
        media: TvMediaItem,
    ): Int {
        val title = normalize(media.name)
        if (title.isBlank()) return 0

        var score = 0
        when {
            title == normalizedQuery -> score += 1_000
            title.startsWith(normalizedQuery) -> score += 850
            title.contains(normalizedQuery) -> score += 650
        }

        val titleTokens = title.split(' ').filter { it.isNotBlank() }
        val matchedTokens =
            queryTokens.count { queryToken ->
                titleTokens.any { titleToken ->
                    titleToken == queryToken || titleToken.startsWith(queryToken)
                }
            }

        if (queryTokens.isNotEmpty()) {
            score += matchedTokens * 90
            if (matchedTokens == queryTokens.size) score += 240
        }

        val releaseInfo = media.releaseInfo
        releaseInfo
            ?.substring(0, minOf(releaseInfo.length, 4))
            ?.toIntOrNull()
            ?.let { year ->
                if (normalizedQuery.contains(year.toString())) score += 100
            }

        media.imdbRating?.let { score += (it * 3).toInt() }
        return score
    }

    private fun persistRemoteItems(items: List<TvSearchResult>) {
        val merged =
            (items + cachedRemoteItems())
                .distinctBy { canonicalKey(it.media) }
                .take(MAX_CACHED_ITEMS)

        val array = JSONArray()
        merged.forEach { result ->
            array.put(
                JSONObject()
                    .put("providerName", result.providerName)
                    .put("media", mediaToJson(result.media))
            )
        }

        prefs.edit()
            .putString(KEY_SEARCH_CACHE, array.toString())
            .putInt(KEY_SEARCH_CACHE_REVISION, contentStore.discoveryRevision())
            .apply()
    }

    private fun cachedRemoteItems(): List<TvSearchResult> {
        if (prefs.getInt(KEY_SEARCH_CACHE_REVISION, Int.MIN_VALUE) != contentStore.discoveryRevision()) {
            return emptyList()
        }
        val raw = prefs.getString(KEY_SEARCH_CACHE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    val media = entry.optJSONObject("media")?.let(::mediaFromJson)
                        ?: return@mapNotNull null
                    TvSearchResult(
                        media = media,
                        providerName = entry.optString("providerName", "Content Manager"),
                    )
                }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "vueo_tv_search"
        private const val KEY_SEARCH_CACHE = "search_cache_v2"
        private const val KEY_SEARCH_CACHE_REVISION = "search_cache_revision_v2"
        private const val MAX_CACHED_ITEMS = 120
        private const val MAX_RESULTS = 60
        private const val MAX_GENRES = 18
        private const val MIN_RELEVANCE_SCORE = 180
    }
}

private fun TvSearchType.toDiscoveryType(): TvDiscoverySearchType =
    when (this) {
        TvSearchType.ALL,
        TvSearchType.ANIME -> TvDiscoverySearchType.ALL
        TvSearchType.MOVIE -> TvDiscoverySearchType.MOVIE
        TvSearchType.SERIES -> TvDiscoverySearchType.SERIES
    }

private fun TvSearchType.accepts(media: TvMediaItem): Boolean {
    val mediaType = media.type.trim().lowercase(Locale.ROOT)
    val anime = isAnime(media)
    return when (this) {
        TvSearchType.ALL -> true
        TvSearchType.MOVIE -> mediaType == "movie" && !anime
        TvSearchType.SERIES -> (mediaType == "series" || mediaType == "tv") && !anime
        TvSearchType.ANIME -> anime
    }
}

private fun isAnime(media: TvMediaItem): Boolean =
    media.type.equals("anime", ignoreCase = true) ||
        media.genres.any { it.equals("anime", ignoreCase = true) } ||
        media.sourceExtensionId?.contains("anime", ignoreCase = true) == true ||
        media.id.contains("anime", ignoreCase = true)

private fun releaseYear(media: TvMediaItem): Int =
    Regex("(19|20)\\d{2}")
        .find(media.releaseInfo.orEmpty())
        ?.value
        ?.toIntOrNull()
        ?: 0

private fun normalize(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun canonicalKey(media: TvMediaItem): String {
    val year =
        media.releaseInfo
            ?.take(4)
            ?.toIntOrNull()
            ?.toString()
            .orEmpty()
    return "${media.type.lowercase(Locale.ROOT)}:${normalize(media.name)}:$year"
}

private fun JSONObject.toMediaItem(fallbackType: String): TvMediaItem? {
    val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").trim().takeIf { it.isNotBlank() } ?: return null

    return TvMediaItem(
        id = id,
        type = optString("type", fallbackType).ifBlank { fallbackType },
        name = name,
        poster = httpsOrNull(optString("poster")),
        background = httpsOrNull(optString("background")),
        description = optString("description").trim().takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").trim().takeIf { it.isNotBlank() },
        genres = optJSONArray("genres").toStringList(),
        imdbRating = flexibleDouble("imdbRating", "imdb_rating"),
    )
}

private fun httpsOrNull(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.startsWith("https://") }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            optString(index)
                .trim()
                .takeIf { it.isNotBlank() }
        }
}

private fun JSONObject.flexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val parsed =
            when (val value = opt(key)) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        if (parsed != null) return parsed
    }
    return null
}

private fun mediaToJson(media: TvMediaItem): JSONObject =
    JSONObject()
        .put("id", media.id)
        .put("type", media.type)
        .put("name", media.name)
        .put("poster", media.poster)
        .put("background", media.background)
        .put("description", media.description)
        .put("releaseInfo", media.releaseInfo)
        .put("genres", JSONArray(media.genres))
        .put("imdbRating", media.imdbRating)

private fun mediaFromJson(json: JSONObject): TvMediaItem? {
    val id = json.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = json.optString("name").trim().takeIf { it.isNotBlank() } ?: return null

    return TvMediaItem(
        id = id,
        type = json.optString("type", "movie"),
        name = name,
        poster = json.optString("poster").takeIf { it.startsWith("https://") },
        background = json.optString("background").takeIf { it.startsWith("https://") },
        description = json.optString("description").takeIf { it.isNotBlank() },
        releaseInfo = json.optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = json.optJSONArray("genres").toStringList(),
        imdbRating = json.optDouble("imdbRating", Double.NaN).takeIf { !it.isNaN() },
    )
}
