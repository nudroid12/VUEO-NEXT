# Phase 16 validation

CollectionTest.kt contains 18 JVM JUnit 4.13.2 tests. It executes the production
CollectionModels, CollectionJson, CollectionStore and CollectionCatalogResolver
from shared/core. Use the actual shared ExtensionModels, AddonInstallationIdentity,
MediaModels and MediaExtension files as well.

The EngineShell, AndroidContext and AtomicFile here are JVM test doubles; do not
place them in production sources or compile them beside the Android SDK/real
engine. They isolate IO/locking/identity behavior and do not validate Android's
AtomicFile implementation or live addon responses.

Dependencies: Kotlin 2.3.21 stdlib, kotlinx-coroutines-core-jvm 1.10.2,
org.json:json 20240303, junit 4.13.2 and hamcrest-core 1.3.
JUnit entry: com.vueo.shared.core.collections.CollectionTest.

The new CollectionScreens.kt also passed a separate compilation with the
Kotlin 2.3.21 Compose compiler and real Compose BOM 2026.06.01 artifacts,
against the tested production core API/engine shell and an Android API jar.
This is not a Gradle app build. Full Mobile/TV integration and device focus/
rendering still require the normal Android build and on-device verification.
