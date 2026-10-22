# Phase 23 — remove temporary playback experiments

Mobile and TV return to the ordinary Sources selector and Media3 renderer. Embedded LibVLC Software test and the separately installed VLC handoff are removed. The same failed HEVC source also misbehaved during the external-player comparison, so neither diagnostic path is being promoted to automatic production fallback.

## Apply

Overlay this ZIP on the current project after Phase 22. Then manually delete every obsolete file listed in DELETE_FILES.md before building. ZIP extraction cannot remove those pre-existing files. No YML, executable deletion script or automation workflow is supplied. No Cloudbank provider code or manifest changes are included.

Keep the existing browser-access implementation files: the shared manifest preserves ProviderBrowserAccessActivity. Earlier SmartSubs, player startup/audio gating, subtitle timing, source recovery, metadata-cache and provider fixes remain in place.

## Removed

- Software test controls, activity, policy and LibVLC Gradle dependency.
- Open in VLC controls, internal bridge, public-link probe, package-visibility query and external activity-result wiring.
- LibVLC-only consumer keep rules and license assets added for that dependency.
- Custom CompatibleVideoRenderersFactory, per-source decoder candidate/session management and runtime codec-switch retries.
- The synchronous queueing experiment, which the logs showed still failed for the problematic sources.
- Obsolete experimental validation files and Phase 19–22 experiment notes.

This patch does not uninstall the separately installed VLC app from the device.

## Retained

- Media3 DefaultRenderersFactory with setEnableDecoderFallback(true) in both actual subtitle renderer factories. Media3 may still select an available Android software codec during ordinary initialization fallback; this is distinct from bundling a second engine. No blanket HEVC/4K restriction or model/title exception is added.
- Existing bounded, supported alternate-video-track recovery and the existing source-fallback caller, respecting its setting. If there is no compatible alternate track, control returns directly to source recovery without the removed codec/queueing experiments.
- Actual subtitle renderer wrappers, subtitle delay/cue normalization, current source headers and normal source selection.
- Runtime playback error diagnostics and useful track-recovery events.

The retained track recovery is simplified to use Media3 common interfaces. Shared core now declares media3-common explicitly instead of depending on ExoPlayer solely for the removed custom renderer. The Mobile and TV modules retain their normal Media3 ExoPlayer dependencies.

## Validation

- The retained recovery and both actual Mobile/TV subtitle renderer class bodies compiled against Media3 1.11.0 APIs. Previously compiled experimental classes were excluded from that compilation classpath.
- 20 executed JVM control-flow checks use real Media3 track/selection objects with a controlled Player proxy and test-only Android string helper.
- 16 executed pure fallback-policy checks.
- Kotlin compiler PSI syntax checks passed for all six modified Compose/player files.
- Production source/config scans contain no references to the removed trial classes, LibVLC dependency, external VLC package or forced synchronous adapter.
- Manifest checks retain only the earlier browser-access activity in shared core and remove the two trial activities/query. ZIP integrity and file-deletion list were checked.
- No Android SDK/device is available here: complete APK builds, release shrinking, resulting APK size and device playback are unverified. After applying the overlay and deletions, build Mobile and TV and smoke-test a known-working source, subtitles and source switching.
