# TV 44A Subtitle Workspace Rebuild

## Scope

Player subtitle presentation only. Shared Core track policy, Media3 selection,
subtitle delay rendering and persisted subtitle settings are unchanged.

## Current layout

- Full-screen cinematic scrim over the playing video.
- Three simultaneous cards: Languages, Subtitles and Subtitle Style.
- No Mobile-only `Float` action.
- Larger 10-foot typography and tighter, consistent card spacing.
- Provider, track identity and duplicate-track numbering remain visible.

## Focus contract

- Accent indicates the selected language or track.
- A neutral-white edge indicates current D-pad focus.
- Left and Right move predictably between the active language, exact track and
  first style control.
- OK/Enter activates on KeyUp once through the existing Player panel control.
- Back closes the workspace through the existing Player panel stack and returns
  focus to the `Subs` control.

## Validation

Static source inspection completed. The local Gradle build could not resolve
Android Gradle Plugin 9.1.1 from the configured repositories in the handoff
environment, so GitHub Actions remains the required compile confirmation.
