package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.delay

internal data class TvSettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val value: String = "",
    val enabled: Boolean = true,
    val onActivate: (() -> Unit)? = null,
    val onPrevious: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    val section: String? = null,
    val icon: ImageVector? = null,
    val onRightAction: (() -> Unit)? = null,
)

internal data class TvSettingsNavItem(
    val id: String,
    val title: String,
    val section: String? = null,
)

internal data class TvSettingsMetric(
    val value: String,
    val label: String,
)

private data class TvSettingsEmbeddedHost(
    val panelKey: String,
    val requesterFor: (String) -> FocusRequester,
    val onFocusableRowsChanged: (List<String>) -> Unit,
    val restorePanelFocus: () -> Boolean,
    val onLeftToCategory: () -> Unit,
    val onRowFocused: (String) -> Unit,
)

private val LocalTvSettingsEmbeddedHost = staticCompositionLocalOf<TvSettingsEmbeddedHost?> { null }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvSettingsMasterDetailShell(
    categories: List<TvSettingsNavItem>,
    selectedCategoryId: String,
    panelKey: String,
    panelAutoFocusToken: Int,
    panelHasBack: Boolean,
    onCategorySelected: (String) -> Unit,
    onPanelBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (categories.isEmpty()) return

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val categoryRequesters = remember(categories.map { it.id }) {
        categories.associate { it.id to FocusRequester() }
    }
    val panelRequesters = remember { mutableMapOf<String, MutableMap<String, FocusRequester>>() }
    val panelFirstRowIds = remember { mutableMapOf<String, String>() }
    val panelLastFocusedIds = remember { mutableMapOf<String, String>() }
    val panelFocusableRowIds = remember { mutableMapOf<String, List<String>>() }
    var lastPane by remember { mutableStateOf("category") }
    var navExpanded by remember { mutableStateOf(false) }
    var sidebarFocusIntent by remember { mutableStateOf(false) }

    fun requesterForPanelRow(key: String, rowId: String): FocusRequester =
        panelRequesters.getOrPut(key) { mutableMapOf() }.getOrPut(rowId) { FocusRequester() }

    fun updatePanelFocusableRows(key: String, rowIds: List<String>) {
        val previousRows = panelFocusableRowIds[key].orEmpty()
        panelFocusableRowIds[key] = rowIds

        if (rowIds.isEmpty()) {
            panelFirstRowIds.remove(key)
            return
        }

        panelFirstRowIds[key] = rowIds.first()
        val previousFocusedId = panelLastFocusedIds[key]
        if (previousFocusedId == null) {
            panelLastFocusedIds[key] = rowIds.first()
            return
        }

        if (previousFocusedId !in rowIds) {
            val previousIndex = previousRows.indexOf(previousFocusedId)
            val fallbackIndex = if (previousIndex >= 0) {
                previousIndex.coerceAtMost(rowIds.lastIndex)
            } else {
                0
            }
            panelLastFocusedIds[key] = rowIds[fallbackIndex]
        }
    }

    fun panelFocusTargetId(key: String): String? {
        val focusableRows = panelFocusableRowIds[key].orEmpty()
        if (focusableRows.isEmpty()) return null
        return panelLastFocusedIds[key]?.takeIf { it in focusableRows }
            ?: panelFirstRowIds[key]?.takeIf { it in focusableRows }
            ?: focusableRows.first()
    }

    fun focusGlobalNav() {
        sidebarFocusIntent = true
        navExpanded = true
        runCatching { navRequesters.getValue("Settings").requestFocus() }
    }

    fun focusSelectedCategory(): Boolean {
        sidebarFocusIntent = false
        navExpanded = false
        lastPane = "category"
        return runCatching {
            categoryRequesters.getValue(selectedCategoryId).requestFocus()
            true
        }.getOrDefault(false)
    }

    fun focusPanel(): Boolean {
        sidebarFocusIntent = false
        navExpanded = false
        val rowId = panelFocusTargetId(panelKey) ?: return false
        val requester = requesterForPanelRow(panelKey, rowId)
        return runCatching {
            requester.requestFocus()
            panelLastFocusedIds[panelKey] = rowId
            lastPane = "panel"
            true
        }.getOrDefault(false)
    }

    BackHandler {
        when {
            panelHasBack -> onPanelBack()
            lastPane == "panel" -> focusSelectedCategory()
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { categoryRequesters.getValue(selectedCategoryId).requestFocus() }
    }

    LaunchedEffect(panelAutoFocusToken) {
        if (panelAutoFocusToken <= 0) return@LaunchedEffect
        delay(70)
        focusPanel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 100.dp, end = 42.dp, top = 34.dp, bottom = 28.dp)
                .background(TvDesign.Surface.copy(alpha = .18f), RoundedCornerShape(22.dp))
                .border(1.dp, TvDesign.White.copy(alpha = .12f), RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(242.dp)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = "SETTINGS",
                    color = TvDesign.Dim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.25.sp,
                    modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    var previousSection: String? = null
                    categories.forEachIndexed { index, category ->
                        val section = category.section?.takeIf { it.isNotBlank() }
                        if (section != null && section != previousSection) {
                            item(key = "category-section-$section") {
                                Text(
                                    text = section.uppercase(),
                                    color = TvDesign.Dim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.15.sp,
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        top = if (index == 0) 2.dp else 9.dp,
                                        bottom = 1.dp,
                                    ),
                                )
                            }
                        }
                        previousSection = section

                        item(key = category.id) {
                            TvSettingsCategoryRow(
                                category = category,
                                selected = category.id == selectedCategoryId,
                                grouped = false,
                                requester = categoryRequesters.getValue(category.id),
                                onFocused = {
                                    navExpanded = false
                                    lastPane = "category"
                                    onCategorySelected(category.id)
                                },
                                onLeft = ::focusGlobalNav,
                                onRight = { focusPanel() },
                                onUp = {
                                    if (index <= 0) true else runCatching {
                                        categoryRequesters.getValue(categories[index - 1].id).requestFocus()
                                        true
                                    }.getOrDefault(false)
                                },
                                onDown = {
                                    if (index >= categories.lastIndex) true else runCatching {
                                        categoryRequesters.getValue(categories[index + 1].id).requestFocus()
                                        true
                                    }.getOrDefault(false)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(26.dp))

            CompositionLocalProvider(
                LocalTvSettingsEmbeddedHost provides TvSettingsEmbeddedHost(
                    panelKey = panelKey,
                    requesterFor = { rowId -> requesterForPanelRow(panelKey, rowId) },
                    onFocusableRowsChanged = { rowIds -> updatePanelFocusableRows(panelKey, rowIds) },
                    restorePanelFocus = {
                        if (lastPane == "panel") focusPanel() else false
                    },
                    onLeftToCategory = { focusSelectedCategory() },
                    onRowFocused = { rowId ->
                        panelLastFocusedIds[panelKey] = rowId
                        navExpanded = false
                        lastPane = "panel"
                    },
                )
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRestorer {
                            panelFocusTargetId(panelKey)?.let { requesterForPanelRow(panelKey, it) }
                                ?: FocusRequester.Default
                        }
                        .focusGroup(),
                ) {
                    content()
                }
            }
        }

        TvSidebar(
            selected = "Settings",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {
                if (sidebarFocusIntent) {
                    navExpanded = true
                } else if (lastPane == "panel") {
                    focusPanel()
                } else {
                    focusSelectedCategory()
                }
            },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = {
                sidebarFocusIntent = false
                if (lastPane == "panel") focusPanel() else focusSelectedCategory()
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun TvSettingsCategoryRow(
    category: TvSettingsNavItem,
    selected: Boolean,
    grouped: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Boolean,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    var focused by remember(category.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (grouped) 46.dp else 50.dp)
            .focusRequester(requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeft()
                        true
                    }
                    event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> onRight()
                    event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onRight()
                        true
                    }
                    else -> false
                }
            }
            .background(
                when {
                    focused -> TvDesign.White.copy(alpha = .13f)
                    selected && grouped -> TvDesign.White.copy(alpha = .045f)
                    selected -> TvDesign.SurfaceRaised.copy(alpha = .58f)
                    grouped -> TvDesign.White.copy(alpha = 0f)
                    else -> TvDesign.Surface.copy(alpha = .44f)
                },
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused) 2.dp else if (grouped) 0.dp else 1.dp,
                when {
                    focused -> TvDesign.White.copy(alpha = .94f)
                    grouped -> TvDesign.White.copy(alpha = 0f)
                    else -> TvDesign.White.copy(alpha = .05f)
                },
                RoundedCornerShape(12.dp),
            )
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.title,
            color = if (focused || selected) TvDesign.White else TvDesign.Muted,
            fontSize = 16.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "›",
            color = if (focused) TvDesign.White else TvDesign.Dim,
            fontSize = 20.sp,
        )
    }
}

@Composable
internal fun TvSettingsProfilePanel(
    profileName: String,
    profileSubtitle: String,
    avatarDrawableRes: Int?,
    myListCount: Int,
    watchedCount: Int,
    dnaValue: String,
    tastePreview: String,
    onOpenDna: () -> Unit,
    onSwitchProfiles: () -> Unit,
) {
    val host = LocalTvSettingsEmbeddedHost.current ?: return
    val profileRequester = host.requesterFor("profile-card")
    val switchRequester = host.requesterFor("switch-profiles")
    LaunchedEffect(host.panelKey) {
        host.onFocusableRowsChanged(listOf("profile-card", "switch-profiles"))
        delay(24)
        host.restorePanelFocus()
    }
    var profileFocused by remember(profileName) { mutableStateOf(false) }
    var switchFocused by remember(profileName) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Profile",
            color = TvDesign.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Your VUEO profile and local taste at a glance.",
            color = TvDesign.Muted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvDesign.Surface.copy(alpha = .52f), RoundedCornerShape(20.dp))
                .border(1.dp, TvDesign.White.copy(alpha = .11f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(profileRequester)
                    .onFocusChanged { state ->
                        profileFocused = state.isFocused
                        if (state.isFocused) host.onRowFocused("profile-card")
                    }
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        when {
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                                host.onLeftToCategory()
                                true
                            }
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_UP -> true
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                                runCatching { switchRequester.requestFocus() }
                                true
                            }
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                            event.isTvActivationKey() -> {
                                if (event.type == KeyEventType.KeyUp) onOpenDna()
                                true
                            }
                            else -> false
                        }
                    }
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (profileFocused) TvDesign.White.copy(alpha = .10f) else TvDesign.White.copy(alpha = .02f))
                    .border(
                        if (profileFocused) 1.5.dp else 0.dp,
                        if (profileFocused) TvDesign.White.copy(alpha = .88f) else TvDesign.White.copy(alpha = 0f),
                        RoundedCornerShape(15.dp),
                    )
                    .focusable()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(TvDesign.SurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarDrawableRes != null) {
                        Image(
                            painter = painterResource(avatarDrawableRes),
                            contentDescription = profileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = profileName.trim().firstOrNull()?.uppercase() ?: "V",
                            color = TvDesign.White,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = profileName,
                        color = TvDesign.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profileSubtitle,
                        color = TvDesign.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "›",
                    color = if (profileFocused) TvDesign.White else TvDesign.Dim,
                    fontSize = 31.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvProfileStat(Modifier.weight(1f), "My List", myListCount.toString())
                TvProfileStat(Modifier.weight(1f), "Watched", watchedCount.toString())
                TvProfileStat(Modifier.weight(1f), "DNA", dnaValue)
            }

            Text(
                text = tastePreview,
                color = TvDesign.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(switchRequester)
                    .onFocusChanged { state ->
                        switchFocused = state.isFocused
                        if (state.isFocused) host.onRowFocused("switch-profiles")
                    }
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        when {
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                                host.onLeftToCategory()
                                true
                            }
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                                runCatching { profileRequester.requestFocus() }
                                true
                            }
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> true
                            event.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                            event.isTvActivationKey() -> {
                                if (event.type == KeyEventType.KeyUp) onSwitchProfiles()
                                true
                            }
                            else -> false
                        }
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (switchFocused) TvDesign.White else TvDesign.Black.copy(alpha = .28f))
                    .border(
                        if (switchFocused) 1.5.dp else 1.dp,
                        if (switchFocused) TvDesign.White else TvDesign.White.copy(alpha = .06f),
                        RoundedCornerShape(14.dp),
                    )
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⇄",
                    color = if (switchFocused) TvDesign.Black else TvDesign.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Switch Profiles",
                    color = if (switchFocused) TvDesign.Black else TvDesign.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TvProfileStat(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier
            .height(86.dp)
            .background(TvDesign.Black.copy(alpha = .25f), RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = TvDesign.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(label, color = TvDesign.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TvSettingsMetricsRow(
    metrics: List<TvSettingsMetric>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvDesign.Surface.copy(alpha = .54f), RoundedCornerShape(16.dp))
            .border(1.dp, TvDesign.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        metrics.forEach { metric ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = metric.value,
                    color = TvDesign.Accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = metric.label,
                    color = TvDesign.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvSettingsListScreen(
    title: String,
    subtitle: String,
    entries: List<TvSettingsEntry>,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    topLabel: String? = null,
    footer: String? = null,
    metrics: List<TvSettingsMetric> = emptyList(),
) {
    val embeddedHost = LocalTvSettingsEmbeddedHost.current
    if (embeddedHost != null) {
        TvSettingsEmbeddedPanel(
            title = title,
            subtitle = subtitle,
            entries = entries,
            host = embeddedHost,
            topLabel = topLabel,
            footer = footer,
            metrics = metrics,
        )
        return
    }

    BackHandler(onBack = onBack)

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    entries.forEach { entry -> rowRequesters.getOrPut(entry.id) { FocusRequester() } }
    val focusableIds = entries.filter { it.enabled }.map { it.id }
    val firstFocusable = entries.firstOrNull { it.enabled }
    val lastFocusable = entries.lastOrNull { it.enabled }
    var lastFocusedId by remember { mutableStateOf(firstFocusable?.id.orEmpty()) }
    var previousFocusableIds by remember { mutableStateOf(focusableIds) }
    var navExpanded by remember { mutableStateOf(false) }
    var sidebarFocusIntent by remember { mutableStateOf(false) }

    LaunchedEffect(focusableIds) {
        if (focusableIds.isEmpty()) return@LaunchedEffect

        if (lastFocusedId !in focusableIds) {
            val previousIndex = previousFocusableIds.indexOf(lastFocusedId)
            val fallbackIndex = if (previousIndex >= 0) {
                previousIndex.coerceAtMost(focusableIds.lastIndex)
            } else {
                0
            }
            lastFocusedId = focusableIds[fallbackIndex]
        }
        previousFocusableIds = focusableIds

        delay(90)
        runCatching { rowRequesters.getValue(lastFocusedId).requestFocus() }
    }

    fun focusSettingsNav() {
        sidebarFocusIntent = true
        navExpanded = true
        runCatching { navRequesters.getValue("Settings").requestFocus() }
    }

    fun restoreContentFocus(): Boolean {
        sidebarFocusIntent = false
        navExpanded = false
        val targetId = lastFocusedId.takeIf { it in focusableIds } ?: focusableIds.firstOrNull() ?: return false
        val requester = rowRequesters[targetId] ?: return false
        lastFocusedId = targetId
        return runCatching { requester.requestFocus(); true }.getOrDefault(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 112.dp, end = 48.dp, top = 38.dp, bottom = 28.dp),
        ) {
            if (!topLabel.isNullOrBlank()) {
                Text(
                    text = topLabel.uppercase(),
                    color = TvDesign.Dim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(5.dp))
            }

            Text(
                text = title,
                color = TvDesign.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = TvDesign.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )

            if (metrics.isNotEmpty()) {
                TvSettingsMetricsRow(metrics = metrics)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRestorer {
                        val targetId = lastFocusedId.takeIf { it in focusableIds } ?: focusableIds.firstOrNull()
                        targetId?.let { rowRequesters[it] } ?: FocusRequester.Default
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var previousSection: String? = null
                entries.forEachIndexed { index, entry ->
                    val section = entry.section?.takeIf { it.isNotBlank() }
                    if (section != null && section != previousSection) {
                        item(key = "section-$index-$section") {
                            Text(
                                text = section.uppercase(),
                                color = TvDesign.Dim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                modifier = Modifier.padding(start = 8.dp, top = if (index == 0) 2.dp else 10.dp, bottom = 1.dp),
                            )
                        }
                        previousSection = section
                    }

                    item(key = entry.id) {
                        TvSettingsRow(
                            entry = entry,
                            requester = rowRequesters.getValue(entry.id),
                            first = entry.id == firstFocusable?.id,
                            last = entry.id == lastFocusable?.id,
                            onLeftToSidebar = ::focusSettingsNav,
                            onFocused = {
                                navExpanded = false
                                lastFocusedId = entry.id
                            },
                        )
                    }
                }
                if (!footer.isNullOrBlank()) {
                    item(key = "settings-footer") {
                        Text(
                            text = footer,
                            color = TvDesign.Dim,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        TvSidebar(
            selected = "Settings",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {
                if (sidebarFocusIntent) navExpanded = true else restoreContentFocus()
            },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = ::restoreContentFocus,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun TvSettingsEmbeddedPanel(
    title: String,
    subtitle: String,
    entries: List<TvSettingsEntry>,
    host: TvSettingsEmbeddedHost,
    topLabel: String?,
    footer: String?,
    metrics: List<TvSettingsMetric>,
) {
    val focusableIds = entries.filter { it.enabled }.map { it.id }
    val firstFocusable = entries.firstOrNull { it.enabled }
    val lastFocusable = entries.lastOrNull { it.enabled }

    LaunchedEffect(host.panelKey, focusableIds) {
        host.onFocusableRowsChanged(focusableIds)
        if (focusableIds.isNotEmpty()) {
            delay(24)
            host.restorePanelFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!topLabel.isNullOrBlank()) {
            Text(
                text = topLabel.uppercase(),
                color = TvDesign.Dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(5.dp))
        }

        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = TvDesign.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
        )

        if (metrics.isNotEmpty()) {
            TvSettingsMetricsRow(metrics = metrics)
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var previousSection: String? = null
            entries.forEachIndexed { index, entry ->
                val section = entry.section?.takeIf { it.isNotBlank() }
                if (section != null && section != previousSection) {
                    item(key = "embedded-section-$index-$section") {
                        Text(
                            text = section.uppercase(),
                            color = TvDesign.Dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(start = 8.dp, top = if (index == 0) 2.dp else 10.dp, bottom = 1.dp),
                        )
                    }
                    previousSection = section
                }

                item(key = entry.id) {
                    TvSettingsRow(
                        entry = entry,
                        requester = host.requesterFor(entry.id),
                        first = entry.id == firstFocusable?.id,
                        last = entry.id == lastFocusable?.id,
                        grouped = false,
                        onLeftToSidebar = host.onLeftToCategory,
                        onFocused = { host.onRowFocused(entry.id) },
                    )
                }
            }

            if (!footer.isNullOrBlank()) {
                item(key = "embedded-settings-footer") {
                    Text(
                        text = footer,
                        color = TvDesign.Dim,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsContextPane(
    title: String,
    subtitle: String,
    topLabel: String?,
    selectedEntry: TvSettingsEntry?,
    footer: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!topLabel.isNullOrBlank()) {
            Text(
                text = topLabel.uppercase(),
                color = TvDesign.Accent.copy(alpha = .88f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.25.sp,
            )
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
        )
        Text(
            text = subtitle,
            color = TvDesign.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(30.dp))

        selectedEntry?.let { entry ->
            Text(
                text = "SELECTED",
                color = TvDesign.Dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.title,
                color = if (entry.enabled) TvDesign.White else TvDesign.Dim,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            if (entry.value.isNotBlank()) {
                Text(
                    text = entry.value,
                    color = TvDesign.White.copy(alpha = .88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(TvDesign.White.copy(alpha = .07f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = TvDesign.Dim,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val controlHint = when {
                selectedEntry?.onPrevious != null || selectedEntry?.onNext != null -> "OK  Select    ◀ ▶  Adjust"
                selectedEntry?.onRightAction != null -> "OK  Toggle    ▶  More"
                selectedEntry?.onActivate != null && selectedEntry.value in setOf("On", "Off") -> "OK  Toggle"
                selectedEntry?.onActivate != null -> "OK  Select"
                else -> ""
            }
            if (controlHint.isNotBlank()) {
                Text(
                    text = controlHint,
                    color = TvDesign.Dim.copy(alpha = .82f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TvSettingsRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    first: Boolean,
    last: Boolean,
    grouped: Boolean = false,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val canAdjust = entry.onPrevious != null || entry.onNext != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (!entry.enabled) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    first &&
                        event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_UP -> true
                    last &&
                        event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> true
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        entry.onPrevious == null -> {
                        onLeftToSidebar()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        entry.onPrevious != null -> {
                        entry.onPrevious.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        entry.onNext != null -> {
                        entry.onNext.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        entry.onRightAction != null -> {
                        entry.onRightAction.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    !entry.enabled -> TvDesign.Surface.copy(alpha = if (grouped) .10f else .18f)
                    focused -> TvDesign.White.copy(alpha = .10f)
                    grouped -> TvDesign.White.copy(alpha = .015f)
                    else -> TvDesign.Surface.copy(alpha = .36f)
                },
                shape = RoundedCornerShape(if (grouped) 14.dp else 11.dp),
            )
            .border(
                width = if (focused) 1.5.dp else if (grouped) 0.dp else 1.dp,
                color = when {
                    !entry.enabled -> TvDesign.White.copy(alpha = .025f)
                    focused -> TvDesign.White.copy(alpha = .88f)
                    grouped -> TvDesign.White.copy(alpha = 0f)
                    else -> TvDesign.White.copy(alpha = .045f)
                },
                shape = RoundedCornerShape(if (grouped) 14.dp else 11.dp),
            )
            .focusable(enabled = entry.enabled)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.icon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (focused) TvDesign.White.copy(alpha = .10f) else TvDesign.SurfaceRaised.copy(alpha = .62f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (entry.enabled) TvDesign.White.copy(alpha = .92f) else TvDesign.Dim,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.title,
                color = if (entry.enabled) TvDesign.White else TvDesign.Dim,
                fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                color = if (entry.enabled) TvDesign.Muted else TvDesign.Dim.copy(alpha = .65f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(14.dp))

        if (entry.value.isNotBlank()) {
            Text(
                text = buildString {
                    if (canAdjust && focused) append("‹  ")
                    append(entry.value)
                    if (canAdjust && focused) append("  ›")
                },
                color = if (focused) TvDesign.White else TvDesign.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(
                        color = if (focused) TvDesign.White.copy(alpha = .10f) else TvDesign.White.copy(alpha = .045f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else if (entry.onActivate != null) {
            Text(
                text = "›",
                color = if (focused) TvDesign.White else TvDesign.Dim,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
internal fun TvTextEntryDialog(
    title: String,
    initialValue: String,
    secret: Boolean = false,
    placeholder: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun <T> cycle(values: List<T>, current: T, delta: Int): T {
    if (values.isEmpty()) return current
    val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
    val next = (index + delta).floorMod(values.size)
    return values[next]
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
