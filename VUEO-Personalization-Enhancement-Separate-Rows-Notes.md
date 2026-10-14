# Personalization in Enhancements + independent Home row switches

TV and mobile. Based on VUEO-NEXT-main (50) and includes the preceding Home loading/cache repair.
Apply this ZIP after the preceding loading/cache patch, or use this ZIP alone on repo (50).
Repository-root paths, no enclosing folder or YAML.

Personalization moves from the root Settings hub/sidebar into Enhancements.
TV treats it as an Enhancements child panel, with Back returning to Enhancements.
Mobile opens it inside Enhancements, with page and system Back returning to that hub.
User DNA and Show DNA Match remain available. The combined recommendation switch is replaced
by For You and Because You Watched switches in a Home Recommendations group.

Each row switch controls only its own visibility and background work. Turning one off cancels
only that row's in-flight job, leaving the sibling running. Cached or late sibling results cannot
restore a disabled row. Re-enabling a row can reuse its compatible cache.
Turning User DNA off retains both saved row choices but gates and stops both rows, as before.

New preferences inherit the old combined recommendation value until individually set.
An old OFF profile therefore starts with both new row switches OFF; no automatic reset.
Preferences remain scoped to the local profile and are absent from Account sync's key allowlist.
No account/backend/sync schema changes are needed. Ranking and recommendation sources stay the same.

The previous independent loading, 3-request Trakt concurrency, same-title request coalescing,
profile/engine disk cache and background navigation survival remain included.

Validation:
- Shared production code and DNA preferences compiled with Kotlin 2.3.21 against JVM Android,
  JSON and metadata-service fixtures plus real coroutines/OkHttp libraries.
- 17 JUnit ranking/cache/visibility tests passed.
- Toggle fixture checks passed: legacy OFF inheritance, profile isolation, DNA master gating,
  both OFF causes no HTTP, each single-row mode skips the other loader, in-flight sibling
  preservation in both directions, late result masking and cache reuse after re-enable.
- Existing feed checks passed: independent publication, 3-request limit, request coalescing,
  tab-observer cancellation survival, profile isolation and disk restoration in a new JVM process.
- Kotlin PSI syntax checks passed for every changed Kotlin file; navigation and sync allowlist
  checks passed; ZIP root paths and integrity verified.

Not a full Android APK build or physical TV/mobile navigation/rendering test.
Use the normal GitHub Actions builds and verify Settings → Enhancements → Personalization.
