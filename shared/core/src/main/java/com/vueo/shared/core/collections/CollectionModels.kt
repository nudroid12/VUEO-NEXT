/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NuvioMedia/NuvioTV domain/model/Collection.kt and
 * NuvioMedia/NuvioMobile features/collection/CollectionModels.kt.
 * VUEO adaptation: scoped installation identities; addon catalog sources.
 */
package com.vueo.shared.core.collections

data class CollectionCatalogSource(
    val addonId: String,
    val type: String,
    val catalogId: String,
    val genre: String? = null,
)

data class CollectionFolder(
    val id: String,
    val title: String,
    val coverImageUrl: String? = null,
    val coverEmoji: String? = null,
    val catalogSources: List<CollectionCatalogSource> = emptyList(),
)

enum class FolderViewMode { TABBED_GRID, ROWS }

data class Collection(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val viewMode: FolderViewMode = FolderViewMode.TABBED_GRID,
    val showAllTab: Boolean = true,
    val folders: List<CollectionFolder> = emptyList(),
)

object CollectionPolicy {
    // Adapted from Nuvio CollectionRepository.moveByIndex; stable IDs keep edits
    // and ordering separate from presentation labels.
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in items.indices || to !in items.indices) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun validate(collections: List<Collection>) {
        require(collections.map { it.id }.distinct().size == collections.size) { "Duplicate collection ID" }
        collections.forEach { collection ->
            require(collection.id.isNotBlank() && collection.title.isNotBlank()) { "Collection name is required" }
            require(collection.folders.map { it.id }.distinct().size == collection.folders.size) { "Duplicate folder ID" }
            collection.folders.forEach { folder ->
                require(folder.id.isNotBlank() && folder.title.isNotBlank()) { "Folder name is required" }
                require(folder.coverImageUrl.isNullOrBlank() || validImageUrl(folder.coverImageUrl)) { "Use a direct HTTPS image URL" }
                require(folder.catalogSources.distinct().size == folder.catalogSources.size) { "Duplicate catalog source" }
                folder.catalogSources.forEach { source ->
                    require(source.addonId.isNotBlank() && source.type.isNotBlank() && source.catalogId.isNotBlank()) { "Invalid catalog source" }
                }
            }
        }
    }

    fun validImageUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value.trim())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
