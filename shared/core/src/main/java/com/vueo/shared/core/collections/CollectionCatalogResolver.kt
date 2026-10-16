/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from Nuvio CollectionCatalogResolver and FolderDetailRepository.
 * VUEO uses exact installation IDs and never falls through to a different addon.
 */
package com.vueo.shared.core.collections

import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class AvailableCollectionCatalog(val source: CollectionCatalogSource, val title: String, val addonName: String, val genres: List<String>, val requiresGenre: Boolean)
data class CollectionCatalogPage(val items: List<MediaItem>, val nextSkip: Int?, val title: String, val itemKeys: Set<String>)

object CollectionCatalogResolver {
    fun available(engine: UnifiedMediaEngine): List<AvailableCollectionCatalog> = engine.stremioAddons()
        .filter { engine.isExtensionEnabled(it.descriptor.installationId) }
        .flatMap { addon -> addon.descriptor.catalogs.filter { c -> c.extras.none { it.isRequired && !it.name.equals("genre", true) } }.map { c ->
            AvailableCollectionCatalog(CollectionCatalogSource(addon.descriptor.installationId, c.type, c.id), c.name ?: c.id,
                addon.descriptor.name, c.extras.firstOrNull { it.name.equals("genre", true) }?.options.orEmpty(),
                c.extras.any { it.name.equals("genre", true) && it.isRequired })
        } }

    suspend fun load(engine: UnifiedMediaEngine, source: CollectionCatalogSource, skip: Int = 0): CollectionCatalogPage = withContext(Dispatchers.IO) {
        val addon = engine.stremioAddons().singleOrNull { it.descriptor.installationId == source.addonId }
            ?: error("This catalog's addon is not installed. Edit the folder to choose a source.")
        check(engine.isExtensionEnabled(source.addonId)) { "This addon is off. Enable it in Content Manager." }
        val catalog = addon.descriptor.catalogs.firstOrNull { it.id == source.catalogId && it.type == source.type }
            ?: error("This catalog is no longer provided by the addon.")
        val extras = buildMap {
            source.genre?.let { put("genre", it) }
            if (skip > 0) put("skip", skip.toString())
        }
        catalog.extras.filter { it.isRequired }.forEach { check(extras[it.name]?.isNotBlank() == true) { "Catalog requires ${it.name}. Edit this source." } }
        val page = withTimeout(25_000L) { addon.catalog(source.type, source.catalogId, extras) }
        // Keep source identity for Details; never infer identity from a displayed label.
        val items = page.items.map { item -> item.copy(sourceExtensionId = source.addonId) }
        CollectionCatalogPage(engine.filterVisible(items), if (page.items.isNotEmpty() && catalog.extras.any { it.name == "skip" }) skip + page.items.size else null, catalog.name ?: catalog.id, page.items.map { "${it.type}:${it.id}" }.toSet())
    }
}
