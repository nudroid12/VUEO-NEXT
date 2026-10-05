package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TvEpisodeRangeState(val episodes: List<EpisodeItem>, initialId: String?) {
    val groups = episodes.chunked(50)
    val enabled get() = groups.size > 1
    var group by mutableIntStateOf(episodes.indexOfFirst { it.id == initialId }.coerceAtLeast(0) / 50)
    var targetId by mutableStateOf(episodes.firstOrNull { it.id == initialId }?.id ?: episodes.firstOrNull()?.id)
    var focusRequest by mutableIntStateOf(0)
    var focusCards by mutableStateOf(false)
    val visible get() = groups.getOrNull(group).orEmpty()
    fun select(index: Int, focusCards: Boolean = false, last: Boolean = false) {
        if (index !in groups.indices) return
        if (index == group && !focusCards) return
        this.focusCards = focusCards
        group = index
        targetId = (if (last) visible.lastOrNull() else visible.firstOrNull())?.id
        if (focusCards) focusRequest++
    }
    fun jump(number: Int): Boolean {
        val index = episodes.indexOfFirst { it.episode == number }
        if (index < 0) return false
        focusCards = true
        group = index / 50
        targetId = episodes[index].id
        focusRequest++
        return true
    }
}

@Composable
internal fun rememberTvEpisodeRanges(episodes: List<EpisodeItem>, initialId: String?): TvEpisodeRangeState {
    val ordered = remember(episodes) { episodes.sortedBy { it.episode } }
    return remember(ordered) { TvEpisodeRangeState(ordered, initialId) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvEpisodeRangeControls(
    state: TvEpisodeRangeState,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onInteraction: () -> Unit = {},
) {
    if (!state.enabled) return
    // Every range chip stays mounted: moving focus never waits for lazy composition.
    val row = rememberScrollState()
    var dialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val chipRequesters = remember(state) { List(state.groups.size + 1) { FocusRequester() } }
    val chipWidths = remember(state) { mutableStateMapOf<Int, Int>() }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var focusedIndex by remember(state) { mutableIntStateOf(state.group) }
    var rowHasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var heldKey by remember { mutableIntStateOf(0) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val latestInteraction by rememberUpdatedState(onInteraction)
    DisposableEffect(state) { onDispose { repeatJob?.cancel() } }
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    // The explicit centering below owns scrolling; disable a second focus scroll.
    val noAutomaticScroll = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
        }
    }
    LaunchedEffect(state.group) {
        if (!rowHasFocus) focusedIndex = state.group
    }
    val centeredTarget by remember(state, row, gapPx) {
        derivedStateOf {
            val width = chipWidths[focusedIndex]
            if (width == null || viewportWidth <= 0 ||
                (0 until focusedIndex).any { it !in chipWidths }) null
            else {
                val left = (0 until focusedIndex).sumOf { chipWidths.getValue(it) } + gapPx * focusedIndex
                (left + width / 2f - viewportWidth / 2f).coerceIn(0f, row.maxValue.toFloat())
            }
        }
    }
    // One scroll owner for the lifetime of the row. Retarget without restarting
    // easing or dropping velocity on each single press / repeat event.
    LaunchedEffect(row, state) {
        var position = row.value.toFloat()
        var velocity = 0f
        while (true) {
            snapshotFlow { centeredTarget }.first { it != null && abs(it - row.value) > .5f }
            position = row.value.toFloat()
            var previousFrame = withFrameNanos { it }
            do {
                val frame = withFrameNanos { it }
                val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .05f)
                previousFrame = frame
                val target = centeredTarget ?: position
                // Exact critically damped spring step: stable at different frame rates.
                val frequency = 24f
                val displacement = position - target
                val coefficient = velocity + frequency * displacement
                val decay = exp(-frequency * dt)
                position = target + (displacement + coefficient * dt) * decay
                velocity = (velocity - frequency * coefficient * dt) * decay
                position = position.coerceIn(0f, row.maxValue.toFloat())
                row.scrollTo(position.roundToInt())
            } while (abs(position - (centeredTarget ?: position)) > .5f || abs(velocity) > 4f)
            centeredTarget?.let { row.scrollTo(it.roundToInt()) }
            velocity = 0f
        }
    }
    fun move(direction: Int) {
        val next = focusedIndex + direction
        if (next in chipRequesters.indices) {
            latestInteraction()
            chipRequesters[next].requestFocus()
        }
    }
    // Own the held key at the row, so repeat survives focus moving between chips.
    fun horizontalKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val direction = when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> return false
        }
        if (native.action == KeyEvent.ACTION_UP) {
            repeatJob?.cancel(); repeatJob = null; heldKey = 0
            if (focusedIndex in state.groups.indices) state.select(focusedIndex)
        } else if (native.action == KeyEvent.ACTION_DOWN && heldKey != native.keyCode) {
            repeatJob?.cancel()
            val keyCode = native.keyCode
            heldKey = keyCode
            move(direction)
            repeatJob = scope.launch {
                delay(320L)
                while (rowHasFocus && heldKey == keyCode) {
                    move(direction)
                    delay(160L)
                }
            }
        }
        return true
    }
    // Commit once the focus settles, avoiding repeated episode/image replacement while holding.
    LaunchedEffect(focusedIndex, rowHasFocus) {
        if (rowHasFocus && focusedIndex in state.groups.indices) {
            delay(190L)
            state.select(focusedIndex)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides noAutomaticScroll) {
    Row(
        modifier = modifier.fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .onFocusChanged {
                rowHasFocus = it.hasFocus
                if (!it.hasFocus) { repeatJob?.cancel(); repeatJob = null; heldKey = 0 }
            }
            .onPreviewKeyEvent { horizontalKey(it) }
            .focusGroup()
            .horizontalScroll(row)
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.groups.forEachIndexed { index, group ->
        key(index) {
            var focused by remember { mutableStateOf(false) }
            val selected = index == state.group
            Box(Modifier.onSizeChanged { chipWidths[index] = it.width }.focusRequester(chipRequesters[index]).focusRequester(if (selected) requester else chipRequesters[index])
                .focusProperties { up = upRequester; if (index == 0) left = FocusRequester.Cancel }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) { focusedIndex = index; onFocused(); onInteraction() } }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) false
                    else { if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) { state.select(index); onInteraction(); onDown() }; true }
                }
                .background(if (selected) Color.White else if (focused) Color(0xFF555555) else Color(0xFF303030), shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .15f), shape)
                .clickable { onInteraction(); state.select(index) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("${group.first().episode}–${group.last().episode}", color = if (selected) Color.Black else Color.White, fontSize = 11.sp)
            }
        }
        }
        key("jump") {
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.onSizeChanged { chipWidths[state.groups.size] = it.width }.focusRequester(chipRequesters.last()).focusProperties { up = upRequester; right = FocusRequester.Cancel }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) { focusedIndex = state.groups.size; onFocused(); onInteraction() } }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) false
                    else { if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onDown(); true }
                }
                .background(if (focused) Color(0xFF555555) else Color(0xFF303030), shape).border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .4f), shape)
                .clickable { onInteraction(); dialog = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Go to episode", color = Color.White, fontSize = 11.sp)
            }
        }
    }
    }
    if (dialog) {
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        val inputRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { inputRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { dialog = false; onInteraction() },
            containerColor = Color(0xFF303030), titleContentColor = Color.White,
            title = { Text("Go to episode") },
            text = { OutlinedTextField(value = input, onValueChange = { onInteraction(); input = it.filter(Char::isDigit).take(6); error = false }, singleLine = true,
                modifier = Modifier.focusRequester(inputRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Episode number") }, isError = error,
                supportingText = { if (error) Text("Episode not available in this season") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.Gray, focusedLabelColor = Color.White)) },
            confirmButton = { TextButton(onClick = { onInteraction(); if (input.toIntOrNull()?.let(state::jump) == true) dialog = false else error = true }) { Text("Go", color = Color.White) } },
            dismissButton = { TextButton(onClick = { dialog = false; onInteraction() }) { Text("Cancel", color = Color.White) } },
        )
    }
}
