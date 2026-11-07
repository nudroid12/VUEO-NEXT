package com.vueo.shared.core.player
import com.vueo.shared.core.media.*
fun main() {
 val url="https://fixture.test/a.srt"
 val config=SubtitleTextTransform("aes-cbc","cue-lines",listOf(SubtitleCipherKey("key","iv")))
 SubtitleSessionCache.register(url, textTransform=config)
 var error: Throwable?=null
 val worker=Thread { try { SubtitleSessionCache.put(url,"1\n00:00:01,000 --> 00:00:02,000\nText".toByteArray()) } catch(t:Throwable){error=t} }
 worker.start()
 check(SubtitleCipherDecoder.entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
 // This must complete while the decoder is deliberately blocked.
 val changed=java.util.concurrent.CountDownLatch(1)
 Thread {
  SubtitleSessionCache.register(url, textTransform=null)
  check(SubtitleSessionCache.get(url)==null)
  changed.countDown()
 }.start()
 val unlocked=changed.await(2,java.util.concurrent.TimeUnit.SECONDS)
 SubtitleCipherDecoder.release.countDown()
 worker.join(5000)
 check(unlocked) { "Decoder held the global cache lock" }
 check(!worker.isAlive)
 check(error is java.io.IOException)
 check(SubtitleSessionCache.get(url)==null) { "Obsolete transform result entered cache" }
 println("PASS blocked transform does not block cache; changed registration rejects stale result")
}
