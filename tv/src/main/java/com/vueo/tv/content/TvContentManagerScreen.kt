package com.vueo.tv.content

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.vueo.tv.TV_TOP_NAV_LABELS
import com.vueo.tv.TvTopNav
import com.vueo.tv.ui.focus.tvVerticalFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ManagerBlack = Color(0xFF050706)
private val ManagerPanel = Color(0xFF101412)
private val ManagerPanelRaised = Color(0xFF151B17)
private val ManagerGreen = Color(0xFF84E100)
private val ManagerMuted = Color(0xFFAAB2AD)
private val ManagerRed = Color(0xFFFF7A73)

private enum class TvManagerMode {
    STREMIO,
    CATALOGS,
    PROVIDERS,
}

@Composable
fun TvContentManagerScreen(
    store: TvContentManagerStore,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val navRequesters =
        remember {
            TV_TOP_NAV_LABELS
                .associateWith { FocusRequester() }
        }
    val stremioRequester = remember { FocusRequester() }
    val catalogsRequester = remember { FocusRequester() }
    val providersRequester = remember { FocusRequester() }
    val inputRequester = remember { FocusRequester() }
    val installRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(TvManagerMode.STREMIO) }
    var snapshot by remember { mutableStateOf<TvContentSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var catalogOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerHealth by remember { mutableStateOf<TvProviderHealthSnapshot?>(null) }
    var actionKey by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            if (mode == TvManagerMode.PROVIDERS) {
                runCatching { store.refreshRepositories() }
                    .onSuccess { message = it }
                    .onFailure { message = it.message ?: "Unable to refresh repositories" }
            } else {
                store.invalidateDiscovery()
            }
            runCatching { store.snapshot() }
                .onSuccess {
                    snapshot = it
                    providerHealth = store.providerHealthSnapshot()
                    catalogOrder = store.reconcileCatalogOrder(
                        it.addons.flatMap { addon -> addon.catalogs.filter { it.canLoadWithoutExtras }.map { catalog -> catalog.key } }
                    )
                    if (mode != TvManagerMode.PROVIDERS) message = null
                }
                .onFailure {
                    message = it.message ?: "Unable to refresh Content Manager"
                }
            loading = false
        }
    }

    BackHandler { onBack?.invoke() ?: onNavigate("Home") }

    LaunchedEffect(Unit) {
        runCatching { store.snapshot() }
            .onSuccess {
                snapshot = it
                providerHealth = store.providerHealthSnapshot()
                catalogOrder = store.reconcileCatalogOrder(
                    it.addons.flatMap { addon -> addon.catalogs.filter { it.canLoadWithoutExtras }.map { catalog -> catalog.key } }
                )
            }
            .onFailure { message = it.message ?: "Unable to load Content Manager" }
        loading = false
        delay(100)
        runCatching { stremioRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A100C),
                            ManagerBlack,
                            ManagerBlack,
                        )
                    )
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 62.dp, end = 62.dp, top = 100.dp, bottom = 38.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Content Manager",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Manage Stremio Addons and JavaScript Provider Plugins for VUEO TV.",
                        color = ManagerMuted,
                        fontSize = 14.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ManagerModeChip(
                        text = "Stremio Addons",
                        selected = mode == TvManagerMode.STREMIO,
                        requester = stremioRequester,
                        upRequester = navRequesters.getValue("Home"),
                        downRequester = inputRequester,
                        onClick = { mode = TvManagerMode.STREMIO },
                    )
                    ManagerModeChip(
                        text = "Catalog Order",
                        selected = mode == TvManagerMode.CATALOGS,
                        requester = catalogsRequester,
                        upRequester = navRequesters.getValue("Home"),
                        downRequester = refreshRequester,
                        onClick = { mode = TvManagerMode.CATALOGS },
                    )
                    ManagerModeChip(
                        text = "JS Providers",
                        selected = mode == TvManagerMode.PROVIDERS,
                        requester = providersRequester,
                        upRequester = navRequesters.getValue("Home"),
                        downRequester = inputRequester,
                        onClick = { mode = TvManagerMode.PROVIDERS },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (mode == TvManagerMode.CATALOGS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Top catalogs appear first on Home. Disabled addons keep their saved position.",
                        color = ManagerMuted,
                        fontSize = 13.sp,
                    )
                    Button(
                        onClick = ::refresh,
                        modifier =
                            Modifier
                                .focusRequester(refreshRequester)
                                .tvVerticalFocus(up = catalogsRequester),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManagerPanelRaised, contentColor = Color.White),
                    ) {
                        Text("Refresh Catalogs")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier =
                            Modifier
                                .width(800.dp)
                                .focusRequester(inputRequester)
                                .tvVerticalFocus(
                                    up = if (mode == TvManagerMode.STREMIO) stremioRequester else providersRequester,
                                    down = installRequester,
                                ),
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (mode == TvManagerMode.STREMIO) {
                                    "Paste Stremio addon manifest URL..."
                                } else {
                                    "Paste JavaScript provider repository URL..."
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
                                focusedContainerColor = ManagerPanelRaised,
                                unfocusedContainerColor = ManagerPanel,
                                focusedPlaceholderColor = ManagerMuted,
                                unfocusedPlaceholderColor = ManagerMuted.copy(alpha = 0.72f),
                            ),
                    )

                    Button(
                        onClick = {
                            val requested = input.trim()
                            if (requested.isNotBlank() && !installing) {
                                installing = true
                                message = null
                                scope.launch {
                                    val result =
                                        runCatching {
                                            if (mode == TvManagerMode.STREMIO) {
                                                store.addAddon(requested)
                                            } else {
                                                store.addRepository(requested)
                                            }
                                        }
                                    if (result.isSuccess) {
                                        input = ""
                                        message = "Installed successfully"
                                        snapshot = runCatching { store.snapshot() }.getOrNull() ?: snapshot
                                        providerHealth = store.providerHealthSnapshot()
                                        snapshot?.let { current ->
                                            catalogOrder = store.reconcileCatalogOrder(
                                                current.addons.flatMap { addon -> addon.catalogs.filter { it.canLoadWithoutExtras }.map { catalog -> catalog.key } }
                                            )
                                        }
                                    } else {
                                        message = result.exceptionOrNull()?.message ?: "Unable to install source"
                                    }
                                    installing = false
                                }
                            }
                        },
                        modifier = Modifier.focusRequester(installRequester),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Text(if (installing) "Installing..." else "Install", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = ::refresh,
                        modifier = Modifier.focusRequester(refreshRequester),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManagerPanelRaised, contentColor = Color.White),
                    ) {
                        Text("Refresh")
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = if (it.contains("success", ignoreCase = true)) ManagerGreen else ManagerMuted,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(18.dp))

            if (loading && snapshot == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 46.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                when (mode) {
                    TvManagerMode.STREMIO ->
                        StremioAddonList(
                            addons = snapshot?.addons.orEmpty(),
                            actionKey = actionKey,
                            onToggle = { addon ->
                                store.setAddonEnabled(addon.manifestUrl, !addon.enabled)
                                snapshot = snapshot?.copy(
                                    addons = snapshot!!.addons.map {
                                        if (it.manifestUrl == addon.manifestUrl) it.copy(enabled = !it.enabled) else it
                                    }
                                )
                            },
                            onRefresh = { addon ->
                                scope.launch {
                                    actionKey = "addon:${addon.manifestUrl}"
                                    runCatching { store.refreshAddon(addon.manifestUrl) }
                                        .onSuccess {
                                            snapshot = runCatching { store.snapshot() }.getOrNull() ?: snapshot
                                            snapshot?.let { current ->
                                                catalogOrder = store.reconcileCatalogOrder(
                                                    current.addons.flatMap { it.catalogs.filter { catalog -> catalog.canLoadWithoutExtras }.map { catalog -> catalog.key } }
                                                )
                                            }
                                            message = "${addon.name} refreshed"
                                        }
                                        .onFailure { message = it.message ?: "Unable to refresh addon" }
                                    actionKey = null
                                }
                            },
                            onRemove = { addon ->
                                store.removeAddon(addon.manifestUrl)
                                scope.launch {
                                    snapshot = runCatching { store.snapshot() }.getOrNull() ?: snapshot
                                    snapshot?.let { current ->
                                        catalogOrder = store.reconcileCatalogOrder(
                                            current.addons.flatMap { it.catalogs.filter { catalog -> catalog.canLoadWithoutExtras }.map { catalog -> catalog.key } }
                                        )
                                    }
                                    message = "${addon.name} removed"
                                }
                            },
                        )

                    TvManagerMode.CATALOGS ->
                        CatalogOrderList(
                            addons = snapshot?.addons.orEmpty(),
                            order = catalogOrder,
                            onMove = { index, delta ->
                                val target = index + delta
                                if (index in catalogOrder.indices && target in catalogOrder.indices) {
                                    val next = catalogOrder.toMutableList()
                                    val moved = next.removeAt(index)
                                    next.add(target, moved)
                                    catalogOrder = next
                                    store.setCatalogOrder(next)
                                }
                            },
                        )

                    TvManagerMode.PROVIDERS ->
                        ProviderRepositoryList(
                            repositories = snapshot?.repositories.orEmpty(),
                            health = providerHealth,
                            actionKey = actionKey,
                            onToggleRepository = { repo ->
                                store.setRepositoryEnabled(repo.manifestUrl, !repo.enabled)
                                snapshot =
                                    snapshot?.copy(
                                        repositories =
                                            snapshot!!.repositories.map {
                                                if (it.manifestUrl == repo.manifestUrl) {
                                                    it.copy(enabled = !it.enabled)
                                                } else {
                                                    it
                                                }
                                            }
                                    )
                            },
                            onToggleProvider = { repo, provider ->
                                store.setProviderEnabled(repo.manifestUrl, provider.id, !provider.enabled)
                                snapshot = snapshot?.copy(
                                    repositories = snapshot!!.repositories.map { storedRepo ->
                                        if (storedRepo.manifestUrl != repo.manifestUrl) storedRepo
                                        else storedRepo.copy(
                                            providers = storedRepo.providers.map {
                                                if (it.id == provider.id) it.copy(enabled = !it.enabled) else it
                                            }
                                        )
                                    }
                                )
                                providerHealth = store.providerHealthSnapshot()
                            },
                            onRefreshRepository = { repo ->
                                scope.launch {
                                    actionKey = "repo:${repo.manifestUrl}"
                                    runCatching { store.refreshRepository(repo.manifestUrl) }
                                        .onSuccess {
                                            snapshot = runCatching { store.snapshot() }.getOrNull() ?: snapshot
                                            providerHealth = store.providerHealthSnapshot()
                                            message = "${repo.name} refreshed"
                                        }
                                        .onFailure { message = it.message ?: "Unable to refresh repository" }
                                    actionKey = null
                                }
                            },
                            onRemoveRepository = { repo ->
                                store.removeRepository(repo.manifestUrl)
                                scope.launch {
                                    snapshot = runCatching { store.snapshot() }.getOrNull() ?: snapshot
                                    providerHealth = store.providerHealthSnapshot()
                                    message = "${repo.name} removed"
                                }
                            },
                            onRefreshHealth = { providerHealth = store.providerHealthSnapshot() },
                        )
                }
            }
        }

        TvTopNav(
            navRequesters = navRequesters,
            contentDownRequester =
                when (mode) {
                    TvManagerMode.STREMIO -> stremioRequester
                    TvManagerMode.CATALOGS -> catalogsRequester
                    TvManagerMode.PROVIDERS -> providersRequester
                },
            selectedLabel = "",
            onSelected = onNavigate,
        )
    }
}

@Composable
private fun ManagerModeChip(
    text: String,
    selected: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "managerModeScale")
    val background by animateColorAsState(
        if (selected) ManagerGreen.copy(alpha = 0.18f) else ManagerPanel,
        label = "managerModeBackground",
    )

    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .tvVerticalFocus(up = upRequester, down = downRequester)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(background, RoundedCornerShape(9.dp))
                .border(
                    width = 1.dp,
                    color = if (focused || selected) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(9.dp),
                )
                .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StremioAddonList(
    addons: List<TvStremioAddonInfo>,
    actionKey: String?,
    onToggle: (TvStremioAddonInfo) -> Unit,
    onRefresh: (TvStremioAddonInfo) -> Unit,
    onRemove: (TvStremioAddonInfo) -> Unit,
) {
    if (addons.isEmpty()) {
        ManagerEmpty("No Stremio addons installed")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "${addons.count { it.enabled }} of ${addons.size} addons enabled",
                color = ManagerMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(addons, key = { it.manifestUrl }) { addon ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ManagerToggleCard(
                    title = addon.name,
                    subtitle = buildList {
                        addon.version?.let { add("v$it") }
                        if (addon.resources.isNotEmpty()) add(addon.resources.take(4).joinToString(" • "))
                        if (!addon.reachable) add("Manifest unavailable")
                    }.joinToString("  •  ").ifBlank { addon.manifestUrl },
                    enabled = addon.enabled,
                    warning = !addon.reachable,
                    onClick = { onToggle(addon) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        enabled = actionKey == null,
                        onClick = { onRefresh(addon) },
                    ) { Text(if (actionKey == "addon:${addon.manifestUrl}") "Refreshing..." else "Refresh") }
                    TextButton(
                        enabled = actionKey == null,
                        onClick = { onRemove(addon) },
                    ) { Text("Remove", color = ManagerRed) }
                }
            }
        }
    }
}

private data class TvCatalogOrderEntry(
    val key: String,
    val title: String,
    val providerName: String,
    val type: String,
    val addonEnabled: Boolean,
)

@Composable
private fun CatalogOrderList(
    addons: List<TvStremioAddonInfo>,
    order: List<String>,
    onMove: (Int, Int) -> Unit,
) {
    val entries =
        remember(addons) {
            addons.flatMap { addon ->
                addon.catalogs
                    .filter { it.canLoadWithoutExtras }
                    .map { catalog ->
                        TvCatalogOrderEntry(
                            key = catalog.key,
                            title = catalog.name,
                            providerName = catalog.providerName,
                            type = catalog.type.replaceFirstChar { it.uppercase() },
                            addonEnabled = addon.enabled && addon.reachable,
                        )
                    }
            }
        }
    val entryByKey = remember(entries) { entries.associateBy { it.key } }
    val visibleOrder = order.filter { it in entryByKey }

    if (entries.isEmpty()) {
        ManagerEmpty("No loadable catalogs found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "${entries.count { it.addonEnabled }} active catalog entries • Home follows this order",
                color = ManagerMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        visibleOrder.forEachIndexed { index, key ->
            val entry = entryByKey[key] ?: return@forEachIndexed
            item(key = "catalog-order:$key") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(ManagerPanel, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.width(850.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(38.dp)
                                    .height(38.dp)
                                    .background(ManagerPanelRaised, RoundedCornerShape(19.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (entry.addonEnabled) Color.White else ManagerMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Column {
                            Text(
                                text = entry.title,
                                color = if (entry.addonEnabled) Color.White else ManagerMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text =
                                    "${entry.providerName}  •  ${entry.type}" +
                                        if (entry.addonEnabled) "" else "  •  Addon disabled",
                                color = ManagerMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = index > 0,
                            onClick = { onMove(index, -1) },
                        ) {
                            Text("↑", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = index < visibleOrder.lastIndex,
                            onClick = { onMove(index, 1) },
                        ) {
                            Text("↓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRepositoryList(
    repositories: List<TvPluginRepositoryInfo>,
    health: TvProviderHealthSnapshot?,
    actionKey: String?,
    onToggleRepository: (TvPluginRepositoryInfo) -> Unit,
    onToggleProvider: (TvPluginRepositoryInfo, TvPluginProviderInfo) -> Unit,
    onRefreshRepository: (TvPluginRepositoryInfo) -> Unit,
    onRemoveRepository: (TvPluginRepositoryInfo) -> Unit,
    onRefreshHealth: () -> Unit,
) {
    if (repositories.isEmpty()) {
        ManagerEmpty("No JavaScript provider repositories installed")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "provider-health") {
            ProviderHealthCard(health = health, onRefresh = onRefreshHealth)
        }
        repositories.forEach { repository ->
            item(key = "repo:${repository.manifestUrl}") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RepositoryHeader(
                        repository = repository,
                        onClick = { onToggleRepository(repository) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            enabled = actionKey == null,
                            onClick = { onRefreshRepository(repository) },
                        ) { Text(if (actionKey == "repo:${repository.manifestUrl}") "Refreshing..." else "Refresh Repository") }
                        TextButton(
                            enabled = actionKey == null,
                            onClick = { onRemoveRepository(repository) },
                        ) { Text("Remove Repository", color = ManagerRed) }
                    }
                }
            }

            if (repository.reachable) {
                items(
                    items = repository.providers,
                    key = { provider -> "${repository.manifestUrl}:${provider.id}" },
                ) { provider ->
                    ProviderCard(
                        repositoryEnabled = repository.enabled,
                        provider = provider,
                        health = health?.records?.firstOrNull {
                            it.repositoryManifestUrl == repository.manifestUrl && it.providerId == provider.id
                        },
                        onClick = { onToggleProvider(repository, provider) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderHealthCard(
    health: TvProviderHealthSnapshot?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ManagerPanelRaised, RoundedCornerShape(11.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(11.dp))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Provider Health", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                text = health?.let {
                    "${it.online} online • ${it.slow} slow • ${it.noResults} no results • ${it.timeout + it.failed + it.blocked} issues"
                } ?: "No provider health data yet",
                color = ManagerMuted,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onRefresh) { Text("Refresh Status") }
    }
}

@Composable
private fun RepositoryHeader(
    repository: TvPluginRepositoryInfo,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "repoScale")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(ManagerPanelRaised, RoundedCornerShape(11.dp))
                .border(
                    1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(850.dp)) {
            Text(
                text = repository.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text =
                    if (!repository.reachable) {
                        "Repository manifest unavailable"
                    } else {
                        "${repository.providers.count { it.enabled }} of ${repository.providers.size} providers enabled" +
                            (repository.version?.let { "  •  v$it" } ?: "")
                    },
                color = if (repository.reachable) ManagerMuted else ManagerRed,
                fontSize = 12.sp,
            )
        }
        StatusPill(repository.enabled, enabledText = "Repository On", disabledText = "Repository Off")
    }
}

@Composable
private fun ProviderCard(
    repositoryEnabled: Boolean,
    provider: TvPluginProviderInfo,
    health: TvProviderHealthInfo?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "providerScale")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 26.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(ManagerPanel, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (focused) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(830.dp)) {
            Text(
                text = provider.name,
                color = if (repositoryEnabled) Color.White else ManagerMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            provider.description?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = it,
                    color = ManagerMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            health?.let { record ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(record.status)
                        record.responseMs?.let { append(" • ${it}ms") }
                        if (record.streamCount > 0) append(" • ${record.streamCount} streams")
                        record.error?.takeIf(String::isNotBlank)?.let { append(" • $it") }
                    },
                    color = if (record.status == "Online") ManagerGreen else ManagerMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusPill(
            enabled = provider.enabled && repositoryEnabled,
            enabledText = if (repositoryEnabled) "Enabled" else "Repo Off",
            disabledText = if (repositoryEnabled) "Disabled" else "Repo Off",
        )
    }
}

@Composable
private fun ManagerToggleCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    warning: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "managerCardScale")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(ManagerPanel, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (focused) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 19.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(880.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = if (warning) ManagerRed else ManagerMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(enabled, "Enabled", "Disabled")
    }
}

@Composable
private fun StatusPill(
    enabled: Boolean,
    enabledText: String,
    disabledText: String,
) {
    Box(
        modifier =
            Modifier
                .background(
                    color = if (enabled) ManagerGreen.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(50),
                )
                .border(
                    1.dp,
                    if (enabled) ManagerGreen.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (enabled) enabledText else disabledText,
            color = if (enabled) ManagerGreen else ManagerMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ManagerEmpty(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Paste a manifest URL above to install one.",
            color = ManagerMuted,
            fontSize = 13.sp,
        )
    }
}
