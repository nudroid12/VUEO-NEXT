# TV 29C.2a — Startup Update Popup Restore

Scope is intentionally narrow: restore the update-available prompt removed by the clean TV rebuild.

## What survived the rebuild
- `TvUpdateManager` still owns the TV release manifest check, version comparison, cached/rate-limited release state, APK download, SHA-256 verification, package-name validation, unknown-source permission handoff and Android installer launch.
- The TV manifest still contains `REQUEST_INSTALL_PACKAGES` and the update `FileProvider`.
- Settings > Updates still exposes automatic checks, manual checks and Download & Install.

## What was missing
- The clean-rebuild root called the automatic update check but discarded its result.
- The pre-rebuild `VueoTvUpdateManager.kt` UI source is no longer present in the available repository history; it is only a tombstone left by 29A-R2.

## Restored behavior
- After the startup/profile destination is resolved, automatic checks run exactly as before when the Shared Core preference is enabled.
- A newer cached/fetched TV release opens a modal update prompt over the current route.
- Prompt shows release title, current -> new version and up to four changelog entries.
- `Update` uses the existing verified TV download/install flow.
- If Android unknown-source permission is required, VUEO opens the system permission page and keeps the prompt available when the user returns.
- `Later` or Back dismisses the prompt for the current app session only; the release remains cached and can be offered again on a later launch.
- Download progress is shown in the Update control. While downloading, dismissal is blocked to avoid abandoning the visible flow.

## TV interaction lock
- Modal owns focus while visible.
- Left/Right moves only between Later and Update.
- Up/Down stays inside the modal.
- One OK/Enter activation performs one action.
- No forced update and no automatic installation without Android's final confirmation.

## Not changed
- Update manifest/feed format or URLs.
- Settings > Updates.
- Search 29C/29C.1, centered navigation 29C.2, Home, Mobile or Shared Core.

## Build note
Local Gradle compile cannot start in this environment because `services.gradle.org` cannot be resolved. Static delimiter/syntax inspection passed; CI/device build remains authoritative.
