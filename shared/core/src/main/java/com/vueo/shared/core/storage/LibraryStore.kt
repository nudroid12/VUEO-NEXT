package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaCompany
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import org.json.JSONArray
import org.json.JSONObject

data class LibraryDetailSnapshot(
    val watchlist: List<MediaItem>,
    val history: List<LibraryPlaybackEntry>,
    val continueWatching: List<LibraryPlaybackEntry>,
    val playbackEntries: List<LibraryPlaybackEntry>,
)

data class LibraryHomeSnapshot(
    val history: List<LibraryPlaybackEntry>,
    val continueWatching: List<LibraryPlaybackEntry>,
)

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
    private val continueWatchingStorageKey: String = KEY_CONTINUE_WATCHING,
    private val dismissedContinueWatchingStorageKey: String = KEY_DISMISSED_CONTINUE_WATCHING,
    private val markedWatchedStorageKey: String = KEY_MARKED_WATCHED,
    profileStore: ProfileStore? = null,
    private val profileIdOverride: String? = null,
    private val publishTraktChanges: Boolean = true,
    private val filterForKids: Boolean = true,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )

    private val episodeCache = LibraryEpisodeCache(appContext)

    private val profileStore =
        profileStore ?: ProfileStore(appContext)

    private fun scopedKey(
        key: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileIdOverride ?: profileStore.activeProfileId(),
            key,
        )

    private fun kidsFilterMedia(items: List<MediaItem>): List<MediaItem> {
        if (!filterForKids) return items
        val limit = profileStore.kidsAgeLimit() ?: return items
        return items.filter { com.vueo.shared.core.profile.KidsContentPolicy.allows(it.certification, limit) }
    }
    private fun kidsFilter(items: List<LibraryPlaybackEntry>): List<LibraryPlaybackEntry> {
        if (!filterForKids) return items
        val limit = profileStore.kidsAgeLimit() ?: return items
        return items.filter { com.vueo.shared.core.profile.KidsContentPolicy.allows(it.media.certification, limit) }
    }

    /** Local explicit feedback; storage remains in the profile's existing library prefs. */
    fun ratings(): List<TitleRating> = synchronized(SYNC_STORAGE_LOCK) {
        val array = runCatching { JSONArray(prefs.getString(scopedKey("title_ratings_v1"), "[]")) }.getOrElse { JSONArray() }
        (0 until array.length()).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            TitleRating(mediaFromJson(row.getJSONObject("media")), row.getInt("value"))
        }.getOrNull()?.takeIf { it.value == 1 || it.value == -1 } }
    }

    fun setRating(media: MediaItem, value: Int) = synchronized(SYNC_STORAGE_LOCK) {
        require(value in -1..1)
        val remaining = ratings().filterNot {
            com.vueo.shared.core.home.ForYouPolicy.aliases(it.media).any(
                com.vueo.shared.core.home.ForYouPolicy.aliases(media)::contains)
        }
        val rows = if (value == 0) remaining else remaining + TitleRating(media.copy(episodes = emptyList()), value)
        val array = JSONArray()
        rows.forEach { array.put(JSONObject().put("media", mediaToJson(it.media)).put("value", it.value)) }
        prefs.edit().putString(scopedKey("title_ratings_v1"), array.toString()).apply()
        RatingChanges.revision.value += 1L
    }

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
            }.let(::kidsFilterMedia)

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
        synchronized(SYNC_STORAGE_LOCK) {
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

            if (publishTraktChanges) com.vueo.shared.core.trakt.TraktService.get(appContext)
                .watchlistChanged(media, added, profileIdOverride ?: profileStore.activeProfileId())
            return added
        }
    }

    @Synchronized
    fun removeWatchlist(
        media: MediaItem,
    ) {
        synchronized(SYNC_STORAGE_LOCK) {
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
            if (publishTraktChanges) com.vueo.shared.core.trakt.TraktService.get(appContext)
                .watchlistChanged(media, false, profileIdOverride ?: profileStore.activeProfileId())
        }
    }

    @Synchronized
    fun history(): List<LibraryPlaybackEntry> =
        readHistory()
            .sortedByDescending {
                it.lastWatchedEpochMs
            }.let(::kidsFilter)

    /**
     * Fast first-stage Continue Watching hydration for Home.
     *
     * The dedicated title cursors are intentionally read without reparsing the
     * bounded history. In-progress cursors are safe to publish immediately; a
     * completed series cursor is deferred to [homeSnapshot], where full history
     * is available to resolve the next released unwatched episode correctly.
     */
    @Synchronized
    fun fastContinueWatching(): List<LibraryPlaybackEntry> {
        val hiddenTitleKeys =
            dismissedContinueWatchingKeys() +
                markedWatchedKeys()

        return readContinueWatching()
            .let(::kidsFilter)
            .sortedByDescending { it.lastWatchedEpochMs }
            .distinctBy { continueWatchingTitleKey(it.media) }
            .filterNot { continueWatchingTitleKey(it.media) in hiddenTitleKeys }
            .filter { entry ->
                if (ContinueWatchingPolicy.isSeries(entry.media)) {
                    !entry.isCompleted
                } else {
                    entry.positionMs > 5_000L && !entry.isCompleted
                }
            }
    }

    /**
     * Tiny persisted snapshot used by TV Home during its very first composition.
     *
     * This deliberately avoids reparsing full history/cast/episode metadata on startup.
     * The cache is maintained when playback/library state changes, then reconciled by
     * [homeSnapshot] in the background after Home is already visible.
     */
    @Synchronized
    fun homeStartupContinueWatching(): List<LibraryPlaybackEntry> {
        synchronized(SYNC_STORAGE_LOCK) {
            val key = scopedKey(KEY_HOME_CONTINUE_WATCHING_CACHE)
            if (prefs.contains(key)) return readHomeContinueWatchingCache().let(::kidsFilter)

            // One-time migration for installs upgraded from pre-v133 builds. From the
            // next write onward this path reads only the lightweight cache.
            val seeded = fastContinueWatching()
            writeHomeContinueWatchingCache(seeded)
            return seeded
        }
    }

    /**
     * Home-only snapshot. It skips watchlist parsing while still resolving
     * Continue Watching against full history for exact series next-episode rules.
     */
    @Synchronized
    fun homeSnapshot(): LibraryHomeSnapshot {
        synchronized(SYNC_STORAGE_LOCK) {
            val history =
                readHistory()
                    .sortedByDescending { it.lastWatchedEpochMs }
            val (_, continueWatching) =
                resolveContinueWatching(
                    cursors = readContinueWatching(),
                    history = history,
                )

            writeHomeContinueWatchingCache(continueWatching)

            return LibraryHomeSnapshot(
                history = history.let(::kidsFilter),
                continueWatching = continueWatching.let(::kidsFilter),
            )
        }
    }

    /**
     * One-pass local snapshot for Details hydration.
     *
     * Details screens need watchlist, history, continue-watching and dedicated
     * playback cursors at the same time. Reading each public accessor separately
     * reparses the same profile JSON more than once, so this keeps those reads
     * together and reuses the parsed history for every derived collection.
     */
    @Synchronized
    fun detailSnapshot(): LibraryDetailSnapshot {
        synchronized(SYNC_STORAGE_LOCK) {
            val watchlist =
                readWatchlist()
                    .sortedByDescending(::watchlistTimestamp)
                    .map { entry ->
                        mediaFromJson(
                            entry.getJSONObject("media")
                        )
                    }

            val history =
                readHistory()
                    .sortedByDescending {
                        it.lastWatchedEpochMs
                    }
            val (playbackEntries, continueWatching) =
                resolveContinueWatching(
                    cursors = readContinueWatching(),
                    history = history,
                )

            return LibraryDetailSnapshot(
                watchlist = watchlist.let(::kidsFilterMedia),
                history = history.let(::kidsFilter),
                continueWatching = continueWatching.let(::kidsFilter),
                playbackEntries = playbackEntries.let(::kidsFilter),
            )
        }
    }

    private fun resolveContinueWatching(
        cursors: List<LibraryPlaybackEntry>,
        history: List<LibraryPlaybackEntry>,
    ): Pair<List<LibraryPlaybackEntry>, List<LibraryPlaybackEntry>> {
        val playbackEntries =
            (cursors + history)
                .sortedByDescending { it.lastWatchedEpochMs }
                .distinctBy { it.mediaKey }
        val hiddenTitleKeys =
            dismissedContinueWatchingKeys() +
                markedWatchedKeys()
        val continueWatching =
            ContinueWatchingPolicy
                .resolve(playbackEntries)
                .filterNot { continueWatchingTitleKey(it.media) in hiddenTitleKeys }

        return playbackEntries to continueWatching
    }

    @Synchronized
    fun continueWatching():
        List<LibraryPlaybackEntry> {
        val hiddenTitleKeys =
            dismissedContinueWatchingKeys() +
                markedWatchedKeys()

        return ContinueWatchingPolicy.resolve(continueWatchingPlaybackEntries())
            .filterNot { continueWatchingTitleKey(it.media) in hiddenTitleKeys }
    }

    /** Dedicated title cursors survive the bounded 150-entry history. */
    @Synchronized
    fun continueWatchingPlaybackEntries(): List<LibraryPlaybackEntry> =
        (readContinueWatching() + history()).sortedByDescending { it.lastWatchedEpochMs }
            .distinctBy { it.mediaKey }
            .let(::kidsFilter)

    fun activeContinueWatchingProfileId(): String = profileStore.activeProfileId()

    @Synchronized
    fun continueWatchingMetadataCandidates(nowMs: Long): List<MediaItem> {
        val hidden = dismissedContinueWatchingKeys() + markedWatchedKeys()
        return continueWatchingPlaybackEntries().groupBy { continueWatchingTitleKey(it.media) }.values
            .map { it.first().media }
            .filter { ContinueWatchingPolicy.isSeries(it) && continueWatchingTitleKey(it) !in hidden }
            .filter { nowMs - prefs.getLong(scopedKey("cw_meta_attempt:${continueWatchingTitleKey(it)}"), 0L) >= 15 * 60_000L }
    }

    @Synchronized
    fun claimContinueWatchingMetadata(media: MediaItem, profileId: String, nowMs: Long): Boolean {
        if (profileStore.activeProfileId() != profileId) return false
        val key = scopedKey("cw_meta_attempt:${continueWatchingTitleKey(media)}")
        if (nowMs - prefs.getLong(key, 0L) < 15 * 60_000L) return false
        prefs.edit().putLong(key, nowMs).apply()
        return true
    }

    @Synchronized
    fun updateContinueWatchingMetadata(media: MediaItem, profileId: String): Boolean {
        synchronized(SYNC_STORAGE_LOCK) {
            if (profileStore.activeProfileId() != profileId || media.episodes.isEmpty()) return false
            val key = continueWatchingTitleKey(media)
            if (key in dismissedContinueWatchingKeys() || key in markedWatchedKeys()) return false
            val before=episodeCache.read(media.type,media.id)
            episodeCache.save(media.type,media.id,media.episodes)
            val changed=before!=episodeCache.read(media.type,media.id)
            if(changed) refreshHomeContinueWatchingCache(exact=true)
            return changed
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
        lastWatchedEpochMs: Long = PlaybackUpdateClock.next(),
    ) {
        synchronized(SYNC_STORAGE_LOCK) {
            // LibraryStore is instantiated by more than one screen. A class-wide
            // lock is required because @Synchronized only protects one instance.
            synchronized(PLAYBACK_PERSISTENCE_LOCK) {
                val key =
                    "${media.type}:${media.id}:$videoId"
                val updateEpochMs =
                    lastWatchedEpochMs
                        .takeIf { it > 0L }
                        ?: PlaybackUpdateClock.next()
                PlaybackUpdateClock.observe(updateEpochMs)
                // A save captured before Delete must not recreate an old watch.
                if (updateEpochMs <= resetAt(media)) return

                val entries =
                    readHistory()
                        .toMutableList()

                // Async persistence must never let an older snapshot overwrite a newer
                // cursor for the same episode. Position itself cannot be compared because
                // a deliberate rewind is a perfectly valid newer playback event.
                val existingForMediaKey =
                    entries.firstOrNull {
                        it.mediaKey == key
                    }
                if (
                    existingForMediaKey != null &&
                    existingForMediaKey.lastWatchedEpochMs > updateEpochMs
                ) {
                    return@synchronized
                }

                restoreContinueWatching(
                    media
                )

                entries.removeAll {
                    it.mediaKey == key
                }

                val updated =
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
                        lastWatchedEpochMs = updateEpochMs,
                    )
                entries.add(updated)

                writeContinueWatching(updated)

                writeHistory(
                    entries
                        .sortedByDescending {
                            it.lastWatchedEpochMs
                        }
                        .take(
                            MAX_HISTORY
                        )
                )
                if (updated.isCompleted) {
                    refreshHomeContinueWatchingCache(exact = true)
                } else {
                    updateHomeContinueWatchingCacheForPlayback(updated)
                }
            }
        }
    }

    /** Forget this title's viewing state while retaining Favorites and metadata. */
    @Synchronized
    fun resetTitleHistory(media: MediaItem) {
        synchronized(SYNC_STORAGE_LOCK) {
            synchronized(PLAYBACK_PERSISTENCE_LOCK) {
                val titleKey = continueWatchingTitleKey(media)
                val profileId = profileIdOverride ?: profileStore.activeProfileId()
                val resetAt = PlaybackUpdateClock.next()
                prefs.edit().putLong(scopedKey("history_reset:$titleKey"), resetAt).apply()
                PlaybackStore(appContext).clearTitle(profileId, titleKey)
                writeHistory(readHistory().filterNot { continueWatchingTitleKey(it.media) == titleKey })
                writeArray(scopedKey(continueWatchingStorageKey),
                    readContinueWatching().filterNot { continueWatchingTitleKey(it.media) == titleKey }.map(::playbackToJson))
                writeDismissedContinueWatchingKeys(dismissedContinueWatchingKeys() - titleKey)
                writeMarkedWatchedKeys(markedWatchedKeys() - titleKey)
                refreshHomeContinueWatchingCache(exact = true)
                if (publishTraktChanges) com.vueo.shared.core.trakt.TraktService.get(appContext)
                    .watchedChanged(media, false, profileId)
            }
        }
    }

    private fun resetAt(media: MediaItem): Long =
        prefs.getLong(scopedKey("history_reset:${continueWatchingTitleKey(media)}"), 0L)

    @Synchronized
    fun removeHistory(
        mediaKey: String,
    ) {
        synchronized(SYNC_STORAGE_LOCK) {
            writeArray(scopedKey(continueWatchingStorageKey),
                readContinueWatching().filterNot { it.mediaKey == mediaKey }.map(::playbackToJson))
            writeHistory(
                readHistory()
                    .filterNot {
                        it.mediaKey ==
                            mediaKey
                    }
            )
            refreshHomeContinueWatchingCache(exact = true)
        }
    }

    @Synchronized
    fun removeFromContinueWatching(
        entry: LibraryPlaybackEntry,
    ) {
        synchronized(SYNC_STORAGE_LOCK) {
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
            refreshHomeContinueWatchingCache(exact = false)
        }
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
        synchronized(SYNC_STORAGE_LOCK) {
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

            val metadataKey = scopedKey("marked_watched_metadata_v1")
            val metadata = runCatching { JSONObject(prefs.getString(metadataKey, "{}")) }.getOrElse { JSONObject() }
            if (watched) metadata.put(key, mediaToJson(media.copy(episodes = emptyList()))) else metadata.remove(key)
            prefs.edit().putString(metadataKey, metadata.toString()).apply()
            writeMarkedWatchedKeys(
                marked
            )
            refreshHomeContinueWatchingCache(exact = false)
            if (publishTraktChanges) com.vueo.shared.core.trakt.TraktService.get(appContext)
                .watchedChanged(media, watched, profileIdOverride ?: profileStore.activeProfileId())
        }
    }

    /** Metadata preserves title/year aliases for matching TMDB and IMDb recommendations. */
    fun markedWatchedMedia(): List<MediaItem> = synchronized(SYNC_STORAGE_LOCK) {
        val metadata = runCatching { JSONObject(prefs.getString(scopedKey("marked_watched_metadata_v1"), "{}")) }.getOrElse { JSONObject() }
        val known = readHistory().map { it.media } + readWatchlist().mapNotNull { row ->
            row.optJSONObject("media")?.let { runCatching { mediaFromJson(it) }.getOrNull() }
        }
        markedWatchedKeys().mapNotNull { key ->
            metadata.optJSONObject(key)?.let { runCatching { mediaFromJson(it) }.getOrNull() }
                ?: known.firstOrNull { continueWatchingTitleKey(it) == key }
                ?: key.split(':', limit = 2).takeIf { it.size == 2 }?.let {
                    MediaItem(id = it[1], type = it[0], name = it[1])
                }
        }
    }

    @Synchronized
    fun traktMarkedWatchedMedia(): List<MediaItem> = markedWatchedKeys().mapNotNull { key ->
        val parts = key.split(':', limit = 2)
        if (parts.size != 2) null else MediaItem(id = parts[1], type = parts[0], name = parts[1])
    }

    /** Cloud imports bypass mutation hooks and preserve changes still queued for upload. */
    @Synchronized
    fun mergeTraktWatchlist(remote: List<MediaItem>, pending: Set<String>) {
        synchronized(SYNC_STORAGE_LOCK) {
            val previous = prefs.getStringSet(scopedKey("trakt_watchlist_ids"), emptySet()).orEmpty()
            fun id(media: MediaItem) = "${media.type}:${media.id}"
            val local = readWatchlist().filter { row ->
                val media = mediaFromJson(row.getJSONObject("media"))
                id(media) !in previous || id(media) in pending
            }
            val localPending = local.map { mediaFromJson(it.getJSONObject("media")) }.map(::id).toSet()
            val incoming = remote.filter { id(it) !in pending && id(it) !in localPending }.map { media ->
                JSONObject().put("media", mediaToJson(media)).put("addedAt", System.currentTimeMillis())
            }
            writeArray(scopedKey(watchlistStorageKey), local + incoming)
            prefs.edit().putStringSet(scopedKey("trakt_watchlist_ids"), remote.map(::id).toSet()).apply()
        }
    }

    @Synchronized
    fun mergeTraktHistory(remote: List<LibraryPlaybackEntry>, protectedTitles: Set<String> = emptySet()) {
        synchronized(SYNC_STORAGE_LOCK) {
            synchronized(PLAYBACK_PERSISTENCE_LOCK) {
                val acceptedRemote = remote.filter { resetAt(it.media).let { cutoff -> cutoff == 0L || it.lastWatchedEpochMs > cutoff } }
                val previous = runCatching { JSONObject(prefs.getString(scopedKey("trakt_history_stamps"), "{}")) }.getOrDefault(JSONObject())
                val remoteKeys = acceptedRemote.map { it.mediaKey }.toSet()
                val local = readHistory().filterNot { entry -> entry.mediaKey !in remoteKeys &&
                    previous.has(entry.mediaKey) && entry.lastWatchedEpochMs == previous.optLong(entry.mediaKey) }
                val imported = acceptedRemote.filter { entry -> "${entry.media.type}:${entry.media.id}" !in protectedTitles && local.none { it.mediaKey == entry.mediaKey && it.lastWatchedEpochMs >= entry.lastWatchedEpochMs } }
                val merged = (local + imported).groupBy { it.mediaKey }.values.map { group -> group.maxBy { it.lastWatchedEpochMs } }
                    .sortedByDescending { it.lastWatchedEpochMs }.take(MAX_HISTORY)
                writeHistory(merged)
                // Watched truth is separate from the bounded recent history, so old movie watches remain known.
                val remoteMovies = acceptedRemote.filter { !ContinueWatchingPolicy.isSeries(it.media) && "${it.media.type}:${it.media.id}" !in protectedTitles }.map { continueWatchingTitleKey(it.media) }.toSet()
                val previousMovies = prefs.getStringSet(scopedKey("trakt_watched_movie_ids"), emptySet()).orEmpty()
                writeMarkedWatchedKeys((markedWatchedKeys() - (previousMovies - protectedTitles)) + remoteMovies)
                val stamps = JSONObject()
                acceptedRemote.forEach { stamps.put(it.mediaKey, it.lastWatchedEpochMs) }
                prefs.edit().putStringSet(scopedKey("trakt_watched_movie_ids"), remoteMovies)
                    .putString(scopedKey("trakt_history_stamps"), stamps.toString()).apply()
                refreshHomeContinueWatchingCache(exact = true)
            }
        }
    }

    @Synchronized
    fun mergeTraktPlayback(remote: List<LibraryPlaybackEntry>) {
        synchronized(SYNC_STORAGE_LOCK) {
            remote.forEach { entry ->
                val existing = readHistory().firstOrNull { it.mediaKey == entry.mediaKey }
                if (entry.lastWatchedEpochMs > 0L && (existing == null || existing.lastWatchedEpochMs < entry.lastWatchedEpochMs)) {
                    recordPlayback(entry.media, entry.videoId, entry.episodeTitle, entry.season, entry.episode,
                        entry.positionMs, entry.durationMs, entry.lastWatchedEpochMs)
                }
            }
        }
    }

    @Synchronized
    fun clearHistory() {
        synchronized(SYNC_STORAGE_LOCK) {
            prefs.edit()
                .remove(
                    scopedKey(
                        historyStorageKey
                    )
                )
                .remove(
                    scopedKey(
                        continueWatchingStorageKey
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
                .remove(
                    scopedKey(
                        KEY_HOME_CONTINUE_WATCHING_CACHE
                    )
                )
                .apply()
        }
    }

    @Synchronized
    fun clearContinueWatching() {
        synchronized(SYNC_STORAGE_LOCK) {
            writeDismissedContinueWatchingKeys(
                dismissedContinueWatchingKeys() +
                    continueWatchingPlaybackEntries()
                        .asSequence()
                        .filter { ContinueWatchingPolicy.isSeries(it.media) || (it.positionMs > 5_000L && !it.isCompleted) }
                        .map {
                            continueWatchingTitleKey(
                                it.media
                            )
                        }
                        .toSet()
            )
            refreshHomeContinueWatchingCache(exact = false)
        }
    }

    private fun updateHomeContinueWatchingCacheForPlayback(
        entry: LibraryPlaybackEntry,
    ) {
        val storageKey = scopedKey(KEY_HOME_CONTINUE_WATCHING_CACHE)
        val existing =
            if (prefs.contains(storageKey)) {
                readHomeContinueWatchingCache()
            } else {
                // One-time migration if playback is recorded before Home has ever
                // seeded the v133 cache. Keep all existing titles, not only this one.
                fastContinueWatching()
            }
        val titleKey = continueWatchingTitleKey(entry.media)
        val shouldShow =
            if (ContinueWatchingPolicy.isSeries(entry.media)) {
                !entry.isCompleted
            } else {
                entry.positionMs > 5_000L && !entry.isCompleted
            }
        val updated =
            buildList {
                if (shouldShow) add(entry)
                addAll(existing.filterNot { continueWatchingTitleKey(it.media) == titleKey })
            }.sortedByDescending { it.lastWatchedEpochMs }

        writeHomeContinueWatchingCache(updated)
    }

    private fun refreshHomeContinueWatchingCache(
        exact: Boolean,
    ) {
        val entries =
            if (exact) {
                val history = readHistory().sortedByDescending { it.lastWatchedEpochMs }
                resolveContinueWatching(
                    cursors = readContinueWatching(),
                    history = history,
                ).second
            } else {
                fastContinueWatching()
            }
        writeHomeContinueWatchingCache(entries)
    }

    private fun readHomeContinueWatchingCache(): List<LibraryPlaybackEntry> =
        readObjectArray(
            scopedKey(KEY_HOME_CONTINUE_WATCHING_CACHE)
        ).mapNotNull {
            runCatching { playbackFromJson(it) }.getOrNull()
        }

    private fun writeHomeContinueWatchingCache(
        entries: List<LibraryPlaybackEntry>,
    ) {
        writeArray(
            scopedKey(KEY_HOME_CONTINUE_WATCHING_CACHE),
            entries.map(::playbackToHomeCacheJson),
        )
    }

    private fun playbackToHomeCacheJson(
        entry: LibraryPlaybackEntry,
    ): JSONObject =
        JSONObject()
            .put("media", mediaToHomeCacheJson(entry.media))
            .put("videoId", entry.videoId)
            .put("episodeTitle", entry.episodeTitle)
            .put("season", entry.season)
            .put("episode", entry.episode)
            .put("positionMs", entry.positionMs)
            .put("durationMs", entry.durationMs)
            .put("lastWatchedEpochMs", entry.lastWatchedEpochMs)

    private fun mediaToHomeCacheJson(
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
            .put("sourceExtensionId", media.sourceExtensionId)
            .put("catalogSources", JSONArray(media.catalogSources))

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
        RatingChanges.revision.value += 1L
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

    private fun readContinueWatching(): List<LibraryPlaybackEntry> =
        readObjectArray(
            scopedKey(
                continueWatchingStorageKey
            )
        ).mapNotNull {
            runCatching {
                playbackFromJson(it).let { entry -> entry.copy(media=entry.media.copy(episodes=episodeCache.read(entry.media.type,entry.media.id))) }
            }.getOrNull()
        }

    private fun writeContinueWatching(entry: LibraryPlaybackEntry) {
        val existing = (readContinueWatching() + readHistory())
            .sortedByDescending { it.lastWatchedEpochMs }
            .distinctBy { continueWatchingTitleKey(it.media) }
        val titleKey = continueWatchingTitleKey(entry.media)
        val old = existing.firstOrNull { continueWatchingTitleKey(it.media) == titleKey }
        val episodes = (entry.media.episodes + old?.media?.episodes.orEmpty()).distinctBy { it.season to it.episode }

        // The title cursor follows the newest playback event, not whichever IO
        // job happens to finish last. This keeps EP4/EP5 switching and rewinds
        // deterministic even when player saves are dispatched in the background.
        val newest =
            if (
                old != null &&
                old.lastWatchedEpochMs > entry.lastWatchedEpochMs
            ) {
                old
            } else {
                entry
            }
        val cursor = newest.copy(
            media = newest.media.copy(
                episodes = (newest.media.episodes + episodes)
                    .distinctBy { it.season to it.episode }
            )
        )
        writeArray(scopedKey(continueWatchingStorageKey),
            (listOf(cursor) + existing.filterNot { continueWatchingTitleKey(it.media) == titleKey }).map(::playbackToJson))
    }

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

    private fun readObjectArray(key: String): List<JSONObject> = synchronized(SYNC_STORAGE_LOCK) {
        val raw=prefs.getString(key,null) ?: return@synchronized emptyList()
        runCatching {
            val entries=CompactLibraryCodec.readArray(raw,keepEpisodeIndex=true) { entry ->
                entry.optJSONObject("media")?.let { media ->
                    media.optJSONArray("episodes")?.let { episodes ->
                        episodeCache.save(media.optString("type"),media.optString("id"),episodes)
                        media.remove("episodes")
                    }
                }
            }
            // Migrate once under the same lock as local/remote array writers; retain playback fields.
            if(!prefs.getBoolean("compact_library_v2:$key",false)) {
                prefs.edit().putString(key,JSONArray(entries).toString())
                    .putBoolean("compact_library_v2:$key",true).apply()
            }
            entries
        }.getOrDefault(emptyList())
    }

    private fun writeArray(key: String,entries: List<JSONObject>) = synchronized(SYNC_STORAGE_LOCK) {
        val array=JSONArray(entries.map(CompactLibraryCodec::compactEntry))
        prefs.edit().putString(key,array.toString()).putBoolean("compact_library_v2:$key",true).apply()
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

    private fun mediaToJson(media: MediaItem): JSONObject {
        episodeCache.save(media.type,media.id,media.episodes)
        return JSONObject()
            .put("id",media.id).put("type",media.type).put("name",media.name).put("tmdbId",media.tmdbId)
            .put("poster",media.poster).put("background",media.background)
            .put("description",media.description).put("releaseInfo",media.releaseInfo)
            .put("originalTitle",media.originalTitle).put("aliases",JSONArray(media.aliases))
            .put("originalLanguage",media.originalLanguage).put("countries",JSONArray(media.countries))
            .put("genres",JSONArray(media.genres)).put("sourceExtensionId",media.sourceExtensionId)
            .put("catalogSources",JSONArray(media.catalogSources))
            .put("imdbRating",media.imdbRating).put("tmdbRating",media.tmdbRating)
            .put("runtimeMinutes",media.runtimeMinutes).put("certification",media.certification)
            .put("directors",JSONArray(media.directors)).put("creators",JSONArray(media.creators))
            .put("writers",JSONArray(media.writers))
    }

    private fun mediaFromJson(
        json: JSONObject,
    ): MediaItem =
        MediaItem(
            id = json.getString("id"),
            tmdbId = com.vueo.shared.core.plugin.TmdbResolver.metadataId(json),
            type = json.getString("type"),
            name = json.getString("name"),
            poster = json.optNullableString("poster"),
            background = json.optNullableString("background"),
            description = json.optNullableString("description"),
            releaseInfo = json.optNullableString("releaseInfo"),
            originalTitle = json.optNullableString("originalTitle"),
            aliases = json.optJSONArray("aliases").toStringList(),
            originalLanguage = json.optNullableString("originalLanguage"),
            countries = json.optJSONArray("countries").toStringList(),
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
            .put("tmdbId", company.tmdbId)

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
                tmdbId = item.optLong("tmdbId", -1L).takeIf { it > 0L },
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

        private const val KEY_CONTINUE_WATCHING =
            "continue_watching"

        private const val KEY_DISMISSED_CONTINUE_WATCHING =
            "dismissed_continue_watching"

        private const val KEY_MARKED_WATCHED =
            "marked_watched"

        private const val KEY_HOME_CONTINUE_WATCHING_CACHE =
            "home_continue_watching_cache_v1"

        private const val MAX_HISTORY =
            150

        internal val SYNC_STORAGE_LOCK = Any()
        private val PLAYBACK_PERSISTENCE_LOCK = SYNC_STORAGE_LOCK
    }
}
