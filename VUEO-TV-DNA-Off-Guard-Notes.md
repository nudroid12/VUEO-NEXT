# TV Your DNA OFF guard

The TV Your DNA screen checks the current profile's User DNA preference before building
any DNA snapshot. OFF returns to a lightweight disabled-state screen; it does not read
History/Favorites or invoke the DNA engine. Back and Switch Profiles remain available.
The disabled screen directs users to Enhancements → Personalization.

Existing ON-screen rendering and computation are unchanged. History/Favorites/progress
are retained. Does not modify recommendation toggles, cache, Account sync or sidebar.

Based on VUEO-NEXT-main (50); compatible with the subsequent loading/cache and
Personalization-in-Enhancements patches. Repository-root paths, no YAML.

Validation: early return precedes DNA/library work; the disabled-state composable does
not reference either engine or library; removing the guard/helper reproduces the source
byte-for-byte; Kotlin PSI syntax and ZIP integrity checks passed.
No full Android APK build or physical TV rendering/navigation test was available.
