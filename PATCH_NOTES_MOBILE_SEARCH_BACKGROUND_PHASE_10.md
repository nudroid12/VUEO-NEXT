# Mobile Search background preparation — Phase 10

Apply after Phase 9. Extract into the repository root and overwrite matching files. No workflow/YML files are included.

## Changes
- Prepare Search/Discover filtering, genre lists, sorting and three-column grid rows on Dispatchers.Default.
- Preserve the existing ranking, Kids rules and 250 ms search debounce.
- Cache up to six type/sort preparations for the current input dataset; genre changes reuse the prepared source.
- Keep displayed results while incremental batches update the same view. Clear the previous view when query, mode, type, genre, sort, configuration, profile or Kids policy changes.
- Tag raw search results with query/mode/configuration to prevent old results entering a new view. Guard search publication by profile as well as request identity.
- Show preparation progress and defer invalid-genre reset until preparation and fetching finish.
- Pass profileVersion from the app root. This root file also retains the earlier Home state, addon reload and bottom-tab avatar changes.

No extra network request is introduced by local filter preparation. This patch does not change TV code.

## Validation
The actual preparation helper and shared model/policy code passed 2,021 JVM behavioral checks: ordering/filter parity, genre choices, grid output, query ranking, source reuse and cooperative cancellation. The reproducible test source is included under validation/. All three production Kotlin files passed PSI syntax checks.

A full Android build and device UI test were not run: this workspace has no Android SDK and the supplied repository lacks the Gradle wrapper JAR. Syntax checks are not Android type checking. No measured frame-time improvement is claimed. Shared sorting checks cancellation before and after the sort, rather than within each comparison.

## Device checks
1. Type rapidly and switch between title/actor search; only the current query should appear.
2. Change type, sort and genre, including while partial results arrive; verify order, progress and genre selection.
3. Switch profiles and Kids limits; verify no previous-profile results remain.
4. Test slow sources, Back navigation and returning to Search.
5. Check Home scroll restoration, addon configuration reload and the bottom-tab avatar from earlier cumulative root changes.
