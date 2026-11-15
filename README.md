# Preserve valid Season 0 — Mobile and TV
Based on repo65. Changes shared DetailUpstreamPolicy.kt and StremioAddonExtension.kt. Removes both all-season-zero to season-one rewrites. Explicit nonnegative season values, including zero, take precedence over parsed episode IDs. Missing/negative season may fall back to a nonnegative ID season, including zero. Episode IDs, episode-number rules and sorting remain unchanged.

A metadata provider incorrectly labeling ordinary episodes as Season 0 will now retain that label; the app no longer guesses Season 1. Correct such data upstream rather than relabeling valid Specials.

Copy shared/ over repository root, or apply CHANGES.diff for production changes and copy the test file separately. No backend/plugin changes required. JUnit regression tests included for specials-only, conflicting IDs, missing season inferred from ID, and mixed regular/special seasons. Tests not executed: Kotlin compiler/Android Gradle toolchain unavailable. Static scope checks, patch applicability and ZIP CRC validated only.
