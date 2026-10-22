VUEO only. Apply at the VUEO repository root, build and install TV/Mobile, then apply the separate Cloudbank VidSrc v3.2.1 patch. Replaces the previous frame-interaction patch with generic diagnostics v2.

Adds document-start/DOM-ready/window-load/unload and script/runtime error counters per configured stage; configured custom-event observations; request-query-key classification; configured console-signal counters. No raw page/console/URL/token content is returned. No domain, title, verification bypass or authentication rules are hardcoded. Existing interaction behavior and limits are retained.

The full modified resolver/session policy compile against Android API 35 and WebKit 1.14.0. Run node tests/provider-frame-interaction.cjs for script fixtures. Full APK and real playback unverified. This is instrumentation to identify the failure stage, not a confirmed playback fix.
