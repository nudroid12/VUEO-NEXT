package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.source.ProviderSourceDetails
import com.vueo.shared.core.source.SourceDisplayText

/** Presentation only: server identity is distinct from the provider filter/group. */
object PlayerSourceDisplay {
    private fun parts(value: String): List<String> = SourceDisplayText.lines(value)
        .flatMap { it.split('•') }.map(String::trim).filter(String::isNotBlank)
        .filterNot { it.startsWith("https://", true) || it.startsWith("http://", true) }

    fun title(source: StreamSource): String =
        source.serverName?.let(::parts)?.firstOrNull()
            ?: parts(source.name).firstOrNull()
            ?: source.providerName.trim().ifBlank { "Source" }

    fun providerTitle(source: StreamSource): String =
        source.providerName.substringAfterLast(" / ").trim().ifBlank { "Source" }

    fun compactDetails(source: StreamSource): String =
        ProviderSourceDetails.compactLine(source, PlayerSourcePolicy.assess(source))
}
