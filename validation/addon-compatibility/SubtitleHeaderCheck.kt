package com.vueo.validation
import android.net.Uri
import androidx.media3.datasource.*
import com.vueo.shared.core.player.*

fun main() {
    class Upstream:DataSource {
        var spec:DataSpec?=null
        var consumed=false
        override fun addTransferListener(t:TransferListener) {}
        override fun open(s:DataSpec):Long { spec=s; consumed=false;return -1 }
        override fun read(b:ByteArray,o:Int,l:Int):Int { if(consumed)return -1;consumed=true;val text="WEBVTT\n\n00:00.000 --> 00:01.000\nhello\n".toByteArray();text.copyInto(b,o);return text.size }
        override fun getUri():Uri?=spec?.uri
        override fun close() {}
    }
    val upstream=Upstream();val source=SubtitleSessionDataSource.Factory{upstream}.createDataSource()
    val url="http://example.test/headers.vtt"
    SubtitleSessionCache.register(url,mapOf("Referer" to "http://subtitle-origin.test/", "X-Subtitle" to "required"))
    source.open(DataSpec.Builder().setUri(Uri.parse(url)).setHttpRequestHeaders(mapOf("referer" to "http://video-origin.test/", "User-Agent" to "VUEO")).build())
    check(upstream.spec!!.httpRequestHeaders==mapOf("User-Agent" to "VUEO", "Referer" to "http://subtitle-origin.test/", "X-Subtitle" to "required"))
    println("PASS Subtitle-specific headers reach Media3 upstream without duplicate Referer")
    check(String(SubtitleSessionCache.get(url)!!).contains("hello"))
    println("PASS HTTP subtitle is prepared by the existing session loader")
    source.close()
    val video="http://example.test/video.m3u8"
    source.open(DataSpec.Builder().setUri(Uri.parse(video)).setHttpRequestHeaders(mapOf("Referer" to "http://video-origin.test/")).build())
    check(upstream.spec!!.httpRequestHeaders==mapOf("Referer" to "http://video-origin.test/"))
    println("PASS Subtitle headers do not leak into next video request")
    source.close()
    println("3 subtitle handoff checks passed")
}
