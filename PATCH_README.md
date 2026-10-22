VUEO only. Copy these files to the VUEO repository root. Build and install TV/Mobile as usual, then apply the SEPARATE Cloudbank VidSrc v3.2.0 patch. No dependency changes. Shared AndroidX WebKit 1.14.0 is already used by the repository.

Generic opt-in framePlaySelectors/playerFrameSelectors activate named Play buttons in their own document after DOM readiness, including cross-origin nested frames. stagePathPrefixes and frameInteraction diagnostics expose bounded stage counts without URL queries or bodies. Old coordinate interaction is disabled only in this opt-in mode; other providers retain their existing path. No website domain/title logic is added to the app. Website verification is left intact.

Modified resolver/session policy compile against Android API 35 and WebKit 1.14.0. Run node tests/provider-frame-interaction.cjs from this repo root for script fixtures. Full APK and device playback are not tested here.
