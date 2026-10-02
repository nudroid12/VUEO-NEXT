package com.vueo.shared.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMediaPolicyTest {
    @Test fun declaredHlsSupportsExtensionlessUrl() {
        assertEquals("application/x-mpegURL", PlaybackMediaPolicy.resolve("application/vnd.apple.mpegurl", "https://example.test/token", null, null))
    }
    @Test fun legacyVoeHlsLabelSupportsExtensionlessUrl() {
        assertEquals("application/x-mpegURL", PlaybackMediaPolicy.resolve(null, "https://example.test/token", null, "Server_5_Voe_HLS_Auto"))
    }
    @Test fun plainVoeNameDoesNotForceHls() {
        assertNull(PlaybackMediaPolicy.resolve(null, "https://example.test/token", "VOE Auto", null))
    }
    @Test fun declaredDashTakesPrecedenceOverLabel() {
        assertEquals("application/dash+xml", PlaybackMediaPolicy.resolve("dash", "https://example.test/token", "HLS", null))
    }
    @Test fun signedManifestRetainsHlsType() {
        assertEquals("application/x-mpegURL", PlaybackMediaPolicy.resolve(null, "https://example.test/MASTER.M3U8?token=secret#x", null, null))
    }
    @Test fun mp4IsNotOverriddenByLabel() {
        assertEquals("video/mp4", PlaybackMediaPolicy.resolve(null, "https://example.test/video.mp4", "HLS", null))
    }
}
