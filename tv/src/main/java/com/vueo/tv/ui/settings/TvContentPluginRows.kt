package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.vueo.tv.ui.TvDesign
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PluginSurface get() = TvDesign.SurfaceRaised
private val PluginFocus get() = TvDesign.FocusSurface
private val PluginMuted = Color.White.copy(alpha = .6f)
private val PluginFailure = Color(0xFFFFB4AB)

internal fun TvSettingsEntry.isRepositoryAction(): Boolean =
    id == "refresh-repository" || id == "remove-repository"

@Composable
internal fun TvContentToggle(checked: Boolean, compact: Boolean = false, focused: Boolean = false) {
    // The enclosing row handles D-pad activation; this switch displays that same saved state.
    Switch(
        checked = checked, onCheckedChange = null,
        modifier = if (compact) Modifier.graphicsLayer { scaleX = .82f; scaleY = .82f } else Modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = TvDesign.OnAccent,
            checkedTrackColor = TvDesign.Accent,
            checkedBorderColor = if (focused) TvDesign.OnAccent else TvDesign.Accent,
            uncheckedThumbColor = Color.White.copy(alpha = .65f),
            uncheckedTrackColor = TvDesign.SurfaceRaised, uncheckedBorderColor = Color.White.copy(alpha = .25f),
        ),
    )
}

@Composable
internal fun TvContentSettingsRow(
    entry: TvSettingsEntry,
    contentLayout: Boolean,
    entries: List<TvSettingsEntry>,
    requesterFor: (String) -> FocusRequester,
    requester: FocusRequester,
    first: Boolean,
    last: Boolean,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
    onEntryFocused: (String) -> Unit,
) {
    when {
        contentLayout && entry.onAddonRemove != null -> TvInlineAddonRow(entry, requester, onLeftToSidebar, onFocused)
        !contentLayout -> TvSettingsRow(entry, requester, first, last, onLeftToSidebar = onLeftToSidebar, onFocused = onFocused)
        entry.id.startsWith("repo-") -> TvPluginRepositoryCard(entry, entries, requesterFor, onLeftToSidebar, onEntryFocused)
        entry.providerStatus != null -> TvPluginProviderRow(entry, requester, last, onLeftToSidebar, onFocused)
        else -> TvSettingsRow(entry, requester, first, last, restoreContentStyle = true, onLeftToSidebar = onLeftToSidebar, onFocused = onFocused)
    }
}

@Composable
private fun TvInlineAddonRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    val configureFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    val configure = entry.onAddonConfigure
    val remove = entry.onAddonRemove ?: return
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(TvDesign.Surface, shape)
            .border(1.dp, TvDesign.White.copy(alpha = .14f), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, color = TvDesign.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, color = TvDesign.Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.operationStatus?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TvDesign.Accent, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (configure != null) {
            TvInlineAddonControl(
                label = "Configure",
                requester = configureFocus,
                onFocused = onFocused,
                onLeft = onLeftToSidebar,
                onRight = { removeFocus.requestFocus() },
                onActivate = configure,
            ) { focused ->
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(if (focused) 22.dp else 19.dp),
                    tint = if (focused) TvDesign.White else TvDesign.Muted,
                )
            }
        } else {
            Spacer(Modifier.width(50.dp))
        }
        TvInlineAddonControl(
            label = "Remove",
            requester = removeFocus,
            onFocused = onFocused,
            onLeft = { if (configure != null) configureFocus.requestFocus() else onLeftToSidebar() },
            onRight = { requester.requestFocus() },
            onActivate = remove,
        ) { focused ->
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(if (focused) 22.dp else 19.dp),
                tint = if (focused) TvDesign.White else Color(0xFFFF898D),
            )
        }
        val checked = entry.switchChecked == true
        TvInlineAddonControl(
            label = if (checked) "Disable addon" else "Enable addon",
            requester = requester,
            onFocused = onFocused,
            onLeft = { removeFocus.requestFocus() },
            onRight = {},
            onActivate = entry.onActivate ?: {},
            modifier = Modifier.width(70.dp),
        ) { focused ->
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = if (focused) 1.12f else 1f
                    scaleY = if (focused) 1.12f else 1f
                },
                contentAlignment = Alignment.Center,
            ) {
                TvContentToggle(checked, compact = true)
            }
        }
    }
}

@Composable
private fun TvInlineAddonControl(
    label: String,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier.width(50.dp),
    content: @Composable (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.height(49.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (focused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            if (code == KeyEvent.KEYCODE_DPAD_LEFT) onLeft() else onRight()
                        }
                        true
                    }
                    activation(code) -> {
                        if (event.type == KeyEventType.KeyUp) onActivate()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onActivate)
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier.align(Alignment.Center).height(34.dp),
            contentAlignment = Alignment.Center,
        ) { content(focused) }
    }
}

private fun activation(key: Int): Boolean = key == KeyEvent.KEYCODE_DPAD_CENTER ||
    key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER

@Composable
private fun TvPluginRepositoryCard(
    entry: TvSettingsEntry,
    entries: List<TvSettingsEntry>,
    requesterFor: (String) -> FocusRequester,
    onLeftToSidebar: () -> Unit,
    onEntryFocused: (String) -> Unit,
) {
    val refresh = entries.firstOrNull { it.id == "refresh-repository" }
    val remove = entries.firstOrNull { it.id == "remove-repository" }
    val nextProvider = entries.firstOrNull { it.providerStatus != null }
    // Keep the repository entry ID on the toggle for the host's focus restoration.
    var toggleFocused by remember(entry.id) { mutableStateOf(false) }
    var cardFocused by remember(entry.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    Column(modifier = Modifier.padding(bottom = if (nextProvider != null) 12.dp else 0.dp)
        .fillMaxWidth().background(PluginSurface, shape)
        .onFocusChanged { cardFocused = it.hasFocus }.focusGroup()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).background(TvDesign.SurfaceElevated, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, color = Color.White, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.detail.orEmpty(), color = PluginMuted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.operationStatus?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, color = TvDesign.Accent, fontSize = 11.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Reserve the icon slot so focus visibility never shifts the card.
                remove?.let { action ->
                    TvRepositoryAction(action, Icons.Default.Delete, TvDesign.Accent,
                        requesterFor(action.id), Modifier.graphicsLayer {
                            alpha = if (cardFocused) 1f else 0f
                        }.focusProperties {
                            up = FocusRequester.Default
                            left = FocusRequester.Cancel
                            right = refresh?.let { requesterFor(it.id) } ?: requesterFor(entry.id)
                            down = nextProvider?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                        }.onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                if (event.type == KeyEventType.KeyDown) onLeftToSidebar()
                                true
                            } else false
                        }, { onEntryFocused(action.id) })
                }
                refresh?.let { action ->
                    TvRepositoryAction(action, Icons.Default.Refresh, Color.White,
                        requesterFor(action.id), Modifier.focusProperties {
                            up = FocusRequester.Default
                            left = remove?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                            right = requesterFor(entry.id)
                            down = nextProvider?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                        }.onPreviewKeyEvent { event ->
                            if (remove == null && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                if (event.type == KeyEventType.KeyDown) onLeftToSidebar()
                                true
                            } else false
                        }, { onEntryFocused(action.id) })
                }
                Box(
                    modifier = Modifier.width(64.dp).height(34.dp)
                        .focusRequester(requesterFor(entry.id))
                        .focusProperties {
                            up = FocusRequester.Default
                            left = refresh?.let { requesterFor(it.id) }
                                ?: remove?.let { requesterFor(it.id) } ?: FocusRequester.Default
                            right = FocusRequester.Cancel
                            down = nextProvider?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            toggleFocused = it.isFocused
                            if (it.isFocused) onEntryFocused(entry.id)
                        }
                        .onPreviewKeyEvent { event ->
                            when {
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && refresh == null && remove == null && event.type == KeyEventType.KeyDown -> { onLeftToSidebar(); true }
                                activation(event.nativeKeyEvent.keyCode) -> {
                                    if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                                    true
                                }
                                else -> false
                            }
                        }
                        .background(if (toggleFocused) PluginFocus else Color.Transparent, RoundedCornerShape(50))
                        .border(1.dp, if (toggleFocused) TvDesign.Accent else Color.Transparent, RoundedCornerShape(50))
                        .focusable(),
                    contentAlignment = Alignment.Center,
                ) {
                    TvContentToggle(entry.switchChecked ?: (entry.value == "On"), compact = true)
                }
            }
        }
    }
}

@Composable
private fun TvRepositoryAction(entry: TvSettingsEntry, icon: ImageVector, tint: Color, requester: FocusRequester, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val refreshing = entry.id == "refresh-repository" && entry.value == "Refreshing…"
    Box(
        modifier = modifier.size(34.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .background(if (focused) PluginFocus else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (focused) TvDesign.Accent else Color.Transparent, RoundedCornerShape(50))
            .onPreviewKeyEvent { event ->
                if (entry.enabled && activation(event.nativeKeyEvent.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                    true
                } else false
            }.focusable(enabled = entry.enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = if (refreshing) "Refreshing repository" else entry.title,
            tint = tint, modifier = Modifier.size(20.dp).then(tvContentRefreshMotion(entry)))
    }
}

@Composable
private fun TvPluginProviderRow(entry: TvSettingsEntry, requester: FocusRequester, last: Boolean, onLeftToSidebar: () -> Unit, onFocused: () -> Unit) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(
        topStart = if (entry.providerPanelStart) 15.dp else 0.dp,
        topEnd = if (entry.providerPanelStart) 15.dp else 0.dp,
        bottomStart = if (entry.providerPanelEnd) 15.dp else 0.dp,
        bottomEnd = if (entry.providerPanelEnd) 15.dp else 0.dp,
    )
    Column {
        if (entry.providerPanelStart) Text("PROVIDERS", color = PluginMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().background(PluginSurface, shape)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent.keyCode
                        when {
                            key == KeyEvent.KEYCODE_DPAD_LEFT && event.type == KeyEventType.KeyDown -> { onLeftToSidebar(); true }
                            key == KeyEvent.KEYCODE_DPAD_RIGHT -> { if (event.type == KeyEventType.KeyDown) entry.onRightAction?.invoke(); true }
                            key == KeyEvent.KEYCODE_DPAD_DOWN && last && event.type == KeyEventType.KeyDown -> true
                            activation(key) -> { if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke(); true }
                            else -> false
                        }
                    }
                    .background(if (focused) PluginFocus else Color.Transparent, shape)
                    .border(1.dp, if (focused) TvDesign.Accent else Color.Transparent, shape)
                    .focusable().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle, color = PluginMuted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.providerStatus.orEmpty(), color = if (entry.providerFailed) PluginFailure else if (entry.switchChecked == false) PluginMuted else Color.White, fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    entry.providerResponse?.let { Text(it, color = PluginMuted, fontSize = 10.sp, lineHeight = 12.sp) }
                }
                Text("›", color = PluginMuted, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Box(Modifier.width(64.dp).height(34.dp), contentAlignment = Alignment.Center) {
                    TvContentToggle(entry.switchChecked == true, compact = true)
                }
            }
            if (!entry.providerPanelEnd) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
        }
    }
}
