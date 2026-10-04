package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
    val row = rememberLazyListState(initialFirstVisibleItemIndex = state.group)
    var dialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    LaunchedEffect(state.group, state.focusRequest) { row.scrollToItem(state.group) }
    LazyRow(state = row, modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 5.dp)) {
        itemsIndexed(state.groups, key = { index, _ -> index }) { index, group ->
            var focused by remember { mutableStateOf(false) }
            val selected = index == state.group
            Box(Modifier.then(if (selected) Modifier.focusRequester(requester) else Modifier)
                .focusProperties { up = upRequester; if (index == 0) left = FocusRequester.Cancel }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) { onFocused(); onInteraction() } }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) false
                    else { if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) { onInteraction(); onDown() }; true }
                }
                .background(if (selected) Color.White else if (focused) Color(0xFF555555) else Color(0xFF303030), shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .15f), shape)
                .clickable { onInteraction(); state.select(index) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("${group.first().episode}–${group.last().episode}", color = if (selected) Color.Black else Color.White, fontSize = 11.sp)
            }
        }
        item(key = "jump") {
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.focusProperties { up = upRequester; right = FocusRequester.Cancel }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) { onFocused(); onInteraction() } }
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
