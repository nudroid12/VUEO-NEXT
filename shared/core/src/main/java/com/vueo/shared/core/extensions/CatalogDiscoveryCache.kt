package com.vueo.shared.core.extensions

import android.content.Context
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
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
        64

    private const val MAX_DISK_ITEMS_PER_ROW =
        50

    private const val RELATED_MAX_CANDIDATES =
        600

    private const val PREFS_NAME =
        "vueo_catalog_cache"

    // v2 invalidates the old cache that could only contain the first 10 rows.
    private const val KEY_HOME =
        "home_v2"

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

    
    fun companyTitles(
        companyName: String,
        networkOnly: Boolean = false,
        limit: Int = 80,
    ): List<MediaItem> {
        val needle = normalizeQuery(companyName)
        if (needle.length < 2 || limit <= 0) return emptyList()

        return allCachedItems()
            .asSequence()
            .filter { item ->
                val companies = if (networkOnly) {
                    item.networks
                } else {
                    item.productionCompanies
                }
                companies.any { company -> normalizeQuery(company.name) == needle }
            }
            .groupBy(::localSearchIdentityKey)
            .values
            .mapNotNull { duplicates ->
                duplicates.maxByOrNull(::relatedMetadataRichness)
            }
            .sortedWith(
                compareByDescending<MediaItem> { relatedMetadataRichness(it) }
                    .thenByDescending { it.tmdbRating ?: it.imdbRating ?: 0.0 }
                    .thenBy { it.name }
            )
            .take(limit)
            .toList()
    }

    fun related(
        item: MediaItem,
        limit: Int = 16,
        checkActive: () -> Unit = {},
    ): List<MediaItem> {
        checkActive()
        if (limit <= 0) {
            return emptyList()
        }

        val targetType =
            relatedCanonicalType(
                item.type
            )
        val snapshot = cacheSnapshot()
        val cached = snapshot.allItems()
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
                    checkActive()
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

        // Shortlist across all relevance signals; genre must not exclude story matches.
        val mergedCandidates = duplicateGroups.values
            .mapNotNull { checkActive(); relatedMergeDuplicates(it) }
        val candidates = if (mergedCandidates.size <= RELATED_MAX_CANDIDATES) {
            mergedCandidates
        } else {
            val quickTarget = relatedBuildFeatures(item, emptyMap(), 1, emptyMap(), 1)
            mergedCandidates.map { candidate ->
                checkActive()
                val features = relatedBuildFeatures(candidate, emptyMap(), 1, emptyMap(), 1)
                val signal = relatedSignals(quickTarget, features, emptyMap(), 1)
                Triple(candidate, signal.passesRelevanceGate, signal.weightedScore)
            }
            .sortedWith(compareByDescending<Triple<MediaItem, Boolean, Double>> { it.second }
                .thenByDescending { it.third }
                .thenByDescending { relatedMetadataRichness(it.first) }
                .thenBy { it.first.name })
            .take(RELATED_MAX_CANDIDATES)
            .map { it.first }
        }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val documents =
            listOf(item) + candidates
        val tokenSets =
            documents.map {
                checkActive()
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
            snapshot.rows
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
                checkActive()
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

        checkActive()
        return relatedDiversityRerank(
            scored = scored,
            limit = limit,
            documentCount =
                documentCount,
            genreFrequency =
                genreFrequency,
        )
    }

    /**
     * Re-ranks local matches and bounded remote candidates.
     * Trakt related mode gives lexical relevance and source order equal 45% budgets,
     * plus the existing diversity adjustment. Related candidates do not need lexical
     * overlap, but an explicit format mismatch is still rejected.
     * The legacy blend path retains its 70/20 relevance/source budgets.
     */
    fun blendRelated(
        item: MediaItem,
        localItems: List<MediaItem>,
        remoteItems: List<MediaItem>,
        limit: Int = 18,
        trustRelatedSource: Boolean = false,
    ): List<MediaItem> {
        if (limit <= 0) {
            return emptyList()
        }

        val rows = cacheSnapshot().rows
        val targetType =
            relatedCanonicalType(
                item.type
            )
        val targetIdentity =
            relatedIdentityKey(item)
        val targetTitle =
            relatedCanonicalTitle(
                item.name,
                targetType,
            )
        val targetYear =
            relatedYear(item)

        val preferredLocalIds =
            localItems
                .map {
                    "${relatedCanonicalType(it.type)}:${it.id}"
                }
                .toSet()

        val remoteConfidenceByIdentity =
            buildMap<String, Double> {
                val denominator =
                    (remoteItems.size - 1)
                        .coerceAtLeast(1)
                        .toDouble()

                remoteItems
                    .forEachIndexed { index, candidate ->
                        if (
                            relatedCanonicalType(
                                candidate.type
                            ) != targetType ||
                            relatedIsCurrentTitle(
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
                        ) {
                            return@forEachIndexed
                        }

                        val rankConfidence =
                            (
                                1.0 -
                                    0.45 *
                                    (index / denominator)
                            ).coerceIn(
                                0.55,
                                1.0,
                            )
                        val identity =
                            relatedIdentityKey(
                                candidate
                            )
                        put(
                            identity,
                            maxOf(
                                get(identity) ?: 0.0,
                                rankConfidence,
                            ),
                        )
                    }
            }

        val candidates =
            (localItems + remoteItems)
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
                .values
                .mapNotNull { duplicates ->
                    relatedMergeBlendedDuplicates(
                        duplicates = duplicates,
                        preferredLocalIds =
                            preferredLocalIds,
                    )
                }

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
                        .filter { it.isNotBlank() }
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
            rows
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
        var sparseRemoteCount = 0
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
                val tmdbConfidence =
                    remoteConfidenceByIdentity[
                        relatedIdentityKey(
                            candidate
                        )
                    ] ?: 0.0

                val trustedRemote = trustRelatedSource && tmdbConfidence > 0.0
                val targetFormat = relatedFormat(targetFeatures.genres)
                val candidateFormat = relatedFormat(candidateFeatures.genres)
                if (trustedRemote && targetFormat != null && candidateFormat != null && targetFormat != candidateFormat) {
                    return@mapNotNull null
                }
                if (!signals.passesRelevanceGate && !trustedRemote) {
                    // Missing synopsis is uncertainty; a rich synopsis that
                    // fails relevance is not a reason to trust API rank alone.
                    val sparseStory = targetFeatures.story.size < 4 || candidateFeatures.story.size < 4
                    val incompatibleGenres = targetFeatures.genres.isNotEmpty() &&
                        candidateFeatures.genres.isNotEmpty() &&
                        (targetFeatures.genres intersect candidateFeatures.genres).isEmpty()
                    if (tmdbConfidence <= 0.0 || !sparseStory || incompatibleGenres || sparseRemoteCount >= 3) {
                        return@mapNotNull null
                    }
                    sparseRemoteCount++
                }

                val vueoRelevance =
                    if (signals.passesRelevanceGate) {
                        signals.weightedScore
                    } else {
                        // Remote discovery can still contribute a candidate,
                        // but weak year/region/rating-only matches must not
                        // masquerade as strong VUEO semantic relevance.
                        signals.weightedScore * 0.30
                    }

                RelatedCandidate(
                    features =
                        candidateFeatures,
                    score =
                        if (trustRelatedSource) 0.45 * signals.weightedScore + 0.45 * tmdbConfidence
                        else 0.70 * vueoRelevance + 0.20 * tmdbConfidence,
                    genreScore =
                        signals.genreScore,
                    topicScore =
                        signals.topicScore,
                )
            }

        if (scored.isEmpty()) {
            return localItems
                .take(limit)
        }

        return relatedBlendedDiversityRerank(
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
            subjects = relatedSubjects(media),
            language = relatedNormalizeLanguage(media.originalLanguage),
            countries =
                media.countries
                    .map(::relatedNormalizeCountry)
                    .filter(String::isNotBlank)
                    .toSet(),
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
            relatedPeopleSimilarity(
                target = target,
                candidate = candidate,
            )

        val regional =
            relatedRegionalSimilarity(
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

        // Synopsis is lexical TF-IDF cosine, not an embedding/semantic model.
        // Supporting context has one shared 5% budget, even with sparse metadata.
        val context = buildList {
            if (regional.available) add(0.75 to regional.score)
            if (yearAvailable) add(0.25 to yearScore)
        }
        val weighted = buildList {
            if (storyAvailable) add(0.45 to storyScore)
            if (topicAvailable) add(0.25 to topicScore)
            if (relation.available) add(0.15 to relation.score)
            if (genreAvailable) add(0.10 to genreScore)
            if (context.isNotEmpty()) {
                add(0.05 to (context.sumOf { it.first * it.second } / context.sumOf { it.first }))
            }
        }
        val totalWeight = weighted.sumOf { it.first }
        // Discount thin metadata so a single available signal cannot score as
        // confidently as a match supported by the main story/topic/person signals.
        val weightedScore = if (totalWeight > 0.0) {
            (weighted.sumOf { it.first * it.second } / totalWeight) *
                (0.70 + 0.30 * (totalWeight / 0.85).coerceAtMost(1.0))
        } else 0.0

        val sharedStoryTerms = (target.story.keys intersect candidate.story.keys).size
        val sharedTopics = target.topic.keys intersect candidate.topic.keys
        val targetFormat = relatedFormat(target.genres)
        val candidateFormat = relatedFormat(candidate.genres)
        val incompatibleFormat = targetFormat != null && candidateFormat != null &&
            targetFormat != candidateFormat
        val targetSubjects = target.subjects
        val candidateSubjects = candidate.subjects
        val sharedSubjects = targetSubjects intersect candidateSubjects
        // Known factual formats need a subject match, not merely the same genre/host.
        val factual = targetFormat == "reality" || targetFormat == "documentary"
        val incompatibleSubject = factual && targetSubjects.isNotEmpty() &&
            candidateSubjects.isNotEmpty() && sharedSubjects.isEmpty()
        val storyEvidence = storyAvailable && sharedStoryTerms >= 2 && storyScore >= 0.12
        val topicEvidence = topicAvailable && sharedTopics.size >= 2 && topicScore >= 0.14
        val subjectEvidence = sharedSubjects.isNotEmpty() ||
            candidateSubjects.isEmpty() && sharedStoryTerms >= 3 && storyScore >= 0.20
        val peopleEvidence = relation.available && relation.score >= 0.20
        val genreSupport = genreAvailable && genreScore >= 0.20
        // Missing synopsis never earns a perfect match; sparse fallback requires people
        // plus genre, and cannot override an explicit format/subject mismatch.
        val sparseEvidence = !storyAvailable && peopleEvidence && genreSupport
        val relevance = if (factual && targetSubjects.isNotEmpty()) {
            subjectEvidence && (genreSupport || storyEvidence || peopleEvidence)
        } else {
            storyEvidence && (genreSupport || topicEvidence || peopleEvidence || storyScore >= 0.24) ||
                topicEvidence && (genreSupport || peopleEvidence) || sparseEvidence
        }
        return RelatedSignals(
            weightedScore = weightedScore,
            genreScore = genreScore,
            topicScore = topicScore,
            passesRelevanceGate = !incompatibleFormat && !incompatibleSubject && relevance,
        )
    }

    private fun relatedFormat(genres: Set<String>): String? = when {
        genres.any { it == "reality" || it == "reality tv" || it == "game show" || it == "game-show" } -> "reality"
        "documentary" in genres -> "documentary"
        genres.any { it == "animation" || it == "anime" } -> "animation"
        genres.any { it in setOf("drama", "comedy", "crime", "horror", "thriller", "romance", "action", "fantasy", "science fiction") } -> "scripted"
        else -> null
    }

    private fun relatedSubjects(media: MediaItem): Set<String> {
        val tokens = relatedTokenize(media.description.orEmpty()).toSet()
        // Two distinct anchors per subject avoid place names such as Hell's Kitchen.
        return RELATED_SUBJECT_LEXICON.mapNotNull { (subject, words) ->
            subject.takeIf { words.map(::relatedStemToken).distinct().count { it in tokens } >= 2 }
        }.toSet()
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
        val tags = relatedThemeTags(media).map { "theme:$it" } +
            relatedSubjects(media).map { "subject:$it" }
        return tags.associateWith { token ->
            relatedIdf(token, documentFrequency, documentCount)
        }
    }

    private fun relatedStoryTermWeights(
        media: MediaItem,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Map<String, Double> {
        val titleTerms = relatedTokenize(media.name).toSet()
        val tokens = relatedTokenize(media.description.orEmpty()).filter { it !in titleTerms }
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

    private fun relatedPeopleSimilarity(
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
                        0.65 to
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
                        0.35 to
                            relatedSetSimilarity(
                                target.creators,
                                candidate.creators,
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

    private fun relatedRegionalSimilarity(
        target: RelatedFeatures,
        candidate: RelatedFeatures,
    ): RelatedRelationSignal {
        val parts =
            buildList {
                if (
                    target.countries.isNotEmpty() &&
                    candidate.countries.isNotEmpty()
                ) {
                    add(
                        0.60 to
                            relatedSetSimilarity(
                                target.countries,
                                candidate.countries,
                            )
                    )
                }
                if (
                    target.language.isNotBlank() &&
                    candidate.language.isNotBlank()
                ) {
                    add(
                        0.40 to
                            if (target.language == candidate.language) {
                                1.0
                            } else {
                                0.0
                            }
                    )
                }
            }
        val totalWeight = parts.sumOf { it.first }
        return if (totalWeight <= 0.0) {
            RelatedRelationSignal(
                score = 0.0,
                available = false,
            )
        } else {
            RelatedRelationSignal(
                score =
                    parts.sumOf { (weight, score) ->
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

    private fun relatedBlendedDiversityRerank(
        scored: List<RelatedCandidate>,
        limit: Int,
        documentCount: Int,
        genreFrequency: Map<String, Int>,
    ): List<MediaItem> {
        val remaining =
            scored.toMutableList()
        val selected =
            mutableListOf<RelatedCandidate>()

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
                                selected.maxOf { chosen ->
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
                    candidate.score +
                        0.10 * diversity
                } ?: break

            remaining.remove(next)
            selected += next
        }

        return selected.map {
            it.features.item
        }
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

        while (
            remaining.isNotEmpty() &&
            selected.size < limit
        ) {
            val bestScore = remaining.maxOf { it.score }
            val next = remaining.filter { it.score >= bestScore - 0.02 }
                .maxWithOrNull(compareBy<RelatedCandidate> { candidate ->
                    val similarity = selected.maxOfOrNull { chosen ->
                        relatedCandidateSimilarity(candidate.features, chosen.features, documentCount, genreFrequency)
                    } ?: 0.0
                    candidate.score + if (selected.isEmpty()) 0.0 else 0.02 * (1.0 - similarity)
                }.thenBy { it.features.quality ?: 0.0 }
                    .thenBy { it.features.popularity ?: 0.0 }
                    .thenBy { relatedMetadataRichness(it.features.item) }
                    .thenByDescending { it.features.item.name }) ?: break

            remaining.remove(next)
            selected += next
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
        return (0.35 * genre + 0.65 * topic).coerceIn(0.0, 1.0)
    }

    private fun relatedMergeBlendedDuplicates(
        duplicates: List<MediaItem>,
        preferredLocalIds: Set<String>,
    ): MediaItem? {
        if (duplicates.isEmpty()) {
            return null
        }

        fun sourceKey(media: MediaItem): String =
            "${relatedCanonicalType(media.type)}:${media.id}"

        val primary =
            duplicates
                .filter {
                    sourceKey(it) in
                        preferredLocalIds
                }
                .maxByOrNull(
                    ::relatedMetadataRichness
                )
                ?: duplicates.maxByOrNull(
                    ::relatedMetadataRichness
                )
                ?: return null
        val richest =
            duplicates.maxByOrNull(
                ::relatedMetadataRichness
            ) ?: primary

        return primary.copy(
            poster =
                primary.poster
                    ?: richest.poster,
            background =
                primary.background
                    ?: richest.background
                    ?: primary.poster
                    ?: richest.poster,
            description =
                primary.description
                    ?.takeIf { it.isNotBlank() }
                    ?: richest.description,
            releaseInfo =
                primary.releaseInfo
                    ?: richest.releaseInfo,
            originalTitle =
                primary.originalTitle
                    ?: richest.originalTitle,
            aliases =
                duplicates
                    .flatMap { it.aliases }
                    .plus(primary.name)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            originalLanguage =
                primary.originalLanguage
                    ?: richest.originalLanguage,
            countries =
                duplicates
                    .flatMap { it.countries }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            genres =
                duplicates
                    .flatMap { it.genres }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            catalogSources =
                duplicates
                    .flatMap { it.catalogSources }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            imdbRating =
                primary.imdbRating
                    ?: richest.imdbRating,
            tmdbRating =
                primary.tmdbRating
                    ?: richest.tmdbRating,
            runtimeMinutes =
                primary.runtimeMinutes
                    ?: richest.runtimeMinutes,
            certification =
                primary.certification
                    ?: richest.certification,
            directors =
                (primary.directors +
                    richest.directors)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            creators =
                (primary.creators +
                    richest.creators)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            writers =
                (primary.writers +
                    richest.writers)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
            cast =
                (primary.cast +
                    richest.cast)
                    .distinctBy { it.name.lowercase() },
            productionCompanies =
                (primary.productionCompanies +
                    richest.productionCompanies)
                    .distinctBy { it.name.lowercase() },
            networks =
                (primary.networks +
                    richest.networks)
                    .distinctBy { it.name.lowercase() },
        )
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
                relatedThemeTags(item).map { "theme:$it" } +
                relatedSubjects(item).map { "subject:$it" }
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
        return RELATED_THEME_LEXICON.mapNotNull { (theme, keywords) ->
            theme.takeIf {
                keywords.map(::relatedStemToken).distinct().count { it in textTokens } >= 2
            }
        }.toSet()
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

    private fun relatedNormalizeLanguage(
        raw: String?,
    ): String {
        val normalized =
            raw.orEmpty()
                .trim()
                .lowercase()
                .replace(Regex("""[^a-z]"""), "")
        return when (normalized) {
            "fil", "tl", "tgl", "tagalog", "filipino" -> "fil"
            "en", "eng", "english" -> "en"
            "ko", "kor", "korean" -> "ko"
            "ja", "jpn", "japanese" -> "ja"
            "zh", "zho", "chi", "chinese", "mandarin" -> "zh"
            "es", "spa", "spanish" -> "es"
            "fr", "fra", "fre", "french" -> "fr"
            "de", "deu", "ger", "german" -> "de"
            "id", "ind", "indonesian" -> "id"
            "ms", "msa", "may", "malay" -> "ms"
            "th", "tha", "thai" -> "th"
            else -> normalized
        }
    }

    private fun relatedNormalizeCountry(
        raw: String,
    ): String {
        val normalized =
            raw.trim()
                .lowercase()
                .replace("&", "and")
                .replace(Regex("""[^a-z]"""), "")
        return when (normalized) {
            "ph", "philippines", "republicofthephilippines" -> "ph"
            "us", "usa", "unitedstates", "unitedstatesofamerica" -> "us"
            "gb", "uk", "unitedkingdom", "greatbritain" -> "gb"
            "kr", "southkorea", "republicofkorea", "korea" -> "kr"
            "jp", "japan" -> "jp"
            "cn", "china", "peoplesrepublicofchina" -> "cn"
            "id", "indonesia" -> "id"
            "my", "malaysia" -> "my"
            "th", "thailand" -> "th"
            else -> normalized
        }
    }

    private data class RelatedFeatures(
        val item: MediaItem,
        val genres: Set<String>,
        val topic: Map<String, Double>,
        val story: Map<String, Double>,
        val cast: Set<String>,
        val creators: Set<String>,
        val subjects: Set<String>,
        val language: String,
        val countries: Set<String>,
        val year: Int?,
        val quality: Double?,
        val popularity: Double?,
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

    private val RELATED_SUBJECT_LEXICON = mapOf(
        "cooking" to setOf("cook", "cooking", "chef", "chefs", "culinary", "food", "dish", "dishes", "recipe", "restaurant", "baking", "baker"),
        "music" to setOf("music", "singer", "singing", "vocal", "song", "band", "musician"),
        "dance" to setOf("dance", "dancing", "dancer", "choreography", "choreographer"),
        "dating" to setOf("dating", "date", "couple", "romance", "romantic", "singles", "partner"),
        "fashion" to setOf("fashion", "designer", "design", "model", "runway", "couture"),
        "sport" to setOf("sport", "athlete", "team", "football", "basketball", "soccer", "coach"),
        "nature" to setOf("wildlife", "animal", "nature", "species", "habitat", "ocean", "forest"),
        "history" to setOf("history", "historical", "ancient", "civilization", "empire", "archaeology"),
        "science" to setOf("science", "scientist", "research", "experiment", "technology", "discovery"),
        "crime" to setOf("crime", "criminal", "murder", "killer", "detective", "police", "mafia", "gang"),
        "survival" to setOf("survival", "survive", "wilderness", "stranded", "island", "shelter"),
        "business" to setOf("business", "entrepreneur", "startup", "investor", "investment", "company"),
        "home" to setOf("renovation", "renovate", "house", "property", "home", "interior"),
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

    // Capture references while protected; flattening, filtering and ranking happen
    // after releasing the monitor used by Home's cheap freshness/read operations.
    private data class CacheSnapshot(
        val rows: List<CatalogRow>,
        val searchItems: List<List<MediaItem>>,
    ) {
        fun allItems(): List<MediaItem> = buildList {
            rows.forEach { addAll(it.items) }
            searchItems.forEach { addAll(it) }
        }
    }

    @Synchronized
    private fun cacheSnapshot(): CacheSnapshot =
        CacheSnapshot(homeRows, searches.values.map { it.items })

    private fun allCachedItems(): List<MediaItem> = cacheSnapshot().allItems()

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
