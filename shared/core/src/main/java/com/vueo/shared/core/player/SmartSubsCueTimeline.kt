package com.vueo.shared.core.player

/** Immutable interval index; seeks and subtitle offsets read the video clock directly. */
internal class SmartSubsCueTimeline<T>(spans: List<Span<T>>) {
    data class Span<T>(val startUs: Long, val endUs: Long, val values: List<T>)
    private val entries = spans.filter { it.startUs >= 0 && it.endUs > it.startUs }.sortedBy { it.startUs }
    private val maxEnd = LongArray(entries.size).also { out ->
        entries.indices.forEach { i -> out[i] = maxOf(entries[i].endUs, if (i == 0) 0 else out[i - 1]) }
    }

    fun at(timeUs: Long): List<T> {
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].startUs <= timeUs) low = mid + 1 else high = mid
        }
        val matches = mutableListOf<List<T>>()
        var index = low - 1
        while (index >= 0 && maxEnd[index] > timeUs) {
            val span = entries[index]
            if (span.endUs > timeUs) matches.add(span.values)
            index--
        }
        return matches.asReversed().flatten()
    }
}
