# Files to delete in GitHub

Uploading replacement folders does not delete existing files. Delete these two production files from the VUEO repository:

- `shared/core/src/main/java/com/vueo/shared/core/player/DeferredPluginSubtitles.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/StartupSubtitleDiscovery.kt`

Also remove the following old validation files and patch notes if present. Skip any that do not exist:

- `validation/DeferredSubtitleCheck.kt`
- `validation/StartupSubtitleDiscoveryCheck.kt`
- `validation/DeferredIndexFixture.kt`
- `README-VIDEO-FIRST.txt`
- `README-TMDB-MODEL-FIX.txt`
- `README-DEFERRED-SUBTITLE-ATTACH-FIX.txt`
- `README-STARTUP-SUBTITLES.txt`

Then upload the mobile/, tv/ and shared/ replacement folders from this ZIP to the repository root, replacing the matching files, and rebuild the APK.
