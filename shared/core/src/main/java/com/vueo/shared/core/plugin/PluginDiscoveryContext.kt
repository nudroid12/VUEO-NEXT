package com.vueo.shared.core.plugin

import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

// VUEO_METADATA_SEED_CONTEXT_V17
/**
 * Scan-scoped discovery broker.
 *
 * V17 rule: addon/VUEO metadata is the only metadata source shared with plugins.
 * Plugins must never fall back to TMDB when the addon did not provide usable
 * metadata. TMDB-shaped responses are synthesized from addon metadata only so
 * existing providers can keep their compatibility path without a network
 * metadata fallback.
 */
internal class PluginDiscoveryContextBroker(
    private val scanId: Long,
    private val tmdbId: String,
    private val mediaType: String,
    private val season: Int?,
    private val episode: Int?,
    private val seedTitle: String? = null,
    private val seedOriginalTitle: String? = null,
    private val seedAliases: List<String> = emptyList(),
    private val seedYear: String? = null,
    private val seedExternalId: String? = null,
    private val seedOriginalLanguage: String? = null,
) {
    private val lock = Mutex()

    @Volatile
    private var cachedContextJson: String? = null

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

            RuntimeDiagnostics.recordDiscoveryTrace(
                scanId = scanId,
                stage = "CONTEXT_UNAVAILABLE",
                details = "source=metadata-layer reason=addon_metadata_missing tmdbFallback=false",
            )
            return errorJson("Addon metadata is unavailable")
        } finally {
            lock.unlock()
        }
    }

    fun cachedContextOrNull(): String? = cachedContextJson

    /**
     * Intercepts TMDB requests before they can reach PluginHttp.
     *
     * Expected details requests receive a TMDB-shaped body synthesized solely
     * from addon metadata. Any other TMDB request is blocked so providers cannot
     * silently restore their own metadata fallback.
     */
    suspend fun interceptTmdbFetch(requestJson: String): String? {
        val request = runCatching { JSONObject(requestJson) }.getOrNull() ?: return null
        val url = request.optString("url").trim()
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps || parsed.host != TMDB_HOST) return null

        if (!isExpectedDetailsRequest(parsed)) {
            return blockedTmdbResponse(url, "Plugin TMDB metadata fallback is disabled")
        }

        val contextRaw = resolveFromProviderRequest(requestJson)
        val context = runCatching { JSONObject(contextRaw) }.getOrNull()
            ?: return blockedTmdbResponse(url, "Addon metadata is unavailable")
        if (context.has("error")) {
            return blockedTmdbResponse(url, context.optString("error").ifBlank { "Addon metadata is unavailable" })
        }

        val tmdb = context.optJSONObject("tmdb")
            ?: return blockedTmdbResponse(url, "Addon metadata is unavailable")

        return JSONObject()
            .put("status", 200)
            .put("statusText", "OK")
            .put("url", parsed.newBuilder().query(null).build().toString())
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
        val originalTitle = seedOriginalTitle.orEmpty().trim().ifBlank { title }
        val originalLanguage = seedOriginalLanguage.orEmpty().trim()
        val aliases = linkedSetOf<String>()
        fun addAlias(raw: String?) {
            raw.orEmpty().trim().takeIf(String::isNotBlank)?.let(aliases::add)
        }
        addAlias(title)
        addAlias(originalTitle)
        seedAliases.forEach(::addAlias)

        val alternateTitles = JSONArray()
        aliases
            .filterNot { it.equals(title, ignoreCase = true) || it.equals(originalTitle, ignoreCase = true) }
            .take(MAX_ALIASES)
            .forEach { alias ->
                alternateTitles.put(JSONObject().put("title", alias))
            }

        val tmdb = JSONObject()
            .put("id", tmdbId.toLongOrNull() ?: tmdbId)
            .put(if (isTv) "name" else "title", title)
            .put(if (isTv) "original_name" else "original_title", originalTitle)
            .put("original_language", originalLanguage)
            .put(
                if (isTv) "first_air_date" else "release_date",
                normalizedYear.takeIf { it.isNotBlank() }?.let { "$it-01-01" }.orEmpty(),
            )
            .put("external_ids", JSONObject().put("imdb_id", imdbId))
            .put("alternative_titles", JSONObject().put(if (isTv) "results" else "titles", alternateTitles))
            .put("translations", JSONObject().put("translations", JSONArray()))

        return JSONObject()
            .put("version", 3)
            .put("tmdbId", tmdbId)
            .put("mediaType", if (isTv) "tv" else "movie")
            .put("season", season ?: JSONObject.NULL)
            .put("episode", episode ?: JSONObject.NULL)
            .put("title", title)
            .put("originalTitle", originalTitle)
            .put("year", normalizedYear)
            .put("imdbId", imdbId)
            .put("externalId", externalId)
            .put("originalLanguage", originalLanguage)
            .put("aliases", JSONArray(aliases.take(MAX_ALIASES)))
            .put("source", "metadata-layer")
            .put("tmdb", tmdb)
    }

    private fun isExpectedDetailsRequest(parsed: HttpUrl): Boolean {
        val segments = parsed.pathSegments
        if (segments.size < 3 || segments[0] != "3") return false

        val endpoint = segments[1]
        if (endpoint != "movie" && endpoint != "tv") return false
        if (segments[2] != tmdbId) return false

        val expectedEndpoint = if (mediaType.lowercase() == "movie") "movie" else "tv"
        return endpoint == expectedEndpoint
    }

    private fun blockedTmdbResponse(url: String, reason: String): String =
        JSONObject()
            .put("error", reason)
            .put("status", 424)
            .put("statusText", "Failed Dependency")
            .put("url", url)
            .put("body", "")
            .put("bodyTruncated", false)
            .put("headers", JSONObject())
            .toString()

    private fun errorJson(message: String): String = JSONObject().put("error", message).toString()

    companion object {
        private const val TMDB_HOST = "api.themoviedb.org"
        private const val MAX_ALIASES = 24
        private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
        private val IMDB_REGEX = Regex("""tt\d{5,12}""", RegexOption.IGNORE_CASE)
    }
}
