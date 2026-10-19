# Player startup frame continuity — Mobile and TV, Phase 17

Baseline: VUEO-NEXT-main (55).zip. Overlay the paths at the repository root. No YAML, build configuration or provider changes; no file deletions.

## What this fixes

Both players performed an explicit seek immediately after replacing the current item for an ordinary subtitle update. Remove that redundant seek so it cannot introduce an extra position discontinuity/decoder flush. Recheck the current URI and applied subtitle keys after the debounce to avoid applying stale or already-installed updates.

Both PlayerViews used default frame clearing when a playing item was reset. Enable keepContentOnPlayerReset for the same video and player. Track a content key (media identity plus source URI), and temporarily disable retention and clear/rebind when the content or player genuinely changes. Ordinary Compose updates do not reassign the player or its video surface.

SmartSubs generated-track changes still use setMediaItem + prepare as required by the existing subtitle-child handling. This patch preserves that functionality and does not claim to remove all rebuffering. Frame retention is a PlayerView mitigation; decoder/surface behavior can still differ by device.

## Diagnosis

The supplied video showed a picture briefly, then black, then video again. The older logs do not identify the cause of that exact recording, so this is a targeted correction of confirmed code paths, not a proven diagnosis of the clip.

Performance logging now records SUBTITLE_MEDIA_UPDATE with rebuild/first-frame/track-count context; VIDEO_FIRST_FRAME_AGAIN after subsequent first-frame callbacks; POSITION_DISCONTINUITY with reason and cursor changes. Existing logging caps and collection settings remain in use. No source URLs or subtitle text are added to logs.

## Validation

14 JVM behavioral checks passed against the actual extracted binding methods using a fake PlayerView: initial binding, repeated recomposition, same-video reset, episode change, URL change and player instance change, for both platforms. Integration checks confirm both view call sites pass a content key, redundant subtitle seeks are removed and SmartSubs rebuild handling remains.

All four modified Kotlin files passed Kotlin PSI syntax parsing. These checks are not Android compilation or on-device testing. Full Gradle compilation was attempted with :mobile:compileDebugKotlin :tv:compileDebugKotlin --offline; it failed before compilation because Android Gradle plugin 9.1.1 was not cached. No APK or emulator verification is claimed.

## Device checks

- Start the same working episode and observe whether the early black flash is gone.
- Let late provider and SmartSubs captions arrive; verify video position and selected subtitles.
- Switch source, switch episode, return from background, retry and exit: ensure the previous frame does not linger.
- If the flash persists, record performance diagnostics from that same attempt. The new events distinguish subtitle updates, cursor changes and repeated first-frame callbacks.
