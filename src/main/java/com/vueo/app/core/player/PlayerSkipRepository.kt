package com.vueo.app.core.player

import com.vueo.app.core.stremio.SimpleHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class PlayerSkipKind {
    INTRO,
    RECAP,
    ENDING,
}

data class PlayerSkipSegment(
    val startMs: Long,
    val endMs: Long,
    val kind: PlayerSkipKind,
    val provider: String,
) {
    val key: String
        get() = "${kind.name}:$startMs:$endMs"
}

object PlayerSkipRepository {
    private val cache =
        ConcurrentHashMap<String, List<PlayerSkipSegment>>()

    suspend fun segments(
        imdbId: String,
        season: Int,
        episode: Int,
    ): List<PlayerSkipSegment> {
        val normalizedImdbId = imdbId.lowercase()
        val cacheKey = "$normalizedImdbId:$season:$episode"
        cache[cacheKey]?.let { return it }

        val loaded = coroutineScope {
            val introDb = async {
                fetchIntroDb(normalizedImdbId, season, episode)
            }
            val aniSkip = async {
                fetchAniSkip(normalizedImdbId, season, episode)
            }
            merge(
                primary = introDb.await(),
                fallback = aniSkip.await(),
            )
        }

        cache[cacheKey] = loaded
        return loaded
    }

    private suspend fun fetchIntroDb(
        imdbId: String,
        season: Int,
        episode: Int,
    ): List<PlayerSkipSegment> = runCatching {
        val url =
            "https://api.introdb.app/segments" +
                "?imdb_id=${encode(imdbId)}" +
                "&season=$season&episode=$episode"
        val root = JSONObject(SimpleHttp.get(url))
        buildList {
            parseIntroDbSegment(
                root.optJSONObject("intro"),
                PlayerSkipKind.INTRO,
            )?.let(::add)
            parseIntroDbSegment(
                root.optJSONObject("recap"),
                PlayerSkipKind.RECAP,
            )?.let(::add)
            parseIntroDbSegment(
                root.optJSONObject("outro"),
                PlayerSkipKind.ENDING,
            )?.let(::add)
        }
    }.getOrDefault(emptyList())

    private fun parseIntroDbSegment(
        value: JSONObject?,
        kind: PlayerSkipKind,
    ): PlayerSkipSegment? {
        value ?: return null
        val startMs = value.timeMs("start") ?: return null
        val endMs = value.timeMs("end") ?: return null
        return segment(startMs, endMs, kind, "IntroDB")
    }

    private suspend fun fetchAniSkip(
        imdbId: String,
        season: Int,
        episode: Int,
    ): List<PlayerSkipSegment> = runCatching {
        val malId = resolveMyAnimeListId(imdbId, season)
            ?: return@runCatching emptyList()
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
                "?types=op&types=ed&types=recap" +
                "&types=mixed-op&types=mixed-ed&episodeLength=0"
        val root = JSONObject(SimpleHttp.get(url))
        if (!root.optBoolean("found", false)) {
            return@runCatching emptyList()
        }
        root.optJSONArray("results")
            .orEmpty()
            .mapObjects { item ->
                val interval = item.optJSONObject("interval")
                    ?: return@mapObjects null
                val kind = when (
                    item.optString("skipType").lowercase()
                ) {
                    "op", "mixed-op" -> PlayerSkipKind.INTRO
                    "recap" -> PlayerSkipKind.RECAP
                    "ed", "mixed-ed" -> PlayerSkipKind.ENDING
                    else -> null
                } ?: return@mapObjects null
                segment(
                    startMs = (interval.optDouble("startTime") * 1_000).toLong(),
                    endMs = (interval.optDouble("endTime") * 1_000).toLong(),
                    kind = kind,
                    provider = "AniSkip",
                )
            }
    }.getOrDefault(emptyList())

    private suspend fun resolveMyAnimeListId(
        imdbId: String,
        season: Int,
    ): Int? {
        val url =
            "https://arm.haglund.dev/api/v2/imdb" +
                "?id=${encode(imdbId)}&include=myanimelist"
        val root = JSONArray(SimpleHttp.get(url))
        if (root.length() == 0) return null
        val preferredIndex = (season - 1).coerceIn(0, root.length() - 1)
        root.optJSONObject(preferredIndex)
            ?.optPositiveInt("myanimelist")
            ?.let { return it }
        for (index in 0 until root.length()) {
            root.optJSONObject(index)
                ?.optPositiveInt("myanimelist")
                ?.let { return it }
        }
        return null
    }

    private fun merge(
        primary: List<PlayerSkipSegment>,
        fallback: List<PlayerSkipSegment>,
    ): List<PlayerSkipSegment> {
        val primaryKinds = primary.mapTo(mutableSetOf()) { it.kind }
        return (
            primary + fallback.filter { it.kind !in primaryKinds }
        ).sortedBy { it.startMs }
    }

    private fun segment(
        startMs: Long,
        endMs: Long,
        kind: PlayerSkipKind,
        provider: String,
    ): PlayerSkipSegment? =
        if (startMs >= 0L && endMs > startMs) {
            PlayerSkipSegment(startMs, endMs, kind, provider)
        } else {
            null
        }

    private fun JSONObject.timeMs(prefix: String): Long? {
        optNumber("${prefix}_ms")?.toLong()?.let { return it }
        optNumber("${prefix}_sec")?.toDouble()
            ?.let { return (it * 1_000).toLong() }
        return parseTime(optString(prefix, ""))
    }

    private fun JSONObject.optNumber(key: String): Number? =
        when (val value = opt(key)) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }

    private fun JSONObject.optPositiveInt(key: String): Int? =
        optNumber(key)?.toInt()?.takeIf { it > 0 }

    private fun parseTime(value: String): Long? {
        value.toDoubleOrNull()?.let { return (it * 1_000).toLong() }
        val parts = value.split(':').mapNotNull(String::toDoubleOrNull)
        if (parts.isEmpty() || parts.size > 3) return null
        var seconds = 0.0
        parts.forEach { seconds = seconds * 60 + it }
        return (seconds * 1_000).toLong()
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private inline fun JSONArray.mapObjects(
        transform: (JSONObject) -> PlayerSkipSegment?,
    ): List<PlayerSkipSegment> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(transform)?.let(::add)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
