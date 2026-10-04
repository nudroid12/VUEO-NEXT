package com.vueo.shared.core.plugin
import com.dokar.quickjs.*
import com.dokar.quickjs.binding.*
import kotlinx.coroutines.*
import java.io.File

fun main(args: Array<String>) = runBlocking {
 val template = File(args.single()).readText()
 suspend fun test(name: String, provider: String, expected: String, expectCancel: Boolean = false) {
  val tasks = ProviderExecutionTasks()
  var cancelled = false
  val started = System.nanoTime()
  val result = withTimeout(3000) { quickJs {
   function<String, Boolean>("__vueoFinishExecution") { tasks.finish(); true }
   function<String, Boolean>("__vueoCancelTimer") { tasks.cancelTimer(it); true }
   asyncFunction<String, Boolean>("__vueoTimerDelay") { request ->
    val id = Regex("\"id\":\"(.*?)\"").find(request)!!.groupValues[1]
    val millis = Regex("\"millis\":(\\d+)").find(request)!!.groupValues[1].toLong()
    tasks.run(id) { delay(millis); true } ?: false
   }
   asyncFunction<String, String>("__vueoNativeFetch") { request ->
    tasks.run {
     if (request.contains("slow")) {
      try { delay(10000) } finally { cancelled=true }
     } else delay(20)
     """{"status":200,"url":"https://example.org/master.m3u8","headers":{},"body":"#EXTM3U"}"""
    } ?: """{"error":"Provider execution completed"}"""
   }
   function<String, String>("__vueoCryptoOp") { CryptoCompatBridge.execute(it) }
   function<String, String>("__vueoBinaryToBase64") { BinaryCompatBridge.encodeBinary(it) }
   function<String, String>("__vueoBase64ToBinary") { BinaryCompatBridge.decodeBinary(it) }
   function<String, String>("__vueoBase64") { BinaryCompatBridge.encodeUtf8(it) }
   function<String, String>("__vueoBase64Decode") { BinaryCompatBridge.decodeUtf8(it) }
   evaluate<String>(template.replace("\${providerScript}",provider))
  } }
  check(result == expected) { "$name: $result" }
  if (expectCancel) check(cancelled)
  println("PASS $name (${(System.nanoTime()-started)/1000000}ms)")
 }


 test("empty and ASCII Base64", """
 module.exports.getStreams=function(){return [{url:btoa('')+':'+btoa('hello')+':'+atob('aGVsbG8=')}];};
 """, """[{"url":":aGVsbG8=:hello"}]""")
 test("NUL at start, middle and end", """
 module.exports.getStreams=function(){return [{url:btoa(String.fromCharCode(0,1,0,2,0))}];};
 """, """[{"url":"AAEAAgA="}]""")
 val allBytes = ByteArray(256) { it.toByte() }
 val expected = java.util.Base64.getEncoder().encodeToString(allBytes)
 test("all 256 byte values encode exactly", """
 module.exports.getStreams=function(){var b='';for(var i=0;i<256;i++)b+=String.fromCharCode(i);return [{url:btoa(b)}];};
 """, """[{"url":"$expected"}]""")
 test("all byte values decode and round trip", """
 module.exports.getStreams=function(){var b=atob('$expected');if(b.length!==256)throw Error('truncated');for(var i=0;i<256;i++)if(b.charCodeAt(i)!==i)throw Error('byte mismatch');return [{url:btoa(b)}];};
 """, """[{"url":"$expected"}]""")
 val text = "A\u0000é العربية"
 val textExpected = java.util.Base64.getEncoder().encodeToString(text.toByteArray())
 test("Buffer UTF8 Base64 preserves NUL and non-ASCII text", """
 module.exports.getStreams=function(){return [{url:Buffer.from('A\u0000é العربية').toString('base64')}];};
 """, """[{"url":"$textExpected"}]""")
 val digest = java.security.MessageDigest.getInstance("SHA-256").digest(allBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
 test("CryptoJS hashing retains all bytes", """
 module.exports.getStreams=function(){var C=require('crypto-js');return [{url:C.SHA256(C.enc.Base64.parse('$expected')).toString()}];};
 """, """[{"url":"$digest"}]""")
 val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
 val key=javax.crypto.spec.SecretKeySpec("0123456789abcdef".toByteArray(),"AES")
 val iv=javax.crypto.spec.IvParameterSpec("fedcba9876543210".toByteArray())
 var encrypted=byteArrayOf()
 var n=0
 while (!encrypted.contains(0)) {
   cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,key,iv)
   encrypted=cipher.doFinal(("""{"url":"https://cdn.example/master.m3u8?sig=a%2Fb","case":$n}""").toByteArray());n++
 }
 val hex=encrypted.joinToString("") { "%02x".format(it.toInt() and 255) }
 test("AES Hex/Base64 ciphertext containing NUL decrypts", """
 module.exports.getStreams=function(){var C=require('crypto-js');var v=C.enc.Base64.stringify(C.enc.Hex.parse('$hex'));var d=C.AES.decrypt(v,C.enc.Utf8.parse('0123456789abcdef'),{iv:C.enc.Utf8.parse('fedcba9876543210'),mode:C.mode.CBC,padding:C.pad.Pkcs7}).toString(C.enc.Utf8);return [{url:JSON.parse(d).url}];};
 """, """[{"url":"https://cdn.example/master.m3u8?sig=a%2Fb"}]""")
 test("AES CipherParams path preserves binary ciphertext", """
 module.exports.getStreams=function(){var C=require('crypto-js');var d=C.AES.decrypt(C.lib.CipherParams.create({ciphertext:C.enc.Hex.parse('$hex')}),C.enc.Utf8.parse('0123456789abcdef'),{iv:C.enc.Utf8.parse('fedcba9876543210')}).toString(C.enc.Utf8);return [{url:JSON.parse(d).url}];};
 """, """[{"url":"https://cdn.example/master.m3u8?sig=a%2Fb"}]""")
}
