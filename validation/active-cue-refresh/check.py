"""Check production-source invariants; run extracted Kotlin resolver if kotlinc exists.
Usage: python3 validation/active-cue-refresh/check.py .
Does not simulate Android rendering or prove elapsed display latency.
"""
from pathlib import Path
import subprocess,shutil,tempfile,sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
s=(root/'tv/src/main/java/com/vueo/tv/ui/player/TvSubtitlePlayerView.kt').read_text()
start=s.index('    private fun currentPresentationCues()')
end=s.index('    private fun refreshRenderedCues()',start)
method=s[start:end]
refresh=s[end:s.index('    private fun displayCues(',end)]
pending=s[s.index('if (layoutRequestKey != null && !layoutReady)'):s.index('if (lastInputCues == cues')]
assert 'lastInputCues = cues.toList()' in pending
assert 'lastInputCues = null' not in pending
assert 'val currentCues = currentPresentationCues()' in refresh
assert refresh.index('lastInputCues = null') < refresh.index('displayCues(currentCues)')
assert 'displayCues(currentPresentationCues())' in s
assert 'displayCues(boundPlayer?.currentCues' not in s
assert method.index('LiveSmartSubs.ownsCues') < method.index('lastInputCues ?:')
print('PASS: pending retention, refresh cache invalidation, active-owner and commentary source guards')
code='''
data class Cue(val text: String)
data class Group(val cues: List<Cue>)
class Player(var currentCues: Group, var ai: Boolean=false, var smart: List<Cue> = emptyList())
object LiveSmartSubs {
 fun ownsCues(player: Player) = player.ai
 fun cues(player: Player) = if (player.ai) player.smart else player.currentCues.cues
}
class Resolver {
 var boundPlayer: Player? = null
 var lastInputCues: List<Cue>? = null
 METHOD
 fun read() = currentPresentationCues()
}
fun main() {
 val resolver=Resolver()
 check(resolver.read().isEmpty())
 val native=listOf(Cue("native"))
 val cached=listOf(Cue("cached native"))
 val smart=listOf(Cue("active AI"))
 val player=Player(Group(native))
 resolver.boundPlayer=player
 check(resolver.read()==native)
 resolver.lastInputCues=cached
 check(resolver.read()==cached)
 player.ai=true; player.smart=smart
 check(resolver.read()==smart) // Ignores stale native cache.
 player.smart=emptyList()
 check(resolver.read().isEmpty()) // Never falls back to native during an AI gap.
 player.smart=listOf(Cue("AI at new clock"))
 check(resolver.read()==player.smart)
 player.ai=false
 check(resolver.read()==cached)
 resolver.lastInputCues=null
 check(resolver.read()==native)
 println("PASS: extracted production resolver checks; not an Android render test")
}
'''.replace('METHOD',method)
if not shutil.which('kotlinc'):
 print('NOT RUN: Kotlin execution; compiler unavailable. Android build/TV test also not run.')
 sys.exit(2)
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Check.kt').write_text(code)
 subprocess.run(['kotlinc',str(p/'Check.kt'),'-include-runtime','-d',str(p/'check.jar')],check=True)
 subprocess.run(['java','-jar',str(p/'check.jar')],check=True)
