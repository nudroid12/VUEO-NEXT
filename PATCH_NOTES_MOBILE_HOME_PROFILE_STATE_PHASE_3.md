# Mobile Home/profile state — audit phase 3

Extract/upload this patch at the VUEO-NEXT repository root, replacing matching files. It contains two Kotlin replacements and these notes, with no YML/workflows or APK.

## Behavior

- Home now lives inside a root-owned SaveableStateHolder, outside the route/tab AnimatedContent lifetime. Home's existing rememberLazyListState and each LazyRow's default saveable list state can be restored after navigating through Details, Player, See All or another tab.
- The state namespace uses the local profile ID plus Kids/standard policy and the selected Kids age limit. Each profile has its own saved Home position and retained presentation object. A rename/avatar edit keeps the same namespace.
- Retained featured content, CW and recommendation sections no longer share one presentation object across all profiles. Existing IO library hydration still refreshes CW on return; this patch adds no network polling or disk persistence loop.
- Profile deletion or a Kids policy/age change removes obsolete Home state and retained presentation references. Retention is limited to valid local profile namespaces (the current ProfileStore supports eight profiles).
- Home receives the active profile ID from the same root profile snapshot used to select its saved state, rather than independently caching the ID under libraryVersion.

## Scope and limits

The patch adapts TV's profile-scoped Home state-holder pattern for mobile. It does not change TV, recommendation scoring, SmartSubs, image caches, player lifecycle, Account layout or built-in addons. Earlier phase-1/phase-2 changes reside in other files and remain applicable.

Only small saveable UI state, such as list indices/offsets, goes into saved instance state. Media lists and presentation data remain in RAM. Android saved-instance restoration can retain these UI coordinates; this is not a complete process-death restoration of the selected route, player or navigation back stack.

Live catalog/CW/recommendation changes can remove or reorder items; Compose may then adjust/clamp a saved position. This patch preserves existing stable row/item keys and does not freeze data to force an obsolete item position.

## Validation completed

- Kotlin PSI syntax parsing passed for VueoApp.kt and MobileHome.kt.
- Reviewed the one mobile HomeScreen call site and its new activeProfileId argument, removal of the old single-instance helper, stable existing LazyColumn/LazyRow item keys, and the current profile-selection/profile-edit/sync revision callbacks.
- Reviewed removal of saved and retained namespaces for deleted profiles and obsolete Kids policies.
- No Android build or device/UI test was run: the workspace has no Android SDK, and the supplied repo has no Gradle wrapper JAR. Syntax checks do not validate Compose runtime restoration.

## Device verification after building

1. Scroll Home vertically, then scroll a catalog/CW row horizontally. Open Details and return. Repeat through Player and See All, then after switching to Search/Library/You and back.
2. Repeat navigation 20 times, including rapid Back gestures and interrupted transitions. Verify no duplicate-saveable-key crash and no unwanted return to the top.
3. Use two profiles with different scroll positions and CW history. Switch between them and confirm positions/content stay with the correct profile while fresh library data hydrates.
4. Delete an inactive profile, create another, and verify the new profile starts fresh. Change Kids mode/age and confirm old policy state is cleared and filtering remains correct. Rename/change avatar and confirm the position remains.
5. Rotate/recreate the Activity with saved instance state. Confirm restored Home coordinates where data remains available; full route/player restoration is outside this patch.
6. Remove a CW item or disable/reorder a catalog while scrolled. Verify the app remains usable when items legitimately change.
