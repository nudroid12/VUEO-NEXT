# Phase 22 — Open in VLC, Mobile and TV

Adds a source-picker option to open the exact selected media URL in the separately installed VLC for Android app. This makes the comparison possible without manually obtaining a signed URL. No movie, provider domain, device model or decoder-name allowlist selects this path. VLC's decoder configuration remains under the user's control.

## Apply and use

Overlay this ZIP onto the current VUEO project and build Mobile/TV normally. It carries forward the Phase 21 files and earlier shared decoder files; the latest instructions here supersede Phase 21's software-only source-picker instructions. Keep the previously installed SmartSubs and browser-access implementation files. No YML or deletion scripts are included. Cloudbank providers and manifest are unchanged.

1. Install the official VLC for Android app from Play Store on the device being tested.
2. Mobile: Sources → Open in VLC → select the specific failed source. Current and failed sources remain selectable.
3. TV: Sources → select the Playback row until it reads Open in VLC → select the source. The selection cycles Default player → Software test → Open in VLC and resets when the panel closes.
4. In VLC, test Hardware acceleration: Automatic. Then set it to Disabled and reopen the same source through VUEO. Observe picture corruption, smoothness and time to start. VUEO does not force either VLC setting.
5. Back returns to the normal VUEO player paused. When VLC provides a successful result with a position and a matching source URI, that position is forwarded; otherwise VUEO retains the handoff position.

This is the external VLC app, distinct from Phase 21's embedded LibVLC software test. This patch supplies the handoff; it does not claim that corrupted or stuttering playback is fixed.

## Direct-link check and headers

VLC's normal ACTION_VIEW path does not reliably accept arbitrary provider headers. The Android app can also recreate media entries when expanding a playlist, so merely placing options in an XSPF is insufficient evidence that they reach playback. This patch does not claim to forward unsupported headers.

Before opening VLC, VUEO makes a fresh, small HTTPS GET with Range bytes=0-31 and no provider User-Agent/Referer/Cookie/Authorization. It reads at most 32 body bytes in application code and closes the response, even when the server ignores the Range request. No full video is downloaded or relayed. A direct video response or a recognized MP4/EBML container must be observed. This is suitable for public direct storage links; it rejects HTML/challenge/error bodies and manifests. HTTPS downgrades are not followed.

If the check fails, the screen explains that it is not a decoder failure. An expired link, required provider headers, network problems or an unsupported response can prevent this test. Even a successful probe is not a guarantee that the external VLC client's later request will succeed; VLC's actual network error must still be distinguished from a decode/render issue. No signed URLs or header values are recorded in diagnostic events.

The ordinary source URL is passed unchanged to VLC, with its start position in milliseconds. No MediaCodec, software-decoder, quality or audio-language override is added to VLC. Playback therefore follows the installed VLC version and its settings.

## Integration

An internal, non-exported ExternalVlcActivity handles validation, package availability, cancellation, the external result and clear error messages. Package visibility declares only org.videolan.vlc. Existing SoftwarePlaybackActivity and ProviderBrowserAccessActivity registrations are preserved. No FileProvider, local proxy, background service, extra native library or new Gradle dependency is added.

The normal player pauses and stops before the handoff. Network work runs off the main thread, with connection/read timeouts of 10 seconds and a 20-second overall timeout. Closing the bridge cancels the call. VLC is launched only while the bridge is resumed; backgrounding during validation does not launch another app unexpectedly. Saved state prevents duplicate launches after recreation.

Diagnostics: EXTERNAL_VLC_PROBE, EXTERNAL_VLC_OPEN, EXTERNAL_VLC_RETURN and EXTERNAL_VLC_UNAVAILABLE. These describe handoff/access, not successful video output in another process. Visual results from VLC must be reported by the tester.

## Validation

- Focused Kotlin compilation of the complete bridge, policy and existing software Activity against actual OkHttp 5.4.0, Okio 3.17.0, coroutine and LibVLC APIs with Android API stubs.
- 29 executed JVM checks for URL validation, media-response classification, rejected network/challenge/manifest responses, source-matched returned positions and TV target cycling.
- Kotlin compiler PSI syntax checks of the changed Mobile and TV Compose files; these do not replace full Compose typechecking.
- Manifest/ZIP checks preserve the internal activities and query entry, and exclude YML and deletion scripts.
- Full APK builds, release shrinking and installed VLC handoff require an Android SDK/device and remain unverified here.

Primary implementation references inspected:
- https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/StartActivity.kt
- https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/gui/video/VideoPlayerActivity.kt
- https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/media/PlaylistManager.kt
- https://docs.videolan.me/vlc-user/android/3.X/en/more/settings/general_settings.html
