package com.vueo.app.core.player

import com.vueo.app.core.model.StreamSource
import com.vueo.shared.core.player.PlayerSourcePolicy as SharedPlayerSourcePolicy

/**
 * Mobile compatibility layer. Playback source policy and recovery now live in
 * :shared:core so Mobile and TV can use the exact same deterministic behaviour.
 */
typealias PlayerSourceQuality = com.vueo.shared.core.player.PlayerSourceQuality
typealias PlayerSourceAssessment = com.vueo.shared.core.player.PlayerSourceAssessment
typealias PlayerSourceAudioMatch = com.vueo.shared.core.player.PlayerSourceAudioMatch
typealias PlayerSourceRecoverySession = com.vueo.shared.core.player.PlayerSourceRecoverySession
typealias PlayerPlaybackPhase = com.vueo.shared.core.player.PlayerPlaybackPhase

object PlayerSourcePolicy {
    fun assess(
        source: StreamSource,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): PlayerSourceAssessment =
        SharedPlayerSourcePolicy.assess(
            source = source,
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )

    fun comparator(
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): Comparator<StreamSource> =
        SharedPlayerSourcePolicy.comparator(
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )

    fun automaticRecoveryCandidates(
        rankedSources: List<StreamSource>,
        attemptedUrls: Set<String>,
        originalLanguage: String? = null,
    ): List<StreamSource> =
        SharedPlayerSourcePolicy.automaticRecoveryCandidates(
            rankedSources = rankedSources,
            attemptedUrls = attemptedUrls,
            originalLanguage = originalLanguage,
        )

    fun detectAudioMatch(
        source: StreamSource,
        originalLanguage: String?,
    ): PlayerSourceAudioMatch =
        SharedPlayerSourcePolicy.detectAudioMatch(
            source = source,
            originalLanguage = originalLanguage,
        )

    fun detectQuality(source: StreamSource): PlayerSourceQuality =
        SharedPlayerSourcePolicy.detectQuality(source)

    fun canonicalLanguageCode(value: String?): String? =
        SharedPlayerSourcePolicy.canonicalLanguageCode(value)
}

const val PLAYER_STARTUP_TIMEOUT_MS: Long =
    com.vueo.shared.core.player.PLAYER_STARTUP_TIMEOUT_MS
const val PLAYER_RECOVERY_SOURCE_TIMEOUT_MS: Long =
    com.vueo.shared.core.player.PLAYER_RECOVERY_SOURCE_TIMEOUT_MS
const val PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS: Long =
    com.vueo.shared.core.player.PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS
const val PLAYER_REBUFFER_TIMEOUT_MS: Long =
    com.vueo.shared.core.player.PLAYER_REBUFFER_TIMEOUT_MS
