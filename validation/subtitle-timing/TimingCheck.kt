package com.vueo.shared.core.player
import com.vueo.shared.core.diagnostics.*
import com.vueo.shared.core.media.*
import androidx.media3.datasource.*
import android.net.Uri
import javax.crypto.Cipher
import javax.crypto.spec.*
import kotlinx.coroutines.runBlocking
fun main()= runBlocking {
 fun b64(b:ByteArray)=java.util.Base64.getEncoder().encodeToString(b)
 val key="0123456789abcdef".toByteArray();val iv="fedcba9876543210".toByteArray()
 val cipher=Cipher.getInstance("AES/CBC/PKCS5Padding")
 cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))
 val encrypted=b64(cipher.doFinal("Timing fixture".toByteArray()))
 val raw=listOf("1","00:00:01,000 --> 00:00:02,000",encrypted,"").joinToString("\n").toByteArray()
 val config=SubtitleTextTransform("aes-cbc","cue-lines",listOf(SubtitleCipherKey(b64(key),b64(iv))))
 var hits=0
 val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
 server.createContext("/private-token-fixture.srt"){e->hits++;e.sendResponseHeaders(200,raw.size.toLong());e.responseBody.use{it.write(raw)}}
 server.start()
 try{
  val url="http://127.0.0.1:${server.address.port}/private-token-fixture.srt"
  SubtitleSessionCache.register(url,textTransform=config)
  check(SubtitleReadinessProbe.awaitReady(url))
  check(String(SubtitleSessionCache.get(url)!!).contains("Timing fixture"))
  check(SubtitleReadinessProbe.awaitReady(url))
  check(hits==1)
  val events=RuntimeDiagnostics.events.toList()
  for(stage in listOf("await_ready","readiness_download","decrypt","normalize"))check(events.any{it.contains("stage=$stage")&&it.contains("state=end")})
  check(events.any{it.contains("result=cache_hit")})
  check(events.none{it.contains("private-token")||it.contains("Timing fixture")||it.contains(b64(key))||it.contains("http://")})
  val u2="$url?second"
  SubtitleSessionCache.register(u2,textTransform=config)
  var upstreamOpens=0
  val factory=DataSource.Factory{object:DataSource by ByteArrayDataSource(raw){
   private val delegate=ByteArrayDataSource(raw)
   override fun open(s:DataSpec):Long { upstreamOpens++;return delegate.open(s) }
   override fun read(b:ByteArray,o:Int,l:Int)=delegate.read(b,o,l)
  }}
  val ds=SubtitleSessionDataSource.Factory(factory).createDataSource()
  ds.open(DataSpec(Uri.parse(u2)));ds.close()
  ds.open(DataSpec(Uri.parse(u2)));ds.close()
  check(upstreamOpens==1)
  check(RuntimeDiagnostics.events.any{it.contains("stage=media3_download")&&it.contains("state=end result=ok")})
  check(RuntimeDiagnostics.events.any{it.contains("stage=media3_cache")&&it.contains("result=hit")})
  PerformanceDiagnostics.enabled=false
  val before=RuntimeDiagnostics.events.size
  val span=SubtitlePreparationDiagnostics.start(url,"disabled")
  SubtitlePreparationDiagnostics.end(span)
  check(span==null && RuntimeDiagnostics.events.size==before)
  println("PASS real local HTTP, decrypt/normalize timings, cache avoids repeat HTTP, datasource paths, URL/key/text privacy and diagnostics-off silence")
 }finally{server.stop(0)}
}
