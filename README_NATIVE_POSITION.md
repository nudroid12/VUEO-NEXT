# VUEO TV native subtitle track/position fix

Target: confirmed baseline 32fdffa, VUEO-NEXT-main (64).zip, optionally with the previous English-VTT-Position-Fix applied. Replaces ONE production file: TvSubtitleTimelineLoader.kt. Do not combine with the discarded Correction v2 / isolation / English-SmartSubs patches.

## Changes
- Resolve native external subtitle configuration by exact ID, by a unique VUEO machine label, or by an ID with numeric Media3 source prefixes removed. No language-only or arbitrary suffix matching. Reject ambiguous matches.
- Native VTT only: a bare negative integer line (e.g. line:-1 through line:-4) no longer blocks the existing layout path. Explicit line anchors, non-negative/percentage lines, position, size, vertical writing and region settings remain protected.
- Record TV_NATIVE_LAYOUT_REQUEST matched=true/false and cached=true/false, without readable subtitle text or source URLs, to diagnose whether the native path actually runs.
- SmartSubs requests retain the original baseline position-protection rule. TV view, split/stack, temporal analysis, AI selection/translation and Bottom Position controls are byte-identical to baseline. No English language restriction: applies to ordinary external subtitle tracks.

## Why the previous patch was insufficient
It changed protection after parsing but left exact-ID-only track lookup in place. If lookup fails, parsing is never reached and the fallback renderer keeps authored line coordinates. Those cues do not use the default SubtitleView bottom-padding position. This patch addresses lookup and classification together. The supplied log supports this explanation but does not record raw native Cue coordinates; on-device confirmation is required.

## Apply
Extract into the repository root in Codespaces and replace the one Kotlin file. Review CHANGES.diff before committing. Then use the normal TV build workflow.

## Verification performed here
- Source guards pass for native-only protection and identity matching.
- Byte comparison confirms only TimelineLoader.kt differs from the confirmed baseline's production sources.
- ZIP integrity checked.
- Kotlin/JVM regression tests were prepared using extracted production identity, settings, split and stack functions. NOT EXECUTED: no kotlinc in this environment, and dependency download timed out.
- No full Android build or TV render test was run. No claim of completed on-device repair is made.

## Codespaces checks
With Kotlin CLI installed:
  python3 validation/native-position/check.py .
Then build the TV app with the repository's normal workflow. The JVM harness uses Cue stubs; it does not execute Android drawing or the Media3 parser. Actual Bottom Position behavior still needs a TV check.

On TV, use the same native English and ordinary Malay tracks and adjust Bottom Position during a single visible cue. Check that dialog moves immediately. Then compare the info/dialogue scene with AI and verify AI remains unchanged. New logs should show TV_NATIVE_LAYOUT_REQUEST matched=true cached=true and TV_SUBTITLE_TIMELINE parsed=true for the ordinary track. If matched=false persists, this patch has not reached layout; do not infer success from the build alone.
