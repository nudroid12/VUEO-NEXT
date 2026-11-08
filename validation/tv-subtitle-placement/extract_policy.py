"""Extract actual production functions for standalone JVM policy tests (no Android runtime)."""
from pathlib import Path
import sys
root=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
s=(root/'tv/src/main/java/com/vueo/tv/ui/player/TvPlayerTracks.kt').read_text()
stack=s[s.index('internal fun tvStackCollidingSubtitleCues('):s.index('private class TvSubtitleOffsetRenderer')]
(out/'Stack.kt').write_text('package com.vueo.tv.player\nimport android.text.*\nimport androidx.media3.common.text.Cue\n'+stack)
v=(root/'tv/src/main/java/com/vueo/tv/ui/player/TvSubtitlePlayerView.kt').read_text()
layers=v[v.index('    private fun splitLowerSubtitleLayers('):v.index('    private fun applyUpperLayerTextSize(')]
layers=layers.replace('private fun splitLowerSubtitleLayers','fun splitLowerSubtitleLayers')
model=v[v.index('    private data class TvSubtitleLayerSplit('):v.index('    private companion object {')]
model=model.replace('private data class','data class')
(out/'Layers.kt').write_text('package com.vueo.tv.player\nimport androidx.media3.common.text.Cue\ninternal object ExtractedLayers {\n'+layers+model+'\n}')
