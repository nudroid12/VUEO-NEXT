package com.vueo.tv.player

import kotlin.math.abs

/** Geometry only. Keeps the protected dialogue rectangles fixed; never classifies text. */
internal object TvSubtitleCollisionLayout {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val height: Float get() = bottom - top
        fun atTop(y: Float) = copy(top = y, bottom = y + height)
    }

    private fun conflicts(a: Box, b: Box, gap: Float): Boolean =
        a.left < b.right && a.right > b.left &&
            a.top < b.bottom + gap && a.bottom + gap > b.top

    fun resolve(box: Box, obstacles: List<Box>, top: Float, bottom: Float, gap: Float): Box {
        if (obstacles.none { conflicts(box, it, gap) }) return box
        if (box.height <= 0 || box.height > bottom - top) return box
        val candidates = obstacles.flatMap { listOf(it.top - gap - box.height, it.bottom + gap) } +
            listOf(top, bottom - box.height)
        return candidates.asSequence()
            .filter { it.isFinite() && it >= top && it + box.height <= bottom }
            .distinct()
            .sortedWith(compareBy<Float> { abs(it - box.top) }.thenBy { it })
            .map { box.atTop(it) }
            .firstOrNull { candidate -> obstacles.none { conflicts(candidate, it, gap) } }
            // If the screen cannot hold all cues, preserve the source instead of hiding text.
            ?: box
    }
}
