package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign

/** Account identity, live status and related actions stay together. */
@Composable
internal fun TvAccountSummaryPanel(entries: List<TvSettingsEntry>, host: TvSettingsEmbeddedHost) {
    TvAccountSummaryPanel(entries, host.requesterFor, host.onRowFocused, host.onLeftToCategory)
}

@Composable
internal fun TvAccountSummaryPanel(
    entries: List<TvSettingsEntry>,
    requesterFor: (String) -> FocusRequester,
    onRowFocused: (String) -> Unit,
    onLeftToCategory: () -> Unit,
) {
    val account = entries.firstOrNull { it.id == "status" }
    val status = entries.firstOrNull { it.id == "message" }
    val actionIds = listOf("sync-all", "sync-now", "sign-out", "cancel-sign-out")
    val actions = actionIds.mapNotNull { id -> entries.firstOrNull { it.id == id } }
    val enabledActions = actions.filter { it.enabled }
    val nextOption = entries.firstOrNull { it.enabled && it.id !in actionIds }
    Column(
        Modifier.fillMaxWidth().background(TvDesign.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(account?.subtitle.orEmpty(), color = TvDesign.White, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status?.subtitle.orEmpty(), color = TvDesign.Muted, fontSize = 12.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Text(account?.value.orEmpty(), color = TvDesign.Accent, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { action ->
                var focused by remember(action.id) { mutableStateOf(false) }
                val index = enabledActions.indexOfFirst { it.id == action.id }
                val shape = RoundedCornerShape(9.dp)
                Box(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .focusRequester(requesterFor(action.id))
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = nextOption?.let { requesterFor(it.id) } ?: FocusRequester.Cancel
                            if (index > 0) left = requesterFor(enabledActions[index - 1].id)
                            if (index >= 0 && index < enabledActions.lastIndex) right = requesterFor(enabledActions[index + 1].id)
                            else right = FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onRowFocused(action.id)
                        }
                        .onPreviewKeyEvent { event ->
                            if (!action.enabled) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> if (index == 0) {
                                    if (event.type == KeyEventType.KeyDown) onLeftToCategory()
                                    true
                                } else false
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (event.type == KeyEventType.KeyUp) action.onActivate?.invoke()
                                    true
                                }
                                else -> false
                            }
                        }
                        .background(if (focused) TvDesign.White.copy(alpha = .18f) else TvDesign.White.copy(alpha = .06f), shape)
                        .border(if (focused) 1.5.dp else 0.dp, if (focused) TvDesign.White else TvDesign.Surface, shape)
                        .focusable(enabled = action.enabled)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.title, color = if (action.enabled) TvDesign.White else TvDesign.Dim,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
