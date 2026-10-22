package com.vueo.shared.core.player

fun main() {
    var count = 0
    fun verify(ok: Boolean) { check(ok); count++ }
    val url = "https://storage.example/movie.mkv?token=one%2Btwo&sig=unchanged"
    ExternalVlcPolicy.validateUrl(url)
    verify(true)
    for (invalid in listOf("http://storage.example/a.mkv", "file:///data/local/a.mkv", "intent://stream", "https:///missing", "https://user:password@host.example/a.mkv")) {
        verify(runCatching { ExternalVlcPolicy.validateUrl(invalid) }.isFailure)
    }
    val mkv = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 0, 0, 0, 0)
    val mp4 = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)
    verify(ExternalVlcPolicy.acceptsProbe(206, "application/octet-stream", mkv))
    verify(ExternalVlcPolicy.acceptsProbe(200, "application/octet-stream", mp4))
    verify(ExternalVlcPolicy.acceptsProbe(206, "video/x-matroska", mkv))
    verify(ExternalVlcPolicy.acceptsProbe(206, "Application/Octet-Stream; charset=binary", mkv))
    verify(ExternalVlcPolicy.acceptsProbe(206, "", mp4))
    verify(!ExternalVlcPolicy.acceptsProbe(403, "video/mp4", mp4))
    verify(!ExternalVlcPolicy.acceptsProbe(404, "application/octet-stream", mkv))
    verify(!ExternalVlcPolicy.acceptsProbe(416, "video/mp4", mp4))
    verify(!ExternalVlcPolicy.acceptsProbe(206, "video/mp4", byteArrayOf()))
    verify(!ExternalVlcPolicy.acceptsProbe(200, "video/mp4", "  <html>challenge</html>".toByteArray()))
    verify(!ExternalVlcPolicy.acceptsProbe(200, "text/html", mkv))
    verify(!ExternalVlcPolicy.acceptsProbe(200, "application/octet-stream", "unknown data".toByteArray()))
    verify(!ExternalVlcPolicy.acceptsProbe(200, "video/mp4", "{\"error\":401}".toByteArray()))
    verify(!ExternalVlcPolicy.acceptsProbe(200, "application/vnd.apple.mpegurl", "#EXTM3U\nsegment.ts".toByteArray()))
    verify(ExternalVlcPolicy.returnPosition(url, url, 123456L) == 123456L)
    verify(ExternalVlcPolicy.returnPosition(url, url, 12000) == 12000L)
    verify(ExternalVlcPolicy.returnPosition(url, "https://unrelated.example/movie.mkv", 123456L) == null)
    verify(ExternalVlcPolicy.returnPosition(url, null, 123456L) == null)
    verify(ExternalVlcPolicy.returnPosition(url, url, -1L) == null)
    verify(ExternalVlcPolicy.returnPosition(url, url, null) == null)
    verify(SourcePlaybackTarget.DEFAULT.next() == SourcePlaybackTarget.SOFTWARE_TEST)
    verify(SourcePlaybackTarget.SOFTWARE_TEST.next() == SourcePlaybackTarget.VLC)
    verify(SourcePlaybackTarget.VLC.next() == SourcePlaybackTarget.DEFAULT)
    println("External VLC policy checks: $count passed")
}
