"""Source guards plus generation of a JVM regression harness from production code.
Run: python3 validation/native-position/check.py [repository-root]
If kotlinc/java are installed, the generated harness is compiled and executed.
These Cue stubs test layout policy, not Android drawing or Media3 parsing.
"""
from pathlib import Path
import re, shutil, subprocess, sys, tempfile
root=Path(sys.argv[1] if len(sys.argv)>1 else '.').resolve()
player=root/'tv/src/main/java/com/vueo/tv/ui/player'
loader=(player/'TvSubtitleTimelineLoader.kt').read_text()
assert 'if (request.nativeTrack) hasNativePositionSettings(timing)' in loader
assert 'else TvSubtitleTemporalPolicy.hasVttPositionSettings(timing)' in loader
assert 'nativeTrack = smart == null' in loader
assert 'configurations.map { it.id }, configurations.map { it.label }' in loader
assert 'format.language' not in loader
print('PASS: source guards for native-only protection and identity matching')
with tempfile.TemporaryDirectory() as tmp:
 out=Path(tmp)
 policies=loader[loader.index('    internal fun configurationIndex'):loader.index('    private var lastNativeDiagnostic')]
 (out/'Identity.kt').write_text('package com.vueo.tv.player\nobject NativeIdentity {\n'+policies+'\n}')
 shutil.copy(player/'TvSubtitleTemporalPolicy.kt',out/'Temporal.kt')
 for name in ['Cue.kt','Text.kt']:
  shutil.copy(root/'validation/tv-subtitle-placement/stubs'/name,out/name)
 tracks=(player/'TvPlayerTracks.kt').read_text()
 stack=tracks[tracks.index('internal fun tvStackCollidingSubtitleCues('):tracks.index('private class TvSubtitleOffsetRenderer')]
 (out/'Stack.kt').write_text('package com.vueo.tv.player\nimport android.text.*\nimport androidx.media3.common.text.Cue\n'+stack)
 automatic=loader[loader.index('    fun isAutomaticText'):loader.rindex('}')]
 (out/'Automatic.kt').write_text('package com.vueo.tv.player\nimport androidx.media3.common.text.Cue\nobject TvSubtitleTimelineLoader {\n'+automatic+'\n}')
 view=(player/'TvSubtitlePlayerView.kt').read_text()
 layers=view[view.index('    private fun splitLowerSubtitleLayers('):view.index('    private fun applyUpperLayerTextSize(')]
 model=view[view.index('    private data class TvSubtitleLayerSplit('):view.index('    private companion object {')]
 layers=layers.replace('private fun splitLowerSubtitleLayers','fun splitLowerSubtitleLayers')
 model=model.replace('private data class','data class')
 (out/'Layers.kt').write_text('package com.vueo.tv.player\nimport androidx.media3.common.text.Cue\nobject Layers {\nvar protectedTimelineText=emptySet<String>()\nvar layoutReady=true\nvar layoutAnalysisFailed=false\n'+layers+model+'\n}')
 shutil.copy(Path(__file__).with_name('Regression.kt'),out/'Regression.kt')
 if not shutil.which('kotlinc'):
  print('NOT RUN: Kotlin/JVM regression; kotlinc is unavailable. No renderer/runtime result is claimed.')
  sys.exit(2)
 subprocess.run(['kotlinc',*[str(p) for p in out.glob('*.kt')],'-include-runtime','-d',str(out/'checks.jar')],check=True)
 subprocess.run(['java','-jar',str(out/'checks.jar')],check=True)
