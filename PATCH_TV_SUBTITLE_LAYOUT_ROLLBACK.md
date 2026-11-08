# Roll back the two TV subtitle layout patches

This rollback undoes TV Subtitle Collision v1 and TV Subtitle Placement v2. Apply at VUEO repository root, then rebuild/reinstall TV.

- TvSubtitlePlayerView.kt is restored byte-for-byte from the supplied VUEO-NEXT-main (63).zip.
- Subtitle stacking/policy functions and their comment in TvPlayerTracks.kt are restored from that same ZIP.
- Existing subtitle timing instrumentation elsewhere in TvPlayerTracks.kt is retained.
- AES/cache optimization, linear normalizer, timing diagnostics, Mobile and provider code are not replaced.
- The previously added TvSubtitleCollisionLayout.kt may remain in the repository: the restored view does not reference it. No deletion is needed for the rollback.

This restores the pre-layout-patch behavior. It does not resolve the original overlapping-cue scene; that remains open. Do not use the previous geometry/placement fixture tests as proof of on-device correctness. The reported reversed information lines and bottom-edge placement show those policy tests did not cover the actual source cue behavior.

Verification: original view byte equality and original stacking section equality checked against the uploaded ZIP; timing marker retention checked. Full Android build/device verification has not been performed for this rollback. A standalone compile attempt could not run because the transient compiler dependencies from the previous task were no longer present.

Before any further layout changes, obtain the actual incoming Cue placement/anchor/line-type metadata from the same scene on TV and Mobile, along with the active font/padding settings. Preserve source cue order and distinguish native numbered-line placement from fractional placement; do not infer dialogue/info identity merely from source list order or whether line is unset.
