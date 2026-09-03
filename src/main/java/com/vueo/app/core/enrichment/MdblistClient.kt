package com.vueo.app.core.enrichment

import android.net.Uri
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.stremio.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Optional MDBList rating enrichment.
 *
 * One media lookup returns all available rating sources. Results are cached
 * locally in memory so opening the same Details page does not repeatedly spend
 * the user's MDBList request allowance.
 */
object MdblistClient {
    private const val API_BASE =
        "https://api.mdblist.com"

    private const val CACHE_TTL_MS =
        6 * 60 * 60_000L

    private const val MAX_CACHE_ENTRIES =
        100

    private val cache =
        object : LinkedHashMap<String, CacheEntry>(
            120,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry>?,
            ): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    suspend fun testConnection(
        apiKey: String,
    ): Boolean {
        if (apiKey.isBlank()) {
            return false
        }

        val url =
            "$API_BASE/tmdb/movie/550" +
                "?apikey=${Uri.encode(apiKey.trim())}"

        return runCatching {
            val json =
                JSONObject(
                    SimpleHttp.get(url)
                )

            json.optJSONArray("ratings") != null
        }.getOrDefault(false)
    }

    suspend fun ratings(
        media: MediaItem,
        apiKey: String,
    ): List<MediaRating> {
        if (apiKey.isBlank()) {
            return emptyList()
        }

        val target =
            lookupTarget(media)
                ?: return emptyList()

        val cacheKey =
            "${target.provider}:${target.type}:${target.id}"

        cached(cacheKey)
            ?.let {
                return it
            }

        val url =
            "$API_BASE/${target.provider}/" +
                "${target.type}/" +
                "${Uri.encode(target.id)}" +
                "?apikey=${Uri.encode(apiKey.trim())}"

        val result =
            runCatching {
                val json =
                    JSONObject(
                        SimpleHttp.get(url)
                    )

                json.optJSONArray("ratings")
                    .toRatings()
            }.getOrDefault(emptyList())

        synchronized(cache) {
            cache[cacheKey] =
                CacheEntry(
                    ratings = result,
                    updatedAt =
                        System.currentTimeMillis(),
                )
        }

        return result
    }

    private fun lookupTarget(
        media: MediaItem,
    ): LookupTarget? {
        val rawId =
            media.id.trim()

        val type =
            if (media.type == "series") {
                "show"
            } else {
                "movie"
            }

        return when {
            rawId.startsWith("tt") ->
                LookupTarget(
                    provider = "imdb",
                    type = type,
                    id = rawId,
                )

            rawId.startsWith("tmdb:") -> {
                val id =
                    rawId
                        .substringAfter("tmdb:")
                        .takeIf {
                            it.matches(
                                Regex("""\d+""")
                            )
                        }
                        ?: return null

                LookupTarget(
                    provider = "tmdb",
                    type = type,
                    id = id,
                )
            }

            rawId.matches(
                Regex("""\d+""")
            ) ->
                LookupTarget(
                    provider = "tmdb",
                    type = type,
                    id = rawId,
                )

            else -> null
        }
    }

    private fun cached(
        key: String,
    ): List<MediaRating>? =
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
                entry.ratings
            }
        }

    private data class LookupTarget(
        val provider: String,
        val type: String,
        val id: String,
    )

    private data class CacheEntry(
        val ratings: List<MediaRating>,
        val updatedAt: Long,
    )
}

data class MediaRating(
    val source: String,
    val value: Double,
    val score: Double? = null,
    val votes: Long? = null,
) {
    val label: String
        get() =
            when (source) {
                "imdb" -> "IMDb"
                "tomatoes" -> "Rotten Tomatoes"
                "metacritic" -> "Metacritic"
                "tmdb" -> "TMDB"
                "trakt" -> "Trakt"
                else -> source
            }

    val compactLabel: String
        get() =
            when (source) {
                "tomatoes" -> "RT"
                "metacritic" -> "Meta"
                else -> label
            }

    fun displayValue(): String =
        when (source) {
            "imdb",
            "tmdb" ->
                formatDecimal(value)

            "tomatoes",
            "trakt" ->
                "${value.roundToInt()}%"

            "metacritic" ->
                value.roundToInt().toString()

            else ->
                formatDecimal(value)
        }
}

private fun JSONArray?
    .toRatings():
    List<MediaRating> {
    if (this == null) {
        return emptyList()
    }

    val supported =
        setOf(
            "imdb",
            "tomatoes",
            "metacritic",
            "tmdb",
            "trakt",
        )

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val json =
                optJSONObject(index)
                    ?: continue

            val source =
                json.optString(
                    "source"
                )
                    .trim()
                    .lowercase()

            if (source !in supported) {
                continue
            }

            val value =
                json.optDouble(
                    "value",
                    Double.NaN,
                )

            if (!value.isFinite()) {
                continue
            }

            val score =
                json.optDouble(
                    "score",
                    Double.NaN,
                )
                    .takeIf {
                        it.isFinite()
                    }

            val votes =
                json.optLong(
                    "votes",
                    -1L,
                )
                    .takeIf {
                        it >= 0L
                    }

            add(
                MediaRating(
                    source = source,
                    value = value,
                    score = score,
                    votes = votes,
                )
            )
        }
    }.distinctBy {
        it.source
    }
}

private fun formatDecimal(
    value: Double,
): String {
    val rounded =
        (value * 10.0)
            .roundToInt() /
            10.0

    return if (
        rounded % 1.0 == 0.0
    ) {
        rounded
            .roundToInt()
            .toString()
    } else {
        rounded.toString()
    }
}
