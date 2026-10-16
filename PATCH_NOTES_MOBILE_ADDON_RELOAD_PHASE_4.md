# Mobile addon configuration reload — audit phase 4

Apply after phase 3 at the VUEO-NEXT repository root. This ZIP replaces VueoApp.kt with a version that preserves phase-3 Home state integration, and adds MobileAddonConfigurationReloader.kt. Phase 3's MobileHome.kt is still required. No YML, workflows, dependency/build changes or APK are included.

## Behavior

- Synced preferences no longer uninstall every Stremio addon before refetching all manifests. Installed manifest URLs are reused. Enable/disable flags are applied from current preferences.
- Startup, account configuration reload and persistent-data import/reset use one mutex-protected coordinator. Startup remains outside the local boot gate.
- Only missing URLs are fetched during these ordinary reloads, with at most two manifest requests in flight. A second overlapping reload waits, then sees descriptors already installed by the first.
- Fetches complete before descriptors are replaced. Failure/cancellation preserves working descriptors. Only addons whose URLs are no longer configured are removed. If preferences remove a URL during a fetch, its result is discarded rather than resurrecting it; toggles are read again at commit.
- A newly added URL using the ID of another still-configured addon is skipped, preserving the existing addon. The engine supports one extension per ID, so these conflicts require user configuration changes rather than silently overwriting a different URL.
- A count-only ADDON_CONFIG_RELOAD log reports loaded/reused/removed/failed/conflicts when performance diagnostics is enabled; it includes no URLs or credentials.

## Scope

Mobile only. TV and shared sync transport are unchanged. Cinemeta/OpenSubtitles defaults remain governed by the earlier defaults patch. Plugin code sync and existing global catalog/source cache invalidation remain in place: they also reflect provider preferences, catalog order and integration changes. Selective cache invalidation and provider-code download policy are separate audit work.

Ordinary sync/import does not refresh an installed manifest merely because the same URL was synced again. Explicit manual refresh in Content Manager retains its existing path. The coordinator has a forceRefresh option for callers that deliberately need it, but the three automatic paths use reuse mode.

Direct manual Content Manager operations remain on their existing path; they are not all protected by this coordinator's lock. The coordinator rechecks configured URLs and applies current flags after downloads, but it is not an atomic transaction across all preference/UI mutations. URLs added during an in-flight reload are handled by the next queued configuration revision.

## Validation

- Compiled the production coordinator with Kotlin 2.3.21 and coroutines 1.10.2 against small in-memory stand-ins for the Android-backed store and engine.
- 24 behavioral checks passed: zero fetch on unchanged/toggle-only configuration, missing URL install, removal, failed new manifest retention, failed forced refresh retention, changed manifest ID, conflicting IDs, maximum concurrency of two, overlapping reload reuse, cancellation, removal during fetch and current-toggle application.
- Kotlin PSI syntax parsing passed for both production files. Reviewed all three coordinator call sites and preservation of phase-3 Home integration.
- These checks exercise the actual coordinator logic with stand-ins, not Android networking, preferences, Compose or the real engine. Full Android build and device tests were not run: no Android SDK is installed and the supplied source lacks the Gradle wrapper JAR.

Standalone verification sources are in validation/mobile-addon-reload. They are outside Android source sets. With Kotlin and its coroutines JVM JAR available:

```sh
kotlinc mobile/src/main/java/com/vueo/mobile/core/extensions/MobileAddonConfigurationReloader.kt validation/mobile-addon-reload/stubs/*.kt validation/mobile-addon-reload/ReloadCheck.kt -cp "$COROUTINES_JAR" -include-runtime -d addon-reload-check.jar
java -cp "addon-reload-check.jar:$COROUTINES_JAR" com.vueo.mobile.core.extensions.ReloadCheckKt
```

## Device checks

1. Sync a toggle-only change from TV; verify manifest reuse in the diagnostic count and correct visibility/source behavior.
2. Sync a new addon that is offline; verify existing addon catalogs/sources remain available.
3. Remove an addon through synced preferences; verify it is removed without reinstalling all others.
4. Start mobile while account configuration is arriving; verify one coherent result and no duplicate manifest work.
5. Import/reset data and check intended addon removal/default seeding, Home restoration, catalog ordering and provider toggles.
6. Use Content Manager's explicit refresh and verify updated manifests still load normally.
