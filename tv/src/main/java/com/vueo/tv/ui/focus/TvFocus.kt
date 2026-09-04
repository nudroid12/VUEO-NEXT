package com.vueo.tv.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class TvFocusZone {
    Nav,
    Hero,
    Rail,
}

object TvFocusMemory {
    var lastZone: TvFocusZone = TvFocusZone.Hero
        private set

    var lastNavLabel: String = "Home"
        private set

    var lastHeroAction: Int = 0
        private set

    var lastRowId: String? = null
        private set

    var lastMediaKey: String? = null
        private set

    var lastRailColumn: Int = 0
        private set

    private val rowIndexes = linkedMapOf<String, Int>()

    fun rememberNav(label: String) {
        lastZone = TvFocusZone.Nav
        lastNavLabel = label
    }

    fun rememberHero(actionIndex: Int) {
        lastZone = TvFocusZone.Hero
        lastHeroAction = actionIndex.coerceAtLeast(0)
    }

    fun rememberRail(
        rowId: String,
        itemIndex: Int,
        mediaKey: String,
    ) {
        val safeIndex = itemIndex.coerceAtLeast(0)
        lastZone = TvFocusZone.Rail
        lastRowId = rowId
        lastMediaKey = mediaKey
        lastRailColumn = safeIndex
        rowIndexes[rowId] = safeIndex
    }

    fun railIndex(
        rowId: String,
        itemCount: Int,
    ): Int {
        if (itemCount <= 0) return 0
        return (rowIndexes[rowId] ?: lastRailColumn).coerceIn(0, itemCount - 1)
    }

    fun resetToHero() {
        lastZone = TvFocusZone.Hero
        lastHeroAction = 0
        lastRowId = null
        lastMediaKey = null
        lastRailColumn = 0
    }
}

fun Modifier.tvVerticalFocus(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }

        when (event.key) {
            Key.DirectionUp -> requestTvFocus(up)
            Key.DirectionDown -> requestTvFocus(down)
            else -> false
        }
    }

fun Modifier.tvHorizontalEdgeGuard(
    blockLeft: Boolean,
    blockRight: Boolean,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }

        when (event.key) {
            Key.DirectionLeft -> blockLeft
            Key.DirectionRight -> blockRight
            else -> false
        }
    }

private fun requestTvFocus(target: FocusRequester?): Boolean {
    if (target == null) return false
    return runCatching {
        target.requestFocus()
        true
    }.getOrDefault(false)
}
