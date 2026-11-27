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

    /** Matches the TV player source card: repository/group on the first line. */
    fun groupTitle(source: StreamSource): String =
        source.providerName.substringBeforeLast(" / ").trim().ifBlank { "Source" }

    fun groupedDetails(source: StreamSource): String {
        val provider = source.providerName.substringAfterLast(" / ").trim()
        val prefix = provider.takeUnless { it.equals(groupTitle(source), true) }.orEmpty()
        return (listOf(prefix) + ProviderSourceDetails.lines(source, PlayerSourcePolicy.assess(source)))
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    fun compactDetails(source: StreamSource): String =
        ProviderSourceDetails.compactLine(source, PlayerSourcePolicy.assess(source))
}
