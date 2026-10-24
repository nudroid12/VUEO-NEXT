package com.vueo.shared.core.player

import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.ProviderHealthRecord
import java.security.MessageDigest
import java.util.LinkedHashMap

/** Bounded session evidence; no caption text, URL query, headers or response bodies. */
object SubtitleFlowDiagnostics {
    private data class Run(val owner: String, val lines: MutableList<String> = mutableListOf())
    private val runs = LinkedHashMap<String, Run>()
    private val sources = LinkedHashMap<String, Run>()
    private val subtitleSources = LinkedHashMap<String, Run>()
    private val receipts = LinkedHashMap<String, String>()
    private val lastHandoffs = LinkedHashMap<String, String>()
    private val sessionStartedMs = System.currentTimeMillis()
    private fun owner(repository: String, provider: String) = "$repository\n$provider"
    fun key(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).take(8)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun token(value: String?): String = value.orEmpty()
        .replace(Regex("[^a-zA-Z0-9_+./-]"), "_").take(80).ifBlank { "unknown" }
    @Synchronized
    fun beginProvider(repository: String, provider: String) {
        val identity = owner(repository, provider)
        val previous = runs.remove(identity)
        sources.filterValues { it === previous }.keys.forEach { lastHandoffs.remove(it) }
        sources.entries.removeAll { it.value === previous }
        subtitleSources.entries.removeAll { it.value === previous }
        runs[identity] = Run(identity)
        while (runs.size > 32) {
            val old = runs.remove(runs.keys.first())
            sources.entries.removeAll { it.value === old }
            subtitleSources.entries.removeAll { it.value === old }
        }
    }
    @Synchronized
    fun received(repository: String, provider: String, sourceUrl: String, count: Int, malformed: Boolean, forwarded: Int = 0) {
        val run = runs[owner(repository, provider)] ?: return
        val sourceKey = key(sourceUrl)
        sources[sourceKey] = run
        while (sources.size > 256) sources.remove(sources.keys.first())
        append(run, "PROVIDER_SUBTITLE_PAYLOAD source=$sourceKey received=$count malformed=$malformed " +
            "forwardedFromStreamPayload=$forwarded route=selected_stream")
    }
    @Synchronized
    fun addonReceived(provider: String, tracks: List<SubtitleTrack>, accepted: List<SubtitleTrack>, attempt: Int, outcome: String) {
        val receipt = "SUBTITLE_DISCOVERY provider=${token(provider)} attempt=$attempt outcome=${token(outcome)} " +
            "received=${tracks.size} acceptedLanguage=${accepted.size} filteredLanguage=${(tracks.size - accepted.size).coerceAtLeast(0)}"
        tracks.take(256).forEach { receipts[key(it.url)] = receipt }
        while (receipts.size > 256) receipts.remove(receipts.keys.first())
        RuntimeDiagnostics.recordPlayerEvent("Shared", "SUBTITLE_FLOW", receipt)
    }
    @Synchronized
    fun handoff(platform: String, sourceUrl: String, supplied: List<SubtitleTrack>, acceptedUrls: List<String>) {
        val sourceKey = key(sourceUrl)
        val signature = supplied.joinToString { key(it.url) } + ":" + acceptedUrls.joinToString { key(it) }
        if (lastHandoffs[sourceKey] == signature) return
        lastHandoffs[sourceKey] = signature
        while (lastHandoffs.size > 64) lastHandoffs.remove(lastHandoffs.keys.first())
        val run = sources[sourceKey]
        emit(platform, sourceKey, run, "SUBTITLE_HANDOFF received=${supplied.size} configured=${acceptedUrls.size}")
        supplied.mapNotNull { receipts[key(it.url)] }.distinct().take(16).forEach {
            emit(platform, sourceKey, run, it)
        }
        val configured = acceptedUrls.toSet()
        val seen = mutableSetOf<String>()
        supplied.take(32).forEach { track ->
            val trackKey = key(track.url)
            val reason = when {
                !com.vueo.shared.core.stremio.StremioUrls.isHttpUrl(track.url) -> "rejected_non_http"
                !seen.add(track.url) -> "duplicate_url"
                track.url !in configured -> "not_configured"
                else -> "configured"
            }
            if (track.url in configured && run != null) subtitleSources[trackKey] = run
            emit(platform, sourceKey, run, "SUBTITLE_TRACK track=$trackKey provider=${token(track.providerName)} " +
                "language=${token(track.language)} mime=${token(track.mimeType)} result=$reason")
        }
        while (subtitleSources.size > 256) subtitleSources.remove(subtitleSources.keys.first())
    }
    @Synchronized
    fun event(platform: String, sourceUrl: String?, event: String, details: String = "") {
        val sourceKey = key(sourceUrl.orEmpty())
        emit(platform, sourceKey, sources[sourceKey], "$event $details")
    }
    @Synchronized
    fun load(platform: String, sourceUrl: String?, subtitleUrl: String, event: String, details: String) {
        val sourceKey = key(sourceUrl.orEmpty())
        emit(platform, sourceKey, sources[sourceKey] ?: subtitleSources[key(subtitleUrl)],
            "$event track=${key(subtitleUrl)} $details")
    }
    @Synchronized
    fun evidence(health: ProviderHealthRecord): List<String> =
        if (health.lastCheckedEpochMs < sessionStartedMs) emptyList()
        else runs[owner(health.repositoryManifestUrl, health.providerId)]?.lines?.toList().orEmpty()
    private fun emit(platform: String, source: String, run: Run?, message: String) {
        val line = "platform=${token(platform)} source=$source $message"
        run?.let { append(it, line) }
        RuntimeDiagnostics.recordPlayerEvent(platform, "SUBTITLE_FLOW", line)
    }
    private fun append(run: Run, message: String) {
        run.lines += "${System.currentTimeMillis()} $message"
        while (run.lines.size > 48) run.lines.removeAt(0)
    }
}
