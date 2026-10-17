# VUEO WebView state diagnostics v2

Based on app repo 54 plus the previous session-handoff v1 patch. Apply to the VUEO app repository, build through the existing GitHub workflow and update the app. Then apply Cloudbank VidLink v1.0.8 and refresh. No YML or separate test provider.

Diagnostic-only change for VidLink v1.0.7's mediaReady=false timeout. Playback, URL acceptance, cookies/headers, readiness requirements, existing click schedule and 12-second WebView timeout are unchanged. No additional HTTP requests or timers are introduced. Legacy providers that do not opt into waitForMediaReady do not collect these fields.

The existing player inspection emits a compact snapshot only when its scalar state changes. Fields: number of video elements, readyState, networkState, numeric MediaError code, paused flag, source scheme (http/https/blob/none/other), document visibility and an allowlisted page-message classification. DOM title/body are inspected locally (body classification limited to the first 8192 characters); their text is never returned or logged. Video error messages and full media URLs are excluded. Native parsing again restricts numeric ranges and enum values.

Existing WebView error callbacks record main-page/media HTTP error statuses and native network error codes. A status of 0 means no error callback was observed, not HTTP 200. HTTP callbacks may not cover every internal media request. MSE/blob flags report only observed hook events; false does not prove that technology was absent. pageSignal values are hints from recognizable messages, not a conclusive security diagnosis. No CAPTCHA interaction is added.

No native playback recovery is claimed. This patch exists to distinguish a missing/blocked page, missing video element, pending/error media, and unsupported blob/MSE readiness without another round of header/timeout guesses.

Validation: actual injected hook tests passed for readiness unchanged, state changes/deduplication, error/blob/page classifications, omission of private values and opt-in disabled behavior. Provider tests passed with unchanged API/session/HTTP counts. Cloudbank validator passed for 53 providers. Full Android build remains unverified: the unchanged AGP 9.1.1 dependency was unresolved in the previous online/offline build attempts; Android compilation was not repeated for this diagnostic-only change. Confirm the new app build in GitHub.
