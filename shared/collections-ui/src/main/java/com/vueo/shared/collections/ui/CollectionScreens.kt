/* SPDX-License-Identifier: GPL-3.0-only
 * Collection -> folder -> catalog editor/browser adapted from NuvioMedia
 * CollectionManagementScreen, CollectionEditorScreen and FolderDetailScreen.
 * Reimplemented against VUEO stores/engine and shared Mobile/TV presentation.
 * See THIRD_PARTY_COLLECTIONS.md for upstream commits and licensing.
 */
package com.vueo.shared.collections.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.collections.*
import com.vueo.shared.core.collections.Collection as UserCollection
import com.vueo.shared.core.extensions.UnifiedMediaEngine
import com.vueo.shared.core.media.MediaItem
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Image decoding is supplied by the host's existing size-aware image cache.
typealias CollectionImage = @Composable (String?, String, Modifier) -> Unit
private val Background = Color(0xFF080C0F)
private val Panel = Color(0xFF151B20)

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Message(message: String) { Text(message, color = Color(0xFFD7DBDF), modifier = Modifier.padding(12.dp)) }

@Composable
private fun rememberStore(profileId: String): CollectionStore {
    val context = LocalContext.current.applicationContext
    return remember(context, profileId) { CollectionStore.get(context, profileId) }
}

data class CollectionHomeState(val collections: List<UserCollection>, val error: String? = null)

@Composable
fun rememberCollectionHomeState(profileId: String): CollectionHomeState {
    val store = rememberStore(profileId)
    val collections by store.collections.collectAsState()
    var error by remember(store) { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try { store.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Collections could not be loaded. Open Content Manager → Collections." }
    }
    return CollectionHomeState(collections, error)
}

@Composable
fun CollectionHomeRow(collection: UserCollection, image: CollectionImage, onOpen: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(collection.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(collection.folders, key = { it.id }) { folder ->
                FolderCard(folder, image) { onOpen(collection.id, folder.id) }
            }
        }
    }
}

@Composable
fun FolderCard(folder: CollectionFolder, image: CollectionImage, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Panel,
        modifier = Modifier.width(192.dp).height(110.dp).onFocusChanged { focused = it.hasFocus }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(14.dp))) {
        Box {
            image(folder.coverImageUrl, folder.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)))
            Text(listOfNotNull(folder.coverEmoji?.takeIf { it.isNotBlank() }, folder.title).joinToString(" "),
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                modifier = Modifier.align(Alignment.Center).padding(10.dp))
        }
    }
}

@Composable
private fun CollectionManagerBody(
    engine: UnifiedMediaEngine,
    profileId: String,
    onBack: () -> Unit,
    image: CollectionImage,
) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val store = rememberStore(profileId)
    val collections by store.collections.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember(store) { mutableStateOf<UserCollection?>(null) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    var ready by remember(store) { mutableStateOf(false) }
    var busy by remember(store) { mutableStateOf(false) }
    var deleteId by remember(store) { mutableStateOf<String?>(null) }
    var importText by remember(store) { mutableStateOf<String?>(null) }
    var importCount by remember(store) { mutableIntStateOf(0) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not save collections" }
            finally { busy = false }
        }
    }
    LaunchedEffect(store) {
        try { store.load(); ready = true } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not load collections" }
    }
    if (draft != null) {
        CollectionEditor(draft!!, engine, image,
            onCancel = { draft = null },
            onSave = { next -> action { store.save(next); draft = null } }, busy = busy, error = error)
        return
    }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Background).statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Collections", onBack) }
        item { Message("Build your own Home rows. Add folders with pictures, then choose the catalogs inside each folder. Saved for this profile.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = ready && !busy, onClick = { draft = UserCollection(UUID.randomUUID().toString(), "") }) { Text("Create") }
                OutlinedButton(enabled = ready && !busy, onClick = {
                    action {
                        val json = withContext(Dispatchers.Default) { CollectionJson.encode(collections) }
                        clipboard.setPrimaryClip(ClipData.newPlainText("VUEO Collections", json))
                        error = "Collections copied"
                    }
                }) { Text("Copy JSON") }
            }
        }
        item {
            OutlinedButton(enabled = ready && !busy, onClick = {
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                action {
                    val incoming = withContext(Dispatchers.Default) { CollectionJson.decode(text) }
                    importCount = incoming.size; importText = text
                }
            }) { Text("Import JSON from clipboard") }
        }
        error?.let { item { Message(it) } }
        if (!ready && error == null) item { CircularProgressIndicator() }
        if (ready && collections.isEmpty()) item { Message("No collections yet. Create one to get started.") }
        items(collections, key = { it.id }) { collection ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(collection.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${collection.folders.size} folders • ${if (collection.enabled) "On" else "Off"}", color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !busy, onClick = { draft = collection }) { Text("Edit") }
                    OutlinedButton(enabled = !busy, onClick = { action { store.toggle(collection.id) } }) { Text(if (collection.enabled) "Turn off" else "Turn on") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = !busy && collections.firstOrNull()?.id != collection.id, onClick = { action { store.move(collection.id, -1) } }) { Text("Move up") }
                    TextButton(enabled = !busy && collections.lastOrNull()?.id != collection.id, onClick = { action { store.move(collection.id, 1) } }) { Text("Move down") }
                    TextButton(enabled = !busy, onClick = { deleteId = collection.id }) { Text("Delete") }
                }
            }
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete collection?") }, text = { Text("This removes the collection setup. Addons, Favorites and watch history are kept.") },
        confirmButton = { TextButton(onClick = { action { store.remove(id); deleteId = null } }, enabled = !busy) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
    importText?.let { text -> AlertDialog(onDismissRequest = { importText = null }, title = { Text("Import $importCount collections?") }, text = { Text("Collections with matching IDs will be updated. Other collections are kept. Sources must already be installed in VUEO.") },
        confirmButton = { TextButton(onClick = { action { store.merge(text); importText = null } }, enabled = !busy) { Text("Import") } },
        dismissButton = { TextButton(onClick = { importText = null }) { Text("Cancel") } }) }
}

@Composable
private fun CollectionEditor(initial: UserCollection, engine: UnifiedMediaEngine, image: CollectionImage, onCancel: () -> Unit, onSave: (UserCollection) -> Unit, busy: Boolean, error: String?) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var folder by remember(initial.id) { mutableStateOf<CollectionFolder?>(null) }
    if (folder != null) {
        FolderEditor(folder!!, engine, image, onCancel = { folder = null }, onSave = { next ->
            draft = draft.copy(folders = if (draft.folders.any { it.id == next.id }) draft.folders.map { if (it.id == next.id) next else it } else draft.folders + next)
            folder = null
        })
        return
    }
    BackHandler(onBack = onCancel)
    LazyColumn(Modifier.fillMaxSize().background(Background).statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Edit collection", onCancel) }
        item { OutlinedTextField(value = draft.title, onValueChange = { draft = draft.copy(title = it) }, label = { Text("Collection name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.viewMode == FolderViewMode.TABBED_GRID, onClick = { draft = draft.copy(viewMode = FolderViewMode.TABBED_GRID) }, label = { Text("Tabs") })
                FilterChip(selected = draft.viewMode == FolderViewMode.ROWS, onClick = { draft = draft.copy(viewMode = FolderViewMode.ROWS) }, label = { Text("Rows") })
                FilterChip(selected = draft.showAllTab, onClick = { draft = draft.copy(showAllTab = !draft.showAllTab) }, label = { Text("All tab") })
            }
        }
        items(draft.folders, key = { it.id }) { f ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                FolderCard(f, image) { folder = f }
                Row {
                    TextButton(onClick = { draft = draft.copy(folders = CollectionPolicy.move(draft.folders, draft.folders.indexOf(f), draft.folders.indexOf(f) - 1)) }) { Text("Up") }
                    TextButton(onClick = { draft = draft.copy(folders = CollectionPolicy.move(draft.folders, draft.folders.indexOf(f), draft.folders.indexOf(f) + 1)) }) { Text("Down") }
                    TextButton(onClick = { draft = draft.copy(folders = draft.folders.filterNot { it.id == f.id }) }) { Text("Remove") }
                }
            }
        }
        item { OutlinedButton(onClick = { folder = CollectionFolder(UUID.randomUUID().toString(), "") }) { Text("Add folder") } }
        error?.let { item { Message(it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !busy && draft.title.isNotBlank(), onClick = { onSave(draft.copy(title = draft.title.trim())) }) { Text("Save changes") }
                TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun FolderEditor(initial: CollectionFolder, engine: UnifiedMediaEngine, image: CollectionImage, onCancel: () -> Unit, onSave: (CollectionFolder) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        CatalogPicker(engine, draft.catalogSources, onBack = { picking = false }, onPick = { source ->
            if (source !in draft.catalogSources) draft = draft.copy(catalogSources = draft.catalogSources + source)
            picking = false
        })
        return
    }
    BackHandler(onBack = onCancel)
    val validImage = draft.coverImageUrl.isNullOrBlank() || CollectionPolicy.validImageUrl(draft.coverImageUrl!!)
    LazyColumn(Modifier.fillMaxSize().background(Background).statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Edit folder", onCancel) }
        item { OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("Folder name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(draft.coverImageUrl.orEmpty(), { draft = draft.copy(coverImageUrl = it.trim().takeIf(String::isNotEmpty)) }, label = { Text("Cover image URL (HTTPS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = !validImage) }
        item { OutlinedTextField(draft.coverEmoji.orEmpty(), { draft = draft.copy(coverEmoji = it) }, label = { Text("Emoji (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { FolderCard(draft, image) {} }
        items(draft.catalogSources, key = { "${it.addonId}/${it.type}/${it.catalogId}/${it.genre}" }) { source ->
            val catalog = CollectionCatalogResolver.available(engine).find { it.source == source.copy(genre = null) }
            Column(Modifier.fillMaxWidth().background(Panel).padding(10.dp)) {
                Text("${catalog?.title ?: source.catalogId} • ${source.type}${source.genre?.let { " • $it" }.orEmpty()}", color = Color.White)
                Text(catalog?.addonName ?: "Addon unavailable — choose a replacement", color = Color.LightGray)
                Row {
                    TextButton(onClick = { val i = draft.catalogSources.indexOf(source); draft = draft.copy(catalogSources = CollectionPolicy.move(draft.catalogSources, i, i - 1)) }) { Text("Up") }
                    TextButton(onClick = { val i = draft.catalogSources.indexOf(source); draft = draft.copy(catalogSources = CollectionPolicy.move(draft.catalogSources, i, i + 1)) }) { Text("Down") }
                    TextButton(onClick = { draft = draft.copy(catalogSources = draft.catalogSources - source) }) { Text("Remove") }
                }
            }
        }
        item { OutlinedButton(onClick = { picking = true }) { Text("Add catalog source") } }
        item {
            Row {
                Button(enabled = draft.title.isNotBlank() && validImage, onClick = { onSave(draft.copy(title = draft.title.trim())) }) { Text("Save folder") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CatalogPicker(engine: UnifiedMediaEngine, selected: List<CollectionCatalogSource>, onBack: () -> Unit, onPick: (CollectionCatalogSource) -> Unit) {
    var query by remember { mutableStateOf("") }
    val catalogs = CollectionCatalogResolver.available(engine)
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Background).statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("Choose catalog", onBack) }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search installed catalogs") }, modifier = Modifier.fillMaxWidth()) }
        if (catalogs.isEmpty()) item { Message("No supported catalogs available. Install or enable a catalog addon first.") }
        items(catalogs.filter { "${it.title} ${it.addonName} ${it.source.type}".contains(query, true) }, key = { "${it.source.addonId}/${it.source.type}/${it.source.catalogId}" }) { catalog ->
            Column(Modifier.fillMaxWidth().background(Panel).padding(12.dp)) {
                Text("${catalog.title} • ${catalog.source.type}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(catalog.addonName, color = Color.LightGray)
                if (!catalog.requiresGenre) TextButton(enabled = catalog.source !in selected, onClick = { onPick(catalog.source) }) { Text("Add catalog") }
                if (catalog.genres.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(catalog.genres) { genre -> val source = catalog.source.copy(genre = genre)
                        OutlinedButton(enabled = source !in selected, onClick = { onPick(source) }) { Text(genre) }
                    }
                }
                if (catalog.requiresGenre && catalog.genres.isEmpty()) Message("This catalog needs a genre but provides no choices.")
            }
        }
    }
}

private data class SourceLoadState(val items: List<MediaItem> = emptyList(), val nextSkip: Int? = 0, val loading: Boolean = false, val error: String? = null, val seen: Set<String> = emptySet())

@Composable
private fun CollectionFolderBody(
    engine: UnifiedMediaEngine,
    profileId: String,
    collectionId: String,
    folderId: String,
    contentRevision: Int,
    active: Boolean = true,
    image: CollectionImage,
    onBack: () -> Unit,
    onMedia: (MediaItem) -> Unit,
) {
    val store = rememberStore(profileId)
    val collections by store.collections.collectAsState()
    var storeError by remember(store) { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try { store.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { storeError = failure.message ?: "Could not load collections" }
    }
    val collection = collections.find { it.id == collectionId }
    val folder = collection?.folders?.find { it.id == folderId }
    val sources = folder?.catalogSources.orEmpty()
    var tab by rememberSaveable(profileId, collectionId, folderId) { mutableIntStateOf(-1) }
    val hasAll = collection?.showAllTab == true
    val selected = if (tab in sources.indices) tab else if (hasAll) -1 else 0
    val states = remember(profileId, folderId, sources, contentRevision) { mutableStateMapOf<CollectionCatalogSource, SourceLoadState>() }
    val scope = rememberCoroutineScope()
    fun load(source: CollectionCatalogSource, reset: Boolean = false) {
        val old = states[source] ?: SourceLoadState()
        if (old.loading) return
        val skip = if (reset) 0 else old.nextSkip ?: return
        states[source] = old.copy(loading = true, error = null)
        scope.launch {
            try {
                val page = CollectionCatalogResolver.load(engine, source, skip)
                val next = ((if (reset) emptyList() else old.items) + page.items).distinctBy { "${it.type}:${it.id}" }
                states[source] = SourceLoadState(next, page.nextSkip?.takeIf { reset || page.itemKeys.any { it !in old.seen } }, seen = (if (reset) emptySet() else old.seen) + page.itemKeys)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { states[source] = old.copy(loading = false, error = failure.message ?: "Catalog could not be loaded") }
        }
    }
    LaunchedEffect(sources, contentRevision, selected, collection?.viewMode, active) {
        if (!active) return@LaunchedEffect
        val needed = if (collection?.viewMode == FolderViewMode.ROWS || selected == -1) sources else listOfNotNull(sources.getOrNull(selected))
        // At most two catalogs fetch concurrently; no Home request is made just
        // to display a collection cover.
        kotlinx.coroutines.coroutineScope {
            val semaphore = kotlinx.coroutines.sync.Semaphore(2)
            needed.filter { it !in states }.forEach { source -> launch {
                semaphore.acquire()
                try {
                    states[source] = SourceLoadState(loading = true)
                    try {
                        val page = CollectionCatalogResolver.load(engine, source)
                        states[source] = SourceLoadState(page.items, page.nextSkip, seen = page.itemKeys)
                    } catch (cancelled: CancellationException) { states.remove(source); throw cancelled }
                    catch (failure: Exception) { states[source] = SourceLoadState(error = failure.message ?: "Catalog could not be loaded") }
                } finally { semaphore.release() }
            } }
        }
    }
    BackHandler(enabled = active, onBack = onBack)
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().padding(horizontal = 16.dp).focusProperties { canFocus = active }) {
        Header(folder?.title ?: "Collection", onBack)
        if (storeError != null) { Message(storeError!!); return@Column }
        if (collection == null || folder == null) {
            Message("This folder is unavailable. Return to Collections to edit it."); return@Column
        }
        if (sources.isEmpty()) { Message("This folder has no catalogs yet. Add sources in Content Manager → Collections."); return@Column }
        val available = remember(contentRevision) { CollectionCatalogResolver.available(engine) }
        fun title(source: CollectionCatalogSource): String = available.find { it.source == source.copy(genre = null) }?.let {
            "${it.title} (${source.type})${source.genre?.let { genre -> " • $genre" }.orEmpty()}"
        } ?: "${source.catalogId} (${source.type})"
        if (collection.viewMode == FolderViewMode.ROWS) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                items(sources, key = { "${it.addonId}/${it.type}/${it.catalogId}/${it.genre}" }) { source ->
                    val state = states[source] ?: SourceLoadState(loading = true)
                    Column {
                        Text(title(source), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        SourceStatus(state, { load(source, reset = true) }, { load(source) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(engine.filterVisible(state.items), key = { "${it.type}:${it.id}" }) { item -> MediaCard(item, image) { onMedia(item) } }
                        }
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasAll) item { FilterChip(selected = selected == -1, onClick = { tab = -1 }, label = { Text("All") }) }
                items(sources.indices.toList()) { index -> FilterChip(selected = selected == index, onClick = { tab = index }, label = { Text(title(sources[index])) }) }
            }
            val shown = if (selected == -1) sources else listOfNotNull(sources.getOrNull(selected))
            LazyRow { items(shown, key = { "${it.addonId}/${it.type}/${it.catalogId}/${it.genre}" }) { source ->
                Column { Text(title(source), color = Color.LightGray, fontSize = 12.sp); SourceStatus(states[source] ?: SourceLoadState(loading = true), { load(source, true) }, { load(source) }) }
            } }
            val visible = engine.filterVisible(shown.flatMap { states[it]?.items.orEmpty() }).distinctBy { "${it.type}:${it.id}" }
            if (visible.isEmpty() && shown.all { states[it]?.loading == false && states[it]?.error == null }) Message("No titles returned by these catalogs.")
            LazyVerticalGrid(columns = GridCells.Adaptive(125.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                gridItems(visible, key = { "${it.type}:${it.id}" }) { item -> MediaCard(item, image) { onMedia(item) } }
            }
        }
    }
}

@Composable
private fun SourceStatus(state: SourceLoadState, retry: () -> Unit, more: () -> Unit) {
    when {
        state.loading -> CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp))
        state.error != null -> Column { Message(state.error); TextButton(onClick = retry) { Text("Retry") } }
        state.nextSkip != null -> TextButton(onClick = more) { Text("Load more") }
        state.items.isEmpty() -> Message("No titles returned")
    }
}

@Composable
private fun MediaCard(item: MediaItem, image: CollectionImage, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Panel,
        modifier = Modifier.width(130.dp).onFocusChanged { focused = it.hasFocus }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))) {
        Column {
            image(item.poster ?: item.background, item.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Text(item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun CollectionManagerScreen(engine: UnifiedMediaEngine, profileId: String, onBack: () -> Unit, image: CollectionImage) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB2FF00), background = Background, surface = Panel)) {
        CollectionManagerBody(engine, profileId, onBack, image)
    }
}

@Composable
fun CollectionFolderScreen(engine: UnifiedMediaEngine, profileId: String, collectionId: String, folderId: String,
    contentRevision: Int, active: Boolean = true, image: CollectionImage, onBack: () -> Unit, onMedia: (MediaItem) -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB2FF00), background = Background, surface = Panel)) {
        CollectionFolderBody(engine, profileId, collectionId, folderId, contentRevision, active, image, onBack, onMedia)
    }
}
