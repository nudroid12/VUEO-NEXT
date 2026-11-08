# TV v1 layout baseline + bounded cue mapping diagnostics

Purpose: resolve the reported clue that info moves with the control panel while spoken dialogue stays fixed. Do not guess a global role swap without actual incoming cue placement.

Apply the ZIP at the VUEO repository root, then rebuild/reinstall TV. The patch is self-contained for the TV layout files:
- Restore TvSubtitlePlayerView.kt and TvSubtitleCollisionLayout.kt from the original v1 Collision patch, with additive cue diagnostics.
- Restore original stacking in TvPlayerTracks.kt, retaining earlier timing markers outside that policy. This undoes v2 source-position changes even if the full rollback was already applied.
- Add TvSubtitleCueDiagnostics.kt.

The original v1 no-overlap behavior is restored, including its known limitation that ordinary cue priority uses source list order. This patch does NOT yet fix semantic dialogue/info priority. AES/cache, normalizer, Mobile, providers, video and SmartSubs activation remain untouched.

Diagnostics:
- Only when Provider or Performance diagnostics is collecting.
- At most 24 multi-cue render groups per content binding, up to eight cues per stage.
- input/main/upper stages record text SHA256 fingerprint, text character/line counts, source line value/type/anchor, horizontal position/anchor, size, viewport, chosen font size, layer padding and overlay ownership.
- Raw URLs are not included by the helper; SubtitleFlowDiagnostics identifies the source by its existing hash. Caption text and key/IV are not logged.
- Layout field dumps are additive and do not mutate Cue instances.

Validation: Kotlin/JVM compile/test of the new helper with lightweight API fixtures; disabled logging, eight-cue bound, negative line metadata, privacy and cue immutability passed. Baseline restoration and v2 absence were checked statically. Full Android build/UI rendering not verified for this patch.

Device capture:
1. Enable Provider and Performance diagnostics.
2. Reopen SNAFU S1E7 and seek directly to about 11:40 so the 24-group budget is not spent earlier.
3. Use English first. At 11:44 pause while the info/dialogue are visible, open and hide the controls once.
4. Export both diagnostic ZIPs. Malay AI can be a second capture after the English evidence.

This gives the actual cue-to-layer mapping needed for a narrow correction rather than another position heuristic. The original role-order problem remains open.
