package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.source.toSourceCandidate
import com.vueo.shared.core.source.toStreamSource
import org.junit.Assert.*
import org.junit.Test

class StreamSubtitlePolicyTest {
    private fun track(id: String) = SubtitleTrack(id, "en", "https://cdn.test/$id.vtt",
        "plugin:test", "AnimeDunya", headers = mapOf("Referer" to "https://anime-dunya.com/"))
    private fun source(tracks: List<SubtitleTrack>) = StreamSource("AnimeDunya",
        url = "https://cdn.test/master.m3u8", providerId = "plugin:test",
        providerName = "AnimeDunya", subtitles = tracks)

    @Test fun switchingSourceDropsPreviousSidecarsAndKeepsAddonTracks() {
        val first = track("first")
        val second = track("second")
        val addon = track("addon")
        assertEquals(listOf(first, addon), StreamSubtitlePolicy.forPlayback(source(listOf(first)), listOf(addon)))
        assertEquals(listOf(second, addon), StreamSubtitlePolicy.forPlayback(source(listOf(second)), listOf(addon)))
        assertEquals(listOf(addon), StreamSubtitlePolicy.forPlayback(source(emptyList()), listOf(addon)))
    }

    @Test fun sourceMetadataAndHeadersSurviveDiscoveryCacheAdapters() {
        val original = source(listOf(track("english")))
        val restored = original.toSourceCandidate().toStreamSource()
        assertEquals(original, restored)
        assertEquals(original.subtitles, StreamSubtitlePolicy.forPlayback(restored, emptyList()))
    }

    @Test fun duplicateAddonUrlKeepsSourceHeaders() {
        val owned = track("english")
        val addon = owned.copy(headers = emptyMap(), providerName = "Addon")
        assertEquals(listOf(owned), StreamSubtitlePolicy.forPlayback(source(listOf(owned)), listOf(addon)))
    }
    @Test fun onsenSidecarIsAvailableWhilePlayingDunyaWithItsOwnMimeAndHeaders() {
        val onsen = track("onsen").copy(providerId = "plugin:onsen", providerName = "AnimeOnsen",
            mimeType = "text/x-ssa", headers = mapOf("Authorization" to "Bearer test-token"))
        val dunya = source(listOf(track("dunya")))
        val onsenSource = source(listOf(onsen)).copy(providerId = "plugin:onsen")
        val episodeTracks = StreamSubtitlePolicy.forEpisode(listOf(dunya, onsenSource), emptyList())
        val playback = StreamSubtitlePolicy.forPlayback(dunya, episodeTracks)
        assertEquals(listOf(dunya.subtitles.single(), onsen), playback)
        assertEquals("text/x-ssa", playback.last().mimeType)
        assertEquals("Bearer test-token", playback.last().headers["Authorization"])
    }

    @Test fun laterAddonPassRetainsProviderSubsAndNewEpisodeDoesNotReusePreviousTracks() {
        val provider = track("episode-one")
        val addon = track("addon")
        val sources = listOf(source(listOf(provider)))
        assertEquals(listOf(provider), StreamSubtitlePolicy.forEpisode(sources, emptyList()))
        assertEquals(listOf(provider, addon), StreamSubtitlePolicy.forEpisode(sources, listOf(addon)))
        assertTrue(StreamSubtitlePolicy.forEpisode(emptyList(), emptyList()).isEmpty())
        val next = track("episode-two")
        assertEquals(listOf(next), StreamSubtitlePolicy.forEpisode(listOf(source(listOf(next))), emptyList()))
    }

    @Test fun freshCredentialsWinOverCachedDuplicateAndAddonDuplicate() {
        val cached = track("same-url").copy(headers = mapOf("Authorization" to "old"))
        val fresh = cached.copy(headers = mapOf("Authorization" to "new"))
        assertEquals(listOf(fresh), StreamSubtitlePolicy.forEpisode(
            listOf(source(listOf(fresh)), source(listOf(cached))), listOf(cached.copy(headers = emptyMap()))))
    }

}
