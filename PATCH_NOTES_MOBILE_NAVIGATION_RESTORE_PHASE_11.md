# Mobile navigation restoration — Phase 11

Apply after Phase 10: extract into the repository root and overwrite matching paths. No YML/workflow files are included. TV and SmartSubs are unchanged.

## Result
- Save active tab, settings page, Search query, title/actor mode, type, sort and genre with rememberSaveable.
- Hoist title/actor mode into the root so leaving Search for a detail or another tab also preserves it.
- Restore detail identity/artwork, entity target and up to eight most recent detail ancestors in Back order. Metadata and local playback history are hydrated again by the existing detail screen.
- Bind navigation to the active profile and Kids policy. On a mismatch, hide the destination while clearing the previous profile's navigation and Search state.
- Preserve the startup profile-picker policy and its existing PIN flow. After successful startup selection of the same profile/policy, resume the saved route. Explicit profile management retains its normal reset behavior.
- A restored detail waits for addon initialization before the detail metadata request starts.
- Playback source, video URL, discovery jobs, source picker, PIN entry and authorization are not saved. Recreation during playback returns to detail and requires another Play action.

Snapshots contain bounded identity and artwork fields; full episodes, descriptions, catalogs and stream URLs are not serialized. Invalid/unsupported payloads are rejected. The cumulative root retains earlier Home state, addon reload and bottom-tab avatar work, and the Search file retains Phase 10 preparation.

## Limits
This uses Android-provided saved instance state for Activity recreation or OS process restoration. It is not durable navigation history for force-stop, task removal or a new cold launch. Catalog See All rows are not restored; they fall back to the underlying tab. In-progress forms, dialogs, authentication and player sessions are not restored. Search results reload rather than being serialized. Detail navigation deeper than eight ancestors retains only the newest eight after restoration.

## Validation
The production snapshot codec compiled with the actual shared MediaItem models and passed 1,034 JVM behavioral checks: round trips, payload bounds, malformed data, latest-eight Back order, invalid-entry handling, profile/policy ownership and explicit picker behavior. Test source is included under validation/mobile-navigation/.

All three production Kotlin files passed PSI syntax checks. Static integration checks verified saveable root fields, owner gating, startup addon gating, Search mode callbacks and earlier cumulative root markers. These are not Compose restoration or Android device tests. A full Android build was not run because this workspace lacks the Android SDK and this supplied repository lacks its Gradle wrapper JAR.

## Phone checks
1. Open Search, enter a query and select actor/type/sort/genre. Recreate the Activity; verify selections and a refreshed result list.
2. Open a detail, open another recommendation, recreate, then Back; verify item identities and tab.
3. Recreate during playback; verify detail returns with no automatic playback. Resume progress should come from existing local history.
4. With startup profile picker/PIN enabled, recreate; verify authentication is still required and selecting the same profile resumes the route.
5. Select a different profile or change Kids age; verify the previous detail and Search are cleared.
6. Open an entity page or settings subpage and recreate; verify destination. Catalog See All intentionally falls back to its tab.
7. Verify Home scroll, addon configuration reload and bottom-tab avatar from earlier patches.
