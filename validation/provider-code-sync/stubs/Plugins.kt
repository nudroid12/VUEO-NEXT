package com.vueo.shared.core.plugin
import android.content.Context
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class PluginProviderDescriptor(val id:String,val name:String=id,val version:String="1",val filename:String="$id.js",val defaultEnabled:Boolean=true)
data class PluginRepositoryDescriptor(val manifestUrl:String,val providers:List<PluginProviderDescriptor>)
class PluginStore(context:Context) {
    companion object {
        var master = true
        val repos = mutableListOf<PluginRepositoryDescriptor>()
        val disabledRepos = mutableSetOf<String>()
        val disabledProviders = mutableSetOf<String>()
        fun reset(){ master=true;repos.clear();disabledRepos.clear();disabledProviders.clear() }
    }
    fun pluginsEnabled()=master
    fun repositories()=repos.toList()
    fun isRepositoryEnabled(repository:PluginRepositoryDescriptor)=repository.manifestUrl !in disabledRepos
    fun isProviderEnabled(repository:PluginRepositoryDescriptor, provider:PluginProviderDescriptor)=provider.defaultEnabled && provider.id !in disabledProviders
}
object PluginRepositoryClient {
 fun providerScriptUrl(repository:PluginRepositoryDescriptor,provider:PluginProviderDescriptor)="${repository.manifestUrl}/${provider.filename}"
}
object PluginHttp {
 const val MAX_PROVIDER_SCRIPT_BYTES = 8L*1024*1024
 val calls = AtomicInteger()
 val active = AtomicInteger()
 val peak = AtomicInteger()
 val sources = ConcurrentHashMap<String,String>()
 var waiting:CompletableDeferred<Unit>?=null
 var fail=false
 suspend fun getText(url:String):String {
  calls.incrementAndGet()
  val count=active.incrementAndGet();peak.updateAndGet{maxOf(it,count)}
  try {
   if(url.endsWith("wait.js")){waiting?.complete(Unit);awaitCancellation()}
   delay(20)
   if(fail) error("offline")
   return sources[url] ?: "// $url"
  }finally{active.decrementAndGet()}
 }
 fun reset(){calls.set(0);active.set(0);peak.set(0);sources.clear();waiting=null;fail=false}
}
object PluginRuntimeCache { val clears=AtomicInteger();fun clear(){clears.incrementAndGet()} }
