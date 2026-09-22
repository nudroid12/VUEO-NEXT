package com.vueo.shared.core.player

import com.vueo.shared.core.media.SubtitleTrack

/**
 * Keeps late subtitle discovery from being treated like a new video session.
 */
object PlayerSubtitleUpdatePolicy {
    fun sourceKeys(subtitles: List<SubtitleTrack>): List<String> =
        subtitles
            .asSequence()
            .map { subtitle ->
                listOf(
                    subtitle.url.trim(),
                    subtitle.mimeType.orEmpty().trim().lowercase(),
                    subtitle.language.trim().lowercase(),
                ).joinToString("\u0000")
            }
            .distinct()
            .sorted()
            .toList()

    fun stableResumePositionMs(
        currentPositionMs: Long,
        lastKnownPositionMs: Long,
    ): Long {
        val current = currentPositionMs.coerceAtLeast(0L)
        if (current > MIN_STABLE_POSITION_MS) return current

        return lastKnownPositionMs
            .coerceAtLeast(0L)
            .takeIf { it > MIN_STABLE_POSITION_MS }
            ?: current
    }

    /**
     * External tracks already discovered by VUEO but not exposed by Media3 yet.
     * The UI uses these as selectable rows while a replacement media item is
     * still publishing its text-track groups.
     */
    fun pendingExternalTracks(
        discovered: List<SubtitleTrack>,
        materializedSelectionIds: Set<String>,
    ): List<SubtitleTrack> =
        discovered
            .asSequence()
            .filter { it.url.startsWith("https://") }
            .distinctBy(PlayerTrackPolicy::externalSubtitleSelectionId)
            .filter {
                PlayerTrackPolicy.externalSubtitleSelectionId(it) !in
                    materializedSelectionIds
            }
            .toList()

    private const val MIN_STABLE_POSITION_MS = 1_000L
}
