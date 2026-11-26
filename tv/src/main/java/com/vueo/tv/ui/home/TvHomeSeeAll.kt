package com.vueo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vueo.shared.core.collections.CollectionCatalogResolver
import com.vueo.shared.core.collections.CollectionCatalogSource
import com.vueo.shared.core.extensions.AddonInstallationIdentity
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import kotlinx.coroutines.CancellationException
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

@Composable
internal fun TvHomeSeeAll(
    runtime: TvRuntime,
    row: TvHomeRow,
    source: CollectionCatalogSource?,
    onDismiss: () -> Unit,
    onSelect: (TvHomeEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val firstFocus = remember(row.key) { FocusRequester() }
    var extraEntries by remember(row.key) { mutableStateOf<List<TvHomeEntry>>(emptyList()) }
    var seenKeys by remember(row.key) { mutableStateOf(emptySet<String>()) }
    var nextSkip by remember(row.key, source) { mutableStateOf(0.takeIf { source != null }) }
    var loading by remember(row.key) { mutableStateOf(false) }
    var failed by remember(row.key) { mutableStateOf(false) }
    val entries = (row.entries + extraEntries).distinctBy { it.media.type to it.media.id }

    suspend fun loadPage(skip: Int) {
        val catalog = source ?: return
        if (loading) return
        loading = true
        failed = false
        try {
            val page = CollectionCatalogResolver.load(runtime.engine, catalog, skip)
            val newPage = page.itemKeys.any { it !in seenKeys }
            seenKeys = seenKeys + page.itemKeys
            extraEntries = (extraEntries + page.items.map { media ->
                TvHomeEntry.Media("catalog-grid:${media.type}:${media.id}", media)
            }).distinctBy { it.key }
            nextSkip = page.nextSkip?.takeIf { newPage && it > skip }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(row.key, source) {
        if (source != null) loadPage(0)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(firstFocus) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            Modifier.fillMaxWidth(.92f).fillMaxHeight(.88f)
                .background(TvDesign.Surface, RoundedCornerShape(18.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, modifier = Modifier.weight(1f), color = TvDesign.White, fontSize = 24.sp)
                if (source != null && (nextSkip != null || loading || failed)) {
                    Button(
                        onClick = { nextSkip?.let { skip -> scope.launch { loadPage(skip) } } },
                    ) {
                        Text(when {
                            loading -> "Loading…"
                            failed -> "Retry loading"
                            else -> "Load more"
                        })
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onDismiss) { Text("Close") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (row.kind == TvHomeRowKind.CONTINUE_WATCHING) 4 else 6),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    val shape = RoundedCornerShape(8.dp)
                    var focused by remember(entry.key) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .then(if (entry.key == entries.first().key) Modifier.focusRequester(firstFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clip(shape)
                            .border(2.dp, if (focused) TvDesign.Accent else androidx.compose.ui.graphics.Color.Transparent, shape)
                            .clickable { onSelect(entry) }
                            .padding(4.dp),
                    ) {
                        val landscape = row.kind == TvHomeRowKind.CONTINUE_WATCHING
                        TvNetworkImage(
                            url = if (landscape) entry.media.background ?: entry.media.poster else entry.media.poster ?: entry.media.background,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(shape),
                            contentScale = ContentScale.Crop,
                        )
                        Text(entry.media.name, color = TvDesign.White, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}
