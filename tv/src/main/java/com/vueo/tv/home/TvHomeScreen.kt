package com.vueo.tv.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.vueo.shared.core.home.HomeRecommendationPolicy
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar

/**
 * Home's VUEO boundary.
 *
 * Runtime/data/routing stay VUEO. Everything below this boundary is the new
 * Home presentation and focus system. The shared sidebar is intentionally
 * left untouched in 32B because sidebar redesign is being handled separately.
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
    var catalogRows by remember { mutableStateOf(runtime.cachedHomeRows()) }
    var loading by remember { mutableStateOf(catalogRows.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime, refreshToken) {
        loading = catalogRows.isEmpty()
        error = null

        runCatching { runtime.homeRows(forceRefresh = false) }
            .onSuccess { rows -> if (rows.isNotEmpty()) catalogRows = rows }
            .onFailure { failure ->
                if (catalogRows.isEmpty()) error = failure.message ?: "Unable to load Home"
            }

        loading = catalogRows.isEmpty() && !runtime.isHomeCatalogRuntimeReady()
    }

    val continueWatching = remember(refreshToken) {
        runtime.libraryStore.continueWatching().take(12)
    }
    val watchHistory = remember(refreshToken) {
        runtime.libraryStore.history()
    }
    val activeProfileId = remember(refreshToken) {
        runtime.profileStore.activeProfileId()
    }
    val personalizedHomeEnabled = remember(refreshToken, activeProfileId) {
        runtime.dnaPreferences.shouldPersonalizeRecommendations(activeProfileId)
    }
    val homeRecommendations = remember(
        catalogRows,
        watchHistory,
        activeProfileId,
        personalizedHomeEnabled,
        refreshToken,
    ) {
        HomeRecommendationPolicy.build(
            catalogRows = catalogRows,
            watchHistory = watchHistory,
            dnaEngine = runtime.dnaEngine,
            personalizationEnabled = personalizedHomeEnabled,
            limit = 12,
        )
    }

    val rows = remember(catalogRows, continueWatching, homeRecommendations) {
        buildList {
            if (continueWatching.isNotEmpty()) {
                add(
                    TvHomeRow(
                        key = "continue-watching",
                        title = "Continue Watching",
                        kind = TvHomeRowKind.CONTINUE_WATCHING,
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

            if (homeRecommendations.forYou.size >= 4) {
                add(
                    TvHomeRow(
                        key = "for-you",
                        title = "For You",
                        kind = TvHomeRowKind.POSTERS,
                        entries = homeRecommendations.forYou.map { media ->
                            TvHomeEntry.Media(
                                key = "for-you:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            val becauseSeed = homeRecommendations.becauseYouWatchedSeed
            if (becauseSeed != null && homeRecommendations.becauseYouWatched.size >= 4) {
                add(
                    TvHomeRow(
                        key = "because-you-watched",
                        title = "Because You Watched ${becauseSeed.name}",
                        kind = TvHomeRowKind.POSTERS,
                        entries = homeRecommendations.becauseYouWatched.map { media ->
                            TvHomeEntry.Media(
                                key = "because:${media.type}:${media.id}",
                                media = media,
                            )
                        },
                    )
                )
            }

            catalogRows.forEach { row ->
                if (row.items.isNotEmpty()) {
                    add(
                        TvHomeRow(
                            key = "catalog:${row.id}",
                            title = row.title,
                            kind = TvHomeRowKind.POSTERS,
                            entries = row.items.mapIndexed { index, media ->
                                TvHomeEntry.Media(
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

    val contentFocusRequester = remember { FocusRequester() }

    // Sidebar is held as-is for now. New Home content only interacts with it
    // through this one focus boundary.
    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    var navExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(rows.isEmpty(), loading, error) {
        if (rows.isEmpty() && !loading) {
            navExpanded = true
            runCatching { navRequesters.getValue("Home").requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvHomePresentation(
            rows = rows,
            loading = loading,
            error = error,
            contentFocusRequester = contentFocusRequester,
            onContentFocused = { navExpanded = false },
            onOpen = { entry -> entry.open(onOpenMedia, onResume) },
            modifier = Modifier.fillMaxSize(),
        )

        TvSidebar(
            selected = "Home",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                navExpanded = false
                runCatching { contentFocusRequester.requestFocus() }.isSuccess
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}
