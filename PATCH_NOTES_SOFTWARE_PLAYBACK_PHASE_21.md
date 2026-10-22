# Phase 21 — independent software playback test, Mobile and TV

This patch adds an explicit in-app LibVLC test for an exact source that still fails through the MediaCodec path. It does not claim that the previously failing files are now playable: that requires a device test. No title, TMDB ID, provider domain, device model or decoder-name allowlist selects this engine.

## Apply

Overlay the ZIP paths on the current VUEO project with the earlier Phase 17/18 SmartSubs, browser-access and Phase 20 patches already applied, then build Mobile and TV normally. Shared decoder files from Phase 20 are included unchanged so that the renderer imports stay consistent. The shared manifest preserves the existing ProviderBrowserAccessActivity registration; keep its previously installed implementation files. There are no YML files or deletion scripts. Cloudbank providers and its manifest do not change in this app patch.

LibVLC is pinned to `org.videolan.android:libvlc-all:3.7.7` in shared core. Gradle downloads the native engine; its AAR is not copied into this ZIP. Both APKs grow because they now include another native playback engine. Shared consumer rules preserve its JNI classes for release shrinking. The published license text and notice are included as assets.

## Device test

1. Mobile: open Sources → Software test → select the specific source that failed. Current and failed sources remain selectable in this mode.
2. TV: open Sources → Software test: OFF → turn ON → select that source. The toggle remains visible under provider filters and resets when the panel closes.
3. The normal player stops before the software test launches. The new player receives the exact URL, User-Agent/Referer headers, timeline position and original audio language preference. Audio and Subtitles open native track pickers. Seek, Play/Pause and Back work with touch or DPAD; Hide removes the controls and DPAD Center brings them back.
4. Test actual moving video, seek forward, listen for audio and try an embedded subtitle. Back returns to the normal player at the observed position, paused. Backgrounding releases the test engine; foregrounding reopens at its saved position. A paused test stays paused.
5. Export the Performance ZIP after testing. `SOFTWARE_START` identifies hardware=false/avcodec; `SOFTWARE_FORMAT` records the parsed video; `SOFTWARE_FIRST_PICTURE` is emitted only after displayed-picture statistics become positive. `SOFTWARE_STATS` records decoded/displayed/lost pictures every five seconds. Playing/opening events alone are not treated as proof of video. Errors report a safe category without signed URLs or header values. Source hashes use the same SHA-256 scheme as subtitle diagnostics.

## Scope and limits

The software test uses VLC's avcodec module, with hardware decoding disabled. This is a separate test player with basic controls, original-language audio preference and embedded subtitle selection. SmartSubs discovery, external provider subtitles, subtitle styling/translation, Trakt, skips and next-episode automation continue to belong to the normal player and are not connected to this test activity. Only direct HTTPS sources with no headers or User-Agent/Referer headers are supported in this initial test. Sources requiring Cookie, Authorization, Origin or other headers fail explicitly rather than silently losing those requirements. DRM is not implemented.

Software 4K playback may be slow, drop frames or still fail. Dolby Vision/HDR color output and sustained frame rate remain unverified. No automatic engine switch is enabled before this evidence is available.

## Validation completed

- Focused Kotlin compilation of the complete shared Activity and policy against the actual LibVLC 3.7.7 classes and Android API stubs.
- 20 executed JVM checks covering allowed HTTP options, signed URL acceptance, required-header rejection, newline/NUL rejection, case handling, locale-independent resume times and bounded seeking.
- Kotlin compiler PSI syntax checks on the four changed Mobile/TV Compose files. These are syntax checks, not a full Compose typecheck or APK build.
- Inspected the published AAR: both 64-bit ABIs have 16 KiB LOAD alignment for the three packaged native libraries. Existing QuickJS AAR contains libquickjs only, so there is no competing libc++_shared copy from that dependency.
- No Android SDK/device is available in this workspace. Full debug/release APK builds, R8 packaging and actual Mobile/TV playback remain unverified. Build commands in an Android environment: `./gradlew :mobile:assembleDebug :tv:assembleDebug`, then `./gradlew :mobile:assembleRelease :tv:assembleRelease`.

Publisher reference: https://code.videolan.org/videolan/libvlcjni
