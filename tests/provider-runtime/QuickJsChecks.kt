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
   evaluate<String>(template.replace("\${providerScript}",provider))
  } }
  check(result == expected) { "$name: $result" }
  if (expectCancel) check(cancelled)
  println("PASS $name (${(System.nanoTime()-started)/1000000}ms)")
 }
 test("race keeps winner and cancels native loser", """
 module.exports.getStreams = async function () {
   var slow = fetch('https://example.org/slow');
   await fetch('https://example.org/fast');
   return [{url:'winner'}];
 };
 """, """[{"url":"winner"}]""", true)
 test("sequential provider keeps all awaited work", """
 module.exports.getStreams = async function () {
   await fetch('https://example.org/one'); await fetch('https://example.org/two');
   return [{url:'sequential'}];
 };
 """, """[{"url":"sequential"}]""")
 test("Promise.all provider completes normally", """
 module.exports.getStreams = async function () {
   await Promise.all([fetch('https://example.org/one'),fetch('https://example.org/two')]);
   return [{url:'all'}];
 };
 """, """[{"url":"all"}]""")
 test("clearTimeout cancels native delay", """
 module.exports.getStreams = async function () {
   var t=setTimeout(function(){throw Error('must not fire');},10000);clearTimeout(t);
   await new Promise(function(r){setTimeout(r,20);}); return [{url:'timer'}];
 };
 """, """[{"url":"timer"}]""")
 test("unneeded timer cancelled at completion", """
 module.exports.getStreams = function () {
   setTimeout(function(){throw Error('late callback');},10000);return [];
 };
 """, "[]")
 test("normal timer arguments preserved", """
 module.exports.getStreams = async function () {
   return await new Promise(function(r){setTimeout(function(a,b){r([{url:a+b}]);},20,'a','b');});
 };
 """, """[{"url":"ab"}]""")
}
