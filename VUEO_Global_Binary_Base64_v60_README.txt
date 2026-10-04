VUEO Global Binary / Base64 v60
Base: VUEO-NEXT-main (36).zip, including runtime completion v59.

Apply this ZIP at repository root and build both Mobile and TV.
This cumulative patch may be applied directly on (36), or over v59.
Keep MSM21 JS v1.0.6 while testing; no provider scripts are changed.

Global fix:
Raw binary strings containing NUL were truncated through the QuickJS native
String binding. Transport binary strings as JSON strings in both directions.
JSON escapes the embedded NUL before the native boundary, then restores it on
the receiving side. Base64 output and the public btoa/atob interfaces stay the
same. The shared Buffer UTF8 Base64 encoding path uses safe transport too.
All providers using these runtime APIs receive the fix, including CryptoJS
encoding, hashes and AES. There are no provider names, host allowlists or
server-specific branches in the implementation.

Preserved:
- All runtime completion v59 changes and per-invocation cancellation.
- Existing timeout values and server-selection rules.
- Provider JavaScript, source parsing/ranking, UI and playback behavior.
- Existing binary byte-masking semantics.

Validation:
- 8 new integration scenarios passed on QuickJS JVM 1.0.14 using extracted
  production runtime JavaScript and the production Kotlin crypto/binary code.
  Covers empty/ASCII strings, NUL positions, exact encoding of all bytes 0-255,
  complete binary round trip, UTF8+NUL Buffer encoding, SHA-256, AES with NUL
  ciphertext and AES CipherParams. Android Base64 uses a desktop test adapter.
- 6 runtime completion scenarios passed again on the updated script.
- 5 v59 JUnit lifecycle tests previously passed; those sources are unchanged.
- Full Android build remains unconfirmed: the attempted build could not resolve
  Android Gradle plugin 9.1.1. No APK or real-device playback claim is made.

Build:
./gradlew :shared:core:testDebugUnitTest :mobile:assembleDebug :tv:assembleDebug

Standalone integration fixtures (outside Android source sets):
python3 tests/provider-runtime/extract-runtime.py /tmp/vueo-runtime.js
Compile each fixture separately with the production helper(s), QuickJS JVM
1.0.14 and its declared dependencies, then run:
com.vueo.shared.core.plugin.QuickJsChecksKt /tmp/vueo-runtime.js
com.vueo.shared.core.plugin.BinaryBridgeChecksKt /tmp/vueo-runtime.js
BinaryBridgeChecks additionally needs BinaryCompatBridge.kt,
CryptoCompatBridge.kt, org.json and the supplied AndroidBase64Jvm.kt adapter.
These fixtures simulate HTTP responses; external server playback needs testing.
