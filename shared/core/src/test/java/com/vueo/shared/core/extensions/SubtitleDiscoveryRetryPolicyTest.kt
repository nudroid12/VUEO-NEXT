package com.vueo.shared.core.extensions

import org.junit.Assert.*
import org.junit.Test

class SubtitleDiscoveryRetryPolicyTest {
    @Test fun validEmptyResponseGetsOnlyOneLateResultRetry() {
        assertTrue(SubtitleDiscoveryRetryPolicy.shouldRetry(1, true, false))
        assertFalse(SubtitleDiscoveryRetryPolicy.shouldRetry(2, true, false))
        assertFalse(SubtitleDiscoveryRetryPolicy.shouldRetry(6, true, false))
    }
    @Test fun transientFailuresRemainEligibleForTheExistingBudget() {
        assertTrue(SubtitleDiscoveryRetryPolicy.shouldRetry(1, false, false))
        assertTrue(SubtitleDiscoveryRetryPolicy.shouldRetry(3, false, false))
        assertTrue(SubtitleDiscoveryRetryPolicy.shouldRetry(6, false, false))
        assertFalse(SubtitleDiscoveryRetryPolicy.shouldRetry(1, true, true))
        assertFalse(SubtitleDiscoveryRetryPolicy.shouldRetry(3, true, true))
    }
}
