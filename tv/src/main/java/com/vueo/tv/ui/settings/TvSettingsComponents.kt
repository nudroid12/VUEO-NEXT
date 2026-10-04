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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import com.vueo.tv.ui.tvSidebarContentStartPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object TvSettingsContrast {
    val Pill = Color(0xFF303030)
    val SelectedPill = Color(0xFF404040)
    val FocusedPill = Color(0xFF555555)
    val Card = Color(0xFF303030)
    val CardContainer = Color(0xFF202020)
    val DisabledCard = Color(0xFF242424)
}

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
    val badge: String? = null,
    val detail: String? = null,
    val accented: Boolean = false,
    val rightActionLabel: String? = null,
    val choices: List<String> = emptyList(),
    val selectedChoiceIndex: Int = -1,
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

internal data class TvSettingsEmbeddedHost(
    val panelKey: String,
    val requesterFor: (String) -> FocusRequester,
    val onFocusableRowsChanged: (List<String>) -> Unit,
    val restorePanelFocus: () -> Boolean,
    val requestDeferredPanelRestore: () -> Unit,
    val onLeftToCategory: () -> Unit,
    val onRowFocused: (String) -> Unit,
)

internal val LocalTvSettingsEmbeddedHost = staticCompositionLocalOf<TvSettingsEmbeddedHost?> { null }

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

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val categoryTextStyle = LocalTextStyle.current.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val arrowTextStyle = LocalTextStyle.current.copy(fontSize = 20.sp)
    // One compact width, sized for the longest label (Content Manager in this menu).
    // Measure the focused weight too so labels never truncate when focus moves.
    val categoryColumnWidth = remember(categories, textMeasurer, density, categoryTextStyle, arrowTextStyle) {
        val labelWidth = categories.maxOf { category ->
            textMeasurer.measure(category.title, style = categoryTextStyle, maxLines = 1).size.width
        }
        val arrowWidth = textMeasurer.measure("›", style = arrowTextStyle, maxLines = 1).size.width
        with(density) { (labelWidth + arrowWidth).toDp() } + 46.dp
    }
    val contentStartPadding = tvSidebarContentStartPadding(100.dp)
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
    val shellScope = rememberCoroutineScope()

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
        val focused = runCatching {
            categoryRequesters.getValue(selectedCategoryId).requestFocus()
        }.getOrDefault(false)
        if (focused) lastPane = "category"
        return focused
    }

    fun focusPanel(): Boolean {
        sidebarFocusIntent = false
        navExpanded = false
        val rowId = panelFocusTargetId(panelKey) ?: return false
        val requester = requesterForPanelRow(panelKey, rowId)
        val focused = runCatching { requester.requestFocus() }.getOrDefault(false)
        if (focused) {
            panelLastFocusedIds[panelKey] = rowId
            lastPane = "panel"
        }
        return focused
    }

    fun requestDeferredPanelRestore() {
        shellScope.launch {
            // A panel replacement can temporarily leave the focused row detached.
            // Retry until the destination row is genuinely focusable. Do not steal
            // focus back after the user intentionally moved to categories/sidebar.
            for (waitMs in listOf(24L, 48L, 90L, 140L)) {
                delay(waitMs)
                if (sidebarFocusIntent || lastPane != "panel") return@launch
                if (focusPanel()) return@launch
            }
            // Empty panels still need a deterministic escape target. Only fall back
            // after the full retry window, never during a normal panel transition.
            if (!sidebarFocusIntent && lastPane == "panel" && panelFocusableRowIds[panelKey].isNullOrEmpty()) {
                focusSelectedCategory()
            }
        }
    }

    BackHandler {
        when {
            navExpanded -> onBack()
            panelHasBack -> onPanelBack()
            else -> focusGlobalNav()
        }
    }

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { categoryRequesters.getValue(selectedCategoryId).requestFocus() }
    }

    LaunchedEffect(panelAutoFocusToken) {
        if (panelAutoFocusToken <= 0) return@LaunchedEffect

        // Opening/backing between Settings panels keeps ownership in the panel.
        // The destination rows publish asynchronously, so use the shared retry path.
        lastPane = "panel"
        requestDeferredPanelRestore()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, end = 42.dp, top = 34.dp, bottom = 28.dp)
                .background(TvDesign.Surface.copy(alpha = .18f), RoundedCornerShape(22.dp))
                .border(1.dp, TvDesign.White.copy(alpha = .12f), RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(categoryColumnWidth)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categories.forEachIndexed { index, category ->
                        item(key = category.id) {
                            TvSettingsCategoryRow(
                                category = category,
                                selected = category.id == selectedCategoryId,
                                grouped = false,
                                requester = categoryRequesters.getValue(category.id),
                                onFocused = {
                                    navExpanded = false
                                    if (lastPane == "panel") {
                                        // During panel replacement Compose may momentarily
                                        // fall back into the category column. That is not a
                                        // user navigation intent, so never let focus alone
                                        // mutate the active Settings page.
                                        requestDeferredPanelRestore()
                                    } else {
                                        lastPane = "category"
                                        onCategorySelected(category.id)
                                    }
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
                    requestDeferredPanelRestore = ::requestDeferredPanelRestore,
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
                    requestDeferredPanelRestore()
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
                    focused -> TvSettingsContrast.FocusedPill
                    selected -> TvSettingsContrast.SelectedPill
                    else -> TvSettingsContrast.Pill
                },
                RoundedCornerShape(percent = 50),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> TvDesign.White.copy(alpha = .94f)
                    selected -> TvDesign.White.copy(alpha = .28f)
                    else -> TvDesign.White.copy(alpha = .10f)
                },
                RoundedCornerShape(percent = 50),
            )
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.title,
            color = TvDesign.White.copy(alpha = if (focused || selected) 1f else .92f),
            fontSize = 16.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "›",
            color = TvDesign.White.copy(alpha = if (focused) 1f else .72f),
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
                .background(TvSettingsContrast.CardContainer, RoundedCornerShape(20.dp))
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
                    .background(if (profileFocused) TvSettingsContrast.FocusedPill else TvSettingsContrast.Card)
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
                    .background(if (switchFocused) TvDesign.White else TvSettingsContrast.Card)
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
            .background(TvSettingsContrast.Card, RoundedCornerShape(13.dp))
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
            .background(TvSettingsContrast.Card, RoundedCornerShape(16.dp))
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
    preferredFocusId: String? = null,
    addonLayout: Boolean = false,
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
            addonLayout = addonLayout,
        )
        return
    }

    BackHandler(onBack = onBack)

    val contentStartPadding = tvSidebarContentStartPadding(112.dp)
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

    LaunchedEffect(focusableIds, preferredFocusId) {
        if (focusableIds.isEmpty()) return@LaunchedEffect

        preferredFocusId?.takeIf { it in focusableIds }?.let { lastFocusedId = it }

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
                .padding(start = contentStartPadding, end = 48.dp, top = 38.dp, bottom = 28.dp),
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
                fontSize = if (addonLayout) 22.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = TvDesign.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = if (addonLayout) 3.dp else 6.dp, bottom = if (addonLayout) 8.dp else 16.dp),
            )

            if (metrics.isNotEmpty()) {
                if (addonLayout) TvAddonCompactMetrics(metrics) else TvSettingsMetricsRow(metrics = metrics)
                Spacer(Modifier.height(if (addonLayout) 8.dp else 12.dp))
            }

            if (addonLayout) {
                TvAddonControls(entries, { rowRequesters.getValue(it) },
                    { lastFocusedId = it; navExpanded = false }, ::focusSettingsNav)
                Spacer(Modifier.height(8.dp))
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
                entries.filterNot { addonLayout && it.isAddonControl() }.forEachIndexed { index, entry ->
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
                            compact = addonLayout,
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
    addonLayout: Boolean = false,
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
            fontSize = if (addonLayout) 22.sp else 29.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = TvDesign.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = if (addonLayout) 3.dp else 5.dp, bottom = if (addonLayout) 8.dp else 16.dp),
        )

        if (metrics.isNotEmpty()) {
            if (addonLayout) TvAddonCompactMetrics(metrics) else TvSettingsMetricsRow(metrics = metrics)
            Spacer(Modifier.height(if (addonLayout) 8.dp else 12.dp))
        }

        if (addonLayout) {
            TvAddonControls(entries, host.requesterFor, host.onRowFocused, host.onLeftToCategory)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var previousSection: String? = null
            entries.filterNot { addonLayout && it.isAddonControl() }.forEachIndexed { index, entry ->
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
                            compact = addonLayout,
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
private fun TvSettingsRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    first: Boolean,
    last: Boolean,
    grouped: Boolean = false,
    compact: Boolean = false,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val canAdjust = entry.onPrevious != null || entry.onNext != null
    val shape = RoundedCornerShape(
        when {
            entry.accented -> 15.dp
            grouped -> 14.dp
            else -> 11.dp
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 70.dp)
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
                    !entry.enabled -> TvSettingsContrast.DisabledCard
                    focused -> TvSettingsContrast.FocusedPill
                    else -> TvSettingsContrast.Card
                },
                shape = shape,
            )
            .border(
                width = if (focused) 1.5.dp else if (grouped) 0.dp else 1.dp,
                color = when {
                    !entry.enabled -> TvDesign.White.copy(alpha = .025f)
                    focused && entry.accented -> TvDesign.Accent.copy(alpha = .90f)
                    focused -> TvDesign.White.copy(alpha = .88f)
                    grouped -> TvDesign.White.copy(alpha = 0f)
                    entry.accented -> TvDesign.Accent.copy(alpha = .12f)
                    else -> TvDesign.White.copy(alpha = .045f)
                },
                shape = shape,
            )
            .focusable(enabled = entry.enabled)
            .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!entry.badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 44.dp)
                    .background(TvDesign.Accent.copy(alpha = .12f), RoundedCornerShape(13.dp))
                    .border(1.dp, TvDesign.Accent.copy(alpha = .28f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.badge.orEmpty(),
                    color = TvDesign.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(14.dp))
        } else {
            entry.icon?.let { icon ->
                Box(
                    modifier = Modifier
                        .size(if (compact) 32.dp else 44.dp)
                        .background(
                            color = if (entry.accented) {
                                TvDesign.Accent.copy(alpha = if (focused) .16f else .09f)
                            } else if (focused) {
                                TvDesign.White.copy(alpha = .10f)
                            } else {
                                TvDesign.SurfaceRaised.copy(alpha = .62f)
                            },
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            !entry.enabled -> TvDesign.Dim
                            entry.accented -> TvDesign.Accent
                            else -> TvDesign.White.copy(alpha = .92f)
                        },
                        modifier = Modifier.size(if (compact) 19.dp else 23.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
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
            if (entry.choices.isNotEmpty()) {
                val selectedIndex = entry.selectedChoiceIndex.coerceIn(0, entry.choices.lastIndex)
                val firstVisible = (selectedIndex - 1)
                    .coerceAtLeast(0)
                    .coerceAtMost((entry.choices.size - 3).coerceAtLeast(0))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    entry.choices.drop(firstVisible).take(3).forEachIndexed { offset, choice ->
                        val choiceIndex = firstVisible + offset
                        val selected = choiceIndex == selectedIndex
                        Text(
                            text = choice,
                            color = if (selected) TvDesign.White else TvDesign.Muted,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(
                                    if (selected) TvDesign.Accent.copy(alpha = .28f)
                                    else TvDesign.White.copy(alpha = .045f),
                                    RoundedCornerShape(50),
                                )
                                .border(
                                    1.dp,
                                    if (selected) TvDesign.Accent.copy(alpha = .72f)
                                    else TvDesign.White.copy(alpha = .12f),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    color = if (entry.enabled) TvDesign.Dim else TvDesign.Dim.copy(alpha = .52f),
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (entry.value.isNotBlank()) {
                Text(
                    text = buildString {
                        if (canAdjust && focused) append("‹  ")
                        append(entry.value)
                        if (canAdjust && focused) append("  ›")
                    },
                    color = when {
                        !entry.enabled -> TvDesign.Dim
                        entry.accented -> TvDesign.Accent
                        focused -> TvDesign.White
                        else -> TvDesign.Muted
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(
                            color = if (entry.accented) {
                                TvDesign.Accent.copy(alpha = if (focused) .16f else .08f)
                            } else if (focused) {
                                TvDesign.White.copy(alpha = .10f)
                            } else {
                                TvDesign.White.copy(alpha = .045f)
                            },
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (entry.onActivate != null && entry.rightActionLabel.isNullOrBlank()) {
                Text(
                    text = "›",
                    color = if (focused && entry.accented) TvDesign.Accent else if (focused) TvDesign.White else TvDesign.Dim,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Light,
                )
            }

            if (focused && !entry.rightActionLabel.isNullOrBlank()) {
                Text(
                    text = "${entry.rightActionLabel}  ›",
                    color = if (entry.accented) TvDesign.Accent else TvDesign.White.copy(alpha = .86f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun rememberTvSettingsDeferredFocusRestore(): () -> Unit {
    val embeddedHost = LocalTvSettingsEmbeddedHost.current
    return remember(embeddedHost) {
        { embeddedHost?.requestDeferredPanelRestore?.invoke() }
    }
}

@Composable
internal fun TvTextEntryDialog(
    title: String,
    initialValue: String,
    secret: Boolean = false,
    placeholder: String = "",
    confirmLabel: String = "Save",
    busy: Boolean = false,
    message: String? = null,
    restoreOnSave: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()

    fun dismissAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    fun saveAndRestore() {
        if (busy || value.isBlank()) return
        onSave(value.trim())
        if (restoreOnSave) restoreSettingsFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) dismissAndRestore() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    enabled = !busy,
                    singleLine = true,
                    placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
                    visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                )
                message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TvDesign.Muted, fontSize = 12.sp)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && value.isNotBlank(),
                onClick = ::saveAndRestore,
            ) { Text(if (busy) "Working…" else confirmLabel) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = ::dismissAndRestore) { Text("Cancel") }
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
    val restoreSettingsFocus = rememberTvSettingsDeferredFocusRestore()

    fun dismissAndRestore() {
        onDismiss()
        restoreSettingsFocus()
    }

    fun confirmAndRestore() {
        onConfirm()
        restoreSettingsFocus()
    }

    AlertDialog(
        onDismissRequest = ::dismissAndRestore,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = ::confirmAndRestore) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = ::dismissAndRestore) { Text("Cancel") }
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

private fun TvSettingsEntry.isAddonControl(): Boolean =
    id == "add" || id == "refresh-addons" || id.startsWith("addon-category-")

@Composable
private fun TvAddonCompactMetrics(metrics: List<TvSettingsMetric>) {
    Text(
        metrics.joinToString(" · ") { "${it.value} ${it.label}" },
        color = TvDesign.Muted, fontSize = 11.sp, maxLines = 1,
    )
}

@Composable
private fun TvAddonControls(
    entries: List<TvSettingsEntry>,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onLeftToSidebar: () -> Unit,
) {
    val actions = entries.filter { it.id == "add" || it.id == "refresh-addons" }
    val categories = entries.filter { it.id.startsWith("addon-category-") }
    val selected = categories.firstOrNull { it.value == "Selected" } ?: categories.firstOrNull()
    val firstAddon = entries.firstOrNull { it.enabled && !it.isAddonControl() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, entry ->
                var focused by remember(entry.id) { mutableStateOf(false) }
                val shape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .focusRequester(requesterFor(entry.id))
                        .focusProperties {
                            up = FocusRequester.Cancel
                            selected?.let { down = requesterFor(it.id) }
                            if (index > 0) left = requesterFor(actions[index - 1].id)
                            if (index < actions.lastIndex) right = requesterFor(actions[index + 1].id)
                            else right = FocusRequester.Cancel
                        }
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(entry.id) }
                        .onPreviewKeyEvent { event ->
                            if (index == 0 && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onLeftToSidebar(); true
                            } else if (event.isTvActivationKey()) {
                                if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                                true
                            } else false
                        }
                        .background(if (focused) TvSettingsContrast.FocusedPill else TvSettingsContrast.Card, shape)
                        .border(1.dp, if (focused) TvDesign.White else TvDesign.White.copy(alpha = .12f), shape)
                        .focusable()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    entry.icon?.let { Icon(it, contentDescription = entry.title, tint = TvDesign.White, modifier = Modifier.size(18.dp)) }
                    if (entry.id == "add") Text(entry.title, color = TvDesign.White, fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            categories.forEachIndexed { index, entry ->
                var focused by remember(entry.id) { mutableStateOf(false) }
                Text(
                    entry.title,
                    color = if (focused || entry.value == "Selected") TvDesign.White else TvDesign.White.copy(alpha = .58f),
                    fontSize = 14.sp,
                    fontWeight = if (entry.value == "Selected") FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier
                        .focusRequester(requesterFor(entry.id))
                        .focusProperties {
                            actions.firstOrNull()?.let { up = requesterFor(it.id) }
                            down = firstAddon?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                            if (index > 0) left = requesterFor(categories[index - 1].id)
                            if (index < categories.lastIndex) right = requesterFor(categories[index + 1].id)
                            else right = FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) { onFocused(entry.id); entry.onActivate?.invoke() }
                        }
                        .onPreviewKeyEvent { event ->
                            if (index == 0 && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onLeftToSidebar(); true
                            } else if (event.isTvActivationKey()) {
                                if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                                true
                            } else false
                        }
                        .focusable()
                        .padding(vertical = 4.dp),
                )
                if (index < categories.lastIndex) Box(Modifier.width(1.dp).height(20.dp).background(TvDesign.White.copy(alpha = .48f)))
            }
        }
    }
}
