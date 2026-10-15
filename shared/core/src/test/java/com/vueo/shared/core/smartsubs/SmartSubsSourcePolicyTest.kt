package com.vueo.shared.core.smartsubs

import com.vueo.shared.core.media.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSubsSourcePolicyTest {
    private fun track(id: String, provider: String, language: String = "en", url: String = "https://subs.example/$id.vtt") =
        SubtitleTrack(id = id, providerId = provider, providerName = provider, language = language, url = url)

    @Test fun defaultModeAcceptsOnlyPluginEnglishOriginals() {
        val plugin = track("plugin-original", "plugin:repo:anime")
        val addon = track("opensubtitles-eng-42", "smartsubsv2")
        assertEquals(listOf(plugin), SmartSubsSourcePolicy.eligibleSources(listOf(plugin, addon)))
    }

    @Test fun allSourcesAddsAddonEnglishWithoutTranslatingExistingAi() {
        val plugin = track("plugin-original", "plugin:repo:anime")
        val addon = track("smartsubsv2:0:opensubtitles-eng-42", "smartsubsv2")
        val ai = track("smartsubsv2:1:gemini-ai-42", "smartsubsv2")
        val ownAi = track("malformed-ai", "vueo-smartsubs-ai")
        val malay = track("malay-original", "addon", "ms")
        assertEquals(listOf(plugin, addon), SmartSubsSourcePolicy.eligibleSources(listOf(plugin, addon, ai, ownAi, malay), true))
    }

    @Test fun bothModesKeepFormatAndPublicHttpsRestrictions() {
        val srt = track("valid", "plugin:repo:anime", "eng", "https://subs.example/valid.srt")
        val invalid = listOf(
            track("ssa", "plugin:repo:anime", url = "https://subs.example/sub.ass"),
            track("http", "plugin:repo:anime", url = "http://subs.example/sub.vtt"),
            track("private", "plugin:repo:anime", url = "https://127.0.0.1/sub.vtt"),
            track("credentials", "plugin:repo:anime", url = "https://user:pass@subs.example/sub.vtt"),
        )
        for (all in listOf(false, true)) assertEquals(listOf(srt), SmartSubsSourcePolicy.eligibleSources(invalid + srt, all))
    }

    @Test fun allSourcesDeduplicatesUrlsAndStillLimitsToFive() {
        val candidates = (1..8).map { track("source-$it", "addon") }
        val duplicate = candidates[0].copy(id = "different-id-same-url")
        assertEquals(candidates.take(5), SmartSubsSourcePolicy.eligibleSources(listOf(candidates[0], duplicate) + candidates.drop(1), true))
    }

    @Test fun switchingBackRestoresPluginOnlyEligibility() {
        val plugin = track("plugin", "plugin:repo:anime")
        val addon = track("addon", "addon")
        val candidates = listOf(plugin, addon)
        assertEquals(2, SmartSubsSourcePolicy.eligibleSources(candidates, true).size)
        assertEquals(listOf(plugin), SmartSubsSourcePolicy.eligibleSources(candidates, false))
    }
}
