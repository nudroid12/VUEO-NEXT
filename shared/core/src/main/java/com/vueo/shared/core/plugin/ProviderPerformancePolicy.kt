package com.vueo.shared.core.plugin

data class ProviderPerformanceSnapshot(
    val score: Int,
    val historyRuns: Int,
    val successfulRuns: Int,
    val noResultRuns: Int,
    val hardFailureRuns: Int,
    val consecutiveHardFailures: Int,
    val hitRatePercent: Int?,
    val reliabilityPercent: Int?,
    val averageResponseMs: Long?,
    val lastSuccessEpochMs: Long?,
)

data class ProviderHealthSortKey(
    val availabilityTier: Int,
    val performanceScore: Int,
    val statusTier: Int,
    val responseMs: Long,
)

fun providerHealthSortKey(
    record: ProviderHealthRecord?,
): ProviderHealthSortKey {
    val performance =
        providerPerformance(record)
    val status =
        record?.status

    return ProviderHealthSortKey(
        availabilityTier =
            when (status) {
                ProviderHealthStatus.NEEDS_SETUP,
                ProviderHealthStatus.UNAVAILABLE -> 1

                else -> 0
            },
        performanceScore =
            performance.score,
        statusTier =
            providerHealthStatusPriority(
                status
            ),
        responseMs =
            performance.averageResponseMs
                ?: record?.responseMs
                ?: Long.MAX_VALUE,
    )
}

fun providerHealthStatusPriority(
    status: ProviderHealthStatus?,
): Int =
    when (status) {
        ProviderHealthStatus.ONLINE -> 0
        ProviderHealthStatus.SLOW -> 1
        ProviderHealthStatus.UNKNOWN,
        null -> 2

        ProviderHealthStatus.NO_RESULTS -> 3
        ProviderHealthStatus.TIMEOUT,
        ProviderHealthStatus.BLOCKED,
        ProviderHealthStatus.FAILED -> 4

        ProviderHealthStatus.NEEDS_SETUP,
        ProviderHealthStatus.UNAVAILABLE -> 5
    }

fun providerPerformance(
    record: ProviderHealthRecord?,
): ProviderPerformanceSnapshot {
    if (record == null) {
        return ProviderPerformanceSnapshot(
            score = NEUTRAL_PERFORMANCE_SCORE,
            historyRuns = 0,
            successfulRuns = 0,
            noResultRuns = 0,
            hardFailureRuns = 0,
            consecutiveHardFailures = 0,
            hitRatePercent = null,
            reliabilityPercent = null,
            averageResponseMs = null,
            lastSuccessEpochMs = null,
        )
    }

    val history =
        record.effectiveHistory()
    val hitRate =
        if (history.runs > 0) {
            history.successes * 100 /
                history.runs
        } else {
            null
        }
    val reliabilityDenominator =
        history.successes +
            history.hardFailures
    val reliability =
        if (reliabilityDenominator > 0) {
            history.successes * 100 /
                reliabilityDenominator
        } else {
            null
        }

    val empiricalScore =
        (
            (reliability ?: 50) * 35 +
                (hitRate ?: 50) * 40 +
                responseLatencyScore(
                    history.averageResponseMs
                ) * 25
            ) / 100
    val confidenceSteps =
        history.runs.coerceAtMost(10)
    var score =
        (
            NEUTRAL_PERFORMANCE_SCORE *
                (10 - confidenceSteps) +
                empiricalScore *
                confidenceSteps
            ) / 10

    score +=
        when (record.status) {
            ProviderHealthStatus.ONLINE -> 12
            ProviderHealthStatus.SLOW -> 6
            ProviderHealthStatus.FAILED,
            ProviderHealthStatus.TIMEOUT,
            ProviderHealthStatus.BLOCKED -> -8

            ProviderHealthStatus.NEEDS_SETUP,
            ProviderHealthStatus.UNAVAILABLE -> -12

            ProviderHealthStatus.NO_RESULTS,
            ProviderHealthStatus.UNKNOWN -> 0
        }
    score -=
        (
            history.consecutiveHardFailures * 7
            ).coerceAtMost(21)

    return ProviderPerformanceSnapshot(
        score = score.coerceIn(0, 100),
        historyRuns = history.runs,
        successfulRuns = history.successes,
        noResultRuns = history.noResults,
        hardFailureRuns = history.hardFailures,
        consecutiveHardFailures =
            history.consecutiveHardFailures,
        hitRatePercent = hitRate,
        reliabilityPercent = reliability,
        averageResponseMs =
            history.averageResponseMs,
        lastSuccessEpochMs =
            history.lastSuccessEpochMs,
    )
}

private data class ProviderHistory(
    val runs: Int,
    val successes: Int,
    val noResults: Int,
    val hardFailures: Int,
    val consecutiveHardFailures: Int,
    val averageResponseMs: Long?,
    val lastSuccessEpochMs: Long?,
)

internal fun mergeProviderHistory(
    previous: ProviderHealthRecord?,
    latest: ProviderHealthRecord,
): ProviderHealthRecord {
    var history =
        previous?.effectiveHistory()
            ?: ProviderHistory(
                runs = 0,
                successes = 0,
                noResults = 0,
                hardFailures = 0,
                consecutiveHardFailures = 0,
                averageResponseMs = null,
                lastSuccessEpochMs = null,
            )

    val sampleKind =
        latest.status.historySampleKind()

    if (sampleKind != HistorySampleKind.NONE) {
        if (history.runs >= HISTORY_SAMPLE_CAP) {
            val successes =
                history.successes / 2
            val noResults =
                history.noResults / 2
            val hardFailures =
                history.hardFailures / 2

            history =
                history.copy(
                    runs =
                        successes +
                            noResults +
                            hardFailures,
                    successes = successes,
                    noResults = noResults,
                    hardFailures = hardFailures,
                )
        }

        val successes =
            history.successes +
                if (
                    sampleKind ==
                    HistorySampleKind.SUCCESS
                ) 1 else 0
        val noResults =
            history.noResults +
                if (
                    sampleKind ==
                    HistorySampleKind.NO_RESULTS
                ) 1 else 0
        val hardFailures =
            history.hardFailures +
                if (
                    sampleKind ==
                    HistorySampleKind.HARD_FAILURE
                ) 1 else 0
        val averageResponseMs =
            latest.responseMs?.let {
                responseMs ->

                history.averageResponseMs
                    ?.let { previousAverage ->
                        (
                            previousAverage * 3L +
                                responseMs
                            ) / 4L
                    }
                    ?: responseMs
            } ?: history.averageResponseMs

        history =
            ProviderHistory(
                runs =
                    successes +
                        noResults +
                        hardFailures,
                successes = successes,
                noResults = noResults,
                hardFailures = hardFailures,
                consecutiveHardFailures =
                    if (
                        sampleKind ==
                        HistorySampleKind.HARD_FAILURE
                    ) {
                        history
                            .consecutiveHardFailures +
                            1
                    } else {
                        0
                    },
                averageResponseMs =
                    averageResponseMs,
                lastSuccessEpochMs =
                    if (
                        sampleKind ==
                        HistorySampleKind.SUCCESS
                    ) {
                        latest.lastCheckedEpochMs
                    } else {
                        history.lastSuccessEpochMs
                    },
            )
    }

    return latest.copy(
        historyRuns = history.runs,
        historySuccesses = history.successes,
        historyNoResults = history.noResults,
        historyHardFailures =
            history.hardFailures,
        consecutiveHardFailures =
            history.consecutiveHardFailures,
        averageResponseMs =
            history.averageResponseMs,
        lastSuccessEpochMs =
            history.lastSuccessEpochMs,
    )
}

private fun ProviderHealthRecord
    .effectiveHistory(): ProviderHistory {
    if (historyRuns > 0) {
        return ProviderHistory(
            runs = historyRuns,
            successes =
                historySuccesses
                    .coerceAtLeast(0),
            noResults =
                historyNoResults
                    .coerceAtLeast(0),
            hardFailures =
                historyHardFailures
                    .coerceAtLeast(0),
            consecutiveHardFailures =
                consecutiveHardFailures
                    .coerceAtLeast(0),
            averageResponseMs =
                averageResponseMs
                    ?: responseMs,
            lastSuccessEpochMs =
                lastSuccessEpochMs,
        )
    }

    return when (
        status.historySampleKind()
    ) {
        HistorySampleKind.SUCCESS ->
            ProviderHistory(
                runs = 1,
                successes = 1,
                noResults = 0,
                hardFailures = 0,
                consecutiveHardFailures = 0,
                averageResponseMs = responseMs,
                lastSuccessEpochMs =
                    lastCheckedEpochMs
                        .takeIf { it > 0L },
            )

        HistorySampleKind.NO_RESULTS ->
            ProviderHistory(
                runs = 1,
                successes = 0,
                noResults = 1,
                hardFailures = 0,
                consecutiveHardFailures = 0,
                averageResponseMs = responseMs,
                lastSuccessEpochMs = null,
            )

        HistorySampleKind.HARD_FAILURE ->
            ProviderHistory(
                runs = 1,
                successes = 0,
                noResults = 0,
                hardFailures = 1,
                consecutiveHardFailures = 1,
                averageResponseMs = responseMs,
                lastSuccessEpochMs = null,
            )

        HistorySampleKind.NONE ->
            ProviderHistory(
                runs = 0,
                successes = 0,
                noResults = 0,
                hardFailures = 0,
                consecutiveHardFailures = 0,
                averageResponseMs = null,
                lastSuccessEpochMs = null,
            )
    }
}

private enum class HistorySampleKind {
    SUCCESS,
    NO_RESULTS,
    HARD_FAILURE,
    NONE,
}

private fun ProviderHealthStatus
    .historySampleKind():
    HistorySampleKind =
    when (this) {
        ProviderHealthStatus.ONLINE,
        ProviderHealthStatus.SLOW ->
            HistorySampleKind.SUCCESS

        ProviderHealthStatus.NO_RESULTS ->
            HistorySampleKind.NO_RESULTS

        ProviderHealthStatus.FAILED,
        ProviderHealthStatus.TIMEOUT,
        ProviderHealthStatus.BLOCKED ->
            HistorySampleKind.HARD_FAILURE

        ProviderHealthStatus.NEEDS_SETUP,
        ProviderHealthStatus.UNAVAILABLE,
        ProviderHealthStatus.UNKNOWN ->
            HistorySampleKind.NONE
    }

private fun responseLatencyScore(
    averageResponseMs: Long?,
): Int {
    val responseMs =
        averageResponseMs
            ?: return 50

    if (responseMs <= 1_000L) {
        return 100
    }

    if (responseMs >= 8_000L) {
        return 0
    }

    return (
        (8_000L - responseMs) *
            100L /
            7_000L
        ).toInt()
        .coerceIn(0, 100)
}

private const val HISTORY_SAMPLE_CAP = 20
private const val NEUTRAL_PERFORMANCE_SCORE = 50
