package com.vueo.shared.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class RecentSearchMode { TITLE, ACTOR }

data class RecentSearch(val query: String, val mode: RecentSearchMode) {
    val key: String get() = mode.name + ":" + query.lowercase(Locale.ROOT)
    val label: String get() = if (mode == RecentSearchMode.ACTOR) "$query · Actor" else query
}

object RecentSearchPolicy {
    const val LIMIT = 12

    fun entry(query: String, mode: RecentSearchMode): RecentSearch? {
        val normalized = query.trim().replace(Regex("\\s+"), " ").take(256)
        return normalized.takeIf { it.length >= 2 }?.let { RecentSearch(it, mode) }
    }

    fun record(entries: List<RecentSearch>, entry: RecentSearch): List<RecentSearch> =
        (listOf(entry) + entries.filterNot { it.key == entry.key }).take(LIMIT)
}

/** Local, profile-pinned search history. Not part of the remote sync allowlist. */
class RecentSearchStore(context: Context, profileId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("vueo_library", Context.MODE_PRIVATE)
    private val storageKey = ProfileStore.scopedPreferenceKey(profileId, "recent_searches_v1")

    fun entries(): List<RecentSearch> = synchronized(LOCK) {
        runCatching {
            val array = JSONArray(prefs.getString(storageKey, "[]"))
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                val mode = RecentSearchMode.entries.firstOrNull { it.name == row.optString("mode") }
                    ?: return@mapNotNull null
                RecentSearchPolicy.entry(row.optString("query"), mode)
            }.distinctBy { it.key }.take(RecentSearchPolicy.LIMIT)
        }.getOrDefault(emptyList())
    }

    fun record(entry: RecentSearch): List<RecentSearch> = synchronized(LOCK) {
        val valid = RecentSearchPolicy.entry(entry.query, entry.mode) ?: return@synchronized entries()
        val previous = entries()
        val next = RecentSearchPolicy.record(previous, valid)
        if (next != previous) write(next)
        next
    }

    fun remove(entry: RecentSearch): List<RecentSearch> = synchronized(LOCK) {
        entries().filterNot { it.key == entry.key }.also(::write)
    }

    fun clear(): List<RecentSearch> = synchronized(LOCK) {
        prefs.edit().remove(storageKey).apply()
        emptyList()
    }

    private fun write(entries: List<RecentSearch>) {
        val array = JSONArray(entries.map { JSONObject().put("query", it.query).put("mode", it.mode.name) })
        prefs.edit().putString(storageKey, array.toString()).apply()
    }

    private companion object { val LOCK = Any() }
}
