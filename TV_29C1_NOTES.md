# TV 29C.1 — Search TV Calibration

29C.1 is a visual/density calibration on top of 29C + its build fix. Search capability and Shared Core behavior are unchanged.

## Locked changes
- Removes the persistent `VUEO` wordmark from normal post-profile page chrome through the shared TV top bar.
- Removes VUEO-as-top-label from Settings page chrome without changing Settings behavior.
- Keeps startup and profile-selection branding intact.
- Keeps VUEO contextual top navigation; the mockup sidebar is **not** introduced.
- Compacts Search title, spacing, search field and filter controls.
- Search field is ~76% of content width and 54dp high.
- Type / Sort / Genre / Title-Actor controls sit on one row.
- Discover / Search Results heading is compact and sits immediately above content.
- Result grid is fixed to **8 columns** of 2:3 posters.
- Poster focus uses ~1.035 scale, 110-145ms timing, low shadow and a 1dp neutral-white edge.

## Preserved
- 29C Search discovery/search/ranking/filter logic
- Title / Actor capability
- query/filter/scroll/exact-focus restoration after Detail
- Shared Core and Mobile source
- 29B Home hero composition, hero settle/fade and Home focus behavior

## Build note
Local Gradle verification may still stop before compilation when `services.gradle.org` is unavailable. CI/device build remains the canonical compile/runtime check.
