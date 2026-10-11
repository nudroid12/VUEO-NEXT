package com.vueo.tv.settings

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.tv.ui.LocalTvModalFocusHost
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotionGroup
import com.vueo.tv.ui.motion.TvMotionTuning
import kotlin.math.roundToInt

/** Session-only visibility; closing the tool does not disable the tuned motion. */
internal object TvMotionBubble {
    var visible by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    val selectedGroup = mutableStateOf(TvMotionGroup.FORWARD)
    fun open() { visible = true; expanded = true }
    fun hide() { expanded = false }
    fun close() { expanded = false; visible = false }
}

@Composable
internal fun TvMotionFloatingHost() {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val diameter = with(density) { 56.dp.toPx() }
    val dropRadius = with(density) { 76.dp.toPx() }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var bubbleFocused by remember { mutableStateOf(false) }
    fun close() { TvMotionTuning.save(context); TvMotionBubble.close() }
    if (!TvMotionBubble.visible) return
    Box(Modifier.fillMaxSize().onSizeChanged { bounds = it }) {
        val maxX = (bounds.width - diameter).coerceAtLeast(0f)
        val maxY = (bounds.height - diameter).coerceAtLeast(0f)
        val actual = (position ?: Offset(maxX - diameter / 2f, diameter / 2f)).let {
            Offset(it.x.coerceIn(0f, maxX), it.y.coerceIn(0f, maxY))
        }
        val target = Offset(bounds.width / 2f, bounds.height - diameter)
        val overTarget = (actual + Offset(diameter / 2f, diameter / 2f) - target).getDistance() <= dropRadius
        if (!TvMotionBubble.expanded) {
            if (dragging) Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).size(64.dp)
                    .background(if (overTarget) TvDesign.Accent else TvDesign.SurfaceRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = if (overTarget) TvDesign.Black else TvDesign.White, fontSize = 30.sp) }
            Box(
                Modifier.offset { IntOffset(actual.x.roundToInt(), actual.y.roundToInt()) }.size(56.dp)
                    .background(TvDesign.Accent, CircleShape)
                    .border(if (bubbleFocused) 3.dp else 0.dp, TvDesign.White, CircleShape)
                    .onFocusChanged { bubbleFocused = it.isFocused }
                    .pointerInput(bounds, diameter, dropRadius) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragCancel = { dragging = false },
                            onDragEnd = {
                                val p = position ?: actual
                                if ((p + Offset(diameter / 2f, diameter / 2f) - target).getDistance() <= dropRadius) close()
                                dragging = false
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                val p = (position ?: actual) + delta
                                position = Offset(p.x.coerceIn(0f, maxX), p.y.coerceIn(0f, maxY))
                            },
                        )
                    }
                    .clickable { TvMotionBubble.open() },
                contentAlignment = Alignment.Center,
            ) { Text("M", color = TvDesign.Black, fontSize = 22.sp) }
        }
    }
    if (TvMotionBubble.expanded) TvMotionFloatingWorkspace(onClose = ::close)
}

@Composable
private fun TvMotionFloatingWorkspace(onClose: () -> Unit) {
    val host = LocalTvModalFocusHost.current
    DisposableEffect(host) { host?.open?.invoke(); onDispose { host?.close?.invoke() } }
    Dialog(
        onDismissRequest = { TvMotionBubble.hide() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val originalDim = window?.attributes?.dimAmount
            val wasDimmed = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            onDispose {
                if (wasDimmed) window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                originalDim?.let { window?.setDimAmount(it) }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.padding(16.dp).width(460.dp).fillMaxHeight(.94f)
                    .background(TvDesign.SurfaceRaised, RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Live Motion", color = TvDesign.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { TvMotionBubble.hide() }) { Text("Hide", color = TvDesign.White) }
                    TextButton(onClick = onClose) { Text("Close", color = TvDesign.White) }
                }
                Text("Hide to test on this page · Menu to reopen", color = TvDesign.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                TvMotionSettings({}, {}, { TvMotionBubble.hide() }, floating = true)
            }
        }
    }
}

@Composable
internal fun TvMotionFloatingEntries(entries: List<TvSettingsEntry>) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    entries.forEach { requesters.getOrPut(it.id) { FocusRequester() } }
    val focusableIds = entries.filter { it.enabled }.map { it.id }
    var focusedId by remember { mutableStateOf(focusableIds.firstOrNull()) }
    LaunchedEffect(focusableIds) {
        if (focusedId !in focusableIds) focusedId = focusableIds.firstOrNull()
        focusedId?.let { runCatching { requesters.getValue(it).requestFocus() } }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            TvSettingsRow(
                entry = entry, requester = requesters.getValue(entry.id), first = false,
                last = index == entries.lastIndex, compact = true,
                onLeftToSidebar = {}, onFocused = { focusedId = entry.id },
            )
        }
    }
}
