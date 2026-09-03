package com.vueo.app.core.dna

import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.storage.LibraryPlaybackEntry
import com.vueo.app.core.storage.LibraryStore
import kotlin.math.roundToInt

/**
 * Local-first taste model for the active VUEO profile.
 *
 * The engine reads only profile-scoped LibraryStore data. Nothing is sent
 * off-device and no account, backend or AI API is required.
 *
 * DNA is intentionally calculated from source data instead of persisted so it
 * cannot become stale when History or My List changes. A cache can be added
 * later if profiling shows that it is useful.
 */
class UserDnaEngine(
    private val libraryStore: LibraryStore,
) {
    fun build(): UserDnaSnapshot =
        analyze(
            history = libraryStore.history(),
            myList = libraryStore.watchlist(),
        )

    /**
     * Pure analysis entry point, useful for tests and future import/sync flows.
     */
    fun analyze(
        history: List<LibraryPlaybackEntry>,
        myList: List<MediaItem>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): UserDnaSnapshot {
        val signals = linkedMapOf<String, MutableMediaSignal>()

        history.forEach { entry ->
            if (entry.positionMs <= MIN_MEANINGFUL_POSITION_MS) {
                return@forEach
            }

            val key = mediaIdentity(entry.media)
            val signal = signals.getOrPut(key) {
                MutableMediaSignal(entry.media)
            }

            signal.media = chooseRicherMedia(signal.media, entry.media)
            signal.historyWeight =
                (signal.historyWeight + watchWeight(entry, nowEpochMs))
                    .coerceAtMost(MAX_HISTORY_WEIGHT_PER_TITLE)
            signal.historyEntries += 1
            signal.lastInteractionEpochMs =
                maxOf(
                    signal.lastInteractionEpochMs,
                    entry.lastWatchedEpochMs,
                )
        }

        myList.forEach { media ->
            val key = mediaIdentity(media)
            val signal = signals.getOrPut(key) {
                MutableMediaSignal(media)
            }

            signal.media = chooseRicherMedia(signal.media, media)
            signal.myListWeight = MY_LIST_WEIGHT
        }

        val weightedSignals =
            signals.values
                .mapNotNull { signal ->
                    val weight =
                        signal.historyWeight + signal.myListWeight
                    if (weight <= 0.0) {
                        null
                    } else {
                        WeightedMediaSignal(
                            media = signal.media,
                            weight = weight,
                        )
                    }
                }

        val genreScores = linkedMapOf<String, Double>()
        val typeScores = linkedMapOf<String, Double>()
        val decadeScores = linkedMapOf<String, Double>()

        weightedSignals.forEach { signal ->
            val genres =
                signal.media.genres
                    .mapNotNull(::canonicalGenre)
                    .distinct()

            genres.forEach { genre ->
                genreScores[genre] =
                    genreScores.getOrDefault(genre, 0.0) + signal.weight
            }

            canonicalType(signal.media.type)?.let { type ->
                typeScores[type] =
                    typeScores.getOrDefault(type, 0.0) + signal.weight
            }

            releaseDecade(signal.media.releaseInfo)?.let { decade ->
                decadeScores[decade] =
                    decadeScores.getOrDefault(decade, 0.0) + signal.weight
            }
        }

        val topGenres = normalize(genreScores, limit = 8)
        val typeBreakdown = normalize(typeScores, limit = 4)
        val decadeBreakdown = normalize(decadeScores, limit = 5)

        val meaningfulHistory =
            history.filter {
                it.positionMs > MIN_MEANINGFUL_POSITION_MS
            }

        val durationKnown =
            meaningfulHistory.filter {
                it.durationMs > 0L
            }

        val completedEntries =
            meaningfulHistory.count {
                it.isCompleted
            }

        val abandonedEntries =
            durationKnown.count { entry ->
                !entry.isCompleted &&
                    entry.progressFraction in 0.01f..<ABANDONED_PROGRESS_THRESHOLD &&
                    nowEpochMs - entry.lastWatchedEpochMs >= ABANDONED_AFTER_MS
            }

        val averageProgressPercent =
            if (durationKnown.isEmpty()) {
                0
            } else {
                (
                    durationKnown
                        .map { it.progressFraction.toDouble() }
                        .average() * 100.0
                )
                    .roundToInt()
                    .coerceIn(0, 100)
            }

        val completionRatePercent =
            if (durationKnown.isEmpty()) {
                0
            } else {
                (
                    completedEntries.toDouble() /
                        durationKnown.size.toDouble() *
                        100.0
                )
                    .roundToInt()
                    .coerceIn(0, 100)
            }

        val uniqueWatchedTitles =
            meaningfulHistory
                .map { mediaIdentity(it.media) }
                .distinct()
                .size

        val myListTitles =
            myList
                .map(::mediaIdentity)
                .distinct()
                .size

        val uniqueSignalTitles = weightedSignals.size
        val confidencePercent =
            confidencePercent(
                uniqueSignalTitles = uniqueSignalTitles,
                uniqueWatchedTitles = uniqueWatchedTitles,
                completedEntries = completedEntries,
                myListTitles = myListTitles,
            )

        return UserDnaSnapshot(
            topGenres = topGenres,
            typeBreakdown = typeBreakdown,
            decadeBreakdown = decadeBreakdown,
            tasteTags = tasteTags(topGenres),
            behavior =
                UserDnaViewingBehavior(
                    uniqueTitles = uniqueSignalTitles,
                    watchedTitles = uniqueWatchedTitles,
                    historyEntries = meaningfulHistory.size,
                    completedEntries = completedEntries,
                    abandonedEntries = abandonedEntries,
                    myListTitles = myListTitles,
                    averageProgressPercent = averageProgressPercent,
                    completionRatePercent = completionRatePercent,
                ),
            confidencePercent = confidencePercent,
            readiness = readinessFor(confidencePercent),
        )
    }

    /**
     * Local DNA match for a candidate title.
     *
     * Returns null while the profile is too new to make a useful claim.
     * The score is deliberately conservative and can later be combined with
     * TMDB popularity, release freshness, parental controls or AI reranking.
     */
    fun matchPercent(
        media: MediaItem,
        dna: UserDnaSnapshot = build(),
    ): Int? {
        if (
            dna.readiness == UserDnaReadiness.STARTING ||
            dna.topGenres.isEmpty()
        ) {
            return null
        }

        val genreMap =
            dna.topGenres.associate {
                it.name.lowercase() to it.percent
            }

        val candidateGenres =
            media.genres
                .mapNotNull(::canonicalGenre)
                .map { it.lowercase() }
                .distinct()

        val genreMatch =
            candidateGenres
                .map { genreMap[it] ?: 0 }
                .sortedDescending()
                .take(2)
                .sum()
                .coerceAtMost(55)

        val type = canonicalType(media.type)
        val typeMatch =
            dna.typeBreakdown
                .firstOrNull {
                    it.name == type
                }
                ?.percent
                ?.let { (it * 0.22).roundToInt() }
                ?: 0

        val decade = releaseDecade(media.releaseInfo)
        val decadeMatch =
            dna.decadeBreakdown
                .firstOrNull {
                    it.name == decade
                }
                ?.percent
                ?.let { (it * 0.12).roundToInt() }
                ?: 0

        val raw =
            BASE_MATCH_SCORE +
                genreMatch +
                typeMatch +
                decadeMatch

        val confidenceScale =
            0.72 +
                (dna.confidencePercent / 100.0) * 0.28

        return (raw * confidenceScale)
            .roundToInt()
            .coerceIn(MIN_MATCH_SCORE, MAX_MATCH_SCORE)
    }

    private fun watchWeight(
        entry: LibraryPlaybackEntry,
        nowEpochMs: Long,
    ): Double {
        val progressWeight =
            when {
                entry.isCompleted -> 6.0
                entry.durationMs <= 0L -> 2.0
                entry.progressFraction >= 0.75f -> 5.0
                entry.progressFraction >= 0.50f -> 4.0
                entry.progressFraction >= 0.20f -> 3.0
                entry.progressFraction >= 0.05f -> 2.0
                else -> 1.0
            }

        val ageMs =
            (nowEpochMs - entry.lastWatchedEpochMs)
                .coerceAtLeast(0L)

        val recencyMultiplier =
            when {
                entry.lastWatchedEpochMs <= 0L -> 1.0
                ageMs <= 7L * DAY_MS -> 1.25
                ageMs <= 30L * DAY_MS -> 1.15
                ageMs <= 90L * DAY_MS -> 1.05
                ageMs <= 365L * DAY_MS -> 1.0
                else -> 0.90
            }

        return progressWeight * recencyMultiplier
    }

    private fun normalize(
        scores: Map<String, Double>,
        limit: Int,
    ): List<UserDnaAffinity> {
        val sorted =
            scores
                .filterValues { it > 0.0 }
                .entries
                .sortedByDescending { it.value }
                .take(limit)

        val total = sorted.sumOf { it.value }
        if (total <= 0.0) {
            return emptyList()
        }

        val result =
            sorted.map { entry ->
                UserDnaAffinity(
                    name = entry.key,
                    percent =
                        (
                            entry.value /
                                total *
                                100.0
                        )
                            .roundToInt()
                            .coerceIn(0, 100),
                    rawScore = entry.value,
                )
            }
                .toMutableList()

        // Keep the displayed breakdown mathematically tidy after rounding.
        if (result.isNotEmpty()) {
            val delta =
                100 - result.sumOf { it.percent }
            if (delta != 0) {
                result[0] =
                    result[0].copy(
                        percent =
                            (result[0].percent + delta)
                                .coerceIn(0, 100),
                    )
            }
        }

        return result
    }

    private fun confidencePercent(
        uniqueSignalTitles: Int,
        uniqueWatchedTitles: Int,
        completedEntries: Int,
        myListTitles: Int,
    ): Int {
        val score =
            uniqueSignalTitles * 3 +
                uniqueWatchedTitles * 2 +
                completedEntries.coerceAtMost(20) * 2 +
                myListTitles.coerceAtMost(10)

        return score.coerceIn(0, 100)
    }

    private fun readinessFor(
        confidencePercent: Int,
    ): UserDnaReadiness =
        when {
            confidencePercent < 20 ->
                UserDnaReadiness.STARTING
            confidencePercent < 45 ->
                UserDnaReadiness.LEARNING
            confidencePercent < 75 ->
                UserDnaReadiness.DEVELOPING
            else ->
                UserDnaReadiness.STRONG
        }

    private fun tasteTags(
        topGenres: List<UserDnaAffinity>,
    ): List<String> {
        val genres =
            topGenres
                .take(6)
                .map { it.name.lowercase() }
                .toSet()

        if (genres.isEmpty()) {
            return emptyList()
        }

        val tags = mutableListOf<String>()

        if (
            genres.any {
                it in setOf(
                    "thriller",
                    "mystery",
                    "crime",
                    "horror",
                )
            }
        ) {
            tags += "Suspenseful"
        }

        if ("drama" in genres) {
            tags += "Character-driven"
        }

        if (
            genres.any {
                it in setOf(
                    "science fiction",
                    "fantasy",
                )
            }
        ) {
            tags += "Imaginative"
        }

        if (
            genres.any {
                it in setOf(
                    "action",
                    "adventure",
                )
            }
        ) {
            tags += "High-energy"
        }

        if ("comedy" in genres) {
            tags += "Lighthearted"
        }

        if ("romance" in genres) {
            tags += "Romantic"
        }

        if (
            genres.any {
                it in setOf(
                    "documentary",
                    "history",
                )
            }
        ) {
            tags += "Curious"
        }

        if (
            genres.any {
                it in setOf(
                    "family",
                    "animation",
                )
            }
        ) {
            tags += "Feel-good"
        }

        return tags.distinct().take(3)
    }

    private fun canonicalGenre(
        raw: String,
    ): String? {
        val clean =
            raw.trim()
                .replace("_", " ")
                .replace(Regex("\\s+"), " ")
                .lowercase()

        if (clean.isBlank()) {
            return null
        }

        return when (clean) {
            "sci fi",
            "sci-fi",
            "science-fiction",
            "science fiction" -> "Science Fiction"
            "action & adventure",
            "action and adventure" -> "Action"
            "war & politics",
            "war and politics" -> "War"
            else ->
                clean
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase()
                            } else {
                                it.toString()
                            }
                        }
                    }
        }
    }

    private fun canonicalType(
        raw: String,
    ): String? {
        val clean = raw.trim().lowercase()
        if (clean.isBlank()) {
            return null
        }

        return when (clean) {
            "movie", "film" -> "Movies"
            "series", "tv", "show", "tvshow", "tv_show" -> "Series"
            "anime" -> "Anime"
            else ->
                clean.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase()
                    } else {
                        it.toString()
                    }
                }
        }
    }

    private fun releaseDecade(
        releaseInfo: String?,
    ): String? {
        val year =
            YEAR_REGEX
                .find(releaseInfo.orEmpty())
                ?.value
                ?.toIntOrNull()
                ?: return null

        if (year !in 1900..2100) {
            return null
        }

        val decade = year / 10 * 10
        return "${decade}s"
    }

    private fun mediaIdentity(
        media: MediaItem,
    ): String =
        "${media.type.trim().lowercase()}:${media.id.trim()}"

    private fun chooseRicherMedia(
        current: MediaItem,
        candidate: MediaItem,
    ): MediaItem =
        if (mediaRichness(candidate) > mediaRichness(current)) {
            candidate
        } else {
            current
        }

    private fun mediaRichness(
        media: MediaItem,
    ): Int =
        media.genres.size * 5 +
            if (!media.releaseInfo.isNullOrBlank()) 3 else 0 +
            if (!media.description.isNullOrBlank()) 2 else 0 +
            if (!media.poster.isNullOrBlank()) 1 else 0

    private data class MutableMediaSignal(
        var media: MediaItem,
        var historyWeight: Double = 0.0,
        var myListWeight: Double = 0.0,
        var historyEntries: Int = 0,
        var lastInteractionEpochMs: Long = 0L,
    )

    private data class WeightedMediaSignal(
        val media: MediaItem,
        val weight: Double,
    )

    private companion object {
        const val MIN_MEANINGFUL_POSITION_MS = 5_000L
        const val MY_LIST_WEIGHT = 3.0
        const val MAX_HISTORY_WEIGHT_PER_TITLE = 18.0
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val ABANDONED_AFTER_MS = 7L * DAY_MS
        const val ABANDONED_PROGRESS_THRESHOLD = 0.15f
        const val BASE_MATCH_SCORE = 28
        const val MIN_MATCH_SCORE = 35
        const val MAX_MATCH_SCORE = 98

        val YEAR_REGEX = Regex("(?:19|20)\\d{2}")
    }
}

data class UserDnaSnapshot(
    val topGenres: List<UserDnaAffinity>,
    val typeBreakdown: List<UserDnaAffinity>,
    val decadeBreakdown: List<UserDnaAffinity>,
    val tasteTags: List<String>,
    val behavior: UserDnaViewingBehavior,
    val confidencePercent: Int,
    val readiness: UserDnaReadiness,
) {
    val hasUsefulData: Boolean
        get() =
            readiness != UserDnaReadiness.STARTING &&
                topGenres.isNotEmpty()
}

data class UserDnaAffinity(
    val name: String,
    val percent: Int,
    val rawScore: Double,
)

data class UserDnaViewingBehavior(
    val uniqueTitles: Int,
    val watchedTitles: Int,
    val historyEntries: Int,
    val completedEntries: Int,
    val abandonedEntries: Int,
    val myListTitles: Int,
    val averageProgressPercent: Int,
    val completionRatePercent: Int,
)

enum class UserDnaReadiness {
    STARTING,
    LEARNING,
    DEVELOPING,
    STRONG,
}
