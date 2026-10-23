package com.vueo.shared.core.player
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.parsePluginSubtitleRequests
import kotlinx.coroutines.*
import org.json.JSONArray

fun main() = runBlocking {
 val identity=com.vueo.shared.core.media.MediaItem(id="tt123",type="movie",name="Identity",tmdbId="12345")
 check(identity.copy(name="Copy").tmdbId=="12345")
 check(identity.id=="tt123")
 println("PASS restored TMDB constructor/property/copy alongside addon identity")
 var count=0
 fun verify(name:String, test:Boolean){check(test){name};count++;println("PASS $name")}
 val descriptors=parsePluginSubtitleRequests(JSONArray("""[{"url":"https://api.example/subtitles/movie/12345","headers":{"Authorization":"private-index-token"}}]"""),"plugin:test","Fixture")
 verify("descriptor parsed as an index",descriptors.size==1 && DeferredPluginSubtitles.isIndex(descriptors[0]))
 verify("non-HTTPS descriptor rejected",parsePluginSubtitleRequests(JSONArray("""[{"url":"http://api.example/list"}]"""),"p","P").isEmpty())
 val normal=SubtitleTrack("existing","en","https://subs.example/existing.vtt","p","P")
 val source=StreamSource(name="Fixture",url="https://media.example/master.m3u8",providerId="p",providerName="P",subtitles=descriptors+normal)
 verify("manifest hidden from episode subtitles",StreamSubtitlePolicy.forEpisode(listOf(source),emptyList())==listOf(normal))
 verify("manifest hidden from selected playback subtitles",StreamSubtitlePolicy.forPlayback(source,emptyList())==listOf(normal))
 var calls=0
 val gate=CompletableDeferred<String>()
 val job=async {DeferredPluginSubtitles.load(descriptors){calls++;gate.await()}}
 yield();delay(20)
 verify("video source usable while subtitle request is pending",!job.isCompleted && source.isDirectPlayable)
 gate.complete("""[{"label":"Malay","file":"https://subs.example/ms.vtt"},{"label":"English","file":"https://subs.example/en.srt","type":"srt"},{"label":"bad","file":"data:text/vtt,invalid"}]""")
 val tracks=job.await()
 verify("late list loaded with original provider",tracks.size==2 && tracks.all{it.providerName=="Fixture"})
 verify("language aliases resolved",tracks.map{it.language}==listOf("ms","en"))
 verify("index credentials not copied to subtitle CDN",tracks.all{it.headers["Authorization"]==null})
 verify("all late tracks use live overlay",tracks.all{it.liveOverlay && LiveSmartSubs.isOverlay(it)})
 verify("late tracks excluded from video MediaItem configuration",LiveSmartSubs.videoSubtitles(listOf(normal)+tracks)==listOf(normal))
 verify("video URL remains identical",source.url=="https://media.example/master.m3u8")
 val repeated=tracks.map{it.copy(liveOverlay=false)}
 val merged=StreamSubtitlePolicy.forPlayback(source,repeated+tracks)
 verify("regular duplicate cannot replace overlay track",merged.filter{it.url in tracks.map{it.url}}.all{it.liveOverlay})
 verify("duplicate merge never attaches late tracks to video",LiveSmartSubs.videoSubtitles(merged)==listOf(normal))
 verify("overlay retained when duplicate order reverses",StreamSubtitlePolicy.forPlayback(source,tracks+repeated)==merged)

 verify("duplicate endpoint fetched once",DeferredPluginSubtitles.load(descriptors+descriptors){calls++;"[]"}.isEmpty() && calls==2)
 verify("failed list has no playback failure",DeferredPluginSubtitles.load(descriptors){error("HTTP 503")}.isEmpty())
 val started=CompletableDeferred<Unit>();var cancelled=false
 val pending=launch {DeferredPluginSubtitles.load(descriptors){started.complete(Unit);try{awaitCancellation()}finally{cancelled=true}}}
 started.await();pending.cancelAndJoin();verify("player/source disposal cancels pending subtitle work",cancelled)
 val many=JSONArray();repeat(66){many.put(org.json.JSONObject().put("language","en").put("url","https://subs.example/$it.vtt"))}
 verify("all 66 indexed tracks retained",DeferredPluginSubtitles.load(descriptors){many.toString()}.size==66)
 verify("empty descriptors make zero requests",DeferredPluginSubtitles.load(emptyList()){error("must not fetch")}.isEmpty())
 val staleStarted=CompletableDeferred<Unit>();val staleGate=CompletableDeferred<String>()
 val stale=async {DeferredPluginSubtitles.load(descriptors){staleStarted.complete(Unit);withContext(NonCancellable){staleGate.await()}}}
 staleStarted.await();stale.cancel();staleGate.complete("[]");try{stale.await()}catch(_:CancellationException){}
 verify("late result from cancelled source cannot be published",stale.isCancelled)
 println("$count deferred subtitle checks passed")
}
