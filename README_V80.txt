VUEO TV Standalone Skip Auto Focus v80

Scope:
- TV player only.
- When Skip Intro / Skip Recap / Skip Credits is the only contextual action while full controls are hidden, focus moves directly to the Skip button.
- OK activates Skip immediately without an extra D-pad move.
- Does not steal focus when full controls or Next Episode are visible.
- When the Skip prompt disappears, focus returns through the existing player focus recovery path.
- Existing v74 Up/Down escape behavior is preserved.

Files:
- tv/src/main/java/com/vueo/tv/ui/player/TvPlayerScreen.kt

Build note:
- Gradle compile could not run in this environment because the wrapper distribution is not locally cached and services.gradle.org is unavailable.
