package com.vueo.tv.data

import android.content.Context
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.content.TvContentManagerStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV-02 real home feed.
 *
 * Loads Home rows from enabled Stremio catalogs managed by Content Manager.
 * The TV app keeps a profile and discovery-revision aware cache so returning
 * from Detail or Player does not unnecessarily rebuild Home.
 */
class TvHomeRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val profileStore = ProfileStore(appContext)
    private val contentStore = TvContentManagerStore(appContext)
    private val discovery = TvUnifiedDiscovery(appContext)
    private val dnaPreferences =
        UserDnaPreferences(
            context = appContext,
            prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
        )
    private val dnaEngine =
        UserDnaEngine(
            LibraryStore(
                context = appContext,
                prefsName = TvLibraryStore.PREFS_NAME,
                watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
            ),
        )

    fun cached(): TvHomeData? {
        val activeProfileId = profileStore.activeProfileId()
        val cachedProfileId = prefs.getString(KEY_PROFILE_ID, null)
        if (cachedProfileId != activeProfileId) return null
        if (prefs.getInt(KEY_DISCOVERY_REVISION, Int.MIN_VALUE) != contentStore.discoveryRevision()) return null

        return prefs.getString(KEY_HOME_CACHE, null)
            ?.let { raw ->
                runCatching {
                    homeFromJson(JSONObject(raw))
                }.getOrNull()
            }
    }

    fun shouldRefresh(home: TvHomeData?): Boolean {
        if (home == null) return true
        val age = System.currentTimeMillis() - home.refreshedAtEpochMs
        return age < 0L || age >= CACHE_MAX_AGE_MS
    }

    suspend fun refresh(): TvHomeData {
        val rows = discovery.catalogRows(
            maxRows = MAX_HOME_ROWS,
            maxItemsPerRow = MAX_ITEMS_PER_ROW,
        )

        if (rows.isEmpty()) {
            error("No enabled Content Manager catalog could be loaded.")
        }

        val finalRows = personalize(rows)
        val hero =
            finalRows
                .asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.background.isNullOrBlank() }
                ?: finalRows.first().items.first()

        val providers = finalRows
            .map { it.providerName }
            .filter { it.isNotBlank() && it != "VUEO DNA" }
            .distinct()

        val home =
            TvHomeData(
                hero = hero,
                rows = finalRows,
                providerName = if (providers.size == 1) providers.first() else "Content Manager",
                refreshedAtEpochMs = System.currentTimeMillis(),
            )

        persist(home)
        return home
    }

    private fun personalize(
        rows: List<TvCatalogRow>,
    ): List<TvCatalogRow> {
        val profileId = profileStore.activeProfileId()
        if (!dnaPreferences.shouldPersonalizeRecommendations(profileId)) {
            return rows
        }

        val dna = dnaEngine.build()
        val ranked =
            rows
                .asSequence()
                .flatMap { it.items.asSequence() }
                .distinctBy { "${it.type}:${it.id}" }
                .mapNotNull { media ->
                    dnaEngine.matchPercent(media, dna)
                        ?.let { score -> media to score }
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(MAX_ITEMS_PER_ROW)
                .toList()

        if (ranked.size < MIN_PERSONALIZED_ITEMS) {
            return rows
        }

        return listOf(
            TvCatalogRow(
                id = "for-you",
                title = "For You",
                providerName = "VUEO DNA",
                items = ranked,
            ),
        ) + rows
    }

    private fun persist(home: TvHomeData) {
        prefs.edit()
            .putString(
                KEY_HOME_CACHE,
                homeToJson(home).toString(),
            )
            .putString(KEY_PROFILE_ID, profileStore.activeProfileId())
            .putInt(KEY_DISCOVERY_REVISION, contentStore.discoveryRevision())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vueo_tv_home"
        private const val KEY_HOME_CACHE = "home_cache_v1"
        private const val KEY_PROFILE_ID = "home_cache_profile_v1"
        private const val KEY_DISCOVERY_REVISION = "home_cache_discovery_revision_v1"
        private const val CACHE_MAX_AGE_MS = 20L * 60L * 1000L
        private const val MAX_HOME_ROWS = 12
        private const val MAX_ITEMS_PER_ROW = 24
        private const val MIN_PERSONALIZED_ITEMS = 6
    }
}

data class TvHomeData(
    val hero: TvMediaItem,
    val rows: List<TvCatalogRow>,
    val providerName: String,
    val refreshedAtEpochMs: Long,
)

data class TvCatalogRow(
    val id: String,
    val title: String,
    val providerName: String,
    val items: List<TvMediaItem>,
)

typealias TvMediaItem = com.vueo.shared.core.media.MediaItem

private fun JSONObject.toMediaItem(
    fallbackType: String,
): TvMediaItem? {
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

private fun JSONObject.flexibleDouble(
    vararg keys: String,
): Double? {
    for (key in keys) {
        val value = opt(key)
        val parsed =
            when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }

        if (parsed != null) return parsed
    }

    return null
}

private fun homeToJson(home: TvHomeData): JSONObject =
    JSONObject()
        .put("hero", mediaToJson(home.hero))
        .put(
            "rows",
            JSONArray().apply {
                home.rows.forEach { row ->
                    put(
                        JSONObject()
                            .put("id", row.id)
                            .put("title", row.title)
                            .put("providerName", row.providerName)
                            .put(
                                "items",
                                JSONArray().apply {
                                    row.items.forEach { item ->
                                        put(mediaToJson(item))
                                    }
                                },
                            )
                    )
                }
            },
        )
        .put("providerName", home.providerName)
        .put("refreshedAtEpochMs", home.refreshedAtEpochMs)

private fun homeFromJson(json: JSONObject): TvHomeData {
    val rowsJson = json.optJSONArray("rows") ?: JSONArray()
    val rows =
        (0 until rowsJson.length())
            .mapNotNull { index ->
                val rowJson = rowsJson.optJSONObject(index) ?: return@mapNotNull null
                val itemsJson = rowJson.optJSONArray("items") ?: JSONArray()
                val items =
                    (0 until itemsJson.length())
                        .mapNotNull { itemIndex ->
                            itemsJson.optJSONObject(itemIndex)?.let(::mediaFromJson)
                        }

                TvCatalogRow(
                    id = rowJson.optString("id"),
                    title = rowJson.optString("title"),
                    providerName = rowJson.optString("providerName", "Content Manager"),
                    items = items,
                )
            }

    val hero =
        json.optJSONObject("hero")
            ?.let(::mediaFromJson)
            ?: rows.firstOrNull()?.items?.firstOrNull()
            ?: error("Invalid VUEO TV home cache.")

    return TvHomeData(
        hero = hero,
        rows = rows,
        providerName = json.optString("providerName", "Content Manager"),
        refreshedAtEpochMs = json.optLong("refreshedAtEpochMs", 0L),
    )
}

private fun mediaToJson(item: TvMediaItem): JSONObject =
    JSONObject()
        .put("id", item.id)
        .put("type", item.type)
        .put("name", item.name)
        .put("poster", item.poster)
        .put("background", item.background)
        .put("description", item.description)
        .put("releaseInfo", item.releaseInfo)
        .put("genres", JSONArray(item.genres))
        .put("imdbRating", item.imdbRating)
        .put("sourceExtensionId", item.sourceExtensionId)
        .put("catalogSources", JSONArray(item.catalogSources))

private fun mediaFromJson(json: JSONObject): TvMediaItem =
    TvMediaItem(
        id = json.optString("id"),
        type = json.optString("type", "movie"),
        name = json.optString("name"),
        poster = json.optString("poster").takeIf { it.isNotBlank() && it != "null" },
        background = json.optString("background").takeIf { it.isNotBlank() && it != "null" },
        description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
        releaseInfo = json.optString("releaseInfo").takeIf { it.isNotBlank() && it != "null" },
        genres = json.optJSONArray("genres").toStringList(),
        sourceExtensionId = json.optString("sourceExtensionId").takeIf { it.isNotBlank() && it != "null" },
        catalogSources = json.optJSONArray("catalogSources").toStringList(),
        imdbRating = json.opt("imdbRating").let { value ->
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        },
    )
