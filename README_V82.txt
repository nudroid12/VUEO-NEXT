VUEO Mobile Sources TV Parity v82
Apply after v81 (cumulative state through v80 + v81).

Scope:
1. Mobile playback now uses SourceDiscoveryControl like TV.
   - When playback commits a source, plugin discovery is stopped.
   - Addon/subtitle branches remain independent and may finish in background.
2. Mobile Player > Sources gets Refresh / Stop controls.
   - Refresh keeps current playback and existing source list alive.
   - New refreshed sources merge into the visible list.
   - Stop cancels source scanning without restarting playback.
3. Mobile source cards now use the same source-name structure as TV.
   - First line: repository/group title.
   - Second line: provider/server details with duplicate tokens removed.
   - Formatting helper lives in shared PlayerSourceDisplay and TV now uses the same helper too.
4. No Gradle build performed by request.
   - Static wiring, delimiter balance, and diff whitespace checks passed.

Files:
- shared/core/src/main/java/com/vueo/shared/core/player/PlayerSourceDisplay.kt
- tv/src/main/java/com/vueo/tv/ui/player/TvPlayerScreen.kt
- mobile/src/main/java/com/vueo/mobile/ui/detail/MobileDetails.kt
- mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
- mobile/src/main/java/com/vueo/mobile/ui/player/PlayerSourcesWorkspace.kt
