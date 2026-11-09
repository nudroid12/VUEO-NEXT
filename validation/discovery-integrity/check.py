"""Source invariants plus JVM tests extracted from production Kotlin (requires kotlinc)."""
from pathlib import Path
import sys,shutil,tempfile,subprocess
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
r=(root/'shared/core/src/main/java/com/vueo/shared/core/plugin/PluginRuntime.kt').read_text()
s=(root/'shared/core/src/main/java/com/vueo/shared/core/source/SourceDiscoveryEngine.kt').read_text()
assert r.count('eligibleDiscoveryTargets(mediaType, animeContent, providerNameFilter)')==2
assert 'return pluginEngine.discoveryProviderKeys(' in s
assert 'configuredPluginProviders.forEach(::markProviderComplete)' not in s
assert 'executionEvents.trySend(provider to running)' in s
assert 'onProviderExecution: (String, Boolean) -> Unit' in r
assert 'notifyExecution(executionKey, true)' in r and 'notifyExecution(executionKey, false)' in r
assert 'finally {' in r[r.index('notifyExecution(executionKey, true)'):r.index('notifyExecution(executionKey, false)')]
assert 'onProgress(snapshot)' not in r
assert 'discoveryContext.ensureActive()' in s
assert 'result?.diagnostics?.forEach' in s
assert s.count('cancelledProviders = synchronized(loadingProviders)')==3
assert s.count('skippedProviders = synchronized(loadingProviders)')==3
assert 'if (completedWithinBudget && cacheableResult)' in r
assert 'if (lowMemoryDevice) 3 else 4' in r
assert '135_000L' in r and '120_000L' in r
print('PASS: shared targets, observer isolation, terminal snapshots, cancellation cleanup, cache/budget/concurrency source guards')
def block(text,needle):
 start=text.index(needle);b=text.index('{',start);depth=1;i=b+1
 while depth:
  if text[i]=='{':depth+=1
  if text[i]=='}':depth-=1
  i+=1
 return text[start:i]
methods='\n'.join(block(s,'        fun '+name) for name in ['markProviderLoading(','markProviderComplete(','finishUnattempted('])
source='''
class Lifecycle {
 val loadingProviders=linkedSetOf<String>()
 val runningProviders=linkedSetOf<String>()
 val completedSourceProviders=linkedSetOf<String>()
 val cancelledProviders=linkedSetOf<String>()
 val skippedProviders=linkedSetOf<String>()
 METHODS
}
fun main() {
 val x=Lifecycle()
 x.markProviderLoading("A");x.markProviderLoading("B");x.markProviderLoading("C")
 x.markProviderComplete("A")
 x.finishUnattempted(listOf("A","B"),false)
 check(x.completedSourceProviders==setOf("A"))
 check(x.cancelledProviders==setOf("B"))
 x.finishUnattempted(listOf("C"),true)
 check(x.skippedProviders==setOf("C"))
 check(x.loadingProviders.isEmpty())
 x.markProviderLoading("B")
 check("B" !in x.cancelledProviders && "B" in x.loadingProviders)
 x.markProviderComplete("B")
 check("B" !in x.cancelledProviders && "B" in x.completedSourceProviders)
 x.finishUnattempted(listOf("A","B"),true)
 check(x.completedSourceProviders==setOf("A","B"))
 check(x.skippedProviders==setOf("C"))
 println("PASS: extracted production lifecycle transitions; not an Android discovery test")
}
'''.replace('METHODS',methods)
if not shutil.which('kotlinc'):
 print('NOT RUN: Kotlin transition tests and Android build; Kotlin compiler unavailable.')
 sys.exit(2)
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Check.kt').write_text(source)
 subprocess.run(['kotlinc',str(p/'Check.kt'),'-include-runtime','-d',str(p/'check.jar')],check=True)
 subprocess.run(['java','-jar',str(p/'check.jar')],check=True)
