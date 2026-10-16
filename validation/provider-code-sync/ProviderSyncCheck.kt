package com.vueo.shared.core.plugin
import android.content.Context
import java.nio.file.Files
import kotlinx.coroutines.*

fun main()= runBlocking {
 var checks=0
 fun verify(value:Boolean){check(value);checks++}
 val directory=Files.createTempDirectory("vueo-provider-sync-check").toFile()
 try {
  val context=Context(directory)
  val active=PluginProviderDescriptor("active")
  val disabled=PluginProviderDescriptor("disabled")
  val defaultOff=PluginProviderDescriptor("defaultOff",defaultEnabled=false)
  val repo=PluginRepositoryDescriptor("https://repo",listOf(active,disabled,defaultOff))
  val repoOff=PluginRepositoryDescriptor("https://off",listOf(PluginProviderDescriptor("off")))
  PluginStore.reset();PluginHttp.reset();PluginRuntimeCache.clears.set(0)
  PluginStore.repos.addAll(listOf(repo,repoOff));PluginStore.disabledRepos += repoOff.manifestUrl;PluginStore.disabledProviders += disabled.id
  val manager=ProviderCodeSyncManager(context)
  val code=ProviderCodeStore(context)
  manager.syncMissing(listOf(repo,repoOff))
  verify(PluginHttp.calls.get()==1)
  verify(code.isReady(repo,active))
  verify(!code.isReady(repo,disabled))
  verify(!code.isReady(repo,defaultOff))
  verify(!code.isReady(repoOff,repoOff.providers.single()))
  verify(PluginRuntimeCache.clears.get()==1)
  manager.syncMissing(listOf(repo,repoOff))
  verify(PluginHttp.calls.get()==1)
  verify(PluginRuntimeCache.clears.get()==1)
  PluginStore.master=false
  PluginStore.disabledProviders.clear()
  manager.syncMissing(listOf(repo))
  verify(!code.isReady(repo,disabled))
  verify(PluginHttp.calls.get()==1)
  // First-use preflight calls explicit syncProviders, so an enabled missing
  // provider can be prepared without warming all disabled code.
  PluginStore.master=true
  manager.syncProviders(repo,listOf(disabled),false)
  verify(code.isReady(repo,disabled))
  verify(PluginHttp.calls.get()==2)
  verify(PluginRuntimeCache.clears.get()==2)
  // Forced refresh with identical text must preserve discovery cache.
  manager.syncProviders(repo,listOf(active),true)
  verify(PluginHttp.calls.get()==3)
  verify(PluginRuntimeCache.clears.get()==2)
  PluginHttp.sources[PluginRepositoryClient.providerScriptUrl(repo,active)]="// revised source"
  manager.syncProviders(repo,listOf(active),true)
  verify(code.read(repo,active)=="// revised source")
  verify(PluginRuntimeCache.clears.get()==3)
  PluginHttp.fail=true
  val failed=manager.syncProviders(repo,listOf(active),true)
  verify(failed.failedProviders==1)
  verify(code.read(repo,active)=="// revised source")
  verify(PluginRuntimeCache.clears.get()==3)
  PluginHttp.fail=false
  val empty=manager.syncProviders(repo,emptyList(),false)
  verify(empty.readyProviders==0 && empty.failedProviders==0)
  verify(PluginRuntimeCache.clears.get()==3)

  val many=PluginRepositoryDescriptor("https://many",(1..9).map{PluginProviderDescriptor("p$it")})
  PluginStore.repos += many
  PluginHttp.reset()
  coroutineScope {listOf(async{manager.syncMissing(listOf(many))},async{ProviderCodeSyncManager(context).syncMissing(listOf(many))}).awaitAll()}
  verify(PluginHttp.calls.get()==9)
  verify(PluginHttp.peak.get() in 1..3)
  verify(code.readyCount(many)==9)

  // Publish one changed script while a second task waits, then cancel. Cache
  // invalidation must already have happened for the successful publication.
  val partial=PluginRepositoryDescriptor("https://partial",listOf(PluginProviderDescriptor("good"),PluginProviderDescriptor("wait")))
  PluginStore.repos += partial
  PluginHttp.reset();PluginHttp.waiting=CompletableDeferred()
  val before=PluginRuntimeCache.clears.get()
  val job=launch{manager.syncRepository(partial,false)}
  PluginHttp.waiting!!.await()
  withTimeout(2000){while(!code.isReady(partial,partial.providers.first()))delay(5)}
  verify(PluginRuntimeCache.clears.get()==before+1)
  job.cancelAndJoin()
  verify(code.isReady(partial,partial.providers.first()))
  verify(!code.isReady(partial,partial.providers.last()))
  println("Provider sync: $checks behavioral checks passed")
 }finally{directory.deleteRecursively()}
}
