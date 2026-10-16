# Provider code warm-up/cache — audit phase 6

Extract/upload at the VUEO-NEXT repository root. Only shared/core/.../ProviderCodeStore.kt is replaced, so the behavior applies to both Mobile and TV. Earlier phase-1 through phase-5 changes are in other production files and remain intact. No YML/workflows, dependencies or APK are included.

## Behavior

- Automatic syncMissing warm-up now uses the existing syncEnabled path instead of syncing every repository/provider. It respects the global Plugins switch, repository switch and each provider's preference/default.
- This affects existing startup, account/configuration reload, data-import/reset and mobile repository-page warm-up callers. No UI replacement is needed.
- Disabled provider code is not deleted. If a provider is enabled later and its current code is absent, the existing runtime preflight downloads it on first use before QuickJS execution. Enabling alone does not immediately trigger a background download; readiness indicators may remain not-ready until use or an explicit refresh.
- Explicit syncRepository/syncProviders retain their caller-selected scope. Manual repository install/refresh can still deliberately download disabled provider code; automatic warm-up is the path filtered by enable preferences.
- A ready/no-op sync no longer clears PluginRuntimeCache. For a fetched script, its text is compared with the cached text under the repository write lock; identical text is not rewritten and does not clear the discovery cache.
- A successful new/changed script publication clears the discovery cache immediately, including when a later provider fails or the remaining sync is cancelled. Failed fetches do not replace the previous script or clear the cache by themselves.
- Empty provider target lists return immediately, avoiding unnecessary pruning and cache work.
- Existing global download semaphore (3), fixed provider/repository lock stripes, size limits, atomic-temp-file publication and conservative obsolete-script pruning remain in place.

## Scope/limits

PluginRuntimeCache caches short-lived source discovery results; it is not a compiled QuickJS-code cache. Existing invalidations for changed toggles/manifests, manual refresh actions and explicit retry remain unchanged. This patch removes the unconditional invalidation inside provider-code sync, not every invalidation in the application.

Enable preferences are selected when automatic warm-up builds its target list. A download already selected/in flight can finish if the user disables it afterward; disabled providers remain excluded from normal discovery. Obsolete-code cleanup remains conservative when disabled providers have no readable replacements.

## Validation

- Compiled the actual production ProviderCodeStore/ProviderCodeSyncManager with Kotlin 2.3.21, coroutines 1.10.2 and Okio, using small stand-ins for Android Context, plugin preferences/models, HTTP and the discovery cache.
- The store used real temporary files and its production hashing/read/write/readiness methods. 28 behavioral checks passed: active/disabled/default-disabled/master-off filtering, ready/no-change cache retention, explicit first-use preparation, identical/changed forced refresh, failed refresh retention, empty targets, duplicate concurrent sync, the download ceiling, and cancellation after partial successful publication.
- Kotlin PSI syntax parsing passed for the production replacement.
- Reviewed existing Mobile/TV syncMissing callers and runtime preflight's explicit syncProviders path.
- Full Android build/device tests were not run: this workspace lacks Android SDK and the supplied source lacks the Gradle wrapper JAR. Stand-ins do not validate actual SharedPreferences, HTTP or app integration.

Standalone sources live under validation/provider-code-sync, outside Android source sets. With Kotlin, coroutines and Okio JVM JARs available:

```sh
kotlinc shared/core/src/main/java/com/vueo/shared/core/plugin/ProviderCodeStore.kt validation/provider-code-sync/stubs/*.kt validation/provider-code-sync/ProviderSyncCheck.kt -cp "$COROUTINES_JAR:$OKIO_JAR" -include-runtime -d provider-sync-check.jar
java -cp "provider-sync-check.jar:$COROUTINES_JAR:$OKIO_JAR" com.vueo.shared.core.plugin.ProviderSyncCheckKt
```

## Device checks after build

1. Use a repo with active and disabled providers, clear script storage through the existing app controls, then restart. Confirm automatic warm-up prepares only active providers.
2. Disable the repository/global Plugins switch and repeat; confirm no automatic script warm-up for those targets.
3. Enable a previously uncached provider, then request a source it supports. Confirm preflight obtains the code and discovery runs; check offline failure messaging too.
4. Repeat source discovery after a ready/no-change warm-up. Confirm existing cache behavior remains; update a provider's code and verify subsequent discovery uses updated code.
5. Exercise manual repository refresh, account sync and data import on Mobile and TV. Confirm explicit refresh still works and provider toggles remain respected.
6. Compare startup/network activity under the same enabled-provider configuration before claiming numeric gains. No measured speedup is claimed here.
