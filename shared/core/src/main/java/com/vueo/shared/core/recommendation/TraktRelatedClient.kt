package com.vueo.shared.core.recommendation

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.plugin.PluginHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public title-related discovery only. No user token, history sync or TMDB recommendations. */
object TraktRelatedClient {
    private const val MAX_CANDIDATES = 18
    private const val MAX_BODY_BYTES = 1024 * 1024
    private const val TTL_MS = 30 * 60_000L
    private const val FAILURE_TTL_MS = 2 * 60_000L
    private val gate = Mutex()
    private val client by lazy {
        PluginHttp.client.newBuilder().callTimeout(7, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    private data class Entry(val items: List<MediaItem>, val expiresAt: Long)
    // Accessed only under gate. Includes the app identity to isolate changed credentials.
    private val cache = object : LinkedHashMap<String, Entry>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 40
    }
    private var blockedClientId = ""
    private var blockedUntil = 0L

    suspend fun testConnection(clientId: String): Boolean {
        val key = clientId.trim()
        if (key.isBlank()) return false
        return withTimeoutOrNull(8_000L) {
            gate.withLock {
                try {
                    JSONArray(get("/movies/tt0137523/related?limit=1&extended=full", key))
                    if (blockedClientId == key) blockedUntil = 0L
                    cache.keys.filter { it.startsWith("$key:") }.forEach { cache.remove(it) }
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            }
        } ?: false
    }

    suspend fun related(item: MediaItem, clientId: String): List<MediaItem> {
        val key = clientId.trim()
        if (key.isBlank()) return emptyList()
        val type = when (item.type.lowercase()) {
            "movie", "movies", "film" -> "movie"
            "series", "tv", "show", "shows" -> "show"
            else -> return emptyList()
        }
        // The timeout includes waiting for another request, so no unbounded request queue.
        return withTimeoutOrNull(8_000L) {
            gate.withLock {
                val now = System.currentTimeMillis()
                val cacheKey = "$key:$type:${item.id}"
                cache[cacheKey]?.takeIf { it.expiresAt > now }?.let {
                    return@withLock it.items.map { media -> media.copy(sourceExtensionId = item.sourceExtensionId) }
                }
                if (blockedClientId == key && blockedUntil > now) return@withLock emptyList()
                try {
                    val imdb = Regex("^tt[0-9]+$").takeIf { it.matches(item.id) }?.let { item.id }
                    val target = imdb ?: resolveTarget(item, type, key)
                    if (target == null) {
                        cache[cacheKey] = Entry(emptyList(), now + FAILURE_TTL_MS)
                        return@withLock emptyList()
                    }
                    val namespace = if (type == "show") "shows" else "movies"
                    val raw = JSONArray(get("/$namespace/$target/related?extended=full&limit=$MAX_CANDIDATES&page=1", key))
                    val mediaType = if (type == "show") "series" else "movie"
                    val result = (0 until minOf(raw.length(), MAX_CANDIDATES)).mapNotNull { index ->
                        raw.optJSONObject(index)?.let { parse(it, mediaType) }
                    }.filterNot { it.id == item.id }.distinctBy { it.id }
                    cache[cacheKey] = Entry(result, System.currentTimeMillis() + TTL_MS)
                    result.map { it.copy(sourceExtensionId = item.sourceExtensionId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: HttpFailure) {
                    if (failure.status in listOf(401, 403, 429)) {
                        blockedClientId = key
                        blockedUntil = System.currentTimeMillis() + failure.pauseMs
                    }
                    cache[cacheKey] = Entry(emptyList(), System.currentTimeMillis() + FAILURE_TTL_MS)
                    emptyList()
                } catch (_: Exception) {
                    cache[cacheKey] = Entry(emptyList(), System.currentTimeMillis() + FAILURE_TTL_MS)
                    emptyList()
                }
            }
        } ?: emptyList()
    }

    private suspend fun resolveTarget(item: MediaItem, type: String, key: String): String? {
        // Never guess by title: ambiguous/remade titles must not get unrelated recommendations.
        val tmdb = item.id.removePrefix("tmdb:").takeIf {
            item.id.startsWith("tmdb:") && it.isNotEmpty() && it.all(Char::isDigit)
        } ?: return null
        val results = JSONArray(get("/search/tmdb/$tmdb?type=$type&limit=5", key))
        for (index in 0 until minOf(results.length(), 5)) {
            val entry = results.optJSONObject(index) ?: continue
            val media = entry.optJSONObject(type) ?: continue
            val ids = media.optJSONObject("ids") ?: continue
            if (ids.optLong("tmdb", -1).toString() != tmdb) continue
            val trakt = ids.optLong("trakt", 0)
            if (trakt > 0) return trakt.toString()
        }
        return null
    }

    private fun parse(json: JSONObject, type: String): MediaItem? {
        val ids = json.optJSONObject("ids") ?: return null
        val imdb = ids.optString("imdb").takeIf { Regex("^tt[0-9]+$").matches(it) }
        val tmdb = ids.optLong("tmdb", 0).takeIf { it > 0 }
        val id = imdb ?: tmdb?.let { "tmdb:$it" } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val images = json.optJSONObject("images")
        fun image(field: String): String? {
            val raw = images?.optJSONArray(field)?.optString(0).orEmpty()
            if (raw.isBlank() || raw == "null") return null
            return if (raw.startsWith("https://")) raw else if (!raw.contains("://")) "https://$raw" else null
        }
        val genres = json.optJSONArray("genres")
        return MediaItem(
            id = id, type = type, name = title,
            poster = image("poster"), background = image("fanart"),
            description = json.optString("overview").takeIf { it.isNotBlank() && it != "null" },
            releaseInfo = json.optInt("year", 0).takeIf { it > 0 }?.toString(),
            originalLanguage = json.optString("language").takeIf { it.isNotBlank() && it != "null" },
            genres = (0 until (genres?.length() ?: 0)).mapNotNull {
                genres?.optString(it)?.replace('-', ' ')?.takeIf(String::isNotBlank)
            },
            runtimeMinutes = json.optInt("runtime", 0).takeIf { it > 0 },
            catalogSources = listOf("Trakt"),
            recommendationRating = json.optDouble("rating", 0.0).takeIf { it > 0.0 },
            recommendationVoteCount = json.optInt("votes", 0).takeIf { it > 0 },
        )
    }

    private class HttpFailure(val status: Int, val pauseMs: Long) : IOException("Trakt HTTP $status")

    private suspend fun get(path: String, key: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("https://api.trakt.tv$path")
            .header("Accept", "application/json").header("Content-Type", "application/json")
            .header("User-Agent", "VUEO/0.9.6").header("trakt-api-version", "2")
            .header("trakt-api-key", key).build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) {
                            val pause = it.header("Retry-After")?.toLongOrNull()
                                ?.coerceIn(1, 86_400)?.times(1000L) ?: FAILURE_TTL_MS
                            throw HttpFailure(it.code, pause)
                        }
                        val body = it.body
                        if (body.contentLength() > MAX_BODY_BYTES) throw IOException("Trakt response too large")
                        body.byteStream().use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_BODY_BYTES) throw IOException("Trakt response too large")
                                output.write(buffer, 0, count)
                            }
                            String(output.toByteArray(), Charsets.UTF_8)
                        }
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
