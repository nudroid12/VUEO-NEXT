VUEO Mobile Home Return Flick Fix v81

Scope: Mobile navigation only.

Fix:
- Adds a dedicated transition for returning to Home from another bottom-tab/settings page.
- Outgoing page fades faster (130 ms) and scales only slightly backward.
- Home alpha begins after 105 ms, reducing poster-layer overlap to a very short window.
- Home keeps the stronger 0.93 -> 1.00 scale language with a no-bounce spring.
- All other page transitions keep the existing v78 motion.
- Global NetworkImage fade/cache behavior is untouched.
- TV/player behavior is untouched.

Apply after the existing v78/v79/v80 cumulative state.
No Gradle build was run per project workflow; static/diff checks only.
