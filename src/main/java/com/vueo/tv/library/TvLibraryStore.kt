package com.vueo.tv.library

import android.content.Context
import com.vueo.tv.data.TvMediaItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV-local library store.
 *
 * VUEO Mobile and VUEO TV intentionally use separate application IDs, so the
 * TV build keeps its own lightweight library until cloud/shared sync is added.
 */
class TvLibraryStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun items(): List<TvMediaItem> =
        readEntries()
            .sortedByDescending { it.addedAt }
            .map { it.media }

    @Synchronized
    fun contains(media: TvMediaItem): Boolean =
        readEntries().any {
            it.media.id == media.id && it.media.type == media.type
        }

    /** Returns true when the media is in My List after the toggle. */
    @Synchronized
    fun toggle(media: TvMediaItem): Boolean {
        val entries = readEntries().toMutableList()
        val index =
            entries.indexOfFirst {
                it.media.id == media.id && it.media.type == media.type
            }

        val added =
            if (index >= 0) {
                entries.removeAt(index)
                false
            } else {
                entries += LibraryEntry(media, System.currentTimeMillis())
                true
            }

        persist(entries)
        return added
    }

    private fun readEntries(): List<LibraryEntry> {
        val raw = prefs.getString(KEY_LIBRARY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index ->
                    val json = array.optJSONObject(index) ?: return@mapNotNull null
                    val mediaJson = json.optJSONObject("media") ?: return@mapNotNull null
                    LibraryEntry(
                        media = mediaFromJson(mediaJson),
                        addedAt = json.optLong("addedAt", 0L),
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<LibraryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("media", mediaToJson(entry.media))
                    .put("addedAt", entry.addedAt)
            )
        }
        prefs.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    private data class LibraryEntry(
        val media: TvMediaItem,
        val addedAt: Long,
    )

    companion object {
        private const val PREFS_NAME = "vueo_tv_library"
        private const val KEY_LIBRARY = "my_list_v1"
    }
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

private fun mediaFromJson(json: JSONObject): TvMediaItem =
    TvMediaItem(
        id = json.optString("id"),
        type = json.optString("type"),
        name = json.optString("name"),
        poster = json.optString("poster").takeIf { it.startsWith("https://") },
        background = json.optString("background").takeIf { it.startsWith("https://") },
        description = json.optString("description").takeIf { it.isNotBlank() },
        releaseInfo = json.optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = json.optJSONArray("genres").toStringList(),
        imdbRating = json.opt("imdbRating").let {
            when (it) {
                is Number -> it.toDouble()
                is String -> it.toDoubleOrNull()
                else -> null
            }
        },
    )

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
}
