# TV 42D Settings mobile content adaptation

Baseline: 42A full repository with 42B and 42C patches applied.

## Changes

- Moves Profile into the VUEO section in the TV Settings category rail.
- Keeps VUEO, PLAYBACK, and APP as non-focusable section labels only.
- Gives every Settings category its own independent rounded card in the left rail. Categories no longer share one grouped container.
- Increases category and panel typography for TV viewing distance.
- Removes the duplicate Profile row from Personalization.
- Keeps the profile startup picker setting by moving it into the nested Profiles panel instead of leaving it inside Personalization.
- Keeps the Profile panel as the existing profile summary card. Selecting the summary still opens the existing Your DNA TV page.
- Reworks the right Settings panel toward the Mobile Settings presentation:
  - independent setting cards instead of one large legacy container
  - section labels
  - larger titles and descriptions
  - icon tiles where Mobile uses identifiable service or category rows
  - status values kept on the right
- Rebuilds Content Manager root content around the Mobile structure:
  - Installed, Online, Slow, and Failed health metrics
  - Addons
  - Plugins & Providers
  - Catalog Order
  - Smart Source health note
- Keeps Content Manager nested pages inside the right panel.
- Rebuilds Enhancements root to match Mobile hierarchy:
  - TMDB
  - MDBList
  - Gemini
- TMDB, MDBList, and Gemini now open nested Settings panels rather than exposing every enhancement control on the root page.
- Existing enhancement switches and API key behavior are preserved in those nested panels.
- Playback, Subtitles, Sources, Appearance, Data & Storage, Updates, and About now use the same card and section presentation while keeping their existing TV logic.

## Focus behavior

- D-pad focus remains only on actual Settings categories and controls.
- Right or OK from a category enters its content panel.
- Left from a panel control returns to the selected category.
- Nested Content Manager and Enhancements panels stay inside the same Settings master-detail shell.
- Back from a nested panel returns to its parent panel.
- Back from a root panel returns focus to the category rail before leaving Settings.

## Verification

- Only the two TV Settings Kotlin files are replaced.
- Kotlin delimiter balance passed for both modified files.
- A standalone Kotlin parser pass found no syntax-pattern errors such as `expecting`, `unexpected tokens`, `syntax error`, or `unclosed`.
- Full Gradle compile could not run because this environment could not resolve `services.gradle.org` to obtain the configured Gradle distribution.
