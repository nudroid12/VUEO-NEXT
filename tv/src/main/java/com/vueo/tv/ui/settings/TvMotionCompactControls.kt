package com.vueo.tv.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Compact controls share the settings actions and the actual app's motion values. */
@Composable
internal fun TvMotionFloatingEntries(
    entries: List<TvSettingsEntry>,
    group: TvMotionGroup,
    value: TvMotionValues,
    edit: ((TvMotionValues) -> TvMotionValues) -> Unit,
) {
    val byId = entries.associateBy { it.id }
    var replay by remember { mutableIntStateOf(0) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedId by remember { mutableStateOf("motion-enabled") }
    val controls = entries.filter { it.id !in setOf("motion-preview", "motion-save", "motion-export", "motion-reset-component", "motion-reset-all", "motion-status") }
    val ids = controls.map { it.id }
    ids.forEach { requesters.getOrPut(it) { FocusRequester() } }
    LaunchedEffect(ids) {
        // Only repair focus when a conditional control disappears, not on every value edit.
        if (focusedId !in ids && focusedId !in setOf("motion-preview", "motion-save", "motion-reset-component", "motion-export", "motion-reset-all")) {
            focusedId = "motion-style"
            withFrameNanos { }
            runCatching { requesters.getValue(focusedId).requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvMotionInlinePreview(group, value, replay)
        Text("Changes apply live · Hide to test on this page", color = TvDesign.Muted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("motion-enabled" to "Live", "motion-reduce" to "Reduce").forEach { (id, label) ->
                val entry = byId.getValue(id)
                TvMotionCompactButton("$label: ${entry.value}", Modifier.weight(1f).focusRequester(requesters.getValue(id)), onFocused = { focusedId = id }) { entry.onActivate?.invoke() }
            }
        }
        listOf("motion-group", "motion-style").forEach { id ->
            TvMotionCompactControl(byId.getValue(id), value, edit, requesters.getValue(id)) { focusedId = id }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(controls.filter { it.id !in setOf("motion-enabled", "motion-reduce", "motion-group", "motion-style") }, key = { it.id }) { entry ->
                TvMotionCompactControl(entry, value, edit, requesters.getValue(entry.id)) { focusedId = entry.id }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TvMotionCompactButton("↻ Replay", Modifier.weight(1f), onFocused = { focusedId = "motion-preview" }) { replay++ }
            TvMotionCompactButton("Save", Modifier.weight(1f), onFocused = { focusedId = "motion-save" }) { byId.getValue("motion-save").onActivate?.invoke() }
            TvMotionCompactButton("Reset", Modifier.weight(1f), onFocused = { focusedId = "motion-reset-component" }) { byId.getValue("motion-reset-component").onActivate?.invoke() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TvMotionCompactButton("Export JSON", Modifier.weight(1f), onFocused = { focusedId = "motion-export" }) { byId.getValue("motion-export").onActivate?.invoke() }
            TvMotionCompactButton("Reset all", Modifier.weight(1f), onFocused = { focusedId = "motion-reset-all" }) { byId.getValue("motion-reset-all").onActivate?.invoke() }
        }
        Text(byId.getValue("motion-status").subtitle, color = TvDesign.Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TvMotionCompactButton(
    label: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.heightIn(min = 30.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .background(if (focused) TvDesign.Accent.copy(alpha = .18f) else TvDesign.Black.copy(alpha = .20f), RoundedCornerShape(8.dp))
        .border(if (focused) 2.dp else 1.dp, if (focused) TvDesign.Accent else TvDesign.Muted.copy(alpha = .3f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, color = TvDesign.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class MotionRange(val current: Float, val min: Float, val max: Float, val step: Float, val change: (TvMotionValues, Float) -> TvMotionValues)
private fun motionRange(id: String, v: TvMotionValues): MotionRange? = when (id) {
    "motion-enter" -> MotionRange(v.enterMs.toFloat(), 0f, 1500f, 10f) { a, n -> a.copy(enterMs = n.roundToInt()) }
    "motion-exit" -> MotionRange(v.exitMs.toFloat(), 0f, 1500f, 10f) { a, n -> a.copy(exitMs = n.roundToInt()) }
    "motion-delay" -> MotionRange(v.exitDelayMs.toFloat(), 0f, 500f, 10f) { a, n -> a.copy(exitDelayMs = n.roundToInt()) }
    "motion-stiffness" -> MotionRange(v.stiffness, 40f, 1500f, 20f) { a, n -> a.copy(stiffness = n) }
    "motion-damping" -> MotionRange(v.damping, .4f, 1.5f, .05f) { a, n -> a.copy(damping = n) }
    "motion-scale" -> MotionRange(v.scale, .8f, 1.2f, .005f) { a, n -> a.copy(scale = n) }
    "motion-alpha" -> MotionRange(v.initialAlpha, 0f, 1f, .05f) { a, n -> a.copy(initialAlpha = n) }
    "motion-distance" -> MotionRange(v.travelPercent.toFloat(), 0f, 30f, 1f) { a, n -> a.copy(travelPercent = n.roundToInt()) }
    else -> null
}

@Composable
private fun TvMotionCompactControl(
    entry: TvSettingsEntry, value: TvMotionValues,
    edit: ((TvMotionValues) -> TvMotionValues) -> Unit,
    requester: FocusRequester, onFocused: () -> Unit,
) {
    val range = motionRange(entry.id, value)
    val title = when (entry.id) {
        "motion-enabled" -> "Live tuning"
        "motion-reduce" -> "Reduce motion"
        "motion-spring" -> "Spring"
        "motion-enter" -> if (TvMotionBubble.selectedGroup.value == TvMotionGroup.HERO) "Crossfade" else "Enter / focus in"
        "motion-exit" -> "Exit / focus out"
        "motion-delay" -> "Exit hold"
        "motion-scale" -> "Scale"
        "motion-alpha" -> "Start opacity"
        else -> entry.title
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = TvDesign.White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            if (entry.onPrevious != null) TvMotionCompactButton("−", onFocused = onFocused) { entry.onPrevious.invoke() }
            TvMotionCompactButton(entry.value, Modifier.widthIn(max = 190.dp).focusRequester(requester).onPreviewKeyEvent { event ->
                val action = when (event.key) { Key.DirectionLeft -> entry.onPrevious; Key.DirectionRight -> entry.onNext; else -> null }
                if (action == null) false else { if (event.type == KeyEventType.KeyDown) action(); true }
            }, onFocused = onFocused) { entry.onActivate?.invoke() }
            if (entry.onNext != null) TvMotionCompactButton("+", onFocused = onFocused) { entry.onNext.invoke() }
        }
        if (range != null) Slider(
            value = range.current.coerceIn(range.min, range.max),
            onValueChange = { raw ->
                val snapped = (range.min + ((raw - range.min) / range.step).roundToInt() * range.step).coerceIn(range.min, range.max)
                edit { range.change(it, snapped) }
            },
            valueRange = range.min..range.max,
            modifier = Modifier.fillMaxWidth().height(24.dp).focusProperties { canFocus = false },
            colors = SliderDefaults.colors(thumbColor = TvDesign.Accent, activeTrackColor = TvDesign.Accent),
        )
    }
}

@Composable
private fun TvMotionInlinePreview(group: TvMotionGroup, value: TvMotionValues, replay: Int) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(group, value, replay, TvMotionTuning.enabled, TvMotionTuning.reduceMotion) {
        showing = false
        // Cancellation makes consecutive edits/replays interruptible.
        delay((if (value.useSpring) 320 else value.exitMs + value.exitDelayMs + 60).toLong())
        showing = true
    }
    val progress by animateFloatAsState(if (showing) 1f else 0f, tvTunedSpec(group, showing, tween(group.enterMs)), label = "inlineMotionPreview")
    Box(Modifier.fillMaxWidth().height(82.dp).background(TvDesign.Black, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (group.scalar) {
            val scale = if (group == TvMotionGroup.FOCUS) 1f + (tvTunedFocusScale(1.025f) - 1f) * progress else 1f
            Box(Modifier.width(if (group == TvMotionGroup.TOPBAR) (70f + 90f * progress).dp else 150.dp).height(48.dp).graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (group == TvMotionGroup.HERO || group == TvMotionGroup.IMAGE) progress.coerceIn(0f, 1f) else 1f
            }.background(TvDesign.Accent, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("VUEO", color = TvDesign.Black, fontSize = 16.sp)
            }
        } else {
            val recipe = when (group) {
                TvMotionGroup.FORWARD -> tvScreenFadeThrough()
                TvMotionGroup.BACK -> tvScreenBackTransition()
                TvMotionGroup.TABS -> tvTabCrossTransition()
                TvMotionGroup.PLAYER_ROUTE -> tvPlayerFadeThrough()
                else -> tvTunedEnter(group, androidx.compose.animation.fadeIn(tween(group.enterMs))) togetherWith
                    tvTunedExit(group, androidx.compose.animation.fadeOut(tween(group.exitMs)))
            }
            androidx.compose.animation.AnimatedVisibility(showing, enter = recipe.targetContentEnter, exit = recipe.initialContentExit) {
                Box(Modifier.size(150.dp, 48.dp).background(TvDesign.Accent, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("VUEO", color = TvDesign.Black, fontSize = 16.sp)
                }
            }
        }
        Text("PREVIEW", color = TvDesign.Muted, fontSize = 8.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}
