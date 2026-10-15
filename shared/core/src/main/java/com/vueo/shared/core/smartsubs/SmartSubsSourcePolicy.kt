package com.vueo.shared.core.smartsubs

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.SubtitleFormat
import com.vueo.shared.core.player.SubtitleFormatPolicy
import java.net.URI

/** Lightweight eligibility only. Does not download or translate any subtitle. */
object SmartSubsSourcePolicy {
    fun eligibleSources(tracks: List<SubtitleTrack>, includeAddonSources: Boolean = false): List<SubtitleTrack> = tracks
        .filter { track ->
            track.providerId != "vueo-smartsubs-ai" &&
                !track.id.contains("gemini-ai-", ignoreCase = true) &&
                (includeAddonSources || track.providerId.startsWith("plugin:")) &&
                LanguagePolicy.canonicalCode(track.language) == "en" &&
                SubtitleFormatPolicy.detect(track.url, track.mimeType) in setOf(SubtitleFormat.SUBRIP, SubtitleFormat.WEBVTT) &&
                isPublicSource(track.url)
        }
        .distinctBy { it.url }
        .take(5)

    private fun isPublicSource(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", true) && uri.userInfo == null && uri.fragment == null &&
            uri.port in setOf(-1, 443) && '.' in host && ':' !in host &&
            !Regex("\\d+(\\.\\d+){3}").matches(host) &&
            !setOf("localhost", "local", "internal", "lan", "invalid", "test").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)

}
