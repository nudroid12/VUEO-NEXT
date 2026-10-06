VUEO v94 - TV Sources Repo Naming + Reliable Tab-to-List Focus

Scope: TV-only.
Shared core behavior is NOT changed. This patch only reuses the existing shared PlayerSourceDisplay formatter from TV Source cards.

Changes
1. TV Source cards now mirror the in-player Sources naming hierarchy:
   - repository/group name on the primary line
   - provider/server/details on the secondary line
   - existing source title/technical metadata remain below
2. DPAD Down from Refresh / All / provider chips no longer performs a one-shot FocusRequester request.
   - schedules source-list entry focus
   - scrolls the target first source into the LazyColumn viewport
   - waits for Compose frame attachment
   - retries focus while discovery/recomposition is still updating the list
   - consumes Down only when a source target exists and the focus handoff is scheduled
3. No Player, Mobile, source discovery, ranking, playback, or shared-core changes.

Validation
- Kotlin delimiter/structure sanity check passed on both modified files.
- No trailing whitespace in modified files.
- No Gradle build performed per project workflow.
