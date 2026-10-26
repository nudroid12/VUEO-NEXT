package com.vueo.shared.core.player
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
fun main() {
 fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray())
 val config=com.vueo.shared.core.media.SubtitleTextTransform("aes-cbc","cue-lines",listOf(com.vueo.shared.core.media.SubtitleCipherKey(b64("0123456789abcdef"),b64("fedcba9876543210"))))
 val cipher=Cipher.getInstance("AES/CBC/PKCS5Padding")
 cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec("0123456789abcdef".toByteArray(),"AES"),IvParameterSpec("fedcba9876543210".toByteArray()))
 val encoded=java.util.Base64.getEncoder().encodeToString(cipher.doFinal("Hello from provider".toByteArray()))
 val raw="1\n00:00:01,000 --> 00:00:03,000\n$encoded\n".toByteArray()
 val u="https://sub.test/en.srt"
 SubtitleSessionCache.register(u,mapOf("Referer" to "https://provider.test/"),config)
 SubtitleSessionCache.put(u,raw)
 check(String(SubtitleSessionCache.get(u)!!).contains("Hello from provider"))
 check(!String(SubtitleSessionCache.get(u)!!).contains(encoded))
 check(SubtitleSessionCache.headers(u).size==1)
 val untouched="https://sub.test/other.srt"
 SubtitleSessionCache.register(untouched)
 SubtitleSessionCache.put(untouched,raw)
 check(String(SubtitleSessionCache.get(untouched)!!).contains(encoded))
 SubtitleSessionCache.register(u,emptyMap(),null)
 check(SubtitleSessionCache.get(u)==null)
 println("PASS production cache prepares only marked sidecars, reuses plaintext for Live Sync/readiness, keeps headers separate, evicts on transform change")
}
