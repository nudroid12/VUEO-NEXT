# Mobile profile Cancel/avatar — audit phase 8

Apply after the earlier patches at the VUEO-NEXT repository root. This ZIP includes VueoProfiles.kt, cumulative VueoApp.kt (preserving phases 3/4) and new MobileProfileAvatarArtwork.kt. No YML/workflows, raster artwork changes, dependencies or APK are included. TV's existing profile fix is unchanged.

## Delete confirmation

- Pressing Delete keeps the editor composition and its name/avatar/Kids/age/PIN draft behind the confirmation dialog.
- Cancel, dialog Back or outside dismissal clears only the delete confirmation, returning to the same editor/draft/scroll position. The editor closes after successful deletion; a failed delete leaves the editor available.
- When parental approval is required, the delete confirmation is hidden while its PIN gate is displayed. The existing approval requirement remains in force. Cancelling the PIN gate returns to confirmation; cancelling confirmation returns to the editor.
- Successful deletion cleans up both the saved local avatar and a different unsaved local photo selected in the draft. Cancelling confirmation preserves the draft photo.
- System Back from the editor performs the same draft-photo cleanup as the toolbar Back button.

## Avatar presentation

- Reuses the TV patch's measured normalized circle center/radius values for 32 stock portraits. The renderer recenters/zooms the artwork inside an untransformed circular clip, avoiding source-tile black crescents around offset portraits.
- Applied to the avatar-selection grid, editor preview, profile picker/manage surfaces and bottom You tab. One mobile renderer now supplies the tab avatar, including local photos and legacy fallback behavior.
- Stock avatar IDs and source files remain unchanged. Legacy avatars without a measured entry retain their original rendering. Custom photos retain the existing center-crop import policy.
- Local photo file reads/BitmapFactory decode run on IO, with a sampled decode for the displayed pixel size. A keyed image state avoids carrying the previous profile's photo while a new photo loads; the initial fallback uses the profile's initial rather than the local filename.

## Validation

- Kotlin PSI syntax parsing passed for all three production files.
- All 32 crop entries match the TV fix exactly, and each measured disk stays within its source tile bounds.
- Reviewed Cancel/dismiss versus successful-delete state mutations, draft-photo cleanup, all stock-avatar call sites, photo decode placement and preservation of phase-3 Home/phase-4 addon reload integration in VueoApp.kt.
- No Android build, emulator visual comparison or phone test was run. This workspace lacks Android SDK and the supplied source lacks the Gradle wrapper JAR. Syntax/geometry checks do not validate actual Compose rendering or dialog behavior.

## Device checks

1. Edit name, avatar, Kids mode/age and PIN draft; press Delete then Cancel. Verify all drafts and editor position remain. Repeat with dialog Back/outside dismissal.
2. Repeat with an unsaved custom photo. Confirm Cancel preserves it and editor Back discards it without deleting the previously saved photo.
3. With parental approval enabled, confirm the PIN dialog is reachable above the editor, wrong/cancelled PIN cannot delete, and successful approval deletes the intended profile.
4. Confirm a successful deletion returns to Manage Profiles and updates the active profile correctly. Verify the protected default profile still has no Delete action.
5. Inspect all stock-avatar categories in the grid, selected preview, picker and You tab. Check border visibility and circle alignment at normal/large font/display sizes.
6. Import a local photo, save, switch profiles and reopen the app; verify profile photo/fallback behavior and responsive navigation.
7. Exercise rapid Back during transitions and Home navigation after profile changes. Full draft restoration across process death/rotation is outside this patch.
