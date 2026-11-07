package com.vueo.shared.core.player
object SubtitleCipherDecoder {
 val entered = java.util.concurrent.CountDownLatch(1)
 val release = java.util.concurrent.CountDownLatch(1)
 fun decode(bytes: ByteArray, keys: List<String>, ivs: List<String>): ByteArray {
  entered.countDown()
  check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
  return bytes
 }
}
