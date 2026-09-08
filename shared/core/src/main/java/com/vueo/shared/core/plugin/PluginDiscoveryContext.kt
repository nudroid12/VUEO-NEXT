package com.vueo.shared.core.plugin

import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

// VUEO_METADATA_SEED_CONTEXT_V16
/**
 * Scan-scoped discovery broker.
 *
 * V16 rule: metadata already resolved by VUEO/addons is the source of truth.
 * Core only normalizes/shares it with providers. A provider-owned TMDB fetch is
 * retained solely as a compatibility fallback when no usable media title was
 * supplied by the caller.
 */
internal class PluginDiscoveryContextBroker(
    private val scanId: Long,
    private val tmdbId: String,
    private val mediaType: String,
    private val season: Int?,
    private val episode: Int?,
    private val seedTitle: String? = null,
    private val seedYear: String? = null,
    private val seedExternalId: String? = null,
) {
    private val lock = Mutex()

    @Volatile
    private var cachedContextJson: String? = null

    private var fetchAttempts = 0

    suspend fun resolveFromProviderRequest(requestJson: String): String {
        lock.lock()
        try {
            cachedContextJson?.let { return it }

            buildSeedContextOrNull()?.let { seeded ->
                val serialized = seeded.toString()
                cachedContextJson = serialized
                RuntimeDiagnostics.recordDiscoveryTrace(
                    scanId = scanId,
                    stage = "CONTEXT_READY",
                    details = buildString {
                        append("source=metadata-layer title=")
                        append(seeded.optString("title").take(80))
                        append(" year=")
                        append(seeded.optString("year"))
                        append(" imdb=")
                        append(seeded.optString("imdbId"))
                        append(" aliases=")
                        append(seeded.optJSONArray("aliases")?.length() ?: 0)
                    },
                )
                return serialized
            }

            if (fetchAttempts >= MAX_FETCH_ATTEMPTS) {
                return errorJson("Shared discovery context is unavailable")
            }

            val request = runCatching { JSONObject(requestJson) }
                .getOrElse { return errorJson("Invalid discovery-context request") }

            val url = request.optString("url").trim()
            val validated = validateTmdbUrl(url)
                ?: return errorJson("Unsupported discovery-context URL")

            fetchAttempts += 1
            val startedNs = System.nanoTime()

            val nativeRequest = JSONObject()
                .put("url", url)
                .put("method", "GET")
                .put(
                    "headers",
                    JSONObject()
                        .put("Accept", "application/json")
                        .put("User-Agent", "VUEO/0.9.6"),
                )

            val responseRaw = PluginHttp.executeJson(nativeRequest.toString())
            val response = runCatching { JSONObject(responseRaw) }.getOrNull()
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L

            if (response == null || response.has("error")) {
                RuntimeDiagnostics.recordDiscoveryTrace(
                    scanId = scanId,
                    stage = "CONTEXT_FAILED",
                    details = "attempt=$fetchAttempts elapsed=${elapsedMs}ms reason=native_fetch_error",
                )
                return errorJson(
                    response?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "TMDB context fetch failed",
                )
            }

            val status = response.optInt("status", 0)
            if (status !in 200..299) {
                RuntimeDiagnostics.recordDiscoveryTrace(
                    scanId = scanId,
                    stage = "CONTEXT_FAILED",
                    details = "attempt=$fetchAttempts elapsed=${elapsedMs}ms status=$status",
                )
                return errorJson("TMDB context HTTP $status")
            }

            val body = response.optString("body")
            val tmdb = runCatching { JSONObject(body) }.getOrNull()
                ?: return errorJson("TMDB context body was not JSON")

            val context = buildTmdbContext(tmdb, validated.endpoint)
            val serialized = context.toString()
            cachedContextJson = serialized

            RuntimeDiagnostics.recordDiscoveryTrace(
                scanId = scanId,
                stage = "CONTEXT_READY",
                details = buildString {
                    append("source=provider-tmdb-fallback elapsed=")
                    append(elapsedMs)
                    append("ms title=")
                    append(context.optString("title").take(80))
                    append(" year=")
                    append(context.optString("year"))
                    append(" imdb=")
                    append(context.optString("imdbId"))
                    append(" aliases=")
                    append(context.optJSONArray("aliases")?.length() ?: 0)
                },
            )

            return serialized
        } finally {
            lock.unlock()
        }
    }

    fun cachedContextOrNull(): String? = cachedContextJson

    suspend fun maybeExecuteTmdbFetch(requestJson: String): String? {
        val request = runCatching { JSONObject(requestJson) }.getOrNull() ?: return null
        val url = request.optString("url").trim()
        val validated = validateTmdbUrl(url) ?: return null

        val contextRaw = resolveFromProviderRequest(JSONObject().put("url", url).toString())
        val context = runCatching { JSONObject(contextRaw) }.getOrNull() ?: return null
        if (context.has("error")) return null

        val tmdb = context.optJSONObject("tmdb") ?: return null
        return JSONObject()
            .put("status", 200)
            .put("statusText", "OK")
            .put("url", validated.safeUrl)
            .put("body", tmdb.toString())
            .put("bodyTruncated", false)
            .put("headers", JSONObject().put("Content-Type", "application/json"))
            .toString()
    }

    private fun buildSeedContextOrNull(): JSONObject? {
        val title = seedTitle.orEmpty().trim()
        if (title.isBlank()) return null

        val isTv = mediaType.lowercase() != "movie"
        val normalizedYear = seedYear.orEmpty()
            .let { YEAR_REGEX.find(it)?.value.orEmpty() }
        val externalId = seedExternalId.orEmpty().trim()
        val imdbId = externalId.takeIf { it.matches(IMDB_REGEX) }.orEmpty()

        val tmdb = JSONObject()
            .put("id", tmdbId.toLongOrNull() ?: tmdbId)
            .put(if (isTv) "name" else "title", title)
            .put(if (isTv) "original_name" else "original_title", title)
            .put(
                if (isTv) "first_air_date" else "release_date",
                normalizedYear.takeIf { it.isNotBlank() }?.let { "$it-01-01" }.orEmpty(),
            )
            .put("external_ids", JSONObject().put("imdb_id", imdbId))
            .put("alternative_titles", JSONObject().put(if (isTv) "results" else "titles", JSONArray()))
            .put("translations", JSONObject().put("translations", JSONArray()))

        return JSONObject()
            .put("version", 2)
            .put("tmdbId", tmdbId)
            .put("mediaType", if (isTv) "tv" else "movie")
            .put("season", season ?: JSONObject.NULL)
            .put("episode", episode ?: JSONObject.NULL)
            .put("title", title)
            .put("originalTitle", title)
            .put("year", normalizedYear)
            .put("imdbId", imdbId)
            .put("aliases", JSONArray().put(title))
            .put("source", "metadata-layer")
            .put("tmdb", tmdb)
    }

    private fun buildTmdbContext(tmdb: JSONObject, endpoint: String): JSONObject {
        val isTv = endpoint == "tv"
        val title = tmdb.optString(if (isTv) "name" else "title")
        val originalTitle = tmdb.optString(if (isTv) "original_name" else "original_title")
        val date = tmdb.optString(if (isTv) "first_air_date" else "release_date")
        val year = date.substringBefore('-').takeIf { it.matches(Regex("""\d{4}""")) }.orEmpty()
        val imdbId = tmdb.optJSONObject("external_ids")?.optString("imdb_id").orEmpty()

        return JSONObject()
            .put("version", 2)
            .put("tmdbId", tmdbId)
            .put("mediaType", if (isTv) "tv" else "movie")
            .put("season", season ?: JSONObject.NULL)
            .put("episode", episode ?: JSONObject.NULL)
            .put("title", title)
            .put("originalTitle", originalTitle)
            .put("year", year)
            .put("imdbId", imdbId)
            .put("aliases", collectAliases(tmdb, isTv))
            .put("source", "provider-tmdb-fallback")
            .put("tmdb", tmdb)
    }

    private fun collectAliases(tmdb: JSONObject, isTv: Boolean): JSONArray {
        val output = JSONArray()
        val seen = linkedSetOf<String>()

        fun add(raw: String?) {
            val value = raw.orEmpty().trim()
            val key = value.lowercase().replace(Regex("""\s+"""), " ")
            if (value.isBlank() || key in seen) return
            seen += key
            output.put(value)
        }

        add(tmdb.optString(if (isTv) "name" else "title"))
        add(tmdb.optString(if (isTv) "original_name" else "original_title"))

        val alternate = tmdb.optJSONObject("alternative_titles")
        val alternateItems = alternate?.optJSONArray("results") ?: alternate?.optJSONArray("titles")
        if (alternateItems != null) {
            for (index in 0 until alternateItems.length()) {
                val item = alternateItems.optJSONObject(index) ?: continue
                add(item.optString("title").ifBlank { item.optString("name") })
                if (output.length() >= MAX_ALIASES) break
            }
        }

        val translations = tmdb.optJSONObject("translations")?.optJSONArray("translations")
        if (translations != null && output.length() < MAX_ALIASES) {
            for (index in 0 until translations.length()) {
                val data = translations.optJSONObject(index)?.optJSONObject("data") ?: continue
                add(data.optString("title").ifBlank { data.optString("name") })
                if (output.length() >= MAX_ALIASES) break
            }
        }

        return output
    }

    private fun validateTmdbUrl(url: String): ValidatedTmdbUrl? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps || parsed.host != TMDB_HOST) return null

        val segments = parsed.pathSegments
        if (segments.size < 3 || segments[0] != "3") return null

        val endpoint = segments[1]
        if (endpoint != "movie" && endpoint != "tv") return null
        if (segments[2] != tmdbId) return null

        val expectedEndpoint = if (mediaType.lowercase() == "movie") "movie" else "tv"
        if (endpoint != expectedEndpoint) return null

        return ValidatedTmdbUrl(
            endpoint = endpoint,
            safeUrl = parsed.newBuilder().query(null).build().toString(),
        )
    }

    private fun errorJson(message: String): String = JSONObject().put("error", message).toString()

    private data class ValidatedTmdbUrl(
        val endpoint: String,
        val safeUrl: String,
    )

    companion object {
        private const val TMDB_HOST = "api.themoviedb.org"
        private const val MAX_FETCH_ATTEMPTS = 2
        private const val MAX_ALIASES = 24
        private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
        private val IMDB_REGEX = Regex("""tt\d{5,12}""", RegexOption.IGNORE_CASE)
    }
}
