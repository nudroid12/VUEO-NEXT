package com.vueo.shared.core.smartsubs

import com.vueo.shared.core.media.SubtitleTrack

/** Transport guard only. Language, format, priority, deduplication and candidate
 * limits are owned by SmartSubs. Never send its own output back as input. */
object SmartSubsSourcePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun eligibleSources(tracks: List<SubtitleTrack>, includeAddonSources: Boolean = false): List<SubtitleTrack> =
        tracks.filter { it.providerId != SmartSubsPlaybackBridge.PROVIDER_ID &&
            !it.id.contains("gemini-ai-", ignoreCase = true) }.take(50)
}
