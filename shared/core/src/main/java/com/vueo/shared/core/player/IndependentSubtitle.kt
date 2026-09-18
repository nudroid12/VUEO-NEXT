package com.vueo.shared.core.player

import com.vueo.shared.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Small sidecar-subtitle loader used when a subtitle arrives after playback has
 * already started. Keeping these cues outside ExoPlayer avoids rebuilding the
 * active video MediaSource just to attach a late subtitle track.
 */
data class TimedSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object IndependentSubtitleRepository {
    private const val MAX_CACHED_TRACKS = 12
    private val cache = ConcurrentHashMap<String, List<TimedSubtitleCue>>()
    private val cacheOrder = ArrayDeque<String>()
    private val cacheLock = Any()

    suspend fun load(url: String): List<TimedSubtitleCue> {
        cache[url]?.let { return it }
        val body = PluginHttp.getText(url)
        val parsed = withContext(Dispatchers.Default) {
            IndependentSubtitleParser.parse(body)
        }
        synchronized(cacheLock) {
            if (!cache.containsKey(url)) {
                cache[url] = parsed
                cacheOrder.remove(url)
                cacheOrder.addLast(url)
                while (cacheOrder.size > MAX_CACHED_TRACKS) {
                    cache.remove(cacheOrder.removeFirst())
                }
            }
        }
        return cache[url] ?: parsed
    }

    fun activeTexts(
        cues: List<TimedSubtitleCue>,
        positionMs: Long,
    ): List<String> {
        if (cues.isEmpty()) return emptyList()
        val position = positionMs.coerceAtLeast(0L)
        return cues.asSequence()
            .dropWhile { it.endMs <= position }
            .takeWhile { it.startMs <= position }
            .filter { position >= it.startMs && position < it.endMs }
            .map { it.text }
            .filter { it.isNotBlank() }
            .toList()
    }
}

internal object IndependentSubtitleParser {
    private val htmlTagRegex = Regex("<[^>]+>")
    private val assTagRegex = Regex("\\{[^}]*}")
    private val ttmlParagraphRegex = Regex(
        "<p\\b([^>]*)>(.*?)</p>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val attributeRegex = Regex(
        "([A-Za-z_:][A-Za-z0-9_.:-]*)\\s*=\\s*[\\\"']([^\\\"']*)[\\\"']",
    )

    fun parse(raw: String): List<TimedSubtitleCue> {
        val normalized = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val trimmed = normalized.trimStart()
        val parsed = when {
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> parseBlockFormat(normalized)
            trimmed.contains("[Events]", ignoreCase = true) ||
                Regex("(?m)^Dialogue:", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> parseAss(normalized)
            trimmed.startsWith("<tt", ignoreCase = true) ||
                trimmed.contains("<tt ", ignoreCase = true) -> parseTtml(normalized)
            else -> parseBlockFormat(normalized)
        }
        return parsed
            .filter { it.endMs > it.startMs && it.text.isNotBlank() }
            .sortedWith(compareBy<TimedSubtitleCue> { it.startMs }.thenBy { it.endMs })
    }

    private fun parseBlockFormat(raw: String): List<TimedSubtitleCue> {
        val lines = raw.lines()
        val result = mutableListOf<TimedSubtitleCue>()
        var index = 0

        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break

            val head = lines[index].trim()
            if (
                head.equals("WEBVTT", ignoreCase = true) ||
                head.startsWith("NOTE", ignoreCase = true) ||
                head.startsWith("STYLE", ignoreCase = true) ||
                head.startsWith("REGION", ignoreCase = true)
            ) {
                index++
                while (index < lines.size && lines[index].isNotBlank()) index++
                continue
            }

            var timingIndex = index
            if (!lines[timingIndex].contains("-->")) {
                timingIndex++
            }
            if (timingIndex >= lines.size || !lines[timingIndex].contains("-->")) {
                index++
                continue
            }

            val timing = lines[timingIndex].split("-->", limit = 2)
            val startMs = parseTimeMs(timing.getOrNull(0))
            val endToken = timing.getOrNull(1)
                ?.trim()
                ?.substringBefore(' ')
            val endMs = parseTimeMs(endToken)
            if (startMs == null || endMs == null) {
                index = timingIndex + 1
                continue
            }

            index = timingIndex + 1
            val textLines = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                textLines += lines[index]
                index++
            }
            val text = cleanText(textLines.joinToString("\n"))
            if (text.isNotBlank()) {
                result += TimedSubtitleCue(startMs, endMs, text)
            }
        }
        return result
    }

    private fun parseAss(raw: String): List<TimedSubtitleCue> {
        val result = mutableListOf<TimedSubtitleCue>()
        var inEvents = false
        var fields = listOf(
            "layer", "start", "end", "style", "name",
            "marginl", "marginr", "marginv", "effect", "text",
        )

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                return@forEach
            }
            if (!inEvents) return@forEach

            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                fields = trimmed.substringAfter(':')
                    .split(',')
                    .map { it.trim().lowercase() }
                return@forEach
            }
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) return@forEach

            val payload = trimmed.substringAfter(':').trimStart()
            val values = payload.split(',', limit = fields.size.coerceAtLeast(1))
            if (values.size < fields.size) return@forEach
            val startIndex = fields.indexOf("start")
            val endIndex = fields.indexOf("end")
            val textIndex = fields.indexOf("text")
            if (startIndex < 0 || endIndex < 0 || textIndex < 0) return@forEach

            val startMs = parseTimeMs(values.getOrNull(startIndex)) ?: return@forEach
            val endMs = parseTimeMs(values.getOrNull(endIndex)) ?: return@forEach
            val text = cleanText(
                values.getOrNull(textIndex).orEmpty()
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
                    .replace(assTagRegex, ""),
            )
            if (text.isNotBlank()) result += TimedSubtitleCue(startMs, endMs, text)
        }
        return result
    }

    private fun parseTtml(raw: String): List<TimedSubtitleCue> =
        ttmlParagraphRegex.findAll(raw).mapNotNull { match ->
            val attrs = attributeRegex.findAll(match.groupValues[1])
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val startMs = parseTimeMs(attrs["begin"]) ?: return@mapNotNull null
            val endMs = parseTimeMs(attrs["end"])
                ?: attrs["dur"]?.let(::parseDurationMs)?.let { startMs + it }
                ?: return@mapNotNull null
            val text = cleanText(match.groupValues[2])
            text.takeIf { it.isNotBlank() }?.let {
                TimedSubtitleCue(startMs, endMs, it)
            }
        }.toList()

    private fun parseDurationMs(raw: String): Long? {
        val value = raw.trim().lowercase()
        return when {
            value.endsWith("ms") -> value.removeSuffix("ms").toDoubleOrNull()?.toLong()
            value.endsWith("s") -> value.removeSuffix("s").toDoubleOrNull()?.times(1_000.0)?.toLong()
            else -> parseTimeMs(value)
        }
    }

    private fun parseTimeMs(raw: String?): Long? {
        val token = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = token.lowercase()
        if (lower.endsWith("ms")) {
            return lower.removeSuffix("ms").toDoubleOrNull()?.toLong()
        }
        if (lower.endsWith("s") && ':' !in lower) {
            return lower.removeSuffix("s").toDoubleOrNull()?.times(1_000.0)?.toLong()
        }

        val normalized = token.replace(',', '.')
        val parts = normalized.split(':')
        val seconds = when (parts.size) {
            3 -> {
                val hours = parts[0].toDoubleOrNull() ?: return null
                val minutes = parts[1].toDoubleOrNull() ?: return null
                val secs = parts[2].toDoubleOrNull() ?: return null
                hours * 3_600.0 + minutes * 60.0 + secs
            }
            2 -> {
                val minutes = parts[0].toDoubleOrNull() ?: return null
                val secs = parts[1].toDoubleOrNull() ?: return null
                minutes * 60.0 + secs
            }
            1 -> parts[0].toDoubleOrNull() ?: return null
            else -> return null
        }
        return (seconds * 1_000.0).toLong().coerceAtLeast(0L)
    }

    private fun cleanText(raw: String): String =
        raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(htmlTagRegex, "")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
