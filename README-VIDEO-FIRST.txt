VUEO — Video First / Deferred Provider Subtitles (Mobile + TV)

INSTALL
Upload the folders in this ZIP to the VUEO repository root, replacing the matching files. Build/install a new APK using your existing build workflow. No workflow/YML file is included.
Then install Cloudbank VidLove v2.5.0 and refresh the provider repository.
This is an incremental patch on the current Phase23 player / Phase18 live overlay and v2.4.0 JSON-source bridge baseline. It retains their existing behavior. This ZIP contains source code, not an APK.

BEHAVIOR
Provider scan returns the eligible video immediately. No subtitle-list request is issued during the scan. Sources with subtitle tracks already in their payload keep them.
Otherwise subtitleRequests describes a JSON subtitle list for the exact requested movie/episode. Only the selected player fetches that list; no request is created for an unsuccessful scan or an unselected source. Switching source/media or leaving the player cancels the coroutine, including its native HTTP call.
Late tracks are displayed through the existing live text overlay. They are excluded from the video's MediaItem subtitle configuration, so their arrival does not rebuild/reprepare/seek the video. Track selection still works through the normal subtitle menu. The overlay also parses SRT, SSA/ASS and TTML in addition to WebVTT.
Subtitle-index credentials stay on the index request; they are not inherited by unrelated subtitle CDN rows. The individual row may specify its own headers. Failed/empty subtitle requests do not fail playback.

IMPLEMENTATION
The plugin parser accepts optional subtitleRequests and represents its descriptors internally with application/vnd.vueo.subtitle-list+json. StreamSubtitlePolicy hides these descriptors from playable subtitle lists. DeferredPluginSubtitles resolves them after selection. SubtitleTrack.liveOverlay routes the returned tracks to the existing text overlay, independently of the SmartSubs AI setting.

VALIDATION
Shared production Kotlin sources compile against the Android/Media3 dependencies. 17 injected-fetch JVM checks pass, including pending list/video independence, cancellation, stale result rejection, index filtering, provider/language identity, header isolation, duplicate endpoints, failed lists and preservation of all 66 rows. Mobile, TV and runtime parser pass Kotlin syntax parsing. Full APK assembly and physical-device playback were not run in this environment.

DEVICE CHECK
Confirm source scan finishes before DEFERRED_SUBTITLES_READY. While the list is pending, video should begin. Subtitle arrival/selection should not reset video; test switching sources and leaving the player while the list is still loading.
