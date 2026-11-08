package com.vueo.tv.player

/** Conservative timeline evidence, independent of caption language and provider. */
internal object TvSubtitleTemporalPolicy {
    private val vttPositionSettings = Regex("(?:^|\\s)(?:line|position|size|vertical):")
    fun hasVttPositionSettings(timing: String): Boolean =
        "-->" in timing && vttPositionSettings.containsMatchIn(timing.substringAfter("-->"))

    fun automaticPlacement(line: Float?, numbered: Boolean, horizontal: Float?, width: Float?,
        vertical: Boolean = false, bitmap: Boolean = false, shear: Float = 0f): Boolean =
        !vertical && !bitmap && shear == 0f &&
            (line == null || (numbered && line < 0f)) &&
            (horizontal == null || horizontal == .5f) && (width == null || width == 1f)

    data class Event(val text: String, val startUs: Long, val endUs: Long)
    data class Region(val startUs: Long, val endUs: Long, val info: Set<String>, val dialogue: Set<String>)

    fun analyze(input: List<Event>): List<Region> {
        // Parser output can split a persistent cue at each other cue's boundary.
        val merged = input.filter { it.endUs > it.startUs && it.text.isNotBlank() }
            .groupBy { it.text }.flatMap { (text, spans) ->
                val out = mutableListOf<Event>()
                for (event in spans.sortedBy { it.startUs }) {
                    val last = out.lastOrNull()
                    if (last != null && event.startUs <= last.endUs) {
                        out[out.lastIndex] = last.copy(endUs = maxOf(last.endUs, event.endUs))
                    } else out.add(event.copy(text = text))
                }
                out
            }.sortedBy { it.startUs }
        val regions = mutableListOf<Region>()
        for ((interval, group) in merged.groupBy { it.startUs to it.endUs }) {
            val (start, end) = interval
            // A multi-line persistent block plus two successive, shorter cues is
            // stronger evidence than duration alone. Single long dialogue is untouched.
            if (end - start < 6_000_000L || group.size < 2) continue
            val info = group.map { it.text }.toSet()
            var low = 0
            var high = merged.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (merged[mid].startUs < start) low = mid + 1 else high = mid
            }
            // Bound local work for malformed/dense files rather than scanning the
            // entire episode for every long block.
            val shorter = merged.asSequence().drop(low).take(512).takeWhile { it.startUs < end }.filter {
                it.startUs >= start && it.endUs <= end && it.text !in info &&
                    it.endUs - it.startUs <= (end - start) / 2
            }.take(256).toList()
            if (shorter.none { a -> shorter.any { b -> a.endUs <= b.startUs && a.text != b.text } }) continue
            regions.add(Region(start, end, info, shorter.map { it.text }.toSet()))
        }
        return regions
    }
}
