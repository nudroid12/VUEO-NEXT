# TV 29B.1 — Navigation + Settings Activation

## Scope
29B.1 is a focused follow-up to 29B. It does not change the clean-rebuild architecture or restore any legacy TV UI.

### Navigation fix
- Top navigation now has one focus target per destination.
- DPAD_CENTER / ENTER commits the focused destination exactly once on key-up.
- The old `focusable() + clickable()` stacking was removed from top-nav items and the profile anchor because it could create a two-step activation path on Android TV.
- D-pad focus movement never changes route by itself.
- Down from the top bar returns to the last content/settings focus when the screen supplies a restore target.

### Home rail spacing
- The title-to-card rhythm in Home rows increases from 11dp to 19dp.
- The rail itself stays at the 29B vertical position; this does not push the entire first rail further down.

### Settings activation
A new TV-native Settings surface is active and backed directly by Shared Core stores. It exposes:
- Profile / profile management entry
- Ask who’s watching on startup
- Resume playback
- Preferred source quality
- Automatic source recovery
- Auto-play next episode
- Skip detected segments
- Subtitles by default
- Preferred subtitle language
- Subtitle size
- Technical source details

Remote grammar:
- Up/Down moves through rows.
- Left/Right adjusts supported values.
- OK toggles/advances/opens exactly once.
- Up from the first setting moves to the Settings top-nav item.
- Down from top navigation restores the last focused setting.

Complex settings areas that do not yet have clean TV-native screens remain intentionally absent rather than restoring legacy TV UI.
