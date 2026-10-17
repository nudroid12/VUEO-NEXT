package com.vueo.shared.core.plugin

fun main() {
    val hosts = listOf("media.example")
    check(WebViewSessionPolicy.isMediaUrl("https://media.example/mp/file.mp4?token=a", hosts))
    check(WebViewSessionPolicy.isMediaUrl("https://media.example/proxy/master.m3u8", hosts))
    listOf("https://media.example/app.js?asset=.mp4", "https://media.example/init.mp4", "https://media.example/trailer.mp4", "https://ads.example/movie.mp4", "https://user:password@media.example/movie.mp4", "blob:https://media.example/id").forEach {
        check(!WebViewSessionPolicy.isMediaUrl(it, hosts)) { it }
    }
    check(!WebViewSessionPolicy.isMediaUrl("https://media.example/movie.mp4", emptyList()))
    val media = "https://media.example/mp/film.mp4?sign=browser"
    check(WebViewSessionPolicy.singleBlobCandidate(emptyList(), hosts) == null)
    check(WebViewSessionPolicy.singleBlobCandidate(listOf(media, media), hosts) == media)
    check(WebViewSessionPolicy.singleBlobCandidate(listOf(media, "https://ads.example/ad.mp4"), hosts) == media)
    check(WebViewSessionPolicy.singleBlobCandidate(listOf(media, "https://media.example/other.mp4"), hosts) == null)
    check(WebViewSessionPolicy.singleBlobCandidate(listOf(media, media + "2"), hosts) == null)
    check(WebViewSessionPolicy.singleBlobCandidate(listOf("blob:https://media.example/id", "https://media.example/segment.mp4"), hosts) == null)
    val defaults = mapOf("User-Agent" to "default", "Referer" to "https://entry.example/")
    val observed = mapOf("user-agent" to "native", "referer" to "https://player.example/", "origin" to "https://player.example", "cookie" to "session=example")
    check(WebViewSessionPolicy.withDefaults(observed, defaults)["User-Agent"] == "native")
    check(WebViewSessionPolicy.withDefaults(observed, defaults)["Referer"] == "https://player.example/")
    val merged = WebViewSessionPolicy.mergeHeaders(defaults, observed, true)
    check(merged["User-Agent"] == "native")
    check(merged["Referer"] == "https://player.example/")
    check(merged["Origin"] == "https://player.example")
    check(merged["Cookie"] == "session=example")
    check(merged.keys.count { it.equals("referer", true) } == 1)
    check(WebViewSessionPolicy.mergeHeaders(merged, defaults, false) == merged)
    check("Bad" !in WebViewSessionPolicy.mergeHeaders(merged, mapOf("Bad" to "x\r\ny"), true))
    println("PASS: real media paths/host restriction, query false positives, segments/credentials, authoritative header update and no later default downgrade")
}
