package com.vueo.shared.core.home

import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.media.AnimeMediaPolicy
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryViewingPolicy
import kotlin.math.ceil
import kotlin.math.ln

/** Shares the existing DNA model; no per-candidate profile rebuild or text vectors. */
object ForYouPolicy {
    private fun feedback(item: MediaItem, ratings: List<com.vueo.shared.core.storage.TitleRating>): Double {
        val own = genres(item)
        if (own.isEmpty()) return 0.0
        val signals = ratings.map { rating -> jaccard(own, genres(rating.media)) * rating.value }
        return (signals.sum() / kotlin.math.sqrt(signals.size.coerceAtLeast(1).toDouble())).coerceIn(-1.0, 1.0)
    }
    private fun rated(item: MediaItem, ratings: List<com.vueo.shared.core.storage.TitleRating>) =
        ratings.any { rating -> aliases(item).any(aliases(rating.media)::contains) }

    data class Seed(val media: MediaItem, val weight: Double, val lastWatched: Long)
    data class RelatedBatch(val seed: Seed, val items: List<MediaItem>)
    fun identity(item: MediaItem): String = "${type(item)}:${item.id}"

    fun profile(history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>,
        now: Long = System.currentTimeMillis(), checkActive: () -> Unit = {}): UserDnaSnapshot =
        UserDnaEngine.analyzeSnapshot(history, favorites, now, checkActive)

    fun seeds(history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>,
        now: Long = System.currentTimeMillis(), dna: UserDnaSnapshot = profile(history, favorites, now),
        checkActive: () -> Unit = {}, ratings: List<com.vueo.shared.core.storage.TitleRating> = emptyList()): List<Seed> {
        val values = linkedMapOf<String, Seed>()
        val favoriteKeys = favorites.flatMap(::aliases).toSet()
        history.forEach { entry ->
            checkActive()
            if (ratings.any { it.value < 0 && aliases(it.media).any(aliases(entry.media)::contains) }) return@forEach
            if (!LibraryViewingPolicy.isMeaningfullyWatched(entry)) return@forEach
            val key = identity(entry.media)
            val strength = when {
                entry.isCompleted -> 1.0
                entry.durationMs <= 0 -> .35
                else -> .35 + .65 * entry.progressFraction.coerceIn(0f, 1f)
            }
            val old = values[key]
            values[key] = Seed(richer(old?.media, entry.media), maxOf(old?.weight ?: 0.0, strength),
                maxOf(old?.lastWatched ?: 0L, entry.lastWatchedEpochMs))
        }
        favorites.forEach { media ->
            if (ratings.any { it.value < 0 && aliases(it.media).any(aliases(media)::contains) }) return@forEach
            checkActive()
            val key = identity(media)
            val old = values[key]
            values[key] = Seed(richer(old?.media, media), old?.weight ?: .20, old?.lastWatched ?: 0L)
        }
        ratings.filter { it.value > 0 }.forEach { rating ->
            val key = identity(rating.media)
            values[key] = Seed(richer(values[key]?.media, rating.media), 2.0, values[key]?.lastWatched ?: 0L)
        }
        // Resolve title/year aliases before selection; unsupported IDs never consume a request slot.
        val unique = linkedMapOf<String, Seed>()
        val seen = mutableMapOf<String, String>()
        values.values.sortedBy { identity(it.media) }.forEach { seed ->
            checkActive()
            if (!resolvable(seed.media)) return@forEach
            val keys = aliases(seed.media)
            val key = keys.firstNotNullOfOrNull { seen[it] } ?: identity(seed.media)
            val old = unique[key]
            unique[key] = Seed(richer(old?.media, seed.media), maxOf(old?.weight ?: 0.0, seed.weight),
                maxOf(old?.lastWatched ?: 0L, seed.lastWatched))
            keys.forEach { seen[it] = key }
        }
        val taste = Taste(dna)
        val candidates = unique.values.map { seed ->
            val favorite = aliases(seed.media).any(favoriteKeys::contains)
            // Recent viewing is at most five percent, never a dedicated slot.
            val ageDays = (now - seed.lastWatched).coerceAtLeast(0L) / 86_400_000.0
            seed.copy(weight = .55 * taste.affinity(seed.media) + .30 * seed.weight +
                (if (favorite) .03 else 0.0) + .35 * feedback(seed.media, ratings) + (if (seed.lastWatched > 0) .05 / (1 + ageDays / 30) else 0.0))
        }.toMutableList()
        val diversity = Diversity(candidates.map { it.media })
        val selected = mutableListOf<Seed>()
        while (candidates.isNotEmpty() && selected.size < 6) {
            checkActive()
            val next = candidates.map { seed -> seed to
                (seed.weight - .18 * selected.sumOf { diversity.similarity(seed.media, it.media) })
            }.maxWithOrNull(compareBy<Pair<Seed, Double>> { it.second }
                .thenByDescending { identity(it.first.media) })!!.first
            selected += next
            candidates.remove(next)
        }
        return selected
    }

    fun rank(seeds: List<Seed>, batches: List<RelatedBatch>, catalog: List<MediaItem>,
        history: List<LibraryPlaybackEntry>, favorites: List<MediaItem>, limit: Int = 12,
        checkActive: () -> Unit = {}, dna: UserDnaSnapshot = profile(history, favorites, checkActive = checkActive),
        ratings: List<com.vueo.shared.core.storage.TitleRating> = emptyList(),
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val excluded = history.filter(LibraryViewingPolicy::isMeaningfullyWatched).flatMap { aliases(it.media) }.toSet() + favorites.flatMap(::aliases)
        val candidates = linkedMapOf<String, MediaItem>()
        val candidateAliases = mutableMapOf<String, String>()
        (batches.flatMap { it.items }.take(108) + catalog.take(72)).forEach { item ->
            checkActive()
            val keys = aliases(item)
            if (keys.none(excluded::contains) && !rated(item, ratings)) {
                val key = keys.firstNotNullOfOrNull { candidateAliases[it] } ?: identity(item)
                candidates[key] = richer(candidates[key], item)
                keys.forEach { candidateAliases[it] = key }
            }
        }
        // Index source support once. Candidate loops never rescan remote result lists.
        val support = mutableMapOf<String, MutableMap<String, Double>>()
        batches.forEach { batch -> batch.items.take(18).forEachIndexed { index, media ->
            checkActive()
            val key = aliases(media).firstNotNullOfOrNull { candidateAliases[it] } ?: return@forEachIndexed
            val seedKey = identity(batch.seed.media)
            val ranks = support.getOrPut(key) { mutableMapOf() }
            ranks[seedKey] = maxOf(ranks[seedKey] ?: 0.0, 1.0 / (1 + index / 6.0))
        } }
        val taste = Taste(dna)
        val seedById = seeds.associateBy { identity(it.media) }
        val tokens = (seeds.map { it.media } + candidates.values).associate { identity(it) to words(it) }
        val activeSeedCount = batches.count { it.items.isNotEmpty() }.coerceAtLeast(1)
        data class Scored(val media: MediaItem, val score: Double, val owner: String?)
        val ranked = candidates.map { (key, media) ->
            checkActive()
            val ranks = support[key].orEmpty()
            val source = ranks.entries.sumOf { (seed, rank) -> rank * (seedById[seed]?.weight ?: .5) } /
                seeds.sumOf { it.weight }.coerceAtLeast(1.0)
            val topical = ranks.keys.maxOfOrNull { seed ->
                jaccard(tokens[identity(media)].orEmpty(), tokens[seed].orEmpty())
            } ?: seeds.maxOfOrNull { jaccard(tokens[identity(media)].orEmpty(), tokens[identity(it.media)].orEmpty()) } ?: 0.0
            val rating = media.recommendationRating ?: media.tmdbRating ?: media.imdbRating
            val votes = media.recommendationVoteCount
            val quality = if (rating != null && rating.isFinite() && votes != null && votes > 0)
                (rating.coerceIn(0.0, 10.0) * votes + 6.8 * 100) / (votes.toDouble() + 100) / 10 else .68
            val popularity = media.popularity?.takeIf { it.isFinite() && it > 0 }?.let { ln(1 + it) / 8 }?.coerceIn(0.0, 1.0) ?: 0.0
            val score = if (seeds.isEmpty()) .85 * quality + .15 * popularity else
                .45 * taste.affinity(media) + .25 * source + .15 * quality + .10 * topical + .05 * popularity
            val owner = ranks.keys.sortedWith(compareByDescending<String> { ranks.getValue(it) * (seedById[it]?.weight ?: .5) }.thenBy { it }).firstOrNull()
            Scored(media, score + .35 * feedback(media, ratings), owner)
        }.toMutableList()
        val diversity = Diversity(candidates.values.toList())
        val selected = mutableListOf<MediaItem>()
        val ownerCounts = mutableMapOf<String, Int>()
        val cap = if (activeSeedCount <= 1) limit else maxOf(3, ceil(minOf(limit, 12).toDouble() / activeSeedCount).toInt())
        while (ranked.isNotEmpty() && selected.size < limit) {
            checkActive()
            val next = ranked.filter { it.owner == null || (ownerCounts[it.owner] ?: 0) < cap }
                .map { candidate -> candidate to (candidate.score -
                    .06 * (selected.maxOfOrNull { diversity.similarity(candidate.media, it) } ?: 0.0) -
                    .06 * (candidate.owner?.let { ownerCounts[it] } ?: 0)) }
                .maxWithOrNull(compareBy<Pair<Scored, Double>> { it.second }
                    .thenByDescending { identity(it.first.media) })?.first ?: break
            selected += next.media
            next.owner?.let { ownerCounts[it] = (ownerCounts[it] ?: 0) + 1 }
            ranked.remove(next)
        }
        return selected
    }

    private class Taste(dna: UserDnaSnapshot) {
        private val genre = dna.topGenres.associate { canonical(it.name) to it.rawScore }
        private val genrePeak = genre.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        private val types = dna.typeBreakdown.associate { it.name to it.rawScore }
        private val typePeak = types.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        private val decades = dna.decadeBreakdown.associate { it.name to it.rawScore }
        private val decadePeak = decades.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        fun affinity(media: MediaItem): Double {
            val genres = genres(media).map { (genre[it] ?: 0.0) / genrePeak }.sortedDescending().take(2)
            val type = if (media.type.equals("anime", true)) "Anime" else if (type(media) == "series") "Series" else "Movies"
            val year = Regex("(?:19|20)\\d{2}").find(media.releaseInfo.orEmpty())?.value?.toIntOrNull()
            val decade = year?.let { "${it / 10 * 10}s" }
            return .75 * (genres.takeIf { it.isNotEmpty() }?.average() ?: 0.0) +
                .20 * ((types[type] ?: 0.0) / typePeak) + .05 * ((decades[decade] ?: 0.0) / decadePeak)
        }
    }
    private fun canonical(raw: String) = when (val value = raw.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")) {
        "sci fi", "scifi" -> "science fiction"
        "action & adventure", "action and adventure" -> "action"
        "war & politics", "war and politics" -> "war"
        else -> value
    }
    private fun genres(media: MediaItem) = media.genres.map(::canonical).filter { it.isNotBlank() }.toSet()
    private class Diversity(items: List<MediaItem>) {
        private data class Features(val genres: Set<String>, val type: String, val anime: Boolean)
        private val features = items.associate { identity(it) to Features(genres(it), type(it), AnimeMediaPolicy.isAnime(it)) }
        fun similarity(a: MediaItem, b: MediaItem): Double {
            val x = features.getValue(identity(a)); val y = features.getValue(identity(b))
            return .7 * jaccard(x.genres, y.genres) + (if (x.type == y.type) .15 else 0.0) +
                (if (x.anime == y.anime) .15 else 0.0)
        }
    }
    private fun jaccard(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else (a intersect b).size.toDouble() / (a + b).size
    private fun resolvable(media: MediaItem): Boolean =
        (MediaTypePolicy.isSeries(media.type) || media.type.lowercase() in setOf("movie", "movies", "film", "anime")) &&
            (Regex("tt[0-9]+").matches(media.id) || Regex("tmdb:[0-9]+").matches(media.id))
    private fun words(media: MediaItem): Set<String> = Regex("[\\p{L}]{3,}")
        .findAll(media.description.orEmpty().take(2500).lowercase()).map { it.value }.filterNot { it in STOP_WORDS }.take(100).toSet()
    private fun type(item: MediaItem) = if (MediaTypePolicy.isSeries(item.type) || item.type.equals("anime", true)) "series" else "movie"
    internal fun aliases(item: MediaItem): Set<String> = buildSet {
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
            originalLanguage = a.originalLanguage ?: b.originalLanguage,
            countries = (a.countries + b.countries).distinct(),
            recommendationRating = a.recommendationRating ?: b.recommendationRating,
            recommendationVoteCount = a.recommendationVoteCount ?: b.recommendationVoteCount,
            tmdbRating = a.tmdbRating ?: b.tmdbRating, imdbRating = a.imdbRating ?: b.imdbRating,
            popularity = a.popularity ?: b.popularity)
    }
    private val STOP_WORDS = "the and with that this from their they them who when where into after before about while during which some more most have has had are was were will would can could his her its but not one two new life world story series movie show follows follow must finds find becomes become also yang dan dengan dalam untuk dari pada ketika mereka kisah cerita ini itu".split(' ').toSet()
}
