package com.vueo.shared.core.player
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.source.SourceDiscoveryControl
import kotlinx.coroutines.*

fun main()=runBlocking {
 var count=0
 fun verify(name:String,ok:Boolean){check(ok){name};count++;println("PASS $name")}
 fun track(url:String,overlay:Boolean=false)=SubtitleTrack(url,"en",url,"fixture","Fixture",liveOverlay=overlay)
 val early=track("https://subs.example/early.vtt")
 val late=track("https://subs.example/late.vtt")
 val window=StartupSubtitleDiscovery()
 verify("initial payload track retained",window.snapshot(listOf(early))==listOf(early))
 val before=window.snapshot(listOf(early,late))
 verify("new startup track uses overlay",before.first{it.url==late.url}.liveOverlay)
 window.close()
 verify("closed exactly at frame boundary",!window.isOpen)
 verify("tracks found before frame retained",window.snapshot(emptyList())==before)
 verify("tracks arriving after frame rejected",window.snapshot(listOf(track("https://subs.example/too-late.vtt")))==before)
 var calls=0
 verify("no request starts after first frame",window.discover{calls++;"ignored"}==null && calls==0)
 val empty=StartupSubtitleDiscovery();empty.snapshot(emptyList());empty.close()
 verify("fast video can freeze an empty subtitle list",empty.snapshot(listOf(late)).isEmpty())
 val next=StartupSubtitleDiscovery()
 verify("new selected source opens a fresh window",next.isOpen && next.snapshot(listOf(late)).size==1)
 val overlay=StartupSubtitleDiscovery();overlay.snapshot(emptyList());overlay.snapshot(listOf(late.copy(liveOverlay=true)))
 verify("regular duplicate cannot undo overlay",overlay.snapshot(listOf(late)).single().liveOverlay)
 val started=CompletableDeferred<Unit>();var cancelled=false
 val pending=launch{next.discover{started.complete(Unit);try{awaitCancellation()}finally{cancelled=true}}}
 started.await();next.close();pending.join()
 verify("first frame cancels in-flight discovery",cancelled && pending.isCancelled)
 val staleWindow=StartupSubtitleDiscovery();val staleStart=CompletableDeferred<Unit>();val staleGate=CompletableDeferred<Unit>();var published=false
 val stale=launch{staleWindow.discover{staleStart.complete(Unit);withContext(NonCancellable){staleGate.await()};listOf(late)}?.let{published=true}}
 staleStart.await();staleWindow.close();staleGate.complete(Unit);stale.join()
 verify("non-cooperative late return cannot publish",!published)
 val disposal=StartupSubtitleDiscovery();val entered=CompletableDeferred<Unit>()
 val disposing=launch{disposal.discover{entered.complete(Unit);awaitCancellation()}}
 entered.await();disposal.close();disposing.join()
 verify("source disposal cancels remaining discovery",disposing.isCancelled)
 val control=SourceDiscoveryControl();val addon=Job();val plugin=Job();val subtitles=Job()
 control.attachAddons(addon);control.attachPlugins(plugin);control.attachSubtitles(subtitles);control.stopSubtitles()
 verify("engine subtitle branch stops",control.subtitlesStopped && subtitles.isCancelled)
 verify("video branches are independent",addon.isActive && plugin.isActive)
 val registeredLate=Job();control.attachSubtitles(registeredLate)
 verify("stop-before-attach race cancels new subtitle job",registeredLate.isCancelled)
 val videoControl=SourceDiscoveryControl();val subtitleJob=Job();videoControl.attachSubtitles(subtitleJob);videoControl.stopSources()
 verify("video stop does not implicitly stop subtitle branch",subtitleJob.isActive)
 // Exact await guard used by SourceDiscoveryEngine: cancelled subtitle child does not kill video sibling.
 val engineScopeSurvived=coroutineScope {
  val c=SourceDiscoveryControl();val child=async(start=CoroutineStart.LAZY){awaitCancellation()}
  c.attachSubtitles(child);c.stopSubtitles()
  try{child.await()}catch(e:CancellationException){currentCoroutineContext().ensureActive();if(!c.subtitlesStopped)throw e}
  currentCoroutineContext().isActive
 }
 verify("stopped subtitle await keeps discovery parent alive",engineScopeSurvived)
 val marker=track("https://api.example/index").copy(mimeType=DeferredPluginSubtitles.INDEX_MIME)
 verify("internal descriptors stay out of subtitle menu",StartupSubtitleDiscovery().snapshot(listOf(marker)).isEmpty())
 addon.cancel();plugin.cancel();subtitleJob.cancel()
 println("$count startup subtitle checks passed")
}
