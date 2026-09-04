package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import org.json.JSONArray
import org.json.JSONObject

data class LibraryPlaybackEntry(
    val media: MediaItem,
    val videoId: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedEpochMs: Long = 0L,
) {
    val mediaKey: String
        get() =
            "${media.type}:${media.id}:$videoId"

    val progressFraction: Float
        get() =
            if (
                durationMs > 0L
            ) {
                (
                    positionMs.toDouble() /
                        durationMs.toDouble()
                )
                    .coerceIn(
                        0.0,
                        1.0,
                    )
                    .toFloat()
            } else {
                0f
            }

    val isCompleted: Boolean
        get() =
            durationMs > 0L &&
                (
                    positionMs >=
                        durationMs - 20_000L ||
                    progressFraction >= 0.95f
                )
}

class LibraryStore(
    context: Context,
    prefsName: String = PREFS_NAME,
    private val watchlistStorageKey: String = KEY_WATCHLIST,
    private val historyStorageKey: String = KEY_HISTORY,
    private val dismissedContinueWatchingStorageKey: String = KEY_DISMISSED_CONTINUE_WATCHING,
    private val markedWatchedStorageKey: String = KEY_MARKED_WATCHED,
    profileStore: ProfileStore? = null,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )

    private val profileStore =
        profileStore ?: ProfileStore(appContext)

    private fun scopedKey(
        key: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileStore.activeProfileId(),
            key,
        )

    @Synchronized
    fun watchlist(): List<MediaItem> =
        readWatchlist()
            .sortedByDescending {
                watchlistTimestamp(
                    it
                )
            }
            .map {
                mediaFromJson(
                    it.getJSONObject(
                        "media"
                    )
                )
            }

    @Synchronized
    fun isWatchlisted(
        media: MediaItem,
    ): Boolean =
        readWatchlist().any {
            val stored =
                it.optJSONObject(
                    "media"
                )
                    ?: return@any false

            stored.optString(
                "id"
            ) == media.id &&
                stored.optString(
                    "type"
                ) == media.type
        }

    @Synchronized
    fun toggleWatchlist(
        media: MediaItem,
    ): Boolean {
        val entries =
            readWatchlist()
                .toMutableList()

        val index =
            entries.indexOfFirst {
                val stored =
                    it.optJSONObject(
                        "media"
                    )

                stored?.optString(
                    "id"
                ) == media.id &&
                    stored.optString(
                        "type"
                    ) == media.type
            }

        val added =
            if (index >= 0) {
                entries.removeAt(
                    index
                )
                false
            } else {
                entries +=
                    JSONObject()
                        .put(
                            "media",
                            mediaToJson(
                                media
                            ),
                        )
                        .put(
                            "addedAt",
                            System
                                .currentTimeMillis(),
                        )
                true
            }

        writeArray(
            scopedKey(
                watchlistStorageKey
            ),
            entries,
        )

        return added
    }

    @Synchronized
    fun removeWatchlist(
        media: MediaItem,
    ) {
        val entries =
            readWatchlist()
                .filterNot {
                    val stored =
                        it.optJSONObject(
                            "media"
                        )

                    stored?.optString(
                        "id"
                    ) == media.id &&
                        stored.optString(
                            "type"
                        ) == media.type
                }

        writeArray(
            scopedKey(
                watchlistStorageKey
            ),
            entries,
        )
    }

    @Synchronized
    fun history(): List<LibraryPlaybackEntry> =
        readHistory()
            .sortedByDescending {
                it.lastWatchedEpochMs
            }

    @Synchronized
    fun continueWatching():
        List<LibraryPlaybackEntry> {
        val hiddenTitleKeys =
            dismissedContinueWatchingKeys() +
                markedWatchedKeys()

        return history()
            .filter {
                it.positionMs > 5_000L &&
                    !it.isCompleted
            }
            .filterNot {
                continueWatchingTitleKey(
                    it.media
                ) in hiddenTitleKeys
            }
            .distinctBy {
                continueWatchingTitleKey(
                    it.media
                )
            }
    }

    @Synchronized
    fun recordPlayback(
        media: MediaItem,
        videoId: String,
        episodeTitle: String?,
        season: Int?,
        episode: Int?,
        positionMs: Long,
        durationMs: Long,
    ) {
        restoreContinueWatching(
            media
        )

        val key =
            "${media.type}:${media.id}:$videoId"

        val entries =
            readHistory()
                .toMutableList()

        entries.removeAll {
            it.mediaKey == key
        }

        entries.add(
            0,
            LibraryPlaybackEntry(
                media = media,
                videoId = videoId,
                episodeTitle =
                    episodeTitle,
                season = season,
                episode = episode,
                positionMs =
                    positionMs
                        .coerceAtLeast(
                            0L
                        ),
                durationMs =
                    durationMs
                        .coerceAtLeast(
                            0L
                        ),
                lastWatchedEpochMs =
                    System
                        .currentTimeMillis(),
            ),
        )

        writeHistory(
            entries.take(
                MAX_HISTORY
            )
        )
    }

    @Synchronized
    fun removeHistory(
        mediaKey: String,
    ) {
        writeHistory(
            readHistory()
                .filterNot {
                    it.mediaKey ==
                        mediaKey
                }
        )
    }

    @Synchronized
    fun removeFromContinueWatching(
        entry: LibraryPlaybackEntry,
    ) {
        val dismissed =
            dismissedContinueWatchingKeys()
                .toMutableSet()

        dismissed +=
            continueWatchingTitleKey(
                entry.media
            )

        writeDismissedContinueWatchingKeys(
            dismissed
        )
    }

    @Synchronized
    fun isMarkedWatched(
        media: MediaItem,
    ): Boolean =
        continueWatchingTitleKey(
            media
        ) in markedWatchedKeys()

    @Synchronized
    fun setMarkedWatched(
        media: MediaItem,
        watched: Boolean,
    ) {
        val key =
            continueWatchingTitleKey(
                media
            )
        val marked =
            markedWatchedKeys()
                .toMutableSet()

        if (watched) {
            marked += key
        } else {
            marked -= key
        }

        writeMarkedWatchedKeys(
            marked
        )
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit()
            .remove(
                scopedKey(
                    historyStorageKey
                )
            )
            .remove(
                scopedKey(
                    dismissedContinueWatchingStorageKey
                )
            )
            .remove(
                scopedKey(
                    markedWatchedStorageKey
                )
            )
            .apply()
    }

    @Synchronized
    fun clearContinueWatching() {
        writeDismissedContinueWatchingKeys(
            dismissedContinueWatchingKeys() +
                history()
                    .asSequence()
                    .filter {
                        it.positionMs > 5_000L &&
                            !it.isCompleted
                    }
                    .map {
                        continueWatchingTitleKey(
                            it.media
                        )
                    }
                    .toSet()
        )
    }

    private fun continueWatchingTitleKey(
        media: MediaItem,
    ): String =
        "${media.type}:${media.id}"

    private fun dismissedContinueWatchingKeys():
        Set<String> =
        prefs.getStringSet(
            scopedKey(
                dismissedContinueWatchingStorageKey
            ),
            emptySet(),
        )
            ?.toSet()
            .orEmpty()

    private fun writeDismissedContinueWatchingKeys(
        keys: Set<String>,
    ) {
        prefs.edit()
            .putStringSet(
                scopedKey(
                    dismissedContinueWatchingStorageKey
                ),
                keys.toSet(),
            )
            .apply()
    }

    private fun markedWatchedKeys():
        Set<String> =
        prefs.getStringSet(
            scopedKey(
                markedWatchedStorageKey
            ),
            emptySet(),
        )
            ?.toSet()
            .orEmpty()

    private fun writeMarkedWatchedKeys(
        keys: Set<String>,
    ) {
        prefs.edit()
            .putStringSet(
                scopedKey(
                    markedWatchedStorageKey
                ),
                keys.toSet(),
            )
            .apply()
    }

    private fun restoreContinueWatching(
        media: MediaItem,
    ) {
        val key =
            continueWatchingTitleKey(
                media
            )
        val dismissed =
            dismissedContinueWatchingKeys()

        if (key in dismissed) {
            writeDismissedContinueWatchingKeys(
                dismissed - key
            )
        }

        val marked =
            markedWatchedKeys()

        if (key in marked) {
            writeMarkedWatchedKeys(
                marked - key
            )
        }
    }

    private fun readWatchlist():
        List<JSONObject> =
        readObjectArray(
            scopedKey(
                watchlistStorageKey
            )
        )

    private fun watchlistTimestamp(
        entry: JSONObject,
    ): Long =
        entry.optLong(
            "addedAt",
            0L,
        )

    private fun readHistory():
        List<LibraryPlaybackEntry> =
        readObjectArray(
            scopedKey(
                historyStorageKey
            )
        ).mapNotNull {
            runCatching {
                playbackFromJson(
                    it
                )
            }.getOrNull()
        }

    private fun writeHistory(
        entries:
            List<LibraryPlaybackEntry>,
    ) {
        writeArray(
            scopedKey(
                historyStorageKey
            ),
            entries.map {
                playbackToJson(
                    it
                )
            },
        )
    }

    private fun readObjectArray(
        key: String,
    ): List<JSONObject> {
        val raw =
            prefs.getString(
                key,
                null,
            )
                ?: return emptyList()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in
                    0 until
                        array.length()
                ) {
                    array.optJSONObject(
                        index
                    )?.let(::add)
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    private fun writeArray(
        key: String,
        entries: List<JSONObject>,
    ) {
        val array =
            JSONArray()

        entries.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString(
                key,
                array.toString(),
            )
            .apply()
    }

    private fun playbackToJson(
        entry: LibraryPlaybackEntry,
    ): JSONObject =
        JSONObject()
            .put(
                "media",
                mediaToJson(
                    entry.media
                ),
            )
            .put(
                "videoId",
                entry.videoId,
            )
            .put(
                "episodeTitle",
                entry.episodeTitle,
            )
            .put(
                "season",
                entry.season,
            )
            .put(
                "episode",
                entry.episode,
            )
            .put(
                "positionMs",
                entry.positionMs,
            )
            .put(
                "durationMs",
                entry.durationMs,
            )
            .put(
                "lastWatchedEpochMs",
                entry.lastWatchedEpochMs,
            )

    private fun playbackFromJson(
        json: JSONObject,
    ): LibraryPlaybackEntry =
        LibraryPlaybackEntry(
            media =
                mediaFromJson(
                    json.getJSONObject(
                        "media"
                    )
                ),
            videoId =
                json.getString(
                    "videoId"
                ),
            episodeTitle =
                json.optString(
                    "episodeTitle",
                ).takeIf {
                    it.isNotBlank()
                },
            season =
                json.optIntOrNull(
                    "season"
                ),
            episode =
                json.optIntOrNull(
                    "episode"
                ),
            positionMs =
                json.optLong(
                    "positionMs",
                    0L,
                ),
            durationMs =
                json.optLong(
                    "durationMs",
                    0L,
                ),
            lastWatchedEpochMs =
                json.optLong(
                    "lastWatchedEpochMs",
                    0L,
                ),
        )

    private fun mediaToJson(
        media: MediaItem,
    ): JSONObject =
        JSONObject()
            .put("id", media.id)
            .put("type", media.type)
            .put("name", media.name)
            .put("poster", media.poster)
            .put("background", media.background)
            .put("description", media.description)
            .put("releaseInfo", media.releaseInfo)
            .put("originalLanguage", media.originalLanguage)
            .put("genres", JSONArray(media.genres))
            .put(
                "episodes",
                JSONArray().apply {
                    media.episodes.forEach { put(episodeToJson(it)) }
                },
            )
            .put("sourceExtensionId", media.sourceExtensionId)
            .put("catalogSources", JSONArray(media.catalogSources))
            .put("imdbRating", media.imdbRating)
            .put("tmdbRating", media.tmdbRating)
            .put("runtimeMinutes", media.runtimeMinutes)
            .put("certification", media.certification)
            .put("directors", JSONArray(media.directors))
            .put("creators", JSONArray(media.creators))
            .put("writers", JSONArray(media.writers))
            .put(
                "cast",
                JSONArray().apply {
                    media.cast.forEach { put(personToJson(it)) }
                },
            )
            .put(
                "productionCompanies",
                JSONArray().apply {
                    media.productionCompanies.forEach { put(companyToJson(it)) }
                },
            )
            .put(
                "networks",
                JSONArray().apply {
                    media.networks.forEach { put(companyToJson(it)) }
                },
            )

    private fun mediaFromJson(
        json: JSONObject,
    ): MediaItem =
        MediaItem(
            id = json.getString("id"),
            type = json.getString("type"),
            name = json.getString("name"),
            poster = json.optNullableString("poster"),
            background = json.optNullableString("background"),
            description = json.optNullableString("description"),
            releaseInfo = json.optNullableString("releaseInfo"),
            originalLanguage = json.optNullableString("originalLanguage"),
            genres = json.optJSONArray("genres").toStringList(),
            episodes = json.optJSONArray("episodes").toEpisodes(),
            sourceExtensionId = json.optNullableString("sourceExtensionId"),
            catalogSources = json.optJSONArray("catalogSources").toStringList(),
            imdbRating = json.optDoubleOrNull("imdbRating"),
            tmdbRating = json.optDoubleOrNull("tmdbRating"),
            runtimeMinutes = json.optIntOrNull("runtimeMinutes"),
            certification = json.optNullableString("certification"),
            directors = json.optJSONArray("directors").toStringList(),
            creators = json.optJSONArray("creators").toStringList(),
            writers = json.optJSONArray("writers").toStringList(),
            cast = json.optJSONArray("cast").toPeople(),
            productionCompanies =
                json.optJSONArray("productionCompanies").toCompanies(),
            networks = json.optJSONArray("networks").toCompanies(),
        )

    private fun episodeToJson(
        episode: EpisodeItem,
    ): JSONObject =
        JSONObject()
            .put("id", episode.id)
            .put("title", episode.title)
            .put("season", episode.season)
            .put("episode", episode.episode)
            .put("released", episode.released)
            .put("overview", episode.overview)
            .put("thumbnail", episode.thumbnail)

    private fun personToJson(
        person: MediaPerson,
    ): JSONObject =
        JSONObject()
            .put("name", person.name)
            .put("character", person.character)
            .put("role", person.role)
            .put("profile", person.profile)

    private fun companyToJson(
        company: MediaCompany,
    ): JSONObject =
        JSONObject()
            .put("name", company.name)
            .put("logo", company.logo)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun JSONArray?.toEpisodes(): List<EpisodeItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val title = item.optString("title").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            EpisodeItem(
                id = id,
                title = title,
                season = item.optInt("season", 0),
                episode = item.optInt("episode", 0),
                released = item.optNullableString("released"),
                overview = item.optNullableString("overview"),
                thumbnail = item.optNullableString("thumbnail"),
            )
        }
    }

    private fun JSONArray?.toPeople(): List<MediaPerson> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MediaPerson(
                name = name,
                character = item.optNullableString("character"),
                role = item.optNullableString("role"),
                profile = item.optNullableString("profile"),
            )
        }
    }

    private fun JSONArray?.toCompanies(): List<MediaCompany> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MediaCompany(
                name = name,
                logo = item.optNullableString("logo"),
            )
        }
    }

    private fun JSONObject.optDoubleOrNull(
        key: String,
    ): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun JSONObject
        .optNullableString(
            key: String,
        ): String? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optString(
            key
        ).takeIf {
            it.isNotBlank() &&
                it != "null"
        }
    }

    private fun JSONObject
        .optIntOrNull(
            key: String,
        ): Int? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optInt(key)
    }

    companion object {
        private const val PREFS_NAME =
            "vueo_library"

        private const val KEY_WATCHLIST =
            "watchlist"

        private const val KEY_HISTORY =
            "history"

        private const val KEY_DISMISSED_CONTINUE_WATCHING =
            "dismissed_continue_watching"

        private const val KEY_MARKED_WATCHED =
            "marked_watched"

        private const val MAX_HISTORY =
            150
    }
}
