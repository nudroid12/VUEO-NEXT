package com.vueo.shared.core.source

/** Standalone checks so the policy can be verified without Android dependencies. */
fun main() {
    fun rejected(name: String = "auto", url: String? = null): Boolean =
        SourceEpisodePolicy.mismatch(name, url, 1, 5) != null

    check(rejected(url = "https://host/Reacher---Season-2.Ep5.m3u8"))
    check(!rejected(url = "https://host/Reacher---Season-1.Ep5.m3u8"))
    check(rejected(name = "Reacher S02E05"))
    check(rejected(name = "Reacher S01E06 1080p"))
    check(!rejected(name = "Reacher S01E05 1080p"))
    check(rejected(name = "S01E05", url = "https://host/Reacher-S02E05.m3u8"))
    check(rejected(url = "https://host/Season%202/Episode%205.m3u8"))
    check(!rejected(url = "https://host/assets/123456-205-720.m3u8"))
    check(!rejected(url = "https://s02e05.host/file.m3u8?token=S02E05#S02E05"))
    check(!rejected(name = "Season 2 collection", url = "https://host/episode5.m3u8"))
    check(!rejected(name = "IDS02E05abc"))
    check(!rejected(url = "not a valid URL"))
    check(!rejected())
    check(SourceEpisodePolicy.mismatch("S00E05", null, 0, 5) == null)
    println("Source episode policy: 14 checks passed")
}
