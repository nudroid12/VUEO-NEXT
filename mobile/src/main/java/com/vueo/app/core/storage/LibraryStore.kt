package com.vueo.app.core.storage

import android.content.Context
import com.vueo.app.core.model.MediaItem
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
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val profileStore =
        ProfileStore(appContext)

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
                KEY_WATCHLIST
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
                KEY_WATCHLIST
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
                    KEY_HISTORY
                )
            )
            .remove(
                scopedKey(
                    KEY_DISMISSED_CONTINUE_WATCHING
                )
            )
            .remove(
                scopedKey(
                    KEY_MARKED_WATCHED
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
                KEY_DISMISSED_CONTINUE_WATCHING
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
                    KEY_DISMISSED_CONTINUE_WATCHING
                ),
                keys.toSet(),
            )
            .apply()
    }

    private fun markedWatchedKeys():
        Set<String> =
        prefs.getStringSet(
            scopedKey(
                KEY_MARKED_WATCHED
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
                    KEY_MARKED_WATCHED
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
                KEY_WATCHLIST
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
                KEY_HISTORY
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
                KEY_HISTORY
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
            .put(
                "id",
                media.id,
            )
            .put(
                "type",
                media.type,
            )
            .put(
                "name",
                media.name,
            )
            .put(
                "poster",
                media.poster,
            )
            .put(
                "background",
                media.background,
            )
            .put(
                "description",
                media.description,
            )
            .put(
                "releaseInfo",
                media.releaseInfo,
            )
            .put(
                "originalLanguage",
                media.originalLanguage,
            )
            .put(
                "genres",
                JSONArray(
                    media.genres
                ),
            )
            .put(
                "sourceExtensionId",
                media.sourceExtensionId,
            )

    private fun mediaFromJson(
        json: JSONObject,
    ): MediaItem {
        val genresJson =
            json.optJSONArray(
                "genres"
            )

        val genres =
            buildList {
                if (
                    genresJson != null
                ) {
                    for (
                        index in
                        0 until
                            genresJson.length()
                    ) {
                        genresJson
                            .optString(
                                index
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let(::add)
                    }
                }
            }

        return MediaItem(
            id =
                json.getString(
                    "id"
                ),
            type =
                json.getString(
                    "type"
                ),
            name =
                json.getString(
                    "name"
                ),
            poster =
                json.optNullableString(
                    "poster"
                ),
            background =
                json.optNullableString(
                    "background"
                ),
            description =
                json.optNullableString(
                    "description"
                ),
            releaseInfo =
                json.optNullableString(
                    "releaseInfo"
                ),
            originalLanguage =
                json.optNullableString(
                    "originalLanguage"
                ),
            genres =
                genres,
            sourceExtensionId =
                json.optNullableString(
                    "sourceExtensionId"
                ),
        )
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
