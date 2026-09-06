package com.vueo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.delay

@Composable
internal fun TvHomePresentation(
    rows: List<TvHomeRow>,
    loading: Boolean,
    error: String?,
    contentFocusRequester: FocusRequester,
    onContentFocused: () -> Unit,
    onOpen: (TvHomeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedEntry by remember { mutableStateOf<TvHomeEntry?>(null) }
    var heroEntry by remember { mutableStateOf<TvHomeEntry?>(null) }

    val allEntries = remember(rows) { rows.flatMap(TvHomeRow::entries) }

    LaunchedEffect(rows) {
        val savedRowKey = TvHomeFocusMemory.activeRowKey
        val savedRow = rows.firstOrNull { it.key == savedRowKey }
        val initial = if (savedRow != null) {
            val index = (TvHomeFocusMemory.focusedIndexByRow[savedRow.key] ?: 0)
                .coerceIn(0, savedRow.entries.lastIndex)
            savedRow.entries[index]
        } else {
            allEntries.firstOrNull()
        }
        if (focusedEntry == null || focusedEntry !in allEntries) focusedEntry = initial
        if (heroEntry == null || heroEntry !in allEntries) heroEntry = initial
    }

    LaunchedEffect(focusedEntry?.key) {
        val next = focusedEntry ?: return@LaunchedEffect
        delay(MODERN_HOME_HERO_FOCUS_SETTLE_MS)
        if (focusedEntry?.key == next.key) heroEntry = next
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        val screenHeight = maxHeight
        val rowsViewportHeight = screenHeight * MODERN_HOME_ROWS_VIEWPORT_FRACTION
        val heroHeight = (screenHeight - rowsViewportHeight + 38.dp).coerceAtMost(screenHeight)

        TvModernHomeHero(
            entry = heroEntry,
            heroHeight = heroHeight,
            rowsViewportHeight = rowsViewportHeight,
        )

        when {
            rows.isNotEmpty() -> {
                TvModernHomeRows(
                    rows = rows,
                    rowsViewportHeight = rowsViewportHeight,
                    contentFocusRequester = contentFocusRequester,
                    onContentFocused = onContentFocused,
                    onFocused = { row, index, entry ->
                        TvHomeFocusMemory.activeRowKey = row.key
                        TvHomeFocusMemory.focusedIndexByRow[row.key] = index
                        focusedEntry = entry
                    },
                    onOpen = onOpen,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }

            loading -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 52.dp, bottom = rowsViewportHeight * .18f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        color = TvDesign.White,
                        strokeWidth = 2.dp,
                    )
                    Text("Loading Home", color = TvDesign.Muted, fontSize = 13.sp)
                }
            }

            error != null -> {
                Text(
                    text = error,
                    color = TvDesign.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 52.dp, bottom = rowsViewportHeight * .18f),
                )
            }
        }
    }
}
