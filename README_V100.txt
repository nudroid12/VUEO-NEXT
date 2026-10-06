VUEO v100 — Motion Polish

Purpose
- Finalize a restrained, modern VUEO motion language focused on smoothness and fluidity rather than visible animation.

Mobile
- Full-page navigation: fade-through + micro-scale (0.985 -> 1.0 on enter, 1.0 -> 0.992 on exit).
- Removed spring settling from full-page navigation in favor of deterministic cubic ease-out timing.
- Home-return anti-flick path remains near-sequential, but now uses the same shallow micro-scale language.
- Player route remains fade-only, shortened to feel more immediate.
- Default soft overlay/workspace motion is reduced to micro-scale.

TV
- Full-page navigation: fade-through + micro-scale (0.985 -> 1.0 on enter, 1.0 -> 0.992 on exit).
- No spring on full-screen page transitions; predictable cubic easing reduces visible frame pacing issues under load.
- Player route remains fade-only so the video surface is never scaled.
- Panels/workspaces use a short fade + shallow micro-scale.

Not changed
- Playback behavior
- Seek / skip
- Subtitle behavior
- Source discovery / ranking
- Episode logic
- Performance diagnostics

Validation
- No Gradle build by project preference.
- Static delimiter/import/diff/path checks only.
