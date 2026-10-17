# VUEO WebView session handoff v1 — app repo 54

Apply this ZIP at the root of the VUEO app repository based on VUEO-NEXT-main (54). Build the app through the existing GitHub workflow and update the phone/TV app. No YML included. Apply the companion Cloudbank VidLink v1.0.7 provider only after the new app is ready.

Scope: shared resolver for Mobile and TV plus a small pure Kotlin policy. Existing subtitle capture and other app changes from repo 54 are retained. No app-side VidLink domain or provider ID hardcoding.

New optional resolver settings:
- waitForMediaReady (default false): captured URL alone cannot finish a session. The direct-page video must have readyState >= HAVE_CURRENT_DATA, no media error, and an HTTP media currentSrc on an allowed host.
- mediaHosts: at most eight exact hosts supplied by the provider. With waitForMediaReady, only MP4/M3U8 pathname endings on these hosts are accepted; matching a media extension inside a JavaScript URL query cannot end discovery.

For opted-in sessions, later intercepted network headers replace the early generic headers case-insensitively. Later JSON/DOM hooks cannot downgrade them. Captured Origin is retained, and cookies are refreshed from CookieManager for the media URL before handoff. Legacy requests keep the original default finish/header behavior. Click attempts continue while candidates exist but video is not yet ready. No additional periodic scan: the existing player inspection checks readiness.

Output for opted-in sessions includes sessionHandoffVersion=1, mediaReady, capturedCount and finishReason (media-ready/timeout/load-error). Each candidate marks mediaReady and networkObserved. No new diagnostic fields contain raw cookies/tokens/URL queries.

Limits: browser readiness only proves the embedded video had data in that session. It does not prove that the native player can reuse the session. The companion provider checks the selected observed URL once outside WebView and rejects HTTP errors. HTTP 428 can still occur. Direct MP4 readiness is covered; blob/MSE playback cannot currently be correlated safely and is skipped rather than treated as ready. No CAPTCHA solver, alternate proxy or clearance guarantee.

Validation: pure Kotlin URL/header policy compiled and checks passed; the actual injected JavaScript hook passed readiness/error/legacy/blob checks; companion provider tests and Cloudbank's 53-provider validation passed. Full Android compile attempted online and offline but stopped before source compilation because com.android.application 9.1.1 could not be resolved. Therefore the complete Android build and on-device playback remain unverified. Build success must be confirmed by the existing GitHub workflow.
