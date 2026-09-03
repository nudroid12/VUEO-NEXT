package com.vueo.app.core.enrichment

import android.net.Uri
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.plugin.TmdbResolver
import com.vueo.app.core.stremio.SimpleHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional TMDB enrichment layer.
 *
 * VUEO core never depends on this object. All calls are guarded by the user
 * supplied TMDB key and callers are expected to fall back to core metadata.
 */
object TmdbEnhancementClient {
    private const val API_BASE =
        "https://api.themoviedb.org/3"

    private const val IMAGE_BASE =
        "https://image.tmdb.org/t/p"

    private const val CACHE_TTL_MS =
        30 * 60_000L

    private const val MAX_CACHE_ENTRIES =
        80

    private const val DOCUMENTARY_GENRE_ID =
        99

    private val NON_NARRATIVE_TV_GENRE_IDS =
        setOf(
            10763, // News
            10764, // Reality
            10767, // Talk
        )

    private val NON_ACTING_CHARACTER_MARKERS =
        listOf(
            "self",
            "himself",
            "herself",
            "themself",
            "themselves",
            "archive footage",
            "archival footage",
            "host",
            "presenter",
            "interviewer",
            "interviewee",
            "panelist",
            "contestant",
            "special guest",
            "guest",
            "audience member",
        )

    private val detailsCache =
        object : LinkedHashMap<String, CacheEntry<JSONObject>>(
            96,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry<JSONObject>>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    private val discoveryCache =
        object : LinkedHashMap<String, CacheEntry<List<MediaItem>>>(
            48,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry<List<MediaItem>>>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    private val personFilmographyCache =
        object : LinkedHashMap<String, CacheEntry<List<MediaItem>>>(
            32,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry<List<MediaItem>>>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    private val seasonEpisodesCache =
        object : LinkedHashMap<String, CacheEntry<List<TmdbEpisodeMetadata>>>(
            96,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry<List<TmdbEpisodeMetadata>>>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    private val resolvedIds =
        object : LinkedHashMap<String, CacheEntry<String>>(
            96,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry<String>>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    suspend fun testConnection(
        apiKey: String,
    ): Boolean {
        if (apiKey.isBlank()) {
            return false
        }

        return runCatching {
            val json = JSONObject(
                SimpleHttp.get(
                    "$API_BASE/configuration?api_key=${Uri.encode(apiKey.trim())}"
                )
            )

            json.optJSONObject("images") != null
        }.getOrDefault(false)
    }

    /**
     * Resolve an exact actor/person name and return their movie + TV cast credits.
     *
     * A null return means the query should continue to be treated as a title search.
     * An empty list means an exact acting person was found but TMDB returned no usable
     * movie/series cast credits.
     */
    suspend fun actorFilmography(
        query: String,
        apiKey: String,
        limit: Int = 80,
    ): List<MediaItem>? {
        val cleanQuery =
            query.trim()

        if (
            cleanQuery.length < 2 ||
            apiKey.isBlank() ||
            limit <= 0
        ) {
            return null
        }

        val normalizedQuery =
            normalizePersonName(
                cleanQuery
            )

        if (normalizedQuery.isBlank()) {
            return null
        }

        val cacheKey =
            "person-cast-v2:$normalizedQuery:$limit"

        cached(
            personFilmographyCache,
            cacheKey,
        )?.let {
            return it
        }

        val searchUrl =
            "$API_BASE/search/person" +
                "?query=${Uri.encode(cleanQuery)}" +
                "&include_adult=false" +
                "&language=en-US" +
                "&page=1" +
                "&api_key=${Uri.encode(apiKey.trim())}"

        val searchResults =
            runCatching {
                JSONObject(
                    SimpleHttp.get(
                        searchUrl
                    )
                ).optJSONArray(
                    "results"
                )
            }.getOrNull()
                ?: return null

        val exactActor =
            (0 until searchResults.length())
                .mapNotNull { index ->
                    searchResults
                        .optJSONObject(index)
                }
                .filter { candidate ->
                    normalizePersonName(
                        candidate
                            .optNullableString(
                                "name"
                            )
                            .orEmpty()
                    ) == normalizedQuery
                }
                .filter { candidate ->
                    val department =
                        candidate
                            .optNullableString(
                                "known_for_department"
                            )

                    department == null ||
                        department.equals(
                            "Acting",
                            ignoreCase = true,
                        )
                }
                .maxByOrNull { candidate ->
                    candidate.optDouble(
                        "popularity",
                        0.0,
                    )
                }
                ?: return null

        val personId =
            exactActor.optLong(
                "id",
                -1L,
            )

        if (personId <= 0L) {
            return null
        }

        val creditsUrl =
            "$API_BASE/person/$personId/combined_credits" +
                "?language=en-US" +
                "&api_key=${Uri.encode(apiKey.trim())}"

        val credits =
            runCatching {
                JSONObject(
                    SimpleHttp.get(
                        creditsUrl
                    )
                )
            }.getOrNull()
                ?: return null

        val cast =
            credits.optJSONArray(
                "cast"
            )
                ?: return emptyList()

        val ranked =
            buildList {
                for (
                    index in
                    0 until cast.length()
                ) {
                    val credit =
                        cast.optJSONObject(
                            index
                        )
                            ?: continue

                    if (
                        credit.optBoolean(
                            "adult",
                            false,
                        )
                    ) {
                        continue
                    }

                    val mediaType =
                        credit
                            .optNullableString(
                                "media_type"
                            )

                    val type =
                        when (mediaType) {
                            "movie" ->
                                "movie"
                            "tv" ->
                                "series"
                            else ->
                                continue
                        }

                    if (
                        !isMeaningfulActingCredit(
                            credit = credit,
                            type = type,
                        )
                    ) {
                        continue
                    }

                    val id =
                        credit.optLong(
                            "id",
                            -1L,
                        )

                    if (id <= 0L) {
                        continue
                    }

                    val name =
                        credit
                            .optNullableString(
                                if (type == "series") {
                                    "name"
                                } else {
                                    "title"
                                }
                            )
                            ?: continue

                    val poster =
                        credit
                            .optNullableString(
                                "poster_path"
                            )
                            ?.let {
                                "$IMAGE_BASE/w500$it"
                            }

                    val background =
                        credit
                            .optNullableString(
                                "backdrop_path"
                            )
                            ?.let {
                                "$IMAGE_BASE/w1280$it"
                            }

                    val date =
                        credit
                            .optNullableString(
                                if (type == "series") {
                                    "first_air_date"
                                } else {
                                    "release_date"
                                }
                            )

                    val rating =
                        credit.optDouble(
                            "vote_average",
                            0.0,
                        )
                            .takeIf {
                                it > 0.0
                            }

                    val popularity =
                        credit.optDouble(
                            "popularity",
                            0.0,
                        )

                    add(
                        popularity to
                            MediaItem(
                                id = "tmdb:$id",
                                type = type,
                                name = name,
                                poster = poster,
                                background =
                                    background
                                        ?: poster,
                                description =
                                    credit
                                        .optNullableString(
                                            "overview"
                                        ),
                                releaseInfo =
                                    date
                                        ?.take(4)
                                        ?.takeIf { year ->
                                            year.length == 4 &&
                                                year.all { ch ->
                                                    ch.isDigit()
                                                }
                                        },
                                originalLanguage =
                                    credit.optNullableString(
                                        "original_language"
                                    ),
                                catalogSources =
                                    listOf(
                                        "TMDB"
                                    ),
                                tmdbRating = rating,
                            )
                    )
                }
            }
                .sortedWith(
                    compareByDescending<
                        Pair<Double, MediaItem>
                    > {
                        it.first
                    }.thenByDescending {
                        it.second.tmdbRating
                            ?: 0.0
                    }.thenByDescending {
                        it.second.releaseInfo
                            ?.toIntOrNull()
                            ?: 0
                    }
                )
                .map {
                    it.second
                }
                .distinctBy {
                    "${it.type}:${it.id}"
                }
                .take(limit)

        synchronized(
            personFilmographyCache
        ) {
            personFilmographyCache[
                cacheKey
            ] =
                CacheEntry(
                    value = ranked,
                    updatedAt =
                        System.currentTimeMillis(),
                )
        }

        return ranked
    }

    /**
     * Convert a TMDB recommendation seed into a core-friendly item when
     * possible. TMDB detail responses expose external_ids, so an IMDb ID can
     * be restored before a Stremio metadata provider is asked for full meta.
     */
    suspend fun prepareForCore(
        item: MediaItem,
        apiKey: String,
    ): MediaItem {
        if (
            apiKey.isBlank() ||
            !item.id.startsWith("tmdb:")
        ) {
            return item
        }

        val tmdbId =
            item.id
                .substringAfter("tmdb:")
                .takeIf {
                    it.matches(Regex("""\d+"""))
                }
                ?: return item

        val details =
            details(
                mediaType = item.type,
                tmdbId = tmdbId,
                apiKey = apiKey,
            )
                ?: return item

        val imdbId =
            details
                .optNullableString("imdb_id")
                ?: details
                    .optJSONObject("external_ids")
                    ?.optNullableString("imdb_id")

        if (
            imdbId != null &&
            imdbId.startsWith("tt")
        ) {
            putResolvedId(
                media = item.copy(id = imdbId),
                tmdbId = tmdbId,
            )
        }

        return mergeDetails(
            item = item.copy(
                id = imdbId ?: item.id,
            ),
            details = details,
            metadataEnabled = true,
            artworkEnabled = true,
        )
    }

    suspend fun enrich(
        item: MediaItem,
        apiKey: String,
        metadataEnabled: Boolean,
        artworkEnabled: Boolean,
    ): MediaItem {
        if (
            apiKey.isBlank() ||
            (!metadataEnabled && !artworkEnabled)
        ) {
            return item
        }

        val tmdbId =
            resolveTmdbId(
                media = item,
                apiKey = apiKey,
            )
                ?: return item

        val details =
            details(
                mediaType = item.type,
                tmdbId = tmdbId,
                apiKey = apiKey,
            )
                ?: return item

        val enriched = mergeDetails(
            item = item,
            details = details,
            metadataEnabled = metadataEnabled,
            artworkEnabled = artworkEnabled,
        )

        if (
            item.type != "series" ||
            !metadataEnabled ||
            item.episodes.isEmpty()
        ) {
            return enriched
        }

        val episodeMetadata =
            episodeMetadata(
                tmdbId = tmdbId,
                seasons = item.episodes
                    .map { it.season }
                    .distinct(),
                apiKey = apiKey,
            )

        return enriched.copy(
            episodes = mergeEpisodeMetadata(
                episodes = enriched.episodes,
                metadata = episodeMetadata,
            )
        )
    }

    suspend fun moreLikeThis(
        item: MediaItem,
        apiKey: String,
        recommendationsEnabled: Boolean,
        similarEnabled: Boolean,
        limit: Int = 18,
    ): List<MediaItem> {
        if (
            apiKey.isBlank() ||
            (!recommendationsEnabled && !similarEnabled) ||
            limit <= 0
        ) {
            return emptyList()
        }

        val tmdbId =
            resolveTmdbId(
                media = item,
                apiKey = apiKey,
            )
                ?: return emptyList()

        val cacheKey =
            listOf(
                item.type,
                tmdbId,
                recommendationsEnabled,
                similarEnabled,
                item.sourceExtensionId.orEmpty(),
                limit,
            ).joinToString(":")

        cached(discoveryCache, cacheKey)
            ?.let {
                return it
            }

        val namespace =
            if (item.type == "series") {
                "tv"
            } else {
                "movie"
            }

        val collected =
            mutableListOf<MediaItem>()

        if (recommendationsEnabled) {
            requestDiscovery(
                path =
                    "/$namespace/$tmdbId/recommendations",
                type = item.type,
                apiKey = apiKey,
                sourceExtensionId =
                    item.sourceExtensionId,
            ).let(collected::addAll)
        }

        if (
            similarEnabled &&
            collected.size < limit
        ) {
            requestDiscovery(
                path =
                    "/$namespace/$tmdbId/similar",
                type = item.type,
                apiKey = apiKey,
                sourceExtensionId =
                    item.sourceExtensionId,
            ).let(collected::addAll)
        }

        val result =
            collected
                .asSequence()
                .filterNot {
                    candidate ->
                    candidate.id == item.id ||
                        candidate.id == "tmdb:$tmdbId"
                }
                .distinctBy {
                    "${it.type}:${it.id}"
                }
                .take(limit)
                .toList()

        synchronized(discoveryCache) {
            discoveryCache[cacheKey] =
                CacheEntry(
                    value = result,
                    updatedAt =
                        System.currentTimeMillis(),
                )
        }

        return result
    }

    private suspend fun requestDiscovery(
        path: String,
        type: String,
        apiKey: String,
        sourceExtensionId: String?,
    ): List<MediaItem> {
        val url =
            "$API_BASE$path" +
                "?language=en-US" +
                "&page=1" +
                "&api_key=${Uri.encode(apiKey.trim())}"

        return runCatching {
            val json =
                JSONObject(
                    SimpleHttp.get(url)
                )

            json.optJSONArray("results")
                .toMediaItems(
                    type = type,
                    sourceExtensionId =
                        sourceExtensionId,
                )
        }.getOrDefault(emptyList())
    }

    private suspend fun details(
        mediaType: String,
        tmdbId: String,
        apiKey: String,
    ): JSONObject? {
        val namespace =
            if (mediaType == "series") {
                "tv"
            } else {
                "movie"
            }

        val cacheKey =
            "$namespace:$tmdbId"

        cached(detailsCache, cacheKey)
            ?.let {
                return JSONObject(it.toString())
            }

        val url =
            "$API_BASE/$namespace/$tmdbId" +
                "?append_to_response=external_ids" +
                "&language=en-US" +
                "&api_key=${Uri.encode(apiKey.trim())}"

        val json =
            runCatching {
                JSONObject(
                    SimpleHttp.get(url)
                )
            }.getOrNull()
                ?: return null

        synchronized(detailsCache) {
            detailsCache[cacheKey] =
                CacheEntry(
                    value =
                        JSONObject(
                            json.toString()
                        ),
                    updatedAt =
                        System.currentTimeMillis(),
                )
        }

        return json
    }

    private suspend fun episodeMetadata(
        tmdbId: String,
        seasons: List<Int>,
        apiKey: String,
    ): List<TmdbEpisodeMetadata> =
        coroutineScope {
            val requestSlots = Semaphore(4)
            seasons
                .filter { it >= 0 }
                .map { season ->
                    async {
                        requestSlots.withPermit {
                            seasonEpisodeMetadata(
                                tmdbId = tmdbId,
                                season = season,
                                apiKey = apiKey,
                            )
                        }
                    }
                }
                .map { it.await() }
                .flatten()
        }

    private suspend fun seasonEpisodeMetadata(
        tmdbId: String,
        season: Int,
        apiKey: String,
    ): List<TmdbEpisodeMetadata> {
        val cacheKey =
            "tv:$tmdbId:season:$season"

        cached(seasonEpisodesCache, cacheKey)
            ?.let {
                return it
            }

        val url =
            "$API_BASE/tv/$tmdbId/season/$season" +
                "?language=en-US" +
                "&api_key=${Uri.encode(apiKey.trim())}"

        val result =
            runCatching {
                val json =
                    JSONObject(
                        SimpleHttp.get(url)
                    )
                val entries =
                    json.optJSONArray("episodes")
                        ?: return@runCatching emptyList()

                buildList {
                    for (index in 0 until entries.length()) {
                        val episode =
                            entries.optJSONObject(index)
                                ?: continue
                        val episodeNumber =
                            episode.optInt(
                                "episode_number",
                                -1,
                            )
                        if (episodeNumber < 0) {
                            continue
                        }

                        val seasonNumber =
                            episode.optInt(
                                "season_number",
                                season,
                            )
                        add(
                            TmdbEpisodeMetadata(
                                season = seasonNumber,
                                episode = episodeNumber,
                                title = episode
                                    .optNullableString("name"),
                                released = episode
                                    .optNullableString("air_date"),
                                overview = episode
                                    .optNullableString("overview"),
                                thumbnail = episode
                                    .optNullableString("still_path")
                                    ?.let {
                                        "$IMAGE_BASE/w500$it"
                                    },
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())

        synchronized(seasonEpisodesCache) {
            seasonEpisodesCache[cacheKey] =
                CacheEntry(
                    value = result,
                    updatedAt =
                        System.currentTimeMillis(),
                )
        }

        return result
    }

    private suspend fun resolveTmdbId(
        media: MediaItem,
        apiKey: String,
    ): String? {
        val key =
            "${media.type}:${media.id}"

        cached(resolvedIds, key)
            ?.let {
                return it
            }

        val resolved =
            TmdbResolver.resolve(
                media = media,
                apiKey = apiKey,
            )
                ?: return null

        putResolvedId(
            media = media,
            tmdbId = resolved,
        )

        return resolved
    }

    private fun putResolvedId(
        media: MediaItem,
        tmdbId: String,
    ) {
        synchronized(resolvedIds) {
            resolvedIds[
                "${media.type}:${media.id}"
            ] = CacheEntry(
                value = tmdbId,
                updatedAt =
                    System.currentTimeMillis(),
            )
        }
    }

    private fun mergeDetails(
        item: MediaItem,
        details: JSONObject,
        metadataEnabled: Boolean,
        artworkEnabled: Boolean,
    ): MediaItem {
        val overview =
            details.optNullableString(
                "overview"
            )

        val releaseDate =
            details.optNullableString(
                if (item.type == "series") {
                    "first_air_date"
                } else {
                    "release_date"
                }
            )

        val tmdbGenres =
            details
                .optJSONArray("genres")
                .toGenreNames()

        val title =
            details.optNullableString(
                if (item.type == "series") {
                    "name"
                } else {
                    "title"
                }
            )

        val tmdbPoster =
            details
                .optNullableString(
                    "poster_path"
                )
                ?.let {
                    "$IMAGE_BASE/w500$it"
                }

        val tmdbBackdrop =
            details
                .optNullableString(
                    "backdrop_path"
                )
                ?.let {
                    "$IMAGE_BASE/w1280$it"
                }

        return item.copy(
            name =
                if (
                    metadataEnabled &&
                    item.name.isBlank()
                ) {
                    title ?: item.name
                } else {
                    item.name
                },
            description =
                if (metadataEnabled) {
                    richerText(
                        item.description,
                        overview,
                    )
                } else {
                    item.description
                },
            releaseInfo =
                if (metadataEnabled) {
                    item.releaseInfo
                        ?: releaseDate
                            ?.take(4)
                            ?.takeIf {
                                it.all { ch -> ch.isDigit() }
                            }
                } else {
                    item.releaseInfo
                },
            genres =
                if (metadataEnabled) {
                    (
                        item.genres +
                            tmdbGenres
                    )
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                } else {
                    item.genres
                },
            poster =
                if (artworkEnabled) {
                    item.poster
                        ?: tmdbPoster
                } else {
                    item.poster
                },
            background =
                if (artworkEnabled) {
                    item.background
                        ?: tmdbBackdrop
                        ?: item.poster
                        ?: tmdbPoster
                } else {
                    item.background
                },
            originalLanguage =
                item.originalLanguage
                    ?: details.optNullableString(
                        "original_language"
                    ),
        )
    }

    private fun mergeEpisodeMetadata(
        episodes: List<EpisodeItem>,
        metadata: List<TmdbEpisodeMetadata>,
    ): List<EpisodeItem> {
        if (metadata.isEmpty()) {
            return episodes
        }

        val metadataByNumber =
            metadata.associateBy {
                it.season to it.episode
            }

        return episodes.map { episode ->
            val candidate =
                metadataByNumber[
                    episode.season to episode.episode
                ] ?: return@map episode

            episode.copy(
                title =
                    if (
                        isGenericEpisodeTitle(
                            title = episode.title,
                            episodeNumber = episode.episode,
                        ) &&
                        !candidate.title.isNullOrBlank()
                    ) {
                        candidate.title.orEmpty()
                    } else {
                        episode.title
                    },
                released =
                    episode.released
                        ?: candidate.released,
                overview = richerText(
                    episode.overview,
                    candidate.overview,
                ),
                thumbnail =
                    episode.thumbnail
                        ?: candidate.thumbnail,
            )
        }
    }

    private fun isGenericEpisodeTitle(
        title: String,
        episodeNumber: Int,
    ): Boolean {
        val normalized =
            title
                .trim()
                .lowercase()

        if (normalized.isBlank()) {
            return true
        }

        return Regex(
            "^(episode|ep|e)\\s*0*${episodeNumber}\$"
        ).matches(normalized)
    }

    private fun richerText(
        current: String?,
        candidate: String?,
    ): String? {
        val a =
            current
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val b =
            candidate
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        return when {
            a == null -> b
            b == null -> a
            b.length > a.length -> b
            else -> a
        }
    }

    /**
     * Keep actor search focused on narrative acting credits. TMDB's cast array
     * also contains self appearances, interviews, award shows and one-episode
     * guest credits, which are technically cast credits but are not useful as
     * a normal actor filmography inside VUEO.
     */
    private fun isMeaningfulActingCredit(
        credit: JSONObject,
        type: String,
    ): Boolean {
        val character =
            credit
                .optNullableString(
                    "character"
                )
                .orEmpty()
                .lowercase()
                .replace(
                    Regex(
                        """[^\p{L}\p{N}]+"""
                    ),
                    " ",
                )
                .trim()

        if (
            character.isNotBlank() &&
            NON_ACTING_CHARACTER_MARKERS.any { marker ->
                character == marker ||
                    character.startsWith(
                        "$marker "
                    ) ||
                    character.contains(
                        " $marker "
                    ) ||
                    character.endsWith(
                        " $marker"
                    )
            }
        ) {
            return false
        }

        val genreIds =
            credit
                .optJSONArray(
                    "genre_ids"
                )
                .toIntSet()

        if (type == "series") {
            if (
                credit.has(
                    "episode_count"
                ) &&
                !credit.isNull(
                    "episode_count"
                ) &&
                credit.optInt(
                    "episode_count",
                    0,
                ) <= 1
            ) {
                return false
            }

            if (
                genreIds.any { genreId ->
                    genreId in
                        NON_NARRATIVE_TV_GENRE_IDS
                }
            ) {
                return false
            }
        }

        if (
            DOCUMENTARY_GENRE_ID in genreIds &&
            (
                character.isBlank() ||
                    character == "narrator"
            )
        ) {
            return false
        }

        return true
    }

    private fun JSONArray?.toIntSet(): Set<Int> {
        if (this == null) {
            return emptySet()
        }

        return buildSet {
            for (index in 0 until length()) {
                val value =
                    optInt(
                        index,
                        Int.MIN_VALUE,
                    )

                if (value != Int.MIN_VALUE) {
                    add(value)
                }
            }
        }
    }

    private fun normalizePersonName(
        value: String,
    ): String =
        value
            .lowercase()
            .replace(
                Regex(
                    """[^\p{L}\p{N}]+"""
                ),
                " ",
            )
            .trim()
            .replace(
                Regex(
                    """\s+"""
                ),
                " ",
            )

    private fun <T> cached(
        cache: MutableMap<String, CacheEntry<T>>,
        key: String,
    ): T? =
        synchronized(cache) {
            val entry =
                cache[key]
                    ?: return@synchronized null

            val age =
                System.currentTimeMillis() -
                    entry.updatedAt

            if (age > CACHE_TTL_MS) {
                cache.remove(key)
                null
            } else {
                entry.value
            }
        }

    private data class CacheEntry<T>(
        val value: T,
        val updatedAt: Long,
    )

    private data class TmdbEpisodeMetadata(
        val season: Int,
        val episode: Int,
        val title: String?,
        val released: String?,
        val overview: String?,
        val thumbnail: String?,
    )
}

private fun JSONArray?
    .toMediaItems(
        type: String,
        sourceExtensionId: String?,
    ): List<MediaItem> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val json =
                optJSONObject(index)
                    ?: continue

            val id =
                json.optLong(
                    "id",
                    -1L,
                )

            if (id <= 0L) {
                continue
            }

            val name =
                json.optNullableString(
                    if (type == "series") {
                        "name"
                    } else {
                        "title"
                    }
                )
                    ?: continue

            val poster =
                json.optNullableString(
                    "poster_path"
                )
                    ?.let {
                        "https://image.tmdb.org/t/p/w500$it"
                    }

            val background =
                json.optNullableString(
                    "backdrop_path"
                )
                    ?.let {
                        "https://image.tmdb.org/t/p/w1280$it"
                    }

            val date =
                json.optNullableString(
                    if (type == "series") {
                        "first_air_date"
                    } else {
                        "release_date"
                    }
                )

            add(
                MediaItem(
                    id = "tmdb:$id",
                    type = type,
                    name = name,
                    poster = poster,
                    background =
                        background ?: poster,
                    description =
                        json.optNullableString(
                            "overview"
                        ),
                    releaseInfo =
                        date
                            ?.take(4)
                            ?.takeIf {
                                it.all { ch -> ch.isDigit() }
                            },
                    originalLanguage =
                        json.optNullableString(
                            "original_language"
                        ),
                    sourceExtensionId =
                        sourceExtensionId,
                )
            )
        }
    }
}

private fun JSONArray?
    .toGenreNames():
    List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            optJSONObject(index)
                ?.optNullableString(
                    "name"
                )
                ?.let(::add)
        }
    }
}

private fun JSONObject
    .optNullableString(
        key: String,
    ): String? {
    if (
        !has(key) ||
        isNull(key)
    ) {
        return null
    }

    return optString(key)
        .trim()
        .takeIf {
            it.isNotBlank() &&
                it != "null"
        }
}
