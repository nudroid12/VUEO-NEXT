package com.vueo.validation

import com.vueo.shared.core.stremio.*
import com.vueo.shared.core.extensions.*
import com.vueo.shared.core.source.*
import com.vueo.shared.core.media.*
import kotlinx.coroutines.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun main(): Unit = runBlocking {
    var passed=0
    suspend fun test(name:String, block:suspend ()->Unit) { block(); passed++; println("PASS $name") }
    test("HTTP LAN URL installation") { check(StremioUrls.normalizeManifest(" http://192.168.1.2:7000/config/ ")=="http://192.168.1.2:7000/config/manifest.json") }
    test("HTTPS bare hostname") { check(StremioUrls.normalizeManifest("example.test")=="https://example.test/manifest.json") }
    test("Stremio links") {
        check(StremioUrls.normalizeManifest("stremio://example.test/manifest.json")=="https://example.test/manifest.json")
        check(StremioUrls.normalizeManifest("stremio://http://example.test/manifest.json")=="http://example.test/manifest.json")
    }
    test("Uppercase scheme and manifest suffix") { check(StremioUrls.normalizeManifest("HTTP://example.test/MANIFEST.JSON")=="http://example.test/MANIFEST.JSON") }
    test("Configured query preserved byte for byte") {
        check(StremioUrls.resource("http://example.test/Cfg/manifest.json?token=A%2FB+Z&v=2#ignored", "stream", "series", "tt12:1:2")=="http://example.test/Cfg/stream/series/tt12%3A1%3A2.json?token=A%2FB+Z&v=2")
    }
    test("Extras encoded without consuming configuration query") {
        check(StremioUrls.resource("https://example.test/manifest.json?k=x", "catalog","movie","top",mapOf("search" to "a/b c", "skip" to "20"))=="https://example.test/catalog/movie/top/search=a%2Fb%20c&skip=20.json?k=x")
    }
    test("Configure URL keeps configured path and query") { check(StremioUrls.configuration("http://example.test/Cfg/manifest.json?k=x")=="http://example.test/Cfg/configure?k=x") }
    test("Invalid and unsupported schemes rejected") {
        listOf("", "file:///tmp/manifest.json", "ftp://example.test/x", "https:///manifest.json", "https://example.test/bad space").forEach {
            check(runCatching { StremioUrls.normalizeManifest(it) }.isFailure)
        }
    }
    test("Mobile deep-link entry uses the same HTTP normalizer") {
        check(com.vueo.mobile.core.extensions.normalizeStremioAddonManifestUrl("stremio://http://example.test/Cfg?key=x")=="http://example.test/Cfg/manifest.json?key=x")
        check(com.vueo.mobile.core.extensions.normalizeStremioAddonManifestUrl(null)==null)
        check(com.vueo.mobile.core.extensions.normalizeStremioAddonManifestUrl("file:///tmp/manifest.json")==null)
    }
    val manifestJson="""{"id":"test","name":"Test","version":"1","types":["movie","series"],"idPrefixes":["tt"],"resources":[{"name":"stream","types":["movie"],"idPrefixes":["tmdb:"]},"subtitles","meta","catalog"],"catalogs":[{"type":"movie","id":"top","extra":[{"name":"search"}]}]}"""
    val manifest=StremioManifestParser.parse("http://example.test/manifest.json?k=x",manifestJson)
    test("Object resource restrictions override defaults") { check(manifest.supportsRequest("stream","movie","tmdb:123")); check(!manifest.supportsRequest("stream","series","tmdb:123"));check(!manifest.supportsRequest("stream","movie","tt123")) }
    test("String resource inherits type and prefixes") { check(manifest.supportsRequest("subtitles","tv","tt12:1:2"));check(!manifest.supportsRequest("subtitles","movie","tmdb:12")) }
    test("Prefix matching is case sensitive") { check(!manifest.supportsRequest("subtitles","movie","TT12")) }
    test("Unrestricted resource accepts arbitrary ID") { val m=StremioManifestParser.parse("https://example.test/manifest.json","""{"id":"x","name":"x","resources":["stream"]}""");check(m.supportsRequest("stream","movie","custom:12")) }
    test("Missing resource is not queried when other resources declared") { check(!manifest.supportsRequest("unknown","movie","tt12")) }
    val requests=mutableListOf<String>()
    val fake=object:StremioHttpClient { override suspend fun get(url:String):String { requests+=url;return """{"streams":[],"subtitles":[]}""" } }
    val client=StremioAddonClient(manifest,fake)
    test("Incompatible request performs no network I/O") { client.streams("series","tmdb:123");check(requests.isEmpty()) }
    test("TV alias uses series endpoint") { client.subtitles("tv","tt12:1:2"); check(requests.last()=="http://example.test/subtitles/series/tt12%3A1%3A2.json?k=x") }
    val payload="""{"streams":[{"name":"Server 1080p","url":"http://example.test/video.m3u8","mimeType":"application/x-mpegURL","behaviorHints":{"filename":"original.mkv","videoHash":"hash","bingeGroup":"series-release","notWebReady":true,"videoSize":123,"proxyHeaders":{"request":{"Referer":"http://example.test/","Range":"bytes=0-"},"response":{"Content-Type":"video/mp2t"}}},"subtitles":[{"id":"english","lang":"en","url":"http://example.test/en.vtt","headers":{"X-Subtitle":"required"}},{"id":"malay","lang":"ms","url":"https://example.test/ms.vtt"},{"url":"file:///tmp/local.vtt"}]}]}"""
    val candidate=StremioStreamParser.parseSources(payload,manifest).single()
    test("HTTP media classified as directly playable") { check(candidate.isDirectPlayable);check(candidate.toStreamSource().isDirectPlayable) }
    test("Inline English and Malay subtitle payload retained") { check(candidate.subtitles.map{it.language}==listOf("en","ms"));check(candidate.subtitles[0].headers["X-Subtitle"]=="required") }
    test("Video headers preserve Referer and exclude Range") { check(candidate.headers==mapOf("Referer" to "http://example.test/")) }
    test("Behavior hints and MIME preserved") { check(candidate.addonHints?.filename=="original.mkv");check(candidate.addonHints?.bingeGroup=="series-release");check(candidate.addonHints?.videoHash=="hash");check(candidate.addonHints?.notWebReady==true);check(candidate.addonHints?.responseHeaders?.get("Content-Type")=="video/mp2t");check(candidate.mimeType=="application/x-mpegURL") }
    test("Source conversion round trip retains hints and subtitles") { val round=candidate.toStreamSource().toSourceCandidate();check(round.subtitles==candidate.subtitles);check(round.addonHints==candidate.addonHints) }
    test("Subtitle endpoint headers retained") { check(StremioStreamParser.parseSubtitles("""{"subtitles":[{"url":"http://example.test/en.vtt","lang":"en","headers":{"Referer":"http://example.test/"}}]}""",manifest).single().headers["Referer"]=="http://example.test/") }
    val extHttp=object:StremioHttpClient { override suspend fun get(url:String):String { requests+=url;return if(url.substringBefore('?').endsWith("manifest.json"))manifestJson else if(url.contains("/stream/"))payload else if(url.contains("/catalog/"))"""{"metas":[]}""" else """{"meta":null,"subtitles":[]}""" } }
    val ext=StremioAddonExtension.fromManifestUrl("http://example.test/Cfg?token=x",extHttp)
    test("Extension descriptor keeps resource constraints") { check(ext.descriptor.idPrefixes==setOf("tt"));check(!ext.descriptor.supportsAddonRequest("stream","series","tmdb:1"));check(ext.descriptor.supportsAddonRequest("stream","movie","tmdb:1")) }
    test("Extension passes inline subtitles and hints to player source") { val result=ext.streams("movie","tmdb:123").single();check(result.subtitles==candidate.subtitles);check(result.addonHints==candidate.addonHints) }
    test("Catalog resource query survives extension URL building") { ext.catalog("movie","top",mapOf("skip" to "20"));check(requests.last()=="http://example.test/Cfg/catalog/movie/top/skip=20.json?token=x") }
    test("Different configuration URLs remain different installations") { val other=StremioAddonExtension.fromManifestUrl("http://example.test/Cfg?token=y",extHttp);check(other.descriptor.installationId!=ext.descriptor.installationId) }
    test("Registry refresh preserves disabled state") { val registry=InstalledExtensionRegistry(); registry.install(ext){false};registry.install(ext);check(!registry.isEnabled(ext.descriptor.installationId)) }
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    val seen=java.util.Collections.synchronizedList(mutableListOf<String>())
    server.createContext("/") { exchange ->
        seen+=exchange.requestURI.toASCIIString()
        val body=if(exchange.requestURI.path.endsWith("manifest.json")) manifestJson else if(exchange.requestURI.path.contains("/stream/"))payload else """{"subtitles":[]}"""
        val bytes=body.toByteArray();exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}
    }
    server.start()
    try {
        test("Real OkHttp HTTP manifest and stream round trip") {
            val e=StremioAddonExtension.fromManifestUrl("http://127.0.0.1:${server.address.port}/Cfg/manifest.json?key=A%2FB")
            check(e.streams("movie","tmdb:12").single().subtitles.size==2)
            check(seen==listOf("/Cfg/manifest.json?key=A%2FB", "/Cfg/stream/movie/tmdb%3A12.json?key=A%2FB"))
        }
        test("Real transport rejects file scheme before network") { check(runCatching{DefaultStremioHttpClient.get("file:///etc/hosts")}.isFailure) }
    } finally { server.stop(0) }
    test("HTTP episode artwork accepted without scheme rewrite") {
        check(com.vueo.shared.core.player.EpisodeArtworkPolicy.usable("HTTP://example.test/episode.jpg")=="http://example.test/episode.jpg")
        check(com.vueo.shared.core.player.EpisodeArtworkPolicy.usable("file:///tmp/art.jpg")==null)
    }
    println("$passed checks passed")
    kotlin.system.exitProcess(0)
}
