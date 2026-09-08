# TV 42B - Settings Direct Master Detail

Base: `VUEO-NEXT-main-42A-settings-mobile-categories.zip`.

## Goal

Replace the temporary VUEO / PLAYBACK / APP grouping with direct TV Settings categories that mirror the Mobile Settings hierarchy while keeping a TV-native D-pad master-detail layout.

## Root categories

The left Settings rail is now:

1. Profile
2. Personalization
3. Content Manager
4. Enhancements
5. Playback
6. Subtitles
7. Sources
8. Appearance
9. Data & Storage
10. Updates
11. About VUEO

## Interaction

- Focusing a root category updates the right panel immediately.
- Press Right or OK from the category rail to enter the current right panel.
- Settings content remains inside the right panel instead of replacing the Settings screen.
- Content Manager child views such as Addons, Providers and Catalog Order replace only the right panel.
- Back from a nested right-panel view returns to its parent panel.
- Back from a root right panel returns focus to the category rail.
- Back from the category rail exits Settings.
- The Profile category shows the active profile card. Selecting it opens the profile switcher in the same right panel. PIN-protected profiles still use the existing PIN modal.

## Architecture

- Shared Core and existing setting stores remain authoritative.
- Existing settings rows, dialogs, provider diagnostics, backup/restore, update actions and preference writes are reused.
- A master-detail host now embeds the existing settings list content inside the right panel, avoiding duplicated preference implementations.
- The obsolete 42A VUEO / PLAYBACK / APP category hub implementation was removed from the active TV Settings path.

## Validation

- Searched the TV Settings source for stale `HUB`, `VUEO`, `PLAYBACK` and `APP` root grouping references.
- Kotlin parser pass found no syntax-form errors in the modified files.
- Full Gradle compilation could not run in this environment because the repository bootstrap requires Gradle 9.3.1 from `services.gradle.org`, and network resolution is unavailable here.
