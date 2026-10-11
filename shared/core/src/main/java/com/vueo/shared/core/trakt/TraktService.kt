package com.vueo.shared.core.trakt

import android.content.Context
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.ProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One shared account/sync service for Mobile and TV. All work captures a profile ID. */
class TraktService private constructor(private val context: Context) {
    private val store = TraktAccountStore(context)
    private val profiles = ProfileStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenGate = Mutex()
    private val syncGate = Mutex()
    private val queueLock = Any()
    private val generations = ConcurrentHashMap<String, Long>()
    private val _libraryRevision = MutableStateFlow(0L)
    val libraryRevision: StateFlow<Long> = _libraryRevision
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision
    private val _catalogRevision = MutableStateFlow(0L)
    val catalogRevision: StateFlow<Long> = _catalogRevision
    private fun invalidateCatalog(profile: String) {
        catalogCache.remove(profile)
        synchronized(_catalogRevision) { _catalogRevision.value += 1L }
    }
    private fun changed() { synchronized(_revision) { _revision.value += 1L } }
    fun profileId(): String = profiles.activeProfileId()
    fun connected(profile: String = profileId()) = store.connected(profile)
    fun username(profile: String = profileId()) = store.prefs.getString(store.key(profile, "username"), "").orEmpty()
    fun status(profile: String = profileId()) = store.prefs.getString(store.key(profile, "status"), "").orEmpty()
    fun enabled(name: String, profile: String = profileId()) = store.enabled(profile, name)
    fun setEnabled(name: String, enabled: Boolean, profile: String = profileId()) {
        store.prefs.edit().putBoolean(store.key(profile, name), enabled).apply()
        invalidateCatalog(profile)
        changed()
    }
    private fun status(profile: String, text: String) {
        if (status(profile) != text) {
            store.prefs.edit().putString(store.key(profile, "status"), text).apply()
            changed()
        }
    }
    private fun library(profile: String) = LibraryStore(context, profileIdOverride = profile, publishTraktChanges = false)
    data class DeviceLogin(val code: String, val userCode: String, val verificationUrl: String,
        val interval: Long, val expiresAt: Long, val profile: String, val generation: Long)

    suspend fun beginLogin(profile: String = profileId()): DeviceLogin = withContext(Dispatchers.IO) {
        val generation = (generations[profile] ?: 0L) + 1
        generations[profile] = generation
        val result = JSONObject(TraktHttp.request("/oauth/device/code", body = JSONObject()
            .put("client_id", TraktConfig.CLIENT_ID)).body)
        DeviceLogin(result.getString("device_code"), result.getString("user_code"), result.getString("verification_url"),
            result.optLong("interval", 5L).coerceAtLeast(5L),
            android.os.SystemClock.elapsedRealtime() + result.getLong("expires_in") * 1_000L, profile, generation)
    }
    fun cancelLogin(login: DeviceLogin) {
        if (!connected(login.profile) && generations[login.profile] == login.generation) generations[login.profile] = login.generation + 1
    }
    suspend fun awaitLogin(login: DeviceLogin) = withContext(Dispatchers.IO) {
        var interval = login.interval * 1_000L
        while (android.os.SystemClock.elapsedRealtime() < login.expiresAt && generations[login.profile] == login.generation) {
            delay(minOf(interval, (login.expiresAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)))
            if (android.os.SystemClock.elapsedRealtime() >= login.expiresAt) break
            val tokens = try {
                JSONObject(TraktHttp.request("/oauth/device/token", body = JSONObject()
                    .put("code", login.code).put("client_id", TraktConfig.CLIENT_ID)).body)
            } catch (error: TraktHttpError) {
                when (error.status) {
                    400 -> continue
                    429 -> { interval = maxOf(interval + 5_000L, error.retryMs); continue }
                    418 -> throw IOException("Trakt connection was declined.")
                    404, 409, 410 -> throw IOException("Login code expired or was used. Request a new code.")
                    else -> throw error
                }
            }
            tokenGate.withLock {
                if (generations[login.profile] != login.generation) throw CancellationException("Login cancelled")
                store.prefs.edit().putBoolean(store.key(login.profile, "verified_session"), false)
                    .putString(store.key(login.profile, "username"), "").apply()
                store.save(login.profile, tokens)
            }
            status(login.profile, "Connected. Syncing…")
            try {
                verifyIdentity(login.profile)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Connection remains usable; a later sync reloads account details. */ }
            invalidateCatalog(login.profile)
            changed()
            scheduleSync(login.profile, force = true)
            return@withContext
        }
        throw IOException("Login code expired. Request a new code.")
    }
    suspend fun disconnect(profile: String = profileId()) = withContext(Dispatchers.IO) {
        generations[profile] = (generations[profile] ?: 0L) + 1L
        val token = tokenGate.withLock {
            val saved = store.read(profile)?.optString("access_token")
            store.clear(profile)
            saved
        }
        invalidateCatalog(profile)
        changed()
        if (!token.isNullOrBlank()) {
            try { TraktHttp.request("/oauth/revoke", body = JSONObject().put("token", token).put("client_id", TraktConfig.CLIENT_ID)) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Local disconnect is already complete, including when offline. */ }
        }
    }
    private suspend fun accessToken(profile: String, rejected: String? = null): String = tokenGate.withLock {
        val session = store.read(profile) ?: throw IOException("Connect your Trakt account first.")
        val access = session.getString("access_token")
        val expires = (session.optLong("created_at") + session.optLong("expires_in", 604_800L)) * 1_000L
        if ((rejected == null && expires > System.currentTimeMillis() + 60_000L) ||
            (rejected != null && access != rejected)) return@withLock access
        try {
            val rotated = JSONObject(TraktHttp.request("/oauth/token", body = JSONObject()
                .put("client_id", TraktConfig.CLIENT_ID).put("redirect_uri", TraktConfig.REDIRECT_URI)
                .put("grant_type", "refresh_token").put("refresh_token", session.getString("refresh_token"))).body)
            store.save(profile, rotated)
            rotated.getString("access_token")
        } catch (error: TraktHttpError) {
            if (error.status == 400 || error.status == 401) {
                store.prefs.edit().remove(store.key(profile, "session")).apply()
                invalidateCatalog(profile)
                status(profile, "Session expired. Connect Trakt again.")
            }
            throw error
        }
    }
    internal suspend fun authenticated(path: String, profile: String, body: JSONObject? = null): TraktReply {
        val generation = generations[profile] ?: 0L
        val token = accessToken(profile)
        val reply = try { TraktHttp.request(path, token, body) } catch (error: TraktHttpError) {
            if (error.status != 401) throw error
            TraktHttp.request(path, accessToken(profile, token), body)
        }
        if (!connected(profile) || (generations[profile] ?: 0L) != generation) throw IOException("Trakt account changed.")
        return reply
    }
    fun watchlistChanged(media: MediaItem, added: Boolean, profile: String) {
        if (!connected(profile) || !store.enabled(profile, "sync")) return
        val payload = TraktMedia.payload(media) ?: return
        enqueue(profile, "watchlist:${media.type}:${media.id}", if (added) "/sync/watchlist" else "/sync/watchlist/remove", payload)
    }
    fun watchedChanged(media: MediaItem, watched: Boolean, profile: String) {
        if (!connected(profile) || !store.enabled(profile, "sync")) return
        val payload = TraktMedia.payload(media) ?: return
        enqueue(profile, "watched:${media.type}:${media.id}", if (watched) "/sync/history" else "/sync/history/remove", payload)
    }
    fun rating(media: MediaItem, profile: String = profileId()): Int =
        store.prefs.getInt(store.key(profile, "rating:${media.type}:${media.id}"), 0)
    fun rate(media: MediaItem, value: Int, profile: String = profileId()) {
        if (!connected(profile)) return
        val payload = TraktMedia.payload(media) ?: return
        val field = if (TraktMedia.isMovie(media)) "movies" else "shows"
        if (value > 0) payload.getJSONArray(field).getJSONObject(0).put("rating", value.coerceIn(1, 10))
        store.prefs.edit().putInt(store.key(profile, "rating:${media.type}:${media.id}"), value.coerceIn(0, 10)).apply()
        enqueue(profile, "rating:${media.type}:${media.id}", if (value > 0) "/sync/ratings" else "/sync/ratings/remove", payload)
        changed()
    }
    private fun queue(profile: String): JSONArray = try {
        JSONArray(store.prefs.getString(store.key(profile, "outbox"), "[]"))
    } catch (_: Exception) { JSONArray() }
    private fun enqueue(profile: String, key: String, path: String, body: JSONObject) {
        synchronized(queueLock) {
            val existing = queue(profile)
            val next = JSONArray()
            for (i in 0 until existing.length()) {
                val entry = existing.getJSONObject(i)
                if (entry.optString("key") != key) next.put(entry)
            }
            if (next.length() >= 500) {
                status(profile, "Sync queue is full. Sync Trakt before making more changes.")
                return
            }
            next.put(JSONObject().put("id", UUID.randomUUID().toString()).put("key", key).put("path", path).put("body", body))
            store.prefs.edit().putString(store.key(profile, "outbox"), next.toString()).apply()
        }
        invalidateCatalog(profile)
        scheduleSync(profile, force = true)
    }
    private suspend fun flush(profile: String) {
        while (connected(profile) && store.enabled(profile, "sync")) {
            val entry = synchronized(queueLock) { queue(profile).optJSONObject(0) } ?: return
            val reply = authenticated(entry.getString("path"), profile, entry.getJSONObject("body"))
            if (reply.body.isNotBlank()) {
                val missing = JSONObject(reply.body).optJSONObject("not_found")
                if (missing != null && listOf("movies", "shows", "episodes").any { (missing.optJSONArray(it)?.length() ?: 0) > 0 })
                    throw IOException("Trakt could not match an item. It remains queued.")
            }
            synchronized(queueLock) {
                val current = queue(profile)
                val next = JSONArray()
                for (i in 0 until current.length()) {
                    val candidate = current.getJSONObject(i)
                    if (candidate.getString("id") != entry.getString("id")) next.put(candidate)
                }
                store.prefs.edit().putString(store.key(profile, "outbox"), next.toString()).apply()
            }
        }
    }
    private suspend fun verifyIdentity(profile: String) {
        val user = JSONObject(authenticated("/users/settings", profile).body).getJSONObject("user")
        val ids = user.optJSONObject("ids")
        val identity = ids?.optString("trakt")?.takeUnless { it.isBlank() || it == "null" }
            ?: ids?.optString("slug")?.takeUnless { it.isBlank() }
            ?: user.getString("username")
        val old = store.prefs.getString(store.key(profile, "account_id"), "").orEmpty()
        val editor = store.prefs.edit()
        if (old.isNotBlank() && old != identity) {
            // Old queued changes must never be sent to a newly connected account.
            editor.remove(store.key(profile, "outbox"))
                .putBoolean(store.key(profile, "seeded_watchlist"), true)
                .putBoolean(store.key(profile, "seeded_history"), true)
                .putBoolean(store.key(profile, "seeded_marks_complete"), true)
            val prefix = store.key(profile, "rating:")
            store.prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        }
        editor.putString(store.key(profile, "account_id"), identity)
            .putString(store.key(profile, "username"), user.optString("username"))
            .putBoolean(store.key(profile, "verified_session"), true).apply()
        changed()
    }
    private val scheduled = ConcurrentHashMap<String, Boolean>()
    fun scheduleSync(profile: String = profileId(), force: Boolean = false) {
        if (!connected(profile) || !store.enabled(profile, "sync") || scheduled.putIfAbsent(profile, true) != null) return
        scope.launch {
            var retry = 0
            try {
                delay(1_500L)
                do {
                    try {
                        sync(profile, force || retry > 0)
                    } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
                        if (connected(profile)) status(profile, message(error))
                        if (!connected(profile) || !store.enabled(profile, "sync")) break
                        val pause = if (error is TraktHttpError && error.status == 429) error.retryMs else 30_000L * (retry + 1)
                        if (retry++ >= 2) break
                        delay(pause.coerceAtMost(300_000L))
                        continue
                    }
                    val pending = synchronized(queueLock) { queue(profile).length() > 0 }
                    if (!pending) break
                    delay(1_500L)
                    retry++
                } while (connected(profile) && store.enabled(profile, "sync"))
            } finally { scheduled.remove(profile) }
        }
    }
    suspend fun sync(profile: String = profileId(), force: Boolean = true) = withContext(Dispatchers.IO) {
        syncGate.withLock {
            if (!connected(profile) || !store.enabled(profile, "sync")) return@withLock
            val last = store.prefs.getLong(store.key(profile, "last_sync"), 0L)
            if (!force && System.currentTimeMillis() - last < 10 * 60_000L) return@withLock
            status(profile, "Syncing…")
            verifyIdentity(profile)
            val local = library(profile)
            val known = (local.watchlist() + local.history().map { it.media }).associateBy { "${it.type}:${it.id}" }
            // Upload existing local watchlist once; subsequent edits use the durable outbox.
            if (!store.prefs.getBoolean(store.key(profile, "seeded_watchlist"), false)) {
                val items = local.watchlist().mapNotNull { TraktMedia.payload(it) }
                val payload = JSONObject().put("movies", JSONArray()).put("shows", JSONArray())
                items.forEach { item -> listOf("movies", "shows").forEach { field ->
                    item.optJSONArray(field)?.let { array -> for (i in 0 until array.length()) payload.getJSONArray(field).put(array.getJSONObject(i)) }
                } }
                if (items.isNotEmpty()) authenticated("/sync/watchlist", profile, payload)
                store.prefs.edit().putBoolean(store.key(profile, "seeded_watchlist"), true).apply()
            }
            flush(profile)
            val watchlist = fetchPages("/sync/watchlist?extended=full", profile).mapNotNull { TraktMedia.unwrap(it, known) }
            val pendingWatchlist = synchronized(queueLock) { queue(profile).let { array ->
                (0 until array.length()).map { array.getJSONObject(it).optString("key") }.filter { it.startsWith("watchlist:") }.map { it.removePrefix("watchlist:") }.toSet()
            } }
            local.mergeTraktWatchlist(watchlist, pendingWatchlist)
            val imports = mutableListOf<LibraryPlaybackEntry>()
            for (type in listOf("movies", "shows")) {
                val watched = JSONArray(authenticated("/sync/watched/$type?extended=full", profile).body)
                for (i in 0 until watched.length()) {
                    val row = watched.getJSONObject(i)
                    val media = TraktMedia.unwrap(row, known) ?: continue
                    if (type == "movies") {
                        val duration = (media.runtimeMinutes ?: 120) * 60_000L
                        imports += LibraryPlaybackEntry(media, media.id, positionMs = duration, durationMs = duration,
                            lastWatchedEpochMs = epoch(row.optString("last_watched_at")))
                    } else {
                        val seasons = row.optJSONArray("seasons") ?: continue
                        for (s in 0 until seasons.length()) {
                            val season = seasons.getJSONObject(s)
                            val episodes = season.optJSONArray("episodes") ?: continue
                            for (e in 0 until episodes.length()) {
                                val episode = episodes.getJSONObject(e)
                                val duration = (media.runtimeMinutes ?: 45) * 60_000L
                                val sn = season.getInt("number"); val en = episode.getInt("number")
                                imports += LibraryPlaybackEntry(media, "${media.id}:$sn:$en", season = sn, episode = en,
                                    positionMs = duration, durationMs = duration, lastWatchedEpochMs = epoch(episode.optString("last_watched_at")))
                            }
                        }
                    }
                }
            }
            // Upload completed local watches once, after comparing remote truth to avoid duplicate plays.
            if (!store.prefs.getBoolean(store.key(profile, "seeded_history"), false)) {
                val localHistory = local.history().filter { it.isCompleted && !store.prefs.getBoolean(store.key(profile, "seeded_history:${it.mediaKey}"), false) && imports.none { remote -> remote.mediaKey == it.mediaKey } }
                for (entry in localHistory) {
                    if (TraktMedia.isShow(entry.media) && (entry.season == null || entry.episode == null)) continue
                    val payload = TraktMedia.payload(entry.media, entry.season, entry.episode) ?: continue
                    val field = if (TraktMedia.isMovie(entry.media)) "movies" else "shows"
                    val item = payload.getJSONArray(field).getJSONObject(0)
                    val watchedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(entry.lastWatchedEpochMs))
                    if (field == "movies") item.put("watched_at", watchedAt)
                    else item.optJSONArray("seasons")?.optJSONObject(0)?.optJSONArray("episodes")?.optJSONObject(0)?.put("watched_at", watchedAt)
                    authenticated("/sync/history", profile, payload)
                    // Checkpoint each successful item; interrupted initial sync will not re-send it.
                    store.prefs.edit().putBoolean(store.key(profile, "seeded_history:${entry.mediaKey}"), true).apply()
                }
                store.prefs.edit().putBoolean(store.key(profile, "seeded_history"), true).apply()
            }
            if (!store.prefs.getBoolean(store.key(profile, "seeded_marks_complete"), false)) {
            for (media in local.traktMarkedWatchedMedia()) {
                val marker = store.key(profile, "seeded_mark:${media.type}:${media.id}")
                if (store.prefs.getBoolean(marker, false)) continue
                if (TraktMedia.isMovie(media) && imports.any { it.media.id == media.id && TraktMedia.isMovie(it.media) }) {
                    store.prefs.edit().putBoolean(marker, true).apply()
                    continue
                }
                val payload = TraktMedia.payload(media) ?: continue
                authenticated("/sync/history", profile, payload)
                store.prefs.edit().putBoolean(marker, true).apply()
            }
            store.prefs.edit().putBoolean(store.key(profile, "seeded_marks_complete"), true).apply()
            }
            val pendingWatched = synchronized(queueLock) { queue(profile).let { array ->
                (0 until array.length()).map { array.getJSONObject(it).optString("key") }
                    .filter { it.startsWith("watched:") }.map { it.removePrefix("watched:") }.toSet()
            } }
            local.mergeTraktHistory(imports, pendingWatched)
            val seededMarks = store.prefs.edit()
            imports.filter { TraktMedia.isMovie(it.media) }.forEach {
                seededMarks.putBoolean(store.key(profile, "seeded_mark:${it.media.type}:${it.media.id}"), true)
            }
            seededMarks.apply()
            val playback = fetchPages("/sync/playback?extended=full", profile).mapNotNull { row ->
                val media = TraktMedia.unwrap(row, known) ?: return@mapNotNull null
                val episode = row.optJSONObject("episode")
                val season = episode?.optInt("season")
                val number = episode?.optInt("number")
                val duration = (media.runtimeMinutes ?: if (TraktMedia.isMovie(media)) 120 else 45) * 60_000L
                val progress = row.optDouble("progress", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
                LibraryPlaybackEntry(media, if (episode == null) media.id else "${media.id}:$season:$number",
                    episodeTitle = episode?.optString("title"), season = season, episode = number,
                    positionMs = (duration * progress / 100.0).toLong(), durationMs = duration,
                    lastWatchedEpochMs = epoch(row.optString("paused_at")))
            }
            local.mergeTraktPlayback(playback)
            for (type in listOf("movies", "shows")) {
                val ratings = fetchPages("/sync/ratings/$type?extended=full", profile)
                val editor = store.prefs.edit()
                val pendingRatings = synchronized(queueLock) { queue(profile).let { array ->
                    (0 until array.length()).map { array.getJSONObject(it).optString("key") }.toSet()
                } }
                val prefix = store.key(profile, "rating:")
                val currentType = if (type == "movies") "movie" else "series"
                store.prefs.all.keys.filter { it.startsWith("$prefix$currentType:") }.forEach { key ->
                    if (key.removePrefix("profile:$profile:") !in pendingRatings) editor.remove(key)
                }
                ratings.forEach { row -> TraktMedia.unwrap(row, known)?.let { media ->
                    if ("rating:${media.type}:${media.id}" !in pendingRatings)
                        editor.putInt(store.key(profile, "rating:${media.type}:${media.id}"), row.optInt("rating"))
                } }
                editor.apply()
            }
            if (username(profile).isBlank()) {
                val user = JSONObject(authenticated("/users/settings", profile).body).optJSONObject("user")
                store.prefs.edit().putString(store.key(profile, "username"), user?.optString("username").orEmpty()).apply()
            }
            store.prefs.edit().putLong(store.key(profile, "last_sync"), System.currentTimeMillis()).apply()
            invalidateCatalog(profile)
            _libraryRevision.value += 1L
            status(profile, "Synced with Trakt")
        }
    }
    private suspend fun fetchPages(path: String, profile: String): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var page = 1
        do {
            val reply = authenticated("$path&limit=100&page=$page", profile)
            val array = JSONArray(reply.body)
            for (i in 0 until array.length()) items += array.getJSONObject(i)
            if (page >= reply.pages) return items
            if (page >= 50) throw IOException("Large Trakt library: sync needs more than 5,000 items.")
            page++
        } while (true)
    }
    private data class CatalogCache(val rows: List<CatalogRow>, val until: Long)
    private val catalogCache = ConcurrentHashMap<String, CatalogCache>()
    private val catalogGate = Mutex()
    private val publicCache = ConcurrentHashMap<String, Pair<String, Long>>()
    fun cachedHomeRows(profile: String = profileId()): List<CatalogRow> =
        if (store.enabled(profile, "discovery")) catalogCache[profile]?.rows.orEmpty() else emptyList()
    suspend fun homeRows(profile: String = profileId()): List<CatalogRow> = withContext(Dispatchers.IO) {
        if (!store.enabled(profile, "discovery")) return@withContext emptyList()
        catalogGate.withLock {
            catalogCache[profile]?.takeIf { it.until > System.currentTimeMillis() }?.let { return@withLock it.rows }
            val rows = mutableListOf<CatalogRow>()
            suspend fun add(path: String, title: String, id: String, auth: Boolean, type: String? = null) {
                try {
                    val body = if (auth) authenticated(path, profile).body else {
                        publicCache[path]?.takeIf { it.second > System.currentTimeMillis() }?.first
                            ?: TraktHttp.request(path).body.also { publicCache[path] = it to (System.currentTimeMillis() + 30 * 60_000L) }
                    }
                    val array = JSONArray(body)
                    val items = (0 until minOf(array.length(), 18)).mapNotNull { index ->
                        val json = array.optJSONObject(index) ?: return@mapNotNull null
                        TraktMedia.unwrap(json) ?: type?.let { TraktMedia.parse(json, it) }
                    }.distinctBy { "${it.type}:${it.id}" }
                    if (items.isNotEmpty()) rows += CatalogRow("trakt:$id", title, "Trakt", items)
                } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { status(profile, message(error)) }
            }
            for ((endpoint, title) in listOf("trending" to "Trending", "popular" to "Popular")) {
                for ((namespace, type, label) in listOf(Triple("movies", "movie", "Movies"), Triple("shows", "series", "Shows")))
                    add("/$namespace/$endpoint?extended=full&limit=18", "Trakt $title · $label", "$endpoint-$type", false, type)
            }
            if (connected(profile)) {
                add("/recommendations/movies?extended=full&limit=18", "Trakt For You · Movies", "recommendations-movies", true, "movie")
                add("/recommendations/shows?extended=full&limit=18", "Trakt For You · Shows", "recommendations-shows", true, "series")
                add("/sync/watchlist/movies?extended=full&limit=18", "Trakt Watchlist · Movies", "watchlist-movies", true)
                add("/sync/watchlist/shows?extended=full&limit=18", "Trakt Watchlist · Shows", "watchlist-shows", true)
                try {
                    val lists = JSONArray(authenticated("/users/me/lists", profile).body)
                    for (i in 0 until minOf(lists.length(), 3)) {
                        val list = lists.getJSONObject(i)
                        val id = list.getJSONObject("ids").getLong("trakt")
                        add("/users/me/lists/$id/items?extended=full&limit=18", list.optString("name", "Trakt List"), "list-$id", true)
                    }
                } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { status(profile, message(error)) }
            }
            val result = rows.toList()
            catalogCache[profile] = CatalogCache(result, System.currentTimeMillis() + if (result.isEmpty()) 60_000L else 30 * 60_000L)
            result
        }
    }
    private data class ScrobbleEvent(val generation: Long, val profile: String, val media: MediaItem, val season: Int?, val episode: Int?, val action: String, val progress: Double)
    private val scrobbleEvents = kotlinx.coroutines.channels.Channel<ScrobbleEvent>(64)
    init {
        scope.launch {
            for (event in scrobbleEvents) {
                if (event.generation == (generations[event.profile] ?: 0L))
                    performScrobble(event.profile, event.media, event.season, event.episode, event.action, event.progress)
            }
        }
    }
    internal fun sendScrobble(profile: String, media: MediaItem, season: Int?, episode: Int?, action: String, progress: Double) {
        if (!connected(profile) || !store.enabled(profile, "scrobble") || !store.enabled(profile, "verified_session", false)) return
        if (scrobbleEvents.trySend(ScrobbleEvent(generations[profile] ?: 0L, profile, media, season, episode, action, progress)).isFailure)
            status(profile, "Trakt is busy. Scrobble event could not be queued.")
    }
    private val episodeIds = object : LinkedHashMap<String, JSONObject>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?): Boolean = size > 100
    }
    private suspend fun performScrobble(profile: String, media: MediaItem, season: Int?, episode: Int?, action: String, progress: Double) {
        if (!connected(profile) || !store.enabled(profile, "scrobble") || !store.enabled(profile, "verified_session", false)) return
        run {
            try {
                val item = if (TraktMedia.isMovie(media)) JSONObject().put("movie", JSONObject().put("ids", TraktMedia.ids(media) ?: return@run))
                else {
                    if (season == null || episode == null || !TraktMedia.isShow(media)) return@run
                    val cacheKey = "${media.type}:${media.id}:$season:$episode"
                    val cached = episodeIds[cacheKey]
                    if (cached != null) JSONObject().put("episode", JSONObject().put("ids", cached)) else {
                    val id = TraktMedia.ids(media) ?: return@run
                    val target = if (id.has("imdb")) id.getString("imdb") else {
                        val matches = JSONArray(TraktHttp.request("/search/tmdb/${id.getLong("tmdb")}?type=show&limit=1").body)
                        matches.optJSONObject(0)?.optJSONObject("show")?.optJSONObject("ids")?.optLong("trakt")?.takeIf { it > 0 }?.toString() ?: return@run
                    }
                    val showEpisode = JSONObject(TraktHttp.request("/shows/$target/seasons/$season/episodes/$episode").body)
                    val ids = showEpisode.getJSONObject("ids")
                    episodeIds[cacheKey] = ids
                    JSONObject().put("episode", JSONObject().put("ids", ids))
                    }
                }
                val reply = authenticated("/scrobble/$action", profile, item.put("progress", progress.coerceIn(0.0, 100.0)))
                if (action == "stop" && reply.body.isNotBlank() && JSONObject(reply.body).optString("action") == "scrobble") scheduleSync(profile, force = true)
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: TraktHttpError) { if (error.status !in listOf(409, 422)) status(profile, message(error)) } catch (error: Exception) { status(profile, message(error)) }
        }
    }
    companion object {
        @Volatile private var instance: TraktService? = null
        fun get(context: Context): TraktService = instance ?: synchronized(this) {
            instance ?: TraktService(context.applicationContext).also { instance = it }
        }
        fun message(error: Exception): String = when {
            error is TraktHttpError && error.status == 429 -> "Trakt rate limit. Retry after ${error.retryMs / 1_000L}s."
            error is TraktHttpError -> "Trakt request failed (${error.status}). Try syncing again."
            error is IOException && error.message?.startsWith("Trakt") == true -> error.message.orEmpty()
            else -> "Could not connect to Trakt. Check your connection and retry."
        }
        private fun epoch(value: String): Long = try {
            val format = if (value.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
            SimpleDateFormat(format, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}
