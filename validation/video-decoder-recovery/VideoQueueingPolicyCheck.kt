package com.vueo.shared.core.player

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    val p = VideoQueueingPolicy()
    verify(p.target("format") == null)
    verify(p.request("format", null, "codec", false) == null)
    verify(p.request("format", "codec", null, false) == null)
    verify(p.request("format", "codec", "different", false) == null)
    verify(p.request("format", "codec", "codec", true) == null)
    verify(p.request("format", "codec", "codec", false) == "codec")
    verify(p.target("format") == "codec")
    verify(p.target("other-format") == null)
    p.failed("other-format", "codec")
    verify(p.target("format") == "codec")
    p.failed("format", "other-codec")
    verify(p.target("format") == "codec")
    verify(p.request("format", "codec", "codec", false) == null)
    p.failed("format", "codec")
    verify(p.target("format") == null)
    verify(p.request("format", "codec", "codec", false) == null)
    verify(p.request("other", "codec2", "codec2", false) == null)
    verify(VideoQueueingPolicy().request("format", "codec", "codec", false) == "codec")
    println("PASS: $checks queueing retry checks")
}
