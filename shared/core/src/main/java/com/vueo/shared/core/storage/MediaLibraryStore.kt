package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import org.json.JSONArray
import org.json.JSONObject

class MediaLibraryStore(
    context: Context,
    prefsName: String,
    private val storageKey: String = "my_list_v1",
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun items(): List<MediaItem> =
        readEntries().sortedByDescending { it.addedAt }.map { it.media }

    @Synchronized
    fun contains(media: MediaItem): Boolean =
        readEntries().any { it.media.id == media.id && it.media.type == media.type }

    @Synchronized
    fun toggle(media: MediaItem): Boolean {
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.media.id == media.id && it.media.type == media.type }
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
        val raw = prefs.getString(storageKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val mediaJson = json.optJSONObject("media") ?: return@mapNotNull null
                LibraryEntry(
                    media = mediaJson.toMediaItem(),
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
                    .put("media", entry.media.toJson())
                    .put("addedAt", entry.addedAt)
            )
        }
        prefs.edit().putString(storageKey, array.toString()).apply()
    }

    private data class LibraryEntry(
        val media: MediaItem,
        val addedAt: Long,
    )
}

private fun MediaItem.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("type", type)
        .put("name", name)
        .put("poster", poster)
        .put("background", background)
        .put("description", description)
        .put("releaseInfo", releaseInfo)
        .put("originalLanguage", originalLanguage)
        .put("genres", JSONArray(genres))
        .put("sourceExtensionId", sourceExtensionId)
        .put("catalogSources", JSONArray(catalogSources))
        .put("imdbRating", imdbRating)
        .put("tmdbRating", tmdbRating)
        .put("runtimeMinutes", runtimeMinutes)
        .put("certification", certification)
        .put("directors", JSONArray(directors))
        .put("creators", JSONArray(creators))
        .put("writers", JSONArray(writers))
        .put("cast", JSONArray().apply { cast.forEach { put(it.toJson()) } })
        .put("productionCompanies", JSONArray().apply { productionCompanies.forEach { put(it.toJson()) } })
        .put("networks", JSONArray().apply { networks.forEach { put(it.toJson()) } })

private fun MediaPerson.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("character", character)
        .put("role", role)
        .put("profile", profile)

private fun MediaCompany.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("logo", logo)

private fun JSONObject.toMediaItem(): MediaItem =
    MediaItem(
        id = optString("id"),
        type = optString("type"),
        name = optString("name"),
        poster = optHttps("poster"),
        background = optHttps("background"),
        description = optNullableString("description"),
        releaseInfo = optNullableString("releaseInfo"),
        originalLanguage = optNullableString("originalLanguage"),
        genres = optJSONArray("genres").toStringList(),
        sourceExtensionId = optNullableString("sourceExtensionId"),
        catalogSources = optJSONArray("catalogSources").toStringList(),
        imdbRating = optNullableDouble("imdbRating"),
        tmdbRating = optNullableDouble("tmdbRating"),
        runtimeMinutes = optNullableInt("runtimeMinutes"),
        certification = optNullableString("certification"),
        directors = optJSONArray("directors").toStringList(),
        creators = optJSONArray("creators").toStringList(),
        writers = optJSONArray("writers").toStringList(),
        cast = optJSONArray("cast").toPeople(),
        productionCompanies = optJSONArray("productionCompanies").toCompanies(),
        networks = optJSONArray("networks").toCompanies(),
    )

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index).trim().takeIf { it.isNotBlank() }
    }
}

private fun JSONArray?.toPeople(): List<MediaPerson> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        MediaPerson(
            name = name,
            character = item.optNullableString("character"),
            role = item.optNullableString("role"),
            profile = item.optHttps("profile"),
        )
    }
}

private fun JSONArray?.toCompanies(): List<MediaCompany> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        MediaCompany(name = name, logo = item.optHttps("logo"))
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optHttps(key: String): String? =
    optNullableString(key)?.takeIf { it.startsWith("https://") }

private fun JSONObject.optNullableDouble(key: String): Double? =
    when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

private fun JSONObject.optNullableInt(key: String): Int? =
    when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
