package com.vueo.shared.core.smartsubs

import com.vueo.shared.core.media.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSubsSourcePolicyTest {
    private fun track(id: String, provider: String, language: String = "en") =
        SubtitleTrack(id = id, providerId = provider, providerName = provider,
            language = language, url = "https://subs.example/$id.vtt")

    @Test fun forwardsOriginalOrderAndLanguagesForWorkerSelection() {
        val addon = track("addon", "addon", "ms")
        val plugin = track("plugin", "plugin:repo:provider")
        val input = listOf(addon, plugin, plugin)
        assertEquals(input, SmartSubsSourcePolicy.eligibleSources(input, false))
        assertEquals(input, SmartSubsSourcePolicy.eligibleSources(input, true))
    }

    @Test fun neverFeedsGeneratedOutputBackToWorker() {
        val original = track("original", "plugin:repo:provider")
        val wrappedAi = track("addon:0:gemini-ai-hash", "addon")
        val ownAi = track("any-id", "vueo-smartsubs-ai")
        assertEquals(listOf(original), SmartSubsSourcePolicy.eligibleSources(listOf(wrappedAi, original, ownAi)))
    }

    @Test fun boundsTransportWithoutApplyingFiveCandidateLimit() {
        val input = (1..60).map { track("track-$it", "addon") }
        assertEquals(input.take(50), SmartSubsSourcePolicy.eligibleSources(input))
    }
}
