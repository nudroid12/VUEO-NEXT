package com.vueo.shared.core.plugin

import org.json.JSONObject
import com.vueo.shared.core.player.SubtitleFlowDiagnostics

/** Presentation only: never changes provider health, scan order or source selection. */
object ProviderDiagnosticSummary {
    fun hasFailure(health: ProviderHealthRecord, codeReady: Boolean): Boolean =
        !codeReady || health.status in setOf(
            ProviderHealthStatus.NEEDS_SETUP, ProviderHealthStatus.UNAVAILABLE,
            ProviderHealthStatus.BLOCKED, ProviderHealthStatus.TIMEOUT, ProviderHealthStatus.FAILED,
        )

    fun statusLabel(health: ProviderHealthRecord): String = when (health.status) {
        ProviderHealthStatus.ONLINE, ProviderHealthStatus.SLOW ->
            if (health.streamCount > 0) "Links found" else "No results"
        ProviderHealthStatus.NO_RESULTS -> "No results"
        ProviderHealthStatus.FAILED -> "Provider failed"
        else -> health.status.label
    }

    fun noteTitle(health: ProviderHealthRecord, codeReady: Boolean): String =
        if (hasFailure(health, codeReady)) "Likely cause" else "Summary"

    fun executionSummary(health: ProviderHealthRecord, codeReady: Boolean): String? {
        if (hasFailure(health, codeReady)) return null
        return when (health.status) {
            ProviderHealthStatus.ONLINE, ProviderHealthStatus.SLOW ->
                if (health.streamCount > 0) {
                    "${health.streamCount} source link${if (health.streamCount == 1) "" else "s"} found." +
                        if (health.status == ProviderHealthStatus.SLOW) " Response was slow." else ""
                } else "No source links found."
            ProviderHealthStatus.NO_RESULTS ->
                "No source links found."
            else -> "No completed source-discovery result is available in this record."
        }
    }

    /** Keep the overview factual; technical evidence remains in the raw log.
     * Missing optional reports are not failures and should not generate warnings.
     */
    fun facts(health: ProviderHealthRecord): List<Pair<String, String>> = buildList {
        val count = health.streamCount.coerceAtLeast(0)
        add("Result" to if (health.status == ProviderHealthStatus.UNKNOWN && count == 0) {
            "Not captured"
        } else "$count source link${if (count == 1) "" else "s"} found")
        val report = health.logs.asReversed().firstNotNullOfOrNull { line ->
            val marker = line.indexOf("[VUEO_MATCH]")
            if (marker < 0) null else runCatching { JSONObject(line.substring(marker + 12).trim()) }.getOrNull()
        }
        val mismatches = listOfNotNull(
            "Title mismatch reported by provider".takeIf { report?.optString("titleMatch") == "mismatch" },
            "Episode mismatch reported by provider".takeIf { report?.optString("episodeMatch") == "mismatch" },
        )
        if (mismatches.isNotEmpty()) add("Provider warning" to mismatches.joinToString("; "))
        val flow = SubtitleFlowDiagnostics.evidence(health)
        if (flow.isNotEmpty()) {
            val latestError = flow.indexOfLast { "SUBTITLE_LOAD_ERROR" in it }
            val latestCues = flow.indexOfLast { "SUBTITLE_CUES_DELIVERED" in it }
            val latestLoad = flow.indexOfLast { "SUBTITLE_LOAD_COMPLETED" in it }
            val observedStatus = when {
                latestError > latestCues && latestError > latestLoad -> "Text load error captured — see raw log"
                latestCues >= 0 -> "Player received subtitle cues"
                latestLoad >= 0 -> "Subtitle file loaded"
                else -> null
            }
            observedStatus?.let { add("Subtitle flow" to it) }
        }
    }
}
