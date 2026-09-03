package com.vueo.shared.core.source

/**
 * Stable shared entry point for source selection. Both VUEO clients should use
 * this API instead of carrying client-specific ranking formulas.
 */
object SourcePolicy : SourceRankingPolicy by SourceRanker

class SourceRecoverySession(
    private val automaticRecoveryBudgetMs: Long = SOURCE_AUTOMATIC_RECOVERY_BUDGET_MS,
    private val elapsedRealtimeMs: () -> Long = {
        System.nanoTime() / 1_000_000L
    },
) {
    private val attemptedUrls = linkedSetOf<String>()
    private val failedUrls = linkedSetOf<String>()
    private var recoveryStartedAtMs: Long? = null

    fun begin(source: SourceCandidate) {
        source.url?.let(attemptedUrls::add)
    }

    fun markFailed(source: SourceCandidate) {
        source.url?.let {
            attemptedUrls += it
            failedUrls += it
        }
    }

    fun allowRetry(source: SourceCandidate) {
        source.url?.let {
            attemptedUrls -= it
            failedUrls -= it
        }
        recoveryStartedAtMs = null
    }

    fun markReady() {
        recoveryStartedAtMs = null
    }

    fun isAutomaticRecoveryActive(): Boolean = recoveryStartedAtMs != null

    fun failedSourceCount(): Int = failedUrls.size

    fun next(
        rankedSources: List<SourceCandidate>,
        originalLanguage: String? = null,
    ): SourceCandidate? {
        if (!recoveryBudgetAvailable(elapsedRealtimeMs())) {
            return null
        }

        val candidate = SourcePolicy
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

    private fun recoveryBudgetAvailable(nowMs: Long): Boolean {
        val startedAt = recoveryStartedAtMs
            ?: nowMs.also { recoveryStartedAtMs = it }
        return nowMs - startedAt < automaticRecoveryBudgetMs
    }
}

const val SOURCE_STARTUP_TIMEOUT_MS = 15_000L
const val SOURCE_RECOVERY_SOURCE_TIMEOUT_MS = 5_000L
const val SOURCE_AUTOMATIC_RECOVERY_BUDGET_MS = 30_000L
const val SOURCE_REBUFFER_TIMEOUT_MS = 25_000L
