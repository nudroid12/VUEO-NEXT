VUEO — Roll back deferred provider subtitle and first-frame discovery patches

INSTALL (GitHub web)
1. Read DELETE_FILES_ROLLBACK_SUBTITLES.md. Delete the two listed production files and remove the optional old validation/patch-note files if present. Uploading a ZIP/folder does not remove existing repository files.
2. Upload mobile/, tv/ and shared/ from this ZIP into the VUEO repository root and replace matching files.
3. Build/install the APK through the existing workflow. No YML changes.

RESTORED
MobilePlayer.kt and TvPlayerScreen.kt restored byte-for-byte to the pre-deferred Phase23 player baseline.
LiveSmartSubs.kt and MobilePlayerMedia.kt restored byte-for-byte to the original Phase18 SmartSubs overlay baseline. This keeps VUEO x SmartSubs AI's original overlay; provider subtitles use the prior native route.
PluginSubtitleParser.kt and StreamSubtitlePolicy.kt restored to the pre-deferred implementations. PluginRuntimeParsing.kt restored to the captured pre-deferred source. No subtitleRequests parser, deferred index descriptor, liveOverlay model flag or provider-overlay merge remains.
The original source engine/control and MobileDetails/VueoTvApp routes restored from supplied repo (56), removing the first-frame subtitle stop callback and addon-subtitle stop hooks. Automatic/manual subtitle discovery returns to the behavior before those patches; it is not frozen at first frame.
DeferredPluginSubtitles.kt and StartupSubtitleDiscovery.kt are removed, together with their optional old validation files/patch notes. There is no feature toggle or disabled leftover implementation.

PRESERVED
Library History, Delete History/reset, Favorites, existing metadata/TMDB identity fields, original VUEO x SmartSubs, Phase23 player cleanup and the pre-existing v2.4.0 JSON-source early-completion bridge remain. Library and deletion files are not overwritten by this rollback.

PROVIDER COMPATIBILITY
This ZIP only changes VUEO. Cloudbank has not been changed. VidLove v2.5.0 depends on the removed deferred API for subtitles missing from the video payload; those late subtitles will no longer be fetched after this rollback. Payload-provided subtitles and the pre-existing subtitle addon/SmartSubs paths retain their original handling. A provider rollback would be a separate change.

VALIDATION
Restored file checks against the captured baselines passed. No references to DeferredPluginSubtitles, StartupSubtitleDiscovery, liveOverlay, parsePluginSubtitleRequests, onStopSubtitleDiscovery or SUBTITLE_DISCOVERY_CLOSED remain anywhere in the resulting Kotlin tree. Restored shared model/parser/language/stream policy/source control compile under Kotlin/JVM. All 12 replacement production files pass Kotlin syntax parsing. The 20 existing title-reset checks still pass with the restored MediaModels. Full APK assembly and device playback were not run here.
