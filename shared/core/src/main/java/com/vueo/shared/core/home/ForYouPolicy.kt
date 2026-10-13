package com.vueo.shared.core.home

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryViewingPolicy
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** A ranking score, not a claimed probability of liking a title. */
object ForYouPolicy {
    data class Seed(val media: MediaItem, val weight: Double, val lastWatched: Long)
    data class RelatedBatch(val seed: Seed, val items: List<MediaItem>)
    private const val DAY_MS = 86_400_000.0

    fun identity(item: MediaItem): String = "${type(item)}:${item.id}"

    fun seeds(history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>, now: Long = System.currentTimeMillis()): List<Seed> {
        val values = linkedMapOf<String, Seed>()
        history.filter(LibraryViewingPolicy::isMeaningfullyWatched).forEach { entry ->
            val key = identity(entry.media)
            val progress = when {
                entry.isCompleted -> 6.0
                entry.durationMs <= 0 -> 2.0
                else -> 2.0 + 4.0 * entry.progressFraction.coerceIn(0f, 1f)
            }
            val decay = exp(-ln(2.0) * (now - entry.lastWatchedEpochMs).coerceAtLeast(0L) / (90.0 * DAY_MS))
            val weight = progress * decay.coerceAtLeast(0.15)
            val old = values[key]
            values[key] = Seed(richer(old?.media, entry.media), maxOf(old?.weight ?: 0.0, weight),
                maxOf(old?.lastWatched ?: 0L, entry.lastWatchedEpochMs))
        }
        favorites.forEach { media ->
            val key = identity(media)
            val old = values[key]
            values[key] = Seed(richer(old?.media, media), ((old?.weight ?: 0.0) + 3.0).coerceAtMost(8.0), old?.lastWatched ?: 0L)
        }
        val ranked = values.values.sortedWith(compareByDescending<Seed> { it.weight }.thenByDescending { it.lastWatched }.thenBy { identity(it.media) })
        // Always retain the latest watched seed, so Because You Watch reuses its result.
        val latest = values.values.filter { it.lastWatched > 0L }.maxByOrNull { it.lastWatched }
        return (listOfNotNull(latest) + ranked).distinctBy { identity(it.media) }.take(12)
    }

    fun rank(
        seeds: List<Seed>, batches: List<RelatedBatch>, catalog: List<MediaItem>,
        history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>, limit: Int = 12,
        checkActive: () -> Unit = {},
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val excluded = history.filter(LibraryViewingPolicy::isMeaningfullyWatched).flatMap { aliases(it.media) }.toSet() + favorites.flatMap(::aliases)
        val candidates = linkedMapOf<String, MediaItem>()
        val candidateAliases = mutableMapOf<String, String>()
        (batches.flatMap { it.items }.take(216) + catalog.take(360)).forEach {
            checkActive()
            val keys = aliases(it)
            if (keys.none { key -> key in excluded }) {
                val key = keys.firstNotNullOfOrNull { alias -> candidateAliases[alias] } ?: identity(it)
                candidates[key] = richer(candidates[key], it)
                keys.forEach { alias -> candidateAliases[alias] = key }
            }
        }
        val items = candidates.values.toList()
        val vectors = TextVectors((seeds.map { it.media } + items).distinctBy(::identity))
        val totalWeight = seeds.sumOf { it.weight }.coerceAtLeast(1.0)
        val strongestWeight = seeds.maxOfOrNull { it.weight }?.coerceAtLeast(1.0) ?: 1.0
        val genreProfile = mutableMapOf<String, Double>()
        val typeProfile = mutableMapOf<String, Double>()
        seeds.forEach { seed ->
            genres(seed.media).forEach { genreProfile[it] = (genreProfile[it] ?: 0.0) + seed.weight }
            typeProfile[type(seed.media)] = (typeProfile[type(seed.media)] ?: 0.0) + seed.weight
        }
        val genrePeak = genreProfile.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val mix = (seeds.size / 8.0).coerceIn(0.0, 1.0)
        data class Scored(val media: MediaItem, val score: Double)
        val ranked = items.mapIndexedNotNull { index, candidate ->
            checkActive()
            val matches = seeds.mapNotNull { seed ->
                val content = similarity(candidate, seed.media, vectors)
                val sourceRank = batches.firstOrNull { identity(it.seed.media) == identity(seed.media) }
                    ?.items?.indexOfFirst { identity(it) == identity(candidate) }?.takeIf { it >= 0 }
                val related = sourceRank?.let { 1.0 / (1.0 + it / 6.0) }
                if (content == null && related == null) return@mapNotNull null
                val pair = combine(listOf(content to 0.75, related to 0.25))
                pair * (seed.weight / strongestWeight)
            }.sortedDescending()
            val personal = matches.firstOrNull()?.let { best -> best * 0.7 + matches.take(3).average() * 0.3 }
            val genreAffinity = genres(candidate).map { (genreProfile[it] ?: 0.0) / genrePeak }.sortedDescending().take(2)
            val taste = if (seeds.isEmpty()) null else combine(listOf(
                genreAffinity.takeIf { it.isNotEmpty() }?.average() to 0.8,
                ((typeProfile[type(candidate)] ?: 0.0) / totalWeight) to 0.2,
            ))
            // Discovery stays near a known interest; strong profiles do not fill with unrelated popular titles.
            if (seeds.size >= 3 && (personal ?: 0.0) <= 0.05 && genreAffinity.none { it > 0.0 }) return@mapIndexedNotNull null
            val rating = candidate.recommendationRating ?: candidate.tmdbRating ?: candidate.imdbRating
            val votes = candidate.recommendationVoteCount
            val quality = if (rating != null && rating.isFinite() && votes != null && votes > 0) {
                ((rating.coerceIn(0.0, 10.0) * votes + 6.8 * 100.0) / (votes + 100.0)) / 10.0
            } else null
            val exploration = if (seeds.isEmpty()) null else 1.0 - (genreAffinity.firstOrNull() ?: 0.0)
            val general = candidate.popularity?.takeIf { it.isFinite() && it > 0 }?.let { ln(1.0 + it) / 8.0 }?.coerceIn(0.0, 1.0)
                ?: 1.0 / (1.0 + index / 24.0)
            val score = combine(listOf(personal to (0.55 * mix), taste to (0.20 * mix),
                quality to 0.15, exploration to 0.10, general to (0.75 * (1.0 - mix))))
            Scored(candidate, score)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { identity(it.media) }).toMutableList()
        val selected = mutableListOf<MediaItem>()
        while (ranked.isNotEmpty() && selected.size < limit) {
            checkActive()
            val best = ranked.indices.maxByOrNull { i ->
                ranked[i].score - 0.08 * (selected.maxOfOrNull { similarity(ranked[i].media, it, vectors) ?: 0.0 } ?: 0.0)
            } ?: break
            selected += ranked.removeAt(best).media
        }
        return selected
    }

    private fun combine(parts: List<Pair<Double?, Double>>): Double {
        val available = parts.filter { it.first != null && it.second > 0.0 }
        val weight = available.sumOf { it.second }
        return if (weight == 0.0) 0.0 else available.sumOf { it.first!! * it.second } / weight
    }
    private fun similarity(a: MediaItem, b: MediaItem, vectors: TextVectors): Double? {
        val ag = genres(a); val bg = genres(b)
        val ap = people(a); val bp = people(b)
        val text = vectors.cosine(a, b)
        val genre = if (ag.isNotEmpty() && bg.isNotEmpty()) overlap(ag, bg) else null
        val person = if (ap.isNotEmpty() && bp.isNotEmpty()) overlap(ap, bp) else null
        if (text == null && genre == null && person == null) return null
        return combine(listOf(text to 0.55, genre to 0.30, person to 0.15))
    }
    private fun overlap(a: Set<String>, b: Set<String>): Double = (a intersect b).size.toDouble() / (a + b).size.coerceAtLeast(1)
    private fun genres(item: MediaItem) = item.genres.flatMap { raw ->
        raw.lowercase().split('&').map { it.replace('-', ' ').replace(Regex("\\s+"), " ").trim() }
    }.map { when (it) {
        "sci fi", "scifi" -> "science fiction"
        "reality tv" -> "reality"
        "kids", "children" -> "family"
        else -> it
    } }.filter { it.isNotBlank() }.toSet()
    private fun people(item: MediaItem) = (item.cast.take(8).map { it.name } + item.directors + item.creators + item.writers).map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    private fun type(item: MediaItem) = if (item.type.lowercase() in setOf("series", "tv", "show")) "series" else "movie"
    private fun aliases(item: MediaItem): Set<String> = buildSet {
        add(identity(item))
        // Exact title + known release year + media type can reconcile TMDB/IMDb IDs.
        // Missing years never use title-only matching; remakes retain separate identities.
        val year = Regex("\\b(?:19|20)\\d{2}\\b").find(item.releaseInfo.orEmpty())?.value
        if (year != null) listOfNotNull(item.name, item.originalTitle).forEach { title ->
            val name = title.trim().lowercase().replace(Regex("\\s+"), " ")
            if (name.isNotBlank()) add("${type(item)}:title:$name:$year")
        }
    }
    private fun richer(a: MediaItem?, b: MediaItem): MediaItem {
        if (a == null) return b
        return a.copy(description = listOfNotNull(a.description, b.description).maxByOrNull { it.length },
            genres = (a.genres + b.genres).distinct(), cast = (a.cast + b.cast).distinctBy { it.name },
            directors = (a.directors + b.directors).distinct(), creators = (a.creators + b.creators).distinct(), writers = (a.writers + b.writers).distinct(),
            poster = a.poster ?: b.poster, background = a.background ?: b.background,
            recommendationRating = a.recommendationRating ?: b.recommendationRating,
            recommendationVoteCount = a.recommendationVoteCount ?: b.recommendationVoteCount,
            tmdbRating = a.tmdbRating ?: b.tmdbRating, imdbRating = a.imdbRating ?: b.imdbRating,
            popularity = a.popularity ?: b.popularity)
    }
    private class TextVectors(items: List<MediaItem>) {
        private val vectors: Map<String, Map<String, Double>>
        init {
            val docs = items.associate { identity(it) to tokens(it.description.orEmpty()) }
            val frequencies = mutableMapOf<String, Int>()
            docs.values.forEach { words -> words.toSet().forEach { frequencies[it] = (frequencies[it] ?: 0) + 1 } }
            vectors = docs.mapValues { (_, words) ->
                words.groupingBy { it }.eachCount().mapValues { (word, count) ->
                    (1.0 + ln(count.toDouble())) * (1.0 + ln((docs.size + 1.0) / ((frequencies[word] ?: 0) + 1.0)))
                }
            }
        }
        fun cosine(a: MediaItem, b: MediaItem): Double? {
            val av = vectors[identity(a)].orEmpty(); val bv = vectors[identity(b)].orEmpty()
            if (av.isEmpty() || bv.isEmpty()) return null
            val denominator = sqrt(av.values.sumOf { it * it }) * sqrt(bv.values.sumOf { it * it })
            return if (denominator == 0.0) null else av.entries.sumOf { (key, value) -> value * (bv[key] ?: 0.0) } / denominator
        }
        private fun tokens(text: String): List<String> = Regex("[\\p{L}]{3,}").findAll(text.take(6000).lowercase())
            .map { it.value }.filterNot { it in STOP_WORDS }.take(250).toList()
    }
    private val STOP_WORDS = "the and with that this from their they them who when where into after before through about while during whose which some more most have has had are was were will would can could his her its out but not one two new life world story series movie show follows follow must finds find becomes become also between together seorang sebuah yang dan dengan dalam untuk dari pada ketika mereka kisah cerita ini itu".split(' ').toSet()
}
