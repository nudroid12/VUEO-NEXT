# SmartSubs live text overlay — Phase 18, Mobile + TV

Base: VUEO-NEXT-main (55) with Phase 17. This ZIP includes the current versions of all four Phase 17 Kotlin files, so it supersedes that patch. Overlay at repository root. No YAML or file deletions. No provider changes.

## Evidence and change

The supplied TV performance recording showed nine sequences of first frame -> SUBTITLE_MEDIA_UPDATE rebuild=true -> SMARTSUBS_SOURCES_REBUILT -> buffering -> VIDEO_FIRST_FRAME_AGAIN. Generated subtitles were attached using setMediaItem + prepare, resetting video track groups and restoring the video cursor.

Generated SmartSubs WebVTT now has a separate text-overlay lifecycle in Shared Core. New AI choices are excluded from the native video MediaItem and its subtitle-source identity. Their arrival does not replace the video item. Selecting a generated choice warms the existing subtitle endpoint, parses the bytes with Media3 WebvttParser on IO, and feeds timed cues to the existing styled Mobile/TV cue views. No extra video/audio player or decoder is created.

The caption clock reads the playing video's position, with the current subtitle delay. Seek, pause and offsets therefore do not seek or prepare the video. The overlay timer runs only while an AI track is selected. Track removal, Off/native selection, episode/source replacement and disposal cancel old work. A generation guard prevents delayed results from an older selection appearing over a newer choice. Cache eviction can trigger a fresh subtitle readiness fetch without touching video.

AI choices remain in the existing subtitle workspace using the same provider/track identity, saved-language/provider preference, readiness indicators and selection confirmation. Appearance/commentary handling remains in the existing cue views. Generated-track list changes re-evaluate subtitle preferences without resetting audio preferences. Native/addon/provider subtitle handling remains on Media3; ordinary non-AI subtitle updates still follow its existing replaceMediaItem path.

Shared Core adds the media3-extractor 1.11.0 dependency to parse WebVTT. Existing Media3 versions stay 1.11.0. This is a source patch, not an APK.

## Diagnostics

SMARTSUBS_SOURCES_REBUILT is removed from both live-update paths. New markers:
- SMARTSUBS_OVERLAY_UPDATED tracks=... videoReset=false
- SMARTSUBS_OVERLAY_SELECTED videoReset=false
- SMARTSUBS_OVERLAY_CUES count=... videoReset=false (first nonempty overlay cues)
- SMARTSUBS_OVERLAY_FAILED videoReset=false

Phase 17's general frame/discontinuity diagnostics remain. No source URL or caption text is added to these marker details.

## Validation and limits

- New overlay controller compiled against real Media3 common/extractor 1.11.0 AAR classes and Android API stubs, using Kotlin 2.3.21. Existing project domain/cache/readiness/diagnostics helpers were represented by compile stubs in this isolated API check; this is not a full app compilation.
- 23 lifecycle assertions passed against the production controller with controlled coroutine dispatchers and fake Player/parser contracts: no translation request on listing, selection, forward/backward seek, positive/negative offsets, Off, slow-selection cancellation, parse failure, track removal and replacement-owner disposal.
- 10,006 interval-index assertions passed against an independent ordered scan, including overlapping cues and arbitrary seeks. The deterministic regression test is included under shared/core/src/test.
- All 10 changed/new Kotlin production/test files passed PSI syntax checks. Integration guards confirm AI is excluded from native video configurations/keys and that the overlay contains no video setMediaItem/prepare/seek/stop or audio mutation.
- Full Android application compilation, real WebVTT rendering and actual TV/Mobile playback remain unverified in this environment. This patch removes the confirmed SmartSubs reset path in code; device testing is still required.

## Device check

Start the same episode with SmartSubs enabled. Let AI choices arrive, select one, switch between English/Malay AI/Off, adjust subtitle delay, seek in both directions, pause and change episode/source. Check that video does not reset, track groups/progress do not clear and the Malay cues render. Export a performance log if it happens again: AI-only arrival/selection should no longer produce SMARTSUBS_SOURCES_REBUILT or a video position discontinuity.
