package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.vueo.tv.ui.TvDesign
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PluginSurface = Color(0xFF303030)
private val PluginFocus = Color(0xFF555555)
private val PluginMuted = Color.White.copy(alpha = .6f)
private val PluginFailure = Color(0xFFFFB4AB)

internal fun TvSettingsEntry.isRepositoryAction(): Boolean =
    id == "refresh-repository" || id == "remove-repository"

@Composable
internal fun TvContentToggle(checked: Boolean) {
    // The enclosing row handles D-pad activation; this switch displays that same saved state.
    Switch(
        checked = checked, onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(0xFF202020), checkedTrackColor = Color.White,
            checkedBorderColor = Color.White,
            uncheckedThumbColor = Color.White.copy(alpha = .65f),
            uncheckedTrackColor = Color(0xFF454545), uncheckedBorderColor = Color.White.copy(alpha = .25f),
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
        !contentLayout -> TvSettingsRow(entry, requester, first, last, onLeftToSidebar = onLeftToSidebar, onFocused = onFocused)
        entry.id.startsWith("repo-") -> TvPluginRepositoryCard(entry, entries, requesterFor, onLeftToSidebar, onEntryFocused)
        entry.providerStatus != null -> TvPluginProviderRow(entry, requester, last, onLeftToSidebar, onFocused)
        else -> TvSettingsRow(entry, requester, first, last, onLeftToSidebar = onLeftToSidebar, onFocused = onFocused)
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
    val shape = RoundedCornerShape(15.dp)
    Column(modifier = Modifier.padding(bottom = if (nextProvider != null) 12.dp else 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(PluginSurface, shape)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).background(Color(0xFF242424), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.detail.orEmpty(), color = PluginMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                refresh?.let { action ->
                    TvRepositoryAction(action, Icons.Default.Refresh, Color.White, requesterFor(action.id), Modifier.focusProperties {
                        up = FocusRequester.Default
                        left = FocusRequester.Cancel
                        right = requesterFor(entry.id)
                        down = if (nextProvider != null) FocusRequester.Default else FocusRequester.Cancel
                    }.onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (event.type == KeyEventType.KeyDown) onLeftToSidebar()
                            true
                        } else false
                    }, { onEntryFocused(action.id) })
                }
                Column(
                    modifier = Modifier.width(76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier.width(76.dp).height(38.dp)
                            .focusRequester(requesterFor(entry.id))
                            .focusProperties {
                                up = FocusRequester.Default
                                left = refresh?.let { requesterFor(it.id) } ?: FocusRequester.Default
                                right = FocusRequester.Cancel
                                down = remove?.let { requesterFor(it.id) }
                                    ?: if (nextProvider != null) FocusRequester.Default else FocusRequester.Cancel
                            }
                            .onFocusChanged {
                                toggleFocused = it.isFocused
                                if (it.isFocused) onEntryFocused(entry.id)
                            }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && refresh == null && event.type == KeyEventType.KeyDown -> { onLeftToSidebar(); true }
                                    activation(event.nativeKeyEvent.keyCode) -> {
                                        if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .background(if (toggleFocused) PluginFocus else Color.Transparent, RoundedCornerShape(50))
                            .border(1.dp, if (toggleFocused) Color.White else Color.Transparent, RoundedCornerShape(50))
                            .focusable(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvContentToggle(entry.switchChecked ?: (entry.value == "On"))
                    }
                    remove?.let { action ->
                        TvRepositoryAction(action, null, if (entry.accented) TvDesign.Accent else Color.White.copy(alpha = .86f), requesterFor(action.id), Modifier.focusProperties {
                            up = requesterFor(entry.id)
                            left = refresh?.let { requesterFor(it.id) } ?: requesterFor(entry.id)
                            right = FocusRequester.Cancel
                            down = if (nextProvider != null) FocusRequester.Default else FocusRequester.Cancel
                        }, { onEntryFocused(action.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRepositoryAction(entry: TvSettingsEntry, icon: ImageVector?, tint: Color, requester: FocusRequester, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val refreshing = entry.id == "refresh-repository" && entry.value == "Refreshing…"
    IconButton(
        onClick = { entry.onActivate?.invoke() },
        modifier = modifier.height(38.dp).width(if (icon == null) 76.dp else 38.dp).focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .background(if (focused) PluginFocus else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50)),
    ) {
        if (icon == null) Text("Remove ›", color = tint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        else {
            Icon(
                icon,
                contentDescription = if (refreshing) "Refreshing repository" else entry.title,
                tint = tint,
                modifier = Modifier.size(21.dp).then(tvContentRefreshMotion(entry)),
            )
        }
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
                    .border(1.dp, if (focused) Color.White else Color.Transparent, shape)
                    .focusable().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle, color = PluginMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(entry.providerStatus.orEmpty(), color = if (entry.providerFailed) PluginFailure else if (entry.switchChecked == false) PluginMuted else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    entry.providerResponse?.let { Text(it, color = PluginMuted, fontSize = 10.sp) }
                }
                Text("›", color = PluginMuted, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp))
                TvContentToggle(entry.switchChecked == true)
            }
            if (!entry.providerPanelEnd) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
        }
    }
}
