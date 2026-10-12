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
                    "Source discovery returned ${health.streamCount} source link${if (health.streamCount == 1) "" else "s"}. " +
                        "Links alone do not confirm the requested title, episode or playback." +
                        if (health.status == ProviderHealthStatus.SLOW) " Response was slow." else ""
                } else "Source discovery returned no links. Check title/episode mapping and extraction evidence."
            ProviderHealthStatus.NO_RESULTS ->
                "Provider completed without source links. Check title/episode mapping and extraction evidence."
            else -> "No completed source-discovery result is available in this record."
        }
    }

    /**
     * Optional provider report: [VUEO_MATCH] followed by a JSON object with
     * titleMatch/episodeMatch = matched, mismatch or unverified, and optional
     * selectedTitle/selectedSeason/selectedEpisode. Claims stay labelled as provider reports.
     * Free-form search, TMDB metadata and result counts never imply a verified match.
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
        fun matchLabel(field: String): String = when (report?.optString(field)) {
            "matched" -> "Match reported by provider"
            "mismatch" -> "Mismatch reported by provider"
            else -> "Not verified — provider supplied no confirmation"
        }
        add("Title match" to matchLabel("titleMatch"))
        if (health.requestMediaType == "tv" || health.requestSeason != null || health.requestEpisode != null) {
            add("Episode match" to matchLabel("episodeMatch"))
        }
        report?.optString("selectedTitle")?.takeIf { it.isNotBlank() }?.let {
            add("Selected title" to it.take(500))
        }
        if (report?.has("selectedSeason") == true && report.has("selectedEpisode")) {
            val season = report.optInt("selectedSeason", -1)
            val episode = report.optInt("selectedEpisode", -1)
            if (season >= 0 && episode > 0) add("Selected episode" to "S$season E$episode (provider report)")
        }
        val selected = health.logs.filter {
            Regex("(?i)(using first result|selected title|matched title|selected episode|matched episode)\\s*[:=]").containsMatchIn(it)
        }.distinct().takeLast(3)
        if (selected.isNotEmpty()) add("Selection evidence" to selected.joinToString("\n") { it.take(500) })
        val fallbacks = health.logs.filter {
            Regex("(?i)\\b(fallback|falling back)\\b").containsMatchIn(it)
        }.distinct().takeLast(3)
        if (fallbacks.isNotEmpty()) {
            add("Fallback evidence" to fallbacks.joinToString("\n") { it.take(500) })
            if (fallbacks.none { Regex("(?i)(because|due to|reason\\s*[:=])").containsMatchIn(it) }) {
                add("Fallback reason" to "Not supplied by provider")
            }
        }
        add("Playback" to "Not verified by source discovery")
        val flow = SubtitleFlowDiagnostics.evidence(health)
        if (flow.isNotEmpty()) {
            val latestError = flow.indexOfLast { "SUBTITLE_LOAD_ERROR" in it }
            val latestCues = flow.indexOfLast { "SUBTITLE_CUES_DELIVERED" in it }
            val latestLoad = flow.indexOfLast { "SUBTITLE_LOAD_COMPLETED" in it }
            add("Subtitle flow" to when {
                latestError > latestCues && latestError > latestLoad -> "Text load error captured — see raw log"
                latestCues >= 0 -> "Player delivered subtitle cues — see raw log"
                latestLoad >= 0 -> "Text load completed; cue delivery not yet observed"
                flow.any { "SUBTITLE_HANDOFF" in it } -> "Handoff captured; text load/cues not yet observed"
                else -> "Provider payload captured; player evidence not yet observed"
            })
        }
    }
}
