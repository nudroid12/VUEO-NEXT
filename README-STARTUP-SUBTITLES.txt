VUEO — Stop subtitle discovery at first video frame (Mobile + TV)
Baseline: supplied VUEO-NEXT-main (56).zip, which already contains VidLove deferred subtitles and the overlay attachment fixes.

INSTALL
Upload mobile/, tv/ and shared/ to the VUEO repository root, replacing the matching files. Rebuild/install the APK using the existing workflow. Keep Cloudbank VidLove v2.5.0; no provider update is needed. No YML. Library History files are not included or overwritten.

BEHAVIOR
The provider continues returning its eligible video immediately. During selected-source startup, provider subtitle-index discovery and the existing addon subtitle branch may run independently. The first rendered video frame synchronously closes the selected-source subtitle window, cancels player-owned deferred/SmartSubs-list jobs, and invokes the dedicated engine subtitle stop control. The menu keeps only external subtitle tracks received before that boundary. A closed window rejects late results even when underlying work returns after cancellation.
New subtitle tracks arriving during startup use the live overlay and are excluded from the video source's MediaItem subtitle configurations. Existing initial payload tracks and native embedded subtitles retain their normal behavior. Choosing a subtitle still loads its file/AI output to display cues; this is playback of an existing choice, not discovery of new choices.
If video starts before the subtitle index finishes, the missing tracks will not appear for this playback session. Changing media/source or leaving disposes its window. A new selected source opens a fresh window. The subtitle Refresh action after the boundary reports that the list is fixed instead of starting another discovery.

VIDEO SCAN CONTROLS
Existing plugin VIDEO provider scans stop when a source is selected for playback. Addon VIDEO source scans have a separate control and are not stopped by the new subtitle boundary. The new SourceDiscoveryControl.stopSubtitles method cancels only the subtitle branch. Do not interpret this patch as stopping every video provider/addon on first frame.

FILES
MobileDetails / VueoTvApp: connect first-frame subtitle stop callback to the current discovery control.
MobilePlayer / TvPlayerScreen: selected-source startup window, cancellation, freeze, and refresh guard.
StartupSubtitleDiscovery: race-safe snapshot/coroutine cancellation boundary.
SourceDiscoveryControl / SourceDiscoveryEngine: independent cancellation of the addon subtitle child and safe completion after it is stopped.

VALIDATION
18 JVM/coroutine checks passed with the production StartupSubtitleDiscovery, SourceDiscoveryControl and MediaModels. Index identification uses the exact two-line predicate extracted from DeferredPluginSubtitles as a test fixture. Checks cover first-frame cancellation, non-cooperative late returns, frozen empty/nonempty lists, source reset/disposal, overlay routing, duplicate preservation, stop-before-attach, independent video branches and cancellation-safe await of the subtitle child. Kotlin syntax parsing passed for all 7 production files.
Full Android APK assembly and physical-device playback were not run here. The test fixture runner is under validation; it is not application code. Use the existing GitHub workflow and test startup once with quick video/slow subtitles and once with subtitles ready first. Expect SUBTITLE_DISCOVERY_CLOSED reason=first_frame in the source log.
