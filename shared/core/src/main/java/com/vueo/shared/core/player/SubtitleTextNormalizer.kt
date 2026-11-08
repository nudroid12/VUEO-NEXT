package com.vueo.shared.core.player

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Linear scan of SRT timestamp lines; dialogue and line endings remain intact. */
internal object SubtitleTextNormalizer {
    private data class Time(val start: Int, val separator: Int, val end: Int)

    private fun digit(c: Char) = c in '0'..'9'
    private fun space(c: Char) = c == ' ' || c == '\t'
    private fun skipSpace(s: String, start: Int, end: Int): Int {
        var i = start
        while (i < end && space(s[i])) i++
        return i
    }

    private fun time(s: String, start: Int, end: Int): Time? {
        var i = start
        while (i < end && digit(s[i])) i++
        val firstDigits = i - start
        if (firstDigits == 0 || i >= end || s[i] != ':') return null
        i++
        val middle = i
        while (i < end && digit(s[i])) i++
        val middleDigits = i - middle
        if (i < end && s[i] == ':') {
            if (middleDigits !in 1..2) return null
            i++
            val seconds = i
            while (i < end && digit(s[i])) i++
            if (i - seconds != 2) return null
        } else if (firstDigits !in 1..2 || middleDigits != 2) return null
        if (i >= end || (s[i] != '.' && s[i] != ',')) return null
        val separator = i++
        val fraction = i
        while (i < end && digit(s[i])) i++
        if (i - fraction !in 1..3) return null
        return Time(start, separator, i)
    }

    private fun indexLine(s: String, start: Int, end: Int): Boolean {
        var i = start
        if (i < end && s[i] == '\uFEFF') i++
        i = skipSpace(s, i, end)
        val digits = i
        while (i < end && digit(s[i])) i++
        return i > digits && skipSpace(s, i, end) == end
    }

    private fun arrow(s: String, start: Int, end: Int): Int? {
        val i = skipSpace(s, start, end)
        return if (i + 3 <= end && s[i] == '-' && s[i + 1] == '-' && s[i + 2] == '>') i + 3 else null
    }

    private fun appendTime(out: StringBuilder, s: String, t: Time) {
        out.append(s, t.start, t.separator).append(',')
        out.append(s, t.separator + 1, t.end)
        repeat(3 - (t.end - t.separator - 1)) { out.append('0') }
    }

    fun normalize(bytes: ByteArray): ByteArray {
        val s = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { return bytes }

        // Keep the original SRT-only gate: WEBVTT/ASS/other text is passed through.
        var first = 0
        while (first < s.length && (s[first] == '\uFEFF' || space(s[first]) || s[first] == '\r' || s[first] == '\n')) first++
        val firstEnd = s.indexOf('\n', first)
        if (firstEnd < 0) return bytes
        val indexEnd = if (firstEnd > first && s[firstEnd - 1] == '\r') firstEnd - 1 else firstEnd
        if (!indexLine(s, first, indexEnd)) return bytes
        val cueStart = skipSpace(s, firstEnd + 1, s.length)
        val firstTime = time(s, cueStart, s.length) ?: return bytes
        if (arrow(s, firstTime.end, s.length) == null) return bytes

        var start = 0
        var previousIndex = false
        var copied = 0
        var output: StringBuilder? = null
        while (start < s.length) {
            val newline = s.indexOf('\n', start).let { if (it < 0) s.length else it }
            val end = if (newline > start && s[newline - 1] == '\r') newline - 1 else newline
            if (previousIndex) {
                val a = time(s, skipSpace(s, start, end), end)
                val afterArrow = a?.let { arrow(s, it.end, end) }
                val b = afterArrow?.let { time(s, skipSpace(s, it, end), end) }
                if (a != null && b != null && skipSpace(s, b.end, end) == end) {
                    val line = StringBuilder(end - start + 8)
                    appendTime(line, s, a)
                    line.append(" --> ")
                    appendTime(line, s, b)
                    line.append(s, b.end, end)
                    val replacement = line.toString()
                    if (replacement != s.substring(start, end)) {
                        val out = output ?: StringBuilder(s.length + 32).also { output = it }
                        out.append(s, copied, start).append(replacement)
                        copied = end
                    }
                }
            }
            previousIndex = indexLine(s, start, end)
            start = if (newline == s.length) s.length else newline + 1
        }
        val out = output ?: return bytes
        out.append(s, copied, s.length)
        return out.toString().toByteArray(Charsets.UTF_8)
    }
}
