# VUEO WebView Capture Patch — CineMode/MSE hardening

Scope: Shared Core provider WebView resolver only. Mobile and TV consume the same Shared Core implementation.

## What changed

1. Matched WebView requests are capture-only by default.
   - Older behavior returned an empty `WebResourceResponse` after a match.
   - That could terminate a JavaScript/MSE player before it reached the final HLS/DASH/MP4 request.
   - Legacy blocking can still be requested with `blockMatchedRequests: true`.

2. Provider-supplied `match` values now extend the built-in media matches instead of replacing them.
   - Built-ins include `.m3u8`, `.mpd`, `.mp4`, `.m4v`, `.webm`, `.mkv`, and `/sora/`.

3. Known source-discovery API routes are no longer treated as playable streams.
   - Includes CineMode-style `/backend_/sources/...` routes and known source names.
   - These requests are allowed to complete so the player can reveal downstream media.

4. WebView JavaScript capture is installed at page start, page commit and page finish.

5. The capture hook now observes:
   - `fetch()` requests and response headers/body text
   - `XMLHttpRequest` requests and response headers/body text
   - URLs appearing after `JSON.parse()`
   - URLs appearing after `atob()`
   - WebCrypto `subtle.decrypt()` text output when small enough to inspect safely
   - `<video>` / `<source>` `src` changes and DOM mutations
   - JW Player source lists
   - `MediaSource` and blob usage for diagnostics/capture progression

6. Extensionless final responses can be accepted when their MIME type clearly identifies HLS, DASH, or a sufficiently large direct video response.

7. Playback Referer now preserves the captured request Referer when available instead of always overwriting it with the initial resolver URL.

8. Runtime WEBVIEW diagnostics now include the number of streams returned.

## Files changed

- `shared/core/src/main/java/com/vueo/shared/core/plugin/PluginWebViewResolver.kt`
- `shared/core/src/main/java/com/vueo/shared/core/plugin/PluginRuntime.kt`

## Validation performed

- Extracted WebView hook JavaScript: `node --check` PASS.
- Kotlin structural/static check performed; no parser/`expecting` errors were found in the changed code.
- Full Gradle compile could not run in the patch environment because the Gradle wrapper distribution was not cached and outbound DNS to `services.gradle.org` is unavailable.

## Test target

Use the existing CineMode provider against the same title that previously stopped after `/backend_/sources/...` capture. The resolver should now allow that request to finish and continue waiting for a final `.m3u8`, `.mpd`, `.mp4`, or MIME-identified media response.
