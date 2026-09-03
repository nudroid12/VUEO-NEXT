package com.vueo.app.core.player

import com.vueo.app.core.model.StreamSource
import com.vueo.shared.core.source.SourceAudioMatch
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourcePolicy
import com.vueo.shared.core.source.SourceQuality
import com.vueo.shared.core.source.SourceRanker

enum class PlayerSourceQuality(
    val label: String,
    val automaticRecoveryEligible: Boolean,
) {
    FULL_HD("1080p", true),
    HD("720p", true),
    AUTO("Auto", true),
    UNKNOWN("Unknown", true),
    ULTRA_HD("4K", true),
    LOW("Below 720p", false),
}

data class PlayerSourceAssessment(
    val quality: PlayerSourceQuality,
    val score: Int,
    val summary: String,
    val audioMatch: PlayerSourceAudioMatch,
)

enum class PlayerSourceAudioMatch(
    val recommendationEligible: Boolean,
) {
    ORIGINAL(true),
    MULTI_WITH_ORIGINAL(true),
    UNKNOWN(true),
    FOREIGN_DUB(false),
}

/**
 * Deterministic source policy. It uses only metadata available for the current
 * title and never learns from viewing history.
 */
object PlayerSourcePolicy {
    fun assess(
        source: StreamSource,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): PlayerSourceAssessment {
        val shared = SourcePolicy.assess(
            source = source.toSharedCandidate(),
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        )
        return PlayerSourceAssessment(
            quality = shared.quality.toMobileQuality(),
            score = shared.score,
            summary = shared.summary,
            audioMatch = shared.audioMatch.toMobileAudioMatch(),
        )
    }

    fun comparator(
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): Comparator<StreamSource> =
        compareByDescending<StreamSource> {
            assess(
                source = it,
                preferredQuality = preferredQuality,
                originalLanguage = originalLanguage,
            ).score
        }.thenBy {
            it.sizeBytes ?: Long.MAX_VALUE
        }.thenBy {
            it.providerName.lowercase()
        }

    fun automaticRecoveryCandidates(
        rankedSources: List<StreamSource>,
        attemptedUrls: Set<String>,
        originalLanguage: String? = null,
    ): List<StreamSource> = rankedSources.filter { source ->
        val url = source.url
        val assessment = assess(
            source = source,
            originalLanguage = originalLanguage,
        )
        url != null &&
            url !in attemptedUrls &&
            assessment.quality.automaticRecoveryEligible &&
            assessment.audioMatch.recommendationEligible
    }

    fun detectAudioMatch(
        source: StreamSource,
        originalLanguage: String?,
    ): PlayerSourceAudioMatch = SourceRanker
        .detectAudioMatch(
            source = source.toSharedCandidate(),
            originalLanguage = originalLanguage,
        )
        .toMobileAudioMatch()

    fun detectQuality(
        source: StreamSource,
    ): PlayerSourceQuality = SourceRanker
        .detectQuality(source.toSharedCandidate())
        .toMobileQuality()

    fun canonicalLanguageCode(value: String?): String? =
        SourceRanker.canonicalLanguageCode(value)

    private fun StreamSource.toSharedCandidate(): SourceCandidate =
        SourceCandidate(
            id = buildString {
                append(providerId)
                append(':')
                append(url ?: infoHash ?: name)
                fileIndex?.let {
                    append(':')
                    append(it)
                }
            },
            name = name,
            url = url,
            infoHash = infoHash,
            fileIndex = fileIndex,
            quality = quality,
            codec = codec,
            hdr = hdr,
            audio = audio,
            language = language,
            sizeBytes = sizeBytes,
            headers = headers,
            rankBoost = rankBoost,
            providerId = providerId,
            providerName = providerName,
        )

    private fun SourceQuality.toMobileQuality(): PlayerSourceQuality =
        PlayerSourceQuality.valueOf(name)

    private fun SourceAudioMatch.toMobileAudioMatch(): PlayerSourceAudioMatch =
        PlayerSourceAudioMatch.valueOf(name)
}

class PlayerSourceRecoverySession(
    private val automaticRecoveryBudgetMs: Long =
        PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS,
    private val elapsedRealtimeMs: () -> Long = {
        System.nanoTime() / 1_000_000L
    },
) {
    private val attemptedUrls = linkedSetOf<String>()
    private val failedUrls = linkedSetOf<String>()
    private var recoveryStartedAtMs: Long? = null

    fun begin(source: StreamSource) {
        source.url?.let(attemptedUrls::add)
    }

    fun markFailed(source: StreamSource) {
        source.url?.let {
            attemptedUrls += it
            failedUrls += it
        }
    }

    fun allowRetry(source: StreamSource) {
        source.url?.let {
            attemptedUrls -= it
            failedUrls -= it
        }
        recoveryStartedAtMs = null
    }

    fun markReady() {
        recoveryStartedAtMs = null
    }

    fun isAutomaticRecoveryActive(): Boolean =
        recoveryStartedAtMs != null

    fun failedSourceCount(): Int = failedUrls.size

    private fun recoveryBudgetAvailable(nowMs: Long): Boolean {
        val startedAt = recoveryStartedAtMs
            ?: nowMs.also { recoveryStartedAtMs = it }
        return nowMs - startedAt < automaticRecoveryBudgetMs
    }

    fun next(
        rankedSources: List<StreamSource>,
        originalLanguage: String? = null,
    ): StreamSource? {
        if (!recoveryBudgetAvailable(elapsedRealtimeMs())) {
            return null
        }
        val candidate = PlayerSourcePolicy
            .automaticRecoveryCandidates(
                rankedSources = rankedSources,
                attemptedUrls = attemptedUrls,
                originalLanguage = originalLanguage,
            )
            .firstOrNull()
            ?: return null

        candidate.url?.let(attemptedUrls::add)
        return candidate
    }

    fun failedSourceUrls(): Set<String> = failedUrls.toSet()
}

enum class PlayerPlaybackPhase {
    LOADING,
    BUFFERING,
    RECOVERING,
    READY,
    FAILED,
}

const val PLAYER_STARTUP_TIMEOUT_MS = 15_000L
const val PLAYER_RECOVERY_SOURCE_TIMEOUT_MS = 5_000L
const val PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS = 30_000L
const val PLAYER_REBUFFER_TIMEOUT_MS = 25_000L
