VUEO Mobile Smooth Scale Cross-Transition v76

Base compatibility:
- VUEO-NEXT-main (38)
- Preserves v68-v75 changes by building from the reconstructed latest tree.

Motion:
- Incoming page: scale 0.965 -> 1.0 + fade 0 -> 1
- Incoming scale: critically damped spring, stiffness 430
- Incoming fade: 280ms ease-out
- Outgoing page: scale 1.0 -> 0.985 + fade 1 -> 0
- Outgoing scale: critically damped spring, stiffness 500
- Outgoing fade: 220ms ease-out
- No delay between scale/fade
- No bounce / overshoot
- No slide / blur / rotation / extra motion

Coverage:
- App surface navigation (root/catalog/entity/details/profile)
- Main tabs and settings page navigation
- Profile manage/editor pages
- Settings personalization sub-pages
- Detail <-> source picker page transition

Intentionally unchanged:
- Full-screen Player transition remains its dedicated fade-through to avoid
  SurfaceView/video rendering artifacts while testing the new page motion.
- Player workspaces/panels/dialogs/gesture feedback are not page transitions.
