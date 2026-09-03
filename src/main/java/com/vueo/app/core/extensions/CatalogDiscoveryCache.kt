package com.vueo.app.core.extensions

import android.content.Context
import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.sqrt

object CatalogDiscoveryCache {
    private const val HOME_TTL_MS =
        10 * 60_000L

    private const val DISK_HOME_MAX_AGE_MS =
        48 * 60 * 60_000L

    private const val SEARCH_TTL_MS =
        5 * 60_000L

    private const val MAX_SEARCH_ENTRIES =
        20

    private const val MAX_DISK_ROWS =
        12

    private const val MAX_DISK_ITEMS_PER_ROW =
        50

    private const val RELATED_MAX_CANDIDATES =
        600

    private const val PREFS_NAME =
        "vueo_catalog_cache"

    private const val KEY_HOME =
        "home_v1"

    private var homeRows:
        List<CatalogRow> =
        emptyList()

    private var homeUpdatedAt:
        Long = 0L

    private val searches =
        object :
            LinkedHashMap<
                String,
                SearchEntry
            >(
                24,
                0.75f,
                true,
            ) {
            override fun removeEldestEntry(
                eldest:
                    MutableMap.MutableEntry<
                        String,
                        SearchEntry
                    >?,
            ): Boolean =
                size >
                    MAX_SEARCH_ENTRIES
        }

    @Synchronized
    fun home(
        allowStale: Boolean = false,
    ): List<CatalogRow>? {
        if (homeRows.isEmpty()) {
            return null
        }

        val age =
            System.currentTimeMillis() -
                homeUpdatedAt

        if (
            !allowStale &&
            age > HOME_TTL_MS
        ) {
            return null
        }

        return homeRows
    }

    @Synchronized
    fun putHome(
        rows: List<CatalogRow>,
    ) {
        if (rows.isEmpty()) {
            return
        }

        homeRows = rows
        homeUpdatedAt =
            System.currentTimeMillis()
    }

    suspend fun restoreHome(
        context: Context,
    ): List<CatalogRow> =
        withContext(
            Dispatchers.IO
        ) {
            val prefs =
                context
                    .applicationContext
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )

            val raw =
                prefs.getString(
                    KEY_HOME,
                    null,
                )
                    ?: return@withContext emptyList<CatalogRow>()

            val decoded =
                runCatching {
                    val root =
                        JSONObject(raw)

                    val updatedAt =
                        root.optLong(
                            "updatedAt",
                            0L,
                        )

                    val age =
                        System
                            .currentTimeMillis() -
                            updatedAt

                    if (
                        updatedAt <= 0L ||
                        age >
                            DISK_HOME_MAX_AGE_MS
                    ) {
                        prefs.edit()
                            .remove(
                                KEY_HOME
                            )
                            .apply()

                        return@runCatching emptyList<CatalogRow>()
                    }

                    val rows =
                        root.optJSONArray(
                            "rows"
                        ).toCatalogRows()

                    if (
                        rows.isNotEmpty()
                    ) {
                        synchronized(this@CatalogDiscoveryCache) {
                            homeRows =
                                rows

                            homeUpdatedAt =
                                updatedAt
                        }
                    }

                    rows
                }.getOrElse {
                    prefs.edit()
                        .remove(KEY_HOME)
                        .apply()


                    emptyList<CatalogRow>()
                }

            decoded
        }

    suspend fun persistHome(
        context: Context,
        rows: List<CatalogRow>,
    ) {
        if (rows.isEmpty()) {
            return
        }

        val snapshot =
            rows
                .take(
                    MAX_DISK_ROWS
                )
                .map {
                    row ->

                    row.copy(
                        items =
                            row.items.take(
                                MAX_DISK_ITEMS_PER_ROW
                            )
                    )
                }

        withContext(
            Dispatchers.IO
        ) {
            val root =
                JSONObject()
                    .put(
                        "updatedAt",
                        System
                            .currentTimeMillis(),
                    )
                    .put(
                        "rows",
                        snapshot.toJson(),
                    )

            context
                .applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .putString(
                    KEY_HOME,
                    root.toString(),
                )
                .apply()
        }
    }

    @Synchronized
    fun invalidateHomeMemory() {
        homeUpdatedAt = 0L
    }

    @Synchronized
    fun clearMemory() {
        homeRows =
            emptyList()

        homeUpdatedAt = 0L
        searches.clear()
    }

    suspend fun clearAll(
        context: Context,
    ) {
        clearMemory()

        withContext(
            Dispatchers.IO
        ) {
            context
                .applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .clear()
                .apply()
        }
    }

    @Synchronized
    fun search(
        query: String,
    ): List<MediaItem>? {
        val key =
            normalizeQuery(query)

        val entry =
            searches[key]
                ?: return null

        val age =
            System.currentTimeMillis() -
                entry.updatedAt

        if (
            age > SEARCH_TTL_MS
        ) {
            searches.remove(key)
            return null
        }

        return entry.items
    }

    @Synchronized
    fun putSearch(
        query: String,
        items: List<MediaItem>,
    ) {
        val key =
            normalizeQuery(query)

        if (
            key.isBlank() ||
            items.isEmpty()
        ) {
            return
        }

        searches[key] =
            SearchEntry(
                items = items,
                updatedAt =
                    System
                        .currentTimeMillis(),
            )
    }

    @Synchronized
    fun searchLocal(
        query: String,
        limit: Int = 60,
    ): List<MediaItem> {
        val needle =
            normalizeQuery(query)

        if (
            needle.length < 2
        ) {
            return emptyList()
        }

        val titleNeedle =
            needle
                .replace(
                    Regex(
                        """\b(19|20)\d{2}\b"""
                    ),
                    " ",
                )
                .trim()
                .replace(
                    Regex(
                        """\s+"""
                    ),
                    " ",
                )

        val tokens =
            titleNeedle
                .split(' ')
                .filter {
                    it.isNotBlank()
                }

        return allCachedItems()
            .asSequence()
            .filter { item ->
                val haystack =
                    searchableText(item)
                val title =
                    normalizeQuery(
                        item.name
                    )

                if (tokens.size <= 1) {
                    haystack.contains(
                        titleNeedle
                    )
                } else {
                    title.contains(
                        titleNeedle
                    ) ||
                        tokens.all {
                            token ->
                            title
                                .split(' ')
                                .any {
                                    titleToken ->
                                    titleToken == token ||
                                        titleToken
                                            .startsWith(
                                                token
                                            )
                                }
                        }
                }
            }
            .groupBy {
                localSearchIdentityKey(
                    it
                )
            }
            .values
            .mapNotNull {
                duplicates ->
                duplicates.maxByOrNull {
                    item ->
                    localSearchScore(
                        item = item,
                        query = titleNeedle,
                    )
                }?.let {
                    best ->
                    best.copy(
                        catalogSources =
                            (
                                best.catalogSources +
                                    duplicates
                                        .flatMap {
                                            item ->
                                            item.catalogSources
                                        }
                            )
                                .map {
                                    it.trim()
                                }
                                .filter {
                                    it.isNotBlank()
                                }
                                .distinctBy {
                                    it.lowercase()
                                }
                    )
                }
            }
            .sortedByDescending { item ->
                localSearchScore(
                    item = item,
                    query = titleNeedle,
                )
            }
            .take(limit)
    }

    @Synchronized
    fun related(
        item: MediaItem,
        limit: Int = 16,
    ): List<MediaItem> {
        if (limit <= 0) {
            return emptyList()
        }

        val targetType =
            relatedCanonicalType(
                item.type
            )
        val cached =
            allCachedItems()
        if (cached.isEmpty()) {
            return emptyList()
        }

        val targetIdentity =
            relatedIdentityKey(
                item
            )
        val targetTitle =
            relatedCanonicalTitle(
                item.name,
                targetType,
            )
        val targetYear =
            relatedYear(item)

        val duplicateGroups =
            cached
                .asSequence()
                .filter { candidate ->
                    relatedCanonicalType(
                        candidate.type
                    ) == targetType &&
                        !relatedIsCurrentTitle(
                            target = item,
                            targetIdentity =
                                targetIdentity,
                            targetTitle =
                                targetTitle,
                            targetYear =
                                targetYear,
                            candidate =
                                candidate,
                        )
                }
                .groupBy {
                    relatedIdentityKey(it)
                }

        if (duplicateGroups.isEmpty()) {
            return emptyList()
        }

        val quickTargetGenres =
            item.genres
                .map(::relatedNormalizeGenre)
                .filter { it.isNotBlank() }
                .toSet()
        val targetFranchise =
            relatedFranchiseStem(
                item.name
            )
        val candidates =
            duplicateGroups.values
                .mapNotNull {
                    relatedMergeDuplicates(it)
                }
                .sortedByDescending { candidate ->
                    val candidateGenres =
                        candidate.genres
                            .map(
                                ::relatedNormalizeGenre
                            )
                            .filter {
                                it.isNotBlank()
                            }
                            .toSet()
                    val shared =
                        quickTargetGenres intersect
                            candidateGenres
                    val specificShared =
                        shared.count {
                            it !in
                                RELATED_GENERIC_GENRES
                        }
                    val candidateFranchise =
                        relatedFranchiseStem(
                            candidate.name
                        )
                    shared.size * 100 +
                        specificShared * 35 +
                        if (
                            targetFranchise.isNotBlank() &&
                            targetFranchise ==
                                candidateFranchise
                        ) {
                            70
                        } else {
                            0
                        } +
                        relatedMetadataRichness(
                            candidate
                        )
                }
                .take(
                    RELATED_MAX_CANDIDATES
                )
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val documents =
            listOf(item) + candidates
        val tokenSets =
            documents.map {
                relatedDocumentTokens(it)
            }
        val documentFrequency =
            buildMap<String, Int> {
                tokenSets.forEach { tokens ->
                    tokens.forEach { token ->
                        put(
                            token,
                            (get(token) ?: 0) + 1,
                        )
                    }
                }
            }
        val documentCount =
            documents.size
                .coerceAtLeast(1)

        val genreFrequency =
            buildMap<String, Int> {
                documents.forEach { media ->
                    media.genres
                        .map(::relatedNormalizeGenre)
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                        .forEach { genre ->
                            put(
                                genre,
                                (get(genre) ?: 0) + 1,
                            )
                        }
                }
            }

        val homePopularity =
            homeRows
                .asSequence()
                .flatMap {
                    it.items.asSequence()
                }
                .filter {
                    relatedCanonicalType(
                        it.type
                    ) == targetType
                }
                .groupingBy {
                    relatedIdentityKey(it)
                }
                .eachCount()
        val maxHomePopularity =
            homePopularity.values
                .maxOrNull()
                ?.coerceAtLeast(1)
                ?: 1

        val targetFeatures =
            relatedBuildFeatures(
                media = item,
                documentFrequency =
                    documentFrequency,
                documentCount =
                    documentCount,
                homePopularity =
                    homePopularity,
                maxHomePopularity =
                    maxHomePopularity,
            )
        val scored =
            candidates.mapNotNull { candidate ->
                val candidateFeatures =
                    relatedBuildFeatures(
                        media = candidate,
                        documentFrequency =
                            documentFrequency,
                        documentCount =
                            documentCount,
                        homePopularity =
                            homePopularity,
                        maxHomePopularity =
                            maxHomePopularity,
                    )
                val signals =
                    relatedSignals(
                        target = targetFeatures,
                        candidate =
                            candidateFeatures,
                        genreFrequency =
                            genreFrequency,
                        documentCount =
                            documentCount,
                    )

                if (!signals.passesRelevanceGate) {
                    return@mapNotNull null
                }

                RelatedCandidate(
                    features =
                        candidateFeatures,
                    score =
                        signals.weightedScore,
                    genreScore =
                        signals.genreScore,
                    topicScore =
                        signals.topicScore,
                )
            }
                .sortedWith(
                    compareByDescending<RelatedCandidate> {
                        it.score
                    }.thenByDescending {
                        relatedMetadataRichness(
                            it.features.item
                        )
                    }.thenBy {
                        it.features.item.name
                    }
                )

        if (scored.isEmpty()) {
            return emptyList()
        }

        return relatedDiversityRerank(
            scored = scored,
            limit = limit,
            documentCount =
                documentCount,
            genreFrequency =
                genreFrequency,
        )
    }

    private fun relatedBuildFeatures(
        media: MediaItem,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        homePopularity: Map<String, Int>,
        maxHomePopularity: Int,
    ): RelatedFeatures {
        val cast =
            media.cast
                .map {
                    normalizeQuery(it.name)
                }
                .filter { it.isNotBlank() }
                .toSet()
        val creators =
            (
                media.creators +
                    media.directors +
                    media.writers
            )
                .map(::normalizeQuery)
                .filter { it.isNotBlank() }
                .toSet()
        val companies =
            (
                media.networks.map {
                    it.name
                } +
                    media.productionCompanies
                        .map {
                            it.name
                        }
            )
                .map(::normalizeQuery)
                .filter { it.isNotBlank() }
                .toSet()
        val popularityAvailable =
            homePopularity.isNotEmpty() ||
                media.catalogSources.isNotEmpty()

        return RelatedFeatures(
            item = media,
            genres =
                media.genres
                    .map(::relatedNormalizeGenre)
                    .filter { it.isNotBlank() }
                    .toSet(),
            topic =
                relatedTopicWeights(
                    media = media,
                    documentFrequency =
                        documentFrequency,
                    documentCount =
                        documentCount,
                ),
            story =
                relatedStoryTermWeights(
                    media = media,
                    documentFrequency =
                        documentFrequency,
                    documentCount =
                        documentCount,
                ),
            cast = cast,
            creators = creators,
            companies = companies,
            year = relatedYear(media),
            quality =
                relatedQualityScore(media),
            popularity =
                if (popularityAvailable) {
                    relatedPopularityScore(
                        candidate = media,
                        homePopularity =
                            homePopularity,
                        maxHomePopularity =
                            maxHomePopularity,
                    )
                } else {
                    null
                },
            franchise =
                relatedFranchiseStem(
                    media.name
                ),
        )
    }

    private fun relatedSignals(
        target: RelatedFeatures,
        candidate: RelatedFeatures,
        genreFrequency: Map<String, Int>,
        documentCount: Int,
    ): RelatedSignals {
        val targetGenres = target.genres
        val candidateGenres = candidate.genres
        val sharedGenres =
            targetGenres intersect
                candidateGenres

        val genreAvailable =
            targetGenres.isNotEmpty() &&
                candidateGenres.isNotEmpty()
        val genreScore =
            if (genreAvailable) {
                relatedGenreSimilarity(
                    left = targetGenres,
                    right = candidateGenres,
                    frequency = genreFrequency,
                    documentCount =
                        documentCount,
                )
            } else {
                0.0
            }

        val topicAvailable =
            target.topic.isNotEmpty() &&
                candidate.topic.isNotEmpty()
        val topicScore =
            if (topicAvailable) {
                relatedWeightedJaccard(
                    target.topic,
                    candidate.topic,
                )
            } else {
                0.0
            }

        val storyAvailable =
            target.story.size >= 4 &&
                candidate.story.size >= 4
        val storyScore =
            if (storyAvailable) {
                relatedCosineSimilarity(
                    target.story,
                    candidate.story,
                )
            } else {
                0.0
            }

        val relation =
            relatedPeopleAndCompanySimilarity(
                target = target,
                candidate = candidate,
            )

        val yearAvailable =
            target.year != null &&
                candidate.year != null
        val yearScore =
            if (yearAvailable) {
                relatedYearSimilarity(
                    targetYear = target.year!!,
                    candidateYear =
                        candidate.year!!,
                )
            } else {
                0.0
            }

        val weighted =
            buildList {
                if (genreAvailable) {
                    add(0.32 to genreScore)
                }
                if (topicAvailable) {
                    add(0.23 to topicScore)
                }
                if (storyAvailable) {
                    add(0.18 to storyScore)
                }
                if (relation.available) {
                    add(0.10 to relation.score)
                }
                if (yearAvailable) {
                    add(0.07 to yearScore)
                }
                candidate.quality?.let {
                    add(0.05 to it)
                }
                candidate.popularity?.let {
                    add(0.05 to it)
                }
            }
        val totalWeight =
            weighted.sumOf {
                it.first
            }
        val weightedScore =
            if (totalWeight > 0.0) {
                weighted.sumOf {
                    (weight, score) ->
                    weight * score
                } / totalWeight
            } else {
                0.0
            }

        val nonGenericSharedGenres =
            sharedGenres.count {
                it !in RELATED_GENERIC_GENRES
            }
        val strongGenre =
            sharedGenres.size >= 2 ||
                nonGenericSharedGenres >= 1 &&
                genreScore >= 0.48 ||
                genreScore >= 0.58
        val strongTopic =
            topicAvailable &&
                topicScore >= 0.18
        val strongStory =
            storyAvailable &&
                storyScore >= 0.20
        val strongRelation =
            relation.available &&
                relation.score >= 0.28
        val mediumSignals =
            listOf(
                genreAvailable &&
                    genreScore >= 0.20,
                topicAvailable &&
                    topicScore >= 0.10,
                storyAvailable &&
                    storyScore >= 0.11,
                relation.available &&
                    relation.score >= 0.12,
            ).count { it }

        return RelatedSignals(
            weightedScore =
                weightedScore,
            genreScore = genreScore,
            topicScore = topicScore,
            passesRelevanceGate =
                strongGenre ||
                    strongTopic ||
                    strongStory ||
                    strongRelation ||
                    mediumSignals >= 2,
        )
    }

    private fun relatedGenreSimilarity(
        left: Set<String>,
        right: Set<String>,
        frequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (
            left.isEmpty() ||
            right.isEmpty()
        ) {
            return 0.0
        }

        val union = left union right
        val intersection = left intersect right
        if (intersection.isEmpty()) {
            return 0.0
        }

        fun weight(genre: String): Double {
            val rarity =
                relatedIdf(
                    token = genre,
                    documentFrequency =
                        frequency,
                    documentCount =
                        documentCount,
                )
            val specificity =
                if (
                    genre in
                    RELATED_GENERIC_GENRES
                ) {
                    0.42
                } else {
                    1.0
                }
            return rarity * specificity
        }

        val numerator =
            intersection.sumOf(::weight)
        val denominator =
            union.sumOf(::weight)
                .takeIf { it > 0.0 }
                ?: return 0.0
        var score =
            (numerator / denominator)
                .coerceIn(0.0, 1.0)

        if (intersection.size == 1) {
            score =
                if (
                    intersection.first() in
                    RELATED_GENERIC_GENRES
                ) {
                    score.coerceAtMost(0.28)
                } else {
                    score.coerceAtMost(0.52)
                }
        }
        return score
    }

    private fun relatedTopicWeights(
        media: MediaItem,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Map<String, Double> {
        val titleTokens =
            relatedTokenize(
                media.name
            ).toSet()
        val descriptionTokens =
            relatedTokenize(
                media.description.orEmpty()
            )
        val frequency =
            descriptionTokens
                .groupingBy { it }
                .eachCount()
        val weighted =
            mutableMapOf<String, Double>()

        titleTokens.forEach { token ->
            weighted[token] =
                relatedIdf(
                    token = token,
                    documentFrequency =
                        documentFrequency,
                    documentCount =
                        documentCount,
                ) * 1.35
        }
        frequency.forEach {
            (token, count) ->
            val score =
                relatedIdf(
                    token = token,
                    documentFrequency =
                        documentFrequency,
                    documentCount =
                        documentCount,
                ) *
                    (1.0 +
                        (count - 1)
                            .coerceAtMost(2) *
                            0.18)
            weighted[token] =
                maxOf(
                    weighted[token] ?: 0.0,
                    score,
                )
        }
        relatedThemeTags(media)
            .forEach { theme ->
                val token =
                    "theme:$theme"
                weighted[token] =
                    relatedIdf(
                        token = token,
                        documentFrequency =
                            documentFrequency,
                        documentCount =
                            documentCount,
                    ) * 1.65
            }

        return weighted.entries
            .sortedByDescending {
                it.value
            }
            .take(24)
            .associate {
                it.key to it.value
            }
    }

    private fun relatedStoryTermWeights(
        media: MediaItem,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Map<String, Double> {
        val tokens =
            relatedTokenize(
                media.description.orEmpty()
            )
        if (tokens.isEmpty()) {
            return emptyMap()
        }

        val counts =
            tokens.groupingBy { it }
                .eachCount()
        return counts.mapValues {
            (token, count) ->
            sqrt(count.toDouble()) *
                relatedIdf(
                    token = token,
                    documentFrequency =
                        documentFrequency,
                    documentCount =
                        documentCount,
                )
        }
    }

    private fun relatedPeopleAndCompanySimilarity(
        target: RelatedFeatures,
        candidate: RelatedFeatures,
    ): RelatedRelationSignal {
        val parts =
            buildList {
                if (
                    target.cast.isNotEmpty() &&
                    candidate.cast.isNotEmpty()
                ) {
                    add(
                        0.55 to
                            relatedSetSimilarity(
                                target.cast,
                                candidate.cast,
                            )
                    )
                }
                if (
                    target.creators.isNotEmpty() &&
                    candidate.creators.isNotEmpty()
                ) {
                    add(
                        0.30 to
                            relatedSetSimilarity(
                                target.creators,
                                candidate.creators,
                            )
                    )
                }
                if (
                    target.companies.isNotEmpty() &&
                    candidate.companies.isNotEmpty()
                ) {
                    add(
                        0.15 to
                            relatedSetSimilarity(
                                target.companies,
                                candidate.companies,
                            )
                    )
                }
            }
        val totalWeight =
            parts.sumOf { it.first }
        return if (totalWeight <= 0.0) {
            RelatedRelationSignal(
                score = 0.0,
                available = false,
            )
        } else {
            RelatedRelationSignal(
                score =
                    parts.sumOf {
                        (weight, score) ->
                        weight * score
                    } / totalWeight,
                available = true,
            )
        }
    }

    private fun relatedYearSimilarity(
        targetYear: Int,
        candidateYear: Int,
    ): Double {
        val difference =
            kotlin.math.abs(
                targetYear - candidateYear
            )
        return when {
            difference <= 3 -> 1.0
            difference <= 7 -> 0.75
            difference <= 12 -> 0.50
            difference <= 20 -> 0.25
            else -> 0.10
        }
    }

    private fun relatedQualityScore(
        item: MediaItem,
    ): Double? {
        val ratings =
            listOfNotNull(
                item.imdbRating,
                item.tmdbRating,
            ).filter {
                it.isFinite() &&
                    it > 0.0
            }
        if (ratings.isEmpty()) {
            return null
        }
        return (
            ratings.average() / 10.0
        ).coerceIn(0.0, 1.0)
    }

    private fun relatedPopularityScore(
        candidate: MediaItem,
        homePopularity: Map<String, Int>,
        maxHomePopularity: Int,
    ): Double {
        val count =
            homePopularity[
                relatedIdentityKey(
                    candidate
                )
            ] ?: 0
        val homeScore =
            if (count > 0) {
                sqrt(
                    count.toDouble() /
                        maxHomePopularity
                            .coerceAtLeast(1)
                            .toDouble()
                )
            } else {
                0.0
            }
        val sourceScore =
            (
                candidate.catalogSources
                    .map {
                        it.trim().lowercase()
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .size / 4.0
            ).coerceIn(0.0, 1.0)
        return maxOf(
            homeScore,
            sourceScore * 0.85,
        ).coerceIn(0.0, 1.0)
    }

    private fun relatedDiversityRerank(
        scored: List<RelatedCandidate>,
        limit: Int,
        documentCount: Int,
        genreFrequency: Map<String, Int>,
    ): List<MediaItem> {
        val remaining =
            scored.toMutableList()
        val selected =
            mutableListOf<RelatedCandidate>()
        val franchiseCounts =
            mutableMapOf<String, Int>()

        while (
            remaining.isNotEmpty() &&
            selected.size < limit
        ) {
            val next =
                remaining.maxByOrNull { candidate ->
                    val diversity =
                        if (selected.isEmpty()) {
                            1.0
                        } else {
                            1.0 -
                                selected.maxOf {
                                    chosen ->
                                    relatedCandidateSimilarity(
                                        left =
                                            candidate.features,
                                        right =
                                            chosen.features,
                                        documentCount =
                                            documentCount,
                                        genreFrequency =
                                            genreFrequency,
                                    )
                                }
                        }
                    val franchise =
                        candidate.features
                            .franchise
                    val sameFranchiseCount =
                        if (franchise.isBlank()) {
                            0
                        } else {
                            franchiseCounts[
                                franchise
                            ] ?: 0
                        }
                    val franchisePenalty =
                        when {
                            sameFranchiseCount < 2 ->
                                0.0
                            sameFranchiseCount == 2 ->
                                0.07
                            else ->
                                0.12 +
                                    (
                                        sameFranchiseCount - 3
                                    ) * 0.04
                        }.coerceAtMost(0.24)

                    (
                        0.90 * candidate.score +
                            0.10 * diversity -
                            franchisePenalty
                    )
                } ?: break

            remaining.remove(next)
            selected += next
            next.features.franchise
                .takeIf {
                    it.isNotBlank()
                }?.let { stem ->
                    franchiseCounts[stem] =
                        (franchiseCounts[stem] ?: 0) + 1
                }
        }

        return selected.map {
            it.features.item
        }
    }

    private fun relatedCandidateSimilarity(
        left: RelatedFeatures,
        right: RelatedFeatures,
        documentCount: Int,
        genreFrequency: Map<String, Int>,
    ): Double {
        val genre =
            if (
                left.genres.isNotEmpty() &&
                right.genres.isNotEmpty()
            ) {
                relatedGenreSimilarity(
                    left = left.genres,
                    right = right.genres,
                    frequency =
                        genreFrequency,
                    documentCount =
                        documentCount,
                )
            } else {
                0.0
            }
        val topic =
            relatedWeightedJaccard(
                left.topic,
                right.topic,
            )
        val sameFranchise =
            left.franchise.isNotBlank() &&
                left.franchise ==
                right.franchise
        return (
            0.58 * genre +
                0.34 * topic +
                if (sameFranchise) 0.08 else 0.0
        ).coerceIn(0.0, 1.0)
    }

    private fun relatedMergeDuplicates(
        duplicates: List<MediaItem>,
    ): MediaItem? {
        val best =
            duplicates.maxByOrNull(
                ::relatedMetadataRichness
            ) ?: return null
        val mergedSources =
            (
                best.catalogSources +
                    duplicates.flatMap {
                        it.catalogSources
                    }
            )
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy {
                    it.lowercase()
                }
        return best.copy(
            catalogSources =
                mergedSources
        )
    }

    private fun relatedMetadataRichness(
        item: MediaItem,
    ): Int =
        buildList {
            if (!item.poster.isNullOrBlank()) add(3)
            if (!item.background.isNullOrBlank()) add(2)
            if (!item.description.isNullOrBlank()) add(4)
            if (!item.releaseInfo.isNullOrBlank()) add(2)
            if (item.genres.isNotEmpty()) add(4)
            if (item.imdbRating != null) add(1)
            if (item.tmdbRating != null) add(1)
            if (item.cast.isNotEmpty()) add(2)
            if (
                item.creators.isNotEmpty() ||
                item.directors.isNotEmpty() ||
                item.writers.isNotEmpty()
            ) add(2)
        }.sum()

    private fun relatedIdentityKey(
        item: MediaItem,
    ): String =
        "${relatedCanonicalType(item.type)}|" +
            "${relatedCanonicalTitle(item.name, relatedCanonicalType(item.type))}|" +
            "${relatedYear(item).orEmpty()}"

    private fun Int?.orEmpty(): String =
        this?.toString().orEmpty()

    private fun relatedIsCurrentTitle(
        target: MediaItem,
        targetIdentity: String,
        targetTitle: String,
        targetYear: Int?,
        candidate: MediaItem,
    ): Boolean {
        if (candidate.id == target.id) {
            return true
        }
        if (
            relatedIdentityKey(candidate) ==
            targetIdentity
        ) {
            return true
        }
        val candidateTitle =
            relatedCanonicalTitle(
                candidate.name,
                relatedCanonicalType(
                    candidate.type
                ),
            )
        if (candidateTitle != targetTitle) {
            return false
        }
        val candidateYear =
            relatedYear(candidate)
        return targetYear == null ||
            candidateYear == null ||
            targetYear == candidateYear
    }

    private fun relatedCanonicalType(
        type: String,
    ): String =
        when (
            type.trim().lowercase()
        ) {
            "tv",
            "show",
            "shows",
            "series" -> "series"

            "film",
            "films",
            "movies",
            "movie" -> "movie"

            else -> type.trim().lowercase()
        }

    private fun relatedCanonicalTitle(
        name: String,
        type: String,
    ): String {
        var title =
            normalizeQuery(name)
                .replace(
                    Regex(
                        """\s+(19|20)\d{2}$"""
                    ),
                    "",
                )
        if (type == "series") {
            title =
                title
                    .replace(
                        Regex(
                            """\s+season\s+\d+.*$"""
                        ),
                        "",
                    )
                    .replace(
                        Regex(
                            """\s+(tv\s+)?series\s*$"""
                        ),
                        "",
                    )
        }
        return title.trim()
    }

    private fun relatedYear(
        item: MediaItem,
    ): Int? =
        item.releaseInfo
            ?.let {
                Regex(
                    """\b(19|20)\d{2}\b"""
                ).find(it)
                    ?.value
                    ?.toIntOrNull()
            }

    private fun relatedDocumentTokens(
        item: MediaItem,
    ): Set<String> =
        (
            relatedTokenize(item.name) +
                relatedTokenize(
                    item.description.orEmpty()
                ) +
                relatedThemeTags(item)
                    .map {
                        "theme:$it"
                    }
        ).toSet()

    private fun relatedThemeTags(
        item: MediaItem,
    ): Set<String> {
        val textTokens =
            relatedTokenize(
                item.description.orEmpty()
            ).toSet()
        if (textTokens.isEmpty()) {
            return emptySet()
        }
        return RELATED_THEME_LEXICON
            .mapNotNull {
                (theme, keywords) ->
                theme.takeIf {
                    keywords.any { keyword ->
                        textTokens.any { token ->
                            token == keyword ||
                                (
                                    token.length >= 5 &&
                                    keyword.length >= 5 &&
                                    (
                                        token.startsWith(keyword) ||
                                            keyword.startsWith(token)
                                    )
                                )
                        }
                    }
                }
            }
            .toSet()
    }

    private fun relatedTokenize(
        text: String,
    ): List<String> =
        text
            .lowercase()
            .replace(
                Regex(
                    """[^\p{L}\p{N}]+"""
                ),
                " ",
            )
            .trim()
            .split(
                Regex("""\s+""")
            )
            .asSequence()
            .map(::relatedStemToken)
            .filter {
                it.length >= 3 &&
                    it !in RELATED_STOP_WORDS &&
                    it.none(Char::isDigit)
            }
            .toList()

    private fun relatedStemToken(
        token: String,
    ): String =
        when {
            token.length > 6 &&
                token.endsWith("ies") ->
                token.dropLast(3) + "y"

            token.length > 6 &&
                token.endsWith("ing") ->
                token.dropLast(3)

            token.length > 5 &&
                token.endsWith("ed") ->
                token.dropLast(2)

            token.length > 5 &&
                token.endsWith("es") ->
                token.dropLast(2)

            token.length > 4 &&
                token.endsWith("s") ->
                token.dropLast(1)

            else -> token
        }

    private fun relatedIdf(
        token: String,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        val frequency =
            documentFrequency[token]
                ?.coerceAtLeast(1)
                ?: 1
        return ln(
            (
                documentCount + 1.0
            ) / (
                frequency + 1.0
            )
        ) + 1.0
    }

    private fun relatedWeightedJaccard(
        left: Map<String, Double>,
        right: Map<String, Double>,
    ): Double {
        if (
            left.isEmpty() ||
            right.isEmpty()
        ) {
            return 0.0
        }
        val keys =
            left.keys union right.keys
        val numerator =
            keys.sumOf { key ->
                minOf(
                    left[key] ?: 0.0,
                    right[key] ?: 0.0,
                )
            }
        val denominator =
            keys.sumOf { key ->
                maxOf(
                    left[key] ?: 0.0,
                    right[key] ?: 0.0,
                )
            }
        return if (denominator <= 0.0) {
            0.0
        } else {
            (numerator / denominator)
                .coerceIn(0.0, 1.0)
        }
    }

    private fun relatedCosineSimilarity(
        left: Map<String, Double>,
        right: Map<String, Double>,
    ): Double {
        if (
            left.isEmpty() ||
            right.isEmpty()
        ) {
            return 0.0
        }
        val dot =
            left.entries.sumOf {
                (token, value) ->
                value * (right[token] ?: 0.0)
            }
        val leftNorm =
            sqrt(
                left.values.sumOf {
                    it * it
                }
            )
        val rightNorm =
            sqrt(
                right.values.sumOf {
                    it * it
                }
            )
        if (
            leftNorm <= 0.0 ||
            rightNorm <= 0.0
        ) {
            return 0.0
        }
        return (
            dot / (leftNorm * rightNorm)
        ).coerceIn(0.0, 1.0)
    }

    private fun relatedSetSimilarity(
        left: Set<String>,
        right: Set<String>,
    ): Double {
        val union =
            left union right
        if (union.isEmpty()) {
            return 0.0
        }
        return (
            (left intersect right).size.toDouble() /
                union.size.toDouble()
        ).coerceIn(0.0, 1.0)
    }

    private fun relatedNormalizeGenre(
        genre: String,
    ): String =
        genre
            .trim()
            .lowercase()
            .replace(
                Regex("""\s+"""),
                " ",
            )

    private fun relatedFranchiseStem(
        name: String,
    ): String {
        val rawBase =
            name
                .substringBefore(':')
                .substringBefore(" - ")
                .substringBefore(" – ")
        var normalized =
            normalizeQuery(rawBase)
                .replace(
                    Regex(
                        """\s+(chapter|part|volume|vol)\s+[a-z0-9]+$"""
                    ),
                    "",
                )
                .replace(
                    Regex(
                        """\s+(ii|iii|iv|v|vi|vii|viii|ix|x|\d+)$"""
                    ),
                    "",
                )
                .trim()
        if (
            normalized.split(' ')
                .count { it.isNotBlank() } < 2
        ) {
            normalized = ""
        }
        return normalized
    }

    private data class RelatedFeatures(
        val item: MediaItem,
        val genres: Set<String>,
        val topic: Map<String, Double>,
        val story: Map<String, Double>,
        val cast: Set<String>,
        val creators: Set<String>,
        val companies: Set<String>,
        val year: Int?,
        val quality: Double?,
        val popularity: Double?,
        val franchise: String,
    )

    private data class RelatedCandidate(
        val features: RelatedFeatures,
        val score: Double,
        val genreScore: Double,
        val topicScore: Double,
    )

    private data class RelatedSignals(
        val weightedScore: Double,
        val genreScore: Double,
        val topicScore: Double,
        val passesRelevanceGate: Boolean,
    )

    private data class RelatedRelationSignal(
        val score: Double,
        val available: Boolean,
    )

    private val RELATED_GENERIC_GENRES =
        setOf(
            "action",
            "adventure",
            "comedy",
            "drama",
            "family",
            "romance",
        )

    private val RELATED_THEME_LEXICON =
        mapOf(
            "military" to
                setOf(
                    "military", "army", "navy", "marine",
                    "soldier", "veteran", "seal", "troop",
                ),
            "investigation" to
                setOf(
                    "investigate", "investigator", "detective",
                    "case", "murder", "mystery",
                ),
            "conspiracy" to
                setOf(
                    "conspiracy", "coverup", "corruption",
                    "secret", "plot",
                ),
            "espionage" to
                setOf(
                    "spy", "espionage", "intelligence", "cia",
                    "operative", "agent",
                ),
            "law" to
                setOf(
                    "police", "detective", "cop", "fbi",
                    "marshal", "law",
                ),
            "crime" to
                setOf(
                    "crime", "criminal", "murder", "killer",
                    "gang", "mafia", "cartel",
                ),
            "heist" to
                setOf(
                    "heist", "robbery", "thief", "thieves",
                    "steal", "vault",
                ),
            "revenge" to
                setOf(
                    "revenge", "vengeance", "avenge",
                ),
            "assassin" to
                setOf(
                    "assassin", "hitman", "mercenary",
                ),
            "survival" to
                setOf(
                    "survive", "survival", "stranded",
                    "wilderness", "escape",
                ),
            "apocalypse" to
                setOf(
                    "apocalypse", "apocalyptic", "outbreak",
                    "pandemic", "zombie",
                ),
            "supernatural" to
                setOf(
                    "supernatural", "ghost", "demon", "haunted",
                    "paranormal",
                ),
            "horror" to
                setOf(
                    "horror", "terror", "monster", "killer",
                    "slasher",
                ),
            "fantasy" to
                setOf(
                    "magic", "magical", "wizard", "witch",
                    "kingdom", "dragon",
                ),
            "science_fiction" to
                setOf(
                    "alien", "robot", "android", "future",
                    "spaceship", "planet", "scientist",
                ),
            "space" to
                setOf(
                    "space", "planet", "astronaut", "galaxy",
                    "spaceship",
                ),
            "time" to
                setOf(
                    "time", "timeline", "future", "past",
                ),
            "dystopia" to
                setOf(
                    "dystopia", "dystopian", "regime",
                    "oppression",
                ),
            "superhero" to
                setOf(
                    "superhero", "hero", "powers", "vigilante",
                ),
            "politics" to
                setOf(
                    "president", "government", "political",
                    "election", "senator",
                ),
            "legal" to
                setOf(
                    "lawyer", "attorney", "court", "trial",
                    "judge",
                ),
            "medical" to
                setOf(
                    "doctor", "hospital", "medical", "surgeon",
                    "patient",
                ),
            "school" to
                setOf(
                    "school", "student", "teacher", "college",
                    "university",
                ),
            "sports" to
                setOf(
                    "sport", "team", "coach", "player",
                    "championship", "athlete",
                ),
            "music" to
                setOf(
                    "music", "musician", "singer", "band",
                    "song",
                ),
            "family" to
                setOf(
                    "family", "father", "mother", "parent",
                    "daughter", "son", "sibling",
                ),
            "romance" to
                setOf(
                    "love", "romance", "romantic", "relationship",
                    "couple",
                ),
            "workplace" to
                setOf(
                    "office", "workplace", "company", "boss",
                    "coworker",
                ),
            "war" to
                setOf(
                    "war", "battle", "army", "soldier",
                    "invasion",
                ),
            "history" to
                setOf(
                    "historical", "empire", "king", "queen",
                    "century",
                ),
        )

    private val RELATED_STOP_WORDS =
        setOf(
            "about", "after", "again", "against", "all", "also",
            "and", "another", "are", "around", "back", "because",
            "been", "before", "being", "between", "both", "but",
            "can", "could", "does", "during", "each", "find",
            "first", "for", "from", "gets", "had", "has", "have",
            "her", "here", "hers", "him", "his", "how", "into",
            "its", "just", "life", "like", "make", "man", "many",
            "more", "most", "must", "new", "not", "now", "off",
            "one", "only", "other", "our", "out", "over", "own",
            "people", "she", "some", "someone", "story", "than",
            "that", "the", "their", "them", "then", "there",
            "these", "they", "this", "those", "through", "two",
            "under", "until", "very", "was", "way", "were", "what",
            "when", "where", "which", "while", "who", "will", "with",
            "woman", "world", "would", "you", "your",
        )

    @Synchronized
    private fun allCachedItems():
        List<MediaItem> =
        buildList {
            homeRows.forEach {
                addAll(it.items)
            }

            searches.values.forEach {
                addAll(it.items)
            }
        }

    private fun searchableText(
        item: MediaItem,
    ): String =
        normalizeQuery(
            buildString {
                append(item.name)
                append(' ')
                append(
                    item.releaseInfo
                        .orEmpty()
                )
                append(' ')
                append(
                    item.genres
                        .joinToString(" ")
                )
            }
        )

    private fun localSearchScore(
        item: MediaItem,
        query: String,
    ): Int {
        val title =
            normalizeQuery(item.name)
        val tokens =
            query
                .split(' ')
                .filter { it.isNotBlank() }

        return when {
            title == query -> 100_000
            title.startsWith("$query ") -> 82_000
            title.contains(query) -> 64_000
            tokens.all { it in title.split(' ') } -> 52_000
            tokens.all { token ->
                title.split(' ')
                    .any {
                        it.startsWith(token)
                    }
            } -> 44_000
            else ->
                tokens.count { token ->
                    title.contains(token)
                } * 4_000
        }
    }

    private fun localSearchIdentityKey(
        item: MediaItem,
    ): String {
        val type =
            when (
                item.type
                    .trim()
                    .lowercase()
            ) {
                "tv",
                "show",
                "shows",
                "series" ->
                    "series"

                "film",
                "films",
                "movies",
                "movie" ->
                    "movie"

                else ->
                    item.type
                        .trim()
                        .lowercase()
            }

        var title =
            normalizeQuery(
                item.name
            )

        title =
            title.replace(
                Regex(
                    """\s+(19|20)\d{2}$"""
                ),
                "",
            )

        if (type == "series") {
            title =
                title
                    .replace(
                        Regex(
                            """\s+season\s+\d+.*$"""
                        ),
                        "",
                    )
                    .replace(
                        Regex(
                            """\s+(tv\s+)?series\s*$"""
                        ),
                        "",
                    )
        }
        val year =
            item.releaseInfo
                ?.let {
                    Regex(
                        """\b(19|20)\d{2}\b"""
                    )
                        .find(it)
                        ?.value
                }
                .orEmpty()

        return "$type|$title|$year"
    }

    private fun normalizeQuery(
        query: String,
    ): String =
        query
            .lowercase()
            .replace(
                Regex(
                    """[^a-z0-9]+"""
                ),
                " ",
            )
            .trim()
            .replace(
                Regex(
                    """\s+"""
                ),
                " ",
            )

    private data class SearchEntry(
        val items: List<MediaItem>,
        val updatedAt: Long,
    )
}

@kotlin.jvm.JvmName("catalogRowsToJson")
private fun List<CatalogRow>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { row ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        row.id,
                    )
                    .put(
                        "title",
                        row.title,
                    )
                    .put(
                        "providerName",
                        row.providerName,
                    )
                    .put(
                        "items",
                        row.items
                            .toJson(),
                    )
            )
        }
    }

@kotlin.jvm.JvmName("mediaItemsToJson")
private fun List<MediaItem>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { item ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        item.id,
                    )
                    .put(
                        "type",
                        item.type,
                    )
                    .put(
                        "name",
                        item.name,
                    )
                    .put(
                        "poster",
                        item.poster,
                    )
                    .put(
                        "background",
                        item.background,
                    )
                    .put(
                        "description",
                        item.description,
                    )
                    .put(
                        "releaseInfo",
                        item.releaseInfo,
                    )
                    .put(
                        "originalLanguage",
                        item.originalLanguage,
                    )
                    .put(
                        "genres",
                        JSONArray(
                            item.genres
                        ),
                    )
                    .put(
                        "sourceExtensionId",
                        item.sourceExtensionId,
                    )
                    .put(
                        "catalogSources",
                        JSONArray(
                            item.catalogSources
                        ),
                    )
                    .put(
                        "imdbRating",
                        item.imdbRating,
                    )
                    .put(
                        "tmdbRating",
                        item.tmdbRating,
                    )
            )
        }
    }

private fun JSONArray?
    .toCatalogRows():
    List<CatalogRow> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val row =
                optJSONObject(index)
                    ?: continue

            val id =
                row.optString("id")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: continue

            val title =
                row.optString(
                    "title"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val providerName =
                row.optString(
                    "providerName",
                    "Addon",
                )

            val items =
                row.optJSONArray(
                    "items"
                ).toMediaItems()

            if (
                items.isNotEmpty()
            ) {
                add(
                    CatalogRow(
                        id = id,
                        title = title,
                        providerName =
                            providerName,
                        items = items,
                    )
                )
            }
        }
    }
}

private fun JSONArray?
    .toMediaItems():
    List<MediaItem> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val json =
                optJSONObject(index)
                    ?: continue

            val id =
                json.optString(
                    "id"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val name =
                json.optString(
                    "name"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            add(
                MediaItem(
                    id = id,
                    type =
                        json.optString(
                            "type",
                            "movie",
                        ),
                    name = name,
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
                        json.optJSONArray(
                            "genres"
                        ).toStringList(),
                    sourceExtensionId =
                        json.optNullableString(
                            "sourceExtensionId"
                        ),
                    catalogSources =
                        json.optJSONArray(
                            "catalogSources"
                        ).toStringList(),
                    imdbRating =
                        json.optDouble(
                            "imdbRating",
                            Double.NaN,
                        ).takeIf {
                            it.isFinite() &&
                                it > 0.0
                        },
                    tmdbRating =
                        json.optDouble(
                            "tmdbRating",
                            Double.NaN,
                        ).takeIf {
                            it.isFinite() &&
                                it > 0.0
                        },
                )
            )
        }
    }
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

    return optString(key)
        .takeIf {
            it.isNotBlank() &&
                it != "null"
        }
}

private fun JSONArray?
    .toStringList():
    List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            optString(index)
                .takeIf {
                    it.isNotBlank()
                }
                ?.let(::add)
        }
    }
}
