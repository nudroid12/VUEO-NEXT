# VUEO TV 29B — Premium Cinematic Home

Baseline: green 29A-R clean rebuild + build fix.

Scope is intentionally Home-only.

- Hero/first-rail composition recalibrated from the first real-TV screenshot.
- First rail begins at ~62% viewport height.
- Home nav labels fully recede while card focus is active; UP reveals them and DOWN restores content focus.
- Hero keeps the locked 180ms focus settle with a 420ms-in / 220ms-out fade-through.
- Layered cinematic scrims improve artwork/copy/rail integration.
- Landscape cards grow from 210dp to 244dp.
- Card titles move below artwork; focused card uses shallow 1.045 scale, soft shadow and a 1dp neutral edge.
- Home brand/profile anchors are quieter while browsing.

No Mobile/Shared Core source, profile flow, Search, Library, Settings, Details, Source or Player implementation is changed by this patch.
