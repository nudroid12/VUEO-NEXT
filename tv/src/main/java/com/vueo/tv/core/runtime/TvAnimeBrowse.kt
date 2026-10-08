package com.vueo.tv.core

import com.vueo.shared.core.extensions.CatalogDescriptor
import com.vueo.shared.core.extensions.MediaExtension
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.search.SearchPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private data class TvAnimeCatalog(
    val extension: MediaExtension,
    val catalog: CatalogDescriptor,
    val extras: Map<String, String>,
) {
    val key: String get() = "${extension.descriptor.id}:${catalog.type}:${catalog.id}"
}

/** TV-only Anime browsing: publish completed catalogs without waiting for the slowest. */
internal suspend fun TvRuntime.browseAnimeProgressive(
    onPartial: (List<MediaItem>) -> Unit,
): List<MediaItem> = coroutineScope {
    val updates = Channel<List<MediaItem>>(Channel.CONFLATED)
    // Deliver state changes on the caller's context, not a catalog worker.
    val publisher = launch { for (items in updates) onPartial(items) }
    try {
        withContext(Dispatchers.Default) {
            val order = content.catalogOrder().withIndex().associate { it.value to it.index }
            val bindings = engine.activeStremioAddons().flatMap { extension ->
                extension.descriptor.catalogs.mapNotNull { catalog ->
                    tvAnimeExtras(catalog)?.let { TvAnimeCatalog(extension, catalog, it) }
                }
            }.distinctBy { it.key + it.extras.toString() }.sortedWith(
                compareBy<TvAnimeCatalog> { order[it.key] ?: Int.MAX_VALUE }
                    .thenBy { it.extension.descriptor.name.lowercase() }
                    .thenBy { it.catalog.name?.lowercase().orEmpty() },
            ).take(10)
            val pages = arrayOfNulls<List<MediaItem>>(bindings.size)
            val mutex = Mutex()
            var result = emptyList<MediaItem>()
            bindings.mapIndexed { index, binding ->
                async {
                    val items = try {
                        withTimeoutOrNull(8_000L) {
                            binding.extension.catalog(binding.catalog.type, binding.catalog.id, binding.extras)
                                .items.filter { it.type.trim().lowercase() in setOf("movie", "series", "tv") }
                                .map { item -> item.copy(catalogSources =
                                    (item.catalogSources + binding.extension.descriptor.name)
                                        .distinctBy { it.lowercase() }) }
                        }.orEmpty()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (items.isNotEmpty()) mutex.withLock {
                        pages[index] = items
                        result = tvMergeAnime(pages.filterNotNull().flatten()).take(80)
                        updates.trySend(result)
                    }
                }
            }.awaitAll()
            result
        }
    } finally {
        updates.close()
        publisher.join()
    }
}

// Match the shared browse selection rules without changing the shared engine.
private fun tvAnimeExtras(catalog: CatalogDescriptor): Map<String, String>? {
    if (catalog.type.trim().lowercase() !in setOf("movie", "series", "tv")) return null
    val explicit = catalog.id.contains("anime", true) || catalog.name?.contains("anime", true) == true
    if (explicit && catalog.canLoadWithoutExtras) return emptyMap()
    val genre = catalog.extras.firstOrNull { extra ->
        extra.name.equals("genre", true) &&
            (extra.options.isEmpty() || extra.options.any { it.equals("Anime", true) })
    }
    return if (genre != null && catalog.extras.filter { it.isRequired }.all { it.name.equals("genre", true) }) {
        mapOf("genre" to "Anime")
    } else null
}

private class TvAnimeGroup(val year: Int, val items: MutableList<MediaItem>)

// Index canonical titles once per item instead of rescanning every existing title.
private fun tvMergeAnime(items: List<MediaItem>): List<MediaItem> {
    val buckets = mutableMapOf<Pair<String, String>, MutableList<TvAnimeGroup>>()
    val groups = mutableListOf<TvAnimeGroup>()
    items.forEach { item ->
        val title = SearchPolicy.canonicalTitle(item)
        val year = SearchPolicy.releaseYear(item)
        val bucket = buckets.getOrPut(title to SearchPolicy.canonicalType(item.type)) { mutableListOf() }
        val group = if (title.isBlank()) null else bucket.firstOrNull {
            year == 0 || it.year == 0 || kotlin.math.abs(year - it.year) <= 1
        }
        if (group != null) group.items += item else {
            val created = TvAnimeGroup(year, mutableListOf(item))
            bucket += created
            groups += created
        }
    }
    return groups.map { group ->
        val best = group.items.maxByOrNull(SearchPolicy::metadataScore)!!
        best.copy(
            catalogSources = (best.catalogSources + group.items.flatMap { it.catalogSources })
                .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() },
            genres = group.items.flatMap { it.genres }.distinctBy { it.lowercase() },
            popularity = group.items.mapNotNull { it.popularity }.maxOrNull() ?: best.popularity,
        )
    }
}
