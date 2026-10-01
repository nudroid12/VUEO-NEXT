package com.vueo.shared.core.source

/** Preserve addon text while removing standalone provider/server duplicates. */
object SourceDisplayText {
    fun lines(value: String): List<String> = value
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace('\r', '\n')
        .lineSequence().map(String::trim).filter(String::isNotBlank).toList()

    fun details(value: String, provider: String, server: String?): String? {
        val serverLines = server?.let(::lines).orEmpty()
        return lines(value).filterNot { line ->
            line.equals(provider, true) || serverLines.any { line.equals(it, true) } ||
                line.startsWith("https://", true) || line.startsWith("http://", true)
        }.distinctBy { it.lowercase() }.joinToString("\n").takeIf(String::isNotBlank)
    }
}
