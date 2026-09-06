package com.vueo.tv.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime

internal sealed interface VueoHomeTile {
    val key: String
    val media: MediaItem

    data class Media(
        override val key: String,
        override val media: MediaItem,
    ) : VueoHomeTile

    data class Resume(
        override val key: String,
        override val media: MediaItem,
        val playback: LibraryPlaybackEntry,
    ) : VueoHomeTile
}

internal data class VueoHomeRow(
    val id: String,
    val title: String,
    val tiles: List<VueoHomeTile>,
    val landscape: Boolean,
)

/**
 * Home data boundary.
 *
 * Presentation intentionally lives outside this file so the 32A rebuild does
 * not inherit the old Home Compose hierarchy. Only VUEO's existing data and
 * navigation contracts are retained here.
 */
@Composable
fun TvHomeScreen(
    runtime: TvRuntime,
    refreshToken: Int,
    onNavigate: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onResume: (LibraryPlaybackEntry) -> Unit,
    onProfile: () -> Unit,
) {
    var catalogRows by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime, refreshToken) {
        loading = catalogRows.isEmpty()
        error = null

        val cached = runCatching {
            runtime.homeRows(forceRefresh = false)
        }.getOrDefault(emptyList())

        if (cached.isNotEmpty()) {
            catalogRows = cached
        }

        runCatching {
            runtime.homeRows(forceRefresh = true)
        }.onSuccess { fresh ->
            if (fresh.isNotEmpty()) {
                catalogRows = fresh
            }
        }.onFailure { failure ->
            if (catalogRows.isEmpty()) {
                error = failure.message ?: "Unable to load Home"
            }
        }

        loading = false
    }

    val continueWatching = remember(refreshToken) {
        runtime.libraryStore.continueWatching()
    }
    val watchlist = remember(refreshToken) {
        runtime.libraryStore.watchlist()
    }

    val rows = remember(catalogRows, continueWatching, watchlist) {
        buildList {
            if (continueWatching.isNotEmpty()) {
                add(
                    VueoHomeRow(
                        id = "continue",
                        title = "Continue Watching",
                        landscape = true,
                        tiles = continueWatching.map { entry ->
                            VueoHomeTile.Resume(
                                key = "continue:${entry.mediaKey}",
                                media = entry.media,
                                playback = entry,
                            )
                        },
                    )
                )
            }

            if (watchlist.isNotEmpty()) {
                add(
                    VueoHomeRow(
                        id = "my-list",
                        title = "My List",
                        landscape = false,
                        tiles = watchlist.map { media ->
                            VueoHomeTile.Media(
                                key = "my-list:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            catalogRows.forEach { row ->
                if (row.items.isNotEmpty()) {
                    add(
                        VueoHomeRow(
                            id = "catalog:${row.id}",
                            title = row.title,
                            landscape = false,
                            tiles = row.items.mapIndexed { index, media ->
                                VueoHomeTile.Media(
                                    key = "${row.id}:$index:${media.type}:${media.id}",
                                    media = media,
                                )
                            },
                        )
                    )
                }
            }
        }
    }

    VueoHomePresentation(
        rows = rows,
        loading = loading,
        error = error,
        onNavigate = onNavigate,
        onOpenTile = { tile ->
            when (tile) {
                is VueoHomeTile.Resume -> onResume(tile.playback)
                is VueoHomeTile.Media -> onOpenMedia(tile.media)
            }
        },
        onProfile = onProfile,
    )
}
