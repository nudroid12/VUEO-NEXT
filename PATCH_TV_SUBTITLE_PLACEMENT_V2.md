# TV subtitle placement correction v2

Apply contents at VUEO repo root and rebuild/reinstall TV. This is cumulative for the prior TV Subtitle Collision patch: it includes the collision view/helper plus the corrected stacking policy in TvPlayerTracks.kt. Based on VUEO-NEXT-main (63) plus the previously delivered timing markers. Mobile files are not included.

Why v1 was insufficient:
The original split policy picked remainingNormal.last() as the bottom cue. The stacking helper also erased authored positions for cues in the lower 30% (or negative line numbers -3..-1). That could pin an info cue below dialogue merely because of list order, even after collision spacing worked.

Correction:
- Remove ordinary-cue lower/upper priority based on list order.
- Keep unpositioned ordinary text in one bottom block.
- Preserve source-authored vertical position/anchor, horizontal fields and size for ordinary cues, including source positions in the lower 30% and negative numbered lines.
- Keep full-parentheses commentary classification, visibility toggle and commentary size. The commentary layer explicitly uses its existing lower-position normalization policy.
- Keep the previous collision guard: source-positioned cues can move when colliding with the fixed default-position block.

No semantic text classifier or provider hardcode. If the source supplies no vertical placement, the player cannot establish dialogue/info roles reliably; those unpositioned cues are stacked in source order in one block without assuming the last cue is dialogue. Authored cues at identical placements retain existing grouping. The exact original SNAFU episode cue file could not be retrieved; fixtures test the source-placement mechanism, not the real episode.

No changes to Mobile, HiAnime, SmartSubs generation, subtitle text/timing, video, network, timeouts, AES or normalizer.

Validation passed:
- Source-policy fixture tests for both cue orders, authored fractional/numbered positions, anchors/size, ordinary stacking, commentary on/off/size/partial parentheses.
- Existing collision geometry cases including 5,000 generated cases.
- Full modified view/helper Kotlin compilation against Android and Media3 APIs with app-service compile stubs.
No full Android APK build or on-device 11:44 render verified.

Install and repeat the scene with original English and Malay AI. The expected behavior, when source cues have distinct authored/default positions, is dialogue at the ordinary subtitle position with info preserving its source placement or moved clear of dialogue. If the source has no position hints, no dialogue/info guarantee is made.
