package com.vueo.tv.home

import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import com.vueo.shared.core.collections.CollectionCatalogResolver
import com.vueo.shared.core.collections.CollectionCatalogSource
import com.vueo.shared.core.extensions.AddonInstallationIdentity
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.profile.KidsContentPolicy
import com.vueo.tv.core.TvRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

internal fun homePagedCatalogs(runtime: TvRuntime): Map<String, CollectionCatalogSource> =
    runtime.engine.activeStremioAddons().flatMap { addon ->
        val id = addon.descriptor.installationId
        addon.descriptor.catalogs.filter { catalog ->
            catalog.extras.any { it.name == "skip" } &&
                catalog.extras.none { it.isRequired }
        }.map { catalog ->
            "catalog:${AddonInstallationIdentity.catalogKey(id, catalog.type, catalog.id)}" to
                CollectionCatalogSource(id, catalog.type, catalog.id)
        }
    }.toMap()

internal class TvHomeCatalogPagination(
    private val profileId: String? = null,
    private val refreshToken: Int = Int.MIN_VALUE,
    private val kidsLimit: Int? = null,
) {
    private data class PageState(
        val nextSkip: Int? = 0,
        val items: List<MediaItem> = emptyList(),
        val visitedPages: Set<Set<String>> = emptySet(),
    )

    private val pages = mutableStateMapOf<CollectionCatalogSource, PageState>()
    private val loading = mutableMapOf<CollectionCatalogSource, Any>()
    private val retryAfter = mutableMapOf<CollectionCatalogSource, Long>()

    fun matches(profileId: String, refreshToken: Int, kidsLimit: Int?): Boolean =
        this.profileId == profileId && this.refreshToken == refreshToken && this.kidsLimit == kidsLimit

    fun appendTo(row: TvHomeRow, source: CollectionCatalogSource): TvHomeRow {
        val items = pages[source]?.items.orEmpty()
        if (items.isEmpty()) return row
        val existing = row.entries.mapTo(hashSetOf()) { it.media.type to it.media.id }
        val appended = items.filter { existing.add(it.type to it.id) }.map {
            TvHomeEntry.Media(catalogCardKey(row.key, it), it)
        }
        return if (appended.isEmpty()) row else row.copy(entries = row.entries + appended)
    }

    fun reset(source: CollectionCatalogSource) {
        pages.remove(source)
        loading.remove(source)
        retryAfter.remove(source)
    }

    fun prepend(source: CollectionCatalogSource, count: Int, canShiftCursor: Boolean) {
        val previous = pages[source] ?: return
        pages[source] = if (canShiftCursor) {
            previous.copy(nextSkip = previous.nextSkip?.let { if (it > 0) it + count else it })
        } else {
            PageState(items = previous.items)
        }
        // An in-flight request still uses the previous cursor.
        loading.remove(source)
        retryAfter.remove(source)
    }

    fun request(
        scope: CoroutineScope,
        runtime: TvRuntime,
        row: TvHomeRow,
        source: CollectionCatalogSource,
    ): Boolean {
        val initial = pages[source] ?: PageState()
        if (initial.nextSkip == null) return false
        if (SystemClock.uptimeMillis() < (retryAfter[source] ?: 0L) || source in loading) return true
        val request = Any()
        loading[source] = request

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val existing = row.entries.mapTo(hashSetOf()) { it.media.type to it.media.id }
                initial.items.forEach { existing.add(it.type to it.id) }
                var state = initial
                // Page zero discovers the raw cursor. The displayed row may
                // already contain that page, or hide some of its titles.
                repeat(4) {
                    val skip = state.nextSkip ?: return@launch
                    val page = CollectionCatalogResolver.load(runtime.engine, source, skip)
                    if (loading[source] !== request) return@launch
                    val repeated = page.itemKeys in state.visitedPages
                    val added = if (repeated) emptyList() else page.items.filterTo(mutableListOf()) { item ->
                        (kidsLimit == null || KidsContentPolicy.allows(item.certification, kidsLimit)) &&
                            existing.add(item.type to item.id)
                    }
                    state = PageState(
                        nextSkip = page.nextSkip?.takeIf { !repeated && it > skip },
                        items = state.items + added,
                        visitedPages = state.visitedPages + setOf(page.itemKeys),
                    )
                    pages[source] = state
                    if (added.isNotEmpty() && skip > 0) return@launch
                }
            } catch (_: TimeoutCancellationException) {
                if (loading[source] === request) retryAfter[source] = SystemClock.uptimeMillis() + 1_000L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (loading[source] === request) retryAfter[source] = SystemClock.uptimeMillis() + 1_000L
            } finally {
                if (loading[source] === request) loading.remove(source)
            }
        }
        return true
    }
}
