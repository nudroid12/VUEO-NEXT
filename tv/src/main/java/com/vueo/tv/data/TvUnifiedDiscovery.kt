package com.vueo.tv.data

import android.content.Context
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.content.TvStremioAddonInfo
import com.vueo.tv.content.TvStremioCatalogInfo
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV discovery bridge backed by the Stremio addons installed in Content Manager.
 *
 * This deliberately does not hard-code Cinemeta. Cinemeta participates only when its
 * Content Manager addon is installed and enabled, just like any other catalog addon.
 */
class TvUnifiedDiscovery(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentStore = TvContentManagerStore(appContext)

    suspend fun catalogRows(
        maxRows: Int = MAX_HOME_ROWS,
        maxItemsPerRow: Int = MAX_ITEMS_PER_ROW,
    ): List<TvCatalogRow> = coroutineScope {
        val catalogs = orderedEnabledCatalogs()
            .filter { it.canLoadWithoutExtras }
            .take(maxRows * 2)

        catalogs
            .map { catalog ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                        runCatching {
                            fetchCatalog(catalog, emptyMap())
                                .take(maxItemsPerRow)
                                .takeIf { it.isNotEmpty() }
                                ?.let { items ->
                                    TvCatalogRow(
                                        id = catalog.key,
                                        title = catalog.name.ifBlank { catalog.id },
                                        providerName = catalog.providerName,
                                        items = items,
                                    )
                                }
                        }.getOrNull()
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .take(maxRows)
    }

    suspend fun browse(kind: TvBrowseKind): List<TvMediaItem> = coroutineScope {
        val catalogs = orderedEnabledCatalogs()
        val requests = browseRequests(kind, catalogs).take(MAX_BROWSE_CATALOGS)

        requests
            .map { request ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                        runCatching {
                            fetchCatalog(request.catalog, request.extras)
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .filter { item -> kind.accepts(item) }
            .distinctBy { canonicalItemKey(it) }
            .take(MAX_BROWSE_ITEMS)
    }

    suspend fun search(
        query: String,
        type: TvDiscoverySearchType,
    ): List<TvDiscoverySearchResult> = coroutineScope {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@coroutineScope emptyList()

        val catalogs = orderedEnabledCatalogs()
            .filter { catalog ->
                catalog.supportsSearch &&
                    catalog.requiredExtras.all { it.equals("search", ignoreCase = true) } &&
                    type.acceptsCatalogType(catalog.type)
            }
            .take(MAX_SEARCH_CATALOGS)

        catalogs
            .map { catalog ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                        runCatching {
                            fetchCatalog(
                                catalog = catalog,
                                extras = mapOf("search" to cleanQuery),
                            ).map { media ->
                                TvDiscoverySearchResult(
                                    media = media,
                                    providerName = catalog.providerName,
                                )
                            }
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { canonicalItemKey(it.media) }
            .take(MAX_SEARCH_RESULTS)
    }

    suspend fun allCatalogs(): List<TvStremioCatalogInfo> =
        catalogSnapshot().flatMap { addon -> addon.catalogs }

    private suspend fun orderedEnabledCatalogs(): List<TvStremioCatalogInfo> {
        val addons = catalogSnapshot()
        val available = addons.flatMap { it.catalogs }
        val homeCatalogKeys = available.filter { it.canLoadWithoutExtras }.map { it.key }
        val order = contentStore.reconcileCatalogOrder(homeCatalogKeys)
        val orderIndex = order.withIndex().associate { it.value to it.index }
        val enabledAddonIds = addons.filter { it.enabled && it.reachable }.map { it.id }.toSet()

        return available
            .asSequence()
            .filter { it.addonId in enabledAddonIds }
            .sortedWith(
                compareBy<TvStremioCatalogInfo> { orderIndex[it.key] ?: Int.MAX_VALUE }
                    .thenBy { it.providerName.lowercase(Locale.ROOT) }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .toList()
    }

    private suspend fun catalogSnapshot(): List<TvStremioAddonInfo> {
        val revision = contentStore.discoveryRevision()
        val now = System.currentTimeMillis()

        synchronized(CACHE_LOCK) {
            val cached = cachedAddons
            if (
                cached.isNotEmpty() &&
                cachedRevision == revision &&
                now - cachedAt in 0 until MANIFEST_CACHE_MS
            ) {
                return cached
            }
        }

        val fresh = contentStore.addons()
        synchronized(CACHE_LOCK) {
            cachedAddons = fresh
            cachedRevision = revision
            cachedAt = now
        }
        return fresh
    }

    private fun browseRequests(
        kind: TvBrowseKind,
        catalogs: List<TvStremioCatalogInfo>,
    ): List<CatalogLoadRequest> =
        when (kind) {
            TvBrowseKind.MOVIE ->
                catalogs
                    .filter { it.type.equals("movie", ignoreCase = true) && it.canLoadWithoutExtras }
                    .map { CatalogLoadRequest(it, emptyMap()) }

            TvBrowseKind.SERIES ->
                catalogs
                    .filter { it.type.isSeriesType() && it.canLoadWithoutExtras }
                    .map { CatalogLoadRequest(it, emptyMap()) }

            TvBrowseKind.ANIME -> {
                val explicitAnime = catalogs
                    .filter { catalog ->
                        (catalog.type.equals("movie", true) || catalog.type.isSeriesType()) &&
                            catalog.canLoadWithoutExtras &&
                            (catalog.id.contains("anime", true) || catalog.name.contains("anime", true))
                    }
                    .map { CatalogLoadRequest(it, emptyMap()) }

                val genreAnime = catalogs
                    .filter { catalog ->
                        (catalog.type.equals("movie", true) || catalog.type.isSeriesType()) &&
                            catalog.supportsGenre("Anime") &&
                            catalog.requiredExtras.all { it.equals("genre", true) }
                    }
                    .map { CatalogLoadRequest(it, mapOf("genre" to "Anime")) }

                (explicitAnime + genreAnime).distinctBy { it.catalog.key + it.extras.toString() }
            }
        }

    private suspend fun fetchCatalog(
        catalog: TvStremioCatalogInfo,
        extras: Map<String, String>,
    ): List<TvMediaItem> = withContext(Dispatchers.IO) {
        val suffix = encodeExtras(extras)
        val url =
            "${catalog.baseUrl}/catalog/${encodeSegment(catalog.type)}/${encodeSegment(catalog.id)}$suffix.json"
        val json = JSONObject(httpGet(url))
        val metas = json.optJSONArray("metas") ?: JSONArray()

        (0 until metas.length())
            .mapNotNull { index ->
                metas.optJSONObject(index)?.toMediaItem(
                    fallbackType = catalog.type,
                    sourceExtensionId = catalog.addonId,
                    providerName = catalog.providerName,
                )
            }
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://", ignoreCase = true)) {
            "VUEO TV discovery requires HTTPS addon endpoints."
        }

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VUEO-TV-Discovery/1.0")
            }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from ${URL(url).host}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private data class CatalogLoadRequest(
        val catalog: TvStremioCatalogInfo,
        val extras: Map<String, String>,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4_500
        private const val READ_TIMEOUT_MS = 6_500
        private const val CATALOG_TIMEOUT_MS = 8_000L
        private const val MANIFEST_CACHE_MS = 20L * 60L * 1000L
        private const val MAX_HOME_ROWS = 12
        private const val MAX_ITEMS_PER_ROW = 24
        private const val MAX_BROWSE_CATALOGS = 10
        private const val MAX_BROWSE_ITEMS = 80
        private const val MAX_SEARCH_CATALOGS = 12
        private const val MAX_SEARCH_RESULTS = 100

        private val CACHE_LOCK = Any()
        @Volatile private var cachedAddons: List<TvStremioAddonInfo> = emptyList()
        @Volatile private var cachedRevision: Int = Int.MIN_VALUE
        @Volatile private var cachedAt: Long = 0L

        fun invalidateMemory() {
            synchronized(CACHE_LOCK) {
                cachedAddons = emptyList()
                cachedRevision = Int.MIN_VALUE
                cachedAt = 0L
            }
        }
    }
}

enum class TvDiscoverySearchType {
    ALL,
    MOVIE,
    SERIES,
}

data class TvDiscoverySearchResult(
    val media: TvMediaItem,
    val providerName: String,
)

private fun TvDiscoverySearchType.acceptsCatalogType(type: String): Boolean =
    when (this) {
        TvDiscoverySearchType.ALL -> type.equals("movie", true) || type.isSeriesType()
        TvDiscoverySearchType.MOVIE -> type.equals("movie", true)
        TvDiscoverySearchType.SERIES -> type.isSeriesType()
    }

private fun TvBrowseKind.accepts(item: TvMediaItem): Boolean =
    when (this) {
        TvBrowseKind.MOVIE -> item.type.equals("movie", true)
        TvBrowseKind.SERIES -> item.type.isSeriesType()
        TvBrowseKind.ANIME ->
            item.type.equals("movie", true) || item.type.isSeriesType()
    }

private fun String.isSeriesType(): Boolean =
    equals("series", ignoreCase = true) || equals("tv", ignoreCase = true)

private fun TvStremioCatalogInfo.supportsGenre(value: String): Boolean =
    extras.any { extra ->
        extra.name.equals("genre", ignoreCase = true) &&
            (extra.options.isEmpty() || extra.options.any { it.equals(value, ignoreCase = true) })
    }

private fun canonicalItemKey(item: TvMediaItem): String {
    val year = item.releaseInfo?.take(4)?.toIntOrNull()?.toString().orEmpty()
    val title = item.name
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\s+"), " ")
    return "${item.type.lowercase(Locale.ROOT)}:$title:$year"
}

private fun encodeSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun encodeExtras(extras: Map<String, String>): String {
    if (extras.isEmpty()) return ""
    val encoded = extras.entries.joinToString("&") { (key, value) ->
        "${encodeSegment(key)}=${encodeSegment(value)}"
    }
    return "/$encoded"
}

private fun JSONObject.toMediaItem(
    fallbackType: String,
    sourceExtensionId: String,
    providerName: String,
): TvMediaItem? {
    val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").trim().takeIf { it.isNotBlank() } ?: return null
    val type = optString("type", fallbackType).trim().ifBlank { fallbackType }

    return TvMediaItem(
        id = id,
        type = type,
        name = name,
        poster = httpsOrNull(optString("poster")),
        background = httpsOrNull(optString("background")),
        description = optString("description").trim().takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").trim().takeIf { it.isNotBlank() },
        originalLanguage = listOf("originalLanguage", "original_language", "language")
            .firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotBlank() } },
        genres = optJSONArray("genres").toStringList(),
        sourceExtensionId = sourceExtensionId,
        catalogSources = listOf(providerName),
        imdbRating = flexibleDouble("imdbRating", "imdb_rating"),
        runtimeMinutes = runtimeMinutes(),
        certification = listOf("certification", "ageRating", "rated")
            .firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotBlank() } },
    )
}

private fun httpsOrNull(value: String?): String? =
    value?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
}

private fun JSONObject.flexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val parsed = when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        if (parsed != null && parsed.isFinite() && parsed > 0) return parsed
    }
    return null
}

private fun JSONObject.runtimeMinutes(): Int? {
    val raw = opt("runtime") ?: return null
    if (raw is Number) return raw.toInt().takeIf { it > 0 }
    val text = raw.toString().lowercase(Locale.ROOT).trim()
    if (text.isBlank()) return null
    val hours = Regex("""(\\d+)\\s*h""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val mins = Regex("""(\\d+)\\s*(?:m|min)""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val total = hours * 60 + mins
    if (total > 0) return total
    return Regex("""\\d+""").find(text)?.value?.toIntOrNull()?.takeIf { it > 0 }
}
