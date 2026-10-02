package com.vueo.shared.core.diagnostics

data class AppCrashReport(
    val timestampMs: Long,
    val summary: String,
    val details: String,
)

internal object CrashRecoveryPolicy {
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
        .replace(Regex("(?i)(authorization|proxy-authorization)\\s*[:=]\\s*(?:Bearer\\s+|Basic\\s+)?[^\\s,;]+")) {
            "${it.groupValues[1]}=<redacted>"
        }
        .replace(Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\\s*[:=]\\s*[^\\s,;]+")) {
            "${it.groupValues[1]}=<redacted>"
        }
        .replace(Regex("https?://[^\\s<>\"']+")) {
            it.value.substringBefore('?').substringBefore('#')
                .replace(Regex("(https?://)[^/\\s@]+@"), "$1<redacted>@")
        }
}
