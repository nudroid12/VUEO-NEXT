package com.vueo.shared.core.player

import java.util.Locale

fun main() {
    var checks = 0
    fun checkThat(value: Boolean) { check(value); checks++ }
    fun rejected(url: String = "https://media.example/video.mkv", headers: Map<String, String> = emptyMap()) {
        checkThat(runCatching { SoftwarePlaybackPolicy.mediaOptions(url, headers, 0) }.isFailure)
    }
    val source = "https://storage.example/a.mkv?token=signed%2Bvalue"
    val opts = SoftwarePlaybackPolicy.mediaOptions(source, mapOf("User-Agent" to "test agent", "Referer" to "https://host.example/file"), 123456)
    checkThat(opts.contains(":http-user-agent=test agent"))
    checkThat(opts.contains(":http-referrer=https://host.example/file"))
    checkThat(opts.contains(":start-time=123.456"))
    checkThat(SoftwarePlaybackPolicy.mediaOptions(source, emptyMap(), 0).none { it.startsWith(":start-time=") })
    checkThat(SoftwarePlaybackPolicy.mediaOptions(source, mapOf("USER-AGENT" to "case"), 0).contains(":http-user-agent=case"))
    checkThat(SoftwarePlaybackPolicy.mediaOptions(source, mapOf("referrer" to "https://referer.example/"), 0).contains(":http-referrer=https://referer.example/"))
    val original = Locale.getDefault()
    try {
        Locale.setDefault(Locale.GERMANY)
        checkThat(SoftwarePlaybackPolicy.mediaOptions(source, emptyMap(), 1500).contains(":start-time=1.500"))
    } finally { Locale.setDefault(original) }
    rejected("file:///data/local/private")
    rejected("intent://player")
    rejected("https:///missing-host")
    rejected("http://media.example/a.mkv")
    rejected(headers = mapOf("Cookie" to "required-session"))
    rejected(headers = mapOf("Authorization" to "Bearer required-token"))
    rejected(headers = mapOf("Origin" to "https://host.example"))
    rejected(headers = mapOf("User-Agent" to "agent\r\nCookie: injected"))
    rejected(headers = mapOf("Referer" to "url\u0000injection"))
    rejected(headers = linkedMapOf("User-Agent" to "one", "user-agent" to "two"))
    checkThat(SoftwarePlaybackPolicy.seekTarget(4000, -10000, 90000) == 0L)
    checkThat(SoftwarePlaybackPolicy.seekTarget(85000, 10000, 90000) == 90000L)
    checkThat(SoftwarePlaybackPolicy.seekTarget(15000, -10000, 0) == 5000L)
    println("Software playback transport/resume checks: $checks passed")
}
