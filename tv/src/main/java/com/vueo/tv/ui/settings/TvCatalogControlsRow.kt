package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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

@Composable
internal fun TvCatalogControlsRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    first: Boolean,
    last: Boolean,
    modifier: Modifier,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    val up = remember(entry.id) { FocusRequester() }
    val down = remember(entry.id) { FocusRequester() }
    Row(
        modifier = modifier.fillMaxWidth().focusGroup()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    ((first && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) ||
                        (last && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN))
            }
            .background(TvDesign.SurfaceRaised, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.badge.orEmpty(), color = TvDesign.Muted, fontSize = 15.sp,
            modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = TvDesign.White, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, color = TvDesign.Muted, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvCatalogControl(if (entry.switchChecked == true) "ON" else "OFF", requester, true,
                onFocused, onLeftToSidebar, { up.requestFocus() }, { entry.onActivate?.invoke() })
            TvCatalogControl("↑", up, entry.onPrevious != null, onFocused,
                { requester.requestFocus() }, { down.requestFocus() }, { entry.onPrevious?.invoke() })
            TvCatalogControl("↓", down, entry.onNext != null, onFocused,
                { up.requestFocus() }, {}, { entry.onNext?.invoke() })
        }
    }
}

@Composable
private fun TvCatalogControl(
    label: String,
    requester: FocusRequester,
    available: Boolean,
    onFocused: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    action: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (event.type == KeyEventType.KeyDown) onLeft()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.type == KeyEventType.KeyDown) onRight()
                        true
                    }
                    else -> false
                }
            }
            .background(if (focused) TvDesign.White.copy(alpha = .20f) else TvDesign.White.copy(alpha = .06f),
                RoundedCornerShape(24.dp))
            .border(if (focused) 2.dp else 1.dp,
                TvDesign.White.copy(alpha = if (focused) .95f else .12f), RoundedCornerShape(24.dp))
            // Boundary arrows remain focusable so reordering never drops remote focus.
            .clickable { if (available) action() }
            .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = TvDesign.White.copy(alpha = if (available) 1f else .35f),
            fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (label == "ON" || label == "OFF") {
            Box(
                modifier = Modifier.width(36.dp).height(20.dp)
                    .background(TvDesign.White.copy(alpha = if (label == "ON") .35f else .12f),
                        RoundedCornerShape(20.dp)).padding(3.dp),
                contentAlignment = if (label == "ON") Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(14.dp).background(TvDesign.White, RoundedCornerShape(14.dp)))
            }
        }
    }
}
