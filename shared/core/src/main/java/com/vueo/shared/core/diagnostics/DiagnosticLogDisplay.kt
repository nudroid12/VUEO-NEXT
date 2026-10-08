package com.vueo.shared.core.diagnostics

/** Presentation only. Recorders and full copy/export retain their original text. */
object DiagnosticLogDisplay {
    private val eventStart = Regex("^\\d{4}-\\d{2}-\\d{2} .* \\| .*")

    fun preview(text: String, raw: Boolean, query: String, maxChars: Int = 24_000): String {
        if (text.isBlank()) return "No diagnostic data recorded yet."
        val blocks = if (raw) rawBlocks(text) else text.split("\n\n").filter { it.isNotBlank() }
        val needle = query.trim()
        val matching = if (needle.isEmpty()) blocks else blocks.filter { it.contains(needle, ignoreCase = true) }
        if (matching.isEmpty()) return "No diagnostic blocks match \"$needle\"."
        // Bound the preview at block boundaries: stack frames stay with their event.
        val selected = ArrayDeque<String>()
        var chars = 0
        for (block in matching.asReversed()) {
            if (selected.isNotEmpty() && chars + block.length + 2 > maxChars) break
            if (block.length > maxChars) {
                if (selected.isEmpty()) return "[This event exceeds the preview limit. Copy or Save includes the full log.]"
                break
            }
            selected.addFirst(block)
            chars += block.length + 2
        }
        val body = selected.joinToString("\n\n") { if (raw) formatRawEvent(it) else it }
        return if (selected.size < matching.size) {
            "[Recent preview. Copy and Save use the full selected log.]\n\n$body"
        } else body
    }

    private val eventHeader = Regex("^(\\d{4}-\\d{2}-\\d{2} [^|]+) \\| (?:\\[([^]]+)\\] )?(.*)$")
    private val contextField = Regex("(?:^| )((?:scan|id|providerId|provider|plugin|run|exec|thread)=.*?)(?= [A-Za-z][A-Za-z0-9]*=| (?:LOG:|STAGE |PARSE:|ERROR:|RESULT:)|$)")

    private fun formatRawEvent(block: String): String {
        val first = block.substringBefore('\n')
        val header = eventHeader.matchEntire(first) ?: return block
        val payload = header.groupValues[3]
        val event = payload.substringBefore(' ')
        val data = payload.substringAfter(' ', "")
        val context = contextField.findAll(data).toList()
        val detail = StringBuilder(data)
        // Remove only displayed context fields, leaving every remaining payload character.
        context.asReversed().forEach { detail.delete(it.range.first, it.range.last + 1) }
        return buildString {
            append("Time: ").append(header.groupValues[1].trim())
            header.groupValues[2].takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
            append('\n').append("Event: ").append(event)
            if (context.isNotEmpty()) append('\n').append("Context: ")
                .append(context.joinToString(" • ") { it.groupValues[1] })
            if (detail.isNotBlank()) append('\n').append("Data: ").append(detail.toString().trim())
            val continuation = block.substringAfter('\n', "").trimEnd()
            if (continuation.isNotBlank()) append('\n').append(continuation)
        }
    }

    private fun rawBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        text.lineSequence().forEach { line ->
            if (eventStart.matches(line) && current.isNotBlank()) {
                blocks.add(current.toString().trimEnd())
                current.setLength(0)
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) blocks.add(current.toString().trimEnd())
        return blocks
    }
}
