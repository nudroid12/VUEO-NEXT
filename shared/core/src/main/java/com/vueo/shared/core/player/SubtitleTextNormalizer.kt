package com.vueo.shared.core.player

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Repairs decimal SubRip cue times while retaining the subtitle's existing format. */
internal object SubtitleTextNormalizer {
    private val firstCue = Regex("^[ \\t]*\\d+[ \\t]*\\r?\\n[ \\t]*(?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3}[ \\t]*-->")
    private val cueTimes = Regex(
        "^(\\uFEFF?[ \\t]*\\d+[ \\t]*\\r?\\n)[ \\t]*((?:\\d+:)?\\d{1,2}:\\d{2})[.,](\\d{1,3})[ \\t]*-->[ \\t]*((?:\\d+:)?\\d{1,2}:\\d{2})[.,](\\d{1,3})([ \\t]*\\r?)$",
        RegexOption.MULTILINE,
    )

    fun normalize(bytes: ByteArray): ByteArray {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return bytes
        }
        if (!firstCue.containsMatchIn(text.trimStart('\uFEFF', ' ', '\t', '\r', '\n'))) return bytes
        val normalized = cueTimes.replace(text) { cue ->
            cue.groupValues[1] + cue.groupValues[2] + "," + cue.groupValues[3].padEnd(3, '0') +
                " --> " + cue.groupValues[4] + "," + cue.groupValues[5].padEnd(3, '0') + cue.groupValues[6]
        }
        return if (normalized == text) bytes else normalized.toByteArray(Charsets.UTF_8)
    }
}
