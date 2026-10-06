package com.vueo.shared.core.diagnostics

data class AppCrashReport(
    val timestampMs: Long,
    val summary: String,
    val details: String,
)

internal object CrashRecoveryPolicy {
    private val authorizationPattern =
        Regex("(?i)(authorization|proxy-authorization)\\s*[:=]\\s*(?:Bearer\\s+|Basic\\s+)?[^\\s,;]+")
    private val secretPattern =
        Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\\s*[:=]\\s*[^\\s,;]+")
    private val urlPattern = Regex("https?://[^\\s<>\"']+")
    private val credentialInUrlPattern = Regex("(https?://)[^/\\s@]+@")

    fun pendingLocal(report: AppCrashReport?, dismissedThrough: Long, currentStartedMs: Long): AppCrashReport? =
        report?.takeIf { it.timestampMs > dismissedThrough && it.timestampMs < currentStartedMs }

    fun choose(local: AppCrashReport?, system: AppCrashReport?): AppCrashReport? = when {
        local == null -> system
        system == null -> local
        kotlin.math.abs(system.timestampMs - local.timestampMs) <= 10_000L -> local
        system.timestampMs > local.timestampMs -> system
        else -> local
    }

    fun sanitize(raw: String): String = raw
        .replace(authorizationPattern) {
            "${it.groupValues[1]}=<redacted>"
        }
        .replace(secretPattern) {
            "${it.groupValues[1]}=<redacted>"
        }
        .replace(urlPattern) {
            it.value.substringBefore('?').substringBefore('#')
                .replace(credentialInUrlPattern, "$1<redacted>@")
        }
}
