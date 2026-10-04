VUEO Provider Runtime Completion v59
Base: VUEO-NEXT-main (36).zip

Apply this replacement ZIP at the repository root. Build Mobile and TV.
Refresh/retest MSM21 v1.0.6; no provider JavaScript changes are required.

Changes:
- One native-task owner per getStreams invocation.
- After getStreams settles, cancel outstanding native fetch, WebView,
  discovery-context waits and timers for that invocation only.
- OkHttp already cancels its Call through coroutine cancellation.
- Late responses cannot execute provider continuations after completion.
- Timers use numeric IDs, support callback arguments, and clearTimeout
  cancels native delay even when called before job registration.
- Preserve genuine caller/deadline cancellation and original provider errors.
- Keep provider timeout values, source selection/ranking and per-server rules.

Compatibility:
Sequential providers and Promise.all providers finish their awaited work before
cleanup. Fire-and-forget work after getStreams completion is now stopped.
Shared Core is used by both Mobile and TV. No UI changes or server exceptions.

Validation:
- New helper compiled using Kotlin/JVM; 5 JUnit tests passed.
- 6 integration scenarios passed with QuickJS JVM 1.0.14 and the extracted
  production JavaScript runtime: race with slow native loser, sequential fetch,
  Promise.all, cleared timer, leftover timer and timer callback arguments.
- Additional standalone checks verified caller cancellation, error preservation
  and separate invocation isolation.
- Full Android build attempted but Android Gradle plugin 9.1.1 could not resolve.
  APK compilation and real-device playback are still pending.

Tests:
./gradlew :shared:core:testDebugUnitTest :mobile:assembleDebug :tv:assembleDebug

Standalone QuickJS fixture:
python3 tests/provider-runtime/extract-runtime.py /tmp/vueo-runtime.js
Compile tests/provider-runtime/QuickJsChecks.kt with ProviderExecutionTasks.kt,
QuickJS JVM 1.0.14, its declared Kotlin/coroutine dependencies, and run
com.vueo.shared.core.plugin.QuickJsChecksKt /tmp/vueo-runtime.js.
The fixture uses simulated HTTP; it does not certify external server playback.
