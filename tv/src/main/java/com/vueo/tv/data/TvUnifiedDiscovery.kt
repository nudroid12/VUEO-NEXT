package com.vueo.tv.data

import android.content.Context
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.extensions.MediaBrowseKind
import com.vueo.shared.core.extensions.StremioAddonExtension
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.content.TvContentManagerStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thin TV adapter over the canonical Shared Core UnifiedMediaEngine.
 *
 * TV owns only installed/enabled state and TV-facing result mapping. Catalog
 * discovery, browse, search, Stremio transport and dedupe all live in shared:core.
 */
class TvUnifiedDiscovery(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentStore = TvContentManagerStore(appContext)

    suspend fun catalogRows(
        maxRows: Int = MAX_HOME_ROWS,
        maxItemsPerRow: Int = MAX_ITEMS_PER_ROW,
    ): List<TvCatalogRow> {
        val engine = syncedEngine()
        return engine.loadCatalogRows(
            maxRows = maxRows,
            forceRefresh = false,
            catalogOrder = contentStore.catalogOrder(),
        )
            .map { row ->
                TvCatalogRow(
                    id = row.id,
                    title = row.title,
                    providerName = row.providerName,
                    items = row.items.take(maxItemsPerRow),
                )
            }
            .filter { it.items.isNotEmpty() }
    }

    suspend fun browse(kind: TvBrowseKind): List<TvMediaItem> =
        syncedEngine()
            .browse(
                kind = kind.toSharedBrowseKind(),
                maxCatalogs = MAX_BROWSE_CATALOGS,
                maxResults = MAX_BROWSE_ITEMS,
                catalogOrder = contentStore.catalogOrder(),
            )

    suspend fun loadMeta(item: TvMediaItem): TvMediaItem =
        syncedEngine().loadMeta(item)

    suspend fun hasActorSearchSource(): Boolean =
        syncedEngine().hasActorSearchAddons()

    suspend fun searchActor(
        query: String,
    ): List<TvDiscoverySearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val engine = syncedEngine()
        val providerNames = engine.installed()
            .associate { extension ->
                extension.descriptor.id to extension.descriptor.name
            }

        return engine.searchActor(
            query = cleanQuery,
            maxCatalogs = MAX_SEARCH_CATALOGS,
            maxResults = MAX_SEARCH_RESULTS,
        )
            .map { media ->
                TvDiscoverySearchResult(
                    media = media,
                    providerName = media.catalogSources.firstOrNull()
                        ?: media.sourceExtensionId?.let(providerNames::get)
                        ?: "Content Manager",
                )
            }
            .take(MAX_SEARCH_RESULTS)
    }

    suspend fun search(
        query: String,
        type: TvDiscoverySearchType,
    ): List<TvDiscoverySearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val engine = syncedEngine()
        val providerNames = engine.installed()
            .associate { extension ->
                extension.descriptor.id to extension.descriptor.name
            }

        return engine.search(
            query = cleanQuery,
            maxCatalogs = MAX_SEARCH_CATALOGS,
            maxResults = MAX_SEARCH_RESULTS,
        )
            .asSequence()
            .filter { media -> type.accepts(media) }
            .map { media ->
                TvDiscoverySearchResult(
                    media = media,
                    providerName = media.catalogSources.firstOrNull()
                        ?: media.sourceExtensionId?.let(providerNames::get)
                        ?: "Content Manager",
                )
            }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    }

    private suspend fun syncedEngine(): UnifiedMediaEngine {
        val revision = contentStore.discoveryRevision()
        val now = System.currentTimeMillis()

        return ENGINE_MUTEX.withLock {
            val needsSync =
                sharedEngine == null ||
                    syncedRevision != revision ||
                    now - syncedAtEpochMs !in 0 until ENGINE_SYNC_TTL_MS

            if (needsSync) {
                val next = UnifiedMediaEngine()

                contentStore.addonInstallations().forEach { installation ->
                    runCatching {
                        StremioAddonExtension.fromManifestUrl(
                            installation.manifestUrl
                        )
                    }.onSuccess { extension ->
                        next.install(extension)
                        next.setExtensionEnabled(
                            id = extension.descriptor.id,
                            enabled = installation.enabled,
                        )
                    }
                }

                if (syncedRevision != revision) {
                    CatalogDiscoveryCache.clearMemory()
                }

                sharedEngine = next
                syncedRevision = revision
                syncedAtEpochMs = now
            }

            sharedEngine ?: UnifiedMediaEngine().also {
                sharedEngine = it
            }
        }
    }

    companion object {
        private const val MAX_HOME_ROWS = 12
        private const val MAX_ITEMS_PER_ROW = 24
        private const val MAX_BROWSE_CATALOGS = 10
        private const val MAX_BROWSE_ITEMS = 80
        private const val MAX_SEARCH_CATALOGS = 12
        private const val MAX_SEARCH_RESULTS = 100
        private const val ENGINE_SYNC_TTL_MS = 10L * 60L * 1000L

        private val ENGINE_MUTEX = Mutex()
        @Volatile private var sharedEngine: UnifiedMediaEngine? = null
        @Volatile private var syncedRevision: Int = Int.MIN_VALUE
        @Volatile private var syncedAtEpochMs: Long = 0L
    }
}

enum class TvDiscoverySearchType {
    ALL,
    MOVIE,
    SERIES,
}

data class TvDiscoverySearchResult(
    val media: TvMediaItem,
    val providerName: String,
)

private fun TvBrowseKind.toSharedBrowseKind(): MediaBrowseKind =
    when (this) {
        TvBrowseKind.MOVIE -> MediaBrowseKind.MOVIE
        TvBrowseKind.SERIES -> MediaBrowseKind.SERIES
        TvBrowseKind.ANIME -> MediaBrowseKind.ANIME
    }

private fun TvDiscoverySearchType.accepts(media: MediaItem): Boolean {
    val type = media.type.trim().lowercase()
    return when (this) {
        TvDiscoverySearchType.ALL -> type == "movie" || type == "series" || type == "tv"
        TvDiscoverySearchType.MOVIE -> type == "movie"
        TvDiscoverySearchType.SERIES -> type == "series" || type == "tv"
    }
}
