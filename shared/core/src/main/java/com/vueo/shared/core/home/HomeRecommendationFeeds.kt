package com.vueo.shared.core.home

import android.content.Context
import android.util.AtomicFile
import com.vueo.shared.core.extensions.toJson
import com.vueo.shared.core.extensions.toMediaItems
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.recommendation.RelatedContentOrchestrator
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryViewingPolicy
import com.vueo.shared.core.storage.RecommendationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** App-owned work survives a Home tab switch. Catalog partials never enter the request key. */
object HomeRecommendationFeeds {
    internal const val FRESH_MS = 15 * 60_000L
    internal const val RETRY_MS = 90_000L
    internal const val STALE_MS = 24 * 60 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)

    data class Request(
        val profile: String,
        val history: List<LibraryPlaybackEntry>,
        val favorites: List<MediaItem>,
        val engine: RecommendationEngine,
        val tmdbKey: String,
        val traktClientId: String,
        val recommendationsEnabled: Boolean,
        val similarEnabled: Boolean,
        val forYouEnabled: Boolean = true,
        val becauseEnabled: Boolean = true,
    )

    private data class Record(
        val sections: HomeRecommendationSections = HomeRecommendationSections(),
        val forYouSignature: String = "", val forYouAt: Long = 0L, val forYouRetry: Boolean = false,
        val becauseSignature: String = "", val becauseAt: Long = 0L, val becauseRetry: Boolean = false,
        val forYouSavedAt: Long = 0L, val becauseSavedAt: Long = 0L,
    )
    private class Stage {
        val generation = AtomicLong()
        var requestedSignature = ""
        @Volatile var job: Job? = null
    }
    private class Entry(val file: AtomicFile, val profile: String) {
        val mutex = Mutex()
        val publicationLock = Any()
        val state = MutableStateFlow(HomeRecommendationSections())
        var initialized = false
        var record = Record()
        val forYou = Stage()
        val because = Stage()
        @Volatile var visibility: Request? = null
    }

    internal fun fresh(at: Long, retry: Boolean, now: Long): Boolean =
        at > 0L && now >= at && now - at < if (retry) RETRY_MS else FRESH_MS

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun scopeKey(request: Request) = hash(listOf("v1", request.profile, request.engine.name,
        request.tmdbKey, request.traktClientId, request.recommendationsEnabled, request.similarEnabled).joinToString("\u0000"))

    internal fun requestSignatures(request: Request, now: Long): Pair<String, String> {
        val seeds = if (request.forYouEnabled) ForYouPolicy.seeds(request.history, request.favorites, now) else emptyList()
        val excluded = request.history.filter(LibraryViewingPolicy::isMeaningfullyWatched)
            .flatMap { ForYouPolicy.aliases(it.media) }.distinct().sorted()
        val favorites = request.favorites.flatMap(ForYouPolicy::aliases).distinct().sorted()
        val forYou = if (!request.forYouEnabled) "" else hash(listOf(now / 86_400_000L, seeds.map {
            listOf(it.media.copy(episodes = emptyList()), (it.weight * 10).toInt())
        }, excluded, favorites).toString())
        val seed = if (request.becauseEnabled) HomeRecommendationPolicy.latestMeaningfulSeed(request.history) else null
        val because = if (!request.becauseEnabled) "" else hash(listOf(seed?.copy(episodes = emptyList()), excluded).toString())
        return forYou to because
    }

    /** Preserve order, but never show newly watched/favorited titles or a mismatched Because seed. */
    internal fun visible(cached: HomeRecommendationSections, request: Request): HomeRecommendationSections {
        val watched = request.history.filter(LibraryViewingPolicy::isMeaningfullyWatched)
            .flatMap { ForYouPolicy.aliases(it.media) }.toSet()
        val excluded = watched + request.favorites.flatMap(ForYouPolicy::aliases)
        val seed = HomeRecommendationPolicy.latestMeaningfulSeed(request.history)
        val sameSeed = seed != null && cached.becauseYouWatchedSeed?.let {
            ForYouPolicy.identity(it) == ForYouPolicy.identity(seed)
        } == true
        return HomeRecommendationSections(
            forYou = if (request.forYouEnabled) cached.forYou.filter { ForYouPolicy.aliases(it).none(excluded::contains) } else emptyList(),
            becauseYouWatchedSeed = if (request.becauseEnabled) seed else null,
            becauseYouWatched = if (request.becauseEnabled && sameSeed) cached.becauseYouWatched.filter {
                ForYouPolicy.aliases(it).none(watched::contains) &&
                    ForYouPolicy.identity(it) != ForYouPolicy.identity(seed)
            } else emptyList(),
        )
    }

    suspend fun observe(context: Context, request: Request): StateFlow<HomeRecommendationSections> = withContext(Dispatchers.IO) {
        val key = scopeKey(request)
        val entry = synchronized(entries) {
            entries.getOrPut(key) {
                val directory = File(context.applicationContext.cacheDir, "vueo-home-recommendations-v1").apply { mkdirs() }
                Entry(AtomicFile(File(directory, "$key.json")), request.profile)
            }.also {
                while (entries.size > 16) {
                    val oldest = entries.entries.firstOrNull { it.key != key && it.value.forYou.job?.isActive != true && it.value.because.job?.isActive != true } ?: break
                    entries.remove(oldest.key)
                }
            }
        }
        entry.mutex.withLock {
            if (!entry.initialized) { entry.record = read(entry.file); entry.initialized = true }
            val now = System.currentTimeMillis()
            val (forYouSignature, becauseSignature) = requestSignatures(request, now)
            entry.record = entry.record.copy(sections = entry.record.sections.copy(
                forYou = entry.record.sections.forYou.takeIf { usable(entry.record.forYouSavedAt, now) }.orEmpty(),
                becauseYouWatched = entry.record.sections.becauseYouWatched.takeIf { usable(entry.record.becauseSavedAt, now) }.orEmpty()))
            val record = entry.record
            val forYouFresh = record.forYouSignature == forYouSignature && fresh(record.forYouAt, record.forYouRetry, now)
            val becauseFresh = record.becauseSignature == becauseSignature && fresh(record.becauseAt, record.becauseRetry, now)
            synchronized(entry.publicationLock) {
                entry.visibility = request
                entry.state.value = visible(record.sections, request)
            }
            if (!request.forYouEnabled) stop(entry.forYou)
            else if (!forYouFresh && !(entry.forYou.requestedSignature == forYouSignature && entry.forYou.job?.isActive == true)) {
                stop(entry.forYou)
                val generation = entry.forYou.generation.incrementAndGet()
                entry.forYou.requestedSignature = forYouSignature
                entry.forYou.job = scope.launch { refreshForYou(entry, generation, request, forYouSignature) }
            }
            if (!request.becauseEnabled) stop(entry.because)
            else if (!becauseFresh && !(entry.because.requestedSignature == becauseSignature && entry.because.job?.isActive == true)) {
                stop(entry.because)
                val generation = entry.because.generation.incrementAndGet()
                entry.because.requestedSignature = becauseSignature
                entry.because.job = scope.launch { refreshBecause(entry, generation, request, becauseSignature) }
            }
            entry.state
        }
    }

    private fun stop(stage: Stage) {
        stage.generation.incrementAndGet()
        stage.job?.cancel()
    }

    private suspend fun refreshForYou(entry: Entry, generation: Long, request: Request, signature: String) {
        try {
            val sources = ForYouCandidateLoader.load(request.profile, request.history, request.favorites, request.traktClientId)
            val result = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                ForYouPolicy.rank(sources.seeds, sources.batches, sources.discovery, request.history,
                    request.favorites, limit = 12, checkActive = { context.ensureActive() })
            }
            publish(entry, generation, request, forYou = true) { old ->
                old.copy(sections = old.sections.copy(forYou = result.ifEmpty {
                    visible(old.sections, request.copy(forYouEnabled = true, becauseEnabled = true)).forYou }),
                    forYouSignature = signature, forYouAt = System.currentTimeMillis(), forYouRetry = result.isEmpty(),
                    forYouSavedAt = if (result.isNotEmpty()) System.currentTimeMillis() else old.forYouSavedAt)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { markRetry(entry, generation, signature, true) }
    }

    private suspend fun refreshBecause(entry: Entry, generation: Long, request: Request, signature: String) {
        try {
            val seed = HomeRecommendationPolicy.latestMeaningfulSeed(request.history)
            val related = seed?.let { RelatedContentOrchestrator.direct(it, request.tmdbKey,
                request.recommendationsEnabled, request.similarEnabled, 18, request.engine, request.traktClientId) }.orEmpty()
            val watched = request.history.filter(LibraryViewingPolicy::isMeaningfullyWatched)
                .map { "${it.media.type}:${it.media.id}" }.toSet()
            val result = seed?.let { HomeRecommendationPolicy.filterBecauseRelated(related, it, watched, 12) }.orEmpty()
            publish(entry, generation, request, forYou = false) { old ->
                old.copy(sections = old.sections.copy(becauseYouWatchedSeed = seed,
                    becauseYouWatched = result.ifEmpty {
                        visible(old.sections, request.copy(forYouEnabled = true, becauseEnabled = true)).becauseYouWatched }),
                    becauseSignature = signature, becauseAt = System.currentTimeMillis(), becauseRetry = seed != null && result.isEmpty(),
                    becauseSavedAt = if (result.isNotEmpty()) System.currentTimeMillis() else old.becauseSavedAt)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { markRetry(entry, generation, signature, false) }
    }

    private suspend fun publish(entry: Entry, generation: Long, request: Request, forYou: Boolean, transform: (Record) -> Record) {
        entry.mutex.withLock {
            currentCoroutineContext().ensureActive()
            if ((if (forYou) entry.forYou else entry.because).generation.get() != generation) return@withLock
            entry.record = transform(entry.record)
            synchronized(entry.publicationLock) { entry.state.value = visible(entry.record.sections, entry.visibility ?: request) }
            write(entry.file, entry.record)
        }
    }
    private suspend fun markRetry(entry: Entry, generation: Long, signature: String, forYou: Boolean) {
        entry.mutex.withLock {
            currentCoroutineContext().ensureActive()
            if ((if (forYou) entry.forYou else entry.because).generation.get() != generation) return@withLock
            entry.record = if (forYou) entry.record.copy(forYouSignature = signature, forYouAt = System.currentTimeMillis(), forYouRetry = true)
                else entry.record.copy(becauseSignature = signature, becauseAt = System.currentTimeMillis(), becauseRetry = true)
            write(entry.file, entry.record)
        }
    }

    fun cancelProfile(profile: String, forYou: Boolean = true, because: Boolean = true) {
        synchronized(entries) {
            entries.values.filter { it.profile == profile }.forEach { entry ->
                if (forYou) stop(entry.forYou)
                if (because) stop(entry.because)
                synchronized(entry.publicationLock) {
                entry.visibility = entry.visibility?.let { request -> request.copy(
                    forYouEnabled = request.forYouEnabled && !forYou,
                    becauseEnabled = request.becauseEnabled && !because) }
                entry.state.update { sections -> sections.copy(
                    forYou = if (forYou) emptyList() else sections.forYou,
                    becauseYouWatchedSeed = if (because) null else sections.becauseYouWatchedSeed,
                    becauseYouWatched = if (because) emptyList() else sections.becauseYouWatched) }
                }
            }
        }
    }

    private fun usable(at: Long, now: Long) = at > 0 && now >= at && now - at < STALE_MS

    private fun read(file: AtomicFile): Record = runCatching {
        if (!file.baseFile.exists() || file.baseFile.length() > 256 * 1024) return@runCatching Record()
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val now = System.currentTimeMillis()
        val fyAt = json.optLong("fyAt"); val becauseAt = json.optLong("becauseAt")
        val fySaved = json.optLong("fySaved"); val becauseSaved = json.optLong("becauseSaved")
        Record(HomeRecommendationSections(
            if (usable(fySaved, now)) json.optJSONArray("forYou").toMediaItems().take(12) else emptyList(),
            json.optJSONArray("seed").toMediaItems().firstOrNull(),
            if (usable(becauseSaved, now)) json.optJSONArray("because").toMediaItems().take(12) else emptyList()),
            json.optString("fySignature"), fyAt, json.optBoolean("fyRetry"),
            json.optString("becauseSignature"), becauseAt, json.optBoolean("becauseRetry"), fySaved, becauseSaved)
    }.getOrDefault(Record())

    private fun write(file: AtomicFile, record: Record) {
        runCatching {
            val json = JSONObject().put("forYou", record.sections.forYou.toJson())
                .put("seed", listOfNotNull(record.sections.becauseYouWatchedSeed).toJson())
                .put("because", record.sections.becauseYouWatched.toJson())
                .put("fySignature", record.forYouSignature).put("fyAt", record.forYouAt).put("fyRetry", record.forYouRetry)
                .put("fySaved", record.forYouSavedAt).put("becauseSaved", record.becauseSavedAt)
                .put("becauseSignature", record.becauseSignature).put("becauseAt", record.becauseAt).put("becauseRetry", record.becauseRetry)
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > 256 * 1024) return@runCatching
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
            // Bound the persistent cache across profiles, engines and changed credentials.
            file.baseFile.parentFile?.listFiles { item -> item.extension == "json" }
                ?.sortedByDescending(File::lastModified)?.drop(16)?.forEach { it.delete() }
        }
    }
}
