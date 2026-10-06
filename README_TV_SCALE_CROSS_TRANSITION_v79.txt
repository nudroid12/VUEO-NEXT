VUEO TV Smooth Scale Cross Transition v79

Apply after current v78 state.

Scope:
- TV root page navigation uses smooth scale + fade cross-transition.
- Incoming page: 0.93 -> 1.00, no-bounce spring (stiffness 300).
- Outgoing page: 1.00 -> 0.97, no-bounce spring (stiffness 340).
- Fade-in: 260 ms ease-out.
- Fade-out: 190 ms ease-out.
- Removed the previous hard cut when returning to Home.
- Player route remains fade-only so the video surface is never scaled.
- Existing player panels/workspaces retain their own motion.

Files:
- tv/src/main/java/com/vueo/tv/ui/theme/TvMotion.kt
- tv/src/main/java/com/vueo/tv/ui/app/VueoTvApp.kt
