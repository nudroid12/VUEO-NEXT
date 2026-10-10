package com.vueo.shared.core.home

import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.MediaExtension
import kotlin.random.Random
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry

object HomeCatalogPolicy {
    private val sessionSeed = Random.nextLong()

    /** Stable addon order, preserving each manifest's original catalog order. */
    fun defaultOrder(extensions: List<MediaExtension>): List<String> = extensions
        .sortedWith(compareBy<MediaExtension> { it.descriptor.baseUrl.trim() }.thenBy { it.descriptor.id })
        .flatMap { extension -> extension.descriptor.catalogs.filter { it.shouldShowOnHome }.map {
            "${extension.descriptor.id}:${it.type}:${it.id}"
        } }.distinct()

    fun orderRows(
        rows: List<CatalogRow>,
        catalogOrder: List<String>,
        disabledCatalogKeys: Set<String> = emptySet(),
        defaultCatalogOrder: List<String> = emptyList(),
        random: Boolean = false,
    ): List<CatalogRow> {
        val enabledRows = if (disabledCatalogKeys.isEmpty()) rows else rows.filterNot { it.id in disabledCatalogKeys }
        if (random) return enabledRows.sortedWith(
            compareBy<CatalogRow> { Random(sessionSeed xor it.id.hashCode().toLong()).nextLong() }.thenBy { it.id },
        )
        val order = (catalogOrder + defaultCatalogOrder).distinct()
        if (order.isEmpty()) return enabledRows
        val index = order.withIndex().associate { it.value to it.index }
        return enabledRows.sortedWith(compareBy<CatalogRow> { index[it.id] ?: Int.MAX_VALUE }.thenBy { it.id })
    }
}

object HomeRecommendationPolicy {
    private const val FOR_YOU_MIN_MATCH_PERCENT = 55
    private const val DEFAULT_LIMIT = 12

    fun build(
        catalogRows: List<CatalogRow>,
        watchHistory: List<LibraryPlaybackEntry>,
        dnaEngine: UserDnaEngine,
        personalizationEnabled: Boolean,
        limit: Int = DEFAULT_LIMIT,
        dnaSnapshot: UserDnaSnapshot? = null,
        checkActive: () -> Unit = {},
    ): HomeRecommendationSections {
        checkActive()
        if (!personalizationEnabled || limit <= 0) return HomeRecommendationSections()
        val catalogCandidates = catalogRows.asSequence().flatMap { it.items.asSequence() }.onEach { checkActive() }.distinctBy(::mediaKey).toList()
        val meaningfulHistory = watchHistory.filter { checkActive(); isMeaningfullyWatched(it) }
        val watchedTitleKeys = meaningfulHistory.asSequence().map { mediaKey(it.media) }.toSet()
        val dna = dnaSnapshot ?: dnaEngine.build()
        checkActive()
        val ranked = if (!dna.hasUsefulData) emptyList() else catalogCandidates.asSequence()
            .filterNot { mediaKey(it) in watchedTitleKeys }
            .mapNotNull { candidate ->
                checkActive()
                dnaEngine.matchPercent(media = candidate, dna = dna)
                    ?.takeIf { it >= FOR_YOU_MIN_MATCH_PERCENT }
                    ?.let { score -> candidate to score }
            }
            .sortedByDescending { it.second }.toList()
        val forYou = diversify(ranked, limit, checkActive)
        val seed = latestMeaningfulSeed(meaningfulHistory)
        val because = seed?.let { watched ->
            val seedGenres = watched.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            val related = CatalogDiscoveryCache.related(watched, limit = 30, checkActive = checkActive)
            val fallback = catalogCandidates.filter { candidate -> checkActive(); candidate.type == watched.type && candidate.genres.any { it.trim().lowercase() in seedGenres } }
            val forYouKeys = forYou.asSequence().map(::mediaKey).toSet()
            val seedKey = mediaKey(watched)
            (related + fallback).asSequence().distinctBy(::mediaKey)
                .filterNot { candidate -> val key=mediaKey(candidate); key == seedKey || key in watchedTitleKeys || key in forYouKeys }
                .take(limit).toList()
        }.orEmpty()
        return HomeRecommendationSections(forYou, seed, because)
    }
    /** Ignore previews; known-duration titles need both time and progress. */
    internal fun isMeaningfullyWatched(entry: LibraryPlaybackEntry): Boolean = when {
        entry.isCompleted -> true
        entry.durationMs > 0L -> entry.positionMs >= 300_000L && entry.progressFraction >= .20f
        else -> entry.positionMs >= 600_000L
    }

    internal fun latestMeaningfulSeed(history: List<LibraryPlaybackEntry>): MediaItem? =
        history.asSequence().filter(::isMeaningfullyWatched)
            .maxByOrNull { it.lastWatchedEpochMs }?.media

    /** Soft diversity within a bounded relevant pool; never fill with low matches. */
    internal fun diversify(
        ranked: List<Pair<MediaItem, Int>>,
        limit: Int,
        checkActive: () -> Unit = {},
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val pool = ranked.asSequence().filter { it.second >= FOR_YOU_MIN_MATCH_PERCENT }
            .distinctBy { mediaKey(it.first) }.sortedByDescending { it.second }
            .take(limit.coerceAtMost(DEFAULT_LIMIT) * 6).toMutableList()
        val chosen = mutableListOf<MediaItem>()
        val chosenGenres = mutableListOf<Set<String>>()
        val families = mutableMapOf<String, Int>()
        while (pool.isNotEmpty() && chosen.size < limit) {
            checkActive()
            var bestIndex = 0
            var bestScore = Double.NEGATIVE_INFINITY
            pool.forEachIndexed { index, (item, score) ->
                checkActive()
                val genres = item.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                val similarity = chosenGenres.maxOfOrNull { other ->
                    val union = (genres + other).size
                    if (union == 0) 0.0 else (genres intersect other).size.toDouble() / union
                } ?: 0.0
                val family = explicitTitleFamily(item)
                val repeatFamily = family?.let { families[it] } ?: 0
                val adjusted = score - similarity * 8.0 - repeatFamily * 8.0
                if (adjusted > bestScore) {
                    bestScore = adjusted
                    bestIndex = index
                }
            }
            val item = pool.removeAt(bestIndex).first
            chosen += item
            chosenGenres += item.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            explicitTitleFamily(item)?.let { families[it] = (families[it] ?: 0) + 1 }
        }
        return chosen
    }

    // Only explicit multi-word "Family: Subtitle" names share a family key.
    // Do not guess collections from generic words or invent missing metadata.
    private fun explicitTitleFamily(item: MediaItem): String? {
        if (':' !in item.name) return null
        val prefix = item.name.substringBefore(':').trim().lowercase()
        return prefix.takeIf { it.split(Regex("\\s+")).size >= 2 && it.length >= 6 }
    }

    private fun mediaKey(item: MediaItem) = "${item.type}:${item.id}"
}

data class HomeRecommendationSections(
    val forYou: List<MediaItem> = emptyList(),
    val becauseYouWatchedSeed: MediaItem? = null,
    val becauseYouWatched: List<MediaItem> = emptyList(),
)
