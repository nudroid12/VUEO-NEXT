# Mobile player lifecycle — audit phase 2

Apply the ZIP contents at the VUEO-NEXT repository root, replacing matching files. No YML/workflows, build configuration changes or APK are included.

## Changes

- Mobile player observes its host Activity lifecycle using the same ON_STOP / ON_START boundary as TV. When stopped/backgrounded/locked, it captures playWhenReady, pauses, and queues the current stable progress through the existing IO persistence path. Library notification follows persistence.
- Returning to a started Activity consumes the saved play intent once. A user-paused session stays paused. Resume prompts, exit, completion, episode handoff and known errors prevent automatic resume.
- Duplicate stop events cannot replace a saved playing intent with the false value produced by lifecycle pause itself. A new player composed while already stopped starts paused.
- The 500 ms position/duration UI poll and periodic position persistence are inactive while backgrounded. A foreground return samples the current player immediately. Background transition has its own progress save; disposal retains the existing save/release behavior.
- Startup/rebuffer timers are cancelled and receive a fresh timeout window on foreground return. Auto-hide and auto-next countdowns are gated by foreground state. Upcoming-episode prefetch eligibility cannot initiate new work while backgrounded.
- UI playback requests and subtitle source rebuilds respect foreground state. A listener also pauses unexpected playWhenReady requests delivered by retained async work while stopped.
- Source failures delivered in the background are deferred until foreground entry, avoiding automatic source replacement while stopped.

## Scope and integration

Only mobile player files are replaced. TV behavior and phase-1 image/cache/shared diagnostics changes remain in their separate files. Existing Kids checks, SmartSubs integration, subtitle preferences, episode completion, Trakt hooks, progress ordering and deferred codec cleanup remain in the supplied latest Kids player source.

This patch pauses playback; it retains the prepared player until normal route disposal. It does not add Picture-in-Picture, background playback, a foreground service or decoder release/reprepare on every lock. Backgrounding does not cancel every network task already started by subtitle discovery or prefetch.

The lifecycle boundary is Activity STARTED: a paused but still visible Activity in multi-window remains foreground, matching the TV boundary. Progress writes are queued off Main; no guarantee is made for a force kill before queued disk writes finish.

## Validation

- Compiled the production MobilePlayerLifecycleState with Kotlin 2.3.21 and ran its standalone JVM verification: 2,028 checks passed. Cases cover playing/buffering intent, user pause, duplicate stop/start, resume veto, initial background creation and 1,000 visibility cycles.
- Kotlin PSI syntax parsing passed for both production files and the verification source.
- Reviewed listener registration/removal, synchronous foreground guards, latest-state callbacks, existing IO persistence, timeout effect keys and auto-next dispatch checks.
- No Android build or device playback test was run. This workspace lacks Android SDK and the supplied repo lacks a Gradle wrapper JAR. Syntax and policy tests do not validate Android/Compose/Media3 runtime integration.

Standalone check with an installed Kotlin compiler:

```sh
kotlinc mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayerLifecycleState.kt validation/mobile-player-lifecycle/LifecycleStateCheck.kt -include-runtime -d lifecycle-check.jar
java -jar lifecycle-check.jar
```

## Device checks after build

1. Play, press Home, return: audio stops while backgrounded and playback resumes from the saved point.
2. Pause manually, press Home, return: stays paused. Repeat with screen lock/unlock and rapid repeated switches.
3. Open a resume prompt, background/return: prompt remains; no automatic playback.
4. Background during startup or buffering longer than the normal recovery timeout: no timeout-driven source switching while stopped; the timeout restarts on return.
5. Background near end credits/countdown: no episode advance while stopped. Check normal foreground auto-next afterward.
6. Trigger a subtitle update while stopped: no background audio; check selection and resume when returning.
7. Return to Details/Home after backgrounding: verify Continue Watching position, completion markers and no duplicate audio/player.
8. Test source errors, source change, episode handoff, rotation and process recreation. Existing route/process restoration limitations are outside this patch.
