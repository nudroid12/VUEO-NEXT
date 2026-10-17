# VUEO WebView network handoff v4 — shared TV/mobile

Root-relative cumulative changed files based on repo 54 and the earlier shared WebView session/diagnostic/blob patches. No YML. Apply at the app repository root, build via the existing workflow and install. Use Cloudbank VidLink v1.0.10 to enable the new behavior.

New generic opt-in request property: mediaRequestMatchers, a bounded list of up to 16 HTTP media URLs (4096 characters each). Only providers using waitForMediaReady=true and supplying a nonempty matching list can take the new early handoff path. Empty matchers preserve previous readiness behavior. No VidLink URL, domain, provider ID or movie ID is introduced into Kotlin.

An eligible media request must be observed by native WebView request interception and pass existing allowed-host/MP4-HLS path rules. A matcher requires equal scheme, host, port and raw path. Each query parameter present in the matcher must have exactly the same decoded values in the observed URL; unspecified parameters may vary. Duplicate required values and wrong upstream query values are rejected. The provider supplies API-derived routes and the required upstream host value while allowing signatures to rotate.

The WebView then schedules completion using its existing finishAfterFirstMs (250ms for this provider), without requiring readyState or MSE/blob readiness. JSON adds networkHandoffVersion=1; finishReason is network-candidate if no browser-ready URL exists. mediaReady remains false when the browser was not ready. Cookies and observed request headers use the established finalization logic. A JS candidate promoted later to a native-observed request now also triggers scheduling.

This ends on a request, before proving its response or playback. The provider performs the one bounded HTTP probe and API identity check afterward. HTTP 428/HTML or other invalid media remains rejected. There is no new retry, HTTP request, subtitle lookup, timer, timeout extension, page parsing or Cloudflare challenge handling. Existing blob/direct readiness support is retained.

Validation: provider mock tests and actual injected JS regression tests passed. Kotlin policy checks are supplied, including rotated signatures, wrong path/host/upstream/scheme/port, duplicate required query values and missing/empty matchers. Kotlin compiler is unavailable in this environment, so those checks and the full Android build were not run. Device playback is not verified; confirm the Android build via the existing GitHub workflow.
