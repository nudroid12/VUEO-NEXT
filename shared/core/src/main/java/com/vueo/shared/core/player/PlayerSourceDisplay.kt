package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.source.SourceDisplayText
import java.util.Locale

/** Presentation only: server identity is distinct from the provider filter/group. */
object PlayerSourceDisplay {
    private fun parts(value: String): List<String> = SourceDisplayText.lines(value)
        .flatMap { it.split('•') }.map(String::trim).filter(String::isNotBlank)
        .filterNot { it.startsWith("https://", true) || it.startsWith("http://", true) }

    fun title(source: StreamSource): String =
        source.serverName?.let(::parts)?.firstOrNull()
            ?: parts(source.name).firstOrNull()
            ?: source.providerName.trim().ifBlank { "Source" }

    fun titleWithQuality(source: StreamSource): String =
        "${title(source)} • ${PlayerSourcePolicy.assess(source).quality.label}"

    fun details(source: StreamSource): String {
        val assessment = PlayerSourcePolicy.assess(source)
        val provider = source.providerName.trim().ifBlank { "Other" }
        val server = title(source)
        val transport = parts(assessment.summary).firstOrNull { it == "HLS" || it == "MP4" }
        val excluded = setOf(provider.lowercase(Locale.ROOT), server.lowercase(Locale.ROOT),
            assessment.quality.label.lowercase(Locale.ROOT))
        return buildList {
            add(provider)
            add(transport ?: if (source.infoHash != null) "Torrent" else "Direct")
            listOfNotNull(source.codec, source.hdr, source.audio, source.language)
                .filter(String::isNotBlank).forEach(::add)
            source.sizeBytes?.takeIf { it > 0L }?.let {
                val gib = it.toDouble() / (1024.0 * 1024.0 * 1024.0)
                add(if (gib >= 1.0) String.format(Locale.US, "%.1f GB", gib)
                    else String.format(Locale.US, "%.0f MB", it.toDouble() / (1024.0 * 1024.0)))
            }
            // Retain addon language/subtitle tags even when there is no structured field.
            (parts(source.name) + source.serverName?.let(::parts).orEmpty())
                .filterNot { it.lowercase(Locale.ROOT) in excluded }.forEach(::add)
        }.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(" • ")
    }
}
