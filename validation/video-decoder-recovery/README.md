# Track recovery checks — Phase 23

These checks live outside Android source sets. Never copy the validation stubs into src/main.

The pure VideoFallbackPolicyTest runs 16 checks for supported-track selection, failed-configuration exclusion and bounded attempts. Compile it with VideoFallbackPolicy.kt using Kotlin/JVM and run VideoFallbackPolicyTestKt.

VideoDecoderRecoveryJvmCheck runs 20 checks against actual Media3 1.11.0 common model objects. The Player is a controlled dynamic proxy; RuntimeDiagnostics is a test sink. A tiny JVM-only android.text.TextUtils shim supplies string helpers that Android's API signature JAR does not implement. No codec or device capability is simulated as a playback result.

Compile VideoFallbackPolicy, VideoDecoderRecovery, this check and the two stubs with Media3 common, Android API signatures, Guava, Kotlin standard library and AndroidX annotations. Run com.vueo.shared.core.player.VideoDecoderRecoveryJvmCheckKt. Check that unsupported tracks are ignored, the expected override is installed, pause/resume position are preserved, retries are bounded, non-video/network errors bypass this recovery, and exhaustion returns control to the existing source fallback caller.

Actual Mobile and TV subtitle-renderer class bodies were separately extracted and compiled against Media3 1.11.0 ExoPlayer. Both now inherit DefaultRenderersFactory and retain setEnableDecoderFallback(true). Six changed Compose files passed Kotlin PSI syntax checks.

Total executed assertions: 36. Full Android builds and real-device playback remain required. Experimental codec/session/queueing tests and the VLC tests were removed along with their production code.
