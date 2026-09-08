# TV 42E Settings focus architecture

42E is a focus-only correction on top of 42D. It does not redesign the Settings visuals or change Shared Core settings behavior.

## Root cause fixed
42D moved focus from a category row to the right panel on activation KeyDown. Right-panel rows commit on activation KeyUp. The KeyUp from the same physical OK press could therefore reach the newly focused right-panel control and activate it immediately.

42E keeps directional movement on KeyDown, but category OK / Enter handoff happens on KeyUp. The KeyDown is consumed without moving focus. This preserves the lock that one OK press performs one action only.

## Focus contract
- UP / DOWN on the left category list changes category focus only.
- RIGHT from a category enters the active right panel.
- OK / Enter from a category enters the active right panel once, on KeyUp.
- Entering a right panel restores that panel's last focused row when available, otherwise its first focusable row.
- LEFT from a normal right-panel row returns to the currently selected category.
- RIGHT on a non-adjustable row is contained and does not escape the panel.
- UP on the first focusable panel row and DOWN on the last focusable panel row are contained.
- Adjustable rows keep LEFT / RIGHT value adjustment behavior.
- OK / Enter activates a right-panel row once, on KeyUp.
- Opening nested Settings content records the parent row that launched it.
- Back from nested content returns to the parent panel and restores that exact row.
- Back from a root right panel returns focus to its selected category.
- Profile card and Switch Profiles now use the same KeyUp-only activation rule rather than clickable focus behavior.

## Scope
Only `TvSettingsComponents.kt` is changed. `TvSettingsScreen.kt`, Shared Core, Mobile, provider logic and TV visual hierarchy are untouched.
