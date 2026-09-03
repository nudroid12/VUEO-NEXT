package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.player.PlayerSourcePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

class UnifiedMediaEngine {
    private val extensions = CopyOnWriteArrayList<MediaExtension>()
    private val disabledExtensionIds =
        CopyOnWriteArraySet<String>()

    private val ACTOR_SEARCH_HINTS =
        setOf(
            "actor",
            "actors",
            "cast",
            "person",
            "people",
            "performer",
            "performers",
            "star",
            "stars",
        )

    fun install(extension: MediaExtension) {
        extensions.removeAll { it.descriptor.id == extension.descriptor.id }
        extensions += extension
    }

    fun uninstall(id: String) {
        extensions.removeAll { it.descriptor.id == id }
        disabledExtensionIds.remove(id)
    }

    fun setExtensionEnabled(
        id: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            disabledExtensionIds.remove(id)
        } else {
            disabledExtensionIds.add(id)
        }
    }

    fun isExtensionEnabled(
        id: String,
    ): Boolean =
        id !in disabledExtensionIds

    fun installed(): List<MediaExtension> = extensions.toList()

    fun stremioAddons(): List<MediaExtension> =
        extensions.filter { it.descriptor.kind == ExtensionKind.STREMIO_ADDON }

    fun activeStremioAddons(): List<MediaExtension> =
        stremioAddons().filter {
            isExtensionEnabled(
                it.descriptor.id
            )
        }

    fun extension(id: String?): MediaExtension? =
        id?.let { target -> extensions.firstOrNull { it.descriptor.id == target } }

    suspend fun loadCatalogRows(
        maxRows: Int = 10,
        forceRefresh: Boolean = false,
        catalogOrder: List<String> = emptyList(),
    ): List<CatalogRow> = coroutineScope {
        if (!forceRefresh) {
            CatalogDiscoveryCache
                .home()
                ?.let {
                    return@coroutineScope orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    ).take(maxRows)
                }
        }

        val orderIndex =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        val candidates =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter {
                            it.canLoadWithoutExtras
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .sortedBy {
                    (extension, catalog) ->
                    orderIndex[
                        catalogKey(
                            extensionId =
                                extension.descriptor.id,
                            type = catalog.type,
                            catalogId = catalog.id,
                        )
                    ] ?: Int.MAX_VALUE
                }
                .take(maxRows)

        val rows =
            candidates
                .map {
                    (extension, catalog) ->
                    async {
                        runCatching {
                            val page =
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    extension.catalog(
                                        catalog.type,
                                        catalog.id,
                                    )
                                }
                                    ?: return@runCatching null

                            CatalogRow(
                                id =
                                    catalogKey(
                                        extensionId =
                                            extension.descriptor.id,
                                        type =
                                            catalog.type,
                                        catalogId =
                                            catalog.id,
                                    ),
                                title =
                                    catalog.name
                                        ?: "${extension.descriptor.name} " +
                                            catalog.type
                                                .replaceFirstChar {
                                                    it.uppercase()
                                                },
                                providerName =
                                    extension
                                        .descriptor
                                        .name,
                                items =
                                    page.items.map {
                                        item ->
                                        item.withCatalogSource(
                                            extension.descriptor.name
                                        )
                                    },
                            )
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .filter {
                    it.items.isNotEmpty()
                }
                .let {
                    orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    )
                }

        CatalogDiscoveryCache.putHome(
            rows
        )

        rows
    }

    private fun catalogKey(
        extensionId: String,
        type: String,
        catalogId: String,
    ): String =
        "$extensionId:$type:$catalogId"

    private fun orderCatalogRows(
        rows: List<CatalogRow>,
        catalogOrder: List<String>,
    ): List<CatalogRow> {
        if (catalogOrder.isEmpty()) {
            return rows
        }

        val index =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        return rows.sortedBy {
            index[it.id] ?: Int.MAX_VALUE
        }
    }

    suspend fun search(
        query: String,
        maxCatalogs: Int = 12,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> = coroutineScope {
        val normalized = query.trim()

        if (normalized.length < 2) {
            return@coroutineScope emptyList<MediaItem>()
        }

        CatalogDiscoveryCache
            .search(normalized)
            ?.let { cached ->
                return@coroutineScope rankSearchResults(
                    items = cached,
                    query = normalized,
                ).take(maxResults)
            }

        val searchableCatalogs =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter { catalog ->
                            val hasSearch =
                                catalog.extras.any {
                                    it.name == "search"
                                }

                            val requiredSupported =
                                catalog.extras
                                    .filter {
                                        it.isRequired
                                    }
                                    .all {
                                        it.name == "search"
                                    }

                            hasSearch &&
                                requiredSupported
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .take(maxCatalogs)

        val local =
            CatalogDiscoveryCache
                .searchLocal(
                    normalized,
                    limit = maxResults,
                )

        val collectedRemote =
            mutableListOf<MediaItem>()
        val mergeMutex =
            Mutex()

        val remote =
            searchableCatalogs
                .map {
                    (extension, catalog) ->

                    async {
                        val result =
                            try {
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    extension.catalog(
                                        type =
                                            catalog.type,
                                        catalogId =
                                            catalog.id,
                                        extras =
                                            mapOf(
                                                "search" to
                                                    normalized
                                            ),
                                    ).items
                                        .map {
                                            item ->
                                            item.withCatalogSource(
                                                extension.descriptor.name
                                            )
                                        }
                                }
                                    ?: emptyList()
                            } catch (
                                cancelled:
                                    CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                _: Throwable
                            ) {
                                emptyList()
                            }

                        if (result.isNotEmpty()) {
                            mergeMutex.withLock {
                                collectedRemote +=
                                    result

                                onPartial?.invoke(
                                    rankSearchResults(
                                        items =
                                            collectedRemote +
                                                local,
                                        query =
                                            normalized,
                                    ).take(
                                        maxResults
                                    )
                                )
                            }
                        }

                        result
                    }
                }
                .awaitAll()
                .flatten()

        val combined =
            rankSearchResults(
                items = remote + local,
                query = normalized,
            ).take(maxResults)

        CatalogDiscoveryCache.putSearch(
            normalized,
            combined,
        )

        combined
    }

    /**
     * Search enabled metadata catalogs that explicitly advertise actor/person
     * filtering. Catalogs without an actor-capable extra are skipped entirely,
     * so actor mode never brute-force scans the regular title database.
     */
    suspend fun searchActor(
        query: String,
        maxCatalogs: Int = 12,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> = coroutineScope {
        val normalized = query.trim()

        if (normalized.length < 2) {
            return@coroutineScope emptyList<MediaItem>()
        }

        val bindings =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .mapNotNull { catalog ->
                            actorCatalogExtras(
                                catalog = catalog,
                                query = normalized,
                            )?.let { extras ->
                                ActorCatalogBinding(
                                    extension = extension,
                                    catalog = catalog,
                                    extras = extras,
                                )
                            }
                        }
                }
                .take(maxCatalogs)

        if (bindings.isEmpty()) {
            return@coroutineScope emptyList<MediaItem>()
        }

        val sourceFingerprint =
            bindings
                .map { binding ->
                    "${binding.extension.descriptor.id}:${binding.catalog.id}"
                }
                .sorted()
                .joinToString("|")
                .hashCode()

        val cacheKey =
            "actor $sourceFingerprint $normalized"

        CatalogDiscoveryCache
            .search(cacheKey)
            ?.let { cached ->
                return@coroutineScope mergeActorResults(
                    items = cached,
                    maxResults = maxResults,
                )
            }

        val collected =
            mutableListOf<MediaItem>()
        val mergeMutex = Mutex()

        val remote =
            bindings
                .map { binding ->
                    async {
                        val result =
                            try {
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    binding.extension
                                        .catalog(
                                            type =
                                                binding.catalog.type,
                                            catalogId =
                                                binding.catalog.id,
                                            extras =
                                                binding.extras,
                                        )
                                        .items
                                        .filter(
                                            ::isActorSearchMediaItem
                                        )
                                        .map { item ->
                                            item
                                                .normalizeActorSearchType()
                                                .withCatalogSource(
                                                    binding.extension
                                                        .descriptor
                                                        .name
                                                )
                                        }
                                } ?: emptyList()
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                _: Throwable
                            ) {
                                emptyList()
                            }

                        if (result.isNotEmpty()) {
                            mergeMutex.withLock {
                                collected += result
                                onPartial?.invoke(
                                    mergeActorResults(
                                        items = collected,
                                        maxResults = maxResults,
                                    )
                                )
                            }
                        }

                        result
                    }
                }
                .awaitAll()
                .flatten()

        val combined =
            mergeActorResults(
                items = remote,
                maxResults = maxResults,
            )

        CatalogDiscoveryCache.putSearch(
            cacheKey,
            combined,
        )

        combined
    }

    fun hasActorSearchAddons(): Boolean =
        activeStremioAddons()
            .any { extension ->
                extension.descriptor.catalogs
                    .any(
                        ::actorCatalogSupportsLookup
                    )
            }

    fun mergeActorResults(
        items: List<MediaItem>,
        maxResults: Int = 80,
    ): List<MediaItem> =
        mergeSearchDuplicates(
            items =
                items.filter(
                    ::isActorSearchMediaItem
                ),
            query = "",
        )
            .take(maxResults)

    private fun actorCatalogSupportsLookup(
        catalog: CatalogDescriptor,
    ): Boolean {
        val hasActorExtra =
            catalog.extras.any { extra ->
                isActorExtraName(
                    extra.name
                )
            }
        val hasSearchExtra =
            catalog.extras.any { extra ->
                extra.name.equals(
                    "search",
                    ignoreCase = true,
                )
            }

        if (
            !hasActorExtra &&
            !(
                actorCatalogHint(catalog) &&
                    hasSearchExtra
            )
        ) {
            return false
        }

        return catalog.extras
            .filter { it.isRequired }
            .all { extra ->
                isActorExtraName(
                    extra.name
                ) ||
                    extra.name.equals(
                        "search",
                        ignoreCase = true,
                    )
            }
    }

    private fun actorCatalogExtras(
        catalog: CatalogDescriptor,
        query: String,
    ): Map<String, String>? {
        if (!actorCatalogSupportsLookup(catalog)) {
            return null
        }

        val actorExtras =
            catalog.extras.filter { extra ->
                isActorExtraName(
                    extra.name
                )
            }
        val actorValues =
            actorExtras.mapNotNull { extra ->
                actorExtraValue(
                    extra = extra,
                    query = query,
                )?.let { value ->
                    extra to value
                }
            }
        val searchExtra =
            catalog.extras.firstOrNull { extra ->
                extra.name.equals(
                    "search",
                    ignoreCase = true,
                )
            }
        val actorCatalog =
            actorCatalogHint(
                catalog
            )

        if (
            actorValues.isEmpty() &&
            !(actorCatalog && searchExtra != null)
        ) {
            return null
        }

        val requiredSupported =
            catalog.extras
                .filter { it.isRequired }
                .all { extra ->
                    when {
                        isActorExtraName(
                            extra.name
                        ) ->
                            actorValues.any { pair ->
                                pair.first.name ==
                                    extra.name
                            }

                        extra.name.equals(
                            "search",
                            ignoreCase = true,
                        ) -> true

                        else -> false
                    }
                }

        if (!requiredSupported) {
            return null
        }

        return buildMap {
            actorValues
                .firstOrNull()
                ?.let { pair ->
                    put(
                        pair.first.name,
                        pair.second,
                    )
                }

            actorValues
                .filter { pair ->
                    pair.first.isRequired
                }
                .forEach { pair ->
                    put(
                        pair.first.name,
                        pair.second,
                    )
                }

            if (
                searchExtra != null &&
                (
                    actorCatalog ||
                        searchExtra.isRequired
                )
            ) {
                put(
                    searchExtra.name,
                    query,
                )
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun actorExtraValue(
        extra: CatalogExtraDescriptor,
        query: String,
    ): String? {
        if (extra.options.isEmpty()) {
            return query
        }

        val normalizedQuery =
            normalizeSearchText(
                query
            )

        return extra.options
            .firstOrNull { option ->
                normalizeSearchText(
                    option
                ) == normalizedQuery
            }
    }

    private fun actorCatalogHint(
        catalog: CatalogDescriptor,
    ): Boolean {
        val value =
            listOfNotNull(
                catalog.type,
                catalog.id,
                catalog.name,
            )
                .joinToString(" ")
                .lowercase()
                .replace(
                    Regex("""[^a-z0-9]+"""),
                    " ",
                )

        return ACTOR_SEARCH_HINTS.any { hint ->
            Regex("(?:^|\\s)${Regex.escape(hint)}(?:$|\\s)")
                .containsMatchIn(value)
        }
    }

    private fun isActorExtraName(
        name: String,
    ): Boolean {
        val words =
            name
                .trim()
                .replace(
                    Regex("([a-z0-9])([A-Z])"),
                    "$1 $2",
                )
                .lowercase()
                .replace(
                    Regex("""[^a-z0-9]+"""),
                    " ",
                )
                .trim()
                .split(Regex("""\s+"""))
                .filter { it.isNotBlank() }

        if (words.size == 1) {
            return words.first() in
                ACTOR_SEARCH_HINTS
        }

        val first =
            words.firstOrNull()
                ?: return false

        if (
            first !in ACTOR_SEARCH_HINTS ||
            "id" in words
        ) {
            return false
        }

        return words
            .drop(1)
            .all { suffix ->
                suffix in
                    setOf(
                        "name",
                        "member",
                        "members",
                        "query",
                        "filter",
                    )
            }
    }

    private fun isActorSearchMediaItem(
        item: MediaItem,
    ): Boolean =
        when (
            item.type
                .trim()
                .lowercase()
        ) {
            "movie",
            "film",
            "series",
            "tv",
            "show",
            "anime" -> true
            else -> false
        }

    private fun MediaItem.normalizeActorSearchType(): MediaItem {
        val normalizedType =
            when (
                type.trim().lowercase()
            ) {
                "film" -> "movie"
                "tv",
                "show" -> "series"
                else -> type
            }

        return if (normalizedType == type) {
            this
        } else {
            copy(type = normalizedType)
        }
    }

    private data class ActorCatalogBinding(
        val extension: MediaExtension,
        val catalog: CatalogDescriptor,
        val extras: Map<String, String>,
    )

    private fun rankSearchResults(
        items: List<MediaItem>,
        query: String,
    ): List<MediaItem> {
        val normalizedQuery =
            normalizeSearchText(query)

        if (normalizedQuery.isBlank()) {
            return mergeSearchDuplicates(
                items = items,
                query = normalizedQuery,
            )
        }

        return mergeSearchDuplicates(
            items =
                items.filter {
                    searchIsRelevantEnough(
                        item = it,
                        query = normalizedQuery,
                    )
                },
            query = normalizedQuery,
        )
            .sortedWith(
                compareByDescending<MediaItem> {
                    searchRelevanceScore(
                        item = it,
                        query = normalizedQuery,
                    )
                }.thenByDescending {
                    searchMetadataScore(it)
                }.thenByDescending {
                    it.imdbRating
                        ?: it.tmdbRating
                        ?: 0.0
                }
            )
    }

    private fun mergeSearchDuplicates(
        items: List<MediaItem>,
        query: String,
    ): List<MediaItem> {
        val groups =
            mutableListOf<
                MutableList<MediaItem>
            >()

        items.forEach {
            candidate ->

            val candidateTitle =
                searchCanonicalTitle(
                    candidate
                )
            val candidateType =
                searchCanonicalType(
                    candidate.type
                )
            val candidateYear =
                searchReleaseYear(
                    candidate
                )

            val target =
                groups.firstOrNull {
                    group ->
                    val sample =
                        group.first()

                    val sampleTitle =
                        searchCanonicalTitle(
                            sample
                        )
                    val sampleType =
                        searchCanonicalType(
                            sample.type
                        )
                    val sampleYear =
                        searchReleaseYear(
                            sample
                        )

                    candidateTitle.isNotBlank() &&
                        candidateTitle ==
                            sampleTitle &&
                        candidateType ==
                            sampleType &&
                        (
                            candidateYear == 0 ||
                                sampleYear == 0 ||
                                kotlin.math.abs(
                                    candidateYear -
                                        sampleYear
                                ) <= 1
                        )
                }

            if (target == null) {
                groups +=
                    mutableListOf(
                        candidate
                    )
            } else {
                target += candidate
            }
        }

        return groups.mapNotNull {
            duplicates ->

            val best =
                duplicates.maxByOrNull {
                    item ->
                    (
                        searchRelevanceScore(
                            item = item,
                            query = query,
                        ) *
                            100
                    ) +
                        searchMetadataScore(
                            item
                        )
                }
                    ?: return@mapNotNull null

            val catalogs =
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

            val mergedGenres =
                duplicates
                    .flatMap {
                        it.genres
                    }
                    .distinctBy {
                        it.lowercase()
                    }

            best.copy(
                catalogSources =
                    catalogs,
                genres =
                    mergedGenres,
            )
        }
    }

    private fun searchIsRelevantEnough(
        item: MediaItem,
        query: String,
    ): Boolean {
        val normalizedQuery =
            normalizeSearchText(query)
        val titleQuery =
            searchTitleQuery(
                normalizedQuery
            )
        val title =
            normalizeSearchText(
                item.name
            )

        if (
            normalizedQuery.isBlank() ||
            titleQuery.isBlank() ||
            title.isBlank()
        ) {
            return false
        }

        val score =
            searchRelevanceScore(
                item = item,
                query = normalizedQuery,
            )

        if (score >= 44_000) {
            return true
        }

        val queryTokens =
            titleQuery
                .split(' ')
                .filter {
                    it.length >= 2
                }
        val titleTokens =
            title
                .split(' ')
                .filter {
                    it.isNotBlank()
                }

        if (queryTokens.isEmpty()) {
            return score > 0
        }

        val matched =
            queryTokens.count {
                token ->
                titleTokens.any {
                    titleToken ->
                    titleToken == token ||
                        titleToken.startsWith(
                            token
                        ) ||
                        token.startsWith(
                            titleToken
                        )
                }
            }

        return if (
            queryTokens.size == 1
        ) {
            matched == 1
        } else {
            matched ==
                queryTokens.size
        }
    }

    private fun searchCanonicalTitle(
        item: MediaItem,
    ): String {
        var title =
            normalizeSearchText(
                item.name
            )

        title =
            title.replace(
                Regex(
                    """\s+(19|20)\d{2}$"""
                ),
                "",
            )

        if (
            searchCanonicalType(
                item.type
            ) == "series"
        ) {
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

    private fun searchCanonicalType(
        value: String,
    ): String =
        when (
            value
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
                value
                    .trim()
                    .lowercase()
        }

    private fun searchRelevanceScore(
        item: MediaItem,
        query: String,
    ): Int {
        val normalizedQuery =
            normalizeSearchText(query)
        val titleQuery =
            searchTitleQuery(
                normalizedQuery
            )
        val title =
            normalizeSearchText(item.name)

        if (
            normalizedQuery.isBlank() ||
            titleQuery.isBlank() ||
            title.isBlank()
        ) {
            return 0
        }

        val queryTokens =
            titleQuery
                .split(' ')
                .filter {
                    it.isNotBlank()
                }
        val titleTokens =
            title
                .split(' ')
                .filter {
                    it.isNotBlank()
                }

        val exactTokenMatches =
            queryTokens.count {
                it in titleTokens
            }

        val prefixTokenMatches =
            queryTokens.count {
                token ->
                titleTokens.any {
                    titleToken ->
                    titleToken.startsWith(
                        token
                    )
                }
            }

        var score =
            when {
                title == titleQuery ->
                    120_000

                title.startsWith(
                    "$titleQuery "
                ) ->
                    96_000

                title.contains(
                    " $titleQuery "
                ) ||
                    title.endsWith(
                        " $titleQuery"
                    ) ->
                    86_000

                title.contains(
                    titleQuery
                ) ->
                    76_000

                queryTokens.isNotEmpty() &&
                    exactTokenMatches ==
                        queryTokens.size ->
                    62_000

                queryTokens.isNotEmpty() &&
                    prefixTokenMatches ==
                        queryTokens.size ->
                    52_000

                else ->
                    (
                        exactTokenMatches *
                            6_000
                    ) +
                        (
                            prefixTokenMatches *
                                3_000
                        )
            }

        if (
            queryTokens.size > 1 &&
            exactTokenMatches <
                queryTokens.size
        ) {
            score -=
                (
                    queryTokens.size -
                        exactTokenMatches
                ) * 4_000
        }

        val queryYear =
            Regex(
                """\b(19|20)\d{2}\b"""
            )
                .find(normalizedQuery)
                ?.value
                ?.toIntOrNull()

        if (queryYear != null) {
            score +=
                if (
                    searchReleaseYear(item) ==
                    queryYear
                ) {
                    12_000
                } else {
                    -4_000
                }
        }

        return score
    }

    private fun searchMetadataScore(
        item: MediaItem,
    ): Int {
        var score = 0

        if (!item.poster.isNullOrBlank()) {
            score += 80
        }
        if (!item.background.isNullOrBlank()) {
            score += 35
        }
        if (!item.description.isNullOrBlank()) {
            score += 30
        }
        if (!item.releaseInfo.isNullOrBlank()) {
            score += 20
        }
        if (item.genres.isNotEmpty()) {
            score += 15
        }

        if (item.catalogSources.isNotEmpty()) {
            score += 12
        }

        score +=
            ((
                item.imdbRating
                    ?: item.tmdbRating
                    ?: 0.0
            ) * 10.0)
                .toInt()

        return score
    }

    private fun searchReleaseYear(
        item: MediaItem,
    ): Int =
        item.releaseInfo
            ?.let {
                Regex(
                    """\b(19|20)\d{2}\b"""
                )
                    .find(it)
                    ?.value
                    ?.toIntOrNull()
            }
            ?: 0

    private fun searchTitleQuery(
        normalizedQuery: String,
    ): String =
        normalizedQuery
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

    private fun normalizeSearchText(
        value: String,
    ): String =
        value
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

    private fun MediaItem.withCatalogSource(
        source: String?,
    ): MediaItem {
        val cleaned =
            source
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return this

        return copy(
            catalogSources =
                (
                    catalogSources +
                        cleaned
                )
                    .distinctBy {
                        it.lowercase()
                    }
        )
    }

    suspend fun loadMeta(
        item: MediaItem,
    ): MediaItem = coroutineScope {
        val providers =
            activeStremioAddons()
                .filter { extension ->
                    "meta" in extension.descriptor.resources &&
                        (
                            extension.descriptor.types.isEmpty() ||
                                item.type in extension.descriptor.types
                            )
                }
                .sortedBy { extension ->
                    if (
                        extension.descriptor.id ==
                        item.sourceExtensionId
                    ) {
                        0
                    } else {
                        1
                    }
                }

        if (providers.isEmpty()) {
            return@coroutineScope item
        }

        val primaryProvider =
            providers.firstOrNull {
                it.descriptor.id == item.sourceExtensionId
            }
        val primaryMetadata =
            primaryProvider?.let { provider ->
                runCatching {
                    withTimeoutOrNull(
                        ADDON_REQUEST_TIMEOUT_MS
                    ) {
                        provider.meta(
                            item.type,
                            item.id,
                        )
                    }
                }.getOrNull()
            }
        val primaryResult =
            primaryMetadata?.let { metadata ->
                mergeMediaMetadata(
                    current = item,
                    candidate = metadata,
                    sourceExtensionId = item.sourceExtensionId,
                )
            } ?: item

        if (!needsMetadataFallback(primaryResult)) {
            return@coroutineScope primaryResult
        }

        val fallbackMetadata =
            providers
                .filterNot {
                    it.descriptor.id == primaryProvider?.descriptor?.id
                }
                .map { provider ->
                    async {
                        runCatching {
                            withTimeoutOrNull(
                                METADATA_FALLBACK_TIMEOUT_MS
                            ) {
                                provider.meta(
                                    item.type,
                                    item.id,
                                )
                            }
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()

        fallbackMetadata.fold(primaryResult) { merged, candidate ->
            mergeMediaMetadata(
                current = merged,
                candidate = candidate,
                sourceExtensionId = item.sourceExtensionId,
            )
        }
    }

    suspend fun resolveStreams(
        type: String,
        videoId: String,
    ): List<StreamSource> =
        resolveStreamsProgressive(
            type = type,
            videoId = videoId,
            onProgress = {},
        )

    suspend fun resolveStreamsProgressive(
        type: String,
        videoId: String,
        onProgress: suspend (AddonStreamProgress) -> Unit,
    ): List<StreamSource> = coroutineScope {
        val providers =
            extensions.filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "stream" in
                        it.descriptor.resources
            }

        if (providers.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val mutex = Mutex()
        val rawStreams =
            mutableListOf<StreamSource>()

        var completed = 0

        providers.map { extension ->
            async {
                val result =
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_STREAM_TIMEOUT_MS
                        ) {
                            extension.streams(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )

                val progress =
                    mutex.withLock {
                        rawStreams += result
                        completed++

                        AddonStreamProgress(
                            streams =
                                SourceCleaner.clean(
                                    rawStreams
                                ),
                            rawCount =
                                rawStreams.size,
                            completedAddons =
                                completed,
                            totalAddons =
                                providers.size,
                        )
                    }

                onProgress(progress)
            }
        }.awaitAll()

        mutex.withLock {
            SourceCleaner.clean(
                rawStreams
            )
        }
    }

    suspend fun resolveSubtitles(
        type: String,
        videoId: String,
    ): List<SubtitleTrack> = coroutineScope {
        extensions
            .filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "subtitles" in
                        it.descriptor.resources
            }
            .map { extension ->
                async {
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_REQUEST_TIMEOUT_MS
                        ) {
                            extension.subtitles(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )
                }
            }
            .awaitAll()
            .flatten()
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
    }
    companion object {
        private const val ADDON_REQUEST_TIMEOUT_MS =
            8_000L

        private const val METADATA_FALLBACK_TIMEOUT_MS =
            4_000L

        private const val ADDON_STREAM_TIMEOUT_MS =
            10_000L
    }
}

private fun mergeMediaMetadata(
    current: MediaItem,
    candidate: MediaItem,
    sourceExtensionId: String?,
): MediaItem =
    current.copy(
        name = current.name.ifBlank { candidate.name },
        poster = current.poster ?: candidate.poster,
        background = current.background ?: candidate.background,
        description = richerMetadataText(
            current.description,
            candidate.description,
        ),
        releaseInfo = current.releaseInfo ?: candidate.releaseInfo,
        originalLanguage =
            current.originalLanguage ?: candidate.originalLanguage,
        genres = (current.genres + candidate.genres).distinct(),
        episodes = mergeMetadataEpisodes(
            current.episodes,
            candidate.episodes,
        ),
        sourceExtensionId = sourceExtensionId,
        imdbRating = current.imdbRating ?: candidate.imdbRating,
        tmdbRating = current.tmdbRating ?: candidate.tmdbRating,
        runtimeMinutes = current.runtimeMinutes ?: candidate.runtimeMinutes,
        certification = current.certification ?: candidate.certification,
        directors = (current.directors + candidate.directors).distinct(),
        creators = (current.creators + candidate.creators).distinct(),
        writers = (current.writers + candidate.writers).distinct(),
        cast = (current.cast + candidate.cast).distinctBy { it.name.lowercase() },
        productionCompanies =
            (current.productionCompanies + candidate.productionCompanies)
                .distinctBy { it.name.lowercase() },
        networks =
            (current.networks + candidate.networks)
                .distinctBy { it.name.lowercase() },
    )

private fun needsMetadataFallback(item: MediaItem): Boolean {
    val hasUsableArtwork =
        !item.poster.isNullOrBlank() ||
            !item.background.isNullOrBlank()

    if (
        item.description.isNullOrBlank() ||
        !hasUsableArtwork
    ) {
        return true
    }

    if (item.type != "series") return false

    // Episode artwork, overview text and pretty titles are enhancement-level
    // gaps. They must not fan out to every metadata addon and hold up Detail.
    return item.episodes.isEmpty()
}

private fun mergeMetadataEpisodes(
    current: List<EpisodeItem>,
    candidate: List<EpisodeItem>,
): List<EpisodeItem> {
    if (current.isEmpty()) return candidate
    if (candidate.isEmpty()) return current

    val candidateByPosition =
        candidate.associateBy { it.season to it.episode }
    val currentPositions =
        current.mapTo(mutableSetOf()) { it.season to it.episode }

    return (
        current.map { episode ->
            val fallback =
                candidateByPosition[episode.season to episode.episode]
                    ?: return@map episode

            episode.copy(
                title = preferredEpisodeTitle(
                    current = episode.title,
                    candidate = fallback.title,
                    episodeNumber = episode.episode,
                ),
                released = episode.released ?: fallback.released,
                overview = richerMetadataText(
                    episode.overview,
                    fallback.overview,
                ),
                thumbnail = episode.thumbnail ?: fallback.thumbnail,
            )
        } +
            candidate.filter {
                (it.season to it.episode) !in currentPositions
            }
        )
        .sortedWith(
            compareBy<EpisodeItem> { it.season }
                .thenBy { it.episode }
        )
}

private fun preferredEpisodeTitle(
    current: String,
    candidate: String,
    episodeNumber: Int,
): String {
    val currentIsGeneric =
        isGenericEpisodeTitle(current, episodeNumber)
    val candidateIsGeneric =
        isGenericEpisodeTitle(candidate, episodeNumber)

    return when {
        currentIsGeneric && !candidateIsGeneric -> candidate
        current.isBlank() && candidate.isNotBlank() -> candidate
        else -> current
    }
}

private fun isGenericEpisodeTitle(
    title: String,
    episodeNumber: Int,
): Boolean =
    title.isBlank() ||
        Regex(
            pattern = "^(?:episode|ep|e)\\s*0*$episodeNumber$",
            option = RegexOption.IGNORE_CASE,
        ).matches(title.trim())

private fun richerMetadataText(
    current: String?,
    candidate: String?,
): String? =
    when {
        current.isNullOrBlank() -> candidate?.takeIf { it.isNotBlank() }
        candidate.isNullOrBlank() -> current
        candidate.length > current.length -> candidate
        else -> current
    }

data class AddonStreamProgress(
    val streams: List<StreamSource>,
    val rawCount: Int,
    val completedAddons: Int,
    val totalAddons: Int,
)

object SourceRanker {
    fun comparator(
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ) = PlayerSourcePolicy.comparator(
        preferredQuality = preferredQuality,
        originalLanguage = originalLanguage,
    )
}


object SourceCleaner {
    fun clean(
        sources: List<StreamSource>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): List<StreamSource> {
        val sorted =
            sources.sortedWith(
                SourceRanker.comparator(
                    preferredQuality = preferredQuality,
                    originalLanguage = originalLanguage,
                )
            )

        val seen =
            hashSetOf<String>()

        return sorted.filter { source ->
            seen.add(
                identityKey(source)
            )
        }
    }

    fun qualityBucket(
        source: StreamSource,
    ): String {
        val value =
            (
                source.quality.orEmpty() +
                " " +
                source.name
            ).lowercase()

        return when {
            "2160" in value ||
                "4k" in value ||
                "uhd" in value ->
                "4K"

            "1080" in value ->
                "1080p"

            "720" in value ->
                "720p"

            else ->
                "Other"
        }
    }

    private fun identityKey(
        source: StreamSource,
    ): String =
        when {
            !source.url.isNullOrBlank() ->
                "url:" +
                    source.url
                        .trim()

            !source.infoHash.isNullOrBlank() ->
                "torrent:" +
                    source.infoHash
                        .lowercase() +
                    ":" +
                    (
                        source.fileIndex
                            ?: -1
                    )

            else ->
                listOf(
                    "fallback",
                    source.providerId,
                    source.name,
                    source.quality,
                ).joinToString("|")
        }
}
