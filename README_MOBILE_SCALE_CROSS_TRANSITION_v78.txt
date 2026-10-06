VUEO Mobile Smooth Scale Cross-Transition v78

Scope:
- Mobile page navigation only.
- Refines the existing v76 global page transition.
- Does not modify TV navigation or Player workspace/panel animations.

Motion tuning:
- Incoming page: scale 0.93 -> 1.00 + fade in.
- Outgoing page: scale 1.00 -> 0.97 + fade out.
- Scale uses critically damped/no-bounce spring motion.
- Fade completes slightly before scale settles, preserving a natural ease-out finish.
- No slide, blur, rotation, overshoot or extra animation.

Apply after v76 (and v77 if present).
