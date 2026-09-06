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

internal sealed interface TvHomeEntry {
    val key: String
    val media: MediaItem

    data class Media(
        override val key: String,
        override val media: MediaItem,
    ) : TvHomeEntry

    data class Resume(
        override val key: String,
        override val media: MediaItem,
        val playback: LibraryPlaybackEntry,
    ) : TvHomeEntry
}

internal data class TvHomeRow(
    val key: String,
    val title: String,
    val entries: List<TvHomeEntry>,
    val style: TvHomeRowStyle,
)

internal enum class TvHomeRowStyle {
    CONTINUE,
    POSTER,
}

internal object TvHomeMemory {
    var activeRowKey: String? = null
    val itemIndexByRow = mutableMapOf<String, Int>()
}

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
            if (fresh.isNotEmpty()) catalogRows = fresh
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
                    TvHomeRow(
                        key = "continue",
                        title = "Continue Watching",
                        style = TvHomeRowStyle.CONTINUE,
                        entries = continueWatching.map { playback ->
                            TvHomeEntry.Resume(
                                key = "continue:${playback.mediaKey}",
                                media = playback.media,
                                playback = playback,
                            )
                        },
                    )
                )
            }

            if (watchlist.isNotEmpty()) {
                add(
                    TvHomeRow(
                        key = "my-list",
                        title = "My List",
                        style = TvHomeRowStyle.POSTER,
                        entries = watchlist.map { media ->
                            TvHomeEntry.Media(
                                key = "my-list:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            catalogRows.forEach { catalog ->
                if (catalog.items.isEmpty()) return@forEach
                add(
                    TvHomeRow(
                        key = "catalog:${catalog.id}",
                        title = catalog.title,
                        style = TvHomeRowStyle.POSTER,
                        entries = catalog.items.mapIndexed { index, media ->
                            TvHomeEntry.Media(
                                key = "catalog:${catalog.id}:$index:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }
        }
    }

    TvHomePresentation(
        rows = rows,
        loading = loading,
        error = error,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onOpenEntry = { entry ->
            when (entry) {
                is TvHomeEntry.Resume -> onResume(entry.playback)
                is TvHomeEntry.Media -> onOpenMedia(entry.media)
            }
        },
    )
}
