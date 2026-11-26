<p align="center">
  <img src="mobile/src/main/res/drawable/vueo_logo_mark.png" width="96" alt="VUEO app icon">
</p>

<h1 align="center">VUEO</h1>

<p align="center">Browse movies, series and anime from your configured catalogs, with dedicated apps for Android phones and Android TV.</p>

<p align="center">
  <a href="#download">Download</a> · <a href="#quick-start">Quick start</a> · <a href="#build-from-source">Build from source</a> · <a href="#support">Support</a>
</p>

## Features

- **Home and library** — Pick up episodes in Continue Watching, keep a watchlist and browse playback history.
- **Search** — Find a title by name or narrow the search by adding its release year.
- **Source selection** — Browse streams from configured sources, filter by provider and choose a stream before playback.
- **Playback controls** — Choose audio and subtitle tracks when available. Android TV supports navigation with a D-pad remote.
- **Content Manager** — Manage compatible add-ons and provider repositories.

## Download

Choose the APK for your device from the [VUEO development release](https://github.com/nudroid12/VUEO-NEXT/releases/tag/vueo-dev).

| Device | APK | Application ID |
| --- | --- | --- |
| Android phone or tablet | [Download Mobile APK](https://github.com/nudroid12/VUEO-NEXT/releases/download/vueo-dev/VUEO-dev.apk) | `com.vueo.mobile` |
| Android TV or Android TV box | [Download TV APK](https://github.com/nudroid12/VUEO-NEXT/releases/download/vueo-dev/VUEOTV-dev.apk) | `com.vueo.tv` |

Both modules declare Android 6.0 (API 23) as their minimum SDK. These APKs are development builds.

## Quick start

1. Download and open the APK for your device. On TV, transfer the APK to the device and open it with a file manager.
2. If Android prompts you, allow APK installs for the app you used to open the file, then finish installation.
3. Open VUEO. To add your own sources, go to **Settings → Content Manager** and add a compatible add-on or provider repository.
4. Open a title from Home or Search, choose an episode if applicable, then pick a source. Playback availability and tracks depend on the source you selected.

## Build from source

Use **JDK 17** and install **Android SDK Platform 36**. Set up the SDK in Android Studio or point Gradle to a local SDK installation.

```bash
git clone https://github.com/nudroid12/VUEO-NEXT.git
cd VUEO-NEXT
./gradlew :mobile:assembleDebug :tv:assembleDebug
```

On Windows, use `gradlew.bat`. The APKs are written to `mobile/build/outputs/apk/debug/` and `tv/build/outputs/apk/debug/`.

| Path | Purpose |
| --- | --- |
| `mobile/` | Android mobile UI and player |
| `tv/` | Android TV UI and remote navigation |
| `shared/core/` | Shared app logic |
| `shared/collections-ui/` | Shared collections components |

### Development releases

The **VUEO Smart Build and Publish** workflow publishes signed APKs to `vueo-dev`. It can be started manually from **Actions**, and also listens for successful **Apply ZIP Patch** runs. Release signing uses the `VUEO_KEYSTORE_BASE64` and `VUEO_KEYSTORE_PASSWORD` Actions secrets and the keystore alias `vueo`.

## Support

Report reproducible app issues in [GitHub Issues](https://github.com/nudroid12/VUEO-NEXT/issues). Include the build, device model, Android version and steps to reproduce. Remove any private URLs or account details from logs before sharing them.

See [Third-party collections](THIRD_PARTY_COLLECTIONS.md) and [license notices](LICENSES/) for upstream credits and terms.

Developed by **NudroidLabs**.
