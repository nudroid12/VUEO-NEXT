# VUEO VidSrc.to subtitle bridge — TV and Mobile

Apply this ZIP at VUEO repository root, build with the existing GitHub Actions workflow, then install the new app. No YAML files or dependencies changed. The original patch was based on VUEO-NEXT-main (52). Its resolver changes have been compared with repo (53) and included in this combined Repo53 package; no other resolver changes are lost.

Adds optional captureSubtitles (default false), a separate subtitles response array, per-track metadata, native .vtt/.srt request capture, and DOM/JW/config/JSON hooks in the document where hooks run. Track URLs are separate from media candidates and do not trigger early video completion. Final headers include target URL cookies and User-Agent. No subtitle cookies or signed URLs are logged by these changes. HLS video capture and ordinary provider defaults remain in place.

Use with Cloudbank VidSrc.to 1.0.1. The provider attempts to unwrap only the observed exact-ID vsembed.ru iframe so JavaScript hooks run on the player document. Hook injection cannot inspect arbitrary nested cross-origin frames; native request capture may still see their requested subtitle URLs. No document-start/all-frame API dependency was added.

Run node tests/vidsrc-to-subtitle-hook.cjs for the hook fixture check.

Validation: JavaScript fixture tests passed; Cloudbank npm run validate passed for all 53 providers. These tests validate routes, bridge hook behavior and timed-text fixtures, not live subtitle playback. Native Android compilation was attempted but blocked before compilation because Gradle could not resolve com.android.application:9.1.1. No Android build success is claimed.

Live status: v1.0.0 video playback was confirmed by the user's Android log. The development network still returns a Cloudflare 403 for the inner player; no live subtitle list or cue timing was observed. Rebuild and test in VUEO to confirm real-site capture and playback. A captured valid cue file can still be wrong-language or out of sync upstream.
